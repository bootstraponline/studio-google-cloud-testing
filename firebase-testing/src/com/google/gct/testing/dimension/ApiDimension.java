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
import com.google.api.services.testing.model.AndroidVersion;
import com.google.api.services.testing.model.Date;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gct.testing.CloudConfigurationImpl;
import com.google.gct.testing.launcher.CloudAuthenticator;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.*;


public class ApiDimension extends CloudConfigurationDimension {

  public static final String DISPLAY_NAME = "Platform";

  private static ImmutableList<ApiLevel> FULL_DOMAIN;
  private static ApiLevel defaultApi;
  private final int minSdkVersion;


  public ApiDimension(CloudConfigurationImpl googleCloudTestingConfiguration, AndroidFacet facet) {
    super(googleCloudTestingConfiguration);
    minSdkVersion = facet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel();
    // facet.getManifest().getUsesSdks().get(0).getMinSdkVersion() would read the app's manifest min SDK rather than the global one.
  }

  @VisibleForTesting
  public ApiDimension(CloudConfigurationImpl googleCloudTestingConfiguration, int minSdkVersion) {
    super(googleCloudTestingConfiguration);
    this.minSdkVersion = minSdkVersion;
  }

  @Override
  public List<? extends CloudTestingType> getAppSupportedDomain() {
    return Lists.newArrayList(Iterables.filter(getFullDomain(), new Predicate<CloudTestingType>() {
      @Override
      public boolean apply(CloudTestingType input) {
        if (input instanceof ApiLevel) {
          return ((ApiLevel)input).apiVersion >= minSdkVersion;
        }
        return false;
      }
    }));
  }

  @Override
  public List<? extends CloudTestingType> getSupportedDomain() {
    return Lists.newArrayList(Iterables.filter(getAppSupportedDomain(), new Predicate<CloudTestingType>() {
      @Override
      public boolean apply(CloudTestingType type) {
        //return BACKEND_SUPPORTED_API_VERSIONS.contains(type);
        return true;
      }
    }));
  }

  public static List<? extends CloudTestingType> getFullDomain() {
    if (isFullDomainMissing() || shouldPollDiscoveryTestApi(DISPLAY_NAME)) {
      List<ApiLevel> apiLevels = new LinkedList<ApiLevel>();
      AndroidDeviceCatalog androidDeviceCatalog = CloudAuthenticator.getInstance().getAndroidDeviceCatalog();
      if (androidDeviceCatalog != null) {
        for (AndroidVersion version : androidDeviceCatalog.getVersions()) {
          Map<String, String> details = new HashMap<String, String>();
          Date date = version.getReleaseDate();
          details.put("Release date",
                      date == null ? "???" : String.format("%4d-%02d-%02d", date.getYear(), date.getMonth(), date.getDay()));
          //TODO: Uncomment when we actually provide the market share data.
          //Distribution distribution = version.getDistribution();
          //details.put("Market share", distribution == null ? "???" : distribution.getMarketShare() + "%");
          ApiLevel apiLevel =
            new ApiLevel(version.getId(), version.getCodeName(), version.getVersionString(), version.getApiLevel(), details);
          apiLevels.add(apiLevel);
          List<String> tags = version.getTags();
          if (tags != null && tags.contains("default")) {
            defaultApi = apiLevel;
          }
        }
      }
      // Do not reset a valid full domain if some intermittent issues happened.
      if (isFullDomainMissing() || !apiLevels.isEmpty()) {
        // Sort them in descending order of api version.
        FULL_DOMAIN = ImmutableList.copyOf(Ordering.from(API_LEVEL_COMPARATOR).reverse().sortedCopy(apiLevels));
      }
      resetDiscoveryTestApiUpdateTimestamp(DISPLAY_NAME);
    }
    return FULL_DOMAIN;
  }

  private static boolean isFullDomainMissing() {
    return FULL_DOMAIN == null || FULL_DOMAIN.isEmpty();
  }

  private static ApiLevel getDefaultApi() {
    if (defaultApi == null) {
      getFullDomain();
    }
    return defaultApi;
  }

  public void enableDefault() {
    if (getDefaultApi() == null) {
      return;
    }
    List<? extends CloudTestingType> appSupportedDomain = getAppSupportedDomain();
    if (appSupportedDomain.contains(defaultApi)) {
      enable(defaultApi);
    } else if (!appSupportedDomain.isEmpty()) {
      enable(appSupportedDomain.get(0));
    }
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

  public static class ApiLevel extends CloudTestingType {

    private final String id;
    private final String codeName;
    private final String osVersion;
    private final int apiVersion;

    public ApiLevel(String id, String codeName, String osVersion, int apiVersion, Map<String, String> details) {
      this.id = id;
      this.codeName = codeName;
      this.osVersion = osVersion;
      this.details = details;
      this.apiVersion = apiVersion;
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return String.format("Android %s, API Level %d (%s)", osVersion, apiVersion, codeName);
    }

    @Override
    public String getId() {
      return id;
    }
  }

  private static final Comparator<ApiLevel> API_LEVEL_COMPARATOR = new Comparator<ApiLevel>() {
    @Override
    public int compare(ApiLevel level1, ApiLevel level2) {
      return level1.apiVersion - level2.apiVersion;
    }
  };
}
