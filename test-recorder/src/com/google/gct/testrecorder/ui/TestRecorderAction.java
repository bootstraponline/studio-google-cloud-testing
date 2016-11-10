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

import com.android.annotations.VisibleForTesting;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.google.common.collect.Lists;
import com.google.gct.testrecorder.debugger.SessionInitializer;
import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxy;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
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

import static org.apache.commons.lang.StringUtils.isEmpty;

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

    List<RunConfiguration> suitableRunConfigurations = getSuitableRunConfigurations(myProject);
    if (suitableRunConfigurations.isEmpty()) {
      String message = "Please create an Android Application or Blaze Command Run configuration with a valid module and Default or Specified launch activity.";
      Messages.showDialog(myProject, message, "No suitable run configuration found", new String[]{"OK"}, 0, null);
      return;
    }

    if (suitableRunConfigurations.size() == 1) {
      // If only one configuration is suitable, use it.
      launchTestRecorder(event, suitableRunConfigurations.get(0));
    } else {
      RunnerAndConfigurationSettings selectedConfiguration = RunManagerEx.getInstanceEx(myProject).getSelectedConfiguration();
      if (selectedConfiguration != null && suitableRunConfigurations.contains(selectedConfiguration.getConfiguration())) {
        // If currently selected configuration is suitable, use it.
        launchTestRecorder(event, selectedConfiguration.getConfiguration());
      } else {
        // If there is more than one possible choice, ask the user to pick a configuration.
        ListPopupImpl configurationPickerPopup = new ListPopupImpl(
          new BaseListPopupStep<RunConfiguration>("Pick configuration to launch", suitableRunConfigurations) {
            @NotNull
            @Override
            public String getTextFor(RunConfiguration runConfiguration) {
              return runConfiguration.getName();
            }

            @Override
            public PopupStep onChosen(RunConfiguration runConfiguration, boolean finalChoice) {
              return doFinalStep(() -> launchTestRecorder(event, runConfiguration));
            }
          });

        configurationPickerPopup.showCenteredInCurrentWindow(myProject);
      }
    }
  }

  private void launchTestRecorder(AnActionEvent event, RunConfiguration configurationBase) {
    TestRecorderRunConfigurationProxy testRecorderConfigurationProxy = TestRecorderRunConfigurationProxy.getInstance(configurationBase);
    LocatableConfigurationBase testRecorderConfiguration = testRecorderConfigurationProxy.getTestRecorderRunConfiguration();

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(
      testRecorderConfiguration.getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), testRecorderConfiguration);

    if (builder == null) {
      throw new RuntimeException("Could not create execution environment builder!");
    }

    Module module = testRecorderConfigurationProxy.getModule();
    AndroidFacet facet = AndroidFacet.getInstance(module);

    if (facet == null) {
      throw new RuntimeException("Could not obtain Android facet for module: " + module.getName());
    }

    ExecutionEnvironment environment = builder.activeTarget().dataContext(event.getDataContext()).build();

    // Terminate any active Run or Debug session of the to-be-recorded run configuration.
    // Even if it is a Run session, it still needs to be terminated, since the app will have to be restarted in debug mode.
    AndroidSessionInfo oldSessionInfo = AndroidSessionInfo.findOldSession(module.getProject(), null, configurationBase.getUniqueID());
    if (oldSessionInfo != null) {
      oldSessionInfo.getProcessHandler().detachProcess();
    }

    try {
      environment.getRunner().execute(environment, descriptor -> ApplicationManager.getApplication().executeOnPooledThread(
        new SessionInitializer(facet, environment, testRecorderConfigurationProxy.getLaunchActivityClass(),
                               testRecorderConfiguration.getUniqueID())));
    } catch (Exception e) {
      String message = isEmpty(e.getMessage()) ? "Unknown error" : e.getMessage();
      Messages.showDialog(myProject, message, "Could not start debugging of the app", new String[]{"OK"}, 0, null);
    }
  }

  @VisibleForTesting
  public static List<RunConfiguration> getSuitableRunConfigurations(Project project) {
    List<RunConfiguration> suitableRunConfigurations = Lists.newLinkedList();

    for (RunConfiguration runConfiguration : RunManagerEx.getInstanceEx(project).getAllConfigurationsList()) {
      TestRecorderRunConfigurationProxy runConfigurationProxy = TestRecorderRunConfigurationProxy.getInstance(runConfiguration);
      if (runConfigurationProxy != null && runConfigurationProxy.getModule() != null && runConfigurationProxy.isLaunchActivitySupported()) {
        suitableRunConfigurations.add(runConfiguration);
      }
    }

    return suitableRunConfigurations;
  }


}
