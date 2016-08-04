/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.gct.testrecorder.util;

import com.android.uiautomator.tree.BasicTreeNode;
import com.android.uiautomator.tree.RootWindowNode;
import com.android.uiautomator.tree.UiNode;
import com.google.common.collect.Maps;

import java.util.LinkedHashMap;
import java.util.Map;

public class UiAutomatorNodeHelper {

  public static LinkedHashMap<BasicTreeNode, Integer> createElementLevelMap(BasicTreeNode root) {
    // Map of tree node -> nesting level in the UI hierarchy.
    LinkedHashMap<BasicTreeNode, Integer> elementLevels = Maps.newLinkedHashMap();
    populateElementLevels(root, 0, elementLevels);
    return elementLevels;
  }

  private static void populateElementLevels(BasicTreeNode currentElement, int currentLevel, Map<BasicTreeNode, Integer> elementLevels) {
    if (currentElement == null) {
      return;
    }

    // Do not map the root node.
    if (!(currentElement instanceof RootWindowNode)) {
      elementLevels.put(currentElement, currentLevel);
    }

    for (BasicTreeNode child : currentElement.getChildren()) {
      populateElementLevels(child, currentLevel + 1, elementLevels);
    }
  }

  public static boolean isTextView(BasicTreeNode node) {
    String className = getClassName(node);
    // UIAutomator uses base classes for most framework classes, including for text view and edit widgets.
    return className.equals("android.widget.TextView") || className.equals("android.widget.EditText");
  }

  public static String getClassName(BasicTreeNode node) {
    return getAttribute(node, "class");
  }

  public static String getResourceId(BasicTreeNode node) {
    return getAttribute(node, "resource-id");
  }

  public static String getText(BasicTreeNode node) {
    return getAttribute(node, "text");
  }

  public static String getContentDescription(BasicTreeNode node) {
    return getAttribute(node, "content-desc");
  }

  public static int getChildPosition(BasicTreeNode node) {
    try {
      return Integer.parseInt(getAttribute(node, "index"));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String getAttribute(BasicTreeNode node, String attributeName) {
    if (node instanceof UiNode) {
      return ((UiNode) node).getAttribute(attributeName);
    }
    return "";
  }
}
