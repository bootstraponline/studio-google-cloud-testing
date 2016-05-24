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
package com.google.gct.testing.launcher;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.StorageObject;
import com.google.api.services.testing.model.*;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.gct.testing.CloudConfigurationImpl;
import com.google.gct.testing.CloudTestingUtils;
import com.google.gct.testing.dimension.CloudTestingType;
import com.intellij.openapi.ui.Messages;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.gct.testing.CloudTestingUtils.ANDROID_STUDIO_URL_FLAG;
import static com.google.gct.testing.launcher.CloudAuthenticator.getStorage;
import static com.google.gct.testing.launcher.CloudAuthenticator.getTest;


public class CloudTestsLauncher {

  //public static final String TEST_RUNNER_CLASS = "com.google.android.apps.common.testing.testrunner.GoogleInstrumentationTestRunner";
  public static final String TEST_RUNNER_CLASS = "android.support.test.runner.AndroidJUnitRunner";

  private static final Function<CloudTestingType, String> TO_CLOUD_TESTING_TYPE_IDS = new Function<CloudTestingType, String>() {
    @Override
    public String apply(CloudTestingType type) {
      return type.getId();
    }
  };

  public CloudTestsLauncher() {
  }

  public static Bucket createBucket(String projectId, String bucketName) {
    try {
      Bucket bucket = new Bucket().setName(bucketName).setLocation("US");
      Storage.Buckets.Insert insertBucket = getStorage().buckets().insert(projectId, bucket);
      return insertBucket.execute();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns {@code StorageObject} for the uploaded file (i.e., the file in the bucket).
   */
  public static StorageObject uploadFile(String bucketName, File file) {
    InputStreamContent mediaContent = null;
    try {
      mediaContent = new InputStreamContent("application/octet-stream", new FileInputStream(file));
    }
    catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

    // Setting the size of the uploaded file is extremely important! It reduces upload times by two orders of magnitude!
    mediaContent.setLength(file.length());

    try {
      Storage.Objects.Insert insertObject = getStorage().objects().insert(bucketName, null, mediaContent);

      // If you don't provide metadata, you will have specify the object
      // name by parameter. You will probably also want to ensure that your
      // default object ACLs (a bucket property) are set appropriately:
      // https://developers.google.com/storage/docs/json_api/v1/buckets#defaultObjectAcl
      insertObject.setName(file.getName());

      return insertObject.execute();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String removeTrailingSlash(String s) {
    return s.endsWith("/")
           ? s.substring(0, s.length() - 1)
           : s;
  }

  /**
   * Returns the triggered test matrix or {@code null} if the attempt was unsuccessful.
   */
  public static @Nullable TestMatrix triggerTestApi(
    String cloudProjectId, String bucketGcsPath, String appApkGcsPath, String testApkGcsPath, String testSpecification,
    String instrumentationTestRunner, CloudConfigurationImpl cloudTestConfiguration, String appPackage, String testPackage) {

    TestMatrix testMatrix = new TestMatrix();

    testMatrix.setClientInfo(new ClientInfo().setName("Android Studio"));

    final String testTimeout = "1800s"; // 30 minutes in seconds

    testMatrix.setTestSpecification(
      new TestSpecification().setTestTimeout(testTimeout).setAndroidInstrumentationTest(
        new AndroidInstrumentationTest().setAppApk(new FileReference().setGcsPath(appApkGcsPath))
          .setTestApk(new FileReference().setGcsPath(testApkGcsPath)).setAppPackageId(appPackage).setTestPackageId(testPackage)
          .setTestRunnerClass(instrumentationTestRunner).setTestTargets(Lists.newArrayList(testSpecification))));

    testMatrix.setResultStorage(new ResultStorage().setGoogleCloudStorage(new GoogleCloudStorage().setGcsPath(bucketGcsPath)));

    AndroidMatrix androidMatrix = new AndroidMatrix();

    androidMatrix.setAndroidModelIds(
      Lists.transform(cloudTestConfiguration.getDeviceDimension().getEnabledTypes(), TO_CLOUD_TESTING_TYPE_IDS));

    androidMatrix.setAndroidVersionIds(
      Lists.transform(cloudTestConfiguration.getApiDimension().getEnabledTypes(), TO_CLOUD_TESTING_TYPE_IDS));

    androidMatrix.setLocales(
      Lists.transform(cloudTestConfiguration.getLanguageDimension().getEnabledTypes(), TO_CLOUD_TESTING_TYPE_IDS));

    androidMatrix.setOrientations(
      Lists.transform(cloudTestConfiguration.getOrientationDimension().getEnabledTypes(), TO_CLOUD_TESTING_TYPE_IDS));

    testMatrix.setEnvironmentMatrix(new EnvironmentMatrix().setAndroidMatrix(androidMatrix));

    TestMatrix triggeredTestMatrix = null;
    try {
      triggeredTestMatrix = getTest().projects().testMatrices().create(cloudProjectId, testMatrix).execute();
    } catch (Exception e) {
      String exceptionMessage = e.getMessage();
      String backendMessageHeader = "\"message\" : \"";
      int indexOfBackendMessage = exceptionMessage.indexOf(backendMessageHeader);
      if (indexOfBackendMessage != -1) {
        int startOfBackendMessageText = indexOfBackendMessage + backendMessageHeader.length();
        final String message = exceptionMessage.substring(startOfBackendMessageText, exceptionMessage.indexOf("\",", startOfBackendMessageText));
        if (message.contains("is not registered for Firebase Test Lab")) {
          String urlPrefix = "Please visit: ";
          int urlPrefixIndex = message.indexOf(urlPrefix);
          if (urlPrefixIndex != -1) {
            int urlIndex = urlPrefixIndex + urlPrefix.length();
            String url = message.substring(urlIndex);
            final String userMessage = "<html>" + message.substring(0, urlPrefixIndex) + "<br>" +
                                       message.substring(urlPrefixIndex, urlIndex) + "<a href='" + url + ANDROID_STUDIO_URL_FLAG + "'>" +
                                       url + "</a></html>";
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                Messages.showDialog(userMessage, "Project not registered", new String[]{Messages.OK_BUTTON}, 0, null);
              }
            });
            return null;
          }
        }
      }
      CloudTestingUtils.showErrorMessage(null, "Error triggering a matrix test", "Failed to trigger a firebase matrix execution!\n" +
                                                                                 "Exception while triggering a matrix execution\n\n" +
                                                                                 exceptionMessage);
    }
    return triggeredTestMatrix;
  }

  /**
   * Not used, left as an example.
   */
  public static void triggerJenkinsJob(
    String jenkinsUrl, String cloudProjectId, String applicationName, String bucketName, String testSpecification, String matrixFilter,
    String appPackage, String testPackage) {

    String gsBucketName = "gs://" + bucketName;

    String json = "{\"parameter\": " +
               "[ " +
               "{\"name\": \"CLOUD_PROJECT\",      \"value\": \"" + cloudProjectId + "\"}, " +
               "{\"name\": \"APPLICATION\",        \"value\": \"" + applicationName + "\"}, " +
               "{\"name\": \"BUCKET\",             \"value\": \"" + gsBucketName + "\"}, " +
               "{\"name\": \"APP_PACKAGE_ID\",     \"value\": \"" + appPackage + "\"}, " +
               "{\"name\": \"TEST_PACKAGE_ID\",    \"value\": \"" + testPackage + "\"}, " +
               "{\"name\": \"TEST_SPECIFICATION\", \"value\": \"" + testSpecification + "\"}, " +
               "{\"name\": \"TEST_RUNNER_CLASS\",  \"value\": \"" + TEST_RUNNER_CLASS + "\"}, " +
               "{\"name\": \"FILTER\",             \"value\": \"" + matrixFilter + "\"}" +
               "], " +
               "}";

    sendPostRequest(removeTrailingSlash(jenkinsUrl) + "/job/matrix-test-multi/build", json);
  }

  public static void sendPostRequest(String targetURL, String json) {
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      List<NameValuePair> params = new ArrayList<NameValuePair>();
      params.add(new BasicNameValuePair("json", json));

      HttpPost request = new HttpPost(targetURL);
      request.addHeader("content-type", "application/x-www-form-urlencoded");
      request.setEntity(new UrlEncodedFormEntity(params));
      CloseableHttpResponse response = httpClient.execute(request);
      //response.getEntity().writeTo(System.out);
      // handle response here...
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    } finally {
      try {
        httpClient.close();
      }
      catch (IOException e) {
        // ignore
      }
    }
  }

  /**
   * Obsolete...
   */
  public static void main(String str[]) {
    if (str.length != 10) {
      System.out.println("Please provide 10 arguments: " +
                         "application name, " +
                         "GCE project name, " +
                         "Jenkins URL, " +
                         "project ID, " +
                         "app package, " +
                         "test package, " +
                         "testSpecification, " +
                         "matrixFilter, " +
                         "path to debug APK, " +
                         "path to debug test APK.");
      return;
    }
    String applicationName = str[0];
    String cloudProjectName = str[1];
    String jenkinsUrl = str[2];
    String projectId = str[3];
    String appPackage = str[4];
    String testPackage = str[5];
    String testSpecification = str[6];
    String matrixFilter = str[7];
    String debugApkPath = str[8];
    String debugTestApkPath = str[9];
    String bucketName = "build-" + applicationName.toLowerCase() + "-" + System.currentTimeMillis();

    CloudTestsLauncher launcher = new CloudTestsLauncher();
    System.out.println("Creating Cloud Storage bucket " + bucketName);
    launcher.createBucket(projectId, bucketName);
    System.out.println("Uploading debug APK...");
    launcher.uploadFile(bucketName, new File(debugApkPath));
    System.out.println("Uploading test APK...");
    launcher.uploadFile(bucketName, new File(debugTestApkPath));
    System.out.println("Triggering Jenkins matrix test...");
    launcher.triggerJenkinsJob(jenkinsUrl, cloudProjectName, applicationName, bucketName, testSpecification, matrixFilter, appPackage,
                               testPackage);
  }
}
