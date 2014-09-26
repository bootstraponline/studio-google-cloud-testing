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

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

public class GoogleCloudTestTreeView extends TestTreeView {
  public static final DataKey<GoogleCloudTestTreeView> SM_TEST_RUNNER_VIEW  = DataKey.create("SM_TEST_RUNNER_VIEW");

  @Nullable private GoogleCloudTestResultsViewer myResultsViewer;

  @Override
  protected TreeCellRenderer getRenderer(final TestConsoleProperties properties) {
    return new GoogleCloudTestTreeRenderer(properties);
  }

  @Override
  @Nullable
  public GoogleCloudTestProxy getSelectedTest(@NotNull final TreePath selectionPath) {
    final Object lastComponent = selectionPath.getLastPathComponent();
    assert lastComponent != null;

    return getTestProxyFor(lastComponent);
  }

  @Nullable
  public static GoogleCloudTestProxy getTestProxyFor(final Object treeNode) {
    final Object userObj = ((DefaultMutableTreeNode)treeNode).getUserObject();
    if (userObj instanceof GoogleCloudTestNodeDescriptor) {
      return ((GoogleCloudTestNodeDescriptor)userObj).getElement();
    }

    return null;
  }

  public void setTestResultsViewer(final GoogleCloudTestResultsViewer resultsViewer) {
    myResultsViewer = resultsViewer;
  }

  @Nullable
  public GoogleCloudTestResultsViewer getResultsViewer() {
    return myResultsViewer;
  }

  @Override
  public Object getData(final String dataId) {
    if (SM_TEST_RUNNER_VIEW.is(dataId)) {
      return this;
    }
    return super.getData(dataId);
  }
}
