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
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public interface TestRecorderRunConfigurationProxy {

  @Nullable
  static TestRecorderRunConfigurationProxy getInstance(@Nullable RunConfiguration configurationBase) {
    if (configurationBase instanceof AndroidRunConfiguration) {
      return new TestRecorderAndroidRunConfigurationProxy((AndroidRunConfiguration)configurationBase);
    }

    return null;
  }

  @NotNull
  LocatableConfigurationBase getTestRecorderRunConfiguration();

  Module getModule();

  boolean isLaunchActivitySupported();

  String getLaunchActivityClass();
}
