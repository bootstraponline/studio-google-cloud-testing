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
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GoogleCloudTestingConfigurable implements OptionalConfigurable, SearchableConfigurable, Configurable.NoScroll {

  public final static String SHOW_GOOGLE_CLOUD_TESTING_SETTINGS = "show.google.cloud.testing.settings";

  private final Project project;

  private JPanel panel;
  private JCheckBox useFakeBucketCheckbox = new JCheckBox();
  private JTextField fakeBucketNameField = new JTextField();

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
    JPanel content = new JPanel(new GridBagLayout());
    panel.add(content, BorderLayout.NORTH);
    useFakeBucketCheckbox.setText("Use fake bucket:");
    content.add(useFakeBucketCheckbox, createGbc(0, 1));
    content.add(fakeBucketNameField, createGbc(1, 1));
    return panel;
  }

  private static GridBagConstraints createGbc(int x, int y, int gridheight, double weighty) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = x;
    gbc.gridy = y;
    gbc.insets = new Insets(2, 5, 2, 5);
    if (x == 0) {
      gbc.anchor = GridBagConstraints.WEST;
    } else {
      gbc.anchor = GridBagConstraints.WEST;
    }
    gbc.weightx = (x == 0) ? 0.0 : 1.0;
    gbc.weighty = weighty;
    gbc.gridheight = gridheight;
    return gbc;
  }

  private static GridBagConstraints createGbc(int x, int y) {
    return createGbc(x, y, 1, 0.0);
  }

  @Override
  public boolean isModified() {
    GoogleCloudTestingState state = getSavedSettings().getState();
    String stateFakeBucketName = state == null ? "" : state.fakeBucketName;
    boolean stateShouldUseFakeBucket = state == null ? false : state.shouldUseFakeBucket;
    return !stateFakeBucketName.equals(fakeBucketNameField.getText())
           || stateShouldUseFakeBucket != useFakeBucketCheckbox.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    GoogleCloudTestingState state = new GoogleCloudTestingState();
    state.fakeBucketName = fakeBucketNameField.getText();
    state.shouldUseFakeBucket = useFakeBucketCheckbox.isSelected();
    getSavedSettings().loadState(state);
  }

  @Override
  public void reset() {
    GoogleCloudTestingState state = getSavedSettings().getState();
    fakeBucketNameField.setText(state == null ? "" : state.fakeBucketName);
    useFakeBucketCheckbox.setSelected(state == null ? false : state.shouldUseFakeBucket);
  }

  private GoogleCloudTestingSettings getSavedSettings() {
    return GoogleCloudTestingSettings.getInstance(project);
  }

  @Override
  public void disposeUIResources() {
    panel = null;
    useFakeBucketCheckbox = null;
    fakeBucketNameField = null;
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

  @Override
  public boolean needDisplay() {
    return Boolean.getBoolean(SHOW_GOOGLE_CLOUD_TESTING_SETTINGS);
  }

  public static class GoogleCloudTestingState {
    public String fakeBucketName = "";
    public boolean shouldUseFakeBucket = false;
  }
}
