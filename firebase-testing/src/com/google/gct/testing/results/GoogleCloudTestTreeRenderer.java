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
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class GoogleCloudTestTreeRenderer extends ColoredTreeCellRenderer {
  @NonNls private static final String SPACE_STRING = " ";

  private final TestConsoleProperties myConsoleProperties;
  private GoogleCloudTestingRootTestProxyFormatter myAdditionalRootFormatter;
  private int myDurationWidth = -1;
  private int myRow;

  public GoogleCloudTestTreeRenderer(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  @Override
  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myRow = row;
    myDurationWidth = -1;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
    final Object userObj = node.getUserObject();
    if (userObj instanceof GoogleCloudTestNodeDescriptor) {
      final GoogleCloudTestNodeDescriptor desc = (GoogleCloudTestNodeDescriptor)userObj;
      final GoogleCloudTestProxy testProxy = desc.getElement();

      if (testProxy instanceof GoogleCloudTestProxy.GoogleCloudRootTestProxy) {
        GoogleCloudTestProxy.GoogleCloudRootTestProxy rootTestProxy = (GoogleCloudTestProxy.GoogleCloudRootTestProxy) testProxy;
        if (rootTestProxy.isLeaf()) {
          GoogleCloudTestsPresentationUtil.formatRootNodeWithoutChildren(rootTestProxy, this);
        } else {
          GoogleCloudTestsPresentationUtil.formatRootNodeWithChildren(rootTestProxy, this);
        }
        if (myAdditionalRootFormatter != null) {
          myAdditionalRootFormatter.format(rootTestProxy, this);
        }
      } else {
        GoogleCloudTestsPresentationUtil.formatTestProxy(testProxy, this);
      }

      if (TestConsoleProperties.SHOW_INLINE_STATISTICS.value(myConsoleProperties)) {
        String durationString = testProxy.getDurationString(myConsoleProperties);
        if (durationString != null) {
          durationString = "  " + durationString;
          myDurationWidth = getFontMetrics(getFont()).stringWidth(durationString);
          if (((TestTreeView)myTree).isExpandableHandlerVisibleForCurrentRow(myRow)) {
            append(durationString);
          }
        }
      }
      //Done
      return;
    }

    //strange node
    final String text = node.toString();
    //no icon
    append(text != null ? text : SPACE_STRING, SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    return myDurationWidth < 0 || ((TestTreeView)myTree).isExpandableHandlerVisibleForCurrentRow(myRow)
           ? preferredSize
           : JBUI.size(preferredSize.width + myDurationWidth, preferredSize.height);
  }

  public TestConsoleProperties getConsoleProperties() {
    return myConsoleProperties;
  }

  public void setAdditionalRootFormatter(@NotNull GoogleCloudTestingRootTestProxyFormatter formatter) {
    myAdditionalRootFormatter = formatter;
  }

  public void removeAdditionalRootFormatter() {
    myAdditionalRootFormatter = null;
  }
}
