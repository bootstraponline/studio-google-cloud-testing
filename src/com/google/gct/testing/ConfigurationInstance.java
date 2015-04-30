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

import com.google.api.client.util.Lists;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gct.testing.dimension.CloudConfigurationDimension;
import com.google.gct.testing.dimension.CloudTestingType;

import java.util.*;
import java.util.regex.Pattern;

public class ConfigurationInstance {

  //public static final Function<CloudTestingType,String> GET_CONFIGURATION_DIALOG_DISPLAY_NAME =
  //  new Function<CloudTestingType, String>() {
  //    @Override
  //    public String apply(CloudTestingType input) {
  //      return input.getConfigurationDialogDisplayName();
  //    }
  //  };
  public static final Function<CloudTestingType,String> GET_RESULTS_VIEWER_DISPLAY_NAME =
    new Function<CloudTestingType, String>() {
      @Override
      public String apply(CloudTestingType input) {
        return input.getResultsViewerDisplayName();
      }
    };
  public static final Function<CloudTestingType,String> GET_ENCODED_NAME = new Function<CloudTestingType, String>() {
    @Override
    public String apply(CloudTestingType input) {
      return input.getId();
    }
  };
  public static final String DISPLAY_NAME_DELIMITER = ", ";
  public static final String ENCODED_NAME_DELIMITER = "-";

  private final Map<String, CloudTestingType> typesByDimensionName = new LinkedHashMap<String, CloudTestingType>();

  private ConfigurationInstance() {
  }

  public static ConfigurationInstance parseFromEncodedString(String configurationInstance) {
    return parse(GET_ENCODED_NAME, ENCODED_NAME_DELIMITER, configurationInstance);
  }

  public static ConfigurationInstance parseFromResultsViewerDisplayString(String configurationInstance) {
    return parse(GET_RESULTS_VIEWER_DISPLAY_NAME, DISPLAY_NAME_DELIMITER, configurationInstance);
  }

  private static ConfigurationInstance parse(Function<CloudTestingType, String> typeToNameFunction, String delimiter, String input) {
    ConfigurationInstance result= new ConfigurationInstance();
    ArrayList<CloudTestingType> allTypes = Lists.newArrayList(Iterables.concat(
      CloudConfigurationProviderImpl.getAllDimensionTypes().values()));

    ImmutableMap<String,CloudTestingType> nameToTypeMap = Maps.uniqueIndex(allTypes, typeToNameFunction);

    for (String name : input.split(Pattern.quote(delimiter))) {
      recordType(result, nameToTypeMap.get(name));
    }
    return result;
  }

  private static void recordType(ConfigurationInstance result, CloudTestingType type) {
    String dimensionName = null;
    for (Map.Entry<String, List<? extends CloudTestingType>> entry : CloudConfigurationProviderImpl.getAllDimensionTypes().entrySet()) {
      if (entry.getValue().contains(type)) {
        dimensionName = entry.getKey();
        break;
      }
    }
    if (dimensionName == null) {
      throw new NoSuchElementException("Could not find the corresponding dimension for type: " + type.getResultsViewerDisplayName());
    }
    result.typesByDimensionName.put(dimensionName, type);
  }

  public CloudTestingType getTypeForDimension(CloudConfigurationDimension dimension) {
    return typesByDimensionName.get(dimension.getDisplayName());
  }

  public ConfigurationInstance(List<CloudTestingType> types) {
    for (CloudTestingType type : types) {
      recordType(this, type);
    }
  }

  public String getEncodedString() {
    return getRepresentationString(GET_ENCODED_NAME, ENCODED_NAME_DELIMITER);
  }

  //public String getConfigurationDialogDisplayString() {
  //  return getRepresentationString(GET_CONFIGURATION_DIALOG_DISPLAY_NAME, DISPLAY_NAME_DELIMITER);
  //}
  //
  public String getResultsViewerDisplayString() {
    return getRepresentationString(GET_RESULTS_VIEWER_DISPLAY_NAME, DISPLAY_NAME_DELIMITER);
  }

  private String getRepresentationString(Function<CloudTestingType,String> typeToNameFunction, String delimiter) {
    StringBuffer sb = new StringBuffer();
    for (CloudTestingType type : typesByDimensionName.values()) {
      sb.append(typeToNameFunction.apply(type) + delimiter);
    }
    sb.replace(sb.length() - delimiter.length(), sb.length(), ""); //Remove trailing delimiter.
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ConfigurationInstance that = (ConfigurationInstance) o;

    return typesByDimensionName.equals(that.typesByDimensionName);
  }

  @Override
  public int hashCode() {
    return typesByDimensionName.hashCode();
  }
}
