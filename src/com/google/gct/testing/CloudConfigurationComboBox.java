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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class CloudConfigurationComboBox extends ComboboxWithBrowseButton {

  private static GoogleCloudTestingConfiguration chosenConfiguration;

  private ImmutableList<GoogleCloudTestingConfiguration> defaultConfigurations;
  private List<GoogleCloudTestingConfiguration> customConfigurations;


  public CloudConfigurationComboBox() {
    setMinimumSize(new Dimension(100, getMinimumSize().height));
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSelectionAppearance();
      }
    });
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

  public void setFacet(final AndroidFacet facet) {
    defaultConfigurations = GoogleCloudTestingConfigurationFactory.getDefaultConfigurationsFromStorage(facet);
    customConfigurations = GoogleCloudTestingConfigurationFactory.getCustomConfigurationsFromStorage(facet);

    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<GoogleCloudTestingConfiguration> copyCustomConfigurations =
          Lists.newArrayList(Iterables.transform(customConfigurations, GoogleCloudTestingConfigurationFactory.CLONE_CONFIGURATIONS));

        GoogleCloudTestingConfiguration selectedConfiguration = getComboBox().getSelectedItem() == null
                                                   ? new GoogleCloudTestingConfiguration(facet)
                                                   : (GoogleCloudTestingConfiguration) getComboBox().getSelectedItem();

        CloudConfigurationChooserDialog dialog =
          new CloudConfigurationChooserDialog(facet.getModule(), copyCustomConfigurations, defaultConfigurations, selectedConfiguration);

        dialog.show();
        if (dialog.isOK()) {
          customConfigurations = copyCustomConfigurations;

          //Persist the edited configurations.
          GoogleCloudTestingPersistentState customState = new GoogleCloudTestingPersistentState();
          customState.myGoogleCloudTestingPersistentConfigurations =
            Lists.newArrayList(Iterables.transform(customConfigurations, new Function<GoogleCloudTestingConfiguration, GoogleCloudTestingPersistentConfiguration>() {
              @Override
              public GoogleCloudTestingPersistentConfiguration apply(GoogleCloudTestingConfiguration configuration) {
                return configuration.getPersistentConfiguration();
              }
            }));
          GoogleCloudTestingCustomPersistentConfigurations.getInstance(facet.getModule().getProject()).loadState(customState);

          // Update list in case new configs were added or removed
          DefaultComboBoxModel model = (DefaultComboBoxModel) getComboBox().getModel();
          model.removeAllElements();
          for (GoogleCloudTestingConfiguration configuration : customConfigurations) {
            model.addElement(configuration);
          }
          for (GoogleCloudTestingConfiguration configuration : defaultConfigurations) {
            model.addElement(configuration);
          }

          GoogleCloudTestingConfiguration newChosenConfiguration = dialog.getSelectedConfiguration();
          if (newChosenConfiguration != null) {
            getComboBox().setSelectedItem(newChosenConfiguration);
          }

          getComboBox().updateUI();
        }
      }
    });

    populate(facet.getModule());
  }

  @Override
  public void dispose() {
    Object selectedItem = getComboBox().getSelectedItem();
    if (selectedItem != null) {
      chosenConfiguration = (GoogleCloudTestingConfiguration) selectedItem;
    }
    super.dispose();
  }

  public void selectConfiguration(int hashCode) {
    if (customConfigurations != null) {
      for (GoogleCloudTestingConfiguration configuration : customConfigurations) {
        if (configuration.getHash() == hashCode) {
          getComboBox().setSelectedItem(configuration);
          return;
        }
      }
    }
    if (defaultConfigurations != null) {
      for (GoogleCloudTestingConfiguration configuration : defaultConfigurations) {
        if (configuration.getHash() == hashCode) {
          getComboBox().setSelectedItem(configuration);
          return;
        }
      }
    }
  }

  private void populate(final Module module) {
    if (module == null || module.isDisposed()) {
      return;
    }

    getComboBox().setModel(new DefaultComboBoxModel(Iterables.toArray(Iterables.concat(customConfigurations, defaultConfigurations),
                                                                      GoogleCloudTestingConfiguration.class)));

    if (chosenConfiguration != null) {
      getComboBox().setSelectedItem(chosenConfiguration);
    }

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

    updateSelectionAppearance();
  }
}
