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

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.screenshot.ScreenshotTask;
import com.android.uiautomator.UiAutomatorModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class TestRecorderScreenshotTask extends ScreenshotTask {
  private static final String UI_HIERARCHY_FAILURE_DIALOG_TITLE = "Failed to get UI hierarchy";

  private final Project myProject;
  private final IDevice myDevice;
  private final String myPackageName;
  private final ScreenshotCallback myCallback;
  private File myUiHierarchyLocalFile;
  private boolean success = false;

  public TestRecorderScreenshotTask(Project project, IDevice device, String packageName, ScreenshotCallback callback) {
    super(project, device);
    myProject = project;
    myDevice = device;
    myPackageName = packageName;
    myCallback = callback;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    super.run(indicator);
    String errorMessage = getError();
    if (errorMessage != null) {
      showErrorMessage(errorMessage, "Failed to capture screenshot");
      return;
    }

    if (indicator.isCanceled()) {
      return;
    }
    indicator.setText("Creating temporary file for UI hierarchy...");
    try {
      myUiHierarchyLocalFile = File.createTempFile("ui_hierarchy", ".xml");
    } catch (Exception e) {
      showErrorMessage("Could not create a temporary file for UI hierarchy: " + e.getMessage(), UI_HIERARCHY_FAILURE_DIALOG_TITLE);
      return;
    }
    myUiHierarchyLocalFile.deleteOnExit();

    if (indicator.isCanceled()) {
      return;
    }
    indicator.setText("Dumping UI hierarchy on the device...");
    String uiHierarchyRemoteContainerPath = String.format("/sdcard/Android/data/%s/files/testrecorder", myPackageName);
    String uiHierarchyRemotePath = uiHierarchyRemoteContainerPath + "/ui_hierarchy.xml";
    try {
      myDevice.executeShellCommand("mkdir -p " + uiHierarchyRemoteContainerPath, new CollectingOutputReceiver(), 3, TimeUnit.SECONDS);
      myDevice.executeShellCommand("uiautomator dump " + uiHierarchyRemotePath, new CollectingOutputReceiver(), 5, TimeUnit.SECONDS);
    } catch (Exception e) {
      showErrorMessage("Could not dump UI hierarchy on the device: " + e.getMessage(), UI_HIERARCHY_FAILURE_DIALOG_TITLE);
      return;
    }

    if (indicator.isCanceled()) {
      return;
    }
    indicator.setText("Pulling UI hierarchy from the device...");
    try {
      myDevice.pullFile(uiHierarchyRemotePath, myUiHierarchyLocalFile.getAbsolutePath());
    } catch (Exception e) {
      showErrorMessage("Could not pull UI hierarchy file from the device: " + e.getMessage(), UI_HIERARCHY_FAILURE_DIALOG_TITLE);
      return;
    }

    if (indicator.isCanceled()) {
      return;
    }
    success = true;
  }

  private void showErrorMessage(final String errorMessage, final String title) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myProject, errorMessage, title);
      }
    });
  }

  @Override
  public void onSuccess() {
    if (success) {
      myCallback.onSuccess(getScreenshot(), new UiAutomatorModel(myUiHierarchyLocalFile));
    }
  }
}
