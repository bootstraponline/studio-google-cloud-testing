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

import com.google.gct.testrecorder.event.TestRecorderAssertion;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import groovy.json.StringEscapeUtils;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.isUnderDarcula;

public class TestRecorderListRenderer extends DefaultListCellRenderer {
  private static final String OUTER_DIV_STYLE = "style='margin: 0; color: %s; padding: 2px 12px'";
  private static final String INNER_DIV_STYLE = "style='margin-top: 4px; display: block; border-left: 2px solid #dddddd; padding-left: 8px; margin-left: 16px'";

  private static final String TEXT_CHANGE_EVENT_FORMAT = "<html><div " + OUTER_DIV_STYLE + ">Type %s <div " + INNER_DIV_STYLE + ">%s</div></div></html>";
  private static final String CLICK_EVENT_FORMAT = "<html><div " + OUTER_DIV_STYLE + ">Tap %s</div></html>";
  private static final String LONG_CLICK_EVENT_FORMAT = "<html><div " + OUTER_DIV_STYLE + ">Long Tap %s</div></html>";
  private static final String PRESS_EVENT_FORMAT = "<html><div " + OUTER_DIV_STYLE + ">Press %s</div></html>";
  private static final String SWIPE_EVENT_FORMAT = "<html><div " + OUTER_DIV_STYLE + ">Swipe %s</div></html>";
  private static final String DELAYED_MESSAGE_POST_EVENT_FORMAT = "<html><div " + OUTER_DIV_STYLE + ">Delay %s milliseconds</div></html>";
  private static final String ASSERTION_FORMAT_ONE_LINE = "<html><div " + OUTER_DIV_STYLE + ">Assert %s %s" + "</div></html>";
  private static final String ASSERTION_FORMAT_MULTI_LINE = "<html><div " + OUTER_DIV_STYLE + ">Assert %s %s<div " + INNER_DIV_STYLE + ">%s</div></div></html>";


  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    final String outerDivTextColor = isUnderDarcula() ? "#cccccc" : "#333333";

    String renderString;
    if (value instanceof TestRecorderEvent) {
      TestRecorderEvent event = (TestRecorderEvent) value;
      if (event.isTextChange()) {
        renderString = String.format(TEXT_CHANGE_EVENT_FORMAT, outerDivTextColor, event.getRendererString(),
                                     StringEscapeUtils.escapeJava(event.getReplacementText()));
      } else if (event.isPressEvent()) {
        renderString = String.format(PRESS_EVENT_FORMAT, outerDivTextColor, event.getRendererString());
      } else if (event.isViewLongClick()) {
        renderString = String.format(LONG_CLICK_EVENT_FORMAT, outerDivTextColor, event.getRendererString());
      } else if (event.isSwipe()) {
        renderString = String.format(SWIPE_EVENT_FORMAT, outerDivTextColor, event.getRendererString());
      } else if (event.isDelayedMessagePost()) {
        renderString = String.format(DELAYED_MESSAGE_POST_EVENT_FORMAT, outerDivTextColor, event.getRendererString());
      } else {
        // Click event.
        renderString = String.format(CLICK_EVENT_FORMAT, outerDivTextColor, event.getRendererString());
      }
    } else if (value instanceof TestRecorderAssertion) {
      // Assertion
      TestRecorderAssertion assertion = (TestRecorderAssertion) value;
      String assertedText = assertion.getText();
      if (assertedText == null) {
        renderString = String.format(ASSERTION_FORMAT_ONE_LINE, outerDivTextColor, assertion.getRendererString(), assertion.getRule());
      } else {
        renderString = String.format(ASSERTION_FORMAT_MULTI_LINE, outerDivTextColor, assertion.getRendererString(), assertion.getRule(),
                                     StringEscapeUtils.escapeJava(assertedText));
      }
    } else {
      throw new RuntimeException("Unsupported Test Recorder entity: " + value.toString());
    }

    Component component = super.getListCellRendererComponent(list, renderString, index, false, cellHasFocus);

    // Change background color such that adjacent elements have different background.
    if (index % 2 != 0) {
      if (isUnderDarcula()) {
        component.setBackground(Color.decode("#333333"));
      } else {
        component.setBackground(Color.decode("#f6f6f6"));
      }
    }

    // Change background color for assertions (overrides adjacency color scheme).
    if (value instanceof TestRecorderAssertion) {
      if (isUnderDarcula()) {
        component.setBackground(Color.decode("#505050"));
      } else {
        component.setBackground(Color.decode("#fffae6"));
      }
    }

    // Set padding.
    ((JLabel)component).setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    return component;
  }
}
