/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.testing.results;


import com.google.gct.testing.DebugConfigurationAction;
import com.google.gct.testing.ShowScreenshotsAction;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerToolbarPanel;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

import javax.swing.*;

public class GoogleCloudTestRunnerToolbarPanel extends SMTRunnerToolbarPanel {

  public GoogleCloudTestRunnerToolbarPanel(TestConsoleProperties properties,
                                           ExecutionEnvironment environment,
                                           TestFrameworkRunningModel model,
                                           JComponent contentPane) {
    super(properties, environment, model, contentPane);
  }

  @Override
  protected void appendAdditionalActions(DefaultActionGroup actionGroup, TestConsoleProperties properties,
                                         ExecutionEnvironment environment, JComponent parent) {

    super.appendAdditionalActions(actionGroup, properties, environment, parent);
    actionGroup.addAction(new ShowScreenshotsAction());
    actionGroup.addAction(new DebugConfigurationAction());
  }

}
