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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.ProxyFilters;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoogleCloudTestingUIActionsHandler extends GoogleCloudTestResultsViewer.SMEventsAdapter {
  private final TestConsoleProperties myConsoleProperties;

  public GoogleCloudTestingUIActionsHandler(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  @Override
  public void onTestNodeAdded(final GoogleCloudTestResultsViewer sender, final GoogleCloudTestProxy test) {
    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myConsoleProperties)) {
      //TODO: Should it be a configurable property or added nodes in Google Cloud Testing should never be selected?
      //sender.selectAndNotify(test);
    }
  }

  @Override
  public void onTestingFinished(final GoogleCloudTestResultsViewer sender) {
    // select first defect at the end (my be TRACK_RUNNING_TEST was enabled and affects on the fly selection)
    final GoogleCloudTestProxy testsRootNode = sender.getTestsRootNode();
    if (TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
      final AbstractTestProxy firstDefect;

      // defects priority:
      // ERROR -> FAILURE -> GENERAL DEFECTIVE NODE
      final List<GoogleCloudTestProxy> allTests = testsRootNode.getAllTests();
      final AbstractTestProxy firstError = ProxyFilters.ERROR_LEAF.detectIn(allTests);
      if (firstError != null) {
        firstDefect = firstError;
      }
      else {
        final AbstractTestProxy firstFailure = ProxyFilters.FAILURE_LEAF.detectIn(allTests);
        if (firstFailure != null) {
          firstDefect = firstFailure;
        }
        else {
          firstDefect = null;
        }
      }

      // select if detected
      if (firstDefect != null) {
        sender.selectAndNotify(firstDefect);
      }
    }
  }

  @Override
  public void onSelected(@Nullable final GoogleCloudTestProxy selectedTestProxy,
                         @NotNull final GoogleCloudTestResultsViewer viewer,
                         @NotNull final TestFrameworkRunningModel model) {
    //TODO: tests o "onSelected"
    SMRunnerUtil.runInEventDispatchThread(new Runnable() {
      @Override
      public void run() {
        if (ScrollToTestSourceAction.isScrollEnabled(model)) {
          OpenSourceUtil.openSourcesFrom(model.getTreeView(), false);
        }
      }
    }, ModalityState.NON_MODAL);
  }
}
