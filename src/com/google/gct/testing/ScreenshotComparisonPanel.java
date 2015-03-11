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

import com.android.annotations.Nullable;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.dimension.CloudTestingType;
import com.google.gct.testing.dimension.GoogleCloudTestingDimension;
import com.google.gct.testing.dimension.OrientationDimension;
import com.google.gct.testing.results.GoogleCloudTestProxy;
import com.google.gct.testing.ui.CopyImageToClipboard;
import com.google.gct.testing.ui.Tab;
import com.google.gct.testing.ui.TabEventListener;
import com.google.gct.testing.ui.WipePanel;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.intellij.icons.AllIcons.RunConfigurations.*;
import static java.awt.Color.BLACK;

public class ScreenshotComparisonPanel implements ScreenshotComparisonHeaderPanelListener, ConfigurationResultListener {

  private enum StaticImageKind {LOADING, NO_IMAGE};

  public static final Function<GoogleCloudTestingTypeSelection, CloudTestingType> GET_SELECTED_TYPE = new Function<GoogleCloudTestingTypeSelection, CloudTestingType>() {
    @Override
    public CloudTestingType apply(GoogleCloudTestingTypeSelection input) {
      return input.getType();
    }
  };
  public static final Color GREEN = new Color(61, 138, 78);
  public static final Color RED = UIUtil.isUnderDarcula() ? CloudTestingUtils.makeDarker(new Color(183, 14, 10), 2) : new Color(183, 14, 10);

  private final ScreenshotComparisonDialog parent;
  private final AbstractTestProxy testTreeRoot;
  private final CloudTestConfigurationImpl configuration;
  private ConfigurationInstance selectedConfigurationInstance;
  private TestName currentTest;
  private int currentStep;
  //Indexed by encoded configuration instance name.
  private final Map<String, ConfigurationResult> results;

  private JPanel myPanel;
  private JPanel myScreenshotPanel;
  private JPanel myImagePanel;
  private JPanel myConfigurationChooserPanel;
  private JPanel myButtonPanel;

  private final WipePanel wipePanel;

  private final List<GoogleCloudTestingTypeSelection> myTypeSelections = new LinkedList<GoogleCloudTestingTypeSelection>();
  private JLabel myImageLabel;

  private static final int MAX_IMAGE_WIDTH = 533;
  private static final int MAX_IMAGE_HEIGHT = 533;
  private static final int PORTRAIT_WIDTH = 300;
  private static final int LANDSCAPE_HEIGHT = 300;

  //private static final int IMAGE_WIDTH = 300;
  //private static int imageHeight = 550;
  private static final BufferedImage NO_IMAGE_PORTRAIT;
  private static final BufferedImage NO_IMAGE_LANSCAPE;
  private static final BufferedImage LOADING_PORTRAIT;
  private static final BufferedImage LOADING_LANDSCAPE;

  private UpdateImageThread updateImageThread;
  private final Object lock;
  private boolean isLoaded;

  static {
    try {
      NO_IMAGE_PORTRAIT = ImageIO.read(ScreenshotComparisonPanel.class.getResourceAsStream("NoImagePortrait.png"));
      NO_IMAGE_PORTRAIT.flush();
      NO_IMAGE_LANSCAPE = ImageIO.read(ScreenshotComparisonPanel.class.getResourceAsStream("NoImageLandscape.png"));
      NO_IMAGE_LANSCAPE.flush();
      LOADING_PORTRAIT = ImageIO.read(ScreenshotComparisonPanel.class.getResourceAsStream("LoadingPortrait.png"));
      LOADING_PORTRAIT.flush();
      LOADING_LANDSCAPE = ImageIO.read(ScreenshotComparisonPanel.class.getResourceAsStream("LoadingLandscape.png"));
      LOADING_PORTRAIT.flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private BufferedImage currentImage;

  private Icon referenceIcon = null;


  public ScreenshotComparisonPanel(ScreenshotComparisonDialog parent, @Nullable ScreenshotComparisonPanel clonedPanel,
                                   AbstractTestProxy testTreeRoot, CloudTestConfigurationImpl configuration,
                                   ConfigurationInstance configurationInstance, TestName currentTest, int currentStep,
                                   Map<String, ConfigurationResult> results) {
    lock = this;
    this.parent = parent;
    if (clonedPanel != null) {
      referenceIcon = clonedPanel.myImageLabel.getIcon();
    }
    this.testTreeRoot = testTreeRoot;
    this.configuration = configuration;
    selectedConfigurationInstance = configurationInstance;
    this.currentTest = currentTest;
    this.currentStep = currentStep;
    this.results = results;
    wipePanel = new WipePanel();
    wipePanel.setContentPanel(myPanel);

    ConfigurationResult selectedConfigurationResult = getSelectedConfigurationResult();
    if (selectedConfigurationResult != null) {
      selectedConfigurationResult.addConfigurationResultListener(this);
    }

    //TODO: Dispose to avoid memory leak.
    init(clonedPanel);
  }

  public void init(@Nullable ScreenshotComparisonPanel clonedPanel) {
    if (UIUtil.isUnderDarcula()) {
      myConfigurationChooserPanel.setBackground(CloudTestingUtils.makeDarker(UIUtil.getPanelBackground(), 1));
    }

    DefaultListCellRenderer dimensionChooserRenderer = new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof CloudTestingType) {
          final CloudTestingType type = (CloudTestingType) value;

          label.setText(type.getResultsViewerDisplayName());
          //label.setIcon(type.getIcon());
          //label.setIconTextGap(2);
        }
        return label;
      }
    };

    GridBagConstraints bagConstraints = new GridBagConstraints();
    bagConstraints.fill = GridBagConstraints.HORIZONTAL;
    bagConstraints.ipadx = 5;

    int index = 0;
    for (final GoogleCloudTestingDimension dimension : configuration.getDimensions()) {
      JLabel label = new JLabel(dimension.getIcon());
      label.setToolTipText(dimension.getDisplayName());

      Font oldFont = label.getFont();
      label.setFont(new Font(oldFont.getFontName(), Font.BOLD, oldFont.getSize()));
      bagConstraints.gridy = index;
      bagConstraints.gridx = 0;
      myConfigurationChooserPanel.add(label, bagConstraints);

      bagConstraints.gridy = index;
      bagConstraints.gridx = 1;
      if (dimension.getEnabledTypes().size() > 1) {
        final JComboBox comboBox = new JComboBox(new DefaultComboBoxModel(dimension.getEnabledTypes().toArray()));
        comboBox.setRenderer(dimensionChooserRenderer);

        comboBox.setSelectedItem(selectedConfigurationInstance.getTypeForDimension(dimension));
        // Add action listener after making the selection to avoid updating the image while not all checkboxes are ready.
        final ScreenshotComparisonPanel thisPanel = this;
        comboBox.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            ConfigurationResult currentConfigurationResult = getSelectedConfigurationResult();
            if (currentConfigurationResult != null) {
              currentConfigurationResult.removeConfigurationResultListener(thisPanel);
            }
            selectedConfigurationInstance = computeSelectedConfigurationInstance();
            currentConfigurationResult = getSelectedConfigurationResult();
            if (currentConfigurationResult != null) {
              currentConfigurationResult.addConfigurationResultListener(thisPanel);
            }
            updateHeaderBar();
            if (dimension instanceof OrientationDimension) {
              referenceIcon = null;
            } else {
              referenceIcon = myImageLabel.getIcon();
            }
            updateImage();
            parent.updateMaxStep();
          }
        });

        myConfigurationChooserPanel.add(comboBox, bagConstraints);
        myTypeSelections.add(new GoogleCloudTestingTypeSelection() {
          @Override
          public CloudTestingType getType() {
            return (CloudTestingType)comboBox.getSelectedItem();
          }
        });
      } else {
        final CloudTestingType type = Iterables.getOnlyElement(dimension.getEnabledTypes());
        myConfigurationChooserPanel.add(new JLabel(" " + type.getResultsViewerDisplayName()), bagConstraints);
        myTypeSelections.add(new GoogleCloudTestingTypeSelection() {
          @Override
          public CloudTestingType getType() {
            return type;
          }
        });
      }

      index++;
    }

    myImageLabel = new JLabel();
    myImagePanel.add(myImageLabel, BorderLayout.CENTER);

    updateHeaderBar();

    if (clonedPanel != null && clonedPanel.isLoaded) {
      myImageLabel.setIcon(clonedPanel.myImageLabel.getIcon());
      isLoaded = true;
    } else {
      updateImage();
    }
  }

  public int getMaxStep() {
    ConfigurationResult selectedConfigurationResult = getSelectedConfigurationResult();
    return selectedConfigurationResult == null ? 0 : selectedConfigurationResult.maxScreenshotStep(currentTest);
  }

  private void updateImage() {
    synchronized (lock) {
      isLoaded = false;
      if (updateImageThread != null) {
        updateImageThread.makeObsolete();
      }
      ConfigurationResult selectedConfigurationResult = getSelectedConfigurationResult();
      if (selectedConfigurationResult == null) {
        setStaticImage(StaticImageKind.NO_IMAGE);
        return;
      }
      setStaticImage(StaticImageKind.LOADING);
      updateImageThread = new UpdateImageThread(currentTest, currentStep, selectedConfigurationResult);
      updateImageThread.start();
    }
  }

  private void setStaticImage(StaticImageKind imageKind) {
    BufferedImage staticImage = imageKind == StaticImageKind.LOADING
      ? (isPortrait() ? LOADING_PORTRAIT : LOADING_LANDSCAPE)
      : (isPortrait() ? NO_IMAGE_PORTRAIT : NO_IMAGE_LANSCAPE);
    Pair<Integer, Integer> imageSize = getStaticImageSize();
    myImageLabel.setIcon(new ImageIcon(staticImage.getScaledInstance(imageSize.getFirst(), imageSize.getSecond(), Image.SCALE_SMOOTH)));
    isLoaded = imageKind != StaticImageKind.LOADING;
    parent.fitWindow();
  }

  /**
   * Returns a pair of (width, height).
   */
  private Pair<Integer, Integer> getStaticImageSize() {
    if (referenceIcon != null) {
      return new Pair(referenceIcon.getIconWidth(), referenceIcon.getIconHeight());
    }
    if (isPortrait()) {
      return new Pair(PORTRAIT_WIDTH, MAX_IMAGE_HEIGHT);
    }
    return new Pair(MAX_IMAGE_WIDTH, LANDSCAPE_HEIGHT);
  }

  private class UpdateImageThread extends Thread {

    private final TestName test;
    private final int step;
    private final ConfigurationResult configurationResult;

    private BufferedImage loadedImage;

    private volatile boolean isObsolete = false;

    public UpdateImageThread(TestName test, int step, ConfigurationResult configurationResult) {
      this.test = test;
      this.step = step;
      this.configurationResult = configurationResult;
    }

    public synchronized void makeObsolete() {
      isObsolete = true;
    }

    public synchronized boolean isObsolete() {
      return isObsolete;
    }

    @Override
    public void run() {
      loadedImage = configurationResult.getScreenshotForTestAndStep(test, step); // A long-running operation.
      if (isObsolete()) {
        return;
      }
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          synchronized (lock) {
            if (isObsolete()) {
              return;
            }
            currentImage = loadedImage;
            if (currentImage == null) {
              setStaticImage(StaticImageKind.NO_IMAGE);
              return;
            }

            //TODO: This is a temporary rotation hack that should be removed after the backend produces correct landscape screenshots.
            // Rotate landscape screenshots that are indeed mis-rotated.
            if (selectedConfigurationInstance.getEncodedString().endsWith("landscape") && currentImage.getHeight() > currentImage.getWidth()) {

              AffineTransform transform = new AffineTransform();
              transform.translate(currentImage.getHeight() / 2, currentImage.getWidth() / 2);
              transform.rotate(-Math.PI / 2);
              //transform.scale(0.5, 0.5);
              transform.translate(-currentImage.getWidth() / 2, -currentImage.getHeight() / 2);
              //transform.rotate(-Math.PI / 2, currentImage.getWidth() / 2, currentImage.getHeight() / 2);
              AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
              currentImage = op.filter(currentImage, null);
            }

            int imageWidth = currentImage.getWidth();
            int imageHeight = currentImage.getHeight();
            if (imageWidth > MAX_IMAGE_WIDTH) {
              imageHeight = imageHeight * MAX_IMAGE_WIDTH / imageWidth;
              imageWidth = MAX_IMAGE_WIDTH;
            }
            if (imageHeight > MAX_IMAGE_HEIGHT) {
              imageWidth = imageWidth * MAX_IMAGE_HEIGHT / imageHeight;
              imageHeight = MAX_IMAGE_HEIGHT;
            }
            myImageLabel.setIcon(new ImageIcon(currentImage.getScaledInstance(imageWidth, imageHeight, Image.SCALE_SMOOTH)));
            isLoaded = true;
            parent.fitWindow();
          }
        }
      });
    }
  }

  private void updateHeaderBar() {
    String configurationInstanceName = selectedConfigurationInstance.getResultsViewerDisplayString();
    GoogleCloudTestProxy testNode;
    try {
      testNode = (GoogleCloudTestProxy) getChild(getChild(getChild(testTreeRoot, configurationInstanceName), currentTest.getClassName()), currentTest.getMethodName());
    } catch(NoSuchElementException e) {
      //This configuration has no results yet => update the status accordingly and return.
      createTabsAndBorders("No Result", TestNotRan, BLACK);
      return;
    }
    if (testNode.isPassed()) {
      createTabsAndBorders("Passed", TestPassed, GREEN);
    } else {
      if (testNode.isError()) { // First check for the error as errors are also failures.
        createTabsAndBorders("Error", TestError, RED);
      } else if (testNode.isFailure()) {
        createTabsAndBorders("Failed", TestFailed, RED);
      } else if (testNode.isIgnored()) {
        createTabsAndBorders("Ignored", TestIgnored, RED);
      } else  {
        createTabsAndBorders("Interrupted", TestTerminated, RED);
      }
    }
  }

  private void createTabsAndBorders(String status, Icon icon, Color color) {
    myScreenshotPanel.setBorder(new MatteBorder(8, 2, 2, 2, color));
    if (UIUtil.isUnderDarcula()) {
      myScreenshotPanel.setBackground(CloudTestingUtils.makeDarker(UIUtil.getPanelBackground(), 1));
    }
    myButtonPanel.removeAll();

    Tab mainTab = Tab.makeStandardTab(status, icon, color);
    myButtonPanel.add(mainTab, BorderLayout.CENTER);

    myButtonPanel.updateUI();

    final ScreenshotComparisonPanel thisPanel = this;

    mainTab.addTabEventListener(new TabEventListener() {
      @Override
      public void closeEvent() {
        parent.removeScreenshotComparisonPanel(thisPanel);
      }

      @Override
      public void openEvent() {
        parent.addScreenshotComparisonPanel(thisPanel);
      }

      @Override
      public void saveImage() {
        BufferedImage image = getImage();
        ConfigurationResult selectedConfigurationResult = getSelectedConfigurationResult();
        if (selectedConfigurationResult == null || image == null) {
          return;
        }

        ConfigurationInstance configurationInstance = selectedConfigurationResult.getConfigurationInstance();
        String proposedFileName = configurationInstance.getEncodedString();
        String description = "Save screenshot of " + configurationInstance.getResultsViewerDisplayString();

        FileSaverDescriptor descriptor = new FileSaverDescriptor("Save Screenshot", description, "png");
        FileSaverDialogImpl fileSaverDialog = new FileSaverDialogImpl(descriptor, parent.getWindow());
        VirtualFileWrapper fileWrapper = fileSaverDialog.save(null, proposedFileName);

        if (fileWrapper != null) {
          try {
            ImageIO.write(image, "png", fileWrapper.getFile());
          }
          catch (IOException e) {
            e.printStackTrace();
          }
        }
      }

      @Override
      public void copyImage() {
        BufferedImage image = getImage();
        if (image == null)
          return;

        CopyImageToClipboard.copy(image);
      }
    });
  }

  private BufferedImage getImage() {
    return currentImage;
  }

  private AbstractTestProxy getChild(AbstractTestProxy parent, String childName) {
    for (AbstractTestProxy child : parent.getChildren()) {
      if (child.getName().equals(childName)) {
        return child;
      }
    }
    throw new NoSuchElementException("Could not find child: " + childName);
  }

  private ConfigurationResult getSelectedConfigurationResult() {
    return results.get(selectedConfigurationInstance.getEncodedString());
  }

  private boolean isPortrait() {
    return selectedConfigurationInstance.getEncodedString().endsWith("portrait");
  }

  public void stopListeningToResults() {
    ConfigurationResult selectedConfigurationResult = getSelectedConfigurationResult();
    if (selectedConfigurationResult != null ) {
      selectedConfigurationResult.removeConfigurationResultListener(this);
    }
  }

  public ConfigurationInstance computeSelectedConfigurationInstance() {
    return new ConfigurationInstance(Lists.newArrayList(Iterables.transform(myTypeSelections, GET_SELECTED_TYPE)));
  }

  public WipePanel getPanel() {
    return wipePanel;
  }

  @Override
  public void updateTest(TestName test, boolean shouldUpdateImage) {
    currentTest = test;
    if (shouldUpdateImage) {
      updateHeaderBar();
      referenceIcon = myImageLabel.getIcon();
      updateImage();
    }
  }

  @Override
  public void updateStep(int step, boolean shouldUpdateImage) {
    currentStep = step;
    if (shouldUpdateImage) {
      referenceIcon = myImageLabel.getIcon();
      updateImage();
    }
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }

  @Override
  public void screenshotsUpdated() {
    updateImage();
    parent.updateMaxStep();
  }
}

interface GoogleCloudTestingTypeSelection {
  public CloudTestingType getType();
}
