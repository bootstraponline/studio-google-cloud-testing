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
package com.google.gct.testing.results;


import com.android.tools.idea.run.testing.AndroidTestConsoleProperties;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.google.gct.testing.CloudOptionEnablementChecker;
import com.google.gct.testing.DebugConfigurationAction;
import com.google.gct.testing.ShowScreenshotsAction;
import com.intellij.execution.Executor;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GoogleCloudTestConsoleProperties extends AndroidTestConsoleProperties {

  public GoogleCloudTestConsoleProperties(@NotNull AndroidTestRunConfiguration configuration, @NotNull Executor executor) {
    super(configuration, executor);
  }

  @Override
  public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) {
    super.appendAdditionalActions(actionGroup, parent, target);
    actionGroup.addAction(new ShowScreenshotsAction());
    if (CloudOptionEnablementChecker.isCloudDebuggingEnabled()) {
      actionGroup.addAction(new DebugConfigurationAction());
    }
  }

}
