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


import com.google.gct.testing.CloudOptionEnablementChecker;
import com.google.gct.testing.DebugConfigurationAction;
import com.google.gct.testing.ShowScreenshotsAction;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerToolbarPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;

import javax.swing.*;
import java.awt.*;

public class GoogleCloudTestRunnerToolbarPanel extends SMTRunnerToolbarPanel {

  public GoogleCloudTestRunnerToolbarPanel(TestConsoleProperties properties,
                                           TestFrameworkRunningModel model,
                                           JComponent contentPane) {
    super(properties, model, contentPane);

    int lastComponentIndex = getComponentCount() - 1;
    ActionToolbarImpl actionToolbar = (ActionToolbarImpl)getComponent(lastComponentIndex);
    final DefaultActionGroup cloudActionGroup = new DefaultActionGroup(null, false);
    int separatorCounter = 0;
    boolean cloudActionsAdded = false;
    for (AnAction action : actionToolbar.getActions(true)) {
      cloudActionGroup.add(action);
      if (!cloudActionsAdded) {
        if (action instanceof Separator) {
          separatorCounter++;
        }
        // Add firebase actions as a 4th group of actions.
        if (separatorCounter == 3) {
          addCloudActions(cloudActionGroup);
          cloudActionsAdded = true;
        }
      }
    }

    // Remove the original action bar and add a firebase action bar instead.
    remove(lastComponentIndex);
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.TESTTREE_VIEW_TOOLBAR, cloudActionGroup, true).getComponent(),
        BorderLayout.CENTER);
  }

  private void addCloudActions(DefaultActionGroup actionGroup) {
    actionGroup.addAction(new ShowScreenshotsAction());
    if (CloudOptionEnablementChecker.isCloudDebuggingEnabled()) {
      actionGroup.addAction(new DebugConfigurationAction());
    }
    actionGroup.addSeparator();
  }

}
