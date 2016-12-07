/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.gct.testrecorder.run;

import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidRunConfigContext;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DefaultActivityLaunch;
import com.android.tools.idea.run.editor.LaunchOptionState;
import com.android.tools.idea.run.editor.SpecificActivityLaunch;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TestRecorderAndroidRunConfigurationProxy implements TestRecorderRunConfigurationProxy {

  private final AndroidRunConfiguration myBaseConfiguration;

  public TestRecorderAndroidRunConfigurationProxy(AndroidRunConfiguration baseConfiguration) {
    myBaseConfiguration = baseConfiguration;
  }

  @NotNull
  @Override
  public LocatableConfigurationBase getTestRecorderRunConfiguration() {
    return new TestRecorderAndroidRunConfiguration(myBaseConfiguration);
  }

  @Override
  public Module getModule() {
    return myBaseConfiguration.getConfigurationModule().getModule();
  }

  @Override
  public boolean isLaunchActivitySupported() {
    LaunchOptionState launchOptionState = myBaseConfiguration.getLaunchOptionState(myBaseConfiguration.MODE);

    // Supported launch activities are Default and Specified.
    return launchOptionState instanceof DefaultActivityLaunch.State || launchOptionState instanceof SpecificActivityLaunch.State;
  }

  @Override
  public String getLaunchActivityClass() {
    LaunchOptionState launchOptionState = myBaseConfiguration.getLaunchOptionState(myBaseConfiguration.MODE);

    if (launchOptionState instanceof SpecificActivityLaunch.State) {
      return ((SpecificActivityLaunch.State)launchOptionState).ACTIVITY_CLASS;
    }

    return "";
  }

  @Nullable
  @Override
  public List<ListenableFuture<IDevice>> getDeviceFutures(ExecutionEnvironment environment) {
    AndroidRunConfigContext context = environment.getCopyableUserData(AndroidRunConfigContext.KEY);
    if (context != null) {
      DeviceFutures targetDevices = context.getTargetDevices();
      if (targetDevices != null) {
        return targetDevices.get();
      }
    }

    return null;
  }
}
