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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gct.testing.CloudTestingUtils;
import com.google.gct.testing.results.events.*;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.TestProxyPrinterProvider;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.google.gct.testing.CloudTestingUtils.ConfigurationStopReason;

/**
 * This class fires events to RTestUnitEventsListener in EventDispatch thread
 */
public class GoogleCloudTestingToSMTRunnerEventsConvertor extends GoogleCloudTestEventsProcessor {
  private static final Logger LOG = Logger.getInstance(GoogleCloudTestingToSMTRunnerEventsConvertor.class.getName());

  private final Map<String, GoogleCloudTestProxy> myRunningTestsFullNameToProxy = new HashMap<String, GoogleCloudTestProxy>();

  private final Set<AbstractTestProxy> myFailedTestsSet = new HashSet<AbstractTestProxy>();

  //private final GoogleCloudTestSuiteStack mySuitesStack = new GoogleCloudTestSuiteStack();
  private final List<GoogleCloudTestEventsListener> myEventsListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final GoogleCloudTestProxy.GoogleCloudRootTestProxy myTestsRootNode;
  private final String myTestFrameworkName;
  private boolean myIsTestingFinished;
  private TestLocationProvider myLocator = null;

  private GoogleCloudTestProxy lastUpdatedTest = null;

  public GoogleCloudTestingToSMTRunnerEventsConvertor(@NotNull final GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRootNode,
                                                      @NotNull final String testFrameworkName) {
    myTestsRootNode = testsRootNode;
    myTestFrameworkName = testFrameworkName;
  }

  @Override
  public void setLocator(@NotNull TestLocationProvider customLocator) {
    myLocator = customLocator;
  }

  @Override
  public void addEventsListener(@NotNull final GoogleCloudTestEventsListener listener) {
    myEventsListeners.add(listener);
  }

  @Override
  public void onStartTesting() {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        myTestsRootNode.setScheduled();
        lastUpdatedTest = null;
        //fire
        fireOnTestingStarted(true);
      }
    });
  }

  @Override
  public void onTestsReporterAttached() {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        myTestsRootNode.setTestsReporterAttached();
      }
    });
  }

  @Override
  public void onFinishTesting() {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        if (myIsTestingFinished) {
          // has been already invoked!
          return;
        }
        myIsTestingFinished = true;

        myRunningTestsFullNameToProxy.clear();
        lastUpdatedTest = null;
        //mySuitesStack.clear();
        if (myTestsRootNode.hasScheduledOrPendingConfigurations()) {
          // The testing has finished prematurely => must have been aborted by the user or caused by severe problems.
          myTestsRootNode.setTerminated();
        } else {
          myTestsRootNode.setFinished();
        }

        onUncapturedOutput(prepareFinishString(), ProcessOutputTypes.STDOUT);
        //fire events
        fireOnTestingFinished();
      }

      private String prepareFinishString() {
        return CloudTestingUtils.shouldShowProgressTimestamps()
          ? "Finish\t" + System.currentTimeMillis() + "\n"
          : "Finish\n";
      }
    });
  }

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
  }

  @Override
  public void onTestStarted(@NotNull final GoogleCloudTestStartedEvent testStartedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configuration = testStartedEvent.getConfiguration();
        final String className = testStartedEvent.getClassName();
        final String testName = testStartedEvent.getName();
        final String locationUrl = testStartedEvent.getLocationUrl();
        final String fullName = getFullTestName(configuration, className, testName);

        if (myRunningTestsFullNameToProxy.containsKey(fullName)) {
          //Duplicated event
          logProblem("Test [" + fullName + "] has been already started");

          if (GoogleCloudTestResultsConnectionUtil.isInDebugMode()) {
            return;
          }
        }

        GoogleCloudTestProxy parentSuite = findOrCreateChildNode(myTestsRootNode, configuration, true);
        parentSuite = findOrCreateChildNode(parentSuite, className, true);

        // creates test
        GoogleCloudTestProxy testProxy = new GoogleCloudTestProxy(testName, false, locationUrl);
        if (myLocator != null) {
          testProxy.setLocator(myLocator);
        }

        lastUpdatedTest = testProxy;

        parentSuite.addChild(testProxy);

        // adds to running tests map
        myRunningTestsFullNameToProxy.put(fullName, testProxy);

        //Progress started
        testProxy.setStarted();

        //fire events
        fireOnTestStarted(testProxy);
      }
    });
  }

  private String getFullTestName(String configuration, String className, String testName) {
    return configuration + ":" + className + ":" + testName;
  }

  private GoogleCloudTestProxy findOrCreateChildNode(GoogleCloudTestProxy parent, String nodeName, boolean shouldStartIfNotFound) {
    GoogleCloudTestProxy testProxy = Iterables.find(parent.getChildren(), getNodeNamed(nodeName), null);
    if (testProxy == null) {
      testProxy = new GoogleCloudTestProxy(nodeName, true, null);
      parent.addChild(testProxy);
      if (shouldStartIfNotFound) {
        //Progress started.
        testProxy.setStarted();
        //Fire events.
        fireOnTestStarted(testProxy);
      }
    }
    return testProxy;
  }

  private Predicate<GoogleCloudTestProxy> getNodeNamed(final String nodeName) {
    return new Predicate<GoogleCloudTestProxy>() {
      @Override
      public boolean apply(GoogleCloudTestProxy testProxy) {
        return testProxy.getName().equals(nodeName);
      }
    };
  }

  @Override
  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException("Unsupported event 'onSuiteStarted'");
        //final String suiteName = suiteStartedEvent.getName();
        //final String locationUrl = suiteStartedEvent.getLocationUrl();
        //final GoogleCloudTestProxy parentSuite = getCurrentSuite();
        ////new suite
        //GoogleCloudTestProxy newSuite = new GoogleCloudTestProxy(suiteName, true, locationUrl);
        //if (myLocator != null) {
        //  newSuite.setLocator(myLocator);
        //}
        //parentSuite.addChild(newSuite);
        //
        ////mySuitesStack.pushSuite(newSuite);
        //
        ////Progress started
        //newSuite.setStarted();
        //
        ////fire event
        //fireOnSuiteStarted(newSuite);
      }
    });
  }

  @Override
  public void onSetTestRunId(@NotNull com.google.gct.testing.results.events.SetTestRunIdEvent setTestRunIdEvent) {
    myTestsRootNode.setTestRunId(setTestRunIdEvent.getTestRunId());
  }

  @Override
  public void onConfigurationStopped(@NotNull final com.google.gct.testing.results.events.TestConfigurationStoppedEvent configurationStoppedEvent)  {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configurationName = configurationStoppedEvent.getName();
          for (GoogleCloudTestProxy configuration : myTestsRootNode.getChildren()) {
            if (configuration.getName().equals(configurationName)) {
              List<GoogleCloudTestProxy> suiteTests = configuration.getAllTests();
              // Should process children first for the correct status propagation, so remove itself from the list.
              suiteTests.remove(configuration);
              for (GoogleCloudTestProxy suiteTest : suiteTests) {
                stopTest(suiteTest, configurationStoppedEvent.getStopReason());
              }
              stopTest(configuration, configurationStoppedEvent.getStopReason());
              return;
            }
          }
          throw new IllegalStateException("Could not find configuration: " + configurationName);
      }
    });
  }

  @Override
  public void onConfigurationStarted(@NotNull final TestConfigurationStartedEvent configurationStartedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configurationName = configurationStartedEvent.getName();
        GoogleCloudTestProxy newConfiguration = findOrCreateChildNode(myTestsRootNode, configurationName, false);
        if (myLocator != null) {
          newConfiguration.setLocator(myLocator);
        }

        //Progress started
        newConfiguration.setStarted();

        //fire event
        fireOnSuiteStarted(newConfiguration);

        // Scheduled -> Pending for root node as soon as a configuration becomes pending.
        if (!myTestsRootNode.isInProgress()) {
          myTestsRootNode.setStarted();
          fireOnTestingStarted(false);
        }
      }
    });
  }

  @Override
  public void onConfigurationProgress(@NotNull final com.google.gct.testing.results.events.TestConfigurationProgressEvent configurationProgressEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configurationName = configurationProgressEvent.getName();
        final String progressText = configurationProgressEvent.getText();

        GoogleCloudTestProxy configurationProxy = Iterables.find(myTestsRootNode.getChildren(), getNodeNamed(configurationName), null);
        if (configurationProxy == null) {
          throw new IllegalStateException("Could not report progress for non-existing configuration: " + configurationName);
        }

        configurationProxy.addStdOutput(progressText, ProcessOutputTypes.STDOUT, true);
      }
    });
  }

  @Override
  public void onConfigurationScheduled(@NotNull final com.google.gct.testing.results.events.TestConfigurationScheduledEvent configurationScheduledEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configurationName = configurationScheduledEvent.getName();
        final String locationUrl = configurationScheduledEvent.getLocationUrl();

        GoogleCloudTestProxy newConfiguration = new GoogleCloudTestProxy(configurationName, true, locationUrl);
        if (myLocator != null) {
          newConfiguration.setLocator(myLocator);
        }
        myTestsRootNode.addChild(newConfiguration);

        newConfiguration.setScheduled();

        //fire event
        fireOnSuiteStarted(newConfiguration);
      }
    });
  }

  private void stopTest(GoogleCloudTestProxy test, ConfigurationStopReason stopReason) {
    switch (stopReason) {
      case FINISHED:
        test.setFinished();
        break;
      case INFRASTRUCTURE_FAILURE:
        test.setInfrastructureFailed();
        break;
      case TRIGGERING_ERROR:
        test.setTriggeringError();
        break;
      case TIMED_OUT:
        test.setTimeout();
        break;
    }
    fireOnTestFinished(test);
  }

  @Override
  public void onTestFinished(@NotNull final GoogleCloudTestFinishedEvent testFinishedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configuration = testFinishedEvent.getConfiguration();
        final String className = testFinishedEvent.getClassName();
        final String testName = testFinishedEvent.getName();
        final long duration = testFinishedEvent.getDuration();
        final String fullTestName = getFullTestName(configuration, className, testName);
        final GoogleCloudTestProxy testProxy = getProxyByFullTestName(fullTestName);

        if (testProxy == null) {
          logProblem("Test wasn't started! TestFinished event: name = {" + testName + "}. " +
                     cannotFindFullTestNameMsg(fullTestName));
          return;
        }

        lastUpdatedTest = testProxy;

        testProxy.setDuration(duration);
        testProxy.setFinished();
        myRunningTestsFullNameToProxy.remove(fullTestName);

        //fire events
        fireOnTestFinished(testProxy);
      }
    });
  }

  @Override
  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException("Unsupported event 'onSuiteFinished'");
        //final GoogleCloudTestProxy mySuite = mySuitesStack.popSuite(suiteFinishedEvent.getName());
        //if (mySuite != null) {
        //  mySuite.setFinished();
        //  //fire events
        //  fireOnSuiteFinished(mySuite);
        //}
      }
    });
  }

  @Override
  public void onConfigurationFinished(@NotNull final com.google.gct.testing.results.events.TestConfigurationFinishedEvent configurationFinishedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException("Unsupported event 'onConfigurationFinished'");
        //final String configurationName = configurationFinishedEvent.getName();
        //final GoogleCloudTestProxy mySuite = mySuitesStack.popSuite(configurationName);
        //if (mySuite != null) {
        //  mySuite.setFinished();
        //  //fire events
        //  //TODO: Is it really safe not to fire these events?
        //  //fireOnSuiteFinished(mySuite);
        //}
      }
    });
  }

  @Override
  public void onUncapturedOutput(@NotNull final String text, final Key outputType) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final GoogleCloudTestProxy currentProxy = findCurrentTestOrSuite();

        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          currentProxy.addStdErr(text, true);
        } else if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
          currentProxy.addSystemOutput(text);
        } else {
          currentProxy.addStdOutput(text, outputType, true);
        }
      }
    });
  }

  @Override
  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final GoogleCloudTestProxy currentProxy = findCurrentTestOrSuite();
        currentProxy.addError(localizedMessage, stackTrace, isCritical);
      }
    });
  }

  @Override
  public void onCustomProgressTestsCategory(@Nullable final String categoryName,
                                            final int testCount) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        fireOnCustomProgressTestsCategory(categoryName, testCount);
      }
    });
  }

  @Override
  public void onCustomProgressTestStarted() {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        fireOnCustomProgressTestStarted();
      }
    });
  }

  @Override
  public void onCustomProgressTestFailed() {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        fireOnCustomProgressTestFailed();
      }
    });
  }

  @Override
  public void onTestFailure(@NotNull final GoogleCloudTestFailedEvent testFailedEvent) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configuration = testFailedEvent.getConfiguration();
        final String className = testFailedEvent.getClassName();
        final String testName = ObjectUtils.assertNotNull(testFailedEvent.getName());
        final String localizedMessage = testFailedEvent.getLocalizedFailureMessage();
        final String stackTrace = testFailedEvent.getStacktrace();
        final boolean isTestError = testFailedEvent.isTestError();
        final String comparisionFailureActualText = testFailedEvent.getComparisonFailureActualText();
        final String comparisionFailureExpectedText = testFailedEvent.getComparisonFailureExpectedText();
        final boolean inDebugMode = GoogleCloudTestResultsConnectionUtil.isInDebugMode();

        final String fullTestName = getFullTestName(configuration, className, testName);
        GoogleCloudTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          logProblem("Test wasn't started! TestFailure event: name = {" + testName + "}" +
                             ", message = {" + localizedMessage + "}" +
                             ", stackTrace = {" + stackTrace + "}. " +
                             cannotFindFullTestNameMsg(fullTestName));
          if (inDebugMode) {
            return;
          } else {
            // try to fix the problem:
            if (!myFailedTestsSet.contains(testProxy)) {
              // if hasn't been already reported
              // 1. report
              //TODO: Get the actual configuration and class name through the test failed event.
              onTestStarted(new GoogleCloudTestStartedEvent(testName, null, configuration, className));
              // 2. add failure
              testProxy = getProxyByFullTestName(fullTestName);
            }
          }
        }

        if (testProxy == null) {
          return;
        }

        lastUpdatedTest = testProxy;

        if (comparisionFailureActualText != null && comparisionFailureExpectedText != null) {
          if (myFailedTestsSet.contains(testProxy)) {
            // duplicate message
            logProblem("Duplicate failure for test [" + fullTestName + "]: msg = " + localizedMessage + ", stacktrace = " + stackTrace);

            if (inDebugMode) {
              return;
            }
          }

          testProxy.setTestComparisonFailed(localizedMessage, stackTrace,
                                            comparisionFailureActualText, comparisionFailureExpectedText);
        } else if (comparisionFailureActualText == null && comparisionFailureExpectedText == null) {
          testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);
        } else {
          logProblem("Comparison failure actual and expected texts should be both null or not null.\n"
                     + "Expected:\n"
                     + comparisionFailureExpectedText + "\n"
                     + "Actual:\n"
                     + comparisionFailureActualText);
        }

        myFailedTestsSet.add(testProxy);

        // fire event
        fireOnTestFailed(testProxy);
      }
    });
  }

  @Override
  public void onTestIgnored(@NotNull final GoogleCloudTestIgnoredEvent testIgnoredEvent) {
     addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configuration = testIgnoredEvent.getConfiguration();
        final String className = testIgnoredEvent.getClassName();
        final String testName = ObjectUtils.assertNotNull(testIgnoredEvent.getName());
        String ignoreComment = testIgnoredEvent.getIgnoreComment();
        if (StringUtil.isEmpty(ignoreComment)) {
          ignoreComment = SMTestsRunnerBundle.message("sm.test.runner.states.test.is.ignored");
        }
        final String stackTrace = testIgnoredEvent.getStacktrace();
        final String fullTestName = getFullTestName(configuration, className, testName);
        GoogleCloudTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          final boolean debugMode = GoogleCloudTestResultsConnectionUtil.isInDebugMode();
          logProblem("Test wasn't started! " +
                     "TestIgnored event: name = {" + testName + "}, " +
                     "message = {" + ignoreComment + "}. " +
                     cannotFindFullTestNameMsg(fullTestName));
          if (debugMode) {
            return;
          } else {
            // try to fix
            // 1. report test opened
            //TODO: Get the actual configuration and class name through the test failed event.
            onTestStarted(new GoogleCloudTestStartedEvent(testName, null, configuration, className));

            // 2. report failure
            testProxy = getProxyByFullTestName(fullTestName);
          }

        }
        if (testProxy == null) {
          return;
        }

        lastUpdatedTest = testProxy;

        testProxy.setTestIgnored(ignoreComment, stackTrace);

        // fire event
        fireOnTestIgnored(testProxy);
      }
    });
  }

  @Override
  public void onTestOutput(@NotNull final GoogleCloudTestOutputEvent testOutputEvent) {
     addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final String configuration = testOutputEvent.getConfiguration();
        final String className = testOutputEvent.getClassName();
        final String testName = testOutputEvent.getName();
        final String text = testOutputEvent.getText();
        final boolean stdOut = testOutputEvent.isStdOut();
        final String fullTestName = getFullTestName(configuration, className, testName);
        final GoogleCloudTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          logProblem("Test wasn't started! TestOutput event: name = {" + testName + "}, " +
                     "isStdOut = " + stdOut + ", " +
                     "text = {" + text + "}. " +
                     cannotFindFullTestNameMsg(fullTestName));
          return;
        }

        lastUpdatedTest = testProxy;

        if (stdOut) {
          testProxy.addStdOutput(text, ProcessOutputTypes.STDOUT, true);
        } else {
          testProxy.addStdErr(text, true);
        }
      }
    });
  }

  @Override
  public void onTestsCountInSuite(final int count) {
     addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        fireOnTestsCountInSuite(count);
      }
    });
  }

  //@NotNull
  //protected final GoogleCloudTestProxy getCurrentSuite() {
  //  final GoogleCloudTestProxy currentSuite = mySuitesStack.getCurrentSuite();
  //
  //  if (currentSuite != null) {
  //    return currentSuite;
  //  }
  //
  //  // current suite shouldn't be null otherwise test runner isn't correct
  //  // or may be we are in debug mode
  //  logProblem("Current suite is undefined. Root suite will be used.");
  //  return myTestsRootNode;
  //
  //}
 
  protected int getRunningTestsQuantity() {
    return myRunningTestsFullNameToProxy.size();
  }

  protected Set<AbstractTestProxy> getFailedTestsSet() {
    return Collections.unmodifiableSet(myFailedTestsSet);
  }

  @Nullable
  protected GoogleCloudTestProxy getProxyByFullTestName(final String fullTestName) {
    return myRunningTestsFullNameToProxy.get(fullTestName);
  }

  @TestOnly
  protected void clearInternalSuitesStack() {
    //mySuitesStack.clear();
  }

  private String cannotFindFullTestNameMsg(String fullTestName) {
    return "Cant find running test for ["
              + fullTestName
              + "]. Current running tests: {"
              + dumpRunningTestsNames() + "}";
  }

  private StringBuilder dumpRunningTestsNames() {
    final Set<String> names = myRunningTestsFullNameToProxy.keySet();
    final StringBuilder namesDump = new StringBuilder();
    for (String name : names) {
      namesDump.append('[').append(name).append(']').append(',');
    }
    return namesDump;
  }

  private void fireOnTestingStarted(boolean printTestingStartedTime) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestingStarted(myTestsRootNode, printTestingStartedTime);
    }
  }

  private void fireOnTestingFinished() {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestingFinished(myTestsRootNode);
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestsCountInSuite(count);
    }
  }


  private void fireOnTestStarted(final GoogleCloudTestProxy test) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestStarted(test);
    }
  }

  private void fireOnTestFinished(final GoogleCloudTestProxy test) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestFinished(test);
    }
  }

  private void fireOnTestFailed(final GoogleCloudTestProxy test) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestFailed(test);
    }
  }

  private void fireOnTestIgnored(final GoogleCloudTestProxy test) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onTestIgnored(test);
    }
  }

  private void fireOnSuiteStarted(final GoogleCloudTestProxy suite) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onSuiteStarted(suite);
    }
  }

  private void fireOnSuiteFinished(final GoogleCloudTestProxy suite) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onSuiteFinished(suite);
    }
  }


  private void fireOnCustomProgressTestsCategory(@Nullable final String categoryName, int testCount) {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestsCategory(categoryName, testCount);
    }
  }

  private void fireOnCustomProgressTestStarted() {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestStarted();
    }
  }

  private void fireOnCustomProgressTestFailed() {
    for (GoogleCloudTestEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestFailed();
    }
  }

  /*
   * Remove listeners,  etc
   */
  @Override
  public void dispose() {
    super.dispose();
     addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        myEventsListeners.clear();

        if (!myRunningTestsFullNameToProxy.isEmpty()) {
          final Application application = ApplicationManager.getApplication();
          if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            logProblem("Not all events were processed! " + dumpRunningTestsNames());
          }
        }
        myRunningTestsFullNameToProxy.clear();
        //mySuitesStack.clear();
      }
    });
  }

  private GoogleCloudTestProxy findCurrentTestOrSuite() {
    if (lastUpdatedTest != null) {
      return lastUpdatedTest;
    }
    return myTestsRootNode;
  }

  public static String getTFrameworkPrefix(final String testFrameworkName) {
    return "[" + testFrameworkName + "]: ";
  }

  private void logProblem(final String msg) {
    logProblem(LOG, msg, myTestFrameworkName);
  }

  public static void logProblem(final Logger log, final String msg, final String testFrameworkName) {
    logProblem(log, msg, GoogleCloudTestResultsConnectionUtil.isInDebugMode(), testFrameworkName);
  }

  public static void logProblem(final Logger log, final String msg, boolean throwError, final String testFrameworkName) {
    final String text = getTFrameworkPrefix(testFrameworkName) + msg;
    if (throwError) {
      log.error(text);
    }
    else {
      log.warn(text);
    }
  }
}
