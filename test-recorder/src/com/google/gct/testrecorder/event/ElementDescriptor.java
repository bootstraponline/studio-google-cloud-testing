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
package com.google.gct.testrecorder.event;

import static com.google.common.base.Strings.isNullOrEmpty;

public class ElementDescriptor {
  // Fully qualified class name of the element.
  private final String className;


  // Attribute fields:

  // Position of this element among the children of its parent.
  // The value of -1 signifies that the child position is absent.
  private final int childPosition;
  private final String resourceId;
  private final String contentDescription;
  private final String text;


  public ElementDescriptor(String className, int childPosition, String resourceId, String contentDescription, String text) {
    this.className = className;
    this.childPosition = childPosition;
    this.resourceId = resourceId;
    this.contentDescription = contentDescription;
    this.text = text;
  }

  public String getClassName() {
    return className;
  }

  public int getChildPosition() {
    return childPosition;
  }

  public String getResourceId() {
    return resourceId;
  }

  public String getContentDescription() {
    return contentDescription;
  }

  public String getText() {
    return text;
  }

  /**
   * Returns {@code true} iff all attribute fields are absent.
   */
  public boolean isEmpty() {
    return childPosition == -1 && isEmptyIgnoringChildPosition();
  }

  /**
   * Returns {@code true} iff all attribute fields not considering {@code childPosition} are absent.
   */
  public boolean isEmptyIgnoringChildPosition() {
    return isNullOrEmpty(resourceId) && isNullOrEmpty(text) && isNullOrEmpty(contentDescription);
  }
}
