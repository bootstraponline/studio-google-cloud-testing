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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.android.tools.idea.stats.UsageTracker;
import com.google.gct.testing.android.CloudDebuggingTarget;
import com.google.gct.testing.results.GoogleCloudTestProxy.GoogleCloudRootTestProxy;
import com.google.gct.testing.results.GoogleCloudTestTreeView;
import com.google.gct.testing.results.GoogleCloudTestingResultsForm;
import com.google.gct.testing.util.CloudTestingTracking;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DebugConfigurationAction extends AnAction {

  private final static String TEXT = "Debug Configuration in Cloud";
  private final static String DESCRIPTION = "Debug Configuration on a Cloud Device";
  private AndroidDebugBridge myAndroidDebugBridge;

  public DebugConfigurationAction() {
    super(TEXT, DESCRIPTION, CloudTestingUtils.CLOUD_DEBUG_ICON);
    getTemplatePresentation().setEnabled(false);
  }

  @Override
  public void update(AnActionEvent actionEvent) {
    GoogleCloudTestTreeView sender = actionEvent.getData(GoogleCloudTestTreeView.CLOUD_TEST_RUNNER_VIEW);

    if (sender == null) {
      return;
    }

    AbstractTestProxy selectedNode = ((GoogleCloudTestingResultsForm)sender.getResultsViewer()).getTreeView().getSelectedTest();
    if (selectedNode == null || isRootNode(selectedNode)) {
      actionEvent.getPresentation().setEnabled(false);
    } else {
      actionEvent.getPresentation().setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(final AnActionEvent actionEvent) {
    ExecutionEnvironment environment = actionEvent.getData(LangDataKeys.EXECUTION_ENVIRONMENT);
    GoogleCloudTestTreeView sender = actionEvent.getData(GoogleCloudTestTreeView.CLOUD_TEST_RUNNER_VIEW);

    if (environment == null || sender == null) {
      return;
    }

    Project project = actionEvent.getData(PlatformDataKeys.PROJECT);

    AbstractTestProxy selectedNode = ((GoogleCloudTestingResultsForm)sender.getResultsViewer()).getTreeView().getSelectedTest();

    assert !isRootNode(selectedNode); // The action should have been disabled for the root node.

    UsageTracker.getInstance().trackEvent(
      CloudTestingTracking.CLOUD_TESTING, CloudTestingTracking.DEBUG_FROM_RESULTS, CloudTestingTracking.SESSION_LABEL, null);

    String configurationName;
    String className = null;
    String methodName = null;
    if (isRootNode(selectedNode.getParent())) {
      // User selected a configuration node.
      configurationName = selectedNode.getName();
    } else if (isRootNode(selectedNode.getParent().getParent())) {
      // User selected a class node.
      className = selectedNode.getName();
      configurationName = selectedNode.getParent().getName();
    } else {
      // User selected a method node.
      methodName = selectedNode.getName();
      className = selectedNode.getParent().getName();
      configurationName = selectedNode.getParent().getParent().getName();
    }

    ConfigurationInstance configurationInstance = ConfigurationInstance.parseFromResultsViewerDisplayString(configurationName);
    if (!configurationInstance.isVirtual()) {
      CloudTestingUtils.showBalloonMessage(project, "Debugging on physical devices is not supported yet", MessageType.WARNING, 10);
      return;
    }

    myAndroidDebugBridge = AndroidSdkUtils.getDebugBridge(project);
    if (myAndroidDebugBridge == null) {
      CloudTestingUtils.showBalloonMessage(project, "Could not obtain a debug bridge", MessageType.WARNING, 10);
      return;
    }

    ApplicationManager.getApplication()
      .executeOnPooledThread(new DebuggingStater(environment, project, configurationInstance, className, methodName));
  }

  private class DebuggingStater extends Thread {
    private final ExecutionEnvironment environment;
    private final Project project;
    private final ConfigurationInstance configurationInstance;
    private final String className;
    private final String methodName;
    private final RunProfile runProfile;
    private final ProgramRunner runner;

    private DebuggingStater(ExecutionEnvironment environment, Project project, @NotNull ConfigurationInstance configurationInstance,
                            @Nullable String className, @Nullable String methodName) {
      this.environment = environment;
      this.project = project;
      this.configurationInstance = configurationInstance;
      this.className = className;
      this.methodName = methodName;
      runProfile = environment.getRunProfile();
      runner = RunnerRegistry.getInstance().getRunner(DefaultDebugExecutor.getDebugExecutorInstance().getId(), runProfile);
    }

    @Override
    public void run() {
      if (!(runProfile instanceof AndroidTestRunConfiguration)) {
        return;
      }
      IDevice device = getMatchingDevice();
      if (device instanceof GhostCloudDevice) {
        // Wait a bit, giving the device sometime to become ready, and then try again.
        try {
          Thread.sleep(3000); // 3 seconds
        }
        catch (InterruptedException e) {
          //ignore
        }
        run();
      } else {
        if (device == null) {
          // Did not find a device, so start a new one.
          CloudConfigurationHelper.launchCloudDevice(configurationInstance.getEncodedString());
          device = getMatchingDevice();
          // Should not happen unless the user closes the corresponding VNC window thus killing the device before it is booted.
          if (device == null) {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                CloudTestingUtils.showBalloonMessage(project, "Could not find a launched cloud device!", MessageType.WARNING, 10);
              }
            });
            return;
          }
        }

        final String deviceSerialNumber = device.getSerialNumber();
        final AndroidTestRunConfiguration runConfiguration = prepareTestRunConfiguration(deviceSerialNumber);
        if (runConfiguration == null) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              CloudTestingUtils.showBalloonMessage(project, "Could not prepare a run configuration for cloud debugging",
                                                   MessageType.WARNING, 10);
            }
          });
          return;
        }

        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            try {
              runner.execute(new ExecutionEnvironmentBuilder(environment)
                               .executor(DefaultDebugExecutor.getDebugExecutorInstance())
                               .runProfile(runConfiguration)
                               .build());
            } catch (ExecutionException e) {
              CloudTestingUtils.showBalloonMessage(project, "Failed to start debugging on a cloud device: " +
                                                            deviceSerialNumber, MessageType.WARNING, 10);
            }
          }
        });
      }
    }

    private @Nullable AndroidTestRunConfiguration prepareTestRunConfiguration(String deviceSerialNumber) {
      // Clone the run configuration such that we do not need to reuse and restore the original one.
      final AndroidTestRunConfiguration runConfiguration = (AndroidTestRunConfiguration) ((AndroidTestRunConfiguration)runProfile).clone();
      for (DeployTarget deployTarget : DeployTarget.getDeployTargets()) {
        if (deployTarget.getId().equals(CloudDebuggingTarget.ID)) {
          ((CloudDebuggingTarget)deployTarget).setCloudDeviceSerialNumber(deviceSerialNumber);
          runConfiguration.setTargetSelectionMode(deployTarget);
          if (className != null) {
            runConfiguration.CLASS_NAME = className;
            if (methodName != null) {
              runConfiguration.METHOD_NAME = methodName;
              runConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_METHOD;
            } else {
              runConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_CLASS;
            }
          }
          return runConfiguration;
        }
      }
      return null;
    }

    private IDevice getMatchingDevice() {
      if (myAndroidDebugBridge != null) { // Should not be null at this point, but check just in case.
        for (IDevice device : myAndroidDebugBridge.getDevices()) {
          String deviceConfigurationInstance = CloudConfigurationHelper.getConfigurationInstanceForSerialNumber(device.getSerialNumber());
          if (configurationInstance.getEncodedString().equals(deviceConfigurationInstance)) {
            return device;
          }
        }
      }
      for (IDevice device : CloudConfigurationHelper.getLaunchingCloudDevices()) {
        if (device.getSerialNumber().equals(configurationInstance.getEncodedString().toLowerCase())) {
          return device;
        }
      }
      return null;
    }
  }

  private boolean isRootNode(AbstractTestProxy node) {
    return node instanceof GoogleCloudRootTestProxy;
  }

}
