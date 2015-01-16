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

public class GoogleCloudTestingConfigurable implements OptionalConfigurable, SearchableConfigurable, Configurable.NoScroll {

  public final static String SHOW_GOOGLE_CLOUD_TESTING_SETTINGS = "show.google.cloud.testing.settings";

  private final Project project;

  private JPanel panel;
  private JCheckBox useFakeBucketCheckbox = new JCheckBox();
  private JTextField fakeBucketNameField = new JTextField();
  private JRadioButton useProd = new JRadioButton("Prod");
  private JTextField prodUrlField = new JTextField("https://test-devtools.googleapis.com");
  private JRadioButton useStaging = new JRadioButton("Staging");
  private JTextField stagingUrlField = new JTextField("https://www-googleapis-staging.sandbox.google.com/test");
  private JRadioButton useTest = new JRadioButton("Test");
  private JTextField testUrlField = new JTextField("https://www-googleapis-test.sandbox.google.com/test");
  private JRadioButton useCustom = new JRadioButton("Custom");
  private JTextField customUrlField = new JTextField("");

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

    ButtonGroup urlGroup = new ButtonGroup();
    urlGroup.add(useProd);
    urlGroup.add(useStaging);
    urlGroup.add(useTest);
    urlGroup.add(useCustom);

    prodUrlField.setEditable(false);
    stagingUrlField.setEditable(false);
    testUrlField.setEditable(false);

    content.add(new JLabel("Backend URL to use for test requests:"), createGbc(0, 2));
    content.add(useProd, createGbc(0, 3));
    content.add(prodUrlField, createGbc(1, 3));
    content.add(useStaging, createGbc(0, 4));
    content.add(stagingUrlField, createGbc(1, 4));
    content.add(useTest, createGbc(0, 5));
    content.add(testUrlField, createGbc(1, 5));
    content.add(useCustom, createGbc(0, 6));
    content.add(customUrlField, createGbc(1, 6));

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
    int urlChoice = state == null ? 0 : state.urlChoice;
    String customUrl = state == null ? "" : state.customUrl;
    return !stateFakeBucketName.equals(fakeBucketNameField.getText())
           || stateShouldUseFakeBucket != useFakeBucketCheckbox.isSelected()
           || urlChoice != getUrlChoice()
           || !customUrl.equals(customUrlField.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    GoogleCloudTestingState state = new GoogleCloudTestingState();
    state.fakeBucketName = fakeBucketNameField.getText();
    state.shouldUseFakeBucket = useFakeBucketCheckbox.isSelected();
    state.urlChoice = getUrlChoice();
    state.backendUrl = getBackendUrl();
    state.customUrl = customUrlField.getText();
    getSavedSettings().loadState(state);
    CloudAuthenticator.recreateTest(getBackendUrl());
  }

  @Override
  public void reset() {
    GoogleCloudTestingState state = getSavedSettings().getState();
    fakeBucketNameField.setText(state == null ? "" : state.fakeBucketName);
    useFakeBucketCheckbox.setSelected(state == null ? false : state.shouldUseFakeBucket);
    setUrlChoice(state == null ? 0 : state.urlChoice);
    customUrlField.setText(state == null ? "" : state.customUrl);
    CloudAuthenticator.recreateTest(getBackendUrl());
  }

  private int getUrlChoice() {
    if (useProd.isSelected()) {
      return 0;
    }
    if (useStaging.isSelected()) {
      return 1;
    }
    if (useTest.isSelected()) {
      return 2;
    }
    if (useCustom.isSelected()) {
      return 3;
    }
    throw new RuntimeException("No URL option is selected!");
  }

  private String getBackendUrl() {
    if (useProd.isSelected()) {
      return prodUrlField.getText();
    }
    if (useStaging.isSelected()) {
      return stagingUrlField.getText();
    }
    if (useTest.isSelected()) {
      return testUrlField.getText();
    }
    if (useCustom.isSelected()) {
      return customUrlField.getText();
    }
    throw new RuntimeException("No URL option is selected!");
  }

  private void setUrlChoice(int urlChoice) {
    switch(urlChoice) {
      case 0 :
        useProd.setSelected(true);
        break;
      case 1:
        useStaging.setSelected(true);
        break;
      case 2:
        useTest.setSelected(true);
        break;
      case 3:
        useCustom.setSelected(true);
        break;
      default:
        throw new RuntimeException("Unsupported URL choice: " + urlChoice);
    }
  }

  private GoogleCloudTestingSettings getSavedSettings() {
    return GoogleCloudTestingSettings.getInstance(project);
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

  public static class GoogleCloudTestingState {
    public String fakeBucketName = "";
    public boolean shouldUseFakeBucket = false;
    public int urlChoice = 0;
    public String backendUrl = "";
    public String customUrl = "";
  }
}
