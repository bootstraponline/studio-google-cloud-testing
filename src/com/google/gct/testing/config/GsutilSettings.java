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
package com.google.gct.testing.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.util.SdkHomeSettings;

import static org.jetbrains.plugins.groovy.util.SdkHomeConfigurable.SdkHomeBean;

@State(
    name = "GsutilSettings",
    storages = {
      @Storage(file = StoragePathMacros.PROJECT_FILE),
      @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gsutil_config.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GsutilSettings extends SdkHomeSettings {
  public GsutilSettings(Project project) {
    super(project);
  }

  public static GsutilSettings getInstance(Project project) {
    return ServiceManager.getService(project, GsutilSettings.class);
  }

  public static String getGsutilExecutable(Project project) {
    SdkHomeBean state = getInstance(project).getState();
    return state == null || state.SDK_HOME.isEmpty() ? "" : state.SDK_HOME + "/gsutil";
  }
}
