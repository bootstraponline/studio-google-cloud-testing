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

public class GoogleCloudTestingConfiguration {

  private String name;
  private Icon icon;
  private final AndroidFacet facet;
  private boolean isEditable;

  private final Set<ConfigurationChangeListener> changeListeners = new HashSet<ConfigurationChangeListener>();

  // Dimensions
  DeviceDimension deviceDimension;
  ApiDimension apiDimension;
  LanguageDimension languageDimension;
  OrientationDimension orientationDimension;

  public GoogleCloudTestingConfiguration(String name, Icon icon, AndroidFacet facet) {
    this.name = name;
    this.icon = icon;
    this.facet = facet;
    isEditable = true;
    createDimensions();
  }

  @VisibleForTesting
  public GoogleCloudTestingConfiguration(String name, int minSdkVersion, List<String> locales) {
    this.name = name;
    facet = null;
    isEditable = true;
    deviceDimension = new DeviceDimension(this);
    apiDimension = new ApiDimension(this, minSdkVersion);
    languageDimension = new LanguageDimension(this, locales);
    orientationDimension = new OrientationDimension(this);
  }

  public GoogleCloudTestingConfiguration(AndroidFacet facet) {
    name = "Unnamed";
    icon = GoogleCloudTestingConfigurationFactory.DEFAULT_ICON;
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

  public int getHash() {
    return (name + FluentIterable.from(getDimensions()).transform(new Function<GoogleCloudTestingDimension, String>() {
      @Override
      public String apply(GoogleCloudTestingDimension dimension) {
        return getStringRepresentation(dimension);
      }
    }).toString()).hashCode();
  }

  private String getStringRepresentation(GoogleCloudTestingDimension dimension) {
    StringBuffer sb = new StringBuffer();
    sb.append(dimension.getId());
    for (GoogleCloudTestingType type : dimension.getEnabledTypes()) {
      sb.append(type.getId());
    }
    return sb.toString();
  }

  public String getDisplayName() {
    return name + " (" + countCombinations() + ")";
  }

  public void setName(String name) {
    this.name = name;
    for (ConfigurationChangeListener changeListener : changeListeners) {
      changeListener.configurationChanged(new ConfigurationChangeEvent(this));
    }
  }

  public List<GoogleCloudTestingDimension> getDimensions() {
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

  public void dimensionChanged(GoogleCloudTestingDimension dimension) {
    for (ConfigurationChangeListener changeListener : changeListeners) {
      changeListener.configurationChanged(new ConfigurationChangeEvent(this));
    }
  }

  /**
   * Returns the number of different combinations represented
   * by all of the types enabled in this configuration.
   */
  public int countCombinations() {
    int product = 1;

    for (GoogleCloudTestingDimension dimension : getDimensions()) {
      product *= dimension.getEnabledTypes().size();
    }

    return product;
  }

  public int countCombinationsCollapsingOrientation() {
    int product = 1;

    for (GoogleCloudTestingDimension dimension : getDimensions()) {
      if (dimension instanceof OrientationDimension) {
        product *= Math.min(dimension.getEnabledTypes().size(), 1);
      } else {
        product *= dimension.getEnabledTypes().size();
      }
    }

    return product;
  }

  public boolean isEditable() {
    return isEditable;
  }

  public void setNonEditable() {
    isEditable = false;
  }

  public String toString() {
    return name;
  }

  public Icon getIcon() { return icon; }

  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  /**
   * Precondition: countCombinations() > 0.
   */
  public String prepareJenkinsRequest() {
    Preconditions.checkState(countCombinations() > 0, "There should be at least one combination in a Jenkins request!");

    StringBuffer bf = new StringBuffer();

    //bf.append("-PmatrixFilter=\"");

    boolean firstDim = true;
    for (GoogleCloudTestingDimension dimension : getDimensions()) {
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
    //bf.append("\"");

    return bf.toString();

    //String s= "-PmatrixFilter=\"(DEVICE=='nexus5' || DEVICE=='nexus7') && OSVERSION=='jellybean' && LANGUAGE=='english'\"";
  }

  public List<String> computeConfigurationInstancesForResultsViewer() {
    List<String> configurationInstances = new LinkedList<String>();
    computeConfigurationInstancesForResultsViewerRecursively("", 0, configurationInstances);
    return configurationInstances;
  }

  private void computeConfigurationInstancesForResultsViewerRecursively(
    String partialConfigurationInstance, int dimensionIndex, List<String> configurationInstances) {

    if (dimensionIndex >= getDimensions().size()) {
      configurationInstances.add(partialConfigurationInstance);
      return;
    }

    String separator = dimensionIndex == 0 ? "" : ConfigurationInstance.DISPLAY_NAME_DELIMITER;
    for (GoogleCloudTestingType type : getDimensions().get(dimensionIndex).getEnabledTypes()) {
      computeConfigurationInstancesForResultsViewerRecursively(
        partialConfigurationInstance + separator + type.getResultsViewerDisplayName(), dimensionIndex + 1, configurationInstances);
    }
  }

  private StringBuffer prepareDimensionRequest(GoogleCloudTestingDimension dimension) {
    StringBuffer bf = new StringBuffer();
    boolean firstType = true;
    for (GoogleCloudTestingType type : dimension.getEnabledTypes()) {
      if (!firstType) {
        bf.append(" || ");
      }
      firstType = false;
      bf.append(dimension.getId() + "=='" + type.getId() + "'");
    }
    return bf;
  }

  @Override
  public GoogleCloudTestingConfiguration clone() {
    return copy("");
  }

  public GoogleCloudTestingConfiguration copy(String prefix) {
    GoogleCloudTestingConfiguration newConfiguration = new GoogleCloudTestingConfiguration(prefix + name, icon, facet);
    newConfiguration.deviceDimension.enableAll(deviceDimension.getEnabledTypes());
    newConfiguration.apiDimension.enableAll(apiDimension.getEnabledTypes());
    newConfiguration.languageDimension.enableAll(languageDimension.getEnabledTypes());
    newConfiguration.orientationDimension.enableAll(orientationDimension.getEnabledTypes());
    return newConfiguration;
  }

  public GoogleCloudTestingPersistentConfiguration getPersistentConfiguration() {
    GoogleCloudTestingPersistentConfiguration persistentConfiguration = new GoogleCloudTestingPersistentConfiguration();
    persistentConfiguration.name = name;
    persistentConfiguration.devices = getEnabledTypes(deviceDimension);
    persistentConfiguration.apiLevels = getEnabledTypes(apiDimension);
    persistentConfiguration.languages = getEnabledTypes(languageDimension);
    persistentConfiguration.orientations = getEnabledTypes(orientationDimension);
    return persistentConfiguration;
  }

  private List<String> getEnabledTypes(GoogleCloudTestingDimension dimension) {
    List<String> enabledTypes = new LinkedList<String>();
    for (GoogleCloudTestingType type : dimension.getEnabledTypes()) {
      enabledTypes.add(type.getId());
    }
    return enabledTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GoogleCloudTestingConfiguration that = (GoogleCloudTestingConfiguration)o;

    if (name != null ? !name.equals(that.name) : that.name != null) return false;

    return getHash() == that.getHash();
  }

  @Override
  public int hashCode() {
    return name != null ? name.hashCode() : 0;
  }
}
