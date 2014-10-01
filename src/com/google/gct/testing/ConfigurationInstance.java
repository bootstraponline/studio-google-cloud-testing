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
import com.google.gct.testing.dimension.GoogleCloudTestingDimension;
import com.google.gct.testing.dimension.GoogleCloudTestingType;

import java.util.*;
import java.util.regex.Pattern;

public class ConfigurationInstance {

  public static final Function<GoogleCloudTestingType,String> GET_DISPLAY_NAME = new Function<GoogleCloudTestingType, String>() {
    @Override
    public String apply(GoogleCloudTestingType input) {
      return input.getConfigurationDialogDisplayName();
    }
  };
  public static final Function<GoogleCloudTestingType,String> GET_ENCODED_NAME = new Function<GoogleCloudTestingType, String>() {
    @Override
    public String apply(GoogleCloudTestingType input) {
      return input.getId();
    }
  };
  public static final String DISPLAY_NAME_DELIMITER = " | ";
  public static final String ENCODED_NAME_DELIMITER = "-";

  private final Map<String, GoogleCloudTestingType> typesByDimensionName = new LinkedHashMap<String, GoogleCloudTestingType>();

  private ConfigurationInstance() {
  }

  public static ConfigurationInstance parseFromEncodedString(String configurationInstance) {
    return parse(GET_ENCODED_NAME, ENCODED_NAME_DELIMITER, configurationInstance);
  }

  public static ConfigurationInstance parseFromDisplayString(String configurationInstance) {
    return parse(GET_DISPLAY_NAME, DISPLAY_NAME_DELIMITER, configurationInstance);
  }

  private static ConfigurationInstance parse(Function<GoogleCloudTestingType, String> typeToNameFunction, String delimiter, String input) {
    ConfigurationInstance result= new ConfigurationInstance();
    ArrayList<GoogleCloudTestingType> allTypes = Lists.newArrayList(Iterables.concat(
      GoogleCloudTestingConfigurationFactory.getAllDimensionTypes().values()));

    ImmutableMap<String,GoogleCloudTestingType> nameToTypeMap = Maps.uniqueIndex(allTypes, typeToNameFunction);

    for (String name : input.split(Pattern.quote(delimiter))) {
      recordType(result, nameToTypeMap.get(name));
    }
    return result;
  }

  private static void recordType(ConfigurationInstance result, GoogleCloudTestingType type) {
    String dimensionName = null;
    for (Map.Entry<String, List<? extends GoogleCloudTestingType>> entry : GoogleCloudTestingConfigurationFactory.getAllDimensionTypes().entrySet()) {
      if (entry.getValue().contains(type)) {
        dimensionName = entry.getKey();
        break;
      }
    }
    if (dimensionName == null) {
      throw new NoSuchElementException("Could not find the corresponding dimension for type: " + type.getConfigurationDialogDisplayName());
    }
    result.typesByDimensionName.put(dimensionName, type);
  }

  public GoogleCloudTestingType getTypeForDimension(GoogleCloudTestingDimension dimension) {
    return typesByDimensionName.get(dimension.getDisplayName());
  }

  public ConfigurationInstance(List<GoogleCloudTestingType> types) {
    for (GoogleCloudTestingType type : types) {
      recordType(this, type);
    }
  }

  public String getEncodedString() {
    return getRepresentationString(GET_ENCODED_NAME, ENCODED_NAME_DELIMITER);
  }

  public String getDisplayString() {
    return getRepresentationString(GET_DISPLAY_NAME, DISPLAY_NAME_DELIMITER);
  }

  private String getRepresentationString(Function<GoogleCloudTestingType,String> typeToNameFunction, String delimiter) {
    StringBuffer sb = new StringBuffer();
    for (GoogleCloudTestingType type : typesByDimensionName.values()) {
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