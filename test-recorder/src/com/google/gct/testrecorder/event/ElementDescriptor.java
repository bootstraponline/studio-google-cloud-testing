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
  private final String resourceId;
  private final String contentDescription;
  private final String text;

  public ElementDescriptor(String resourceId, String contentDescription, String text) {
    this.resourceId = resourceId;
    this.contentDescription = contentDescription;
    this.text = text;
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

  public boolean isEmpty() {
    return isNullOrEmpty(resourceId) && isNullOrEmpty(text) && isNullOrEmpty(contentDescription);
  }
}
