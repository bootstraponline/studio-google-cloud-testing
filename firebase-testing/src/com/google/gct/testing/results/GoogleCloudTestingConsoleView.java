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

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.ui.AttachToProcessListener;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoogleCloudTestingConsoleView extends BaseTestsOutputConsoleView {
  private final ExecutionEnvironment myEnvironment;
  private GoogleCloudTestingResultsForm myResultsViewer;
  @Nullable private final String mySplitterProperty;
  private final List<AttachToProcessListener> myAttachToProcessListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public GoogleCloudTestingConsoleView(final TestConsoleProperties consoleProperties, final ExecutionEnvironment environment) {
    this(consoleProperties, environment, null);
  }

  /**
   * @param splitterProperty               Key to store(project level) latest value of testTree/consoleTab splitter. E.g. "RSpec.Splitter.Proportion"
   */
  public GoogleCloudTestingConsoleView(final TestConsoleProperties consoleProperties,
                                       final ExecutionEnvironment environment,
                                       @Nullable final String splitterProperty) {
    super(consoleProperties, null);
    myEnvironment = environment;
    mySplitterProperty = splitterProperty;
  }

  @Override
  protected TestResultsPanel createTestResultsPanel() {
    // Results View
    myResultsViewer = new GoogleCloudTestingResultsForm(myProperties.getConfiguration(),
                                                  getConsole().getComponent(),
                                                  getConsole().createConsoleActions(),
                                                  myProperties,
                                                  mySplitterProperty);
    return myResultsViewer;
  }

  @Override
  public void initUI() {
    super.initUI();

    // Console
    myResultsViewer.addEventsListener(new GoogleCloudTestResultsViewer.SMEventsAdapter() {
      @Override
      public void onSelected(@Nullable final GoogleCloudTestProxy selectedTestProxy,
                             @NotNull final GoogleCloudTestResultsViewer viewer,
                             @NotNull final TestFrameworkRunningModel model) {
        if (selectedTestProxy == null) {
          return;
        }

        // print selected content
        SMRunnerUtil.runInEventDispatchThread(new Runnable() {
          @Override
          public void run() {
            getPrinter().updateOnTestSelected(selectedTestProxy);
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  public GoogleCloudTestingResultsForm getResultsViewer() {
    return myResultsViewer;
  }

  /**
   * Prints a given string of a given type on the root node.
   * Note: it's a permanent printing, as opposed to calling the same method on {@link #getConsole()} instance.
   * @param s            given string
   * @param contentType  given type
   */
  @Override
  public void print(@NotNull final String s, @NotNull final ConsoleViewContentType contentType) {
    myResultsViewer.getRoot().addLast(new Printable() {
      @Override
      public void printOn(final Printer printer) {
        printer.print(s, contentType);
      }
    });
  }

  /**
   * Prints a given hyperlink on the root node.
   * Note: it's a permanent printing, as opposed to calling the same method on {@link #getConsole()} instance.
   * @param hyperlinkText hyperlink text
   * @param info          HyperlinkInfo
   */
  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
    myResultsViewer.getRoot().addLast(new HyperLink(hyperlinkText, info));
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    super.attachToProcess(processHandler);
    for (AttachToProcessListener listener : myAttachToProcessListeners) {
      listener.onAttachToProcess(processHandler);
    }
  }

  public void addAttachToProcessListener(@NotNull AttachToProcessListener listener) {
    myAttachToProcessListeners.add(listener);
  }

  public void remoteAttachToProcessListener(@NotNull AttachToProcessListener listener) {
    myAttachToProcessListeners.remove(listener);
  }

  @Override
  public void dispose() {
    myAttachToProcessListeners.clear();
    super.dispose();
  }
}
