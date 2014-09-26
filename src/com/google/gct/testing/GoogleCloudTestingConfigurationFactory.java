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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gct.testing.dimension.*;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.*;

public class GoogleCloudTestingConfigurationFactory {

  public static final Icon DEFAULT_ICON = AndroidIcons.AndroidFile;

  public static final Function<GoogleCloudTestingConfiguration,GoogleCloudTestingConfiguration> CLONE_CONFIGURATIONS =
    new Function<GoogleCloudTestingConfiguration, GoogleCloudTestingConfiguration>() {
      @Override
      public GoogleCloudTestingConfiguration apply(GoogleCloudTestingConfiguration configuration) {
        return configuration.clone();
      }
    };

  public static Map<String, List<? extends GoogleCloudTestingType>> getAllDimensionTypes() {
    Map<String, List<? extends GoogleCloudTestingType>> dimensionTypes = new HashMap<String, List<? extends GoogleCloudTestingType>>();
    dimensionTypes.put(DeviceDimension.DISPLAY_NAME, DeviceDimension.getFullDomain());
    dimensionTypes.put(ApiDimension.DISPLAY_NAME, ApiDimension.getFullDomain());
    dimensionTypes.put(LanguageDimension.DISPLAY_NAME, LanguageDimension.getFullDomain());
    dimensionTypes.put(OrientationDimension.DISPLAY_NAME, OrientationDimension.getFullDomain());
    return dimensionTypes;
  }

  public static ArrayList<GoogleCloudTestingConfiguration> getCustomConfigurationsFromStorage(AndroidFacet facet) {
    List<GoogleCloudTestingPersistentConfiguration> googleCloudTestingPersistentConfigurations =
      GoogleCloudTestingCustomPersistentConfigurations.getInstance(facet.getModule().getProject()).getState().myGoogleCloudTestingPersistentConfigurations;
    return Lists.newArrayList(deserializeConfigurations(googleCloudTestingPersistentConfigurations, true, facet));
  }

  public static ImmutableList<GoogleCloudTestingConfiguration> getDefaultConfigurationsFromStorage(AndroidFacet facet) {
    GoogleCloudTestingConfiguration allConfiguration = new GoogleCloudTestingConfiguration("All", AndroidIcons.Display, facet);
    allConfiguration.deviceDimension.enableAll();
    allConfiguration.apiDimension.enableAll();
    allConfiguration.languageDimension.enableAll();
    allConfiguration.orientationDimension.enableAll();
    allConfiguration.setNonEditable();
    return ImmutableList.of(allConfiguration);
    //TODO: For now, there are no default configurations to store/read from the persistent storage (i.e., an xml file).
    //List<GoogleCloudTestingPersistentConfiguration> myGoogleCloudTestingPersistentConfigurations =
    //  GoogleCloudTestingDefaultPersistentConfigurations.getInstance(facet.getModule().getProject()).getState().myGoogleCloudTestingPersistentConfigurations;
    //return ImmutableList.copyOf(deserializeConfigurations(myGoogleCloudTestingPersistentConfigurations, false, facet));
  }

  public static List<GoogleCloudTestingConfiguration> deserializeConfigurations(
    final List<GoogleCloudTestingPersistentConfiguration> persistentConfigurations, boolean isEditable, AndroidFacet facet) {
    List<GoogleCloudTestingConfiguration> googleCloudTestingConfigurations = new LinkedList<GoogleCloudTestingConfiguration>();
    for (GoogleCloudTestingPersistentConfiguration persistentConfiguration : persistentConfigurations) {
      Icon icon = getIcon(persistentConfiguration.name, isEditable);
      GoogleCloudTestingConfiguration configuration = new GoogleCloudTestingConfiguration(persistentConfiguration.name, icon, facet);
      configuration.deviceDimension.enable(DeviceDimension.getFullDomain(), persistentConfiguration.devices);
      configuration.apiDimension.enable(ApiDimension.getFullDomain(), persistentConfiguration.apiLevels);
      configuration.languageDimension.enable(LanguageDimension.getFullDomain(), persistentConfiguration.languages);
      configuration.orientationDimension.enable(OrientationDimension.getFullDomain(), persistentConfiguration.orientations);
      if (!isEditable) {
        configuration.setNonEditable();
      }
      googleCloudTestingConfigurations.add(configuration);
    }
    return googleCloudTestingConfigurations;
  }

  private static Icon getIcon(String configurationName, boolean isEditable) {
    if (isEditable) {
      return AndroidIcons.AndroidFile;
    }
    if (configurationName.equals("All Available")) {
      return AndroidIcons.Display;
    }
    return AndroidIcons.Portrait;
  }
}
