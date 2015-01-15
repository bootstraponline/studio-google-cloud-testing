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

import com.android.tools.idea.run.GoogleCloudTestingConfiguration;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public class GoogleCloudTestingUtils {

  private static final String GOOGLE_GROUP_URL = "'https://groups.google.com/a/google.com/forum/#!newtopic/cloud-test-lab-users-external'";

  private static final String SHOW_GOOGLE_CLOUD_TESTING_TIMESTAMPS = "show.google.cloud.testing.timestamps";

  private static final GoogleCloudTestingConfigurationFactoryImpl TESTING_CONFIGURATION_FACTORY =
    new GoogleCloudTestingConfigurationFactoryImpl();

  //GCT-specific message names.
  public static final String SET_TEST_RUN_ID = "setTestRunId";
  public static final String TEST_CONFIGURATION_STOPPED = "testConfigurationStopped";
  public static final String TEST_CONFIGURATION_STARTED = "testConfigurationStarted";
  public static final String TEST_CONFIGURATION_PROGRESS = "testConfigurationProgress";
  public static final String TEST_CONFIGURATION_SCHEDULED = "testConfigurationScheduled";
  public static final String TEST_CONFIGURATION_FINISHED = "testConfigurationFinished";

  public static enum ConfigurationStopReason {
    FINISHED, INFRASTRUCTURE_FAILURE, TIMED_OUT
  }

  public static boolean shouldShowProgressTimestamps() {
    return Boolean.getBoolean(SHOW_GOOGLE_CLOUD_TESTING_TIMESTAMPS);
  }

  public static GoogleCloudTestingConfigurationImpl getConfigurationById(int id, AndroidFacet facet) {
    for (GoogleCloudTestingConfiguration configuration : TESTING_CONFIGURATION_FACTORY.getCustomConfigurationsFromStorage(facet)) {
      if (configuration.getId() == id) {
        return (GoogleCloudTestingConfigurationImpl) configuration;
      }
    }
    for (GoogleCloudTestingConfiguration configuration : TESTING_CONFIGURATION_FACTORY.getDefaultConfigurationsFromStorage(facet)) {
      if (configuration.getId() == id) {
        return (GoogleCloudTestingConfigurationImpl) configuration;
      }
    }
    return null;
  }

  public static String prepareTestSpecification(AndroidTestRunConfiguration testRunConfiguration) {
    switch (testRunConfiguration.TESTING_TYPE) {
      case AndroidTestRunConfiguration.TEST_METHOD :
        return "class " + testRunConfiguration.CLASS_NAME + "#" + testRunConfiguration.METHOD_NAME;
      case AndroidTestRunConfiguration.TEST_CLASS :
        return "class " + testRunConfiguration.CLASS_NAME;
      case AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE :
        return "package " + testRunConfiguration.PACKAGE_NAME;
      case AndroidTestRunConfiguration.TEST_ALL_IN_MODULE :
        return "";
      default:
        throw new IllegalStateException("Unsupported testing type: " + testRunConfiguration.TESTING_TYPE);
    }
  }

  public static void showBalloonMessage(final Project project, final String message, final MessageType type, final int delaySeconds) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, type, null).setFadeoutTime(delaySeconds * 1000).createBalloon()
          .show(RelativePoint.getCenterOf(statusBar.getComponent()), Balloon.Position.atRight);
      }
    });
  }

  public static void showErrorMessage(@Nullable Project project, String errorDialogTitle, String errorMessage) {
    int newLineIndex = errorMessage.indexOf("\n");
    String userErrorMessage = newLineIndex != -1 ? errorMessage.substring(0, newLineIndex) : errorMessage;
    String detailedErrorMessage = newLineIndex != -1
                                  ? "<html><a href=" + GOOGLE_GROUP_URL
                                    + ">Report this issue</a> (please copy/paste the text below into the form)<br><br>"
                                    + errorMessage.substring(newLineIndex + 1).replace("\n", "<br>") + "</html>"
                                  : "No details...";
    showCascadingErrorMessages(project, errorDialogTitle, userErrorMessage, detailedErrorMessage);
  }

  private static void showCascadingErrorMessages(@Nullable final Project project, final String errorDialogTitle, String userErrorMessage,
                                                 final String detailedErrorMessage) {

    new Notification(errorDialogTitle, "", String.format("<b>%s</b> <a href=''>Details</a>", userErrorMessage), NotificationType.ERROR,
      new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();
          Messages.showDialog(project, detailedErrorMessage, errorDialogTitle, new String[]{Messages.CANCEL_BUTTON}, 0, null);
        }
      }).notify((project == null || project.isDefault()) ? null : project);
  }

  public static GridBagConstraints createConfigurationChooserGbc(int x, int y) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = x;
    gbc.gridy = y;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;

    gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.EAST;
    gbc.fill = (x == 0) ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;

    gbc.insets = (x == 0 && y == 0) ? new Insets(5, 7, 5, 5) : new Insets(5, 5, 5, 5);
    gbc.weightx = (x == 0) ? 0.1 : 1.0;
    gbc.weighty = 0.0;
    return gbc;
  }

  public static GridBagConstraints createCloudTestOptionGbc(int x, int y, boolean isExtendedDeviceChooserDialog) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = x;
    gbc.gridy = y;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;

    gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.WEST;
    gbc.fill = (x == 0) ? GridBagConstraints.BOTH : GridBagConstraints.BOTH;

    int leftFirstInsets = isExtendedDeviceChooserDialog ? 21 : 20;
    int leftSecondInsets = isExtendedDeviceChooserDialog ? 49 : 58;
    gbc.insets = (x == 0) ? new Insets(0, leftFirstInsets, 0, 0) : new Insets(0, leftSecondInsets, 0, 0);
    gbc.weightx = (x == 0) ? 0.0 : 1.0;
    gbc.weighty = 0.0;
    return gbc;
  }

  public static Color makeDarker(Color color, int shades) {
    if (shades < 1) {
      return color;
    }
    return makeDarker(UIUtil.getSlightlyDarkerColor(color), shades - 1);
  }
}
