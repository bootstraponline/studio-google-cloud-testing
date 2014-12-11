/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gct.testing;

import com.google.api.client.http.HttpHeaders;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.api.services.test.model.TestExecution;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gct.testing.results.IGoogleCloudTestRunListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static com.google.gct.testing.BucketFileMetadata.Type.*;
import static com.google.gct.testing.launcher.CloudAuthenticator.getStorage;
import static com.google.gct.testing.launcher.CloudAuthenticator.getTest;

public class CloudResultsLoader {
  private static final Function<StorageObject, BucketFileMetadata> TO_BUCKET_FILE = new Function<StorageObject, BucketFileMetadata>() {
    @Override
    public BucketFileMetadata apply(StorageObject input) {
      return new BucketFileMetadata(input.getName());
    }
  };

  private static final Function<BucketFileMetadata, String> TO_COMPLETED_CONFIGURATION_OR_NULL = new Function<BucketFileMetadata, String>() {
    @Override
    public String apply(BucketFileMetadata input) {
      if (input.getType() == DONE) {
        return input.getEncodedConfigurationInstance();
      }
      return null;
    }
  };

  // Used to track whether any new data (except for DONE file) was received from the backend,
  // e.g., new progress status, results file, or screenshot.
  private boolean newDataReceived = false;

  private final Function<BucketFileMetadata, BucketFileMetadata> UPDATE_CONFIGURATION_PROGRESS =
    new Function<BucketFileMetadata, BucketFileMetadata>() {
    @Override
    public BucketFileMetadata apply(BucketFileMetadata file) {
      if (file.getType() == PROGRESS) {
        Optional<byte[]> optionalBytes = getFileBytes(file);
        if (optionalBytes.isPresent()) {
          String progressLine = new String(optionalBytes.get());
          String encodedConfigurationInstance = file.getEncodedConfigurationInstance();
          String previousProgressLine = configurationProgress.get(encodedConfigurationInstance);
          if (!progressLine.equals(previousProgressLine)) {
            newDataReceived = true;
            configurationProgress.put(encodedConfigurationInstance, progressLine);
            testRunListener.testConfigurationProgress(
              ConfigurationInstance.parseFromEncodedString(encodedConfigurationInstance).getDisplayString(), progressLine);
          }
        }
      }
      return file;
    }
  };

  private final String cloudProjectId;
  private final IGoogleCloudTestRunListener testRunListener;
  private final String bucketName;
  private final Map<String, TestExecution> testExecutions;

  private final Map<String, String> configurationProgress = new HashMap<String, String>();


  public CloudResultsLoader(String cloudProjectId, IGoogleCloudTestRunListener testRunListener, String bucketName,
                            Map<String, TestExecution> testExecutions) {
    this.cloudProjectId = cloudProjectId;
    this.testRunListener = testRunListener;
    this.bucketName = bucketName;
    this.testExecutions = testExecutions;
  }

  //TODO: Check file size after loading it and load the missing parts, if any (i.e., keep loading until the file's size does not change).
  private Optional<byte[]> getFileBytes(BucketFileMetadata fileMetadata) {
    int chunkSize = 2 * 1000 * 1000; //A bit less than 2MB.
    int currentStart = 0;
    byte [] bytes = null;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      do {
        //Retrieve the Get object in each iteration to avoid exceptions while updating request headers with a different range.
        Storage.Objects.Get getObject = getStorage().objects().get(bucketName, fileMetadata.getPath());
        getObject.getMediaHttpDownloader().setDirectDownloadEnabled(true);
        getObject.setRequestHeaders(new HttpHeaders().setRange(String.format("bytes=%d-%d", currentStart, currentStart + chunkSize - 1)));
        getObject.executeMediaAndDownloadTo(out);
        currentStart = currentStart + chunkSize;
      } while (out.size() == currentStart); //Repeat as long as all the requested bytes are loaded.
      bytes = out.toByteArray();
    } catch (Exception e) {
      System.err.println("Failed to load a cloud file: " + fileMetadata.getName());
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException e) {
          //ignore;
        }
      }
    }
    return Optional.fromNullable(bytes);
  }

  /**
   *
   * @return true if some new data was received from the backend (except for DONE file).
   */
  public boolean updateResults(Map<String, ConfigurationResult> results) {
    newDataReceived = false;
    try {
      updateResultsFromApiStatus(results);

      Storage.Objects.List objects = getStorage().objects().list(bucketName);
      List<StorageObject> storageObjects = objects.execute().getItems();

      Iterable<BucketFileMetadata> files =
        Iterables.transform(storageObjects, Functions.compose(UPDATE_CONFIGURATION_PROGRESS, TO_BUCKET_FILE));

      Set<String> finishedConfigurations =
        Sets.newHashSet(Iterables.filter(Iterables.transform(files, TO_COMPLETED_CONFIGURATION_OR_NULL), Predicates.notNull()));

      for (BucketFileMetadata file : files) {
        if (file.getType() == PROGRESS) {
          String encodedConfigurationInstance = file.getEncodedConfigurationInstance();
          ConfigurationResult result = results.get(encodedConfigurationInstance);
          if (result == null) {
            result = new ConfigurationResult(encodedConfigurationInstance);
            results.put(encodedConfigurationInstance, result);
          }
          result.setComplete(finishedConfigurations.contains(encodedConfigurationInstance));
          result.setInfrastructureFailure(configurationProgress.get(encodedConfigurationInstance) != null &&
                                          configurationProgress.get(encodedConfigurationInstance).startsWith("Infrastructure Failure:"));
        }
      }
      loadResultFiles(results);
      loadScreenshots(results);
    } catch (Exception e) {
      throw new RuntimeException("Failed updating the results from the bucket!", e);
    }
    return newDataReceived;
  }

  private void updateResultsFromApiStatus(Map<String, ConfigurationResult> results) {
    if (testExecutions == null) { //Could happen for the fake bucket.
      return;
    }
    for (Map.Entry<String, TestExecution> testExecutionEntry : testExecutions.entrySet()) {
      try {
        TestExecution testExecution =
          getTest().projects().testExecutions().get(cloudProjectId, testExecutionEntry.getValue().getId()).execute();
        if (testExecution.getState().equals("ERROR")) {
          String progressLine = "Infrastructure Failure: " + testExecution.getTestDetails().getErrorDetails() + "\n";
          String encodedConfigurationInstance = testExecutionEntry.getKey();
          String previousProgressLine = configurationProgress.get(encodedConfigurationInstance);
          if (!progressLine.equals(previousProgressLine)) {
            newDataReceived = true;
            configurationProgress.put(encodedConfigurationInstance, progressLine);
            testRunListener.testConfigurationProgress(
              ConfigurationInstance.parseFromEncodedString(encodedConfigurationInstance).getDisplayString(), progressLine);

            ConfigurationResult configurationResult = results.get(encodedConfigurationInstance);
            if (configurationResult == null) {
              configurationResult = new ConfigurationResult(encodedConfigurationInstance);
              configurationResult.setInfrastructureFailure(true);
              results.put(encodedConfigurationInstance, configurationResult);
            } else {
              configurationResult.setInfrastructureFailure(true);
            }
          }
        }
      }
      catch (IOException e) {
        // Suppress, not a critical problem.
      }
    }
  }

  private void loadResultFiles(Map<String, ConfigurationResult> results) {
    try {
      Storage.Objects.List objects = getStorage().objects().list(bucketName);
      List<StorageObject> storageObjects = objects.execute().getItems();

      Iterable<BucketFileMetadata> files = Iterables.transform(storageObjects, TO_BUCKET_FILE);

      for (BucketFileMetadata file : files) {
        if (file.getType() == RESULT) {
          String encodedConfigurationInstance = file.getEncodedConfigurationInstance();
          ConfigurationResult configurationResult = results.get(encodedConfigurationInstance);
          if (configurationResult != null && !configurationResult.hasResult()) {
            Optional<String> optionalResult = toOptionalString(getFileBytes(file));
            if (optionalResult.isPresent() && file.hasEncodedConfigurationInstance()) {
              newDataReceived = true;
              configurationResult.setResult(optionalResult.get());
            }
          }
        }
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  public void loadScreenshots(Map<String, ConfigurationResult> results) {
    List<StorageObject> storageObjects = null;
    try {
      Storage.Objects.List objects = getStorage().objects().list(bucketName);
      storageObjects = objects.execute().getItems();
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to retrieve bucket objects: ", e);
    }

    Iterable<BucketFileMetadata> files = Iterables.transform(storageObjects, TO_BUCKET_FILE);
    ArrayList<ScreenshotDownloadThread> downloadThreads = new ArrayList<ScreenshotDownloadThread>();
    for (BucketFileMetadata file : files) {
      if (file.getType() == SCREENSHOT) {
        ConfigurationResult result = results.get(file.getEncodedConfigurationInstance());
        if (result != null && result.getScreenshots().get(file.getName()) == null) {
          downloadThreads.add(new ScreenshotDownloadThread(file, result));
        }
      }
    }

    // TODO: Replace with a pool of worker threads.
    final int capParallelThreads = 10;
    int currentParallelTreads = 0;
    for (int i = 0; i < downloadThreads.size(); i++) {
      downloadThreads.get(i).start();
      currentParallelTreads++;
      if (currentParallelTreads == capParallelThreads) {
        // Join the existing threads before proceeding to avoid going over the limit.
        for (int j = i - currentParallelTreads + 1; j <= i; j++){
          try {
            downloadThreads.get(j).join();
          }
          catch (InterruptedException e) {
            //ignore
          }
        }
        currentParallelTreads = 0;
      }
    }

    // Join any remaining threads.
    for (int i = downloadThreads.size() - currentParallelTreads; i < downloadThreads.size(); i++){
      try {
        downloadThreads.get(i).join();
      }
      catch (InterruptedException e) {
        //ignore
      }
    }
  }

  private class ScreenshotDownloadThread extends Thread {

    private final BucketFileMetadata file;
    private final ConfigurationResult result;

    private ScreenshotDownloadThread(BucketFileMetadata file, ConfigurationResult result) {
      this.file = file;
      this.result = result;
    }

    @Override
    public void run() {
      Optional<byte[]> optionalFileBytes = getFileBytes(file);
      if (optionalFileBytes.isPresent()) {
        BufferedImage image = null;
        try {
          image = ImageIO.read(new ByteArrayInputStream(optionalFileBytes.get()));
        }
        catch (IOException e) {
          System.out.println("Failed to create an image for screenshot: " + e.getMessage());
          return;
        }
        image.flush();
        result.addScreenshot(file.getName(), image);
        // Mark that the new data was received as the last statement to ensure that no failures can follow after that
        // (to avoid infinite failure mode).
        newDataReceived = true;
      }
    }
  }

  private Optional<String> toOptionalString(Optional<byte[]> optionalBytes) {
    return optionalBytes.isPresent()
           ? Optional.of(new String(optionalBytes.get()))
           : Optional.<String>absent();
  }
}
