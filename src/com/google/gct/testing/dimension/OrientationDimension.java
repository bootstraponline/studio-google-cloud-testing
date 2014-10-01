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

public class OrientationDimension extends GoogleCloudTestingDimension {

  public static final String DISPLAY_NAME = "Orientation";

  public static final Orientation PORTRAIT = new Orientation("Portrait");
  public static final Orientation LANDSCAPE = new Orientation("Landscape");

  public OrientationDimension(GoogleCloudTestingConfiguration googleCloudTestingConfiguration) {
    super(googleCloudTestingConfiguration);
  }

  @Override
  public List<? extends GoogleCloudTestingType> getAppSupportedDomain() {
    return ImmutableList.of(PORTRAIT, LANDSCAPE);
  }

  public static List<? extends GoogleCloudTestingType> getFullDomain() {
    return ImmutableList.of(PORTRAIT, LANDSCAPE);
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

    String name;

    public Orientation(String name) {
      this.name = name;
      this.details = ImmutableMap.of();
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return name;
    }

    @Override
    public String getId() {
      return name.toLowerCase();
    }
  }
}