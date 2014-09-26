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

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.beans.ConstructorProperties;

/**
 * Duplicated here to avoid API compatability issues
 */
public class StrokeBorder extends AbstractBorder {
  private final BasicStroke stroke;
  private final Paint paint;

  /**
   * Creates a border of the specified {@code stroke}.
   * The component's foreground color will be used to render the border.
   *
   * @param stroke  the {@link java.awt.BasicStroke} object used to stroke a shape
   *
   * @throws NullPointerException if the specified {@code stroke} is {@code null}
   */
  public StrokeBorder(BasicStroke stroke) {
    this(stroke, null);
  }

  /**
   * Creates a border of the specified {@code stroke} and {@code paint}.
   * If the specified {@code paint} is {@code null},
   * the component's foreground color will be used to render the border.
   *
   * @param stroke  the {@link java.awt.BasicStroke} object used to stroke a shape
   * @param paint   the {@link java.awt.Paint} object used to generate a color
   *
   * @throws NullPointerException if the specified {@code stroke} is {@code null}
   */
  @ConstructorProperties({ "stroke", "paint" })
  public StrokeBorder(BasicStroke stroke, Paint paint) {
    if (stroke == null) {
      throw new NullPointerException("border's stroke");
    }
    this.stroke = stroke;
    this.paint = paint;
  }

  /**
   * Paints the border for the specified component
   * with the specified position and size.
   * If the border was not specified with a {@link java.awt.Paint} object,
   * the component's foreground color will be used to render the border.
   * If the component's foreground color is not available,
   * the default color of the {@link java.awt.Graphics} object will be used.
   *
   * @param c       the component for which this border is being painted
   * @param g       the paint graphics
   * @param x       the x position of the painted border
   * @param y       the y position of the painted border
   * @param width   the width of the painted border
   * @param height  the height of the painted border
   *
   * @throws NullPointerException if the specified {@code g} is {@code null}
   */
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    float size = this.stroke.getLineWidth();
    if (size > 0.0f) {
      g = g.create();
      if (g instanceof Graphics2D) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(this.stroke);
        g2d.setPaint(this.paint != null ? this.paint : c == null ? null : c.getForeground());
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.draw(new Rectangle2D.Float(x + size / 2, y + size / 2, width - size, height - size));
      }
      g.dispose();
    }
  }

  /**
   * Reinitializes the {@code insets} parameter
   * with this border's current insets.
   * Every inset is the smallest (closest to negative infinity) integer value
   * that is greater than or equal to the line width of the stroke
   * that is used to paint the border.
   *
   * @param c       the component for which this border insets value applies
   * @param insets  the {@code Insets} object to be reinitialized
   * @return the reinitialized {@code insets} parameter
   *
   * @throws NullPointerException if the specified {@code insets} is {@code null}
   *
   * @see Math#ceil
   */
  @Override
  public Insets getBorderInsets(Component c, Insets insets) {
    int size = (int) Math.ceil(this.stroke.getLineWidth());
    insets.set(size, size, size, size);
    return insets;
  }

  /**
   * Returns the {@link java.awt.BasicStroke} object used to stroke a shape
   * during the border rendering.
   *
   * @return the {@link java.awt.BasicStroke} object
   */
  public BasicStroke getStroke() {
    return this.stroke;
  }

  /**
   * Returns the {@link java.awt.Paint} object used to generate a color
   * during the border rendering.
   *
   * @return the {@link java.awt.Paint} object or {@code null}
   *         if the {@code paint} parameter is not set
   */
  public Paint getPaint() {
    return this.paint;
  }
}
