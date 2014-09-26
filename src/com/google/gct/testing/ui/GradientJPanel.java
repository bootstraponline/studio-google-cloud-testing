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

import javax.swing.*;
import java.awt.*;

/**
 * A JPanel with a setBackground method that takes two colors to use for a vertical gradient.
 */
public class GradientJPanel extends JPanel {
  // True if this panel is currently using a gradient background
  private boolean useGradient = false;

  private Color bottomColor = null;
  private Color topColor = null;

  @Override
  public void setBackground(Color color) {
    super.setBackground(color);
    useGradient = false;
  }

  public void setBackground(Color bottomColor, Color topColor) {
    this.bottomColor = bottomColor;
    this.topColor = topColor;
    useGradient = true;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (useGradient) {
      Graphics2D g2d = (Graphics2D)g;
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      int w = getWidth();
      int h = getHeight();
      GradientPaint gp = new GradientPaint(0, 0, topColor, 0, h, bottomColor);
      g2d.setPaint(gp);
      g2d.fillRect(0, 0, w, h);
    }
  }
}
