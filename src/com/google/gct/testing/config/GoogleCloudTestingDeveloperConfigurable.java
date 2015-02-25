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

import com.google.gct.testing.launcher.CloudAuthenticator;
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

import static com.google.gct.testing.config.GoogleCloudTestingDeveloperConfigurable.BackendOption.*;

public class GoogleCloudTestingDeveloperConfigurable implements OptionalConfigurable, SearchableConfigurable, Configurable.NoScroll {

  public final static String SHOW_GOOGLE_CLOUD_TESTING_SETTINGS = "show.google.cloud.testing.settings";

  enum BackendOption {PROD, STAGING, TEST, CUSTOM};

  private final Project project;

  private JPanel panel;
  private JCheckBox useFakeBucketCheckbox = new JCheckBox();
  private JTextField fakeBucketNameField = new JTextField();
  private JRadioButton useProd = new JRadioButton("Prod");
  //private JTextField prodUrlField = new JTextField("https://test-devtools.googleapis.com");
  private JTextField prodUrlField = new JTextField("https://testing.googleapis.com");
  private JRadioButton useStaging = new JRadioButton("Staging");
  //private JTextField stagingUrlField = new JTextField("https://staging-test-devtools.sandbox.googleapis.com");
  private JTextField stagingUrlField = new JTextField("https://staging-testing.sandbox.googleapis.com");
  private JRadioButton useTest = new JRadioButton("Test");
  //private JTextField testUrlField = new JTextField("https://test-test-devtools.sandbox.googleapis.com");
  private JTextField testUrlField = new JTextField("https://test-testing.sandbox.googleapis.com");
  private JRadioButton useCustom = new JRadioButton("Custom");
  private JTextField customUrlField = new JTextField("");

  public GoogleCloudTestingDeveloperConfigurable(Project project) {
    this.project = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Google Cloud Testing Developer";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "google.cloud.testing.developer";
  }

  @Override
  public JComponent createComponent() {
    panel = new JPanel(new BorderLayout(5, 10));
    JPanel content = new JPanel(new GridBagLayout());
    panel.add(content, BorderLayout.NORTH);
    useFakeBucketCheckbox.setText("Use fake bucket:");
    content.add(useFakeBucketCheckbox, createSettingsGbc(0, 1));
    content.add(fakeBucketNameField, createSettingsGbc(1, 1));

    ButtonGroup urlGroup = new ButtonGroup();
    urlGroup.add(useProd);
    urlGroup.add(useStaging);
    urlGroup.add(useTest);
    urlGroup.add(useCustom);

    prodUrlField.setEditable(false);
    stagingUrlField.setEditable(false);
    testUrlField.setEditable(false);

    content.add(new JLabel("Backend URL to use for test requests:"), createSettingsGbc(0, 2));
    content.add(useProd, createSettingsGbc(0, 3));
    content.add(prodUrlField, createSettingsGbc(1, 3));
    content.add(useStaging, createSettingsGbc(0, 4));
    content.add(stagingUrlField, createSettingsGbc(1, 4));
    content.add(useTest, createSettingsGbc(0, 5));
    content.add(testUrlField, createSettingsGbc(1, 5));
    content.add(useCustom, createSettingsGbc(0, 6));
    content.add(customUrlField, createSettingsGbc(1, 6));

    return panel;
  }

  private GridBagConstraints createGbc(int x, int y, int gridheight, double weighty) {
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

  private GridBagConstraints createSettingsGbc(int x, int y) {
    return createGbc(x, y, 1, 0.0);
  }

  @Override
  public boolean isModified() {
    GoogleCloudTestingDeveloperState state = getSavedSettings().getState();
    String stateFakeBucketName = state == null ? "" : state.fakeBucketName;
    boolean stateShouldUseFakeBucket = state == null ? false : state.shouldUseFakeBucket;
    int backendOption = state == null ? 0 : state.backendOption;
    String customUrl = state == null ? "" : state.customUrl;
    return !stateFakeBucketName.equals(fakeBucketNameField.getText())
           || stateShouldUseFakeBucket != useFakeBucketCheckbox.isSelected()
           || backendOption != getBackendOption().ordinal()
           || !customUrl.equals(customUrlField.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    GoogleCloudTestingDeveloperState state = new GoogleCloudTestingDeveloperState();
    state.fakeBucketName = fakeBucketNameField.getText();
    state.shouldUseFakeBucket = useFakeBucketCheckbox.isSelected();
    state.backendOption = getBackendOption().ordinal();
    state.backendUrl = getTestBackendUrl();
    state.customUrl = customUrlField.getText();
    getSavedSettings().loadState(state);
    CloudAuthenticator.recreateTestAndToolResults(getTestBackendUrl(), getToolResultsBackendUrl());
  }

  @Override
  public void reset() {
    GoogleCloudTestingDeveloperState state = getSavedSettings().getState();
    fakeBucketNameField.setText(state == null ? "" : state.fakeBucketName);
    useFakeBucketCheckbox.setSelected(state == null ? false : state.shouldUseFakeBucket);
    setBackendOption(BackendOption.values()[state == null ? 0 : state.backendOption]);
    customUrlField.setText(state == null ? "" : state.customUrl);
    CloudAuthenticator.recreateTestAndToolResults(getTestBackendUrl(), getToolResultsBackendUrl());
  }

  private BackendOption getBackendOption() {
    if (useProd.isSelected()) {
      return PROD;
    }
    if (useStaging.isSelected()) {
      return STAGING;
    }
    if (useTest.isSelected()) {
      return TEST;
    }
    if (useCustom.isSelected()) {
      return CUSTOM;
    }
    throw new RuntimeException("No URL option is selected!");
  }

  private String getTestBackendUrl() {
    switch (getBackendOption()) {
      case PROD:
        return prodUrlField.getText();
      case STAGING:
        return stagingUrlField.getText();
      case TEST:
        return testUrlField.getText();
      case CUSTOM:
        return customUrlField.getText();
      default:
        throw new RuntimeException("No URL option is selected!");
    }
  }

  private String getToolResultsBackendUrl() {
    switch (getBackendOption()) {
      case STAGING:
        return "https://www-googleapis-staging.sandbox.google.com/";
      case TEST:
        return "https://www-googleapis-test.sandbox.google.com/";
      default:
        // Use prod by default.
        return "https://www.googleapis.com/";
    }
  }

  private void setBackendOption(BackendOption backendOption) {
    switch(backendOption) {
      case PROD :
        useProd.setSelected(true);
        break;
      case STAGING:
        useStaging.setSelected(true);
        break;
      case TEST:
        useTest.setSelected(true);
        break;
      case CUSTOM:
        useCustom.setSelected(true);
        break;
      default:
        throw new RuntimeException("Unsupported backend option: " + backendOption);
    }
  }

  private GoogleCloudTestingDeveloperSettings getSavedSettings() {
    return GoogleCloudTestingDeveloperSettings.getInstance(project);
  }

  @Override
  public void disposeUIResources() {
    panel = null;
    useFakeBucketCheckbox = null;
    fakeBucketNameField = null;
    useProd = null;
    prodUrlField = null;
    useStaging = null;
    stagingUrlField = null;
    useTest = null;
    testUrlField = null;
    useCustom = null;
    customUrlField = null;
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

  public static class GoogleCloudTestingDeveloperState {
    public String fakeBucketName = "";
    public boolean shouldUseFakeBucket = false;
    public int backendOption = 0;
    public String backendUrl = "";
    public String customUrl = "";
  }
}
