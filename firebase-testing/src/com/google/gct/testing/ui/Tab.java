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

import com.android.annotations.Nullable;
import com.google.gct.testing.CloudTestingUtils;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;

import static com.google.gct.testing.ScreenshotComparisonPanel.GREEN;
import static com.intellij.icons.AllIcons.RunConfigurations.TestPassed;
import static javax.swing.JFrame.EXIT_ON_CLOSE;

public class Tab extends GradientJPanel {

  //private static final Color BOTTOM_COLOR = new Color(204, 204, 204);
  //private static final Color TOP_COLOR = new Color(245, 245, 245);

  private static final Color TOP_COLOR = UIUtil.getPanelBackground();
  private static final Color BOTTOM_COLOR = CloudTestingUtils.makeDarker(UIUtil.getPanelBackground(), 2);

  private static final Color STUB_TAB_BACKGROUND = new Color(200, 200, 200);
  private static final Color STUB_TAB_FOREGROUND = new Color(114, 114, 114);

  private static final Icon closeIcon = AllIcons.Actions.CloseNew;

  private final JBLabel label;
  private final List<TabEventListener> listeners = new LinkedList<TabEventListener>();
  private final JPopupMenu popupMenu;

  public static Tab makeStandardTab(String title, Icon icon, Color borderColor) {
    return new Tab(title, icon, borderColor, true);
  }

  /**
   * Create a tab that is used to open new tabs
   */
  public static Tab makeStubTab(String title) {
    return new Tab(title, null, STUB_TAB_BACKGROUND, false);
  }

  /**
   * Create a standard tab
   */
  protected Tab(String title, @Nullable Icon icon, Color borderColor, boolean closeable) {
    setLayout(new BorderLayout());

    // Add the label
    label = new JBLabel(title);
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(5);
    }
    JPanel iconLabelPanel = new JPanel();
    iconLabelPanel.setOpaque(false);
    iconLabelPanel.setLayout(new GridBagLayout());
    iconLabelPanel.add(label, createGbc(0));
    add(iconLabelPanel, BorderLayout.WEST);

    popupMenu = new JPopupMenu();
    JMenuItem duplicatePanel = new JMenuItem("Duplicate Panel");
    duplicatePanel.setIcon(AllIcons.Actions.Diff);
    duplicatePanel.addActionListener(new DuplicatePanelActionListener());
    popupMenu.add(duplicatePanel);
    JMenuItem copyImage = new JMenuItem("Copy to Clipboard");
    copyImage.setIcon(AllIcons.Actions.Copy);
    copyImage.addActionListener(new CopyImageAction());
    popupMenu.add(copyImage);
    JMenuItem saveImage = new JMenuItem("Save Image ...");
    saveImage.setIcon(AllIcons.Actions.Menu_saveall);
    saveImage.addActionListener(new SaveImageAction());
    popupMenu.add(saveImage);

    // Add close label
    if (closeable) {
      JLabel closeLabel = new JLabel(closeIcon);
      JLabel dropMenuLabel = new JLabel(AllIcons.Actions.Down);
      dropMenuLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          popupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
      });

      closeLabel.setToolTipText("Close this screenshot");
      JPanel closeLabelPanel = new JPanel();
      closeLabelPanel.setOpaque(false);
      closeLabelPanel.setLayout(new GridBagLayout());
      closeLabelPanel.add(dropMenuLabel, createGbc(1));
      closeLabelPanel.add(closeLabel, createGbc(2));
      add(closeLabelPanel, BorderLayout.EAST);
      closeLabel.addMouseListener(new CloseButtonMouseListener());

    }

    // Set borders and colors
    if (closeable) {
      setBackground(BOTTOM_COLOR, TOP_COLOR);
      setBorder(new MatteBorder(1, 1, 0, 1, borderColor));
    } else {
      label.setForeground(STUB_TAB_FOREGROUND);
      setBackground(STUB_TAB_BACKGROUND);
      addMouseListener(new NonCloseableMouseListener());
    }

  }

  public void addTabEventListener(TabEventListener listener) {
    listeners.add(listener);
  }

  class NonCloseableMouseListener extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent e) {
      setBackground(BOTTOM_COLOR, TOP_COLOR);
      repaint();
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setBackground(STUB_TAB_BACKGROUND);
      repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      for (TabEventListener listener : listeners) {
        listener.openEvent();
      }
    }
  }

  class CloseButtonMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      for (TabEventListener listener : listeners) {
        listener.closeEvent();
      }
    }
  }

  private class DuplicatePanelActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      for (TabEventListener listener : listeners) {
        listener.openEvent();
      }
    }
  }

  private class SaveImageAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      for (TabEventListener listener : listeners) {
        listener.saveImage();
      }
    }
  }

  private class CopyImageAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      for (TabEventListener listener : listeners) {
        listener.copyImage();
      }
    }
  }

  private static GridBagConstraints createGbc(int x) {
    int y = 0;
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = x;
    gbc.gridy = y;
    gbc.insets = new Insets(2, 5, 2, 5);
    if (x == 0) {
      gbc.anchor = GridBagConstraints.WEST;
    } else {
      gbc.anchor = GridBagConstraints.EAST;
    }
    return gbc;
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        JFrame frame = new JFrame(Tab.class.getSimpleName());
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);

        Tab passedTab = makeStandardTab("Passed", TestPassed, GREEN);
        Tab newTab = makeStubTab("Compare");

        JPanel panel = new JPanel(new FlowLayout());

        frame.add(panel);
        panel.add(passedTab);
        panel.add(new JLabel("    "));
        panel.add(newTab);
        frame.setSize(400, 300);
        frame.setVisible(true);
      }
    });
  }
}
