/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.gct.testrecorder.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TestRecorderSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final TestRecorderSettings mySettings;

  private JPanel myPanel;
  private JSpinner myEvaluationDepthSpinner;
  private JSpinner myScrollDepthSpinner;
  private JSpinner myAssertionDepthSpinner;
  private JCheckBox myCapEvaluationDepthCheckBox;
  private JCheckBox myCleanBeforeStartCheckbox;
  private JCheckBox myCleanAfterFinishCheckbox;
  private JCheckBox myStopAppCheckbox;
  private JPanel myLaunchAndTearDownSettingsPanel;
  private JPanel myDataCollecationAndCodeGenerationSettingsPanel;
  private JCheckBox myUseTextForElementMatchingCheckBox;

  public TestRecorderSettingsConfigurable() {
    mySettings = TestRecorderSettings.getInstance();
  }

  @Override
  @NotNull
  public String getId() {
    return "test.recorder";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Espresso Test Recorder";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    myDataCollecationAndCodeGenerationSettingsPanel.setBorder(
      IdeBorderFactory.createTitledBorder("Data collection and code generation settings", false, new Insets(7, 0, 0, 0)));

    myLaunchAndTearDownSettingsPanel.setBorder(
      IdeBorderFactory.createTitledBorder("Launch and tear down settings", false, new Insets(7, 0, 0, 0)));

    myCleanAfterFinishCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDependentCheckboxes();
      }
    });

    return myPanel;
  }

  private void updateDependentCheckboxes() {
    if (myCleanAfterFinishCheckbox.isSelected()) {
      myStopAppCheckbox.setSelected(true);
      myStopAppCheckbox.setEnabled(false);
    } else {
      myStopAppCheckbox.setEnabled(true);
    }
  }

  @Override
  public boolean isModified() {
    Integer evaluationDepth = getSpinnerValue(myEvaluationDepthSpinner);
    Integer scrollDepth = getSpinnerValue(myScrollDepthSpinner);
    Integer assertionDepth = getSpinnerValue(myAssertionDepthSpinner);
    return evaluationDepth != null && mySettings.EVALUATION_DEPTH != evaluationDepth
           || scrollDepth != null && mySettings.SCROLL_DEPTH != scrollDepth
           || assertionDepth != null && mySettings.ASSERTION_DEPTH != assertionDepth
           || mySettings.CAP_AT_NON_IDENTIFIABLE_ELEMENTS != myCapEvaluationDepthCheckBox.isSelected()
           || mySettings.USE_TEXT_FOR_ELEMENT_MATCHING != myUseTextForElementMatchingCheckBox.isSelected()
           || mySettings.CLEAN_BEFORE_START != myCleanBeforeStartCheckbox.isSelected()
           || mySettings.CLEAN_AFTER_FINISH != myCleanAfterFinishCheckbox.isSelected()
           || mySettings.STOP_APP_AFTER_RECORDING != myStopAppCheckbox.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    Integer evaluationDepth = getSpinnerValue(myEvaluationDepthSpinner);
    if (evaluationDepth != null) {
      mySettings.EVALUATION_DEPTH = evaluationDepth;
    }

    Integer scrollDepth = getSpinnerValue(myScrollDepthSpinner);
    if (scrollDepth != null) {
      mySettings.SCROLL_DEPTH = scrollDepth;
    }

    Integer assertionDepth = getSpinnerValue(myAssertionDepthSpinner);
    if (assertionDepth != null) {
      mySettings.ASSERTION_DEPTH = assertionDepth;
    }

    mySettings.CAP_AT_NON_IDENTIFIABLE_ELEMENTS = myCapEvaluationDepthCheckBox.isSelected();
    mySettings.USE_TEXT_FOR_ELEMENT_MATCHING = myUseTextForElementMatchingCheckBox.isSelected();
    mySettings.CLEAN_BEFORE_START = myCleanBeforeStartCheckbox.isSelected();
    mySettings.CLEAN_AFTER_FINISH = myCleanAfterFinishCheckbox.isSelected();
    mySettings.STOP_APP_AFTER_RECORDING = myStopAppCheckbox.isSelected();
  }

  @Override
  public void reset() {
    myEvaluationDepthSpinner.setValue(mySettings.EVALUATION_DEPTH);
    myScrollDepthSpinner.setValue(mySettings.SCROLL_DEPTH);
    myAssertionDepthSpinner.setValue(mySettings.ASSERTION_DEPTH);
    myCapEvaluationDepthCheckBox.setSelected(mySettings.CAP_AT_NON_IDENTIFIABLE_ELEMENTS);
    myUseTextForElementMatchingCheckBox.setSelected(mySettings.USE_TEXT_FOR_ELEMENT_MATCHING);
    myCleanBeforeStartCheckbox.setSelected(mySettings.CLEAN_BEFORE_START);
    myCleanAfterFinishCheckbox.setSelected(mySettings.CLEAN_AFTER_FINISH);
    myStopAppCheckbox.setSelected(mySettings.STOP_APP_AFTER_RECORDING);
    updateDependentCheckboxes();
  }

  @Override
  public void disposeUIResources() {
  }

  private void createUIComponents() {
    TestRecorderSettings testRecorderSettings = TestRecorderSettings.getInstance();

    myEvaluationDepthSpinner = new JSpinner(new SpinnerNumberModel(testRecorderSettings.EVALUATION_DEPTH, 1, 20, 1));
    forceToAcceptNumbersOnly(myEvaluationDepthSpinner);
    myScrollDepthSpinner = new JSpinner(new SpinnerNumberModel(testRecorderSettings.SCROLL_DEPTH, 1, 20, 1));
    forceToAcceptNumbersOnly(myScrollDepthSpinner);
    myAssertionDepthSpinner = new JSpinner(new SpinnerNumberModel(testRecorderSettings.ASSERTION_DEPTH, 1, 20, 1));
    forceToAcceptNumbersOnly(myAssertionDepthSpinner);
  }

  @Nullable
  private Integer getSpinnerValue(JSpinner spinner) {
    Object value = spinner.getValue();
    return value instanceof Integer ? (Integer)value : null;
  }

  private void forceToAcceptNumbersOnly(JSpinner spinner) {
    JComponent editor = spinner.getEditor();
    if (editor instanceof JSpinner.NumberEditor) {
      JFormattedTextField.AbstractFormatter formatter = ((JSpinner.NumberEditor)editor).getTextField().getFormatter();
      if (formatter instanceof NumberFormatter) {
        ((NumberFormatter)formatter).setAllowsInvalid(false);
      }
    }
  }
}
