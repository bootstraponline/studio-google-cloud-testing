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

import com.android.uiautomator.UiAutomatorModel;
import com.android.uiautomator.tree.BasicTreeNode;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

public class ScreenshotPanel extends JPanel {

  private RecordingDialog myParent;
  private BufferedImage myImage;
  private UiAutomatorModel myModel;

  private Dimension myCanvasSize;
  private Dimension myImageSize;
  private float myScale;
  private int startX;
  private int startY;
  private int myScaledWidth;
  private int myScaleHeight;

  private BasicTreeNode myHoverNode;
  private BasicTreeNode mySelectedNode;

  public ScreenshotPanel(final RecordingDialog parent) {

    // Initialize member variable
    myParent = parent;

    // Add mouse click listener
    this.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {

        if (!isValidAssertionMode()) {
          return;
        }

        // Get UI element at mouse position
        BasicTreeNode node = getElementAtMousePosition(mouseEvent);

        // If no element, do nothing
        if (node == null) {
          return;
        }

        // If no selection made, select current element.
        // And update screenshot panel and assertion panel.
        if (mySelectedNode == null) {
          mySelectedNode = node;
          myParent.setUpAssertionPanel(node);
          repaint();
        } else {
          // Else if already made a selection, cancel that selection.
          // Abd update screenshot panel and assertion panel.
          mySelectedNode = null;
          myParent.setUpEmptyAssertionPanel();
          repaint();
        }
      }
    });

    // Add mouse hover over listener
    this.addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent mouseEvent) {
        // Do nothing
      }

      @Override
      public void mouseMoved(MouseEvent mouseEvent) {

        if (!isValidAssertionMode()) {
          return;
        }

        // Get UI element at mouse position
        BasicTreeNode node = getElementAtMousePosition(mouseEvent);

        // If no element or find a different element, update screenshot panel
        if (node == null || !node.equals(myHoverNode)) {
          myHoverNode = node;
          repaint();
        }
        // update previous element
        myHoverNode = node;
      }
    });
  }

  private boolean isValidAssertionMode() {
    return myImage != null && myModel != null && myParent.isAssertionMode();
  }

  private BasicTreeNode getElementAtMousePosition(MouseEvent mouseEvent) {
    // Get mouse position
    int mouseX = mouseEvent.getX();
    int mouseY = mouseEvent.getY();

    // Scale mouse position
    mouseX = (int) ((float) (mouseX - startX) / myScale);
    mouseY = (int) ((float) (mouseY - startY) / myScale);

    // Return UI element at mouse position
    return myModel.updateSelectionForCoordinates(mouseX, mouseY);
  }

  @Override
  protected void paintComponent(Graphics g) {

    super.paintComponent(g);

    // If no screenshot, do nothing.
    if (myImage == null) {
      return;
    }

    // Compute canvas panel size and screenshot image size
    myCanvasSize = getSize();
    myImageSize = new Dimension(myImage.getWidth(), myImage.getHeight());

    // Compute scale
    float scaleX = (float) (myCanvasSize.width) / (float) myImageSize.width;
    float scaleY = (float) (myCanvasSize.height) / (float) myImageSize.height;
    myScale = Math.min(scaleX, scaleY);

    // Compute scaled screenshot image size
    myScaledWidth = (int) (myImageSize.width * myScale);
    myScaleHeight = (int) (myImageSize.height * myScale);

    // Scale screenshot image
    Image scaledImage = myImage.getScaledInstance(myScaledWidth, myScaleHeight, Image.SCALE_SMOOTH);

    // Compute screenshot image start position
    startX = (myCanvasSize.width - myScaledWidth) / 2;
    startY = (myCanvasSize.height - myScaleHeight) / 2;

    // Draw scaled screenshot image
    g.drawImage(scaledImage, startX, startY, null);

    // If no in assertion mode, clear selected node and hover node (only draw screenshot)
    if (!myParent.isAssertionMode()) {
      mySelectedNode = null;
      myHoverNode = null;
    }

    // If a node is selected, draw corresponding rectangle
    if (mySelectedNode != null) {

      // Compute start position, width and height
      final int x = (int) (mySelectedNode.x * myScale) + startX;
      final int y = (int) (mySelectedNode.y * myScale) + startY;
      final int w = (int) (mySelectedNode.width * myScale);
      final int h = (int) (mySelectedNode.height * myScale);

      // Draw rectangle
      Graphics2D g2 = (Graphics2D) g;
      g2.setColor(Color.RED);
      g2.setStroke(new BasicStroke(2));
      g2.drawRect(x, y, w, h);
    } else if (myHoverNode != null) {
      // else if we have a hover node, draw corresponding rectangle

      // Compute start position, width and height
      final int x = (int) (myHoverNode.x * myScale) + startX;
      final int y = (int) (myHoverNode.y * myScale) + startY;
      final int w = (int) (myHoverNode.width * myScale);
      final int h = (int) (myHoverNode.height * myScale);

      // Draw rectangle
      g.setColor(Color.RED);
      g.drawRect(x, y, w, h);
    }
  }

  // Update screenshot and UI hierarchy, and refresh screenshot panel
  public void updateScreenShot(BufferedImage image, UiAutomatorModel model) {
    myImage = image;
    myModel = model;

    clearSelectionAndRepaint();
  }


  public void clearSelectionAndRepaint() {
    mySelectedNode = null;
    myHoverNode = null;

    this.revalidate();
    this.repaint();
  }

  // Update selected node, and refresh screenshot panel
  public void setSelectedNodeAndRepaint(@Nullable BasicTreeNode node) {
    mySelectedNode = node;
    repaint();
  }

  // Get UI hierarchy model
  public UiAutomatorModel getModel() {
    return myModel;
  }
}
