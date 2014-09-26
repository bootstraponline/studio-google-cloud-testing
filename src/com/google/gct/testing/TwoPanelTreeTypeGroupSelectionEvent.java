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

import com.google.gct.testing.dimension.GoogleCloudTestingDimension;
import com.google.gct.testing.dimension.GoogleCloudTestingTypeGroup;

class TwoPanelTreeTypeGroupSelectionEvent {

  private GoogleCloudTestingDimension currentDimension;

  private GoogleCloudTestingTypeGroup clickedGroup;

  public TwoPanelTreeTypeGroupSelectionEvent(GoogleCloudTestingDimension currentDimension, GoogleCloudTestingTypeGroup clickedGroup) {
    this.currentDimension = currentDimension;
    this.clickedGroup = clickedGroup;
  }

  public GoogleCloudTestingDimension getCurrentDimension() {
    return currentDimension;
  }

  public GoogleCloudTestingTypeGroup getGroup() {
    return clickedGroup;
  }
}
