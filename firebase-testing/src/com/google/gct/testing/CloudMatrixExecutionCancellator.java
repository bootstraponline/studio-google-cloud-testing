/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.google.gct.testing.launcher.CloudAuthenticator;

import java.io.IOException;


public class CloudMatrixExecutionCancellator {

  private volatile boolean isCancelled = false;
  private volatile String cloudProjectId = null;
  private volatile String testMatrixId = null;


  public synchronized void cancel() {
    if (isCancelled) {
      return;
    }

    if (cloudProjectId != null && testMatrixId != null) {
      try {
        doCancel();
      } catch (Exception e1) {
        // Retry
        try {
          doCancel();
        } catch (Exception e2) {
          // Give up
        }
      }
    }
    isCancelled = true;
  }

  private void doCancel() throws IOException {
    CloudAuthenticator.getInstance().getTest().projects().testMatrices().cancel(cloudProjectId, testMatrixId);
  }

  public synchronized boolean isCancelled() {
    return isCancelled;
  }

  public synchronized void setCloudProjectId(String cloudProjectId) {
    this.cloudProjectId = cloudProjectId;
  }

  public synchronized void setTestMatrixId(String testMatrixId) {
    this.testMatrixId = testMatrixId;
  }
}
