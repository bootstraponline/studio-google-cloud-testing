/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.gct.testing.vnc;

import com.glavsoft.viewer.Viewer;
import com.glavsoft.viewer.VncKeepAliveThread;
import com.glavsoft.viewer.cli.Parser;

import javax.swing.*;
import java.io.File;

import static com.google.gct.testing.launcher.CloudAuthenticator.getTest;

public class VncKeepAliveThreadImpl extends VncKeepAliveThread {
  private final Parser parser;
  private final String configurationName;
  private final String cloudProjectId;
  private final String cloudDeviceId;
  private final String deviceAddress;
  private final File workingDir;
  private volatile boolean hasCrashed = false;
  private Viewer currentViewer;


  public static void startVnc(String[] args, String configurationName, String cloudProjectId, String cloudDeviceId, String deviceAddress,
                              File workingDir) {
    Parser parser = Viewer.prepareParser(args);
    if (parser == null) {
      return;
    }
    new VncKeepAliveThreadImpl(parser, configurationName, cloudProjectId, cloudDeviceId, deviceAddress, workingDir).start();
  }

  public VncKeepAliveThreadImpl(Parser parser, String configurationName, String cloudProjectId, String cloudDeviceId, String deviceAddress,
                                File workingDir) {
    this.parser = parser;
    this.configurationName = configurationName;
    this.cloudProjectId = cloudProjectId;
    this.cloudDeviceId = cloudDeviceId;
    this.deviceAddress = deviceAddress;
    this.workingDir = workingDir;
  }

  @Override
  public void run() {
    try {
      currentViewer = new Viewer(this, parser, configurationName);
      SwingUtilities.invokeLater(currentViewer);

      while (!Thread.currentThread().isInterrupted() && deviceIsReady()) {
        try {
          getTest().projects().devices().keepalive(cloudProjectId, cloudDeviceId).execute();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        // Restart the viewer if it accidentally crashed.
        if (hasCrashed) {
          hasCrashed = false;
          System.out.println("Restarting TightVNC Viewer");
          currentViewer = new Viewer(this, parser, configurationName);
          SwingUtilities.invokeLater(currentViewer);
        }
        try {
          Thread.sleep(1 * 1000); // 1 second
        }
        catch (InterruptedException e) {
          break;
        }
      }
    } finally {
      tearDown();
    }
  }

  private void tearDown() {
    // Perform tearing down in separate try blocks to make sure that every step gets a chance to be executed.
    try {
      // Disconnect adb from the deleted device (otherwise, it will keep showing the stale cloud device).
      File adbFile = new File(workingDir, System.getProperty("os.name").toLowerCase().indexOf("win") != -1 ? "adb.exe" : "adb");
      Runtime.getRuntime().exec(new String[]{adbFile.getAbsolutePath(), "disconnect", deviceAddress}, null, workingDir);
    } catch (Exception exception) {
      exception.printStackTrace();
    }

    try {
      // Delete the cloud device after the viewer is closed.
      getTest().projects().devices().delete(cloudProjectId, cloudDeviceId).execute();
    } catch (Exception exception) {
      exception.printStackTrace();
    }

    try {
      //Stop the viewer in case we are tearing down because of the device not being ready. Do it as the last step since it can preclude
      //other steps from being executed if the viewer was closed manually.
      currentViewer.stopViewer();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private boolean deviceIsReady() {
    try {
      return getTest().projects().devices().get(cloudProjectId, cloudDeviceId).execute().getState().equals("READY");
    } catch (Exception e) {
      return true; // Do not stop the keep alive thread for intermittent connection failures.
    }
  }

  @Override
  public void setCrashed() {
    hasCrashed = true;
  }

}
