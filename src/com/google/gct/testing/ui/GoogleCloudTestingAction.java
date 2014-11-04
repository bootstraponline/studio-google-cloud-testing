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

import com.google.gct.testing.launcher.ExperimentalCloudAuthenticator;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;

import javax.swing.*;

public class GoogleCloudTestingAction extends AnAction implements CustomComponentAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    try {
      //ExperimentalCloudAuthenticator.discoverApi();
      //ExperimentalCloudAuthenticator.discoverApi("sqladmin", "v1beta1");
      //ExperimentalCloudAuthenticator.discoverApi("androidpublisher", "v2");
      //ExperimentalCloudAuthenticator.discoverApi("drive", "v2");
      //ExperimentalCloudAuthenticator.discoverApi("storage", "v1");
      //ExperimentalCloudAuthenticator.call("androidpublisher", "v2", "inappproducts.list", "my.package.name", "--maxResults", "5", "--startIndex", "0");
      //String response = ExperimentalCloudAuthenticator.call("drive", "v2", "apps.list");
      //String response = ExperimentalCloudAuthenticator.call("sqladmin", "v1beta1", "instances.list", "cloud-test-infra");
      //ExperimentalCloudAuthenticator.call("storage", "v1", "objects.list", "a-build-mytracks-fake2");
      //String response = ExperimentalCloudAuthenticator.call("storage", "v1", "bucketAccessControls.list", "a-build-mytracks-fake2");
      //String response = ExperimentalCloudAuthenticator.callTestApi("test_devtools", "v1", "projects.testMatrices.list", "projectId", "12345");
      //new Notification("Cloud API", "test_devtools v1 projects.testMatrices.list myProjectId", response, NotificationType.INFORMATION,
      //                 null).notify(e.getProject());
      String response = ExperimentalCloudAuthenticator.callTestApi();
      new Notification("Cloud API", "testExecutions.list", response, NotificationType.INFORMATION, null).notify(e.getProject());
    }
    catch (Exception exception) {
      throw new RuntimeException("Failed to perform the API call", exception);
    }
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return new GoogleCloudTestingActionButton(this, presentation, presentation.getText(), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }

}

