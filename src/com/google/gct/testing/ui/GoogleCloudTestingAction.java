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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;

import javax.swing.*;

public class GoogleCloudTestingAction extends AnAction implements CustomComponentAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    showPopup(e);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return new GoogleCloudTestingActionButton(this, presentation, presentation.getText(), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  /**
   * Opens up the Google Login panel as a popup.
   */
  private void showPopup(AnActionEvent e) {
    System.out.println("showPopup!!!");
  }

}

