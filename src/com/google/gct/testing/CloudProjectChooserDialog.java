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
package com.google.gct.testing;

import com.google.gct.idea.elysium.ProjectSelector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

public class CloudProjectChooserDialog extends DialogWrapper {

  private JPanel myPanel;
  private ProjectSelector myProjectSelector;


  public CloudProjectChooserDialog(Project project, String initiallySelectedProject) {

    super(project, true);

    setTitle("Choose Cloud Project");

    myProjectSelector = new ProjectSelector();
    myProjectSelector.setText(initiallySelectedProject);

    myPanel.add(myProjectSelector, BorderLayout.NORTH);

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public String getSelectedProject() {
    return myProjectSelector.getText();
  }
}
