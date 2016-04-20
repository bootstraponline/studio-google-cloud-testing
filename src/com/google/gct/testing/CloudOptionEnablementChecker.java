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

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class CloudOptionEnablementChecker implements ApplicationComponent {

  private static final String ENABLE_CLOUD_TESTING_REMOTELY = "com.google.gct.enable.cloud.testing";
  private static final String ENABLE_CLOUD_DEBUGGING_REMOTELY = "com.google.gct.enable.cloud.debugging";

  // If this JVM option is present and set to true, enable the firebase matrix testing regardless of anything.
  private static final String LOCAL_ENABLE_CLOUD_TESTING_FLAG = "enable.google.cloud.testing.plugin";

  // If this JVM option is present and set to true, enable firebase debugging regardless of anything.
  private static final String LOCAL_ENABLE_CLOUD_DEBUGGING_FLAG = "enable.google.cloud.debugging";


  @Override
  public void initComponent() {
    scheduleCheckingCloudOption(false);
    scheduleCheckingCloudOption(true);
  }

  private void scheduleCheckingCloudOption(boolean isDebugging) {
    if (!isCloudOptionEnabled(isDebugging)) {
      JobScheduler.getScheduler().schedule(new CloudOptionEnablementCheckerRunnable(isDebugging), 20, TimeUnit.SECONDS);
    }
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "CloudOptionEnablementChecker";
  }

  public static boolean isCloudTestingEnabled() {
    return isCloudOptionEnabled(false);
  }

  public static boolean isCloudDebuggingEnabled() {
    return isCloudOptionEnabled(true);
  }

  private static boolean isCloudOptionEnabled(boolean isDebugging) {
    String localFlag = isDebugging ? LOCAL_ENABLE_CLOUD_DEBUGGING_FLAG : LOCAL_ENABLE_CLOUD_TESTING_FLAG;
    String remoteFlag = isDebugging ? ENABLE_CLOUD_DEBUGGING_REMOTELY : ENABLE_CLOUD_TESTING_REMOTELY;

    return Boolean.getBoolean(localFlag) || PropertiesComponent.getInstance().getBoolean(remoteFlag, false);
  }

  private class CloudOptionEnablementCheckerRunnable implements Runnable {
    private final boolean isDebugging;
    private final String remoteFlag;

    CloudOptionEnablementCheckerRunnable(boolean isDebugging) {
      this.isDebugging = isDebugging;
      remoteFlag = isDebugging ? ENABLE_CLOUD_DEBUGGING_REMOTELY : ENABLE_CLOUD_TESTING_REMOTELY;
    }

    @Override
    public void run() {
      if (!isCloudOptionEnabled(isDebugging)) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            if (CloudConfigurationHelper.isCloudOptionEnabledRemotely(isDebugging)) {
              PropertiesComponent.getInstance().setValue(remoteFlag, "true");
            }
          }
        });
        // Check again in 30 minutes.
        JobScheduler.getScheduler().schedule(this, 30, TimeUnit.MINUTES);
      }
    }
  }
}
