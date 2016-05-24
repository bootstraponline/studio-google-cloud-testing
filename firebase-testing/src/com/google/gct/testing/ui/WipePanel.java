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
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.net.URL;

import static com.google.gct.testing.ui.WipePanel.State.*;


public class WipePanel extends JPanel {
  private long start;
  private float percentRevealed;

  private int fullHeight;
  private int fullWidth;
  private BufferedImage buffer;

  private State state = hidden;

  enum State {
    hidden,
    revealing,
    hiding,
    revealed
  }

  public WipePanel() {
    setLayout(new BorderLayout());
  }

  public void setContentPanel(JPanel contents) {
    add(contents);
  }

  @Override
  public void paint(Graphics g) {
    if (state == hidden) {
      this.setVisible(false);
    } else if (state == revealing || state == hiding) {
      this.setVisible(true);

      int width = (int)(fullWidth * percentRevealed);
      this.setSize(width, fullHeight);

      g.clearRect(0, 0, fullWidth, fullHeight);
      //((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, percentRevealed));
      g.drawImage(buffer, 0, 0, this);
    } else {
      this.setVisible(true);
      super.paint(g);
    }
  }

  public void instantReveal(@Nullable final com.google.gct.testing.ui.WipePanelCallback callBack) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        reveal(-1, callBack);
      }
    });
  }

  public void reveal(@Nullable final com.google.gct.testing.ui.WipePanelCallback callBack) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        reveal(300, callBack);
      }
    });
  }

  public void unReveal(@Nullable final com.google.gct.testing.ui.WipePanelCallback callBack) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        unReveal(300, callBack);
      }
    });
  }

  private void reveal(final int totalTimeMillis, @Nullable final com.google.gct.testing.ui.WipePanelCallback callBack) {
    start = System.currentTimeMillis();

    state = revealed;

    buffer = UIUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
    this.print(buffer.getGraphics()); // Draw the current components on the buffer.
    state = revealing;

    fullWidth = buffer.getWidth();
    fullHeight = buffer.getHeight();

    percentRevealed = 0.01f;

    final Timer t = new Timer(10, null);
    t.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > totalTimeMillis) {
          setSize(fullWidth, fullHeight);
          t.stop();
          state = revealed;
          if (callBack != null) {
            callBack.panelRevealed();
          }
        } else {
          percentRevealed = ((float) elapsed / totalTimeMillis);
        }
        repaint();
      }
    });
    t.start();
  }

  private void unReveal(final int millis, @Nullable final com.google.gct.testing.ui.WipePanelCallback callBack) {
    start = System.currentTimeMillis();

    state = revealed;
    buffer = UIUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
    this.print(buffer.getGraphics()); // Draw the current components on the buffer.
    state = hiding;

    fullWidth = buffer.getWidth();
    fullHeight = buffer.getHeight();

    percentRevealed = 0.99f;

    final WipePanel thisPanel = this;
    final Timer t = new Timer(10, null);
    t.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > millis) {
          t.stop();
          setSize(fullWidth, fullHeight);
          setVisible(false);
          state = hidden;
          if (callBack != null) {
            callBack.panelHidden(thisPanel);
          }
        } else {
          percentRevealed = 1f - ((float) elapsed / millis);
        }
        repaint();
      }
    });
    t.start();
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        try {
          JFrame frame = new JFrame(WipePanel.class.getSimpleName());
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

          JPanel mainPanel = new JPanel(new BorderLayout());
          frame.add(mainPanel);

          JPanel centerPanel = new JPanel(new FlowLayout());
          mainPanel.add(centerPanel, BorderLayout.CENTER);

          final WipePanel wipe = new WipePanel();
          centerPanel.add(wipe);

          JPanel contentsPanel = new JPanel();
          contentsPanel.setBorder(new MatteBorder(2,2,2,2,Color.BLUE));
          wipe.setContentPanel(contentsPanel);

          JTextField textfield = new JTextField(50);
          JLabel image = new JLabel(new ImageIcon(new URL("http://helios.gsfc.nasa.gov/image_mag_stamp.jpg")));

          contentsPanel.add(textfield);
          contentsPanel.add(image);

          JButton button = new JButton("Show");
          button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              wipe.reveal(null);
            }
          });

          JButton button2 = new JButton("Hide");
          button2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              wipe.unReveal(null);
            }
          });

          JPanel buttonPanel = new JPanel(new FlowLayout());
          buttonPanel.add(button);
          buttonPanel.add(button2);

          mainPanel.add(buttonPanel, BorderLayout.SOUTH);

          JLabel image2 = new JLabel(new ImageIcon(new URL("http://img2.wikia.nocookie.net/__cb20110226214654/logopedia/images/a/ab/Google_logo_Sept-Oct_1998.png")));
          centerPanel.add(image2);

          frame.pack();
          frame.setVisible(true);
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }
      }
    });
  }
}
