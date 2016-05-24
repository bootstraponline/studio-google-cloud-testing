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

import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class GoogleCloudTestTreeBuilder extends AbstractTestTreeBuilder {
  public GoogleCloudTestTreeBuilder(final JTree tree, final GoogleCloudTestTreeStructure structure) {
    super(tree,
          new DefaultTreeModel(new DefaultMutableTreeNode(structure.getRootElement())),
          structure,
          IndexComparator.INSTANCE);

    setCanYieldUpdate(true);
    initRootNode();
  }

  public GoogleCloudTestTreeStructure getGoogleCloudTestTreeStructure() {
    return ((GoogleCloudTestTreeStructure)getTreeStructure());
  }

  public void updateTestsSubtree(final GoogleCloudTestProxy parentTestProxy) {
    final AbstractTreeUpdater updater = getUpdater();
    if (updater != null) {
      updater.addSubtreeToUpdateByElement(parentTestProxy);
    }
  }


  @Override
  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    final AbstractTreeStructure treeStructure = getTreeStructure();
    final Object rootElement = treeStructure.getRootElement();
    final Object nodeElement = nodeDescriptor.getElement();

    if (nodeElement == rootElement) {
      return true;
    }

    if (((GoogleCloudTestProxy)nodeElement).getParent() == rootElement
        && ((GoogleCloudTestProxy)rootElement).getChildren().size() == 1) {
      return true;
    }
    //return false;
    return true; //Expand all nodes.
  }

  /**
   * for java unit tests
   */
  public void performUpdate() {
    getUpdater().performUpdate();
  }
}
