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
import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;
import com.google.api.services.storage.Storage;
import com.google.api.services.testing.Testing;
import com.google.api.services.testing.model.AndroidDeviceCatalog;
import com.google.api.services.toolresults.Toolresults;
import com.google.gct.login.GoogleLogin;
import com.google.gct.testing.CloudTestingUtils;
import org.jetbrains.annotations.NotNull;

public class CloudAuthenticator {

  private static final String APPLICATION_NAME = "GCTL";

  private static CloudAuthenticator instance;

  /** Global instance of the HTTP transport. */
  private HttpTransport myHttpTransport;
  private Credential myCredential;
  private Storage myStorage;
  private Testing myTest;
  private Toolresults myToolresults;
  private long myLastDiscoveryServiceInvocationTimestamp = -1;


  @NotNull
  public static CloudAuthenticator getInstance() {
    if (instance == null) {
      instance = new CloudAuthenticator();
    }
    return instance;
  }

  /**
   * Should be used in tests only!
   */
  @VisibleForTesting
  public static void setInstance(CloudAuthenticator testInstance) {
    instance = testInstance;
  }

  public Storage getPublicStorage() {
    if (myHttpTransport == null) {
      myHttpTransport = createHttpTransport();
    }
    // A storage accessible to anyone without authentication and authorization (null credential).
    return new Storage.Builder(myHttpTransport, JacksonFactory.getDefaultInstance(), null).setApplicationName(APPLICATION_NAME).build();
  }

  public Storage getStorage() {
    prepareCredential();
    if (myStorage == null) {
      myStorage =
        new Storage.Builder(myHttpTransport, JacksonFactory.getDefaultInstance(), myCredential).setApplicationName(APPLICATION_NAME).build();
    }
    return myStorage;
  }

  public void recreateTestAndToolResults(String testBackendUrl, String toolResultsBackendUrl) {
    prepareCredential();
    myTest =
      new Testing.Builder(myHttpTransport, JacksonFactory.getDefaultInstance(), myCredential).setApplicationName(APPLICATION_NAME)
        .setRootUrl(testBackendUrl).build();
    myToolresults =
      new Toolresults.Builder(myHttpTransport, JacksonFactory.getDefaultInstance(), myCredential).setApplicationName(APPLICATION_NAME)
        .setRootUrl(toolResultsBackendUrl).build();
  }

  public Testing getTest() {
    prepareCredential();
    if (myTest == null) {
      myTest =
        new Testing.Builder(myHttpTransport, JacksonFactory.getDefaultInstance(), myCredential).setApplicationName(APPLICATION_NAME).build();
    }
    return myTest;
  }

  public AndroidDeviceCatalog getAndroidDeviceCatalog() {
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
      myLastDiscoveryServiceInvocationTimestamp = currentTimestamp;
    }
  }

  private void showDeviceCatalogError(String errorMessageSuffix, long currentTimestamp) {
    // The error should be reported just once per burst of invocations.
    if (currentTimestamp - myLastDiscoveryServiceInvocationTimestamp > 1000l) { // If more than a second has passed.
      CloudTestingUtils.showErrorMessage(null, "Error retrieving android device catalog",
                                         "Failed to retrieve available firebase devices! Please try again later.\n" + errorMessageSuffix);
    }
  }

  public Toolresults getToolresults() {
    prepareCredential();
    if (myToolresults == null) {
      myToolresults =
        new Toolresults.Builder(myHttpTransport, JacksonFactory.getDefaultInstance(), myCredential).setApplicationName(APPLICATION_NAME)
          .build();
    }
    return myToolresults;
  }

  public void prepareCredential() {
    if (myHttpTransport == null) {
      myHttpTransport = createHttpTransport();
    }
    if (myCredential == null) {
      if (!authorize()) {
        throw new RuntimeException("Failed to authorize to Google Cloud!");
      }
      myCredential = GoogleLogin.getInstance().getCredential();
    }
  }

  private HttpTransport createHttpTransport() {
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
