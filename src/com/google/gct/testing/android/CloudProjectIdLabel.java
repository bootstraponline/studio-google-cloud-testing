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
package com.google.gct.testing.android;

import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

public class CloudProjectIdLabel extends JBLabel {
  private static final String CLOUD_PROJECT_PROMPT = "Please select a project...";

  private final CloudConfiguration.Kind myConfigurationKind;
  private int myCurrentConfigurationId = -1;
  private Module myCurrentModule;

  // Used to keep track of user choices when run config and/or module are not available.
  private static Map<CloudConfiguration.Kind, String> myLastChosenProjectIdPerKind = Maps.newHashMapWithExpectedSize(5);

  /** A cache of project ids selected by <kind, module> per android run configuration, so that if
   * the configuration and/or module selections change back and forth, we retain the appropriate selected project id.
   */
  private static Map<Integer, Map<Pair<CloudConfiguration.Kind, Module>, String>> myProjectByConfigurationIdAndModuleCache =
    Maps.newHashMapWithExpectedSize(5);

  public CloudProjectIdLabel(@NotNull CloudConfiguration.Kind configurationKind) {
    myConfigurationKind = configurationKind;
    updateCloudProjectId(CLOUD_PROJECT_PROMPT);
  }

  @NotNull
  public String getProjectId() {
    return isProjectSpecified() ? getText() : "";
  }

  public boolean isProjectSpecified() {
    return !getText().isEmpty() && !getText().equals(CLOUD_PROJECT_PROMPT);
  }

  public void updateCloudProjectId(@NotNull String cloudProjectId) {
    if (cloudProjectId.isEmpty() || cloudProjectId.equals(CLOUD_PROJECT_PROMPT)) {
      Font currentFont = getFont();
      setFont(new Font("Dialog", Font.BOLD, currentFont.getSize()));
      setForeground(JBColor.RED);
      setText(CLOUD_PROJECT_PROMPT);
    } else {
      Font currentFont = getFont();
      setFont(new Font("Dialog", Font.PLAIN, currentFont.getSize()));
      setForeground(JBColor.BLACK);
      setText(cloudProjectId);
    }
    rememberChosenProjectId();
  }

  public void setFacet(@Nullable AndroidFacet facet) {
    if (facet == null) {
      return;
    }

    myCurrentModule = facet.getModule();
    restoreChosenProjectId();
  }

  public void setRunConfigurationId(int configurationId) {
    myCurrentConfigurationId = configurationId;
  }

  private void rememberChosenProjectId() {
    if (isProjectSpecified()) {
      myLastChosenProjectIdPerKind.put(myConfigurationKind, getText());
    }

    if (myCurrentConfigurationId == -1 || myCurrentModule == null) {
      return;
    }

    Map<Pair<CloudConfiguration.Kind, Module>, String> projectByModuleCache =
      myProjectByConfigurationIdAndModuleCache.get(myCurrentConfigurationId);
    if (projectByModuleCache == null) {
      projectByModuleCache = Maps.newHashMapWithExpectedSize(5);
      myProjectByConfigurationIdAndModuleCache.put(myCurrentConfigurationId, projectByModuleCache);
    }
    projectByModuleCache.put(Pair.create(myConfigurationKind, myCurrentModule), getText());
  }

  public void restoreChosenProjectId() {
    if (myCurrentConfigurationId == -1 || myCurrentModule == null) {
      String lastChosenProjectId = myLastChosenProjectIdPerKind.get(myConfigurationKind);
      if (lastChosenProjectId != null) {
        updateCloudProjectId(lastChosenProjectId);
      }
      return;
    }

    Map<Pair<CloudConfiguration.Kind, Module>, String> projectByModuleCache =
      myProjectByConfigurationIdAndModuleCache.get(myCurrentConfigurationId);
    if (projectByModuleCache != null) {
      String projectId = projectByModuleCache.get(Pair.create(myConfigurationKind, myCurrentModule));
      if (projectId != null) {
        updateCloudProjectId(projectId);
      }
    }
  }

}
