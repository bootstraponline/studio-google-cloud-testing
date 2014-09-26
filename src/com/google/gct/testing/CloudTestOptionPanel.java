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


import com.android.tools.idea.run.AdditionalRunDebugChangeListener;
import com.android.tools.idea.run.AdditionalRunDebugOptionPanel;
import com.google.gct.testing.config.GoogleCloudTestingConfigurable;
import com.google.gct.testing.config.GoogleCloudTestingSettings;
import com.google.gct.testing.config.GoogleCloudTestingSettingsListener;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidRunConfigurationBase;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

import static com.google.gct.testing.GoogleCloudTestingUtils.createCloudTestOptionGbc;

public class CloudTestOptionPanel extends AdditionalRunDebugOptionPanel implements GoogleCloudTestingSettingsListener {

  public final static String SHOW_GOOGLE_CLOUD_TESTING_OPTION = "show.google.cloud.testing.option";
  public static final String DISPLAY_NAME = "Run tests in Google Cloud";
  public static final int MNEMONIC_INDEX = DISPLAY_NAME.indexOf('G');

  private final List<AdditionalRunDebugChangeListener> selectionChangeListeners = new LinkedList<AdditionalRunDebugChangeListener>();
  private final List<AdditionalRunDebugChangeListener> isShownChangeListeners = new LinkedList<AdditionalRunDebugChangeListener>();
  private AndroidRunConfigurationBase configuration;
  private Executor executor;

  private JLabel myCloudConfigurationLabel;
  private JLabel myCloudProjectLabel;
  private JLabel myCloudProjectNameLabel;
  private JPanel myCloudConfigurationWrapper;
  private JPanel myCloudProjectNameUpdatePanel;
  private ActionButton myCloudProjectNameUpdateButton;
  private CloudConfigurationComboBox myCloudConfigurationCombo;


  public CloudTestOptionPanel(final Project project, boolean isExtendedDeviceChooserDialog) {
    myCloudConfigurationLabel = new JLabel("Matrix configuration:");
    myCloudConfigurationLabel.setDisplayedMnemonic('M');
    myCloudProjectLabel = new JLabel("Google Cloud project:");
    myCloudProjectNameLabel = new JLabel("");
    myCloudConfigurationWrapper = new JPanel();
    myCloudConfigurationWrapper.setLayout(new BorderLayout());
    myCloudConfigurationCombo = new CloudConfigurationComboBox();

    myCloudConfigurationCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (AdditionalRunDebugChangeListener listener : selectionChangeListeners) {
          listener.changed();
        }
      }
    });

    myCloudConfigurationWrapper.add(myCloudConfigurationCombo, BorderLayout.CENTER);
    myCloudConfigurationLabel.setLabelFor(myCloudConfigurationCombo);

    GoogleCloudTestingSettings googleCloudTestingSettings = GoogleCloudTestingSettings.getInstance(project);
    //TODO: Should we unregister this listener after the dialog is disposed?
    // Looks harmless to keep it, but might revisit when implementing extension points.
    googleCloudTestingSettings.addChangeListener(this);
    updateCloudProjectName(googleCloudTestingSettings.getState());

    AnAction action = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Google Cloud Testing");
      }

      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setIcon(AllIcons.General.Settings);
      }
    };

    myCloudProjectNameUpdatePanel = new JPanel();
    myCloudProjectNameUpdatePanel.setLayout(new BorderLayout());
    JPanel innerProjectNameUpdatePanel = new JPanel();
    myCloudProjectNameUpdatePanel.add(innerProjectNameUpdatePanel, BorderLayout.WEST);
    myCloudProjectNameUpdateButton =
      new ActionButton(action, new PresentationFactory().getPresentation(action), "MyPlace", new Dimension(25, 25));
    innerProjectNameUpdatePanel.add(myCloudProjectNameLabel);
    innerProjectNameUpdatePanel.add(myCloudProjectNameUpdateButton);

    setLayout(new GridBagLayout());

    add(myCloudConfigurationLabel, createCloudTestOptionGbc(0, 0, isExtendedDeviceChooserDialog));
    add(myCloudConfigurationWrapper, createCloudTestOptionGbc(1, 0, isExtendedDeviceChooserDialog));

    add(myCloudProjectLabel, createCloudTestOptionGbc(0, 1, isExtendedDeviceChooserDialog));

    add(myCloudProjectNameUpdatePanel, createCloudTestOptionGbc(1, 1, isExtendedDeviceChooserDialog));

    updateGoogleCloudVisible(isShown());
  }

  private void updateCloudProjectName(GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState) {
    String cloudProjectName = googleCloudTestingState.shouldUseStagingJenkins ? googleCloudTestingState.stagingProjectName : googleCloudTestingState.prodProjectName;
    myCloudProjectNameLabel.setText(cloudProjectName);
  }

  @Override
  public void setFacet(AndroidFacet facet, boolean isEnabled) {
    if (facet == null) {
      updateEnabled(false);
    } else {
      updateEnabled(isEnabled);
      myCloudConfigurationCombo.setFacet(facet);
    }
  }

  /**
   * In the current implementation, the provided {@code id} is the hash code of the string representation of a configuration.
   */
  @Override
  public void selectConfigurationById(int id) {
    myCloudConfigurationCombo.selectConfiguration(id);
  }

  @Override
  public boolean isShown() {
    return configuration instanceof AndroidTestRunConfiguration
           && !(executor instanceof DefaultDebugExecutor)
           && Boolean.getBoolean(SHOW_GOOGLE_CLOUD_TESTING_OPTION);
  }

  @Override
  public void setConfiguration(AndroidRunConfigurationBase configuration) {
    this.configuration = configuration;
    updateGoogleCloudVisible(isShown());
  }

  @Override
  public void setExecutor(Executor executor) {
    this.executor = executor;
    updateGoogleCloudVisible(isShown());
  }

  /**
   * In the current implementation, the id of a configuration is the hash code of its string representation.
   */
  @Override
  public int getSelectedConfigurationId() {
    GoogleCloudTestingConfiguration selection = getSelection();
    if (selection == null) {
      return -1;
    }
    return selection.getHash();
  }

  @Override
  public void updateEnabled(boolean isEnabled) {
    myCloudConfigurationCombo.setEnabled(isEnabled);
    myCloudConfigurationLabel.setEnabled(isEnabled);
    myCloudProjectLabel.setEnabled(isEnabled);
    myCloudProjectNameLabel.setEnabled(isEnabled);
    myCloudProjectNameUpdateButton.setEnabled(isEnabled);
  }

  private GoogleCloudTestingConfiguration getSelection() {
    return (GoogleCloudTestingConfiguration) myCloudConfigurationCombo.getComboBox().getSelectedItem();
  }

  @Override
  public boolean isValidSelection() {
    GoogleCloudTestingConfiguration selection = getSelection();
    return selection != null && selection.countCombinations() > 0;
  }

  @Override
  public String getInvalidSelectionErrorMessage() {
    GoogleCloudTestingConfiguration selection = getSelection();
    if (selection == null) {
      return "Matrix configuration not specified";
    }
    if (selection.countCombinations() < 1) {
      return "Selected matrix configuration is empty";
    }
    return "";
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public int getMnemonicIndex() {
    return MNEMONIC_INDEX;
  }

  @Override
  public void addSelectionChangeListener(AdditionalRunDebugChangeListener listener) {
    selectionChangeListeners.add(listener);
  }

  @Override
  public void addIsShownChangeListener(AdditionalRunDebugChangeListener listener) {
    isShownChangeListeners.add(listener);
  }

  private void updateGoogleCloudVisible(boolean shouldShow) {
    myCloudConfigurationCombo.setVisible(shouldShow);
    myCloudConfigurationLabel.setVisible(shouldShow);
    myCloudProjectLabel.setVisible(shouldShow);
    myCloudProjectNameLabel.setVisible(shouldShow);
    myCloudProjectNameUpdateButton.setVisible(shouldShow);

    for (AdditionalRunDebugChangeListener isShownChangeListener : isShownChangeListeners) {
      isShownChangeListener.changed();
    }
  }

  @Override
  public void googleCloudTestingSettingsChanged(GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState) {
    updateCloudProjectName(googleCloudTestingState);
  }
}
