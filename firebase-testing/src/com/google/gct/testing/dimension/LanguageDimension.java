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

import com.google.api.client.util.Lists;
import com.google.api.services.testing.model.AndroidDeviceCatalog;
import com.google.api.services.testing.model.Locale;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gct.testing.CloudConfigurationImpl;
import com.google.gct.testing.launcher.CloudAuthenticator;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LanguageDimension extends CloudConfigurationDimension {

  public static final String DISPLAY_NAME = "Locale";

  private static ImmutableList<Language> FULL_DOMAIN;

  private static Language defaultLanguage;

  private final List<Language> supportedLanguages;


  public LanguageDimension(CloudConfigurationImpl googleCloudTestingConfiguration, AndroidFacet facet) {
    super(googleCloudTestingConfiguration);
    final List<String> locales = getLocales(facet);
    supportedLanguages = Lists.newArrayList(Iterables.filter(getFullDomain(), new Predicate<Language>() {
      @Override
      public boolean apply(LanguageDimension.Language input) {
        return locales.contains(input.getId());
      }
    }));
    Collections.sort(supportedLanguages, new Comparator<Language>() {
      @Override
      public int compare(Language lang1, Language lang2) {
        return lang1.getResultsViewerDisplayName().compareTo(lang2.getResultsViewerDisplayName());
      }
    });
    addLocalDefaultLocale();
  }

  private void addLocalDefaultLocale() {
    //TODO: Make sure we do not "guess" incorrectly the user's language.
    Language userLanguage = getLanguage(System.getProperty("user.language"));
    if (userLanguage != null) {
      Language localDefaultLanguage = new Language(userLanguage, true);
      supportedLanguages.remove(localDefaultLanguage);
      supportedLanguages.add(0, localDefaultLanguage);
    }
  }

  @VisibleForTesting
  public LanguageDimension(CloudConfigurationImpl googleCloudTestingConfiguration, final List<String> locales) {
    super(googleCloudTestingConfiguration);
    supportedLanguages = Lists.newArrayList(Iterables.filter(getFullDomain(), new Predicate<Language>() {
      @Override
      public boolean apply(LanguageDimension.Language input) {
        return locales.contains(input.getId());
      }
    }));
    addLocalDefaultLocale();
  }

  private List<String> getLocales(AndroidFacet facet) {
    List<String> locales = new LinkedList<String>();
    Pattern pattern = Pattern.compile("\\Avalues-([a-z]+)(-r[a-zA-Z0-9]+)?(-[a-zA-Z0-9]+)?\\z");
    for (VirtualFile resourceDirectory : facet.getAllResourceDirectories()) {
      for (VirtualFile subfolder : resourceDirectory.getChildren()) {
        String subfolderName = subfolder.getName();
        Matcher matcher = pattern.matcher(subfolderName);
        if (matcher.find()) {
          StringBuffer sb = new StringBuffer();
          sb.append(matcher.group(1));
          String group2 = matcher.group(2);
          if (group2 != null) {
            appendLocaleGroup(sb, group2, 2);
          }
          String group3 = matcher.group(3);
          if (group3 != null) {
            appendLocaleGroup(sb, group3, 1);
          }
          locales.add(sb.toString());
        }
      }
    }
    return locales;
  }

  private void appendLocaleGroup(StringBuffer sb, String groupText, int startIndex) {
    sb.append("_" + groupText.substring(startIndex));
  }

  @Override
  public List<? extends CloudTestingType> getAppSupportedDomain() {
    return supportedLanguages;
  }

  public static List<Language> getFullDomain() {
    if (isFullDomainMissing() || shouldPollDiscoveryTestApi(DISPLAY_NAME)) {
      ImmutableList.Builder<Language> fullDomainBuilder = new ImmutableList.Builder<Language>();
      AndroidDeviceCatalog androidDeviceCatalog = CloudAuthenticator.getInstance().getAndroidDeviceCatalog();
      if (androidDeviceCatalog != null) {
        for (Locale locale : androidDeviceCatalog.getRuntimeConfiguration().getLocales()) {
          Language language = new Language(locale.getId(), locale.getName(), locale.getRegion(), false);
          fullDomainBuilder.add(language);
          List<String> tags = locale.getTags();
          if (tags != null && tags.contains("default")) {
            defaultLanguage = language;
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

  public static Language getDefaultLanguage() {
    if (defaultLanguage == null) {
      getFullDomain();
    }
    return defaultLanguage;
  }

  public void enableDefault() {
    if (getDefaultLanguage() == null) {
      return;
    }
    List<? extends CloudTestingType> appSupportedDomain = getAppSupportedDomain();
    if (appSupportedDomain.contains(defaultLanguage)) {
      enable(defaultLanguage);
    } else if (!appSupportedDomain.isEmpty()) {
      enable(appSupportedDomain.get(0));
    }
  }

  public static Language getLanguage(final String locale) {
    try {
      return Iterables.find(getFullDomain(), new Predicate<Language>() {
        @Override
        public boolean apply(LanguageDimension.Language input) {
          return input.getId().equals(locale);
        }
      });
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getId() {
    return "LOCALE";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Configs.Language;
  }

  public static class Language extends CloudTestingType {

    private final String id;
    private final String name;
    private final String region;
    private final boolean isLocalDefault;

    public Language(Language language, boolean isLocalDefault) {
      this(language.id, language.name, language.region, isLocalDefault);
    }

    //public Language(String id, String name, String region) {
    //  this(id, name, region, false);
    //}

    public Language(String id, String name, String region, boolean isLocalDefault) {
      this.id = id;
      this.name = name;
      this.region = region;
      this.details = ImmutableMap.of();
      this.isLocalDefault = isLocalDefault;
    }

    @Override
    public String getGroupName() {
      int endIndex = name.indexOf("(");
      return endIndex != -1 ? name.substring(0, endIndex).trim() : name;
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return getResultsViewerDisplayName() + (isLocalDefault ? " - default" : "");
    }

    @Override
    public String getResultsViewerDisplayName() {
      return name + (region == null ? "" : " (" + region + ")");
    }

    @Override
    public String getId() {
      return id;
    }
  }
}
