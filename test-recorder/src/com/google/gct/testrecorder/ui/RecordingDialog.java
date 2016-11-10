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
package com.google.gct.testrecorder.ui;

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.uiautomator.UiAutomatorModel;
import com.android.uiautomator.tree.BasicTreeNode;
import com.android.uiautomator.tree.UiNode;
import com.google.common.collect.ImmutableList;
import com.google.gct.testrecorder.codegen.TestCodeGenerator;
import com.google.gct.testrecorder.event.ElementDescriptor;
import com.google.gct.testrecorder.event.TestRecorderAssertion;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import com.google.gct.testrecorder.event.TestRecorderEventListener;
import com.google.gct.testrecorder.settings.TestRecorderSettings;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.ANDROID_TEST_COMPILE;
import static com.android.tools.idea.templates.SupportLibrary.*;
import static com.google.gct.testrecorder.event.TestRecorderAssertion.*;
import static com.google.gct.testrecorder.event.TestRecorderEvent.SUPPORTED_EVENTS;
import static com.google.gct.testrecorder.util.ImageHelper.rotateImage;
import static com.google.gct.testrecorder.util.UiAutomatorNodeHelper.*;

public class RecordingDialog extends DialogWrapper implements TestRecorderEventListener {
  private static final long ANIMATION_INTERVAL = 400; // milliseconds.
  private static final int ANIMATION_TIMER_INTERVAL = 10; // milliseconds.

  private static final String ESPRESSO_CORE_CUSTOM_ARTIFACT_NAME = "espresso";
  private static final String ESPRESSO_CORE_CUSTOM_GROUP_NAME = "com.jakewharton.espresso";

  public static final String TEST_INSTRUMENTATION_RUNNER = "android.support.test.runner.AndroidJUnitRunner";

  /**
   * Version of Espresso added to build.gradle files if we need to add it there. This needs to be kept in sync with the excludes lists
   * below.
   */
  public static final String ESPRESSO_VERSION = "2.2.2";

  public static final ImmutableList<ArtifactDependencySpec> ESPRESSO_EXCLUDES =
    ImmutableList.of(new ArtifactDependencySpec(SUPPORT_ANNOTATIONS.getArtifactId(), SUPPORT_ANNOTATIONS.getGroupId(), null));

  public static final ImmutableList<ArtifactDependencySpec> ESPRESSO_CONTRIB_EXCLUDES =
    ImmutableList.of(new ArtifactDependencySpec(SUPPORT_ANNOTATIONS.getArtifactId(), SUPPORT_ANNOTATIONS.getGroupId(), null),
                     new ArtifactDependencySpec(SUPPORT_V4.getArtifactId(), SUPPORT_V4.getGroupId(), null),
                     new ArtifactDependencySpec(DESIGN.getArtifactId(), DESIGN.getGroupId(), null),
                     new ArtifactDependencySpec(RECYCLERVIEW_V7.getArtifactId(), RECYCLERVIEW_V7.getGroupId(), null));

  private static final String RECORDING_DIALOG_TITLE = "Record Your Test";
  private static final String DEFAULT_MESSAGE = "Select an element from screenshot";

  private Project myProject;
  private AndroidFacet myFacet;
  private IDevice myDevice;
  private String myPackageName;
  private final GradleBuildModel myGradleBuildModel;
  private final AndroidGradleModel myAndroidGradleModel;

  private boolean myAssertionMode;
  private int myAssertionIndex;
  private LinkedHashMap<BasicTreeNode, Integer> myNodeIndentMap;
  private DefaultComboBoxModel myElementComboBoxModel;
  private DefaultListModel myEventListModel;

  private JPanel myRootPanel;
  private ScreenshotPanel myScreenshotPanel;
  private JPanel myEventListPanel;
  private JBScrollPane myScrollPane;
  private JBList myEventList;
  private JPanel myAssertionPanel;
  private JPanel myButtonsPanel;
  private JButton myAddAssertionButton;
  private JButton myTakeScreenshotButton;
  private JPanel myEditAssertionPanel;
  private JComboBox myAssertionElementComboBox;
  private JComboBox myAssertionRuleComboBox;
  private JPanel myTextFieldWrapper;
  private JTextField myAssertionTextField;
  private JButton mySaveAssertionButton;
  private JButton myCancelButton;
  private JButton mySaveAssertionAndAddAnotherButton;
  private JButton myCompleteRecordingButton;
  private JPanel myRecordingPanel;
  private JEditorPane myDisclaimerPane;

  public RecordingDialog(AndroidFacet facet, IDevice device, String packageName, String launchedActivityName) {
    super(facet.getModule().getProject());
    myProject = facet.getModule().getProject();
    myFacet = facet;
    myDevice = device;
    myPackageName = packageName;
    myAssertionMode = false;
    myGradleBuildModel = GradleBuildModel.get(myFacet.getModule());
    myAndroidGradleModel = AndroidGradleModel.get(myFacet);

    init();

    setTitle(RECORDING_DIALOG_TITLE);

    getRootPane().setDefaultButton(myCompleteRecordingButton);

    // TODO: Make it visible when we add the required functionality.
    myTakeScreenshotButton.setVisible(false);

    myEventList.setEmptyText("No events recorded yet.");
    myEventListModel = new DefaultListModel();
    myEventList.setModel(myEventListModel);
    myEventList.setCellRenderer(new TestRecorderListRenderer());

    myAddAssertionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        new TestRecorderScreenshotTask(myProject, myDevice, myPackageName, new ScreenshotCallback() {
          @Override
          public void onSuccess(BufferedImage initialImage, UiAutomatorModel model) {
            myAssertionMode = true;
            getRootPane().setDefaultButton(mySaveAssertionButton);
            BasicTreeNode root = model.getXmlRootNode();
            BufferedImage preparedImage = rotateImage(initialImage, getRotation(root));
            myScreenshotPanel.updateScreenShot(preparedImage, model);
            // Populate drop down menu
            myNodeIndentMap = createElementLevelMap(root);
            myElementComboBoxModel = new DefaultComboBoxModel(myNodeIndentMap.keySet().toArray());
            // Add a default element for drop down menu
            myElementComboBoxModel.insertElementAt(DEFAULT_MESSAGE, 0);
            // Show assertion panel
            CardLayout cardLayout = (CardLayout) myAssertionPanel.getLayout();
            cardLayout.show(myAssertionPanel, "myEditAssertionPanel");
            // Set up assertion panel
            setUpEmptyAssertionPanel();
            // Remember the index of to-be-added assertion.
            myAssertionIndex = myEventListModel.size();

            revealScreenshotPanel(preparedImage.getWidth(), preparedImage.getHeight());
          }
        }).queue();
      }
    });

    // TODO: take screenshot in Espresso test code
    myTakeScreenshotButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {

      }
    });

    mySaveAssertionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        exitAssertionMode(true);
        hideScreenshotPanel();
      }
    });

    mySaveAssertionAndAddAnotherButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        // Add the new assertion at its remembered index.
        myEventListModel.add(myAssertionIndex, buildAssertionForCurrentSelection());
        // Scroll event list so that assertion is visible
        myEventList.ensureIndexIsVisible(myAssertionIndex);
        myAssertionIndex++;
      }
    });

    myCancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        exitAssertionMode(false);
        hideScreenshotPanel();
      }
    });

    myAssertionElementComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        // Get selected element
        Object element = myAssertionElementComboBox.getSelectedItem();
        if (element instanceof BasicTreeNode) {
          // selected element is UI element
          BasicTreeNode node = (BasicTreeNode) element;
          // Update selected element in screenshot panel
          myScreenshotPanel.setSelectedNodeAndRepaint(node);
          // Update edit assertion panel
          if (isTextView(node)) {
            CardLayout cardLayout = (CardLayout) myTextFieldWrapper.getLayout();
            cardLayout.show(myTextFieldWrapper, "myAssertionTextField");
            myAssertionTextField.setText(getText(node));
            myAssertionRuleComboBox.setModel(new DefaultComboBoxModel(ASSERTION_RULES_WITH_TEXT));
          } else {
            CardLayout cardLayout = (CardLayout) myTextFieldWrapper.getLayout();
            cardLayout.show(myTextFieldWrapper, "myPlaceHolder");
            myAssertionRuleComboBox.setModel(new DefaultComboBoxModel(ASSERTION_RULES_WITHOUT_TEXT));
          }
          // Enable save assertion buttons.
          mySaveAssertionButton.setEnabled(true);
          mySaveAssertionAndAddAnotherButton.setEnabled(true);
          myAssertionTextField.setForeground(JBColor.BLACK);
        } else {
          // selected element is not UI element (default element)
          myScreenshotPanel.clearSelectionAndRepaint();
        }
      }
    });

    myAssertionElementComboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value instanceof BasicTreeNode) {
          BasicTreeNode node = (BasicTreeNode) value;
          // Add indent.
          int indent = myNodeIndentMap.get(node);
          String prefix = StringUtils.repeat("  ", indent);
          // No indent for selected element.
          if (index == -1) {
            prefix = "";
          }
          String resourceId = getResourceId(node);
          String nodeString = resourceId.isEmpty() ? getClassName(node) : resourceId;
          return super.getListCellRendererComponent(list, prefix + nodeString, index, isSelected, cellHasFocus);
        } else {
          // non UI element
          return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
      }
    });

    myAssertionRuleComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        String rule = myAssertionRuleComboBox.getSelectedItem().toString();
        if (TEXT_IS.equals(rule)) {
          // Display assertion text field when rule is "text ***"
          CardLayout cardLayout = (CardLayout) myTextFieldWrapper.getLayout();
          cardLayout.show(myTextFieldWrapper, "myAssertionTextField");
        } else {
          // Otherwise (exists, does not exist), don't display assertion text field
          CardLayout cardLayout = (CardLayout) myTextFieldWrapper.getLayout();
          cardLayout.show(myTextFieldWrapper, "myPlaceHolder");
        }
      }
    });

    myCompleteRecordingButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Show the test class name input dialog before (potentially) setting up Espresso dependencies,
        // which might confuse Gradle about the location of android tests.
        TestClassNameInputDialog chooser = new TestClassNameInputDialog(myFacet, launchedActivityName);
        chooser.show();

        boolean hasAddedEspressoDependencies = false;
        boolean hasCustomEspressoDependency = false;
        // Automatically check/setup Espresso dependencies for Gradle projects only.
        if (myGradleBuildModel != null) {
          AndroidModel androidModel = myGradleBuildModel.android();
          // androidModel will be null when the Gradle experimental plugin is used and it's not possible to update the instrumentation runner.
          // TODO: Provide an appropriate error message or some alternative way to update instrumentation runner when the Gradle experimental
          // plugin is used.
          if (androidModel != null && !hasAllRequiredEspressoDependencies(androidModel)) {
            UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                             .setCategory(EventCategory.TEST_RECORDER)
                                             .setKind(EventKind.TEST_RECORDER_MISSING_ESPRESSO_DEPENDENCIES));

            if (Messages.showDialog(myProject,
                                    "This app is missing some dependencies for running Espresso tests.\n" +
                                    "Would you like to automatically add Espresso dependencies for this app?\n" +
                                    "To complete the set up, Gradle might ask you to install the missing libraries.\n" +
                                    "Please click on the corresponding link(s) to install them.",
                                    "Missing Espresso dependencies",
                                    new String[]{Messages.NO_BUTTON, Messages.YES_BUTTON}, 1, null) != 0) {
              hasAddedEspressoDependencies = true;
              setupEspresso();
            }
          }
          hasCustomEspressoDependency = hasCustomEspressoDependency();
        }

        // Get all events (UI events and assertions).
        ArrayList<Object> events = new ArrayList<Object>();
        for (int i = 0; i < myEventListModel.size(); i++) {
          events.add(myEventListModel.get(i));
        }

        PsiClass testClass = chooser.getTestClass();

        if (testClass != null) {
          doOKAction();
          new TestCodeGenerator(myFacet, testClass, events, launchedActivityName, hasCustomEspressoDependency,
                                hasAddedEspressoDependencies).generate();
        }
      }
    });
  }

  private void exitAssertionMode(boolean shouldAddAssertion) {
    myAssertionMode = false;
    getRootPane().setDefaultButton(myCompleteRecordingButton);
    // Display button panel.
    CardLayout cardLayout = (CardLayout) myAssertionPanel.getLayout();
    cardLayout.show(myAssertionPanel, "myButtonsPanel");

    if (shouldAddAssertion) {
      // Add the new assertion at its remembered index.
      myEventListModel.add(myAssertionIndex, buildAssertionForCurrentSelection());
      // Scroll event list so that assertion is visible.
      myEventList.ensureIndexIsVisible(myAssertionIndex);
    } else {
      // Scroll event list so that the last event is visible.
      myEventList.ensureIndexIsVisible(myEventListModel.size() - 1);
    }
  }

  private void revealScreenshotPanel(int imageWidth, int imageHeight) {
    // Make the current size the minimum one to avoid shrinking of the current recording panel.
    // TODO: As a result, the user would not be able to reduce the size of the recording panel to a value smaller than the one
    // it had at the latest screenshot panel reveal.
    myRecordingPanel.setMinimumSize(new Dimension(myRecordingPanel.getWidth(), myRecordingPanel.getHeight()));

    myScreenshotPanel.setVisible(true);

    final int screenshotPanelTotalHeight = myRootPanel.getHeight();
    int scaledImageWidth = imageHeight <= screenshotPanelTotalHeight
                           ? imageWidth
                           : (int) ((double) (imageWidth * screenshotPanelTotalHeight) / imageHeight);

    // Cap panel width to not be greater than panel height.
    final int screenshotPanelTotalWidth = scaledImageWidth > screenshotPanelTotalHeight ? screenshotPanelTotalHeight : scaledImageWidth;

    final Timer t = new Timer(ANIMATION_TIMER_INTERVAL, null);
    final long start = System.currentTimeMillis();
    t.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > ANIMATION_INTERVAL) {
          myScreenshotPanel.setMinimumSize(new Dimension(screenshotPanelTotalWidth, screenshotPanelTotalHeight));
          t.stop();
        } else {
          double percentRevealed = ((double) elapsed / ANIMATION_INTERVAL);
          myScreenshotPanel.setMinimumSize(new Dimension((int)(screenshotPanelTotalWidth * percentRevealed), screenshotPanelTotalHeight));
        }

        myScreenshotPanel.clearSelectionAndRepaint();
        getWindow().pack();
      }
    });

    t.start();
  }

  private void hideScreenshotPanel() {
    final int screenshotPanelInitialWidth = myScreenshotPanel.getWidth();
    final int screenshotPanelInitialHeight = myScreenshotPanel.getHeight();
    final int marginWidth = ((FlowLayout)myScreenshotPanel.getLayout()).getHgap() * 2;
    final int windowInitialWidth = getWindow().getWidth();

    final Timer t = new Timer(ANIMATION_TIMER_INTERVAL, null);
    final long start = System.currentTimeMillis();
    t.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > ANIMATION_INTERVAL) {
          myScreenshotPanel.setVisible(false);
          myScreenshotPanel.setMinimumSize(new Dimension(0, 0));
          getWindow().setMinimumSize(
            new Dimension(windowInitialWidth - screenshotPanelInitialWidth - marginWidth, getWindow().getHeight()));
          t.stop();
        } else {
          double percentHidden = ((double) elapsed / ANIMATION_INTERVAL);
          myScreenshotPanel.setMinimumSize(
            new Dimension((int)(screenshotPanelInitialWidth * (1d - percentHidden)), screenshotPanelInitialHeight));
          getWindow().setMinimumSize(
            new Dimension(windowInitialWidth - (int)(screenshotPanelInitialWidth * percentHidden) - marginWidth, getWindow().getHeight()));
        }

        myScreenshotPanel.clearSelectionAndRepaint();
        getWindow().pack();
      }
    });

    t.start();
  }

  private void createUIComponents() {
    myScreenshotPanel = new ScreenshotPanel(this);
    myScreenshotPanel.setPreferredSize(new Dimension(0, 0));
    myScreenshotPanel.setVisible(false);

    myDisclaimerPane = new JEditorPane(
      UIUtil.HTML_MIME, "<html><a href='https://developer.android.com/r/studio-ui/test-recorder.html'>Espresso Test Recorder</a> is currently in beta. "
                        + "Please <a href='https://code.google.com/p/android/issues/entry?template=Espresso%20Test%20Recorder%20Bug'>report any issues</a>.</html>");

    myDisclaimerPane.setFont(myDisclaimerPane.getFont().deriveFont(12f));
    linkifyEditorPane(myDisclaimerPane, myScreenshotPanel.getBackground());
  }

  private void linkifyEditorPane(@NotNull JEditorPane editorPane, @NotNull Color backgroundColor) {
    editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    editorPane.setEditable(false);
    editorPane.setBackground(backgroundColor);
    editorPane.addHyperlinkListener(getHyperlinkListener());
  }

  private HyperlinkListener getHyperlinkListener() {
    return new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(final HyperlinkEvent linkEvent) {
        if (linkEvent.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              try {
                Desktop.getDesktop().browse(linkEvent.getURL().toURI());
              }
              catch (Exception e) {
                // ignore
              }
            }
          });
        }
      }
    };
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JBDimension initialSize = JBUI.size(450, 600);
    myRootPanel.setPreferredSize(initialSize);
    myRecordingPanel.setMinimumSize(initialSize);
    return myRootPanel;
  }

  private boolean hasAllRequiredEspressoDependencies(@NotNull AndroidModel androidModel) {
    // TODO: To improve performance, consider doing these checks in a single pass.
    return (hasEspressoCoreDependency() || hasCustomEspressoDependency())
           && (!needsEspressoContribDependency() || hasEspressoContribDependency())
           && hasSetInstrumentationRunner(androidModel);
  }

  private boolean needsEspressoContribDependency() {
    for (int i = 0; i < myEventListModel.size(); i++) {
      Object event = myEventListModel.get(i);
      if (event instanceof TestRecorderEvent && ((TestRecorderEvent)event).getRecyclerViewPosition() != -1) {
        return true;
      }
    }
    return false;
  }

  private boolean hasCustomEspressoDependency() {
    String artifact = ESPRESSO_CORE_CUSTOM_GROUP_NAME + ":" + ESPRESSO_CORE_CUSTOM_ARTIFACT_NAME;
    return GradleUtil.dependsOnAndroidTest(myAndroidGradleModel, artifact) || GradleUtil.dependsOn(myAndroidGradleModel, artifact);
  }

  private boolean hasEspressoCoreDependency() {
    String artifact = ESPRESSO_CORE.getGroupId() + ":" + ESPRESSO_CORE.getArtifactId();
    return GradleUtil.dependsOnAndroidTest(myAndroidGradleModel, artifact) || GradleUtil.dependsOn(myAndroidGradleModel, artifact);
  }

  private boolean hasEspressoContribDependency() {
    String artifact = ESPRESSO_CONTRIB.getGroupId() + ":" + ESPRESSO_CONTRIB.getArtifactId();
    return GradleUtil.dependsOnAndroidTest(myAndroidGradleModel, artifact) || GradleUtil.dependsOn(myAndroidGradleModel, artifact);
  }

  private boolean hasSetInstrumentationRunner(@NotNull AndroidModel androidModel) {
    String testInstrumentationRunner = androidModel.defaultConfig().testInstrumentationRunner().value();
    return testInstrumentationRunner != null && !testInstrumentationRunner.isEmpty();
  }

  private void setupEspresso() {
    new Task.Modal(myProject, "Setting up Espresso", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Adding Espresso dependencies");
        indicator.setIndeterminate(true);

        // TODO: This is a trick to make sure the progress dialog is shown. Otherwise, the action is too quick for the dialog to show up,
        // but long enough to see a noticeable delay.
        try {
          Thread.sleep(500);
        } catch (Exception e) {
          //  ignore
        }

        WriteCommandAction.runWriteCommandAction(myProject, () -> {
          if (!hasEspressoCoreDependency() && !hasCustomEspressoDependency()) {
            myGradleBuildModel.dependencies().addArtifact(ANDROID_TEST_COMPILE,
                                                          new ArtifactDependencySpec(ESPRESSO_CORE.getArtifactId(),
                                                                                     ESPRESSO_CORE.getGroupId(),
                                                                                     ESPRESSO_VERSION),
                                                          ESPRESSO_EXCLUDES);
          }

          if (needsEspressoContribDependency() && !hasEspressoContribDependency()) {
            myGradleBuildModel.dependencies().addArtifact(ANDROID_TEST_COMPILE,
                                                          new ArtifactDependencySpec(ESPRESSO_CONTRIB.getArtifactId(),
                                                                                     ESPRESSO_CONTRIB.getGroupId(),
                                                                                     ESPRESSO_VERSION),
                                                          ESPRESSO_CONTRIB_EXCLUDES);
          }

          AndroidModel androidModel = myGradleBuildModel.android();
          if (androidModel != null && !hasSetInstrumentationRunner(androidModel)) {
            androidModel.defaultConfig().setTestInstrumentationRunner(TEST_INSTRUMENTATION_RUNNER);
          }

          myGradleBuildModel.applyChanges();

          GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(myProject, null);
        });
      }
    }.queue();
  }

  private TestRecorderAssertion buildAssertionForCurrentSelection() {
    UiNode node = (UiNode)myAssertionElementComboBox.getSelectedItem();
    String rule = myAssertionRuleComboBox.getSelectedItem().toString();

    TestRecorderAssertion assertion = new TestRecorderAssertion(rule);
    addElementDescriptors(assertion, node);

    if (TEXT_IS.equals(rule)) {
      assertion.setText(myAssertionTextField.getText());
    }

    return assertion;
  }

  private void addElementDescriptors(TestRecorderAssertion assertion, UiNode node) {
    if (node == null || assertion.getElementDescriptorsCount() >= TestRecorderSettings.getInstance().ASSERTION_DEPTH) {
      return;
    }

    String className = getClassName(node);
    String resourceId = getResourceId(node);
    // Use text identification only for text views.
    String text = isTextView(node) ? getText(node) : "";
    String contentDescription = getContentDescription(node);
    int childPosition = getChildPosition(node);

    if (!className.isEmpty() || !resourceId.isEmpty() || !text.isEmpty() || !contentDescription.isEmpty() || childPosition != -1) {
      assertion.addElementDescriptor(new ElementDescriptor(className, childPosition, resourceId, contentDescription, text));
      addElementDescriptors(assertion, (UiNode)node.getParent());
    }
  }

  // Set up assertion panel with no element selected.
  protected void setUpEmptyAssertionPanel() {
    myAssertionElementComboBox.setModel(myElementComboBoxModel);
    myAssertionElementComboBox.setSelectedIndex(0);
    myAssertionElementComboBox.setForeground(JBColor.BLACK);

    myAssertionRuleComboBox.setModel(new DefaultComboBoxModel());

    // Hide assertion text field.
    CardLayout cardLayout = (CardLayout) myTextFieldWrapper.getLayout();
    cardLayout.show(myTextFieldWrapper, "myPlaceHolder");

    // Disable save assertion buttons.
    mySaveAssertionButton.setEnabled(false);
    mySaveAssertionAndAddAnotherButton.setEnabled(false);
  }

  protected void setUpAssertionPanel(BasicTreeNode node) {
    myAssertionElementComboBox.setModel(myElementComboBoxModel);
    myAssertionElementComboBox.setSelectedItem(node);
    myAssertionElementComboBox.setForeground(JBColor.BLACK);

    mySaveAssertionButton.setEnabled(true);
    mySaveAssertionAndAddAnotherButton.setEnabled(true);

    CardLayout cardLayout = (CardLayout) myTextFieldWrapper.getLayout();
    if (isTextView(node)) {
      cardLayout.show(myTextFieldWrapper, "myAssertionTextField");
      myAssertionTextField.setText(getText(node));
      myAssertionRuleComboBox.setModel(new DefaultComboBoxModel(ASSERTION_RULES_WITH_TEXT));
    } else {
      cardLayout.show(myTextFieldWrapper, "myPlaceHolder");
      myAssertionRuleComboBox.setModel(new DefaultComboBoxModel(ASSERTION_RULES_WITHOUT_TEXT));
    }
  }

  public boolean isAssertionMode() {
    return myAssertionMode;
  }

  @Override
  /**
   *  Listen to debugger event and update event list.
   */
  public void onEvent(final TestRecorderEvent event) {
    // Ignore not supported events.
    if (!SUPPORTED_EVENTS.contains(event.getEventType())) {
      return;
    }
    // Add event to list
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        // It it is first element, add it anyway
        if (myEventListModel.isEmpty()) {
          myEventListModel.addElement(event);
        } else {
          Object lastEvent = myEventListModel.lastElement();
          // If can merge with last event, replace last event with the merged one.
          if (lastEvent instanceof TestRecorderEvent && ((TestRecorderEvent)lastEvent).canMerge(event)) {
            ((TestRecorderEvent)lastEvent).merge(event);
            // Repaint is needed since otherwise the change would not be picked up by the renderer.
            myEventList.repaint();
          } else {
            myEventListModel.addElement(event);
          }
        }
        // Scroll event list so that the last event is visible
        myEventList.ensureIndexIsVisible(myEventList.getItemsCount() - 1);
      }
    });
  }

}
