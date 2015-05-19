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

import com.google.api.services.testing.model.AndroidDeviceCatalog;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gct.testing.CloudConfigurationImpl;
import icons.AndroidIcons;

import javax.swing.*;
import java.util.List;

import static com.google.gct.testing.launcher.CloudAuthenticator.getAndroidDeviceCatalog;

public class OrientationDimension extends CloudConfigurationDimension {

  public static final String DISPLAY_NAME = "Orientation";

  //public static final Orientation PORTRAIT = new Orientation("portrait", "Portrait");
  //public static final Orientation LANDSCAPE = new Orientation("landscape", "Landscape");

  private static ImmutableList<Orientation> FULL_DOMAIN;
  private static Orientation defaultOrientation;

  public OrientationDimension(CloudConfigurationImpl googleCloudTestingConfiguration) {
    super(googleCloudTestingConfiguration);
  }

  @Override
  public List<? extends CloudTestingType> getAppSupportedDomain() {
    return getFullDomain();
  }

  public static List<? extends CloudTestingType> getFullDomain() {
    if (isFullDomainMissing() || shouldPollDiscoveryTestApi(DISPLAY_NAME)) {
      ImmutableList.Builder<Orientation> fullDomainBuilder = new ImmutableList.Builder<Orientation>();
      AndroidDeviceCatalog androidDeviceCatalog = getAndroidDeviceCatalog();
      if (androidDeviceCatalog != null) {
        List<com.google.api.services.testing.model.Orientation> modelOrientations =
          androidDeviceCatalog.getRuntimeConfiguration().getOrientations();
        for (com.google.api.services.testing.model.Orientation modelOrientation : modelOrientations) {
          Orientation orientation = new Orientation(modelOrientation.getId(), modelOrientation.getName());
          fullDomainBuilder.add(orientation);
          List<String> tags = modelOrientation.getTags();
          if (tags != null && tags.contains("default")) {
            defaultOrientation = orientation;
          }
        }
      }
      // Do not reset a valid full domain if some intermittent issues happened.
      if (isFullDomainMissing() || !fullDomainBuilder.build().isEmpty()) {
        FULL_DOMAIN = fullDomainBuilder.build();
      }
      resetDiscoveryTestApiUpdateTimestamp(DISPLAY_NAME);
    }
    return FULL_DOMAIN;
  }

  private static boolean isFullDomainMissing() {
    return FULL_DOMAIN == null || FULL_DOMAIN.isEmpty();
  }

  public static Orientation getDefaultOrientation() {
    if (defaultOrientation == null) {
      getFullDomain();
    }
    return defaultOrientation;
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

  public static class Orientation extends CloudTestingType {

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
