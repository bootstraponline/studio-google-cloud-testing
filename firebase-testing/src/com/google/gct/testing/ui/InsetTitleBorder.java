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
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * A Matte border with title text inset into the top
 */
public class InsetTitleBorder implements Border {

  private final Border myBorder;
  private final String myTitle;
  private final Icon myIcon;

  public InsetTitleBorder(String title, Icon icon, Color color) {
    myTitle = title;
    myIcon = icon;
    myBorder = new MatteBorder(20, 2, 2, 2, color);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    myBorder.paintBorder(c, g, x, y, width, height);

    if (g instanceof Graphics2D) {
      Graphics2D g2d = (Graphics2D)g;

      myIcon.paintIcon(c,g,5,2);

      g2d.setColor(Color.white);
      g2d.setFont(new Font("Sans", Font.BOLD, 12));
      g2d.drawString(myTitle, 30, 15);
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return myBorder.getBorderInsets(c);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
