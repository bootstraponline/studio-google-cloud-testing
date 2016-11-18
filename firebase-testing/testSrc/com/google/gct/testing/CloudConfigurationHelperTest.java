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
package com.google.gct.testing;


import com.google.api.services.testing.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gct.testing.android.CloudConfiguration;
import com.google.gct.testing.dimension.CloudTestingType;
import com.google.gct.testing.launcher.CloudAuthenticator;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

import java.util.List;

public class CloudConfigurationHelperTest extends AndroidTestCase {

  public void testDefaultConfigurations() {
    CloudAuthenticator cloudAuthenticator = Mockito.mock(CloudAuthenticator.class);
    CloudAuthenticator.setInstance(cloudAuthenticator);

    Mockito.when(cloudAuthenticator.getAndroidDeviceCatalog()).thenReturn(createAndroidDeviceCatalog());

    List<? extends CloudConfiguration> defaultConfigurations =
      CloudConfigurationHelper.getDefaultConfigurations(myFacet, CloudConfiguration.Kind.MATRIX);

    assertEquals("Unexpected number of default configurations", 2, defaultConfigurations.size());

    CloudConfigurationImpl sampleSparkConfiguration = (CloudConfigurationImpl)defaultConfigurations.get(0);
    assertEquals("Unexpected name of sample Spark configuration", "Sample Spark configuration (4)", sampleSparkConfiguration.getDisplayName());
    assertEquals("Unexpected size of sample Spark configuration", 4, sampleSparkConfiguration.getDeviceConfigurationCount());

    ImmutableList<CloudTestingType> devices = sampleSparkConfiguration.getDeviceDimension().getEnabledTypes();
    assertEquals("Unexpected number of devices in sample Spark configuration", 2, devices.size());
    assertEquals("Unexpected device in sample Spark configuration", "Nexus9", devices.get(0).getId());
    assertEquals("Unexpected device in sample Spark configuration", "shamu", devices.get(1).getId());

    ImmutableList<CloudTestingType> apis = sampleSparkConfiguration.getApiDimension().getEnabledTypes();
    assertEquals("Unexpected number of API levels in sample Spark configuration", 2, apis.size());
    assertEquals("Unexpected API level in sample Spark configuration", "23", apis.get(0).getId());
    assertEquals("Unexpected API level in sample Spark configuration", "22", apis.get(1).getId());

    ImmutableList<CloudTestingType> languages = sampleSparkConfiguration.getLanguageDimension().getEnabledTypes();
    assertEquals("Unexpected number of languages in sample Spark configuration", 1, languages.size());
    assertEquals("Unexpected language in sample Spark configuration", "en", languages.get(0).getId());

    ImmutableList<CloudTestingType> orientations = sampleSparkConfiguration.getOrientationDimension().getEnabledTypes();
    assertEquals("Unexpected number of orientations in sample Spark configuration", 1, orientations.size());
    assertEquals("Unexpected orientation in sample Spark configuration", "portrait", orientations.get(0).getId());


    CloudConfigurationImpl sampleConfiguration = (CloudConfigurationImpl)defaultConfigurations.get(1);
    assertEquals("Unexpected name of sample configuration", "Sample configuration (18)", sampleConfiguration.getDisplayName());
    assertEquals("Unexpected size of sample configuration", 18, sampleConfiguration.getDeviceConfigurationCount());
    assertEquals("Unexpected number of devices in sample configuration", 3, sampleConfiguration.getDeviceDimension().getEnabledTypes().size());
    assertEquals("Unexpected number of API levels in sample configuration", 3, sampleConfiguration.getApiDimension().getEnabledTypes().size());
    assertEquals("Unexpected number of languages in sample configuration", 1, sampleConfiguration.getLanguageDimension().getEnabledTypes().size());
    assertEquals("Unexpected number of orientations in sample configuration", 2, sampleConfiguration.getOrientationDimension().getEnabledTypes().size());
  }

  private AndroidDeviceCatalog createAndroidDeviceCatalog() {
    AndroidDeviceCatalog deviceCatalog = new AndroidDeviceCatalog();

    List<AndroidModel> androidModels = Lists.newLinkedList();
    addAndroidModel(androidModels, "Nexus6", "Nexus 6");
    addAndroidModel(androidModels, "hammerhead", "Nexus 5");
    addAndroidModel(androidModels, "mako", "Nexus 4");
    addAndroidModel(androidModels, "Nexus9", "Nexus 9");
    addAndroidModel(androidModels, "shamu", "Nexus 6");
    deviceCatalog.setModels(androidModels);

    List<AndroidVersion> androidVersions = Lists.newLinkedList();
    addAndroidVersion(androidVersions, "19", 19);
    addAndroidVersion(androidVersions, "21", 21);
    addAndroidVersion(androidVersions, "22", 22);
    addAndroidVersion(androidVersions, "23", 23);
    deviceCatalog.setVersions(androidVersions);

    AndroidRuntimeConfiguration runtimeConfiguration = new AndroidRuntimeConfiguration();

    List<Locale> locales = Lists.newLinkedList();
    addLocale(locales, "en", "en", "default");
    runtimeConfiguration.setLocales(locales);

    List<Orientation> orientations = Lists.newLinkedList();
    addOrientation(orientations, "landscape", "Landscape", "");
    addOrientation(orientations, "portrait", "Portrait", "default");
    runtimeConfiguration.setOrientations(orientations);

    deviceCatalog.setRuntimeConfiguration(runtimeConfiguration);

    return deviceCatalog;
  }

  private void addAndroidModel(List<AndroidModel> androidModels, String id, String name) {
    AndroidModel androidModel = new AndroidModel();
    androidModel.setId(id);
    androidModel.setName(name);
    androidModels.add(androidModel);
  }

  private void addAndroidVersion(List<AndroidVersion> androidVersions, String id, int apiLevel) {
    AndroidVersion androidVersion = new AndroidVersion();
    androidVersion.setId(id);
    androidVersion.setApiLevel(apiLevel);
    androidVersions.add(androidVersion);
  }

  private void addLocale(List<Locale> locales, String id, String name, String tag) {
    Locale locale = new Locale();
    locale.setId(id);
    locale.setName(name);
    locale.setTags(Lists.newArrayList(tag));
    locales.add(locale);
  }

  private void addOrientation(List<Orientation> orientations, String id, String name, String tag) {
    Orientation orientation = new Orientation();
    orientation.setId(id);
    orientation.setName(name);
    orientation.setTags(Lists.newArrayList(tag));
    orientations.add(orientation);
  }
}
