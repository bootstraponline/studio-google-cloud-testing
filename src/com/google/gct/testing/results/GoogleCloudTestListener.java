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

import com.google.gct.testing.CloudTestingUtils;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import org.jetbrains.android.run.AndroidRunningState;
import org.jetbrains.android.run.testing.AndroidTestLocationProvider;

import java.util.Map;

import static com.google.gct.testing.CloudTestingUtils.ConfigurationStopReason;

public class GoogleCloudTestListener implements IGoogleCloudTestRunListener {
  private final AndroidRunningState myRunningState;
  private long myTestStartingTime;
  private long myTestSuiteStartingTime;
  private String myConfiguration = null;
  private String myTestClassName = null;
  private ProcessHandler myProcessHandler;

  public ProcessHandler getProcessHandler() {
    if (myProcessHandler == null) {
      myProcessHandler = myRunningState.getProcessHandler();
    }
    return myProcessHandler;
  }

  public GoogleCloudTestListener(AndroidRunningState runningState) {
    myRunningState = runningState;
  }

  @Override
  public void testRunStopped(long elapsedTime) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running stopped\n", ProcessOutputTypes.STDOUT);
    handler.destroyProcess();
  }

  @Override
  public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
    //if (myTestClassName != null) {
    //  testSuiteFinished();
    //}
    //if (myConfiguration != null) {
    //  testConfigurationFinished(myConfiguration);
    //  myConfiguration = null;
    //}
    final ProcessHandler handler = getProcessHandler();
    //handler.notifyTextAvailable("Finish\n", ProcessOutputTypes.STDOUT);
    handler.destroyProcess();
  }

  @Override
  public void testRunStarted(String runName, int testCount) {
    ProcessHandler handler = getProcessHandler();

    final ServiceMessageBuilder builder = new ServiceMessageBuilder("enteredTheMatrix");
    handler.notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testStarted(GoogleCloudTestIdentifier test) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testStarted");
    builder.addAttribute("configuration", test.getConfiguration());
    builder.addAttribute("className", test.getClassName());
    builder.addAttribute("name", test.getTestName());
    builder
      .addAttribute("locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" + myRunningState.getModule().getName() +
                                    ':' + test.getClassName() + '.' + test.getTestName() + "()");
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);

    myTestStartingTime = System.currentTimeMillis();
  }

  @Override
  public void setTestRunId(String testRunId) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder(CloudTestingUtils.SET_TEST_RUN_ID);
    builder.addAttribute("testRunId", testRunId);
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void stopTestConfiguration(String configurationName, ConfigurationStopReason stopReason) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder(CloudTestingUtils.TEST_CONFIGURATION_STOPPED);
    builder.addAttribute("name", configurationName);
    builder.addAttribute("stopReason" , stopReason.name());
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testConfigurationProgress(String configurationName, String progressMessage) {
    myConfiguration = configurationName;
    ServiceMessageBuilder builder = new ServiceMessageBuilder(CloudTestingUtils.TEST_CONFIGURATION_PROGRESS);
    builder.addAttribute("name", configurationName);
    builder.addAttribute("text", prepareProgressString(progressMessage));
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  private String prepareProgressString(String progressMessage) {
    return CloudTestingUtils.shouldShowProgressTimestamps()
      ? progressMessage.substring(0, progressMessage.length() - 1) + "\t" + System.currentTimeMillis() + "\n"
      : progressMessage;
  }

  @Override
  public void testConfigurationScheduled(String configurationName) {
    myConfiguration = configurationName;
    ServiceMessageBuilder builder = new ServiceMessageBuilder(CloudTestingUtils.TEST_CONFIGURATION_SCHEDULED);
    builder.addAttribute("name", configurationName);
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testConfigurationStarted(String configurationName) {
    myConfiguration = configurationName;
    ServiceMessageBuilder builder = new ServiceMessageBuilder(CloudTestingUtils.TEST_CONFIGURATION_STARTED);
    builder.addAttribute("name", configurationName);
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  //private void testSuiteStarted() {
  //  myTestSuiteStartingTime = System.currentTimeMillis();
  //  ServiceMessageBuilder builder = new ServiceMessageBuilder("testSuiteStarted");
  //  builder.addAttribute("name", myTestClassName);
  //  builder.addAttribute("locationHint", AndroidTestLocationProvider.PROTOCOL_ID + "://" +
  //                                       myRunningState.getModule().getName() + ':' + myTestClassName);
  //  getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  //}

  public void testConfigurationFinished(String configurationName) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder(CloudTestingUtils.TEST_CONFIGURATION_FINISHED);
    builder.addAttribute("name", configurationName);
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  //private void testSuiteFinished() {
  //  ServiceMessageBuilder builder = new ServiceMessageBuilder("testSuiteFinished");
  //  builder.addAttribute("name", myTestClassName);
  //  builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestSuiteStartingTime));
  //  getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  //  myTestClassName = null;
  //}

  @Override
  public void testFailed(TestFailure status, GoogleCloudTestIdentifier test, String stackTrace) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testFailed");
    builder.addAttribute("configuration", test.getConfiguration());
    builder.addAttribute("className", test.getClassName());
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("message", "");
    builder.addAttribute("details", stackTrace);
    if (status == TestFailure.ERROR) {
      builder.addAttribute("error", "true");
    }
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testEnded(GoogleCloudTestIdentifier test, Map<String, String> testMetrics) {
    ServiceMessageBuilder builder = new ServiceMessageBuilder("testFinished");
    builder.addAttribute("configuration", test.getConfiguration());
    builder.addAttribute("className", test.getClassName());
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestStartingTime));
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testRunFailed(String errorMessage) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running failed: " + errorMessage + "\n", ProcessOutputTypes.STDERR);
    handler.destroyProcess();
  }
}
