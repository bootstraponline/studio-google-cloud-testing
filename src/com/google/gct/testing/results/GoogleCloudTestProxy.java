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

import com.intellij.execution.Location;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMStacktraceParser;
import com.intellij.execution.testframework.sm.TestsLocationProviderUtil;
import com.intellij.execution.testframework.sm.runner.states.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GoogleCloudTestProxy extends AbstractTestProxy {
  private static final Logger LOG = Logger.getInstance(GoogleCloudTestProxy.class.getName());

  private List<GoogleCloudTestProxy> myChildren;
  private GoogleCloudTestProxy myParent;

  private AbstractState myState = NotRunState.getInstance();
  private final String myName;
  private Long myDuration = null; // duration is unknown
  @Nullable private final String myLocationUrl;
  private boolean myDurationIsCached = false; // is used for separating unknown and unset duration
  private boolean myHasCriticalErrors = false;
  private boolean myHasErrorsCached = false;
  private boolean myHasPassedTests = false;
  private boolean myHasPassedTestsCached = false;

  @Nullable private String myStacktrace;

  private final boolean myIsSuite;
  private boolean myIsEmptyIsCached = false; // is used for separating unknown and unset values
  private boolean myIsEmpty = true;
  TestLocationProvider myLocator = null;
  private final boolean myPreservePresentableName;
  private Printer myPreferredPrinter = null;

  public GoogleCloudTestProxy(final String testName, final boolean isSuite, @Nullable final String locationUrl) {
    this(testName, isSuite, locationUrl, false);
  }

  public GoogleCloudTestProxy(final String testName,
                              final boolean isSuite,
                              @Nullable final String locationUrl,
                              boolean preservePresentableName) {
    myName = testName;
    myIsSuite = isSuite;
    myLocationUrl = locationUrl;
    myPreservePresentableName = preservePresentableName;
  }

  public void setLocator(@NotNull TestLocationProvider locator) {
    myLocator = locator;
  }

  public void setPreferredPrinter(@NotNull Printer preferredPrinter) {
    myPreferredPrinter = preferredPrinter;
  }

  @Override
  public boolean isInProgress() {
    return myState.isInProgress();
  }

  @Override
  public boolean isDefect() {
    return myState.isDefect();
  }

  public boolean isError() {
    return myState instanceof TestErrorState;
  }

  public boolean isFailure() {
    return myState instanceof TestFailedState;
  }

  @Override
  public boolean shouldRun() {
    return true;
  }

  @Override
  public int getMagnitude() {
    // Is used by some of Tests Filters

    //WARN: It is Hack, see PoolOfTestStates, API is necessary
    return getMagnitudeInfo().getValue();
  }

  public TestStateInfo.Magnitude getMagnitudeInfo() {
    return myState.getMagnitude();
  }

  public boolean hasErrors() {
    // if already cached
    if (myHasErrorsCached) {
      return myHasCriticalErrors;
    }

    final boolean canCacheErrors = !myState.isInProgress();
    // calculate
    final boolean hasErrors = calcHasErrors();
    if (canCacheErrors) {
      myHasCriticalErrors = hasErrors;
      myHasErrorsCached = true;
    }
    return hasErrors;
  }

  private boolean calcHasErrors() {
    if (myHasCriticalErrors) {
      return true;
    }

    for (GoogleCloudTestProxy child : getChildren()) {
      if (child.hasErrors()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true if the state is final (PASSED, FAILED, IGNORED, TERMINATED)
   */
  public boolean isFinal() {
    return myState.isFinal();
  }

  private void setStacktraceIfNotSet(@Nullable String stacktrace) {
    if (myStacktrace == null) myStacktrace = stacktrace;
  }

  @Override
  public boolean isLeaf() {
    return myChildren == null || myChildren.isEmpty();
  }

  @Override
  public boolean isInterrupted() {
    return myState.wasTerminated();
  }

  boolean hasPassedTests() {
    if (myHasPassedTestsCached) {
      return myHasPassedTests;
    }
    boolean hasPassedTests = calcPassedTests();
    boolean canCache = !myState.isInProgress();
    if (canCache) {
      myHasPassedTests = hasPassedTests;
      myHasPassedTestsCached = true;
    }
    return hasPassedTests;
  }

  boolean hasScheduledOrPendingConfigurations() {
    for (GoogleCloudTestProxy configuration : getChildren()) {
      if (configuration.getMagnitudeInfo() == TestStateInfo.Magnitude.SCHEDULED_INDEX
        || configuration.getMagnitudeInfo() == TestStateInfo.Magnitude.RUNNING_INDEX) {
        return true;
      }
    }
    return false;
  }

  private boolean calcPassedTests() {
    if (isPassed()) {
      return true;
    }
    for (GoogleCloudTestProxy child : getChildren()) {
      if (child.hasPassedTests()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isIgnored() {
    if (hasPassedTests()) {
      return false;
    }
    return myState.getMagnitude() == TestStateInfo.Magnitude.IGNORED_INDEX;
  }

  @Override
  public boolean isPassed() {
    return myState.getMagnitude() == TestStateInfo.Magnitude.SKIPPED_INDEX ||
           myState.getMagnitude() == TestStateInfo.Magnitude.COMPLETE_INDEX ||
           myState.getMagnitude() == TestStateInfo.Magnitude.PASSED_INDEX; 
  }

  public void addChild(final GoogleCloudTestProxy child) {
    if (myChildren == null) {
      myChildren = new ArrayList<GoogleCloudTestProxy>();
    }

    myChildren.add(child);

    // At this point, add printables just for leaf nodes (i.e., individual tests) to avoid grouping effect.
    //
    // add link to child's future output in correct place
    // actually if after this suite will obtain output
    // it will place it after this child and before future child
    if (this.getParent() != null && this.getParent().getParent() != null) {
      addLast(child);
      final GoogleCloudTestProxy configurationNode = this.getParent();
      configurationNode.addLast(child);
      configurationNode.getParent().addLast(child);
    }

    // add child
    //
    //TODO reset children cache
    child.setParent(this);
    // if parent is being printed then all childs output
    // should be also send to the same printer
    child.setPrinter(myPrinter);
    if (myPreferredPrinter != null && child.myPreferredPrinter == null) {
      child.setPreferredPrinter(myPreferredPrinter);
    }
  }

  @Nullable
  private Printer getRightPrinter(@Nullable Printer printer) {
    if (myPreferredPrinter != null && printer != null) {
      return myPreferredPrinter;
    }
    return printer;
  }

  @Override
  public void setPrinter(Printer printer) {
    super.setPrinter(getRightPrinter(printer));
  }


  @Override
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public Location getLocation(final Project project, GlobalSearchScope searchScope) {
    //determines location of test proxy

    //TODO multiresolve support

    if (myLocationUrl == null || myLocator == null) {
      return null;
    }

    final String protocolId = VirtualFileManager.extractProtocol(myLocationUrl);
    final String path = TestsLocationProviderUtil.extractPath(myLocationUrl);

    if (protocolId != null && path != null) {
      List<Location> locations = myLocator.getLocation(protocolId, path, project);
      if (!locations.isEmpty()) {
        return locations.iterator().next();
      }
    }

    return null;
  }

  @Override
  @Nullable
  public Navigatable getDescriptor(final Location location, final TestConsoleProperties testConsoleProperties) {
    // by location gets navigatable element.
    // It can be file or place in file (e.g. when OPEN_FAILURE_LINE is enabled)
    if (location == null) return null;

    final String stacktrace = myStacktrace;
    if (stacktrace != null && (testConsoleProperties instanceof SMStacktraceParser) && isLeaf()) {
      final Navigatable result = ((SMStacktraceParser)testConsoleProperties).getErrorNavigatable(location.getProject(), stacktrace);
      if (result != null) {
        return result;
      }
    }

    return EditSourceUtil.getDescriptor(location.getPsiElement());
  }

  public boolean isSuite() {
    return myIsSuite;
  }

  @Override
  public GoogleCloudTestProxy getParent() {
    return myParent;
  }

  @Override
  public List<? extends GoogleCloudTestProxy> getChildren() {
    return myChildren != null ? myChildren : Collections.<GoogleCloudTestProxy>emptyList();
  }

  @Override
  public List<GoogleCloudTestProxy> getAllTests() {
    final List<GoogleCloudTestProxy> allTests = new ArrayList<GoogleCloudTestProxy>();

    allTests.add(this);

    for (GoogleCloudTestProxy child : getChildren()) {
      allTests.addAll(child.getAllTests());
    }

    return allTests;
  }

  public void setStarted() {
    myState = !myIsSuite ? TestInProgressState.TEST : new GoogleCloudTestingSuiteInProgressState(this);
  }

  public void setScheduled() {
    myState = !myIsSuite ? TestInProgressState.TEST : new GoogleCloudTestingScheduledState(this);
  }

  /**
   * Calculates and caches duration of test or suite
   * @return null if duration is unknown, otherwise duration value in milliseconds;
   */
  @Nullable
  @Override
  public Long getDuration() {
    // Returns duration value for tests
    // or cached duration for suites
    if (myDurationIsCached || !isSuite()) {
      return myDuration;
    }

    //For suites counts and caches durations of its children. Also it evaluates partial duration,
    //i.e. if duration is unknown it will be ignored in summary value.
    //If duration for all children is unknown summary duration will be also unknown
    //if one of children is ignored - it's duration will be 0 and if child wasn't run,
    //then it's duration will be unknown
    myDuration = calcSuiteDuration();
    myDurationIsCached = true;

    return myDuration;
  }

  @Override
  public boolean shouldSkipRootNodeForExport() {
    return true;
  }

  /**
   * Sets duration of test
   * @param duration In milliseconds
   */
  public void setDuration(final long duration) {
    invalidateCachedDurationForContainerSuites();

    if (!isSuite()) {
      myDurationIsCached = true;
      myDuration = (duration >= 0) ? duration : null;
      return;
    }

    // Not allow to directly set duration for suites.
    // It should be the sum of children. This requirement is only
    // for safety of current model and may be changed
    LOG.warn("Unsupported operation");
  }

  public void setFinished() {
    if (myState.isFinal()) {
      // we shouldn't fire new printable because final state
      // has been already fired
      return;
    }

    if (!isSuite()) {
      // if isn't in other finished state (ignored, failed or passed)
      myState = TestPassedState.INSTANCE;
    } else {
      //Test Suite
      myState = determineSuiteStateOnFinished();
    }
    // prints final state additional info
    fireOnNewPrintable(myState);
  }

  public void setTestFailed(@NotNull String localizedMessage,
                            @Nullable final String stackTrace,
                            final boolean testError) {
    setStacktraceIfNotSet(stackTrace);
    //TODO: In future, we might not want to show the name of the enclosing configuration unless the root node is selected.
    localizedMessage = getParent().getParent().getName() + "\n\t" + localizedMessage;
    if (myState instanceof TestFailedState) {
      ((TestFailedState) myState).addError(localizedMessage, stackTrace, myPrinter);
    }
    else {
      myState = testError
                ? new TestErrorState(localizedMessage, stackTrace)
                : new TestFailedState(localizedMessage, stackTrace);
      fireOnNewPrintable(myState);
    }
  }

  public void setTestComparisonFailed(@NotNull final String localizedMessage,
                                      @Nullable final String stackTrace,
                                      @NotNull final String actualText,
                                      @NotNull final String expectedText) {
    setStacktraceIfNotSet(stackTrace);
    myState = new TestComparisionFailedState(localizedMessage, stackTrace,
                                             actualText, expectedText);
    fireOnNewPrintable(myState);
  }

  public void setTestIgnored(@Nullable String ignoreComment, @Nullable String stackTrace) {
    setStacktraceIfNotSet(stackTrace);
    myState = new TestIgnoredState(ignoreComment, stackTrace);
    fireOnNewPrintable(myState);
  }

  public void setParent(@Nullable final GoogleCloudTestProxy parent) {
    myParent = parent;
  }

  public List<? extends GoogleCloudTestProxy> collectChildren(@Nullable final Filter<GoogleCloudTestProxy> filter) {
    return filterChildren(filter, collectChildren());
  }

  public List<? extends GoogleCloudTestProxy> collectChildren() {
    final List<? extends GoogleCloudTestProxy> allChildren = getChildren();

    final List<GoogleCloudTestProxy> result = ContainerUtilRt.newArrayList();

    result.addAll(allChildren);

    for (GoogleCloudTestProxy p: allChildren) {
      result.addAll(p.collectChildren());
    }

    return result;
  }

  public List<? extends GoogleCloudTestProxy> getChildren(@Nullable final Filter<? super GoogleCloudTestProxy> filter) {
    final List<? extends GoogleCloudTestProxy> allChildren = getChildren();

    return filterChildren(filter, allChildren);
  }

  private static List<? extends GoogleCloudTestProxy> filterChildren(@Nullable Filter<? super GoogleCloudTestProxy> filter,
                                                            List<? extends GoogleCloudTestProxy> allChildren) {
    if (filter == Filter.NO_FILTER || filter == null) {
      return allChildren;
    }

    final List<GoogleCloudTestProxy> selectedChildren = new ArrayList<GoogleCloudTestProxy>();
    for (GoogleCloudTestProxy child : allChildren) {
      if (filter.shouldAccept(child)) {
        selectedChildren.add(child);
      }
    }

    if ((selectedChildren.isEmpty())) {
      return Collections.<GoogleCloudTestProxy>emptyList();
    }
    return selectedChildren;
  }

  public boolean wasLaunched() {
    return myState.wasLaunched();
  }


  /**
   * Prints this proxy and all its children on given printer
   * @param printer Printer
   */
  @Override
  public void printOn(final Printer printer) {
    final Printer rightPrinter = getRightPrinter(printer);
    super.printOn(rightPrinter);
    final AbstractState oldState = myState;

    CompositePrintable.invokeInAlarm(new Runnable() {
      @Override
      public void run() {
        //Tests State, that provide and formats additional output
        oldState.printOn(rightPrinter);
      }
    });
  }

  public void addStdOutput(final String output, final Key outputType, boolean shouldFireEvent) {
    GoogleCloudTestProxy parent = this.getParent();
    if (parent != null) {
      if (parent instanceof GoogleCloudRootTestProxy) {
        //Prefix output with the configuration name.
        String configurationPrefix = getName() + "\n\t";
        //Fire the event on the parent such that if this configuration is selected (i.e., the parent is NOT selected),
        //the parent's printer will be null, and thus, the prefix will not be printed (the desired behavior).
        parent.fireOnNewPrintable(printableFromString(configurationPrefix, outputType));
        parent.addStdOutput(configurationPrefix + output, outputType, false);
      } else {
        parent.addStdOutput(output, outputType, false);
      }
    }

    Printable outputPrintable = printableFromString(output, outputType);
    if (shouldFireEvent) {
      addLast(outputPrintable);
    } else {
      myNestedPrintables.add(outputPrintable);
    }
  }

  private Printable printableFromString(final String text, final Key outputType) {
    return new Printable() {
        @Override
        public void printOn(final Printer printer) {
          printer.print(text, ConsoleViewContentType.getConsoleViewType(outputType));
        }
      };
  }

  public void addStdErr(final String output, boolean shouldFireEvent) {
    Printable printable = new Printable() {
      @Override
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.ERROR_OUTPUT);
      }
    };
    if (shouldFireEvent) {
      addLast(printable);
    } else {
      myNestedPrintables.add(printable);
    }
    GoogleCloudTestProxy parent = this.getParent();
    if (parent != null) {
      parent.addStdErr(output, false);
    }
  }

  /**
   * This method was left for backward compatibility.
   *
   * @param output
   * @param stackTrace
   * @deprecated use GoogleCloudTestProxy.addError(String output, String stackTrace, boolean isCritical)
   */
  @Deprecated
  public void addError(final String output,
                       @Nullable final String stackTrace) {
    addError(output, stackTrace, true);
  }

  public void addError(final String output,
                       @Nullable final String stackTrace,
                       final boolean isCritical) {
    myHasCriticalErrors = isCritical;
    setStacktraceIfNotSet(stackTrace);

    addLast(new Printable() {
      @Override
      public void printOn(final Printer printer) {
        final String errorText = TestFailedState.buildErrorPresentationText(output, stackTrace);
        LOG.assertTrue(errorText != null);

        TestFailedState.printError(printer, Arrays.asList(errorText));
      }
    });
  }

  public void addSystemOutput(final String output) {
    addLast(new Printable() {
      @Override
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
  }

  @NotNull
  public String getPresentableName() {
    if (myPreservePresentableName) {
      return GoogleCloudTestsPresentationUtil.getPresentableNameTrimmedOnly(this);
    }
    return GoogleCloudTestsPresentationUtil.getPresentableName(this);
  }

  @Override
  @Nullable
  public AssertEqualsDiffViewerProvider getDiffViewerProvider() {
    if (myState instanceof AssertEqualsDiffViewerProvider) {
      return (AssertEqualsDiffViewerProvider)myState;
    }
    return null;
  }

  @Override
  public String toString() {
    return getPresentableName();
  }

  /**
   * Process was terminated.
   */
  public void setTerminated() {
    if (myState.isFinal()) {
      return;
    }
    myState = TerminatedState.INSTANCE;
    for (GoogleCloudTestProxy child : getChildren()) {
      child.setTerminated();
    }
    fireOnNewPrintable(myState);
  }

  /**
   * Process has timed out.
   */
  public void setTimeout() {
    if (myState.isFinal()) {
      return;
    }
    myState = new GoogleCloudTestingTimeoutState(this);
    for (GoogleCloudTestProxy child : getChildren()) {
      child.setTimeout();
    }
    fireOnNewPrintable(myState);
  }

  /**
   * Process' infrastructure has failed.
   */
  public void setInfrastructureFailed() {
    if (myState.isFinal()) {
      return;
    }
    myState = new GoogleCloudTestingInfrastructureFailureState(this);
    for (GoogleCloudTestProxy child : getChildren()) {
      child.setInfrastructureFailed();
    }
    fireOnNewPrintable(myState);
  }

  public boolean wasTerminated() {
    return myState.wasTerminated();
  }

  @Nullable
  protected String getLocationUrl() {
    return myLocationUrl;
  }

  /**
   * Check if suite contains error tests or suites
   * @return True if contains
   */
  private boolean containsErrorTests() {
    final List<? extends GoogleCloudTestProxy> children = getChildren();
    for (GoogleCloudTestProxy child : children) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX) {
        return true;
      }
    }
    return false;
  }

  private boolean containsFailedTests() {
    final List<? extends GoogleCloudTestProxy> children = getChildren();
    for (GoogleCloudTestProxy child : children) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines site state after it has been finished
   * @return New state
   */
  protected AbstractState determineSuiteStateOnFinished() {
    final AbstractState state;
    if (isLeaf()) {
      state = SuiteFinishedState.EMPTY_LEAF_SUITE;
    } else if (isEmptySuite() && !isDefect()) {
      state = SuiteFinishedState.EMPTY_SUITE;
    } else {
      if (isDefect()) {
        if (containsTerminatedChildren()) { //Terminated has precedence over time out and infrastructure failure.
          state = TerminatedState.INSTANCE;
        } else if (containsTimeoutChildren()) { //Timeout has precedence over infrastructure failure.
          state = new GoogleCloudTestingTimeoutState(this);
        } else if (containsInfrastructureFailureChildren()) {
          state = new GoogleCloudTestingInfrastructureFailureState(this);
        } else
        if (containsErrorTests()) {
          // Test suit contains errors if at least one of its tests contains error
          state = SuiteFinishedState.ERROR_SUITE;
        } else {
          // if suite contains failed tests - all suite should be
          // consider as failed
          state = containsFailedTests()
                  ? SuiteFinishedState.FAILED_SUITE
                  : SuiteFinishedState.WITH_IGNORED_TESTS_SUITE;
        }
      } else {
        state = SuiteFinishedState.PASSED_SUITE;
      }
    }
    return state;
  }

  private boolean containsTerminatedChildren() {
    for (GoogleCloudTestProxy child : getChildren()) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.TERMINATED_INDEX) {
        return true;
      }
    }
    return false;
  }

  private boolean containsTimeoutChildren() {
    for (GoogleCloudTestProxy child : getChildren()) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.TIMEOUT_INDEX) {
        return true;
      }
    }
    return false;
  }

  private boolean containsInfrastructureFailureChildren() {
    for (GoogleCloudTestProxy child : getChildren()) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.INFRASTRUCTURE_FAILURE_INDEX) {
        return true;
      }
    }
    return false;
  }

  public boolean isEmptySuite() {
    if (myIsEmptyIsCached) {
      return myIsEmpty;
    }

    if (!isSuite()) {
      // test - no matter what we will return
      myIsEmpty = true;
      myIsEmptyIsCached = true;

      return myIsEmpty;
    }

    myIsEmpty = true;
    final List<? extends GoogleCloudTestProxy> allTestCases = getChildren();
    for (GoogleCloudTestProxy testOrSuite : allTestCases) {
      if (testOrSuite.isSuite()) {
        // suite
        if (!testOrSuite.isEmptySuite()) {
          // => parent suite isn't empty
          myIsEmpty = false;
          myIsEmptyIsCached = true;
          break;
        }
        // all suites are empty
        myIsEmpty = true;
        // we can cache only final state, otherwise test may be added
        myIsEmptyIsCached = myState.isFinal();
      } else {
        // test => parent suite isn't empty
        myIsEmpty = false;
        myIsEmptyIsCached = true;
        break;
      }
    }
    return myIsEmpty;
  }



  @Nullable
  private Long calcSuiteDuration() {
    long partialDuration = 0;
    boolean durationOfChildrenIsUnknown = true;

    for (GoogleCloudTestProxy child : getChildren()) {
      final Long duration = child.getDuration();
      if (duration != null) {
        durationOfChildrenIsUnknown = false;
        partialDuration += duration.longValue();
      }
    }
    // Lets convert partial duration in integer object. Negative partial duration
    // means that duration of all children is unknown
    return durationOfChildrenIsUnknown ? null : partialDuration;
  }

  /**
   * Recursively invalidates cached duration for container(parent) suites
   */
  private void invalidateCachedDurationForContainerSuites() {
    // Invalidates duration of this suite
    myDuration = null;
    myDurationIsCached = false;

    // Invalidates duration of container suite
    final GoogleCloudTestProxy containerSuite = getParent();
    if (containerSuite != null) {
      containerSuite.invalidateCachedDurationForContainerSuites();
    }
  }

  public static class GoogleCloudRootTestProxy extends GoogleCloudTestProxy {
    private String testRunId;
    private boolean myTestsReporterAttached; // false by default

    public GoogleCloudRootTestProxy() {
      super("[root]", true, null);
    }

    public String getTestRunId() {
      return testRunId;
    }

    public void setTestRunId(String testRunId ) {
      this.testRunId = testRunId;
    }

    public void setTestsReporterAttached() {
      myTestsReporterAttached = true;
    }

    public boolean isTestsReporterAttached() {
      return myTestsReporterAttached;
    }

    @Override
    protected AbstractState determineSuiteStateOnFinished() {
      if (isLeaf() && !isTestsReporterAttached()) {
        return SuiteFinishedState.TESTS_REPORTER_NOT_ATTACHED;
      }
      return super.determineSuiteStateOnFinished();
    }
  }
}
