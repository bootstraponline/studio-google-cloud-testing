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

import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.android.tools.idea.stats.UsageTracker;
import com.google.gct.testing.android.CloudConfiguration;
import com.google.gct.testing.util.CloudTestingTracking;
import com.intellij.icons.AllIcons;
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
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public class CloudTestingUtils {

  //private static final String GOOGLE_GROUP_URL = "'https://groups.google.com/a/google.com/forum/#!newtopic/cloud-test-lab-users-external'";
  private static final String GOOGLE_GROUP_URL = "'https://groups.google.com/forum/#!newtopic/google-cloud-test-lab-external'";

  private static final String SHOW_GOOGLE_CLOUD_TESTING_TIMESTAMPS = "show.google.cloud.testing.timestamps";

  //GCT-specific message names.
  public static final String SET_TEST_RUN_ID = "setTestRunId";
  public static final String SET_ACTIVE_CLOUD_MATRIX = "setActiveCloudMatrix";
  public static final String TEST_CONFIGURATION_STOPPED = "testConfigurationStopped";
  public static final String TEST_CONFIGURATION_STARTED = "testConfigurationStarted";
  public static final String TEST_CONFIGURATION_PROGRESS = "testConfigurationProgress";
  public static final String TEST_CONFIGURATION_SCHEDULED = "testConfigurationScheduled";
  public static final String TEST_CONFIGURATION_FINISHED = "testConfigurationFinished";

  public static Icon CLOUD_DEVICE_ICON;
  public static Icon CLOUD_DEBUG_ICON;

  static {
    try {
      CLOUD_DEBUG_ICON = new ImageIcon(ImageIO.read(CloudTestingUtils.class.getResourceAsStream("CloudDebug.png")));
    }
    catch (Exception e) { // If something goes wrong, just use the original debug icon.
      CLOUD_DEBUG_ICON = AllIcons.General.Debug;
    }
    try {
      CLOUD_DEVICE_ICON = new ImageIcon(ImageIO.read(CloudTestingUtils.class.getResourceAsStream("CloudDevice.png")));
    } catch (Exception e) { // If something goes wrong, just use the default device icon.
      CLOUD_DEVICE_ICON = AndroidIcons.Views.DeviceScreen;
    }
  }


  public static enum ConfigurationStopReason {
    FINISHED, INFRASTRUCTURE_FAILURE, TRIGGERING_ERROR, TIMED_OUT
  }

  public static boolean shouldShowProgressTimestamps() {
    return Boolean.getBoolean(SHOW_GOOGLE_CLOUD_TESTING_TIMESTAMPS);
  }

  public static CloudConfigurationImpl getConfigurationById(int id, AndroidFacet facet) {
    for (CloudConfiguration configuration : CloudConfigurationHelper.getAllCloudConfigurations(facet)) {
      if (configuration.getId() == id) {
        return (CloudConfigurationImpl) configuration;
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

  /**
   * Returns {@code false} iff the Java version is too old for launching cloud devices (i.e., < 1.8).
   *
   */
  public static boolean checkJavaVersion() {
    String javaVersion = System.getProperty("java.version");
    String[] versionParts = javaVersion.split("\\.");
    if (Double.parseDouble(versionParts[0] + "." + versionParts[1]) < 1.8) {
      final String message = "<html>You are using Java <b>" + javaVersion + "</b>.<br>"
                             + "Due to security reasons, to launch cloud devices, you need to upgrade to Java <b>1.8</b> or higher.<br>"
                             + "You can download the latest Java release from <a href='https://java.com'>here</a>.</html>";
      final Project project = null;
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages
            .showDialog(project, message, "Your Java is too old for launching cloud devices!", new String[]{Messages.CANCEL_BUTTON}, 0, null);
        }
      });
      return false;
    }
    return true;
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
                                    + getDetailedErrorMessage(errorMessage.substring(newLineIndex + 1)) + "</html>"
                                  : "No details...";
    UsageTracker.getInstance().trackEvent(CloudTestingTracking.CLOUD_TESTING, CloudTestingTracking.BACKEND_ERROR,
                                          CloudTestingTracking.SESSION_LABEL + "|" + userErrorMessage, null);
    showCascadingErrorMessages(project, errorDialogTitle, userErrorMessage, detailedErrorMessage);
  }

  private static String getDetailedErrorMessage(String errorMessage) {
    String debugInfoField = "\"debugInfo\" : \""; // Available for internal runs only.
    int debugInfoIndex = errorMessage.indexOf(debugInfoField);
    if (debugInfoIndex != -1) {
      int debugInfoStartIndex = debugInfoIndex + debugInfoField.length();
      int debugInfoEndIndex = errorMessage.indexOf("\"", debugInfoStartIndex);
      if (debugInfoEndIndex != -1) {
        String debugInfo = errorMessage.substring(debugInfoStartIndex, debugInfoEndIndex);
        errorMessage = errorMessage.substring(0, debugInfoIndex) + errorMessage.substring(debugInfoEndIndex + 2);
        String unescapedDebugInfo = debugInfo.replace("\\n", "\n").replace("\\t", "\t");
        errorMessage += "\n\n" + "DEBUG INFO:\n" + unescapedDebugInfo;
      }
    }
    return errorMessage.replace("\n", "<br>").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
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
