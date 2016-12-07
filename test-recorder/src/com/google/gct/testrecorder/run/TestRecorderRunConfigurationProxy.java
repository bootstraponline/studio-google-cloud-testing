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
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TestRecorderRunConfigurationProxy {
  ExtensionPointName<TestRecorderRunConfigurationProxyProvider> EP_NAME =
    ExtensionPointName.create("com.google.gct.testrecorder.run.testRecorderRunConfigurationProxyProvider");


  @Nullable
  static TestRecorderRunConfigurationProxy getInstance(@Nullable RunConfiguration runConfiguration) {
    for (TestRecorderRunConfigurationProxyProvider proxyProvider : EP_NAME.getExtensions()) {
      TestRecorderRunConfigurationProxy proxy = proxyProvider.getProxy(runConfiguration);
      if (proxy != null) {
        return proxy;
      }
    }

    return null;
  }

  @NotNull
  LocatableConfigurationBase getTestRecorderRunConfiguration();

  Module getModule();

  boolean isLaunchActivitySupported();

  String getLaunchActivityClass();

  @Nullable
  List<ListenableFuture<IDevice>> getDeviceFutures(ExecutionEnvironment environment);
}
