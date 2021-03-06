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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gct.testing.CloudConfigurationImpl;

import javax.swing.*;
import java.util.*;


public abstract class CloudConfigurationDimension {

  private static final long DISCOVERY_TEST_API_REFRESH_TIMEOUT = 5 * 60 * 1000; // 5 minutes

  /**
   * Map dimension -> last update timestamp.
   */
  private static Map<String, Long> lastDiscoveryTestApiUpdateTimestampMap = new HashMap<String, Long>();

  /**
   * The list of types that are currently enabled (use List rather than Set for comparison consistency).
   */
  private List<CloudTestingType> enabledTypes = new LinkedList<CloudTestingType>();
  private CloudConfigurationImpl myCloudConfiguration;
  private Icon icon;

  public CloudConfigurationDimension(CloudConfigurationImpl cloudConfiguration) {
    myCloudConfiguration = cloudConfiguration;
  }

  static boolean shouldPollDiscoveryTestApi(String dimension) {
    Long lastTimestamp = lastDiscoveryTestApiUpdateTimestampMap.get(dimension);
    if (lastTimestamp == null) {
      return true;
    }
    return System.currentTimeMillis() - lastTimestamp > DISCOVERY_TEST_API_REFRESH_TIMEOUT;
  }

  static void resetDiscoveryTestApiUpdateTimestamp(String dimension) {
    lastDiscoveryTestApiUpdateTimestampMap.put(dimension, System.currentTimeMillis());
  }

  /**
   * Returns the list of types supported by the app for this dimension.
   */
  public abstract List<? extends CloudTestingType> getAppSupportedDomain();

  /**
   * Returns the list of type groups supported by the app and the backend for this dimension.
   */
  public List<? extends CloudTestingTypeGroup> getSupportedGroups() {
    List<CloudTestingTypeGroup> result = new LinkedList<CloudTestingTypeGroup>();
    for (CloudTestingType type : getSupportedDomain()) {
      CloudTestingTypeGroup groupToAddTo = null;
      for (CloudTestingTypeGroup group : result) {
        if (type.getGroupName().equals(group.getName())) {
          groupToAddTo = group;
          break;
        }
      }
      if (groupToAddTo == null) {
        groupToAddTo = new CloudTestingTypeGroup(type.getGroupName(), type.getGroupDescription());
        result.add(groupToAddTo);
      }
      groupToAddTo.addType(type);
    }
    return result;
  }

  /**
   * Returns the list of types supported both by the app and the backend for this dimension.
   */
  public List<? extends CloudTestingType> getSupportedDomain() {
    return getAppSupportedDomain();
  }

  public void enable(CloudTestingType... types) {
    checkIsEditable();
    for (CloudTestingType type : types) {
      if (getSupportedDomain().contains(type)) {
        enableType(type);
      }
    }
  }

  public void enable(List<? extends CloudTestingType> types, Iterable<String> ids) {
    checkIsEditable();
    Set<String> idsSet = Sets.newHashSet(ids);
    for (CloudTestingType type : types) {
      if (getSupportedDomain().contains(type) && idsSet.contains(type.getId())) {
        enableType(type);
      }
    }
  }

  public void disable(CloudTestingType... types) {
    checkIsEditable();
    for (CloudTestingType type : types) {
      enabledTypes.remove(type);
    }
  }

  public void setEnabled(CloudTestingType type, boolean isEnabled) {
    if (!getSupportedDomain().contains(type)) {
      return;
    }
    checkIsEditable();
    if (isEnabled) {
      enableType(type);
    } else {
      enabledTypes.remove(type);
    }
  }

  private void enableType(CloudTestingType type) {
    if (!enabledTypes.contains(type)) {
      enabledTypes.add(type);
    }
  }

  public void clear() {
    enabledTypes.clear();
  }

  public void enableAll() {
    checkIsEditable();
    for (CloudTestingType type : getSupportedDomain()) {
      enable(type);
    }
  }

  public void disableAll() {
    checkIsEditable();
    for (CloudTestingType type : getSupportedDomain()) {
      disable(type);
    }
  }

  public ImmutableList<CloudTestingType> getEnabledTypes() {
    return ImmutableList.copyOf(enabledTypes);
  }

  public boolean isEditable() {
    return myCloudConfiguration.isEditable();
  }

  public void dimensionChanged() {
    myCloudConfiguration.dimensionChanged(this);
  }

  private void checkIsEditable() {
    Preconditions.checkState(isEditable(), "Cannot change a non-editable dimension!");
  }

  public boolean shouldBeAlwaysGrouped() {
    return false;
  }

  public String toString() {
    return getDisplayName();
  }

  public abstract String getDisplayName();

  /**
   * Used to construct matrix test requests.
   */
  public abstract String getId();

  public abstract Icon getIcon();

  public void enableAll(Iterable<CloudTestingType> enabledTypes) {
    for (CloudTestingType enabledType : enabledTypes) {
      enable(enabledType);
    }
  }
}
