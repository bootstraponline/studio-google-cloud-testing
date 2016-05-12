/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.gct.testrecorder.util;

import com.android.utils.Pair;
import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringUtil {

  /**
   * trim (double) quotation marks in the ends of the given string
   * "\"txt\"" => "txt"
   * @param org
   * @return
   */
  public static String trimQuotations(String org) {
    if (org.length() > 2 && org.charAt(0) == '"' && org.charAt(org.length()-1) == '"')
      return org.substring(1, org.length()-1);
    else
      return org;
  }


  /**
   * boxing string constant, including escape characters
   * "txt\n" => "\"txt\\n\""
   * @param str
   * @return
   */
  public static String boxString(String str) {
    return "\"" + StringEscapeUtils.escapeJava(str) + "\"";
  }

  public static String boxStringIfNotEmpty(String str) {
    if (str.isEmpty()) {
      return "";
    }
    return boxString(str);
  }

  public static String lowerCaseFirstCharacter(String originalString) {
    if (originalString.isEmpty()) {
      return originalString;
    }
    return originalString.substring(0, 1).toLowerCase() + (originalString.length() > 1 ? originalString.substring(1) : "");
  }

  @NotNull
  public static String getClassName(@NotNull String qualifiedClassName) {
    return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1);
  }

  /**
   * Returns a pair of (package_name, element_id).
   */
  @Nullable
  public static Pair<String, String> parseId(@NotNull String resourceId) {
    final String idMarker = ":id/";
    final int idMarkerIndex = resourceId.indexOf(idMarker);

    if (idMarkerIndex == -1) {
      // Unexpected resource id format.
      return null;
    }

    return Pair.of(resourceId.substring(0, idMarkerIndex), resourceId.substring(idMarkerIndex + idMarker.length()));
  }
}
