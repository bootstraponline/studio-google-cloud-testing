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

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.editor.DefaultActivityLaunch;
import com.android.tools.idea.run.editor.LaunchOptionState;
import com.android.tools.idea.run.editor.SpecificActivityLaunch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gct.testrecorder.debugger.SessionInitializer;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
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
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Map;
import java.util.Set;

public class TestRecorderAction extends AnAction {
  private static final String DEFAULT_TEXT = "Record Espresso Test";
  private static final String DEFAULT_DESCRIPTION = "Record Espresso test for selected configuration";

  public static final Icon TEST_RECORDER_ICON = IconLoader.getIcon("circle_small.png", TestRecorderAction.class);

  private Project myProject;
  private AndroidFacet myFacet;
  private volatile boolean isTestRecorderRunning = false;

  private final DocumentBuilderFactory myDocumentBuilderFactory;

  private final Set<String> existingIds = Sets.newHashSet();
  /**
   * Maps a given id prefix to the last used id increment, e.g.,
   * linearLayout -> 2 (i.e., the last used id for a LinearLayout was linearLayout2)
   * textView -> 5 (i.e., the last used id for a TextView was textView5)
   */
  private final Map<String, Integer> lastUsedIdIndexMap = Maps.newHashMap();


  public TestRecorderAction() {
    myDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
    myDocumentBuilderFactory.setNamespaceAware(true);
  }

  @Override
  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setIcon(TEST_RECORDER_ICON);

    if (isTestRecorderRunning) {
      presentation.setEnabled(false);
      return;
    }

    final Project project = event.getProject();

    if (project == null || !project.isInitialized() || project.isDisposed() || DumbService.getInstance(project).isDumb()) {
      presentation.setEnabled(false);
      return;
    }

    boolean isEnabled = true;
    String text = DEFAULT_TEXT;
    String description = DEFAULT_DESCRIPTION;

    RunnerAndConfigurationSettings configuration = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (configuration == null || configuration.getConfiguration() == null
        || !(configuration.getConfiguration().getType() instanceof AndroidRunConfigurationType)) {
      isEnabled = false;
      text = "Not an Android Application configuration";
      description = "Please select an Android Application configuration";
    } else {
      AndroidRunConfiguration androidRunConfiguration = (AndroidRunConfiguration)configuration.getConfiguration();
      if (androidRunConfiguration.getConfigurationModule().getModule() == null) {
        isEnabled = false;
        text = "No module selected in the chosen Run/Debug configuration";
        description = "Please select a module in the chosen Run/Debug configuration";
      } else {
        LaunchOptionState launchOptionState = androidRunConfiguration.getLaunchOptionState(androidRunConfiguration.MODE);
        if (!(launchOptionState instanceof DefaultActivityLaunch.State) && !(launchOptionState instanceof SpecificActivityLaunch.State)) {
          isEnabled = false;
          text = "Launch activity is neither default nor specified in the chosen Run/Debug configuration";
          description = "Please select either default or specified launch activity in the chosen Run/Debug configuration";
        }
      }
    }

    // TODO: Restore setting the text and description after this action is shown (again) in the main toolbar.
    //presentation.setText(text);
    //presentation.setDescription(description);
    presentation.setEnabled(isEnabled);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    myProject = event.getProject();
    if (myProject == null || myProject.isDisposed()) {
      return;
    }

    RunnerAndConfigurationSettings configuration = RunManagerEx.getInstanceEx(myProject).getSelectedConfiguration();
    ExecutionEnvironmentBuilder builder =
      configuration == null ? null : ExecutionEnvironmentBuilder.createOrNull(DefaultDebugExecutor.getDebugExecutorInstance(), configuration);

    if (builder == null) {
      return;
    }

    if (configuration.getConfiguration() == null || !(configuration.getConfiguration().getType() instanceof AndroidRunConfigurationType)) {
      return; // Should never happen, but check just in case.
    }

    AndroidRunConfiguration runConfiguration = (AndroidRunConfiguration)configuration.getConfiguration();

    final LaunchOptionState launchOptionState = runConfiguration.getLaunchOptionState(runConfiguration.MODE);

    if (!(launchOptionState instanceof DefaultActivityLaunch.State) && !(launchOptionState instanceof SpecificActivityLaunch.State)) {
      return; // Should never happen, but check just in case.
    }

    Module module = runConfiguration.getConfigurationModule().getModule();
    if (module == null) {
      return; // Should never happen, but check just in case.
    }

    myFacet = AndroidFacet.getInstance(module);

    // TODO: Disable Test Recorder launch button when enabling it back after the Test Recorder session is implemented.
    //isTestRecorderRunning = true;

    final ExecutionEnvironment environment = builder.activeTarget().dataContext(event.getDataContext()).build();

    try {
      environment.getRunner().execute(environment, new ProgramRunner.Callback() {
        @Override
        public void processStarted(RunContentDescriptor descriptor) {
          ApplicationManager.getApplication().executeOnPooledThread(new SessionInitializer(myFacet, environment, launchOptionState));
        }
      });
    } catch (ExecutionException e) {
      throw new RuntimeException("Could not start debugging of the app: ", e);
    }
  }

}
