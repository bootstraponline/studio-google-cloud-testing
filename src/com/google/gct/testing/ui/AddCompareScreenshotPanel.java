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
package com.google.gct.testing.ui;

import com.google.gct.testing.GoogleCloudTestingUtils;
import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static javax.swing.JFrame.EXIT_ON_CLOSE;

public class AddCompareScreenshotPanel {
  public static final int WIDTH = 140;

  private JLabel imageLabel;
  private JLabel textLabel;
  private JPanel myPanel;

  private final ImageIcon COMPARE_REGULAR;
  private final ImageIcon COMPARE_HOVER;

  private final Color regularColor = UIUtil.isUnderDarcula() ? Color.gray : Color.lightGray;
  private final Color hoverColor = UIUtil.isUnderDarcula() ? Color.lightGray : Color.gray;

  private final Border regularBorder = createDashedBorder(regularColor, 4, 5, 3, true);
  private final Border hoverBorder = createDashedBorder(hoverColor, 4, 5, 3, true);

  List<AddScreenshotListener> listeners = new LinkedList<AddScreenshotListener>();

  public AddCompareScreenshotPanel() {
    BufferedImage darkImage = null;
    BufferedImage lightImage = null;
    BufferedImage brightImage = null;

    try {
      darkImage = ImageIO.read(AddCompareScreenshotPanel.class.getResourceAsStream("compare-dark.png"));
      darkImage.flush();

      lightImage = ImageIO.read(AddCompareScreenshotPanel.class.getResourceAsStream("compare-light.png"));
      lightImage.flush();

      brightImage = ImageIO.read(AddCompareScreenshotPanel.class.getResourceAsStream("compare-bright.png"));
      brightImage.flush();
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    COMPARE_HOVER = UIUtil.isUnderDarcula() ? new ImageIcon(brightImage) : new ImageIcon(darkImage);
    COMPARE_REGULAR = new ImageIcon(lightImage);

    textLabel.setForeground(Color.gray);
    textLabel.setFont(new Font("Arial", Font.BOLD, 13));

    //noinspection Since15
    myPanel.setBorder(regularBorder);

    imageLabel.setText(null);
    imageLabel.setIcon(COMPARE_REGULAR);

    //myPanel.setPreferredSize(new Dimension(WIDTH, 450));

    myPanel.addMouseListener(new MouseListener());
  }

  public void setHeight(int height) {
    myPanel.setPreferredSize(new Dimension(WIDTH, height));
  }

  public void addListener(AddScreenshotListener listener) {
    listeners.add(listener);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  class MouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      for (AddScreenshotListener listener : listeners) {
        listener.addScreenshot();
      }
      mouseExited(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      myPanel.setBackground(null);
    }

    @Override
    public void mousePressed(MouseEvent e) {
      myPanel.setBackground(GoogleCloudTestingUtils.makeDarker(myPanel.getBackground(), 1));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      myPanel.setToolTipText("Compare to another screenshot");
      imageLabel.setIcon(COMPARE_HOVER);
      myPanel.setBorder(hoverBorder);
      textLabel.setForeground(hoverColor);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      myPanel.setToolTipText(null);
      imageLabel.setIcon(COMPARE_REGULAR);
      myPanel.setBorder(regularBorder);
      textLabel.setForeground(regularColor);
    }
  }

  public static Border createDashedBorder(Paint paint, float thickness, float length, float spacing, boolean rounded) {
    int cap = rounded ? BasicStroke.CAP_ROUND : BasicStroke.CAP_SQUARE;
    int join = rounded ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_MITER;
    float[] array = { thickness * (length - 1.0f), thickness * (spacing + 1.0f) };
    Border border = new StrokeBorder(new BasicStroke(thickness, cap, join, thickness * 2.0f, array, 0.0f), paint);
    return border;
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        JFrame frame = new JFrame("AddScreenshotPanel");
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel containerPanel = new JPanel(new FlowLayout());
        containerPanel.add(new AddCompareScreenshotPanel().getPanel());

        frame.add(containerPanel);
        frame.setSize(600, 600);
        frame.setVisible(true);
      }
    });
  }
}
