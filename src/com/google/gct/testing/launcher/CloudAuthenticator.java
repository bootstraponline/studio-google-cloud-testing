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
import com.google.gct.login.GoogleLogin;

public class CloudAuthenticator {

  /** Global instance of the HTTP transport. */
  private static HttpTransport httpTransport;

  private static Credential credential;

  private static Storage storage;


  public static Storage getStorage() {
    if (httpTransport == null) {
      httpTransport = createHttpTransport();
    }
    if (credential == null) {
      if (!authorize()) {
        throw new RuntimeException("Failed to authorize in Google Cloud!");
      }
      credential = GoogleLogin.getInstance().getCredential();
    }
    if (storage == null) {
      storage = new Storage.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).build();
    }
    return storage;
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
