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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

public class GsutilConfigurable extends SdkHomeConfigurable implements Configurable.NoScroll {


  public GsutilConfigurable(Project project) {
    super(project, "GSUtil");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.project.gsutil";
  }

  @Override
  protected boolean isSdkHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      VirtualFile gsutilFile = file.findFileByRelativePath("./gsutil");
      if (gsutilFile != null && !gsutilFile.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected GsutilSettings getFrameworkSettings() {
    return GsutilSettings.getInstance(myProject);
  }
}
