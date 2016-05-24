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

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class BlankVncViewer extends JApplet implements Runnable {
  private final String myConfigurationName;
  private final BlankVncViewerCallback myBlankVncViewerCallback;
  private JFrame blankFrame;
  private WindowListener exitListener;

  public BlankVncViewer(String configurationName, BlankVncViewerCallback blankVncViewerCallback) {
    myConfigurationName = configurationName;
    myBlankVncViewerCallback = blankVncViewerCallback;
    exitListener = new WindowAdapter() {

      @Override
      public void windowClosing(WindowEvent e) {
        int confirm = JOptionPane.showOptionDialog(blankFrame,
                                                   "Are you sure you want to close the window? Closing the window will delete the Firebase instance.",
                                                   "Exit Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                                                   null, null);
        if (confirm == 0) {
          System.out.println("Exiting blank VNC Viewer");
          myBlankVncViewerCallback.viewerClosed();
          blankFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        }
      }
    };
  }

  @Override
  public void run() {
    blankFrame = new JFrame("Launching Firebase Device");
    blankFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    blankFrame.addWindowListener(exitListener);
    JLabel infoLabel = new JLabel("Launching " + myConfigurationName, SwingConstants.CENTER);
    infoLabel.setPreferredSize(new Dimension(600, 100));

    blankFrame.getContentPane().add(infoLabel, BorderLayout.CENTER);

    blankFrame.pack();
    blankFrame.setVisible(true);
  }

  public void closeWindow() {
    blankFrame.removeWindowListener(exitListener);
    blankFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    blankFrame.dispatchEvent(new WindowEvent(blankFrame, WindowEvent.WINDOW_CLOSING));
  }

  public static BlankVncViewer showBlankVncViewer(String configurationName, BlankVncViewerCallback blankVncViewerCallback) {
    BlankVncViewer blankViewer = new BlankVncViewer(configurationName, blankVncViewerCallback);
    SwingUtilities.invokeLater(blankViewer);
    return blankViewer;
  }
}
