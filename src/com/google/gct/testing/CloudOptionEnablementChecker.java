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

  // If this JVM option is present and set to true, enable the cloud matrix testing regardless of anything.
  private static final String LOCAL_ENABLE_FLAG = "enable.google.cloud.testing.plugin";


  @Override
  public void initComponent() {
    if (!isCloudOptionEnabled()) {
      //JobScheduler.getScheduler().schedule(new EnablementCheckerRunnable(), 20, TimeUnit.SECONDS);
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

  public static boolean isCloudOptionEnabled() {
    return Boolean.getBoolean(LOCAL_ENABLE_FLAG);// || PropertiesComponent.getInstance().getBoolean(ENABLE_CLOUD_TESTING_REMOTELY, false);
  }

  private class EnablementCheckerRunnable implements Runnable {
    @Override
    public void run() {
      if (!isCloudOptionEnabled()) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            if (CloudConfigurationHelper.isCloudTestingEnabledRemotely()) {
              PropertiesComponent.getInstance().setValue(ENABLE_CLOUD_TESTING_REMOTELY, "true");
            }
          }
        });
        // Check again in 30 minutes.
        JobScheduler.getScheduler().schedule(this, 30, TimeUnit.MINUTES);
      }
    }
  }
}
