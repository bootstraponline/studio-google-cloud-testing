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
import com.google.api.services.testing.model.AndroidDevice;
import com.google.api.services.testing.model.TestExecution;
import com.google.api.services.testing.model.TestMatrix;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gct.testing.results.IGoogleCloudTestRunListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static com.google.gct.testing.BucketFileMetadata.Type.*;
import static com.google.gct.testing.launcher.CloudAuthenticator.getStorage;
import static com.google.gct.testing.launcher.CloudAuthenticator.getTest;

public class CloudResultsLoader {
  public static final String INFRASTRUCTURE_FAILURE_PREFIX = "Infrastructure Failure:";

  private static final long MAX_SCREENSHOT_DOWNLOAD_SIZE = 512 * 1024 * 1024; // 512 MB

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

  // Is used to support fake buckets only (does not handle cumulative progress).
  private final Function<BucketFileMetadata, BucketFileMetadata> UPDATE_CONFIGURATION_PROGRESS =
    new Function<BucketFileMetadata, BucketFileMetadata>() {
    @Override
    public BucketFileMetadata apply(BucketFileMetadata file) {
      if (file.getType() == PROGRESS) {
        Optional<byte[]> optionalBytes = getFileBytes(bucketName, file);
        if (optionalBytes.isPresent()) {
          String progressLine = new String(optionalBytes.get());
          String encodedConfigurationInstance = file.getEncodedConfigurationInstance();
          String previousProgressLine = getPreviousProgress(encodedConfigurationInstance);
          if (!progressLine.equals(previousProgressLine)) {
            newDataReceived = true;
            configurationProgress.put(encodedConfigurationInstance, progressLine);
            testRunListener.testConfigurationProgress(
              ConfigurationInstance.parseFromEncodedString(encodedConfigurationInstance).getResultsViewerDisplayString(), progressLine);
          }
        }
      }
      return file;
    }
  };

  private final String cloudProjectId;
  private final IGoogleCloudTestRunListener testRunListener;
  private final String bucketName;
  private final String testMatrixId;
  private final Set<String> allConfigurationInstances = new HashSet<String>();
  private final Set<String> finishedConfigurationInstances = new HashSet<String>();
  private long loadedScreenshotSize = 0;
  private int consecutivePollFailuresCount = 0;

  // Encoded configuration instance -> progress accumulated so far.
  private final Map<String, String> configurationProgress = new HashMap<String, String>();


  public CloudResultsLoader(String cloudProjectId, IGoogleCloudTestRunListener testRunListener, String bucketName, TestMatrix testMatrix) {
    this.cloudProjectId = cloudProjectId;
    this.testRunListener = testRunListener;
    this.bucketName = bucketName;
    // testMatrix is null for runs with a fake bucket.
    if (testMatrix != null) {
      testMatrixId = testMatrix.getTestMatrixId();
      for (TestExecution testExecution : testMatrix.getTestExecutions()) {
        allConfigurationInstances.add(getEncodedConfigurationNameForTestExecution(testExecution));
      }
    } else {
      testMatrixId = null;
    }
  }

  //TODO: Check file size after loading it and load the missing parts, if any (i.e., keep loading until the file's size does not change).
  public static Optional<byte[]> getFileBytes(String bucketName, BucketFileMetadata fileMetadata) {
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
      if (testMatrixId == null) { // The obsolete logic kept for handling fake buckets.
        Storage.Objects.List objects = getStorage().objects().list(bucketName);
        List<StorageObject> storageObjects = objects.execute().getItems();

        Iterable<BucketFileMetadata> files =
          Iterables.transform(storageObjects, Functions.compose(UPDATE_CONFIGURATION_PROGRESS, TO_BUCKET_FILE));

        Set<String> finishedConfigurations =
          Sets.newHashSet(Iterables.filter(Iterables.transform(files, TO_COMPLETED_CONFIGURATION_OR_NULL), Predicates.notNull()));

        for (BucketFileMetadata file : files) {
          if (file.getType() == PROGRESS) {
            String encodedConfigurationInstance = file.getEncodedConfigurationInstance();
            ConfigurationResult result = getOrCreateConfigurationResult(encodedConfigurationInstance, results);
            result.setComplete(finishedConfigurations.contains(encodedConfigurationInstance));
            result.setInfrastructureFailure(isInfrastructureFailure(getPreviousProgress(encodedConfigurationInstance)));
          }
        }
      } else {
        updateResultsFromApi(results);
      }
      loadResultFiles(results);
      loadScreenshots(results);
    } catch (Exception e) {
      throw new RuntimeException("Failed updating the results from the bucket!", e);
    }
    return newDataReceived;
  }

  private void updateResultsFromApi(Map<String, ConfigurationResult> results) {
    TestMatrix testMatrix = null;
    try {
       testMatrix = getTest().projects().testMatrices().get(cloudProjectId, testMatrixId).execute();
    } catch (Exception e) {
      if (consecutivePollFailuresCount == 2) { // Give up on the 3rd failure in a row.
        for (String configurationInstance : allConfigurationInstances) {
          if (!finishedConfigurationInstances.contains(configurationInstance)) {
            ConfigurationResult result = getOrCreateConfigurationResult(configurationInstance, results);
            result.setInfrastructureFailure(true);
            finishedConfigurationInstances.add(configurationInstance);
          }
        }
        CloudTestingUtils
          .showErrorMessage(null, "Error retrieving matrix test results", "Failed to retrieve results of a cloud test matrix!\n" +
                                                                          "Exception while updating results for test matrix " +
                                                                          testMatrixId +
                                                                          "\n\n" +
                                                                          e.getMessage());
      } else {
        consecutivePollFailuresCount++;
      }
    }
    if (testMatrix != null) {
      updateResultsFromTestMatrix(results, testMatrix);
      consecutivePollFailuresCount = 0; // Reset the consecutive poll failures on every success.
    }
  }

  private void updateResultsFromTestMatrix(Map<String, ConfigurationResult> results, @NotNull TestMatrix testMatrix) {
    for (TestExecution testExecution : testMatrix.getTestExecutions()) {
      String encodedConfigurationInstance = getEncodedConfigurationNameForTestExecution(testExecution);
      if (finishedConfigurationInstances.contains(encodedConfigurationInstance)) {
        continue;
      }
      String testExecutionState = testExecution.getState();
      if (testExecutionState.equals("UNSUPPORTED_ENVIRONMENT")) {
        String diffProgress = "Skipped triggering the test execution: Incompatible API level for requested model\n";
        // Probably, no previous progress could be expected in this scenario, but it would not hurt considering it anyway.
        String previousProgress = getPreviousProgress(encodedConfigurationInstance);
        reportNewProgress(encodedConfigurationInstance, previousProgress, previousProgress + diffProgress);
        ConfigurationResult result = getOrCreateConfigurationResult(encodedConfigurationInstance, results);
        result.setTriggeringError(true);
        finishedConfigurationInstances.add(encodedConfigurationInstance);
      } else if (!testExecutionState.equals("QUEUED")) {
        if (testExecutionState.equals("ERROR")) {
          String diffProgress = INFRASTRUCTURE_FAILURE_PREFIX + " " + testExecution.getTestDetails().getErrorDetails() + "\n";
          String previousProgress = getPreviousProgress(encodedConfigurationInstance);
          if (!previousProgress.endsWith(diffProgress)) {
            reportNewProgress(encodedConfigurationInstance, previousProgress, previousProgress + diffProgress);
          }
        } else if (testExecutionState.equals("IN_PROGRESS")) {
          String newProgress = testExecution.getTestDetails().getProgressDetails();
          String previousProgress = getPreviousProgress(encodedConfigurationInstance);
          if (newProgress != null && !newProgress.equals(previousProgress)) {
            reportNewProgress(encodedConfigurationInstance, previousProgress, newProgress);
          }
        }
        ConfigurationResult result = getOrCreateConfigurationResult(encodedConfigurationInstance, results);
        result.setComplete(testExecutionState.equals("FINISHED"));
        result.setInfrastructureFailure(isInfrastructureFailure(getPreviousProgress(encodedConfigurationInstance)));
        if (result.isNoProgressExpected()) {
          finishedConfigurationInstances.add(encodedConfigurationInstance);
        }
      }
    }
  }

  private String getEncodedConfigurationNameForTestExecution(TestExecution testExecution) {
    AndroidDevice androidDevice = testExecution.getEnvironment().getAndroidDevice();
    return androidDevice.getAndroidModelId() + ConfigurationInstance.ENCODED_NAME_DELIMITER
           + androidDevice.getAndroidVersionId() + ConfigurationInstance.ENCODED_NAME_DELIMITER
           + androidDevice.getLocale() + ConfigurationInstance.ENCODED_NAME_DELIMITER
           + androidDevice.getOrientation();
  }

  private boolean failedToTriggerTest(String testExecutionId) {
    return testExecutionId.startsWith("Error ") || testExecutionId.startsWith("Skipped ");
  }

  private void reportNewProgress(String encodedConfigurationInstance, String previousProgress, String newProgress) {
    newDataReceived = true;
    configurationProgress.put(encodedConfigurationInstance, newProgress);
    String diffProgress = newProgress.substring(previousProgress.length());
    testRunListener.testConfigurationProgress(
      ConfigurationInstance.parseFromEncodedString(encodedConfigurationInstance).getResultsViewerDisplayString(), diffProgress);
  }

  private ConfigurationResult getOrCreateConfigurationResult(String encodedConfigurationInstance,
                                                             Map<String, ConfigurationResult> results) {

    ConfigurationResult result = results.get(encodedConfigurationInstance);
    if (result == null) {
      result = new ConfigurationResult(encodedConfigurationInstance, bucketName);
      results.put(encodedConfigurationInstance, result);
    }
    return result;
  }

  private static boolean isInfrastructureFailure(String progress) {
    String[] progressLines = progress.split("\n");
    return progressLines[progressLines.length - 1].startsWith(INFRASTRUCTURE_FAILURE_PREFIX);
  }

  private String getPreviousProgress(String encodedConfigurationInstance) {
    String progress = configurationProgress.get(encodedConfigurationInstance);
    return progress == null ? "" : progress;
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
            Optional<String> optionalResult = toOptionalString(getFileBytes(bucketName, file));
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
    if (loadedScreenshotSize > MAX_SCREENSHOT_DOWNLOAD_SIZE) {
      return;
    }
    List<StorageObject> storageObjects = null;
    try {
      Storage.Objects.List objects = getStorage().objects().list(bucketName);
      storageObjects = objects.execute().getItems();
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to retrieve bucket objects: ", e);
    }

    Iterable<BucketFileMetadata> files = Iterables.transform(storageObjects, TO_BUCKET_FILE);
    //ArrayList<ScreenshotDownloadThread> downloadThreads = new ArrayList<ScreenshotDownloadThread>();
    for (BucketFileMetadata file : files) {
      if (file.getType() == SCREENSHOT && !isIgnoredScreenshot(file)) {
        ConfigurationResult result = results.get(file.getEncodedConfigurationInstance());
        if (result != null && result.getScreenshotMetadata().get(file.getName()) == null) {
          result.addScreenshotMetadata(file.getName(), file);
          newDataReceived = true;
        }
      }
    }

    // TODO: Replace with a pool of worker threads.
    //final int capParallelThreads = 10;
    //int currentParallelThreads = 0;
    //for (int i = 0; i < downloadThreads.size(); i++) {
    //  if (loadedScreenshotSize > MAX_SCREENSHOT_DOWNLOAD_SIZE) {
    //    joinCurrentParallelThreads(downloadThreads, currentParallelThreads, i);
    //    return;
    //  }
    //  ScreenshotDownloadThread newDownloadThread = downloadThreads.get(i);
    //  loadedScreenshotSize += newDownloadThread.getFileSize();
    //  newDownloadThread.start();
    //  currentParallelThreads++;
    //  if (currentParallelThreads == capParallelThreads) {
    //    // Join the existing threads before proceeding to avoid going over the limit.
    //    joinCurrentParallelThreads(downloadThreads, currentParallelThreads, i);
    //    currentParallelThreads = 0;
    //  }
    //}
    //
    //// Join any remaining threads.
    //for (int i = downloadThreads.size() - currentParallelThreads; i < downloadThreads.size(); i++){
    //  try {
    //    downloadThreads.get(i).join();
    //  }
    //  catch (InterruptedException e) {
    //    //ignore
    //  }
    //}
  }

  private boolean isIgnoredScreenshot(BucketFileMetadata file) {
    return file.getPath().contains("/flipbook/") // Ignore video screenshots (they are stored in flipbook subfolder).
           || file.getName().startsWith("TestRunner-prepareVirtualDevice-beforeunlock-") // Ignore screenshot that we take before unlocking.
           || file.getName().startsWith("TestRunner-prepareVirtualDevice-afterunlock-"); // Ignore screenshot that we take after unlocking.
  }

  //private void joinCurrentParallelThreads(ArrayList<ScreenshotDownloadThread> downloadThreads, int currentParallelThreads,
  //                                        int lastStartedThreadIndex) {
  //  for (int j = lastStartedThreadIndex - currentParallelThreads + 1; j <= lastStartedThreadIndex; j++){
  //    try {
  //      downloadThreads.get(j).join();
  //    }
  //    catch (InterruptedException e) {
  //      //ignore
  //    }
  //  }
  //}

  //private class ScreenshotDownloadThread extends Thread {
  //
  //  private final BucketFileMetadata file;
  //  private final ConfigurationResult result;
  //
  //  private ScreenshotDownloadThread(BucketFileMetadata file, ConfigurationResult result) {
  //    this.file = file;
  //    this.result = result;
  //  }
  //
  //  public long getFileSize() {
  //    try {
  //      return getStorage().objects().get(bucketName, file.getPath()).executeCloudMatrixTests().getSize().longValue();
  //    }
  //    catch (IOException e) {
  //      System.err.println("Failed to estimate a cloud file size: " + file.getName());
  //      return 0;
  //    }
  //  }
  //
  //  @Override
  //  public void run() {
  //    Optional<byte[]> optionalFileBytes = getFileBytes(file);
  //    if (optionalFileBytes.isPresent()) {
  //      BufferedImage image = null;
  //      try {
  //        image = ImageIO.read(new ByteArrayInputStream(optionalFileBytes.get()));
  //      }
  //      catch (IOException e) {
  //        System.out.println("Failed to create an image for screenshot: " + e.getMessage());
  //        return;
  //      }
  //      image.flush();
  //      result.addScreenshotMetadata(file.getName(), image);
  //      // Mark that the new data was received as the last statement to ensure that no failures can follow after that
  //      // (to avoid infinite failure mode).
  //      newDataReceived = true;
  //    }
  //  }
  //}

  private Optional<String> toOptionalString(Optional<byte[]> optionalBytes) {
    return optionalBytes.isPresent()
           ? Optional.of(new String(optionalBytes.get()))
           : Optional.<String>absent();
  }
}
