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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * The Google Cloud Testing button that appears on the main toolbar.
 */
public final class GoogleCloudTestingActionButton extends ActionButton {

  private final static String SHOW_GOOGLE_CLOUD_TESTING_BUTTON_PROPERTY = "show.google.cloud.testing.button";


  public GoogleCloudTestingActionButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
    super(action, presentation, place, minimumSize);

    boolean showTestingButton = Boolean.getBoolean(SHOW_GOOGLE_CLOUD_TESTING_BUTTON_PROPERTY);
    if(!showTestingButton) {
      setVisible(false);
      return;
    }

    updateUi();
  }

  /**
   * Updates the buttons tooltip and description text.
   */
  public void updateUi() {
    myPresentation.setDescription("Hi Google Cloud Testing!");
    myPresentation.setIcon(AllIcons.Actions.Checked);
  }
}
