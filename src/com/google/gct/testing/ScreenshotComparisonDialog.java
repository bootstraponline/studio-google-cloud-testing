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
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.gct.testing.ui.AddCompareScreenshotPanel;
import com.google.gct.testing.ui.AddScreenshotListener;
import com.google.gct.testing.ui.WipePanel;
import com.google.gct.testing.ui.WipePanelCallback;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class ScreenshotComparisonDialog {

  private final Set<ScreenshotComparisonHeaderPanelListener> headerListeners = new HashSet<ScreenshotComparisonHeaderPanelListener>();

  private final Project myProject;
  private final AbstractTestProxy testTreeRoot;
  private final CloudConfigurationImpl configuration;
  private final ConfigurationInstance configurationInstance;
  @Nullable private final ConfigurationInstance anotherConfigurationInstance;
  private final List<TestName> allTests;
  private TestName currentTest;
  private final Map<String, ConfigurationResult> results;
  private int step = 1;
  private int maxStep = 1;
  private final List<ScreenshotComparisonPanel> screenshotPanels = new LinkedList<ScreenshotComparisonPanel>();
  private final Map<TestName, Integer> testToStep = new HashMap<TestName, Integer>();

  private JPanel myPanel;
  private JPanel myHeaderPanel;
  private JPanel myAllScreenshotsPanel;
  private JPanel myLeftHeaderPanel;
  private JPanel myRightHeaderPanel;
  private JPanel myScreenshotNamePanel;
  private JLabel myScreenshotNameLabel;
  private Window myWindow;
  private JComboBox myTestComboBox;
  private JLabel myStepLabel;
  private AddCompareScreenshotPanel addScreenshotPanel;
  private ActionButton myDecrementStepButton;
  private ActionButton myIncrementStepButton;
  private Function<ConfigurationResult,String> getName;


  public ScreenshotComparisonDialog(Project project,
                                    AbstractTestProxy testTreeRoot,
                                    CloudConfigurationImpl configuration,
                                    ConfigurationInstance configurationInstance,
                                    @Nullable ConfigurationInstance anotherConfigurationInstance,
                                    List<TestName> allTests,
                                    TestName currentTest,
                                    Map<String, ConfigurationResult> results) {
    myProject = project;
    this.testTreeRoot = testTreeRoot;
    this.configuration = configuration;
    this.configurationInstance = configurationInstance;
    this.anotherConfigurationInstance = anotherConfigurationInstance;
    this.allTests = allTests;
    this.currentTest = currentTest;
    this.results = results;
    for (TestName test : allTests) {
      testToStep.put(test, 1);
    }
  }

  public void showDialog() {
    DialogBuilder builder = new DialogBuilder(myProject);
    builder.setCenterPanel(myPanel);
    builder.setPreferredFocusComponent(myLeftHeaderPanel);
    builder.setTitle("Screenshot Viewer");
    builder.removeAllActions();

    populateHeaderPanel();

    createFirstScreenshotComparisonPanel(configurationInstance);

    addScreenshotPanel = new AddCompareScreenshotPanel();
    //addScreenshotPanel.setHeight(UIUtil.isUnderDarcula() ? 585 : 570);
    addScreenshotPanel.addListener(new AddScreenshotListener() {
      @Override
      public void addScreenshot() {
        addScreenshotComparisonPanel(screenshotPanels.get(screenshotPanels.size() - 1));
      }
    });

    myAllScreenshotsPanel.add(addScreenshotPanel.getPanel());

    myTestComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TestName selectedTest = (TestName)myTestComboBox.getSelectedItem();
        if (!selectedTest.equals(currentTest)) {
          testToStep.put(currentTest, step);
          currentTest = selectedTest;
          step = testToStep.get(currentTest);
          notifyHeaderListenersAboutStep(false);
        }
        for (ScreenshotComparisonHeaderPanelListener headerListener : headerListeners) {
          headerListener.updateTest(selectedTest, true);
        }
        updateMaxStep();
      }
    });

    myTestComboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof TestName) {
          final TestName testName = (TestName)value;
          label.setText(testName.getDisplayName());
        }
        return label;
      }
    });

    myWindow = builder.getWindow();

    updateMaxStep();
    updateScreenshotName();
    builder.show();
  }

  public Window getWindow() {
    return myWindow;
  }

  private void createFirstScreenshotComparisonPanel(ConfigurationInstance configurationInstance) {
    ScreenshotComparisonPanel screenshotComparisonPanel =
      new ScreenshotComparisonPanel(this, null, testTreeRoot, configuration, configurationInstance,
                                    (TestName)myTestComboBox.getSelectedItem(), step, results);
    screenshotPanels.add(screenshotComparisonPanel);
    WipePanel wipePanel = screenshotComparisonPanel.getPanel();
    myAllScreenshotsPanel.add(wipePanel);
    wipePanel.instantReveal(new WipePanelCallback() {
      @Override
      public void panelRevealed() {
        fitWindow();
      }
    });
    headerListeners.add(screenshotComparisonPanel);
  }

  private void populateHeaderPanel() {
    //myLeftHeaderPanel.add(new JLabel("Test:"));
    myTestComboBox = new JComboBox();
    myTestComboBox.setModel(new DefaultComboBoxModel(new Vector(allTests)));
    myTestComboBox.setSelectedItem(currentTest);
    myLeftHeaderPanel.add(myTestComboBox);

    myScreenshotNameLabel = new JLabel("");
    myScreenshotNameLabel.setFont(myScreenshotNameLabel.getFont().deriveFont(Font.BOLD).deriveFont(18.0f));
    myScreenshotNamePanel.add(myScreenshotNameLabel);
    myScreenshotNamePanel.setBorder(new EmptyBorder(0, 15, 0, 0));

    AnAction decrementStepAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        step--;
        // This is not expected to happen, but just in case.
        if (step < 1) {
          step = 1;
        }
        updateStepLabel();
        notifyHeaderListenersAboutStep(true);
      }
      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setIcon(AllIcons.Actions.Back);
      }

    };
    PresentationFactory presentationFactory = new PresentationFactory();
    myDecrementStepButton =
      new ActionButton(decrementStepAction, presentationFactory.getPresentation(decrementStepAction), "MyPlace", new Dimension(25, 25));
    myRightHeaderPanel.add(myDecrementStepButton);

    myStepLabel = new JLabel();
    myRightHeaderPanel.add(myStepLabel);

    AnAction incrementStepAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        step++;
        // This is not expected to happen, but just in case.
        if (step > maxStep) {
          step = maxStep;
        }
        updateStepLabel();
        notifyHeaderListenersAboutStep(true);
      }
      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setIcon(AllIcons.Actions.Forward);
      }

    };
    myIncrementStepButton =
      new ActionButton(incrementStepAction, presentationFactory.getPresentation(incrementStepAction), "MyPlace", new Dimension(25, 25));

    myRightHeaderPanel.add(myIncrementStepButton);

    updateStepLabel();
  }

  private void notifyHeaderListenersAboutStep(boolean shouldUpdateImage) {
    updateScreenshotName();
    for (ScreenshotComparisonHeaderPanelListener headerListener : headerListeners) {
      headerListener.updateStep(step, shouldUpdateImage);
    }
  }

  private void updateScreenshotName() {
    myScreenshotNameLabel.setText("Screenshot name");
    for (ConfigurationResult result : results.values()) {
      String screenshotName = result.getScreenshotNameForTestAndStep(currentTest, step);
      if (!screenshotName.isEmpty()) {
        myScreenshotNameLabel.setText(screenshotName);
        break;
      }
    }
  }

  private void updateStepLabel() {
    myStepLabel.setText("Screenshot " + step + "/" + maxStep);

    myDecrementStepButton.setEnabled(step > 1);
    myIncrementStepButton.setEnabled(step < maxStep);
  }

  public void addScreenshotComparisonPanel(ScreenshotComparisonPanel createAfterPanel) {
    int index = 0;
    for (Component component : myAllScreenshotsPanel.getComponents()) {
      if (component == createAfterPanel.getPanel()) {
        ScreenshotComparisonPanel screenshotComparisonPanel =
          new ScreenshotComparisonPanel(this, createAfterPanel, testTreeRoot, configuration,
                                        createAfterPanel.computeSelectedConfigurationInstance(), (TestName)myTestComboBox.getSelectedItem(),
                                        step, results);
        screenshotPanels.add(index + 1, screenshotComparisonPanel);
        updateMaxStep();
        WipePanel newPanel = screenshotComparisonPanel.getPanel();
        myAllScreenshotsPanel.add(newPanel, index + 1);
        headerListeners.add(screenshotComparisonPanel);
        fitWindow();
        newPanel.reveal(null);
        return;
      }
      index++;
    }
    throw new IllegalArgumentException("Could not find panel to insert after: " + createAfterPanel);
  }

  public void fitWindow() {
    if (myWindow == null) {
      return;
    }
    myAllScreenshotsPanel.updateUI();
    myWindow.pack();
    int maxHeight = 0;
    for (ScreenshotComparisonPanel panel : screenshotPanels) {
      if (panel.getPanel().getHeight() > maxHeight) {
        maxHeight = panel.getPanel().getHeight();
      }
    }
    addScreenshotPanel.setHeight(maxHeight);
    myAllScreenshotsPanel.updateUI();
    myWindow.pack();
  }

  public void removeScreenshotComparisonPanel(final ScreenshotComparisonPanel removedPanel) {
    WipePanel panel = removedPanel.getPanel();
    panel.unReveal(new WipePanelCallback() {
      @Override
      public void panelHidden(WipePanel panel) {
        headerListeners.remove(removedPanel);
        screenshotPanels.remove(removedPanel);
        removedPanel.stopListeningToResults();

        myAllScreenshotsPanel.remove(panel);
        if (myAllScreenshotsPanel.getComponentCount() == 1) {
          myWindow.dispose();
        } else {
          updateMaxStep();
          fitWindow();
        }
      }
    });
  }

  void updateMaxStep() {
    maxStep = Ordering.natural().max(Iterables.transform(screenshotPanels, new Function<ScreenshotComparisonPanel, Integer>() {
      @Override
      public Integer apply(ScreenshotComparisonPanel panel) {
        return panel.getMaxStep();
      }
    }));
    int oldStep = step;
    step = Math.min(step, maxStep);
    // In case we got new screenshots.
    if (step == 0 && maxStep != 0) {
      step = 1;
    }
    updateStepLabel();
    if (step != oldStep) {
      notifyHeaderListenersAboutStep(true);
    }
  }
}
