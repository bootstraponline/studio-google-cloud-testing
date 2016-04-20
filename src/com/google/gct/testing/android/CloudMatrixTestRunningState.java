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
package com.google.gct.testing.android;

import com.android.tools.idea.run.AndroidProcessText;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.google.gct.testing.CloudConfigurationHelper;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A run configuration execution which runs an Android test on a matrix of firebase devices.
 */
public class CloudMatrixTestRunningState implements RunProfileState {

  @NotNull private final ExecutionEnvironment myEnvironment;
  @NotNull private final AndroidFacet myFacet;
  private final int myMatrixConfigurationId;
  @NotNull private final String myCloudProjectId;
  @NotNull private final AndroidTestRunConfiguration myConfiguration;
  @NotNull private final ProcessHandler myProcessHandler = new DefaultDebugProcessHandler();

  public CloudMatrixTestRunningState(@NotNull ExecutionEnvironment environment, @NotNull AndroidFacet facet,
                                     @NotNull AndroidTestRunConfiguration configuration, int matrixConfigurationId,
                                     @NotNull String cloudProjectId) {
    myEnvironment = environment;
    myFacet = facet;
    myConfiguration = configuration;
    myMatrixConfigurationId = matrixConfigurationId;
    myCloudProjectId = cloudProjectId;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    AndroidProcessText.attach(myProcessHandler);
    return CloudConfigurationHelper.executeCloudMatrixTests(myMatrixConfigurationId, myCloudProjectId, this, executor);
  }

  @NotNull
  public ExecutionEnvironment getEnvironment() {
    return myEnvironment;
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public AndroidTestRunConfiguration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }
}
