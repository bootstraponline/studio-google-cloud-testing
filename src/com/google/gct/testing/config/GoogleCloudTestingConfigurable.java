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
  private JCheckBox useFakeBucketCheckbox = new JCheckBox();
  private JTextField fakeBucketNameField = new JTextField();
  private JCheckBox useStagingJenkinsCheckbox = new JCheckBox();
  private JTextField stagingJenkinsUrlField = new JTextField();
  private JTextField stagingProjectNameField = new JTextField();
  private JTextField stagingProjectIdField = new JTextField();
  private JTextField prodJenkinsUrlField = new JTextField();
  private JTextField prodProjectNameField = new JTextField();
  private JTextField prodProjectIdField = new JTextField();

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

    content.add(new JLabel(" "), createGbc(0, 2));
    useStagingJenkinsCheckbox.setText("Use staging");
    content.add(useStagingJenkinsCheckbox, createGbc(0, 3));

    content.add(new JLabel("Staging:"), createGbc(0, 4, 3, 1.0));
    content.add(createLabelTextField("Jenkins URL:    ", stagingJenkinsUrlField), createGbc(1, 4));
    content.add(createLabelTextField("Project name: ", stagingProjectNameField), createGbc(1, 5));
    content.add(createLabelTextField("Project ID:       ", stagingProjectIdField), createGbc(1, 6));

    content.add(new JLabel("Production:"), createGbc(0, 7, 3, 1.0));
    content.add(createLabelTextField("Jenkins URL:    ", prodJenkinsUrlField), createGbc(1, 7));
    content.add(createLabelTextField("Project name: ", prodProjectNameField), createGbc(1, 8));
    content.add(createLabelTextField("Project ID:       ", prodProjectIdField), createGbc(1, 9));

    return panel;
  }

  private JPanel createLabelTextField(String label, JTextField textField) {
    JPanel stagingJenkinsUrlPanel = new JPanel(new BorderLayout());
    stagingJenkinsUrlPanel.add(new JLabel(label), BorderLayout.WEST);
    stagingJenkinsUrlPanel.add(textField, BorderLayout.CENTER);
    return stagingJenkinsUrlPanel;
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
    boolean stateShouldUseStagingJenkins = state == null ? false : state.shouldUseStagingJenkins;
    String stateStagingJenkinsUrl = state == null ? "" : state.stagingJenkinsUrl;
    String stateStagingProjectName = state == null ? "" : state.stagingProjectName;
    String stateStagingProjectId = state == null ? "" : state.stagingProjectId;
    String stateProdJenkinsUrl = state == null ? "" : state.prodJenkinsUrl;
    String stateProdProjectName = state == null ? "" : state.prodProjectName;
    String stateProdProjectId = state == null ? "" : state.prodProjectId;
    return !stateFakeBucketName.equals(fakeBucketNameField.getText())
           || stateShouldUseFakeBucket != useFakeBucketCheckbox.isSelected()
           || stateShouldUseStagingJenkins != useStagingJenkinsCheckbox.isSelected()
           || !stateStagingJenkinsUrl.equals(stagingJenkinsUrlField.getText())
           || !stateStagingProjectName.equals(stagingProjectNameField.getText())
           || !stateStagingProjectId.equals(stagingProjectIdField.getText())
           || !stateProdJenkinsUrl.equals(prodJenkinsUrlField.getText())
           || !stateProdProjectName.equals(prodProjectNameField.getText())
           || !stateProdProjectId.equals(prodProjectIdField.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    GoogleCloudTestingState state = new GoogleCloudTestingState();
    state.fakeBucketName = fakeBucketNameField.getText();
    state.shouldUseFakeBucket = useFakeBucketCheckbox.isSelected();
    state.shouldUseStagingJenkins = useStagingJenkinsCheckbox.isSelected();
    state.stagingJenkinsUrl = stagingJenkinsUrlField.getText();
    state.stagingProjectName = stagingProjectNameField.getText();
    state.stagingProjectId = stagingProjectIdField.getText();
    state.prodJenkinsUrl = prodJenkinsUrlField.getText();
    state.prodProjectName = prodProjectNameField.getText();
    state.prodProjectId = prodProjectIdField.getText();
    getSavedSettings().loadState(state);
  }

  @Override
  public void reset() {
    GoogleCloudTestingState state = getSavedSettings().getState();
    fakeBucketNameField.setText(state == null ? "" : state.fakeBucketName);
    useFakeBucketCheckbox.setSelected(state == null ? false : state.shouldUseFakeBucket);
    useStagingJenkinsCheckbox.setSelected(state == null ? false : state.shouldUseStagingJenkins);
    stagingJenkinsUrlField.setText(state == null ? "" : state.stagingJenkinsUrl);
    stagingProjectNameField.setText(state == null ? "" : state.stagingProjectName);
    stagingProjectIdField.setText(state == null ? "" : state.stagingProjectId);
    prodJenkinsUrlField.setText(state == null ? "" : state.prodJenkinsUrl);
    prodProjectNameField.setText(state == null ? "" : state.prodProjectName);
    prodProjectIdField.setText(state == null ? "" : state.prodProjectId);
  }

  private GoogleCloudTestingSettings getSavedSettings() {
    return GoogleCloudTestingSettings.getInstance(project);
  }

  @Override
  public void disposeUIResources() {
    panel = null;
    useFakeBucketCheckbox = null;
    fakeBucketNameField = null;
    useStagingJenkinsCheckbox = null;
    stagingJenkinsUrlField = null;
    stagingProjectIdField = null;
    stagingProjectNameField = null;
    prodJenkinsUrlField = null;
    prodProjectNameField = null;
    prodProjectIdField = null;
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
    public String fakeBucketName = "";
    public boolean shouldUseFakeBucket = false;
    public boolean shouldUseStagingJenkins = false;
    public String stagingJenkinsUrl = "";
    public String stagingProjectId = "";
    public String stagingProjectName = "";
    public String prodJenkinsUrl = "";
    public String prodProjectName = "";
    public String prodProjectId = "";
  }
}
