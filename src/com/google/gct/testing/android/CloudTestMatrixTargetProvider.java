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

import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.*;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.google.api.client.util.Maps;
import com.google.common.collect.ImmutableMap;
import com.google.gct.testing.CloudOptionEnablementChecker;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CloudTestMatrixTargetProvider extends DeployTargetProvider {
  public static final class State extends DeployTargetState {
    public int SELECTED_CLOUD_MATRIX_CONFIGURATION_ID = -1;
    public String SELECTED_CLOUD_MATRIX_PROJECT_ID = "";

    @NotNull
    @Override
    public List<ValidationError> validate(@NotNull AndroidFacet facet) {
      return CloudTargetUtil.validate(facet, CloudConfiguration.Kind.MATRIX, SELECTED_CLOUD_MATRIX_PROJECT_ID,
                                      SELECTED_CLOUD_MATRIX_CONFIGURATION_ID);
    }
  }

  @NotNull
  @Override
  public String getId() {
    return "CLOUD_DEVICE_MATRIX";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Cloud Test Lab Device Matrix";
  }

  @NotNull
  @Override
  public DeployTargetState createState() {
    return new State();
  }

  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project, Disposable parentDisposable,
                                                     @NotNull DeployTargetConfigurableContext context) {
    return new CloudTestMatrixTargetConfigurable(project, parentDisposable, context);
  }

  @Override
  public boolean showInDevicePicker() {
    return true;
  }

  @Override
  public boolean isApplicable(boolean isTestConfig) {
    return isTestConfig && CloudOptionEnablementChecker.isCloudTestingEnabled();
  }

  @Override
  public DeployTarget getDeployTarget() {
    return new DeployTarget() {
      @Override
      public boolean hasCustomRunProfileState(@NotNull Executor executor) {
        return !(executor instanceof DefaultDebugExecutor);
      }

      @Override
      public RunProfileState getRunProfileState(@NotNull Executor executor,
                                                @NotNull ExecutionEnvironment env,
                                                @NotNull DeployTargetState state) throws ExecutionException {
        RunProfile runProfile = env.getRunProfile();
        // It is expected to be invoked for test run configurations only.
        if (!(runProfile instanceof AndroidTestRunConfiguration)) {
          return null;
        }

        AndroidTestRunConfiguration runConfiguration = (AndroidTestRunConfiguration) runProfile;
        AndroidFacet facet = AndroidFacet.getInstance(runConfiguration.getConfigurationModule().getModule());
        CloudTestMatrixTargetProvider.State cloudTargetState = (CloudTestMatrixTargetProvider.State) state;

        return new CloudMatrixTestRunningState(env, facet, runConfiguration, cloudTargetState.SELECTED_CLOUD_MATRIX_CONFIGURATION_ID,
                                               cloudTargetState.SELECTED_CLOUD_MATRIX_PROJECT_ID);
      }

      @Nullable
      @Override
      public DeviceFutures getDevices(@NotNull DeployTargetState state,
                                      @NotNull AndroidFacet facet,
                                      @NotNull DeviceCount deviceCount,
                                      boolean debug,
                                      int runConfigId,
                                      @NotNull ConsolePrinter printer) {
        // This method will be called only if hasCustomRunProfileState returned false (i.e., the user clicked Debug), so
        // open the Device Chooser dialog.
        List<DeployTargetProvider> deployTargetProviders = Collections.emptyList();
        Map<String, DeployTargetState> deployTargetStates = Maps.newHashMap();
        deployTargetStates.put(ShowChooserTargetProvider.ID, new ShowChooserTargetProvider.State());

        DeployTargetPickerDialog dialog =
          new DeployTargetPickerDialog(runConfigId, facet, deviceCount, deployTargetProviders, deployTargetStates, printer);
        if (dialog.showAndGet()) {
          return dialog.getSelectedDeployTarget().getDevices(state, facet, deviceCount, debug, runConfigId, printer);
        }
        else {
          return null;
        }
      }
    };
  }
}
