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
import com.google.api.services.test.model.AndroidDeviceCatalog;
import com.google.api.services.test.model.Locale;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gct.testing.GoogleCloudTestingConfigurationImpl;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.gct.testing.launcher.CloudAuthenticator.getAndroidDeviceCatalog;

public class LanguageDimension extends GoogleCloudTestingDimension {

  public static final String DISPLAY_NAME = "Locale";

  private static ImmutableList<Language> FULL_DOMAIN;

  private static final Language defaultLanguage;

  static {
    //TODO: Make sure we do not "guess" incorrectly the user's language.
    Language userLanguage = getLanguage(System.getProperty("user.language"));
    defaultLanguage = userLanguage != null ? new Language(userLanguage, true) : null;
  }

  private final List<Language> supportedLanguages;


  public LanguageDimension(GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration, AndroidFacet facet) {
    super(googleCloudTestingConfiguration);
    final List<String> locales = getLocales(facet);
    supportedLanguages = Lists.newArrayList(Iterables.filter(getFullDomain(), new Predicate<Language>() {
      @Override
      public boolean apply(LanguageDimension.Language input) {
        return locales.contains(input.getId());
      }
    }));
    if (defaultLanguage != null) {
      supportedLanguages.remove(defaultLanguage);
      supportedLanguages.add(0, defaultLanguage);
    }
  }

  @VisibleForTesting
  public LanguageDimension(GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration, final List<String> locales) {
    super(googleCloudTestingConfiguration);
    supportedLanguages = Lists.newArrayList(Iterables.filter(getFullDomain(), new Predicate<Language>() {
      @Override
      public boolean apply(LanguageDimension.Language input) {
        return locales.contains(input.getId());
      }
    }));
    if (defaultLanguage != null) {
      supportedLanguages.remove(defaultLanguage);
      supportedLanguages.add(0, defaultLanguage);
    }
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
  public List<? extends GoogleCloudTestingType> getAppSupportedDomain() {
    return supportedLanguages;
  }

  public static List<Language> getFullDomain() {
    if (FULL_DOMAIN == null || FULL_DOMAIN.isEmpty() || shouldPollDiscoveryTestApi(DISPLAY_NAME)) {
      ImmutableList.Builder<Language> fullDomainBuilder = new ImmutableList.Builder<Language>();
      AndroidDeviceCatalog androidDeviceCatalog = getAndroidDeviceCatalog();
      if (androidDeviceCatalog != null) {
        for (Locale locale : androidDeviceCatalog.getRuntimeConfiguration().getLocales()) {
          fullDomainBuilder.add(new Language(locale.getId(), locale.getName(), locale.getRegion(), false));
        }
      }
      FULL_DOMAIN = fullDomainBuilder.build();
      resetDiscoveryTestApiUpdateTimestamp(DISPLAY_NAME);
    }
    return FULL_DOMAIN;
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

  public static class Language extends GoogleCloudTestingType {

    private final String id;
    private final String name;
    private final String region;
    private final boolean isDefault;

    public Language(Language language, boolean isDefault) {
      this(language.id, language.name, language.region, isDefault);
    }

    //public Language(String id, String name, String region) {
    //  this(id, name, region, false);
    //}

    public Language(String id, String name, String region, boolean isDefault) {
      this.id = id;
      this.name = name;
      this.region = region;
      this.details = ImmutableMap.of();
      this.isDefault = isDefault;
    }

    @Override
    public String getGroupName() {
      int endIndex = name.indexOf("(");
      return endIndex != -1 ? name.substring(0, endIndex).trim() : name;
    }

    @Override
    public String getConfigurationDialogDisplayName() {
      return getResultsViewerDisplayName() + (isDefault ? " - default" : "");
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
