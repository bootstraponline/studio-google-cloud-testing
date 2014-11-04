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

import com.google.api.services.test.model.AndroidModel;
import com.google.common.collect.ImmutableList;
import com.google.gct.testing.GoogleCloudTestingConfiguration;
import icons.AndroidIcons;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.gct.testing.launcher.CloudAuthenticator.getAndroidDeviceCatalog;

public class DeviceDimension extends GoogleCloudTestingDimension {

  public static final String DISPLAY_NAME = "Device";

  //public static final Device NEXUS_5 =
  //  new Device("Nexus5", "Google", "Nexus 5", ImmutableMap.of("RAM", "2GB", "Disk", "16-32GB", "Display", "1080x1920", "Min API Level", "19"));
  //public static final Device NEXUS_7 =
  //  new Device("Nexus7", "Google", "Nexus 7", ImmutableMap.of("RAM", "2GB", "Disk", "16-32GB", "Display", "1200x1920", "Min API Level", "18"));
  //public static final Device GS_5 =
  //  new Device("GalaxyS5", "Samsung", "Galaxy S5", ImmutableMap.of("RAM", "2GB", "Disk", "16-32GB", "Display", "1080x1920", "Min API Level", "19"));

  private static ImmutableList<Device> FULL_DOMAIN;


  public DeviceDimension(GoogleCloudTestingConfiguration googleCloudTestingConfiguration) {
    super(googleCloudTestingConfiguration);
  }

  @Override
  public List<? extends GoogleCloudTestingType> getAppSupportedDomain() {
    return getFullDomain();
  }

  public static List<? extends GoogleCloudTestingType> getFullDomain() {
    if (FULL_DOMAIN == null || shouldPollDiscoveryTestApi(DISPLAY_NAME)) {
      ImmutableList.Builder<Device> fullDomainBuilder = new ImmutableList.Builder<Device>();
      for (AndroidModel model : getAndroidDeviceCatalog().getModels()) {
        Map<String, String> details = new HashMap<String, String>();
        details.put("Display", model.getScreenX() + "x" + model.getScreenY());
        fullDomainBuilder.add(new Device(model.getId(), model.getName(), model.getManufacturer(), model.getForm(), details));
      }
      FULL_DOMAIN = fullDomainBuilder.build();
      resetDiscoveryTestApiUpdateTimestamp(DISPLAY_NAME);
    }
    return FULL_DOMAIN;
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

  @Override
  public boolean shouldBeAlwaysGrouped() {
    return true;
  }

  public static class Device extends GoogleCloudTestingType {

    private final String id;
    private final String name;
    private final String manufacturer;
    private final String form;

    public Device(String id, String name, String manufacturer, String form, Map<String, String> details) {
      this.id = id;
      this.manufacturer = manufacturer;
      this.name = name;
      this.form = form;
      this.details = details;
    }

    @Override
    public String getGroupName() {
      return form; // Group devices by their form, i.e., VIRTUAL or PHYSICAL.
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return manufacturer + " " + name;
    }

    @Override
    public String getId() {
      return id;
    }
  }
}
