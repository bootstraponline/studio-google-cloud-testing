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
import com.google.gct.testing.GoogleCloudTestingConfigurationImpl;

import javax.swing.*;
import java.util.*;


public abstract class GoogleCloudTestingDimension {

  private static final long DISCOVERY_TEST_API_REFRESH_TIMEOUT = 5 * 60 * 1000; // 5 minutes

  /**
   * Map dimension -> last update timestamp.
   */
  private static Map<String, Long> lastDiscoveryTestApiUpdateTimestampMap = new HashMap<String, Long>();

  /**
   * The list of types that are currently enabled (use List rather than Set for comparison consistency).
   */
  private List<GoogleCloudTestingType> enabledTypes = new LinkedList<GoogleCloudTestingType>();
  private GoogleCloudTestingConfigurationImpl myGoogleCloudTestingConfiguration;
  private Icon icon;

  public GoogleCloudTestingDimension(GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration) {
    myGoogleCloudTestingConfiguration = googleCloudTestingConfiguration;
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
  public abstract List<? extends GoogleCloudTestingType> getAppSupportedDomain();

  /**
   * Returns the list of type groups supported by the app and the backend for this dimension.
   */
  public List<? extends GoogleCloudTestingTypeGroup> getSupportedGroups() {
    List<GoogleCloudTestingTypeGroup> result = new LinkedList<GoogleCloudTestingTypeGroup>();
    for (GoogleCloudTestingType type : getSupportedDomain()) {
      GoogleCloudTestingTypeGroup groupToAddTo = null;
      for (GoogleCloudTestingTypeGroup group : result) {
        if (type.getGroupName().equals(group.getName())) {
          groupToAddTo = group;
          break;
        }
      }
      if (groupToAddTo == null) {
        groupToAddTo = new GoogleCloudTestingTypeGroup(type.getGroupName());
        result.add(groupToAddTo);
      }
      groupToAddTo.addType(type);
    }
    return result;
  }

  /**
   * Returns the list of types supported both by the app and the backend for this dimension.
   */
  public List<? extends GoogleCloudTestingType> getSupportedDomain() {
    return getAppSupportedDomain();
  }

  public void enable(GoogleCloudTestingType... types) {
    checkIsEditable();
    for (GoogleCloudTestingType type : types) {
      validateType(type);
      enableType(type);
    }
  }

  public void enable(List<? extends GoogleCloudTestingType> types, Iterable<String> ids) {
    checkIsEditable();
    Set<String> idsSet = Sets.newHashSet(ids);
    for (GoogleCloudTestingType type : types) {
      if (idsSet.contains(type.getId())) {
        validateType(type);
        enableType(type);
      }
    }
  }

  public void disable(GoogleCloudTestingType... types) {
    checkIsEditable();
    for (GoogleCloudTestingType type : types) {
      validateType(type);
      enabledTypes.remove(type);
    }
  }

  public void setEnabled(GoogleCloudTestingType type, boolean isEnabled) {
    checkIsEditable();
    validateType(type);
    if (isEnabled) {
      enableType(type);
    } else {
      enabledTypes.remove(type);
    }
  }

  private void enableType(GoogleCloudTestingType type) {
    if (!enabledTypes.contains(type)) {
      enabledTypes.add(type);
    }
  }

  public void clear() {
    enabledTypes.clear();
  }

  public void enableAll() {
    checkIsEditable();
    for (GoogleCloudTestingType type : getSupportedDomain()) {
      enable(type);
    }
  }

  public void disableAll() {
    checkIsEditable();
    for (GoogleCloudTestingType type : getSupportedDomain()) {
      disable(type);
    }
  }

  public ImmutableList<GoogleCloudTestingType> getEnabledTypes() {
    return ImmutableList.copyOf(enabledTypes);
  }

  private void validateType(GoogleCloudTestingType type) {
    if (!getSupportedDomain().contains(type)) {
      throw new IllegalArgumentException("Type " + type + " is not supported in domain: " + getSupportedDomain());
    }
  }

  public boolean isEditable() {
    return myGoogleCloudTestingConfiguration.isEditable();
  }

  public void dimensionChanged() {
    myGoogleCloudTestingConfiguration.dimensionChanged(this);
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
   * Used to construct Jenkins requests.
   */
  public abstract String getId();

  public abstract Icon getIcon();

  public void enableAll(Iterable<GoogleCloudTestingType> enabledTypes) {
    for (GoogleCloudTestingType enabledType : enabledTypes) {
      enable(enabledType);
    }
  }
}
