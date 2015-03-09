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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ConfigurationResult {

  public static final String SCREENSHOT_FILENAME_DELIMITER = "-";

  private final ConfigurationInstance configurationInstance;

  private final String bucketName;

  private Optional<String> result = Optional.absent();

  private final Map<String, BucketFileMetadata> fileNameToScreenshotMetadata = new HashMap<String, BucketFileMetadata>();

  private final Map<ScreenshotKey, String> screenshotKeyToFileName = new HashMap<ScreenshotKey, String>();


  //TODO: Consider unifying the state into a single enum value.
  private boolean isComplete;
  private boolean isInfrastructureFailure;
  private boolean isTriggeringError;

  private final List<ConfigurationResultListener> listeners = new LinkedList<ConfigurationResultListener>();


  public ConfigurationResult(String encodedConfigurationString, String bucketName) {
    configurationInstance = ConfigurationInstance.parseFromEncodedString(encodedConfigurationString);
    this.bucketName = bucketName;
  }

  public boolean hasResult() {
    return result.isPresent();
  }

  public String getResult() {
    return result.get();
  }

  public void setResult(String result) {
    this.result = Optional.of(result);
  }

  public ConfigurationInstance getConfigurationInstance() {
    return configurationInstance;
  }

  public void setComplete(boolean complete) {
    isComplete = complete;
  }

  public boolean isComplete() {
    return isComplete;
  }

  public void setInfrastructureFailure(boolean infrastructureFailure) {
    isInfrastructureFailure = infrastructureFailure;
  }

  public boolean isInfrastructureFailure() {
    return isInfrastructureFailure;
  }

  public void setTriggeringError(boolean triggeringError) {
    isTriggeringError = triggeringError;
  }

  public boolean isTriggeringError() {
    return isTriggeringError;
  }

  public boolean isNoProgressExpected() {
    return isComplete() || isInfrastructureFailure() || isTriggeringError();
  }

  public void addScreenshotMetadata(String fileName, BucketFileMetadata fileMetadata) {
    fileNameToScreenshotMetadata.put(fileName, fileMetadata);
    screenshotKeyToFileName.put(getScreenshotKey(fileName), fileName);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        for (ConfigurationResultListener listener : listeners) {
          listener.screenshotsUpdated();
        }
      }
    });
  }

  private static ScreenshotKey getScreenshotKey(String fileName) {
    String[] fileNameParts = getFileNameParts(fileName);
    String className = fileNameParts[0];
    String methodName = fileNameParts[1];
    String step = fileNameParts[3].substring(0, fileNameParts[3].indexOf("."));
    return new ScreenshotKey(className, methodName, step);
  }

  private static String[] getFileNameParts(String fileName) {
    String[] originalNameParts;
    try {
      originalNameParts = URLDecoder.decode(fileName, "UTF-8").split(SCREENSHOT_FILENAME_DELIMITER);
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Unsupported encoding!", e);
    }
    if (originalNameParts.length < 4) {
      System.out.println("Screenshot file name is not formatted properly: " + fileName);
      // To tolerate such scenarios, use some dummy file name parts.
      String[] nameParts = new String[4];
      nameParts[0] = "dummyTestClass";
      nameParts[1] = "dummyTestMethod";
      nameParts[2] = "dummyScreenshotName";
      nameParts[3] = "1.jpg"; //step
      return nameParts;

    }
    // Since a screenshot name may contain screenshot filename delimiter, concatenate the corresponding parts.
    String[] nameParts = new String[4];
    nameParts[0] = originalNameParts[0];
    nameParts[1] = originalNameParts[1];
    nameParts[2] = originalNameParts[2];
    for (int i = 3; i < originalNameParts.length - 1; i++) {
      nameParts[2] += "-" + originalNameParts[i];
    }
    nameParts[3] = originalNameParts[originalNameParts.length - 1];
    return nameParts;
  }

  public Map<String, BucketFileMetadata> getScreenshotMetadata() {
    return fileNameToScreenshotMetadata;
  }

  public BufferedImage getScreenshotForTestAndStep(TestName testName, int step) {
    String fileName = screenshotKeyToFileName.get(new ScreenshotKey(testName, step));

    BufferedImage image = null;
    BucketFileMetadata fileMetadata = fileNameToScreenshotMetadata.get(fileName);
    if (fileMetadata == null) {
      return null;
    }
    Optional<byte[]> optionalFileBytes = CloudResultsLoader.getFileBytes(bucketName, fileMetadata);
    if (optionalFileBytes.isPresent()) {
      try {
        image = ImageIO.read(new ByteArrayInputStream(optionalFileBytes.get()));
      }
      catch (IOException e) {
        System.out.println("Failed to create an image for screenshot: " + e.getMessage());
        return null;
      }
      image.flush();
    }
    return image;
  }

  public String getScreenshotNameForTestAndStep(TestName testName, int step) {
    String fileName = screenshotKeyToFileName.get(new ScreenshotKey(testName, step));
    return fileName == null ? "" : getScreenshotName(fileName);
  }

  private String getScreenshotName(String fileName) {
    String[] fileNameParts = getFileNameParts(fileName);
    return fileNameParts[2];
  }

  public int maxScreenshotStep(final TestName testName) {
    if (fileNameToScreenshotMetadata.isEmpty()) {
      return 0;
    }
    return Ordering.natural().max(Iterables.transform(fileNameToScreenshotMetadata.keySet(), new Function<String, Integer>() {
      @Override
      public Integer apply(String fileName) {
        String[] fileNameParts = getFileNameParts(fileName);
        if (testName.getClassName().equals(fileNameParts[0]) && testName.getMethodName().equals(fileNameParts[1])) {
          return Integer.parseInt(fileNameParts[3].substring(0, fileNameParts[3].indexOf(".")));
        }
        return 0;
      }
    }));
  }

  public void addConfigurationResultListener(ConfigurationResultListener listener) {
    listeners.add(listener);
  }

  public boolean removeConfigurationResultListener(ConfigurationResultListener listener) {
    return listeners.remove(listener);
  }

  @Override
  public String toString() {
    return "ConfigurationResult{" +
           "configurationInstance='" + configurationInstance.getResultsViewerDisplayString() + '\'' +
           ", result=" + result +
           ", isComplete=" + isComplete +
           '}';
  }

  private static class ScreenshotKey {
    private final String className;
    private final String methodName;
    private final int step;

    private ScreenshotKey(String className, String methodName, String step) {
      this.className = className;
      this.methodName = methodName;
      this.step = Integer.parseInt(step);
    }

    private ScreenshotKey(TestName testName, int step) {
      this.className = testName.getClassName();
      this.methodName = testName.getMethodName();
      this.step = step;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ScreenshotKey that = (ScreenshotKey)o;

      if (step != that.step) return false;
      if (className != null ? !className.equals(that.className) : that.className != null) return false;
      if (methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) return false;

      return true;
    }

    @Override
    public String toString() {
      return "ScreenshotKey{" +
             "className='" + className + '\'' +
             ", methodName='" + methodName + '\'' +
             ", step=" + step +
             '}';
    }

    @Override
    public int hashCode() {
      int result = className != null ? className.hashCode() : 0;
      result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
      result = 31 * result + step;
      return result;
    }
  }

  //TODO: Remove this and add the corresponding test.
  public static void main(String[] args) {
    System.out.println(getScreenshotKey("com.google.android.bootcamp.memegen.test.MemegenUITest%23testCreateMeme%23creatememe%231.bmp"));
    System.out.println(getScreenshotKey("com.google.android.bootcamp.memegen.test.MemegenUITest%23testCreateMeme%23create+meme%231.bmp"));
    System.out.println(getScreenshotKey("com.google.android.bootcamp.memegen.test.MemegenUITest#testCreateMeme#create meme#1.bmp"));
  }
}
