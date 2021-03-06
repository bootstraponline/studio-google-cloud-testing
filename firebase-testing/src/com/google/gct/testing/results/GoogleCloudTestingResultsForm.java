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

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GoogleCloudTestingResultsForm extends TestResultsPanel
  implements TestFrameworkRunningModel, GoogleCloudTestResultsViewer, GoogleCloudTestEventsListener {
  @NonNls private static final String DEFAULT_SM_RUNNER_SPLITTER_PROPERTY = "SMTestRunner.Splitter.Proportion";

  public static final Color DARK_YELLOW = JBColor.YELLOW.darker();

  private GoogleCloudTestTreeView myTreeView;

  private TestsProgressAnimator myTestAnimator;

  /**
   * Fake parent suite for all tests and suites
   */
  private final GoogleCloudTestProxy.GoogleCloudRootTestProxy myTestsRootNode;
  private GoogleCloudTestTreeBuilder myTreeBuilder;
  private final TestConsoleProperties myConsoleProperties;

  private final List<EventsListener> myEventListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private GoogleCloudTestingPropagateSelectionHandler myShowStatisticForProxyHandler;

  private final Project myProject;

  private int myTotalTestCount = 0;
  private int myStartedTestCount = 0;
  private int myFinishedTestCount = 0;
  private int myFailedTestCount = 0;
  private int myIgnoredTestCount = 0;
  private long myStartTime;
  private long myEndTime;

  // custom progress
  private String myCurrentCustomProgressCategory;
  private final Set<String> myMentionedCategories = new LinkedHashSet<String>();
  private boolean myTestsRunning = true;

  public GoogleCloudTestingResultsForm(final RunProfile runProfile,
                                       @NotNull final JComponent console,
                                       final TestConsoleProperties consoleProperties) {
    this(runProfile, console, AnAction.EMPTY_ARRAY, consoleProperties, null);
  }

  public GoogleCloudTestingResultsForm(final RunProfile runProfile,
                                       @NotNull final JComponent console,
                                       AnAction[] consoleActions,
                                       final TestConsoleProperties consoleProperties,
                                       final String splitterPropertyName) {
    super(console, consoleActions, consoleProperties,
          splitterPropertyName != null ? splitterPropertyName : DEFAULT_SM_RUNNER_SPLITTER_PROPERTY, 0.5f);
    myConsoleProperties = consoleProperties;

    myProject = ((RunConfiguration)runProfile).getProject();

    //Create tests common suite root
    //noinspection HardCodedStringLiteral
    myTestsRootNode = new GoogleCloudTestProxy.GoogleCloudRootTestProxy();
    //todo myTestsRootNode.setOutputFilePath(runConfiguration.getOutputFilePath());

    // Fire selection changed and move focus on SHIFT+ENTER
    //TODO[romeo] improve
    /*
    final ArrayList<Component> components = new ArrayList<Component>();
    components.add(myTreeView);
    components.add(myTabs.getComponent());
    myContentPane.setFocusTraversalPolicy(new MyFocusTraversalPolicy(components));
    myContentPane.setFocusCycleRoot(true);
    */
  }

  @Override
  public void initUI() {
    super.initUI();

    final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    SMRunnerUtil.registerAsAction(shiftEnterKey, "show-statistics-for-test-proxy",
                                  new Runnable() {
                                    @Override
                                    public void run() {
                                      showStatisticsForSelectedProxy();
                                    }
                                  },
                                  myTreeView);
  }

  @Override
  protected ToolbarPanel createToolbarPanel() {
    return new GoogleCloudTestRunnerToolbarPanel(myConsoleProperties, this, this);
  }

  @Override
  protected JComponent createTestTreeView() {
    myTreeView = new GoogleCloudTestTreeView();

    myTreeView.setLargeModel(true);
    myTreeView.attachToModel(this);
    myTreeView.setTestResultsViewer(this);

    final GoogleCloudTestTreeStructure structure = new GoogleCloudTestTreeStructure(myProject, myTestsRootNode);
    myTreeBuilder = new GoogleCloudTestTreeBuilder(myTreeView, structure);
    myTreeBuilder.setTestsComparator(TestConsoleProperties.SORT_ALPHABETICALLY.value(myProperties));
    Disposer.register(this, myTreeBuilder);

    myTestAnimator = new MyAnimator(myTreeBuilder);

    //TODO always hide root node
    //myTreeView.setRootVisible(false);

    return myTreeView;
  }

  public void addTestsTreeSelectionListener(final TreeSelectionListener listener) {
    myTreeView.getSelectionModel().addTreeSelectionListener(listener);
  }

  /**
   * Is used for navigation from tree view to other UI components
   *
   * @param handler
   */
  @Override
  public void setShowStatisticForProxyHandler(final GoogleCloudTestingPropagateSelectionHandler handler) {
    myShowStatisticForProxyHandler = handler;
  }

  /**
   * Returns root node, fake parent suite for all tests and suites
   *
   * @param testsRoot
   * @return
   */
  @Override
  public void onTestingStarted(@NotNull GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRoot, boolean printTestingStartedTime) {
    myTestAnimator.setCurrentTestCase(myTestsRootNode);

    // Status line
    myStatusLine.setStatusColor(ColorProgressBar.GREEN);

    // Tests tree
    selectAndNotify(myTestsRootNode);

    myStartTime = System.currentTimeMillis();

    if (printTestingStartedTime) {
      myTestsRootNode.addSystemOutput("Testing started at "
                                      + DateFormatUtil.formatTime(myStartTime)
                                      + " ...\n");
    }

    updateStatusLabel(false);

    fireOnTestingStarted();
  }

  @Override
  public void onTestingFinished(@NotNull GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRoot) {
    myEndTime = System.currentTimeMillis();

    if (myTotalTestCount == 0) {
      myTotalTestCount = myStartedTestCount;
      myStatusLine.setFraction(1);
    }

    updateStatusLabel(true);
    updateIconProgress();

    myTestAnimator.stopMovie();
    myTreeBuilder.updateFromRoot();

    LvcsHelper.addLabel(this);

    selectAndNotify(myTestsRootNode, new Runnable() {
      @Override
      public void run() {
        myTestsRunning = false;
      }
    });

    fireOnTestingFinished();
  }

  @Override
  public void onTestsCountInSuite(final int count) {
    updateCountersAndProgressOnTestCount(count, false);
  }

  /**
   * Adds test to tree and updates status line.
   * Test proxy should be initialized, proxy parent must be some suite (already added to tree)
   *
   * @param testProxy Proxy
   */
  @Override
  public void onTestStarted(@NotNull final GoogleCloudTestProxy testProxy) {
    updateOnTestStarted(false);
    _addTestOrSuite(testProxy);
    fireOnTestNodeAdded(testProxy);
  }

  @Override
  public void onTestFailed(@NotNull final GoogleCloudTestProxy test) {
    updateOnTestFailed(false);
    updateIconProgress();
  }

  @Override
  public void onTestIgnored(@NotNull final GoogleCloudTestProxy test) {
    updateOnTestIgnored();
  }

  /**
   * Adds suite to tree
   * Suite proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then suite will be added to tests root.
   *
   * @param newSuite Tests suite
   */
  @Override
  public void onSuiteStarted(@NotNull final GoogleCloudTestProxy newSuite) {
    _addTestOrSuite(newSuite);
  }

  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, int testCount) {
    myCurrentCustomProgressCategory = categoryName;
    updateCountersAndProgressOnTestCount(testCount, true);
  }

  @Override
  public void onCustomProgressTestStarted() {
    updateOnTestStarted(true);
  }

  @Override
  public void onCustomProgressTestFailed() {
    updateOnTestFailed(true);
  }

  @Override
  public void onTestFinished(@NotNull final GoogleCloudTestProxy test) {
    updateOnTestFinished(false);
    updateIconProgress();
  }

  @Override
  public void onSuiteFinished(@NotNull final GoogleCloudTestProxy suite) {
    //Do nothing
  }

  @Override
  public GoogleCloudTestProxy.GoogleCloudRootTestProxy getTestsRootNode() {
    return myTestsRootNode;
  }

  @Override
  public TestConsoleProperties getProperties() {
    return myConsoleProperties;
  }

  @Override
  public void setFilter(final Filter filter) {
    // is used by Test Runner actions, e.g. hide passed, etc
    final GoogleCloudTestTreeStructure treeStructure = myTreeBuilder.getGoogleCloudTestTreeStructure();
    treeStructure.setFilter(filter);

    // TODO - show correct info if no children are available
    // (e.g no tests found or all tests passed, etc.)
    // treeStructure.getChildElements(treeStructure.getRootElement()).length == 0

    myTreeBuilder.queueUpdate();
  }

  @Override
  public boolean isRunning() {
    return myTestsRunning;
  }

  @Override
  public TestTreeView getTreeView() {
    return myTreeView;
  }

  @Override
  public GoogleCloudTestTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  @Override
  public boolean hasTestSuites() {
    return !getRoot().getChildren().isEmpty();
  }

  @Override
  @NotNull
  public AbstractTestProxy getRoot() {
    return myTestsRootNode;
  }

  /**
   * Manual test proxy selection in tests tree. E.g. do select root node on
   * testing started or do select current node if TRACK_RUNNING_TEST is enabled
   * <p/>
   * <p/>
   * Will select proxy in Event Dispatch Thread. Invocation of this
   * method may be not in event dispatch thread
   *
   * @param testProxy Test or suite
   */
  @Override
  public void selectAndNotify(AbstractTestProxy testProxy) {
    selectAndNotify(testProxy, null);
  }

  private void selectAndNotify(@Nullable final AbstractTestProxy testProxy, @Nullable Runnable onDone) {
    selectWithoutNotify(testProxy, onDone);

    // Is used by Statistic tab to differ use selection in tree
    // from manual selection from API (e.g. test runner events)
    showStatisticsForSelectedProxy(testProxy, false);
  }

  @Override
  public void addEventsListener(final EventsListener listener) {
    myEventListeners.add(listener);
    addTestsTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        //We should fire event only if it was generated by this component,
        //e.g. it is focused. Otherwise it is side effect of selecting proxy in
        //try by other component
        //if (myTreeView.isFocusOwner()) {
        @Nullable final GoogleCloudTestProxy selectedProxy = (GoogleCloudTestProxy)getTreeView().getSelectedTest();
        listener.onSelected(selectedProxy, GoogleCloudTestingResultsForm.this, GoogleCloudTestingResultsForm.this);
        //}
      }
    });
  }

  @Override
  public void dispose() {
    super.dispose();
    myShowStatisticForProxyHandler = null;
    myEventListeners.clear();
  }

  @Override
  public void showStatisticsForSelectedProxy() {
    TestConsoleProperties.SHOW_STATISTICS.set(myProperties, true);
    final AbstractTestProxy selectedProxy = myTreeView.getSelectedTest();
    showStatisticsForSelectedProxy(selectedProxy, true);
  }

  private void showStatisticsForSelectedProxy(final AbstractTestProxy selectedProxy,
                                              final boolean requestFocus) {
    if (selectedProxy instanceof GoogleCloudTestProxy && myShowStatisticForProxyHandler != null) {
      myShowStatisticForProxyHandler.handlePropagateSelectionRequest((GoogleCloudTestProxy)selectedProxy, this, requestFocus);
    }
  }

  protected int getTotalTestCount() {
    return myTotalTestCount;
  }

  protected int getStartedTestCount() {
    return myStartedTestCount;
  }

  protected int getFinishedTestCount() {
    return myFinishedTestCount;
  }

  protected int getFailedTestCount() {
    return myFailedTestCount;
  }

  protected int getIgnoredTestCount() {
    return myIgnoredTestCount;
  }

  protected Color getTestsStatusColor() {
    return myStatusLine.getStatusColor();
  }

  public Set<String> getMentionedCategories() {
    return myMentionedCategories;
  }

  protected long getStartTime() {
    return myStartTime;
  }

  protected long getEndTime() {
    return myEndTime;
  }

  private void _addTestOrSuite(@NotNull final GoogleCloudTestProxy newTestOrSuite) {

    final GoogleCloudTestProxy parentSuite = newTestOrSuite.getParent();
    assert parentSuite != null;

    // Tree
    myTreeBuilder.updateTestsSubtree(parentSuite);
    myTreeBuilder.repaintWithParents(newTestOrSuite);

    myTestAnimator.setCurrentTestCase(newTestOrSuite);
  }

  private void fireOnTestNodeAdded(final GoogleCloudTestProxy test) {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestNodeAdded(this, test);
    }
  }

  private void fireOnTestingFinished() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingFinished(this);
    }
  }

  private void fireOnTestingStarted() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingStarted(this);
    }
  }

  private void selectWithoutNotify(final AbstractTestProxy testProxy, @Nullable final Runnable onDone) {
    if (testProxy == null) {
      return;
    }

    SMRunnerUtil.runInEventDispatchThread(new Runnable() {
      @Override
      public void run() {
        if (myTreeBuilder.isDisposed()) {
          return;
        }
        myTreeBuilder.select(testProxy, onDone);
      }
    }, ModalityState.NON_MODAL);
  }

  private void updateStatusLabel(final boolean testingFinished) {
    if (myFailedTestCount > 0) {
      myStatusLine.setStatusColor(ColorProgressBar.RED);
    }
    else if (myIgnoredTestCount > 0) {
      myStatusLine.setStatusColor(DARK_YELLOW);
    }

    if (testingFinished) {
      if (myTotalTestCount == 0) {
        myStatusLine.setStatusColor(myTestsRootNode.wasLaunched() || !myTestsRootNode.isTestsReporterAttached()
                                    ? JBColor.LIGHT_GRAY
                                    : ColorProgressBar.RED);
      }
      // else color will be according failed/passed tests
    }

    // launchedAndFinished - is launched and not in progress. If we remove "launched' that onTestingStarted() before
    // initializing will be "launchedAndFinished"
    final boolean launchedAndFinished = myTestsRootNode.wasLaunched() && !myTestsRootNode.isInProgress();
    if (!TestsPresentationUtil.hasNonDefaultCategories(myMentionedCategories)) {
      // TODO: For firebase test runs no statistics (e.g., total tests count, passed tests count, etc.) make sense,
      // so showing only duration for now.
      //myStatusLine.formatTestMessage(myTotalTestCount, myFinishedTestCount, myFailedTestCount, myIgnoredTestCount, myTestsRootNode.getDuration(), myEndTime);
      if (myTestsRootNode.getDuration() == null || myEndTime == 0) {
        myStatusLine.setText("");
      } else {
        myStatusLine.setText("Total time – " + StringUtil.formatDuration(myTestsRootNode.getDuration()));
      }
    }
    else {
      myStatusLine.setText(TestsPresentationUtil.getProgressStatus_Text(myStartTime, myEndTime,
                                                                        myTotalTestCount, myFinishedTestCount,
                                                                        myFailedTestCount, myMentionedCategories,
                                                                        launchedAndFinished));
    }
  }

  /**
   * for java unit tests
   */
  public void performUpdate() {
    myTreeBuilder.performUpdate();
  }

  private void updateIconProgress() {
    final int totalTestCount, doneTestCount;
    if (myTotalTestCount == 0) {
      totalTestCount = 2;
      doneTestCount = 1;
    }
    else {
      totalTestCount = myTotalTestCount;
      doneTestCount = myFinishedTestCount + myFailedTestCount + myIgnoredTestCount;
    }
    TestsUIUtil.showIconProgress(myProject, doneTestCount, totalTestCount, myFailedTestCount, true);
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   *
   * @return Listener
   */
  public GoogleCloudTestingPropagateSelectionHandler createSelectMeListener() {
    return new GoogleCloudTestingPropagateSelectionHandler() {
      @Override
      public void handlePropagateSelectionRequest(@Nullable final GoogleCloudTestProxy selectedTestProxy, @NotNull final Object sender,
                                                  final boolean requestFocus) {
        SMRunnerUtil.addToInvokeLater(new Runnable() {
          @Override
          public void run() {
            selectWithoutNotify(selectedTestProxy, null);

            // Request focus if necessary
            if (requestFocus) {
              //myTreeView.requestFocusInWindow();
              IdeFocusManager.getInstance(myProject).requestFocus(myTreeView, true);
            }
          }
        });
      }
    };
  }


  private static class MyAnimator extends TestsProgressAnimator {
    public MyAnimator(final AbstractTestTreeBuilder builder) {
      super(builder);
    }
  }

  private void updateCountersAndProgressOnTestCount(final int count, final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    //This is for better support groups of TestSuites
    //Each group notifies about it's size
    myTotalTestCount += count;
    updateStatusLabel(false);
  }

  private void updateOnTestStarted(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    // for mixed tests results : mention category only if it contained tests
    //TODO: Consider re-using TestsPresentationUtil.DEFAULT_TESTS_CATEGORY instead of "Tests".
    myMentionedCategories
      .add(myCurrentCustomProgressCategory != null ? myCurrentCustomProgressCategory : "Tests"); //TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);

    myStartedTestCount++;

    // fix total count if it is corrupted
    // but if test count wasn't set at all let's process such case separately
    if (myStartedTestCount > myTotalTestCount && myTotalTestCount != 0) {
      myTotalTestCount = myStartedTestCount;
    }

    updateStatusLabel(false);
  }

  private void updateProgressOnTestDone() {
    int doneTestCount = myFinishedTestCount + myFailedTestCount + myIgnoredTestCount;
    // update progress
    if (myTotalTestCount != 0) {
      // if total is set
      myStatusLine.setFraction((double) doneTestCount / myTotalTestCount);
    }
    else {
      // if at least one test was launcher than just set progress in the middle to show user that tests are running
      myStatusLine.setFraction(doneTestCount > 0 ? 0.5 : 0);
    }
  }

  private void updateOnTestFailed(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;
    myFailedTestCount++;
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private void updateOnTestFinished(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;
    myFinishedTestCount++;
    updateProgressOnTestDone();
  }

  private void updateOnTestIgnored() {
    myIgnoredTestCount++;
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private boolean isModeConsistent(boolean isCustomMessage) {
    // check that we are in consistent mode
    return isCustomMessage != (myCurrentCustomProgressCategory == null);
  }
}
