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
package com.google.gct.testing;

import com.google.gct.testing.dimension.ApiDimension;
import com.google.gct.testing.dimension.DeviceDimension;
import com.google.gct.testing.dimension.LanguageDimension;
import com.google.gct.testing.dimension.OrientationDimension;
import com.intellij.openapi.options.ConfigurationException;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class GoogleCloudTestingConfigurationTest {


  @Test
  public void testConfigurationExpansion() throws IOException, ConfigurationException {
    GoogleCloudTestingConfiguration
      configuration = new GoogleCloudTestingConfiguration("TestConfiguration", 8, asList("en", "de", "fr", "ro", "ru", "uk"));
    configuration.deviceDimension.enable(DeviceDimension.getFullDomain(), asList("Nexus5", "Nexus7"));
    configuration.apiDimension.enable(ApiDimension.getFullDomain(), asList("19", "18"));
    configuration.languageDimension.enable(LanguageDimension.getFullDomain(), asList("en", "de", "fr"));
    configuration.orientationDimension.enable(OrientationDimension.getFullDomain(), asList("portrait"));

    List<String> expectedConfigurationInstances = asList("Google Nexus 5 | Android 4.4.3 - KitKat (API 19) | German (de) | Portrait",
                                                         "Google Nexus 5 | Android 4.4.3 - KitKat (API 19) | English (en) | Portrait",
                                                         "Google Nexus 5 | Android 4.4.3 - KitKat (API 19) | French (fr) | Portrait",
                                                         "Google Nexus 5 | Android 4.3.1 - Jelly Bean (API 18) | German (de) | Portrait",
                                                         "Google Nexus 5 | Android 4.3.1 - Jelly Bean (API 18) | English (en) | Portrait",
                                                         "Google Nexus 5 | Android 4.3.1 - Jelly Bean (API 18) | French (fr) | Portrait",
                                                         "Google Nexus 7 | Android 4.4.3 - KitKat (API 19) | German (de) | Portrait",
                                                         "Google Nexus 7 | Android 4.4.3 - KitKat (API 19) | English (en) | Portrait",
                                                         "Google Nexus 7 | Android 4.4.3 - KitKat (API 19) | French (fr) | Portrait",
                                                         "Google Nexus 7 | Android 4.3.1 - Jelly Bean (API 18) | German (de) | Portrait",
                                                         "Google Nexus 7 | Android 4.3.1 - Jelly Bean (API 18) | English (en) | Portrait",
                                                         "Google Nexus 7 | Android 4.3.1 - Jelly Bean (API 18) | French (fr) | Portrait");

    assertEquals(expectedConfigurationInstances, configuration.computeConfigurationInstancesForResultsViewer());
  }
}
