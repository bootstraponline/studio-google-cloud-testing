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
package com.google.gct.testing;

import com.android.tools.analytics.UsageTracker;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.results.GoogleCloudTestProxy.GoogleCloudRootTestProxy;
import com.google.gct.testing.results.GoogleCloudTestResultsViewer;
import com.google.gct.testing.results.GoogleCloudTestTreeView;
import com.google.gct.testing.results.GoogleCloudTestingResultsForm;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShowScreenshotsAction extends AnAction {

  private final static String TEXT = "View Screenshots";
  private final static String DESCRIPTION = "Open the Screenshot Comparison Tool";
  private final static Icon ICON = AllIcons.Actions.Share;

  public static final Function<AbstractTestProxy,TestName> TO_TEST_NAMES = new Function<AbstractTestProxy, TestName>() {
    @Override
    public TestName apply(@Nullable AbstractTestProxy input) {
      return getTestNameForNode(input);
    }
  };


  public ShowScreenshotsAction() {
    super(TEXT, DESCRIPTION, ICON);
    getTemplatePresentation().setEnabled(false);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                     .setCategory(EventCategory.CLOUD_TESTING)
                                     .setKind(EventKind.CLOUD_TESTING_COMPARE_SCREENSHOTS_OPENED));

    final GoogleCloudTestTreeView sender = e.getData(GoogleCloudTestTreeView.CLOUD_TEST_RUNNER_VIEW);
    if (sender == null) {
      return;
    }

    final GoogleCloudTestResultsViewer resultsViewer = sender.getResultsViewer();
    final GoogleCloudRootTestProxy rootNode = ((GoogleCloudTestingResultsForm)resultsViewer).getTestsRootNode();

    AbstractTestProxy selectedLeaf = getFirstLeaf(((GoogleCloudTestingResultsForm)resultsViewer).getTreeView().getSelectedTest());

    if (selectedLeaf.getParent() == null || selectedLeaf.getParent().getParent() == null) {
      //This leaf is not a test. Most probably it is a pending configuration, so ignore.
      showNoScreenshotsWarning(e);
      return;
    }

    AbstractTestProxy selectedConfigurationNode = selectedLeaf.getParent().getParent();
    ConfigurationInstance configurationInstance =
      ConfigurationInstance.parseFromResultsViewerDisplayString(selectedConfigurationNode.getName());
    Map<String,ConfigurationResult> results =
      CloudConfigurationHelper.getCloudResultsAdapter(rootNode.getTestRunId()).getResults();

    if (results.get(configurationInstance.getEncodedString()).getScreenshotMetadata().isEmpty()) {
      showNoScreenshotsWarning(e);
      return;
    }

    AbstractTestProxy anotherConfigurationNode = findClosestNeighborWithResults(selectedConfigurationNode);
    ConfigurationInstance anotherConfigurationInstance =
      anotherConfigurationNode == null
      ? null
      : ConfigurationInstance.parseFromResultsViewerDisplayString(anotherConfigurationNode.getName());

    ArrayList<TestName> allTests = Lists.newArrayList(Iterables.transform(selectedLeaf.getParent().getChildren(), TO_TEST_NAMES));

    ScreenshotComparisonDialog dialog =
      new ScreenshotComparisonDialog(
        e.getData(PlatformDataKeys.PROJECT), rootNode,
        CloudConfigurationHelper.getSelectedCloudConfiguration(rootNode.getTestRunId()),
        configurationInstance, anotherConfigurationInstance, allTests, getTestNameForNode(selectedLeaf), results);

    dialog.showDialog();
  }

  private void showNoScreenshotsWarning(AnActionEvent e) {
    //TODO: Instead of ignoring invalid invocations, should remove the menu option to show screenshot if the context is not correct.
    CloudTestingUtils
      .showBalloonMessage(e.getData(PlatformDataKeys.PROJECT), "Screenshots are not yet available for this configuration",
                          MessageType.WARNING, 3);
  }

  private AbstractTestProxy findClosestNeighborWithResults(AbstractTestProxy givenConfigurationNode) {
    List<? extends AbstractTestProxy> allConfigurationNodes = givenConfigurationNode.getParent().getChildren();
    int givenConfigurationIndex = 0;
    for (AbstractTestProxy configurationNode : allConfigurationNodes) {
      if (configurationNode == givenConfigurationNode) {
        break;
      }
      givenConfigurationIndex++;
    }
    int forwardIndex = givenConfigurationIndex + 1;
    int backwardIndex = givenConfigurationIndex - 1;
    while (forwardIndex < allConfigurationNodes.size() || backwardIndex >= 0) {
      if (forwardIndex < allConfigurationNodes.size()) {
        AbstractTestProxy neighborConfiguration = allConfigurationNodes.get(forwardIndex);
        if (hasTestResults(neighborConfiguration)) {
          return neighborConfiguration;
        }
        forwardIndex++;
      }
      if (backwardIndex >= 0) {
        AbstractTestProxy neighborConfiguration = allConfigurationNodes.get(backwardIndex);
        if (hasTestResults(neighborConfiguration)) {
          return neighborConfiguration;
        }
        backwardIndex--;
      }
    }
    //Not found any other configuration with test results.
    return null;
  }

  private boolean hasTestResults(AbstractTestProxy configuration) {
    return configuration.getAllTests().size() > configuration.getChildren().size() + 1;
  }

  private static TestName getTestNameForNode(AbstractTestProxy testNode) {
    return new TestName(testNode.getParent().getName(), testNode.getName());
  }

  private AbstractTestProxy getFirstLeaf(AbstractTestProxy testNode) {
    while (!testNode.isLeaf()) {
      return getFirstLeaf(testNode.getChildren().get(0));
    }
    return testNode;
  }

  @Override
  public void update(AnActionEvent actionEvent) {
    GoogleCloudTestTreeView sender = actionEvent.getData(GoogleCloudTestTreeView.CLOUD_TEST_RUNNER_VIEW);

    if (sender == null) {
      return;
    }

    AbstractTestProxy selectedNode = ((GoogleCloudTestingResultsForm)sender.getResultsViewer()).getTreeView().getSelectedTest();
    if (selectedNode == null || selectedNode instanceof GoogleCloudRootTestProxy) {
      actionEvent.getPresentation().setEnabled(false);
    } else {
      actionEvent.getPresentation().setEnabled(true);
    }
  }

}
