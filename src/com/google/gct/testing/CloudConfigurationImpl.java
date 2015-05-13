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

import com.android.tools.idea.run.CloudConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.gct.testing.dimension.*;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.run.CloudConfiguration.Kind.SINGLE_DEVICE;

public class CloudConfigurationImpl extends CloudConfiguration {

  public static final int ALL_CONFIGURATION_ID = Integer.MAX_VALUE;
  public static final int DEFAULT_DEVICE_CONFIGURATION_ID = Integer.MAX_VALUE - 1;

  private static int nextAvailableID = 1;

  private int id;
  private String name;
  private Kind kind;
  private Icon icon;
  private final AndroidFacet facet;
  private boolean isEditable;

  private final Set<ConfigurationChangeListener> changeListeners = new HashSet<ConfigurationChangeListener>();

  // Dimensions
  DeviceDimension deviceDimension;
  ApiDimension apiDimension;
  LanguageDimension languageDimension;
  OrientationDimension orientationDimension;

  public CloudConfigurationImpl(int id, String name, Kind kind, Icon icon, AndroidFacet facet) {
    if (!isPredefinedId(id) && id >= nextAvailableID) {
      nextAvailableID = id + 1;
    }
    this.id = id;
    this.name = name;
    this.kind = kind;
    this.icon = icon;
    this.facet = facet;
    isEditable = true;
    createDimensions();
  }

  private boolean isPredefinedId(int id) {
    return id == ALL_CONFIGURATION_ID || id == DEFAULT_DEVICE_CONFIGURATION_ID;
  }

  public CloudConfigurationImpl(String name, Kind kind, Icon icon, AndroidFacet facet) {
    this(nextAvailableID++, name, kind, icon, facet);
  }

  @VisibleForTesting
  CloudConfigurationImpl(String name, Kind kind, int minSdkVersion, List<String> locales) {
    id = nextAvailableID++;
    this.name = name;
    this.kind = kind;
    facet = null;
    isEditable = true;
    deviceDimension = new DeviceDimension(this);
    apiDimension = new ApiDimension(this, minSdkVersion);
    languageDimension = new LanguageDimension(this, locales);
    orientationDimension = new OrientationDimension(this);
  }

  public CloudConfigurationImpl(AndroidFacet facet, Kind kind) {
    id = nextAvailableID++;
    name = "Unnamed";
    this.kind = kind;
    icon = CloudConfigurationProviderImpl.DEFAULT_ICON;
    this.facet = facet;
    isEditable = true;
    createDimensions();
  }

  private void createDimensions() {
    deviceDimension = new DeviceDimension(this);
    apiDimension = new ApiDimension(this, facet);
    languageDimension = new LanguageDimension(this, facet);
    orientationDimension = new OrientationDimension(this);
  }

  public String getName() {
    return name;
  }

  public Kind getKind() {
    return kind;
  }

  /**
   * A single number representation of a matrix configuration.
   */
  private int getHash() {
    return (name + FluentIterable.from(getDimensions()).transform(new Function<CloudConfigurationDimension, String>() {
      @Override
      public String apply(CloudConfigurationDimension dimension) {
        return getStringRepresentation(dimension);
      }
    }).toString()).hashCode();
  }

  private String getStringRepresentation(CloudConfigurationDimension dimension) {
    StringBuffer sb = new StringBuffer();
    sb.append(dimension.getId());
    for (CloudTestingType type : dimension.getEnabledTypes()) {
      sb.append(type.getId());
    }
    return sb.toString();
  }

  @Override
  public int getId() {
    return id;
  }

  @Override
  public String getDisplayName() {
    int deviceConfigurationCount = getDeviceConfigurationCount();
    if (kind != SINGLE_DEVICE) {
      return name + " (" + deviceConfigurationCount + ")";
    }
    if (deviceConfigurationCount > 0) { // Should be exactly 1
      return name;
    }
    return name + " (not configured)";
  }

  public void setName(String name) {
    this.name = name;
    for (ConfigurationChangeListener changeListener : changeListeners) {
      changeListener.configurationChanged(new ConfigurationChangeEvent(this));
    }
  }

  public List<CloudConfigurationDimension> getDimensions() {
    return ImmutableList.of(deviceDimension, apiDimension, languageDimension, orientationDimension);
  }

  public ApiDimension getApiDimension() {
    return apiDimension;
  }

  public LanguageDimension getLanguageDimension() {
    return languageDimension;
  }

  public DeviceDimension getDeviceDimension() {
    return deviceDimension;
  }

  public OrientationDimension getOrientationDimension() {
    return orientationDimension;
  }

  public void addConfigurationChangeListener(ConfigurationChangeListener listener) {
    changeListeners.add(listener);
  }

  public boolean removeConfigurationChangeListener(ConfigurationChangeListener listener) {
    return changeListeners.remove(listener);
  }

  public void dimensionChanged(CloudConfigurationDimension dimension) {
    for (ConfigurationChangeListener changeListener : changeListeners) {
      changeListener.configurationChanged(new ConfigurationChangeEvent(this));
    }
  }

  /**
   * Returns the number of different combinations represented
   * by all of the types enabled in this configuration.
   */
  @Override
  public int getDeviceConfigurationCount() {
    int product = 1;

    for (CloudConfigurationDimension dimension : getDimensions()) {
      product *= dimension.getEnabledTypes().size();
    }

    return product;
  }

  public int countCombinationsCollapsingOrientation() {
    int product = 1;

    for (CloudConfigurationDimension dimension : getDimensions()) {
      if (dimension instanceof OrientationDimension) {
        product *= Math.min(dimension.getEnabledTypes().size(), 1);
      } else {
        product *= dimension.getEnabledTypes().size();
      }
    }

    return product;
  }

  @Override
  public boolean isEditable() {
    return isEditable;
  }

  public void setNonEditable() {
    isEditable = false;
  }

  public String toString() {
    return name;
  }

  @Override
  public Icon getIcon() {
    return icon;
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  /**
   * Precondition: getDeviceConfigurationCount() > 0.
   */
  public String prepareMatrixTestRequest() {
    Preconditions.checkState(getDeviceConfigurationCount() > 0, "There should be at least one combination in a matrix test request!");

    StringBuffer bf = new StringBuffer();

    boolean firstDim = true;
    for (CloudConfigurationDimension dimension : getDimensions()) {
      if(!firstDim) {
        bf.append(" && ");
      }
      firstDim = false;
      StringBuffer dimensionRequest = prepareDimensionRequest(dimension);
      if (dimension.getEnabledTypes().size() > 1) {
        bf.append("(").append(dimensionRequest).append(")");
      } else {
        bf.append(dimensionRequest);
      }
    }
    return bf.toString();

    //String s= "(DEVICE=='nexus5' || DEVICE=='nexus7') && OSVERSION=='jellybean' && LANGUAGE=='english'";
  }

  /**
   * TODO: Use an enum rather than delimiter to decide what presentation (and delimiter) to use (i.e., id or display name).
   */
  public List<String> computeConfigurationInstances(String delimiter) {
    List<String> configurationInstances = new LinkedList<String>();
    computeConfigurationInstancesRecursively(delimiter, "", 0, configurationInstances);
    return configurationInstances;
  }

  private void computeConfigurationInstancesRecursively(
    String delimiter, String partialConfigurationInstance, int dimensionIndex, List<String> configurationInstances) {

    if (dimensionIndex >= getDimensions().size()) {
      configurationInstances.add(partialConfigurationInstance);
      return;
    }

    String separator = dimensionIndex == 0 ? "" : delimiter;
    for (CloudTestingType type : getDimensions().get(dimensionIndex).getEnabledTypes()) {
      String typeName;
      if (ConfigurationInstance.DISPLAY_NAME_DELIMITER.equals(delimiter)) {
        typeName = type.getResultsViewerDisplayName();
      } else {
        typeName = type.getId();
      }
      computeConfigurationInstancesRecursively(
        delimiter, partialConfigurationInstance + separator + typeName, dimensionIndex + 1, configurationInstances);
    }
  }

  private StringBuffer prepareDimensionRequest(CloudConfigurationDimension dimension) {
    StringBuffer bf = new StringBuffer();
    boolean firstType = true;
    for (CloudTestingType type : dimension.getEnabledTypes()) {
      if (!firstType) {
        bf.append(" || ");
      }
      firstType = false;
      bf.append(dimension.getId() + "=='" + type.getId() + "'");
    }
    return bf;
  }

  @Override
  public CloudConfigurationImpl clone() {
    return copy(null);
  }

  public CloudConfigurationImpl copy(String prefix) {
    CloudConfigurationImpl newConfiguration = prefix == null
                                                       ? new CloudConfigurationImpl(id, name, kind, icon, facet) //clone
                                                       : new CloudConfigurationImpl(prefix + name, kind, icon, facet);
    newConfiguration.deviceDimension.enableAll(deviceDimension.getEnabledTypes());
    newConfiguration.apiDimension.enableAll(apiDimension.getEnabledTypes());
    newConfiguration.languageDimension.enableAll(languageDimension.getEnabledTypes());
    newConfiguration.orientationDimension.enableAll(orientationDimension.getEnabledTypes());
    return newConfiguration;
  }

  public CloudPersistentConfiguration getPersistentConfiguration() {
    CloudPersistentConfiguration persistentConfiguration = new CloudPersistentConfiguration();
    persistentConfiguration.id = id;
    persistentConfiguration.name = name;
    persistentConfiguration.kind = kind;
    persistentConfiguration.devices = getEnabledTypes(deviceDimension);
    persistentConfiguration.apiLevels = getEnabledTypes(apiDimension);
    persistentConfiguration.languages = getEnabledTypes(languageDimension);
    persistentConfiguration.orientations = getEnabledTypes(orientationDimension);
    return persistentConfiguration;
  }

  private List<String> getEnabledTypes(CloudConfigurationDimension dimension) {
    List<String> enabledTypes = new LinkedList<String>();
    for (CloudTestingType type : dimension.getEnabledTypes()) {
      enabledTypes.add(type.getId());
    }
    return enabledTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CloudConfigurationImpl that = (CloudConfigurationImpl)o;

    //return getHash() == that.getHash() && id == that.id;
    return id == that.id;
  }

  @Override
  public int hashCode() {
    return id;
  }
}
