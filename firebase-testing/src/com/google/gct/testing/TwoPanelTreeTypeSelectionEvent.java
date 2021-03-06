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

import com.google.gct.testing.dimension.CloudTestingType;
import com.google.gct.testing.dimension.CloudConfigurationDimension;

class TwoPanelTreeTypeSelectionEvent {

  private CloudConfigurationDimension currentDimension;

  private CloudTestingType clickedType;

  public TwoPanelTreeTypeSelectionEvent(CloudConfigurationDimension currentDimension, CloudTestingType clickedType) {
    this.currentDimension = currentDimension;
    this.clickedType = clickedType;
  }

  public CloudConfigurationDimension getCurrentDimension() {
    return currentDimension;
  }

  public CloudTestingType getType() {
    return clickedType;
  }
}
