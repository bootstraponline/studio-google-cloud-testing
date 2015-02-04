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

import com.google.common.base.Optional;

class BucketFileMetadata {
  private String path;
  private Optional<String> encodedConfigurationInstance;
  private String name;
  private Type type;

  public enum Type {
    UNKNOWN,
    DONE,
    PROGRESS,
    RESULT,
    SCREENSHOT,
    APK,
    FOLDER
  }

  public BucketFileMetadata(String filePath) {
    path = filePath;

    if (path.endsWith("/")) {
      type = Type.FOLDER;
      name = path.substring(0, path.length() - 1);
    } else if (path.contains("/")) {
      String[] fileNameParts = path.split("/");
      encodedConfigurationInstance = Optional.of(fileNameParts[0]);
      name = fileNameParts[1];
    } else {
      encodedConfigurationInstance = Optional.absent();
      name = path;
    }

    if (path.endsWith("/DONE")) {
      type = Type.DONE;
    } else if (path.endsWith("/PROGRESS")) {
      type = Type.PROGRESS;
    } else if (path.endsWith(".results")) {
      type = Type.RESULT;
    } else if (path.endsWith(".png") || path.endsWith(".bmp") || path.endsWith(".jpg")) {
      type = Type.SCREENSHOT;
    } else if (path.endsWith(".apk")) {
      type = Type.APK;
    } else {
      type = Type.UNKNOWN;
    }
  }

  public String getPath() {
    return path;
  }

  public boolean hasEncodedConfigurationInstance() {
    return encodedConfigurationInstance.isPresent();
  }

  public String getEncodedConfigurationInstance() {
    return encodedConfigurationInstance.get();
  }

  public String getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    return "BucketFileMetadata{" +
           "path='" + path + '\'' +
           ", configuration=" + encodedConfigurationInstance +
           ", name='" + name + '\'' +
           ", type=" + type +
           '}';
  }

}
