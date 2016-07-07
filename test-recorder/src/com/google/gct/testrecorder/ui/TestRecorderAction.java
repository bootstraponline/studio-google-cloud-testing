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
package com.google.gct.testrecorder.ui;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.editor.DefaultActivityLaunch;
import com.android.tools.idea.run.editor.LaunchOptionState;
import com.android.tools.idea.run.editor.SpecificActivityLaunch;
import com.google.common.collect.Lists;
import com.google.gct.testrecorder.debugger.SessionInitializer;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventKind;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class TestRecorderAction extends AnAction {
  public static final Icon TEST_RECORDER_ICON = IconLoader.getIcon("circle_small.png", TestRecorderAction.class);

  private Project myProject;


  @Override
  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setIcon(TEST_RECORDER_ICON);

    final Project project = event.getProject();

    if (project == null || !project.isInitialized() || project.isDisposed() || DumbService.getInstance(project).isDumb()) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                   .setCategory(EventCategory.TEST_RECORDER)
                                   .setKind(EventKind.TEST_RECORDER_LAUNCH));

    myProject = event.getProject();
    if (myProject == null || myProject.isDisposed()) {
      return;
    }

    List<AndroidRunConfiguration> suitableRunConfigurations = getSuitableRunConfigurations();
    if (suitableRunConfigurations.isEmpty()) {
      String message = "Please create an Android Application configuration with a valid module and Default or Specified launch activity.";
      Messages.showDialog(myProject, message, "No suitable Android Application configuration found", new String[]{"OK"}, 0, null);
      return;
    }

    if (suitableRunConfigurations.size() == 1) {
      // If only one configuration is suitable, use it.
      launchTestRecorder(event, suitableRunConfigurations.get(0));
    } else {
      RunnerAndConfigurationSettings selectedConfiguration = RunManagerEx.getInstanceEx(myProject).getSelectedConfiguration();
      if (selectedConfiguration != null && suitableRunConfigurations.contains(selectedConfiguration.getConfiguration())) {
        // If currently selected configuration is suitable, use it.
        launchTestRecorder(event, (AndroidRunConfiguration) selectedConfiguration.getConfiguration());
      } else {
        // If there is more than one possible choice, ask the user to pick a configuration.
        ListPopupImpl configurationPickerPopup = new ListPopupImpl(
          new BaseListPopupStep<AndroidRunConfiguration>("Pick configuration to launch", suitableRunConfigurations) {
            @NotNull
            @Override
            public String getTextFor(AndroidRunConfiguration runConfiguration) {
              return runConfiguration.getName();
            }

            @Override
            public PopupStep onChosen(AndroidRunConfiguration runConfiguration, boolean finalChoice) {
              return doFinalStep(new Runnable() {
                @Override
                public void run() {
                  launchTestRecorder(event, runConfiguration);
                }
              });
            }
          });

        configurationPickerPopup.showCenteredInCurrentWindow(myProject);
      }
    }
  }

  private void launchTestRecorder(AnActionEvent event, AndroidRunConfiguration runConfiguration) {
    AndroidRunConfiguration testRecorderConfiguration = (AndroidRunConfiguration) runConfiguration.clone();
    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(
      testRecorderConfiguration.getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), testRecorderConfiguration);

    if (builder == null) {
      throw new RuntimeException("Could not create execution environment builder!");
    }

    Module module = testRecorderConfiguration.getConfigurationModule().getModule();
    AndroidFacet facet = AndroidFacet.getInstance(module);

    if (facet == null) {
      throw new RuntimeException("Could not obtain Android facet for module: " + module.getName());
    }

    LaunchOptionState launchOptionState = runConfiguration.getLaunchOptionState(runConfiguration.MODE);

    ExecutionEnvironment environment = builder.activeTarget().dataContext(event.getDataContext()).build();

    try {
      environment.getRunner().execute(environment, new ProgramRunner.Callback() {
        @Override
        public void processStarted(RunContentDescriptor descriptor) {
          ApplicationManager.getApplication().executeOnPooledThread(
            new SessionInitializer(facet, environment, launchOptionState, testRecorderConfiguration.getUniqueID()));
        }
      });
    } catch (ExecutionException e) {
      throw new RuntimeException("Could not start debugging of the app: ", e);
    }
  }

  private List<AndroidRunConfiguration> getSuitableRunConfigurations() {
    List<AndroidRunConfiguration> suitableRunConfigurations = Lists.newLinkedList();

    for (RunConfiguration runConfiguration : RunManagerEx.getInstanceEx(myProject).getAllConfigurationsList()) {
      // Should be an Android Application configuration.
      if (runConfiguration != null && runConfiguration.getType() instanceof AndroidRunConfigurationType) {
        AndroidRunConfiguration androidRunConfiguration = (AndroidRunConfiguration) runConfiguration;
        // Should have a selected module.
        if (androidRunConfiguration.getConfigurationModule().getModule() != null) {
          LaunchOptionState launchOptionState = androidRunConfiguration.getLaunchOptionState(androidRunConfiguration.MODE);

          // The launch activity should be either Default or Specified.
          if (launchOptionState instanceof DefaultActivityLaunch.State || launchOptionState instanceof SpecificActivityLaunch.State) {
            suitableRunConfigurations.add(androidRunConfiguration);
          }
        }
      }
    }

    return suitableRunConfigurations;
  }


}
