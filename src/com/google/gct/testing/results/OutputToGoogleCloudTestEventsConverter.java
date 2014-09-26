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

import com.google.gct.testing.GoogleCloudTestingUtils;
import com.google.gct.testing.results.events.*;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.OutputLineSplitter;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.Map;

import static com.google.gct.testing.GoogleCloudTestingUtils.ConfigurationStopReason;

/**
 *         This implementation also supports messages splitted in parts by early flush.
 *         Implementation assumes that buffer is being flushed on line end or by timer,
 *         i.e. incomming text contains no more than one line's end marker ('\r', '\n', or "\r\n")
 *         (e.g. process was run with IDEA program's runner)
 */
public class OutputToGoogleCloudTestEventsConverter implements GoogleCloudTestingProcessOutputConsumer {
  private static final Logger LOG = Logger.getInstance(OutputToGoogleCloudTestEventsConverter.class.getName());

  private GoogleCloudTestEventsProcessor myProcessor;
  private final MyServiceMessageVisitor myServiceMessageVisitor;
  private final String myTestFrameworkName;

  private final OutputLineSplitter mySplitter;
  private boolean myPendingLineBreakFlag;

  public OutputToGoogleCloudTestEventsConverter(@NotNull final String testFrameworkName,
                                                @NotNull final TestConsoleProperties consoleProperties) {
    myTestFrameworkName = testFrameworkName;
    myServiceMessageVisitor = new MyServiceMessageVisitor();

    mySplitter = new OutputLineSplitter(consoleProperties.isEditable()) {
      @Override
      protected void onLineAvailable(@NotNull String text, @NotNull Key outputType, boolean tcLikeFakeOutput) {
        processConsistentText(text, outputType, tcLikeFakeOutput);
      }
    };
  }

  @Override
  public void setProcessor(@Nullable final GoogleCloudTestEventsProcessor processor) {
    myProcessor = processor;
  }

  @Override
  public void dispose() {
    setProcessor(null);
  }

  @Override
  public void process(final String text, final Key outputType) {
    mySplitter.process(text, outputType);
  }

  /**
   * Flashes the rest of stdout text buffer after output has been stopped
   */
  @Override
  public void flushBufferBeforeTerminating() {
    mySplitter.flush();
    if (myPendingLineBreakFlag) {
      fireOnUncapturedLineBreak();
    }
  }

  private void fireOnUncapturedLineBreak() {
    fireOnUncapturedOutput("\n", ProcessOutputTypes.STDOUT);
  }

  private void processConsistentText(final String text, final Key outputType, boolean tcLikeFakeOutput) {
    try {
      if (!processServiceMessages(text, outputType, myServiceMessageVisitor)) {
        if (myPendingLineBreakFlag) {
          // output type for line break isn't important
          // we may use any, e.g. current one
          fireOnUncapturedLineBreak();
          myPendingLineBreakFlag = false;
        }
        // Filters \n
        String outputToProcess = text;
        if (tcLikeFakeOutput && text.endsWith("\n")) {
          // ServiceMessages protocol requires that every message
          // should start with new line, so such behaviour may led to generating
          // some number of useless \n.
          //
          // IDEA process handler flush output by size or line break
          // So:
          //  1. "a\n\nb\n" -> ["a\n", "\n", "b\n"]
          //  2. "a\n##teamcity[..]\n" -> ["a\n", "#teamcity[..]\n"]
          // We need distinguish 1) and 2) cases, in 2) first linebreak is redundant and must be ignored
          // in 2) linebreak must be considered as output
          // output will be in TestOutput message
          // Lets set myPendingLineBreakFlag if we meet "\n" and then ignore it or apply depending on
          // next output chunk
          myPendingLineBreakFlag = true;
          outputToProcess = outputToProcess.substring(0, outputToProcess.length() - 1);
        }
        //fire current output
        fireOnUncapturedOutput(outputToProcess, outputType);
      }
      else {
        myPendingLineBreakFlag = false;
      }
    }
    catch (ParseException e) {

      LOG.error(GeneralToSMTRunnerEventsConvertor.getTFrameworkPrefix(myTestFrameworkName) + "Error parsing text: [" + text + "]", e);
    }
  }

  protected boolean processServiceMessages(final String text,
                                           final Key outputType,
                                           final ServiceMessageVisitor visitor) throws ParseException {
    // service message parser expects line like "##teamcity[ .... ]" without whitespaces in the end.
    final ServiceMessage message = ServiceMessage.parse(text.trim());
    if (message != null) {
      message.visit(visitor);
    }
    return message != null;
  }


  private void fireOnTestStarted(@NotNull GoogleCloudTestStartedEvent testStartedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestStarted(testStartedEvent);
    }
  }

  private void fireOnTestFailure(@NotNull GoogleCloudTestFailedEvent testFailedEvent) {
    assertNotNull(testFailedEvent.getLocalizedFailureMessage());

    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestFailure(testFailedEvent);
    }
  }

  private void fireOnTestIgnored(@NotNull GoogleCloudTestIgnoredEvent testIgnoredEvent) {

    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestIgnored(testIgnoredEvent);
    }
  }

  private void fireOnTestFinished(@NotNull GoogleCloudTestFinishedEvent testFinishedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestFinished(testFinishedEvent);
    }
  }

  private void fireOnCustomProgressTestsCategory(final String categoryName,
                                                 int testsCount) {
    assertNotNull(categoryName);

    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      final boolean disableCustomMode = StringUtil.isEmpty(categoryName);
      processor.onCustomProgressTestsCategory(disableCustomMode ? null : categoryName,
                                              disableCustomMode ? 0 : testsCount);
    }
  }

  private void fireOnCustomProgressTestStarted() {
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onCustomProgressTestStarted();
    }
  }

  private void fireOnCustomProgressTestFailed() {
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onCustomProgressTestFailed();
    }
  }

  private void fireOnTestFrameworkAttached() {
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestsReporterAttached();
    }
  }

  private void fireOnTestOutput(@NotNull GoogleCloudTestOutputEvent testOutputEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestOutput(testOutputEvent);
    }
  }

  private void fireOnUncapturedOutput(final String text, final Key outputType) {
    assertNotNull(text);

    if (StringUtil.isEmpty(text)) {
      return;
    }

    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onUncapturedOutput(text, outputType);
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestsCountInSuite(count);
    }
  }

  private void fireOnSuiteStarted(@NotNull TestSuiteStartedEvent suiteStartedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteStarted(suiteStartedEvent);
    }
  }

  private void fireOnSetTestRunId(@NotNull SetTestRunIdEvent setTestRunIdEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSetTestRunId(setTestRunIdEvent);
    }
  }

  private void fireOnConfigurationStopped(@NotNull com.google.gct.testing.results.events.TestConfigurationStoppedEvent configurationStoppedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onConfigurationStopped(configurationStoppedEvent);
    }
  }

  private void fireOnConfigurationStarted(@NotNull TestConfigurationStartedEvent configurationStartedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onConfigurationStarted(configurationStartedEvent);
    }
  }

  private void fireOnConfigurationProgress(@NotNull TestConfigurationProgressEvent configurationProgressEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onConfigurationProgress(configurationProgressEvent);
    }
  }

  private void fireOnConfigurationScheduled(@NotNull TestConfigurationScheduledEvent configurationScheduledEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onConfigurationScheduled(configurationScheduledEvent);
    }
  }

  private void fireOnConfigurationFinished(@NotNull TestConfigurationFinishedEvent configurationFinishedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onConfigurationFinished(configurationFinishedEvent);
    }
  }

  private void fireOnSuiteFinished(@NotNull TestSuiteFinishedEvent suiteFinishedEvent) {
    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteFinished(suiteFinishedEvent);
    }
  }

  protected void fireOnErrorMsg(final String localizedMessage,
                                @Nullable final String stackTrace,
                                boolean isCritical) {
    assertNotNull(localizedMessage);

    // local variable is used to prevent concurrent modification
    final GoogleCloudTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onError(localizedMessage, stackTrace, isCritical);
    }
  }

  private void assertNotNull(final String s) {
    if (s == null) {
      LOG.error(GeneralToSMTRunnerEventsConvertor.getTFrameworkPrefix(myTestFrameworkName) + " @NotNull value is expected.");
    }
  }

  private class MyServiceMessageVisitor extends DefaultServiceMessageVisitor {
    @NonNls public static final String KEY_TESTS_COUNT = "testCount";
    @NonNls private static final String ATTR_KEY_TEST_ERROR = "error";
    @NonNls private static final String ATTR_KEY_TEST_COUNT = "count";
    @NonNls private static final String ATTR_KEY_TEST_DURATION = "duration";
    @NonNls private static final String ATTR_KEY_LOCATION_URL = "locationHint";
    @NonNls private static final String ATTR_KEY_LOCATION_URL_OLD = "location";
    @NonNls private static final String ATTR_KEY_STACKTRACE_DETAILS = "details";
    @NonNls private static final String ATTR_KEY_DIAGNOSTIC = "diagnosticInfo";

    @NonNls private static final String MESSAGE = "message";
    @NonNls private static final String TEST_REPORTER_ATTACHED = "enteredTheMatrix";
    @NonNls private static final String ATTR_KEY_STATUS = "status";
    @NonNls private static final String ATTR_VALUE_STATUS_ERROR = "ERROR";
    @NonNls private static final String ATTR_VALUE_STATUS_WARNING = "WARNING";
    @NonNls private static final String ATTR_KEY_TEXT = "text";
    @NonNls private static final String ATTR_KEY_ERROR_DETAILS = "errorDetails";

    @NonNls public static final String CUSTOM_STATUS = "customProgressStatus";
    @NonNls private static final String ATTR_KEY_TEST_TYPE = "type";
    @NonNls private static final String ATTR_KEY_TESTS_CATEGORY = "testsCategory";
    @NonNls private static final String ATTR_VAL_TEST_STARTED = "testStarted";
    @NonNls private static final String ATTR_VAL_TEST_FAILED = "testFailed";

    @Override
    public void visitTestSuiteStarted(@NotNull final TestSuiteStarted suiteStarted) {
      final String locationUrl = fetchTestLocation(suiteStarted);
      TestSuiteStartedEvent suiteStartedEvent = new TestSuiteStartedEvent(suiteStarted, locationUrl);
      fireOnSuiteStarted(suiteStartedEvent);
    }

    @Nullable
    private String fetchTestLocation(final TestSuiteStarted suiteStarted) {
      final Map<String, String> attrs = suiteStarted.getAttributes();
      final String location = attrs.get(ATTR_KEY_LOCATION_URL);
      if (location == null) {
        // try old API
        final String oldLocation = attrs.get(ATTR_KEY_LOCATION_URL_OLD);
        if (oldLocation != null) {
          LOG.error(GeneralToSMTRunnerEventsConvertor.getTFrameworkPrefix(myTestFrameworkName)
                    +
                    "Test Runner API was changed for TeamCity 5.0 compatibility. Please use 'locationHint' attribute instead of 'location'.");
          return oldLocation;
        }
        return null;
      }
      return location;
    }

    @Override
    public void visitTestSuiteFinished(@NotNull final TestSuiteFinished suiteFinished) {
      TestSuiteFinishedEvent finishedEvent = new TestSuiteFinishedEvent(suiteFinished);
      fireOnSuiteFinished(finishedEvent);
    }

    @Override
    public void visitTestStarted(@NotNull final TestStarted testStarted) {
      // TODO
      // final String locationUrl = testStarted.getLocationHint();

      final String locationUrl = testStarted.getAttributes().get(ATTR_KEY_LOCATION_URL);
      GoogleCloudTestStartedEvent testStartedEvent = new GoogleCloudTestStartedEvent(testStarted, locationUrl);
      fireOnTestStarted(testStartedEvent);
    }

    @Override
    public void visitTestFinished(@NotNull final TestFinished testFinished) {
      //TODO
      //final Integer duration = testFinished.getTestDuration();
      //fireOnTestFinished(testFinished.getTestName(), duration != null ? duration.intValue() : 0);

      final String durationStr = testFinished.getAttributes().get(ATTR_KEY_TEST_DURATION);

      // Test duration in milliseconds
      long duration = 0;

      if (!StringUtil.isEmptyOrSpaces(durationStr)) {
        duration = convertToLong(durationStr, testFinished);
      }

      GoogleCloudTestFinishedEvent testFinishedEvent = new GoogleCloudTestFinishedEvent(testFinished, duration);
      fireOnTestFinished(testFinishedEvent);
    }

    @Override
    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      final String stacktrace = testIgnored.getAttributes().get(ATTR_KEY_STACKTRACE_DETAILS);
      fireOnTestIgnored(new GoogleCloudTestIgnoredEvent(testIgnored, stacktrace));
    }

    @Override
    public void visitTestStdOut(@NotNull final TestStdOut testStdOut) {
      fireOnTestOutput(new GoogleCloudTestOutputEvent(testStdOut, testStdOut.getStdOut(), true, testStdOut.getAttributes().get("configuration"),
                                                testStdOut.getAttributes().get("className")));
    }

    @Override
    public void visitTestStdErr(@NotNull final TestStdErr testStdErr) {
      fireOnTestOutput(new GoogleCloudTestOutputEvent(testStdErr, testStdErr.getStdErr(), false, testStdErr.getAttributes().get("configuration"),
                                                testStdErr.getAttributes().get("className")));
    }

    @Override
    public void visitTestFailed(@NotNull final TestFailed testFailed) {
      final boolean testError = testFailed.getAttributes().get(ATTR_KEY_TEST_ERROR) != null;
      GoogleCloudTestFailedEvent testFailedEvent = new GoogleCloudTestFailedEvent(testFailed, testError);
      fireOnTestFailure(testFailedEvent);
    }

    @Override
    public void visitPublishArtifacts(@NotNull final PublishArtifacts publishArtifacts) {
      //Do nothing
    }

    @Override
    public void visitProgressMessage(@NotNull final ProgressMessage progressMessage) {
      //Do nothing
    }

    @Override
    public void visitProgressStart(@NotNull final ProgressStart progressStart) {
      //Do nothing
    }

    @Override
    public void visitProgressFinish(@NotNull final ProgressFinish progressFinish) {
      //Do nothing
    }

    @Override
    public void visitBuildStatus(@NotNull final BuildStatus buildStatus) {
      //Do nothing
    }

    @Override
    public void visitBuildNumber(@NotNull final BuildNumber buildNumber) {
      //Do nothing
    }

    @Override
    public void visitBuildStatisticValue(@NotNull final BuildStatisticValue buildStatsValue) {
      //Do nothing
    }

    @Override
    public void visitMessageWithStatus(@NotNull Message msg) {
      final Map<String, String> msgAttrs = msg.getAttributes();

      final String text = msgAttrs.get(ATTR_KEY_TEXT);
      if (!StringUtil.isEmpty(text)) {
        // msg status
        final String status = msgAttrs.get(ATTR_KEY_STATUS);
        if (status.equals(ATTR_VALUE_STATUS_ERROR)) {
          // error msg

          final String stackTrace = msgAttrs.get(ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, true);
        }
        else if (status.equals(ATTR_VALUE_STATUS_WARNING)) {
          // warning msg

          // let's show warning via stderr
          final String stackTrace = msgAttrs.get(ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, false);
        }
        else {
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      }
    }

    @Override
    public void visitServiceMessage(@NotNull final ServiceMessage msg) {
      final String name = msg.getMessageName();

      if (KEY_TESTS_COUNT.equals(name)) {
        processTestCountInSuite(msg);
      }
      else if (CUSTOM_STATUS.equals(name)) {
        processCustomStatus(msg);
      }
      else if (MESSAGE.equals(name)) {
        final Map<String, String> msgAttrs = msg.getAttributes();

        final String text = msgAttrs.get(ATTR_KEY_TEXT);
        if (!StringUtil.isEmpty(text)) {
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      }
      else if (TEST_REPORTER_ATTACHED.equals(name)) {
        fireOnTestFrameworkAttached();
      }
      else {
        handleCustomMessages(msg);
      }
    }

    private void handleCustomMessages(@NotNull final ServiceMessage message) {
      final String messageName = message.getMessageName();

      if (messageName.equals(GoogleCloudTestingUtils.SET_TEST_RUN_ID)) {
        SetTestRunIdEvent setTestRunIdEvent = new SetTestRunIdEvent(message.getAttributes().get("testRunId"));
        fireOnSetTestRunId(setTestRunIdEvent);
      } else {
        String configurationName = message.getAttributes().get("name");
        if (messageName.equals(GoogleCloudTestingUtils.TEST_CONFIGURATION_STOPPED)) {
          ConfigurationStopReason stopReason = ConfigurationStopReason.valueOf(message.getAttributes().get("stopReason"));
          TestConfigurationStoppedEvent configurationStoppedEvent = new TestConfigurationStoppedEvent(configurationName, stopReason);
          fireOnConfigurationStopped(configurationStoppedEvent);
        } else if (messageName.equals(GoogleCloudTestingUtils.TEST_CONFIGURATION_PROGRESS)) {
          TestConfigurationProgressEvent configurationProgressEvent =
            new TestConfigurationProgressEvent(configurationName, message.getAttributes().get("text"));
          fireOnConfigurationProgress(configurationProgressEvent);
        } else if (messageName.equals(GoogleCloudTestingUtils.TEST_CONFIGURATION_SCHEDULED)) {
          TestConfigurationScheduledEvent configurationScheduledEvent = new TestConfigurationScheduledEvent(configurationName);
          fireOnConfigurationScheduled(configurationScheduledEvent);
        } else if (messageName.equals(GoogleCloudTestingUtils.TEST_CONFIGURATION_STARTED)) {
          TestConfigurationStartedEvent configurationStartedEvent = new TestConfigurationStartedEvent(configurationName);
          fireOnConfigurationStarted(configurationStartedEvent);
        } else if (messageName.equals(GoogleCloudTestingUtils.TEST_CONFIGURATION_FINISHED)) {
          TestConfigurationFinishedEvent configurationFinishedEvent = new TestConfigurationFinishedEvent(configurationName);
          fireOnConfigurationFinished(configurationFinishedEvent);
        } else {
          GeneralToSMTRunnerEventsConvertor.logProblem(LOG, "Unexpected service message:" + messageName, myTestFrameworkName);
        }
      }
    }

    private void processTestCountInSuite(final ServiceMessage msg) {
      final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
      fireOnTestsCountInSuite(convertToInt(countStr, msg));
    }

    private int convertToInt(String countStr, final ServiceMessage msg) {
      int count = 0;
      try {
        count = Integer.parseInt(countStr);
      }
      catch (NumberFormatException ex) {
        final String diagnosticInfo = msg.getAttributes().get(ATTR_KEY_DIAGNOSTIC);
        LOG.error(GeneralToSMTRunnerEventsConvertor.getTFrameworkPrefix(myTestFrameworkName) + "Parse integer error." + (diagnosticInfo == null ? "" : " " + diagnosticInfo),
                  ex);
      }
      return count;
    }

    private long convertToLong(final String countStr, @NotNull final ServiceMessage msg) {
      long count = 0;
      try {
        count = Long.parseLong(countStr);
      }
      catch (NumberFormatException ex) {
        final String diagnosticInfo = msg.getAttributes().get(ATTR_KEY_DIAGNOSTIC);
        LOG.error(GeneralToSMTRunnerEventsConvertor.getTFrameworkPrefix(myTestFrameworkName) + "Parse long error." + (diagnosticInfo == null ? "" : " " + diagnosticInfo), ex);
      }
      return count;
    }

    private void processCustomStatus(final ServiceMessage msg) {
      final Map<String, String> attrs = msg.getAttributes();
      final String msgType = attrs.get(ATTR_KEY_TEST_TYPE);
      if (msgType != null) {
        if (msgType.equals(ATTR_VAL_TEST_STARTED)) {
          fireOnCustomProgressTestStarted();
        }
        else if (msgType.equals(ATTR_VAL_TEST_FAILED)) {
          fireOnCustomProgressTestFailed();
        }
        return;
      }
      final String testsCategory = attrs.get(ATTR_KEY_TESTS_CATEGORY);
      if (testsCategory != null) {
        final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
        fireOnCustomProgressTestsCategory(testsCategory, convertToInt(countStr, msg));

        //noinspection UnnecessaryReturnStatement
        return;
      }
    }
  }
}
