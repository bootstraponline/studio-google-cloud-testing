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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.JBColor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidRunConfigurationBase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class CloudConfigurationComboBox extends ComboboxWithBrowseButton {

  private static Set<CloudConfigurationComboBox> configurationBoxes = new HashSet<CloudConfigurationComboBox>();

  // test run configuration -> module -> matrix configuration ID
  private static Map<AndroidRunConfigurationBase, Map<Module, Integer>> chosenConfigurations =
    new HashMap<AndroidRunConfigurationBase, Map<Module, Integer>>();

  private AndroidRunConfigurationBase currentRunConfiguration;
  private Module currentModule;
  private AndroidFacet currentFacet;
  private ImmutableList<GoogleCloudTestingConfiguration> defaultConfigurations;
  private List<GoogleCloudTestingConfiguration> customConfigurations;
  private ActionListener actionListener;


  public CloudConfigurationComboBox() {
    setMinimumSize(new Dimension(100, getMinimumSize().height));
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        rememberChosenMatrixConfiguration();
        updateSelectionAppearance();
      }
    });

    getComboBox().setRenderer(new DefaultListCellRenderer() {

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof GoogleCloudTestingConfiguration) {
          final GoogleCloudTestingConfiguration config = (GoogleCloudTestingConfiguration)value;

          label.setText(config.getDisplayName());
          label.setIcon(config.getIcon());
          label.setIconTextGap(2);
          if (config.countCombinations() < 1) {
            label.setForeground(JBColor.RED);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
          } else {
            label.setForeground(JBColor.BLACK);
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
          }
        }

        return label;
      }
    });

    //TODO: Find a way to remove disposed comboboxes from this set to avoid unnecessary memory and CPU usage.
    configurationBoxes.add(this);
  }

  private void updateSelectionAppearance() {
    Object selectedItem = getComboBox().getSelectedItem();
    if (selectedItem != null) {
      if (((GoogleCloudTestingConfiguration) selectedItem).countCombinations() < 1) {
        getComboBox().setForeground(Color.RED);
        getComboBox().setFont(getComboBox().getFont().deriveFont(Font.BOLD));
      } else {
        getComboBox().setForeground(Color.BLACK);
        getComboBox().setFont(getComboBox().getFont().deriveFont(Font.PLAIN));
      }
    }
  }

  public void setConfiguration(AndroidRunConfigurationBase runConfiguration) {
    currentRunConfiguration = runConfiguration;
    rememberChosenMatrixConfiguration();
  }

  public void setFacet(AndroidFacet facet) {
    rememberChosenMatrixConfiguration();

    currentFacet = facet;
    currentModule = currentFacet.getModule();
    defaultConfigurations = GoogleCloudTestingConfigurationFactory.getDefaultConfigurationsFromStorage(currentFacet);
    customConfigurations = GoogleCloudTestingConfigurationFactory.getCustomConfigurationsFromStorage(currentFacet);

    // Since setFacet can be called multiple times, make sure to remove any previously registered listeners.
    removeActionListener(actionListener);

    actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<GoogleCloudTestingConfiguration> copyCustomConfigurations =
          Lists.newArrayList(Iterables.transform(customConfigurations, GoogleCloudTestingConfigurationFactory.CLONE_CONFIGURATIONS));

        GoogleCloudTestingConfiguration selectedConfiguration = getComboBox().getSelectedItem() == null
                                                                ? new GoogleCloudTestingConfiguration(currentFacet)
                                                                : (GoogleCloudTestingConfiguration)getComboBox().getSelectedItem();

        CloudConfigurationChooserDialog dialog =
          new CloudConfigurationChooserDialog(currentModule, copyCustomConfigurations, defaultConfigurations, selectedConfiguration);

        dialog.show();
        if (dialog.isOK()) {
          customConfigurations = copyCustomConfigurations;

          //Persist the edited configurations.
          GoogleCloudTestingPersistentState customState = new GoogleCloudTestingPersistentState();
          customState.myGoogleCloudTestingPersistentConfigurations = Lists.newArrayList(
            Iterables.transform(customConfigurations,
                                new Function<GoogleCloudTestingConfiguration, GoogleCloudTestingPersistentConfiguration>() {
                                  @Override
                                  public GoogleCloudTestingPersistentConfiguration apply(
                                    GoogleCloudTestingConfiguration configuration) {
                                    return configuration
                                      .getPersistentConfiguration();
                                  }
                                }));
          GoogleCloudTestingCustomPersistentConfigurations.getInstance(currentModule).loadState(customState);

          // Update list in case new configs were added or removed.
          getComboBox().setModel(new DefaultComboBoxModel(Iterables.toArray(Iterables.concat(customConfigurations, defaultConfigurations),
                                                                            GoogleCloudTestingConfiguration.class)));

          GoogleCloudTestingConfiguration newChosenConfiguration = dialog.getSelectedConfiguration();
          if (newChosenConfiguration != null) {
            getComboBox().setSelectedItem(newChosenConfiguration);
          }

          getComboBox().updateUI();

          updateLinkedComboboxes();
        }
      }
    };
    addActionListener(actionListener);

    updateContent();
  }

  private void updateLinkedComboboxes() {
    for (CloudConfigurationComboBox configurationBox : configurationBoxes) {
      if (!configurationBox.equals(this)
          && !configurationBox.currentRunConfiguration.equals(currentRunConfiguration)
          && configurationBox.currentModule.equals(currentModule)
          && configurationBox.currentFacet != null
          && !configurationBox.customConfigurations.equals(customConfigurations)) {
        configurationBox.customConfigurations = customConfigurations;
        configurationBox.updateContent();
      }
    }
  }

  private void rememberChosenMatrixConfiguration() {
    if (currentRunConfiguration != null && currentModule != null && getComboBox().getSelectedItem() != null) {
      Map<Module, Integer> configurationMap = chosenConfigurations.get(currentRunConfiguration);
      if (configurationMap == null) {
        configurationMap = new HashMap<Module, Integer>();
        chosenConfigurations.put(currentRunConfiguration, configurationMap);
      }
      configurationMap.put(currentModule, ((GoogleCloudTestingConfiguration)getComboBox().getSelectedItem()).getId());
    }
  }

  @Override
  public void dispose() {
    rememberChosenMatrixConfiguration();
    super.dispose();
  }

  public void selectConfiguration(int id) {
    if (customConfigurations != null) {
      for (GoogleCloudTestingConfiguration configuration : customConfigurations) {
        if (configuration.getId() == id) {
          getComboBox().setSelectedItem(configuration);
          return;
        }
      }
    }
    if (defaultConfigurations != null) {
      for (GoogleCloudTestingConfiguration configuration : defaultConfigurations) {
        if (configuration.getId() == id) {
          getComboBox().setSelectedItem(configuration);
          return;
        }
      }
    }
  }

  private void updateContent() {
    if (currentModule == null || currentModule.isDisposed()) {
      return;
    }

    getComboBox().setModel(new DefaultComboBoxModel(Iterables.toArray(Iterables.concat(customConfigurations, defaultConfigurations),
                                                                      GoogleCloudTestingConfiguration.class)));

    Map<Module, Integer> configurationMap = chosenConfigurations.get(currentRunConfiguration);
    if (configurationMap != null) {
      Integer configurationId = configurationMap.get(currentModule);
      if (configurationId != null) {
        selectConfiguration(configurationId);
      }
    }

    updateSelectionAppearance();
  }

}
