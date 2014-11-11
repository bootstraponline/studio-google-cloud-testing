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
import com.google.gct.testing.GoogleCloudTestingConfigurationImpl;
import icons.AndroidIcons;

import javax.swing.*;
import java.util.List;

import static com.google.gct.testing.launcher.CloudAuthenticator.getAndroidDeviceCatalog;

public class OrientationDimension extends GoogleCloudTestingDimension {

  public static final String DISPLAY_NAME = "Orientation";

  //public static final Orientation PORTRAIT = new Orientation("portrait", "Portrait");
  //public static final Orientation LANDSCAPE = new Orientation("landscape", "Landscape");

  private static ImmutableList<Orientation> FULL_DOMAIN;

  public OrientationDimension(GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration) {
    super(googleCloudTestingConfiguration);
  }

  @Override
  public List<? extends GoogleCloudTestingType> getAppSupportedDomain() {
    return getFullDomain();
  }

  public static List<? extends GoogleCloudTestingType> getFullDomain() {
    if (FULL_DOMAIN == null || shouldPollDiscoveryTestApi(DISPLAY_NAME)) {
      ImmutableList.Builder<Orientation> fullDomainBuilder = new ImmutableList.Builder<Orientation>();
      List<com.google.api.services.test.model.Orientation> orientations =
        getAndroidDeviceCatalog().getRuntimeConfiguration().getOrientations();
      for (com.google.api.services.test.model.Orientation orientation : orientations) {
        fullDomainBuilder.add(new Orientation(orientation.getId(), orientation.getName()));
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
    return "ORIENTATION";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Configs.Orientation;
  }

  public static class Orientation extends GoogleCloudTestingType {

    private final String id;
    private final String name;

    public Orientation(String id, String name) {
      this.id = id;
      this.name = name;
      this.details = ImmutableMap.of();
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return name;
    }

    @Override
    public String getId() {
      return id;
    }
  }
}
