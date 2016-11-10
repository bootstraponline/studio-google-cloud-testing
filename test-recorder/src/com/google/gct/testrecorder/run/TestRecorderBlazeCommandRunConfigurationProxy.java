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

import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public class TestRecorderBlazeCommandRunConfigurationProxy implements TestRecorderRunConfigurationProxy {

  private final BlazeCommandRunConfiguration myBaseConfiguration;
  private final BlazeAndroidBinaryRunConfigurationHandler myBaseConfigurationHandler;

  public TestRecorderBlazeCommandRunConfigurationProxy(BlazeCommandRunConfiguration baseConfiguration) {
    myBaseConfiguration = baseConfiguration;
    myBaseConfigurationHandler = (BlazeAndroidBinaryRunConfigurationHandler)baseConfiguration.getHandler();
  }

  @NotNull
  @Override
  public LocatableConfigurationBase getTestRecorderRunConfiguration() {
    return new TestRecorderBlazeCommandRunConfiguration(myBaseConfiguration);
  }

  @Override
  public Module getModule() {
    return myBaseConfigurationHandler.getModule();
  }

  @Override
  public boolean isLaunchActivitySupported() {
    String mode = myBaseConfigurationHandler.getState().getMode();

    // Supported launch activities are Default and Specified.
    return BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY.equals(mode)
           || BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY.equals(mode);
  }

  @Override
  public String getLaunchActivityClass() {
    BlazeAndroidBinaryRunConfigurationState state = myBaseConfigurationHandler.getState();

    if (BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY.equals(state.getMode())) {
      return state.getActivityClass();
    }

    return "";
  }
}
