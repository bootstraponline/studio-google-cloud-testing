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

import java.util.LinkedHashMap;

public class ElementLevelMapCreator {

  public static LinkedHashMap<BasicTreeNode, Integer> createElementLevelMap(BasicTreeNode root) {
    LinkedHashMap<BasicTreeNode, Integer> map = new LinkedHashMap<BasicTreeNode, Integer>();
    deepFirstSearch(root, 0, map);
    return map;
  }

  // helper function
  private static void deepFirstSearch(BasicTreeNode element,
                                      int level,
                                      LinkedHashMap<BasicTreeNode, Integer> map) {
    if (element == null) {
      return;
    }
    // Ignore root
    if (!(element instanceof RootWindowNode)) {
      // Only add element if it has id
      String id = ((UiNode) element).getAttribute("resource-id");
      if (!id.equals("")) {
        map.put(element, level);
      }
    }
    for (BasicTreeNode child : element.getChildren()) {
      deepFirstSearch(child, level + 1, map);
    }
  }
}
