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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GoogleCloudTestTreeStructure extends AbstractTreeStructure
{
  private final Object myRootNode;
  private Filter myTestNodesFilter;
  private final Project myProject;

  public GoogleCloudTestTreeStructure(final Project project, final Object rootNode) {
    myProject = project;
    myRootNode = rootNode;
    myTestNodesFilter = Filter.NO_FILTER;
  }

  @Override
  public void commit() {
  }

  @NotNull
  @Override
  public GoogleCloudTestNodeDescriptor createDescriptor(final Object element,
                                                  final NodeDescriptor parentDesc) {
    //noinspection unchecked
    return new GoogleCloudTestNodeDescriptor(myProject,
                                       (GoogleCloudTestProxy)element,
                                       (NodeDescriptor<GoogleCloudTestProxy>)parentDesc);
  }

  public Filter getFilter() {
    return myTestNodesFilter;
  }

  @Override
  public Object[] getChildElements(final Object element) {
    final List<? extends GoogleCloudTestProxy> results =
        ((GoogleCloudTestProxy)element).getChildren(myTestNodesFilter);

    return results.toArray(new AbstractTestProxy[results.size()]);
  }

  @Override
  public Object getParentElement(final Object element) {
    return ((AbstractTestProxy)element).getParent();
  }

  @Override
  public Object getRootElement() {
    return myRootNode;
  }


  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public void setFilter(final Filter nodesFilter) {
    myTestNodesFilter = nodesFilter;
  }
}
