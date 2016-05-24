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
package com.google.gct.testing.dimension;

import java.util.Map;

/**
 * A {@link CloudTestingType} consists of a set of Types; concrete instantiations of a dimension.
 */
public abstract class CloudTestingType {

  protected Map<String, String> details;

  public abstract String getConfigurationDialogDisplayName();

  public String getResultsViewerDisplayName() {
    return getConfigurationDialogDisplayName();
  }

  /**
   * Used to construct matrix test requests.
   */
  public abstract String getId();

  public String getGroupName() {
    return getId();
  }

  public String getGroupDescription() {
    return getGroupName();
  }

  public Map<String, String> getDetails() {
    return details;
  }

  public String toString() {
    return getConfigurationDialogDisplayName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CloudTestingType cloudTestingType = (CloudTestingType)o;

    return cloudTestingType.getId().equals(this.getId());
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }
}
