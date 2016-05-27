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

import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.gct.testrecorder.util.StringHelper.getClassName;
import static com.google.gct.testrecorder.util.StringHelper.parseId;
import static com.intellij.util.ui.UIUtil.isUnderDarcula;

public abstract class ElementAction {

  /**
   * Type of the affected UI hierarchy element.
   */
  protected String elementType;

  /**
   * Descriptors of elements starting with the affected one and up the UI hierarchy.
   */
  private final List<ElementDescriptor> elementDescriptors = Lists.newLinkedList();

  public ElementDescriptor getElementDescriptor(int index) {
    return elementDescriptors.get(index);
  }

  public List<ElementDescriptor> getElementDescriptorList() {
    return ImmutableList.copyOf(elementDescriptors);
  }

  public int getElementDescriptorsCount() {
    return elementDescriptors.size();
  }

  public void addElementDescriptor(ElementDescriptor descriptor) {
    elementDescriptors.add(descriptor);
  }

  @Nullable
  public String getElementType() {
    return elementType;
  }

  public void setElementType(String elementType) {
    this.elementType = elementType;
  }

  /**
   * Returns the string that represents this element action in the recording dialog.
   */
  public abstract String getRendererString();

  protected String getDisplayResourceId() {
    String resourceId = getElementResourceId();
    if (!resourceId.isEmpty()) {
      Pair<String, String> parsedId = parseId(resourceId);
      return getIdAttributeDisplayPresentation("ID", parsedId == null ? resourceId : parsedId.getSecond());
    }
    return "";
  }

  protected String getDisplayText() {
    String text = getElementText();
    if (!text.isEmpty()) {
      return getIdAttributeDisplayPresentation("text", text);
    }
    return "";
  }

  protected String getDisplayContentDescription() {
    String contentDescription = getElementContentDescription();
    if (!contentDescription.isEmpty()) {
      return getIdAttributeDisplayPresentation("content description", contentDescription);
    }
    return "";
  }

  protected String getIdAttributeDisplayPresentation(String idAttributeKind, String idAttributeValue) {
    final String idTextColor = isUnderDarcula() ? "#eeeeee" : "#111111";
    return idAttributeKind + " <span style='color: " + idTextColor + "; font-weight: bold;'>" + idAttributeValue + "</span>";
  }

  @NotNull
  protected String getRendererString(String displayElementAttribute) {
    String displayElementType = isNullOrEmpty(elementType) ? "element" : getClassName(elementType);
    return displayElementType + " with " + displayElementAttribute;
  }

  /**
   * Returns top-level element resource id, if present. Otherwise, returns an empty string.
   */
  public String getElementResourceId() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getResourceId();
    }
    return "";
  }

  /**
   * Returns top-level element text, if present. Otherwise, returns an empty string.
   */
  public String getElementText() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getText();
    }
    return "";
  }

  /**
   * Returns top-level element content description, if present. Otherwise, returns an empty string.
   */
  public String getElementContentDescription() {
    if (!elementDescriptors.isEmpty()) {
      return elementDescriptors.get(0).getContentDescription();
    }
    return "";
  }
}
