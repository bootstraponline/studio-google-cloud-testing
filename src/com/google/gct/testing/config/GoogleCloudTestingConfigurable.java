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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GoogleCloudTestingConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private final Project project;

  private JPanel panel;
  private JCheckBox enableCloudTesting = new JCheckBox();


  public GoogleCloudTestingConfigurable(Project project) {
    this.project = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Google Cloud Testing";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "google.cloud.testing";
  }

  @Override
  public JComponent createComponent() {
    panel = new JPanel(new BorderLayout(5, 10));
    JPanel content = new JPanel(new BorderLayout());
    panel.add(content, BorderLayout.NORTH);
    enableCloudTesting.setText("Enable testing in Google Cloud");
    content.add(enableCloudTesting, BorderLayout.WEST);
    return panel;
  }

  @Override
  public boolean isModified() {
    GoogleCloudTestingState state = getSavedSettings().getState();
    boolean stateEnableCloudTesting = state == null ? false : state.enableCloudTesting;
    return stateEnableCloudTesting != enableCloudTesting.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    GoogleCloudTestingState state = new GoogleCloudTestingState();
    state.enableCloudTesting = enableCloudTesting.isSelected();
    getSavedSettings().loadState(state);
  }

  @Override
  public void reset() {
    GoogleCloudTestingState state = getSavedSettings().getState();
    enableCloudTesting.setSelected(state == null ? false : state.enableCloudTesting);
  }

  private GoogleCloudTestingSettings getSavedSettings() {
    return GoogleCloudTestingSettings.getInstance(project);
  }

  @Override
  public void disposeUIResources() {
    panel = null;
    enableCloudTesting = null;
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  public static class GoogleCloudTestingState {
    public boolean enableCloudTesting = false;
  }
}
