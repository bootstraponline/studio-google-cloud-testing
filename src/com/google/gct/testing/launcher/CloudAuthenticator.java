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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.test.Test;
import com.google.api.services.test.model.AndroidDeviceCatalog;
import com.google.api.services.toolresults.Toolresults;
import com.google.gct.login.GoogleLogin;
import com.google.gct.testing.GoogleCloudTestingUtils;

public class CloudAuthenticator {

  /** Global instance of the HTTP transport. */
  private static HttpTransport httpTransport;

  private static Credential credential;

  private static Storage storage;

  private static Test test;

  private static Toolresults toolresults;

  private static long lastDiscoveryServiceInvocationTimestamp = -1;


  public static Storage getStorage() {
    prepareCredential();
    if (storage == null) {
      storage = new Storage.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).build();
    }
    return storage;
  }

  public static void recreateTestAndToolResults(String testBackendUrl, String toolResultsBackendUrl) {
    prepareCredential();
    test = new Test.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setRootUrl(testBackendUrl).build();
    toolresults =
      new Toolresults.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setRootUrl(toolResultsBackendUrl).build();
  }

  public static Test getTest() {
    prepareCredential();
    if (test == null) {
      //TODO: Update the client lib for Test API and use the default URL (i.e., no need to set it explicitly to prod here).
      test = new Test.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential)
        //.setRootUrl("http://snegara0.mtv.corp.google.com:8788") // final distributed local
        //.setRootUrl("http://snegara0.mtv.corp.google.com:8787/test") // classic local
        .setRootUrl("https://test-devtools.googleapis.com") // final distributed prod
        //.setRootUrl("https://test.googleapis.com") // initial distributed prod
        //.setRootUrl("https://www.googleapis.com/test") // classic prod
        //.setRootUrl("https://staging-test-devtools.sandbox.google.com") // final distributed staging
        //.setRootUrl("https://test-staging.sandbox.google.com") // initial distributed staging
        //.setRootUrl("https://www-googleapis-staging.sandbox.google.com/test") // classic staging
        //.setRootUrl("https://test-test-devtools.googleapis.com") // final distributed test
        //.setRootUrl("https://test-test.sandbox.googleapis.com") // initial distributed test
        //.setRootUrl("https://www-googleapis-test.sandbox.google.com/test") // classic test
        .build();
    }
    return test;
  }

  public static AndroidDeviceCatalog getAndroidDeviceCatalog() {
    long currentTimestamp = System.currentTimeMillis();
    try {
      AndroidDeviceCatalog catalog = getTest().testEnvironmentCatalog().get("ANDROID").execute().getAndroidDeviceCatalog();
      if (catalog.getVersions().isEmpty() || catalog.getModels().isEmpty() || catalog.getRuntimeConfiguration().getLocales().isEmpty()
        || catalog.getRuntimeConfiguration().getOrientations().isEmpty()) {
        showDeviceCatalogError("Android device catalog is empty for some dimensions", currentTimestamp);
      }
      return catalog;
    }
    catch (Exception e) {
      showDeviceCatalogError("Exception while getting Android device catalog\n\n" + e.getMessage(), currentTimestamp);
      return null;
    } finally {
      lastDiscoveryServiceInvocationTimestamp = currentTimestamp;
    }
  }

  private static void showDeviceCatalogError(String errorMessageSuffix, long currentTimestamp) {
    // The error should be reported just once per burst of invocations.
    if (currentTimestamp - lastDiscoveryServiceInvocationTimestamp > 1000l) { // If more than a second has passed.
      GoogleCloudTestingUtils.showErrorMessage(null, "Error retrieving android device catalog",
                                               "Failed to retrieve available cloud devices! Please try again later.\n" +
                                               errorMessageSuffix);
    }
  }

  public static Toolresults getToolresults() {
    prepareCredential();
    if (toolresults == null) {
      toolresults = new Toolresults.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).build();
    }
    return toolresults;
  }

  private static void prepareCredential() {
    if (httpTransport == null) {
      httpTransport = createHttpTransport();
    }
    if (credential == null) {
      if (!authorize()) {
        throw new RuntimeException("Failed to authorize in Google Cloud!");
      }
      credential = GoogleLogin.getInstance().getCredential();
    }
  }

  private static HttpTransport createHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      System.err.println(e.getMessage());
      throw new RuntimeException("Failed to acquire HTTP transport for Google Cloud Storage!");
    }
  }

  /**
   * Authorizes the installed application to access user's protected data.
   */
  public static boolean authorize() {
    final GoogleLogin googleLogin = GoogleLogin.getInstance();
    Credential credential = googleLogin.getCredential();
    if (credential == null) {
      googleLogin.logIn();
      credential = googleLogin.getCredential();
      if (credential == null) {
        return false;
      }
    }
    return true;
  }

}
