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

import com.google.gct.testing.CloudMatrixExecutionCancellator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.CompositeTestLocationProvider;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.TestProxyFilterProvider;
import com.intellij.execution.testframework.sm.runner.TestProxyPrinterProvider;
import com.intellij.execution.testframework.sm.runner.ui.AttachToProcessListener;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.testIntegration.TestLocationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoogleCloudTestResultsConnectionUtil {
  private static final String TEST_RUNNER_DEBUG_MODE_PROPERTY = "idea.smrunner.debug";

  private GoogleCloudTestResultsConnectionUtil() {
    // Do nothing. Utility class.
  }

  /**
   * Creates Firebase Test Runner console component with test tree, console, statistics tabs
   * and attaches it to given Process handler.
   *
   * You can use this method in run configuration's CommandLineState. You should
   * just override "executeCloudMatrixTests" method of your custom command line state and return
   * test runner's console.
   *
   * NB: For debug purposes please enable "debug mode". In this mode test runner will also validate
   * consistency of test events communication protocol and throw assertion errors. To enable debug mode
   * please set system property idea.smrunner.debug=true
   *
   * @param testFrameworkName Is used to store(project level) latest value of testTree/consoleTab splitter and other settings
   * and also will be mentioned in debug diagnostics
   * @param processHandler Process handler
   * @param consoleProperties Console properties for test console actions
   * @return Console view
   * @throws com.intellij.execution.ExecutionException If IDEA cannot executeCloudMatrixTests process this Exception will
   * be caught and shown in error message box
   */
  public static BaseTestsOutputConsoleView createAndAttachConsole(@NotNull final String testFrameworkName,
                                                                  @NotNull final ProcessHandler processHandler,
                                                                  @NotNull final TestConsoleProperties consoleProperties,
                                                                  ExecutionEnvironment environment,
                                                                  @NotNull final CloudMatrixExecutionCancellator matrixExecutionCancellator
  ) throws ExecutionException {
    BaseTestsOutputConsoleView console = createConsole(testFrameworkName, consoleProperties, environment, matrixExecutionCancellator);
    console.attachToProcess(processHandler);
    return console;
  }

  public static BaseTestsOutputConsoleView createConsoleWithCustomLocator(@NotNull final String testFrameworkName,
                                                                          @NotNull final TestConsoleProperties consoleProperties,
                                                                          ExecutionEnvironment environment,
                                                                          @Nullable final TestLocationProvider locator,
                                                                          @NotNull final CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    return createConsoleWithCustomLocator(testFrameworkName,
                                          consoleProperties,
                                          environment,
                                          new CompositeTestLocationProvider(locator),
                                          false,
                                          null,
                                          matrixExecutionCancellator);
  }

  public static GoogleCloudTestingConsoleView createConsoleWithCustomLocator(@NotNull final String testFrameworkName,
                                                                    @NotNull final TestConsoleProperties consoleProperties,
                                                                    ExecutionEnvironment environment,
                                                                    @Nullable final SMTestLocator locator,
                                                                    final boolean idBasedTreeConstruction,
                                                                    @Nullable final TestProxyFilterProvider filterProvider,
                                                                    @NotNull final CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    String splitterPropertyName = getSplitterPropertyName(testFrameworkName);
    GoogleCloudTestingConsoleView consoleView = new GoogleCloudTestingConsoleView(consoleProperties,
                                                                environment,
                                                                splitterPropertyName);
    initConsoleView(consoleView, testFrameworkName, locator, idBasedTreeConstruction, filterProvider, matrixExecutionCancellator);
    return consoleView;
  }

  @NotNull
  public static String getSplitterPropertyName(@NotNull String testFrameworkName) {
    return testFrameworkName + ".Splitter.Proportion";
  }

  public static void initConsoleView(@NotNull final GoogleCloudTestingConsoleView consoleView,
                                     @NotNull final String testFrameworkName,
                                     @Nullable final SMTestLocator locator,
                                     final boolean idBasedTreeConstruction,
                                     @Nullable final TestProxyFilterProvider filterProvider,
                                     @NotNull final CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    consoleView.addAttachToProcessListener(new AttachToProcessListener() {
      @Override
      public void onAttachToProcess(@NotNull ProcessHandler processHandler) {
        TestProxyPrinterProvider printerProvider = null;
        if (filterProvider != null) {
          printerProvider = new TestProxyPrinterProvider(consoleView, filterProvider);
        }
        GoogleCloudTestingResultsForm resultsForm = consoleView.getResultsViewer();
        attachEventsProcessors(consoleView.getProperties(),
                               resultsForm,
                               processHandler,
                               testFrameworkName,
                               locator,
                               idBasedTreeConstruction,
                               printerProvider,
                               matrixExecutionCancellator);
      }
    });
    consoleView.setHelpId("reference.runToolWindow.testResultsTab");
    consoleView.initUI();
  }

  public static BaseTestsOutputConsoleView createConsole(@NotNull final String testFrameworkName,
                                                         @NotNull final TestConsoleProperties consoleProperties,
                                                         ExecutionEnvironment environment,
                                                         @NotNull final CloudMatrixExecutionCancellator matrixExecutionCancellator) {

    return createConsoleWithCustomLocator(testFrameworkName, consoleProperties, environment, null, matrixExecutionCancellator);
  }

  /**
   * In debug mode SM Runner will check events consistency. All errors will be reported using IDEA errors logger.
   * This mode must be disabled in production. The most widespread false positives were detected when you debug tests.
   * In such cases Test Framework may fire events several times, etc.
   * @return true if in debug mode, otherwise false.
   */
  public static boolean isInDebugMode() {
    return Boolean.valueOf(System.getProperty(TEST_RUNNER_DEBUG_MODE_PROPERTY));
  }

  private static ProcessHandler attachEventsProcessors(@NotNull final TestConsoleProperties consoleProperties,
                                                       final GoogleCloudTestingResultsForm resultsViewer,
                                                       final ProcessHandler processHandler,
                                                       @NotNull final String testFrameworkName,
                                                       @Nullable final SMTestLocator locator,
                                                       boolean idBasedTreeConstruction,
                                                       @Nullable TestProxyPrinterProvider printerProvider,
                                                       @NotNull final CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    //build messages consumer
    final OutputToGoogleCloudTestEventsConverter
      outputConsumer = new OutputToGoogleCloudTestEventsConverter(testFrameworkName, consoleProperties);

    //events processor
    final GoogleCloudTestEventsProcessor eventsProcessor;
    if (idBasedTreeConstruction) {
      throw new RuntimeException("ID-based converter is not supported!");
      //eventsProcessor = new GoogleCloudTestingBasedToSMTRunnerEventsConvertor(resultsViewer.getTestsRootNode(), testFrameworkName);
    } else {
      eventsProcessor = new GoogleCloudTestingToSMTRunnerEventsConvertor(resultsViewer.getTestsRootNode(), testFrameworkName);
    }
    if (locator != null) {
      eventsProcessor.setLocator(locator);
    }
    if (printerProvider != null) {
      eventsProcessor.setPrinterProvider(printerProvider);
    }

    // ui actions
    final GoogleCloudTestingUIActionsHandler uiActionsHandler = new GoogleCloudTestingUIActionsHandler(consoleProperties);

    // subscribe on events

    // subscribes event processor on output consumer events
    outputConsumer.setProcessor(eventsProcessor);
    // subscribes result viewer on event processor
    eventsProcessor.addEventsListener(resultsViewer);
    // subscribes test runner's actions on results viewer events
    resultsViewer.addEventsListener(uiActionsHandler);
    // subscribes statistics tab viewer on event processor

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        matrixExecutionCancellator.cancel();
        outputConsumer.flushBufferBeforeTerminating();
        eventsProcessor.onFinishTesting();

        Disposer.dispose(eventsProcessor);
        Disposer.dispose(outputConsumer);
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        eventsProcessor.onStartTesting();
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        outputConsumer.process(event.getText(), outputType);
      }
    });
    return processHandler;
  }

}
