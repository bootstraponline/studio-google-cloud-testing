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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gct.testing.GoogleCloudTestingConfiguration;
import icons.AndroidIcons;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class DeviceDimension extends GoogleCloudTestingDimension {

  public static final String DISPLAY_NAME = "Device";

  public static final Device NEXUS_5 =
    new Device("Nexus5", "Google", "Nexus 5", ImmutableMap.of("RAM", "2GB", "Disk", "16-32GB", "Display", "1080x1920", "Min API Level", "19"));
  public static final Device NEXUS_7 =
    new Device("Nexus7", "Google", "Nexus 7", ImmutableMap.of("RAM", "2GB", "Disk", "16-32GB", "Display", "1200x1920", "Min API Level", "18"));
  //public static final Device GS_5 =
  //  new Device("GalaxyS5", "Samsung", "Galaxy S5", ImmutableMap.of("RAM", "2GB", "Disk", "16-32GB", "Display", "1080x1920", "Min API Level", "19"));


  public DeviceDimension(GoogleCloudTestingConfiguration googleCloudTestingConfiguration) {
    super(googleCloudTestingConfiguration);
  }

  @Override
  public List<? extends GoogleCloudTestingType> getAppSupportedDomain() {
    return ImmutableList.of(NEXUS_5, NEXUS_7);
  }

  public static List<? extends GoogleCloudTestingType> getFullDomain() {
    return ImmutableList.of(NEXUS_5, NEXUS_7);
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getId() {
    return "DEVICE";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Views.DeviceScreen;
  }

  public static class Device extends GoogleCloudTestingType {

    String id;
    String make;
    String model;

    public Device(String id, String make, String model, Map<String, String> details) {
      this.id = id;
      this.make = make;
      this.model = model;
      this.details = details;
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return make + " " + model;
    }

    @Override
    public String getId() {
      return id;
    }
  }
}
