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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.google.gct.testing.GoogleCloudTestingConfiguration;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;

public class ApiDimension extends GoogleCloudTestingDimension {

  public static final String DISPLAY_NAME = "Platform";

  public static final ApiLevel KITKAT_19 =
    new ApiLevel("KitKat", "4.4.3", 19, ImmutableMap.of("Release date", "October 2013", "Market share", "13.6%"));
  public static final ApiLevel JELLY_BEAN_18 =
    new ApiLevel("Jelly Bean", "4.3.1", 18, ImmutableMap.of("Release date", "July 2013", "Market share", "10.3%"));
  public static final ApiLevel JELLY_BEAN_17 =
    new ApiLevel("Jelly Bean", "4.2.2", 17, ImmutableMap.of("Release date", "November 2012", "Market share", "19.1%"));
  public static final ApiLevel JELLY_BEAN_16 =
    new ApiLevel("Jelly Bean", "4.1.2", 16, ImmutableMap.of("Release date", "July 2012", "Market share", "29.0%"));
  public static final ApiLevel ICE_CREAM_SANDWICH_15 =
    new ApiLevel("Ice Cream Sandwich", "4.0.4", 15, ImmutableMap.of("Release date", "December 2011", "Market share", "12.3% (all Ice Cream Sandwich)"));
  public static final ApiLevel ICE_CREAM_SANDWICH_14 =
    new ApiLevel("Ice Cream Sandwich", "4.0.2", 14, ImmutableMap.of("Release date", "October 2011", "Market share", "12.3% (all Ice Cream Sandwich)"));
  public static final ApiLevel GINGERBREAD_10 =
    new ApiLevel("Gingerbread", "2.3.7", 10, ImmutableMap.of("Release date", "February 2011", "Market share", "14.9% (all Gingerbread)"));
  public static final ApiLevel GINGERBREAD_9 =
    new ApiLevel("Gingerbread", "2.3.2", 9, ImmutableMap.of("Release date", "December 2010", "Market share", "14.9% (all Gingerbread)"));
  public static final ApiLevel FROYO_8 =
    new ApiLevel("Froyo", "2.2.3", 8, ImmutableMap.of("Release date", "May 2010", "Market share", "0.8%"));

  private static final Set<ApiLevel> BACKEND_SUPPORTED_API_VERSIONS =
    ImmutableSet.of(KITKAT_19, JELLY_BEAN_18, JELLY_BEAN_17, JELLY_BEAN_16, ICE_CREAM_SANDWICH_15, ICE_CREAM_SANDWICH_14);

  private final int minSdkVersion;

  public ApiDimension(GoogleCloudTestingConfiguration googleCloudTestingConfiguration, AndroidFacet facet) {
    super(googleCloudTestingConfiguration);
    minSdkVersion = facet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel();
    // facet.getManifest().getUsesSdks().get(0).getMinSdkVersion() would read the app's manifest min SDK rather than the global one.
  }

  @VisibleForTesting
  public ApiDimension(GoogleCloudTestingConfiguration googleCloudTestingConfiguration, int minSdkVersion) {
    super(googleCloudTestingConfiguration);
    this.minSdkVersion = minSdkVersion;
  }

  @Override
  public List<? extends GoogleCloudTestingType> getAppSupportedDomain() {
    return Lists.newArrayList(Iterables.filter(getFullDomain(), new Predicate<GoogleCloudTestingType>() {
      @Override
      public boolean apply(GoogleCloudTestingType input) {
        if (input instanceof ApiLevel) {
          return ((ApiLevel)input).apiVersion >= minSdkVersion;
        }
        return false;
      }
    }));
  }

  @Override
  public List<? extends GoogleCloudTestingType> getSupportedDomain() {
    return Lists.newArrayList(Iterables.filter(getAppSupportedDomain(), new Predicate<GoogleCloudTestingType>() {
      @Override
      public boolean apply(GoogleCloudTestingType type) {
        return BACKEND_SUPPORTED_API_VERSIONS.contains(type);
      }
    }));
  }

  public static List<? extends GoogleCloudTestingType> getFullDomain() {
    // Sort them in descending order of api version.
    return Ordering.from(API_LEVEL_COMPARATOR).reverse().sortedCopy(Lists.newArrayList(FROYO_8, GINGERBREAD_9, GINGERBREAD_10,
                                                                                       ICE_CREAM_SANDWICH_14, ICE_CREAM_SANDWICH_15,
                                                                                       JELLY_BEAN_16, JELLY_BEAN_17, JELLY_BEAN_18,
                                                                                       KITKAT_19));
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getId() {
    return "APILEVEL";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.SdkManager;
  }

  public static class ApiLevel extends GoogleCloudTestingType {

    String codeName;
    String osVersion;
    int apiVersion;

    public ApiLevel(String codeName, String osVersion, int apiVersion, Map<String, String> details) {
      this.codeName = codeName;
      this.osVersion = osVersion;
      this.details = details;
      this.apiVersion = apiVersion;
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return String.format("Android %s - %s (API %d)", osVersion, codeName, apiVersion);
    }

    @Override
    public String getId() {
      return "" + apiVersion;
    }
  }

  private static final Comparator<ApiLevel> API_LEVEL_COMPARATOR = new Comparator<ApiLevel>() {
    @Override
    public int compare(ApiLevel level1, ApiLevel level2) {
      return level1.apiVersion - level2.apiVersion;
    }
  };
}
