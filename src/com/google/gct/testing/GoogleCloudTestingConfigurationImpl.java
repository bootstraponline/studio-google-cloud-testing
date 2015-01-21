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

import com.android.tools.idea.run.GoogleCloudTestingConfiguration;
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

public class GoogleCloudTestingConfigurationImpl implements GoogleCloudTestingConfiguration {

  public static final int ALL_ID = Integer.MAX_VALUE;

  private static int nextAvailableID = 1;

  private int id;
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

  public GoogleCloudTestingConfigurationImpl(int id, String name, Icon icon, AndroidFacet facet) {
    if (id != ALL_ID && id >= nextAvailableID) {
      nextAvailableID = id + 1;
    }
    this.id = id;
    this.name = name;
    this.icon = icon;
    this.facet = facet;
    isEditable = true;
    createDimensions();
  }

  public GoogleCloudTestingConfigurationImpl(String name, Icon icon, AndroidFacet facet) {
    this(nextAvailableID++, name, icon, facet);
  }

  @VisibleForTesting
  GoogleCloudTestingConfigurationImpl(String name, int minSdkVersion, List<String> locales) {
    id = nextAvailableID++;
    this.name = name;
    facet = null;
    isEditable = true;
    deviceDimension = new DeviceDimension(this);
    apiDimension = new ApiDimension(this, minSdkVersion);
    languageDimension = new LanguageDimension(this, locales);
    orientationDimension = new OrientationDimension(this);
  }

  public GoogleCloudTestingConfigurationImpl(AndroidFacet facet) {
    id = nextAvailableID++;
    name = "Unnamed";
    icon = GoogleCloudTestingConfigurationFactoryImpl.DEFAULT_ICON;
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

  /**
   * A single number representation of a matrix configuration.
   */
  private int getHash() {
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

  @Override
  public int getId() {
    return id;
  }

  @Override
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
  @Override
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
   * Precondition: countCombinations() > 0.
   */
  public String prepareMatrixTestRequest() {
    Preconditions.checkState(countCombinations() > 0, "There should be at least one combination in a matrix test request!");

    StringBuffer bf = new StringBuffer();

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
    for (GoogleCloudTestingType type : getDimensions().get(dimensionIndex).getEnabledTypes()) {
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
  public GoogleCloudTestingConfigurationImpl clone() {
    return copy(null);
  }

  public GoogleCloudTestingConfigurationImpl copy(String prefix) {
    GoogleCloudTestingConfigurationImpl newConfiguration = prefix == null
                                                       ? new GoogleCloudTestingConfigurationImpl(id, name, icon, facet) //clone
                                                       : new GoogleCloudTestingConfigurationImpl(prefix + name, icon, facet);
    newConfiguration.deviceDimension.enableAll(deviceDimension.getEnabledTypes());
    newConfiguration.apiDimension.enableAll(apiDimension.getEnabledTypes());
    newConfiguration.languageDimension.enableAll(languageDimension.getEnabledTypes());
    newConfiguration.orientationDimension.enableAll(orientationDimension.getEnabledTypes());
    return newConfiguration;
  }

  public GoogleCloudTestingPersistentConfiguration getPersistentConfiguration() {
    GoogleCloudTestingPersistentConfiguration persistentConfiguration = new GoogleCloudTestingPersistentConfiguration();
    persistentConfiguration.id = id;
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

    GoogleCloudTestingConfigurationImpl that = (GoogleCloudTestingConfigurationImpl)o;

    return getHash() == that.getHash() && id == that.id;
  }

  @Override
  public int hashCode() {
    return id;
  }
}
