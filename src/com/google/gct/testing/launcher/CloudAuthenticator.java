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
import com.google.api.services.testing.Testing;
import com.google.api.services.testing.model.AndroidDeviceCatalog;
import com.google.api.services.toolresults.Toolresults;
import com.google.gct.login.GoogleLogin;
import com.google.gct.testing.CloudTestingUtils;

public class CloudAuthenticator {

  private static final String APPLICATION_NAME = "GCTL";

  /** Global instance of the HTTP transport. */
  private static HttpTransport httpTransport;

  private static Credential credential;

  private static Storage storage;

  private static Testing test;

  private static Toolresults toolresults;

  private static long lastDiscoveryServiceInvocationTimestamp = -1;


  public static Storage getPublicStorage() {
    if (httpTransport == null) {
      httpTransport = createHttpTransport();
    }
    // A storage accessible to anyone without authentication and authorization (null credential).
    return new Storage.Builder(httpTransport, JacksonFactory.getDefaultInstance(), null).setApplicationName(APPLICATION_NAME).build();
  }

  public static Storage getStorage() {
    prepareCredential();
    if (storage == null) {
      storage =
        new Storage.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME).build();
    }
    return storage;
  }

  public static void recreateTestAndToolResults(String testBackendUrl, String toolResultsBackendUrl) {
    prepareCredential();
    test =
      new Testing.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME)
        .setRootUrl(testBackendUrl).build();
    toolresults =
      new Toolresults.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME)
        .setRootUrl(toolResultsBackendUrl).build();
  }

  public static Testing getTest() {
    prepareCredential();
    if (test == null) {
      test =
        new Testing.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME).build();
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
      CloudTestingUtils.showErrorMessage(null, "Error retrieving android device catalog",
                                         "Failed to retrieve available cloud devices! Please try again later.\n" + errorMessageSuffix);
    }
  }

  public static Toolresults getToolresults() {
    prepareCredential();
    if (toolresults == null) {
      toolresults =
        new Toolresults.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME)
          .build();
    }
    return toolresults;
  }

  public static void prepareCredential() {
    if (httpTransport == null) {
      httpTransport = createHttpTransport();
    }
    if (credential == null) {
      if (!authorize()) {
        throw new RuntimeException("Failed to authorize to Google Cloud!");
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

  public static boolean isUserLoggedIn() {
    return GoogleLogin.getInstance().getCredential() != null;
  }

}
