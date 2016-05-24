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
package com.google.gct.testing.results;

import com.intellij.icons.AllIcons;

import javax.swing.*;

public abstract class CloudMatrixProgressAnimator {
  private static final int FRAMES_COUNT = 12;
  private static final int MOVIE_TIME = 1200;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  private static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  static {
    FRAMES[0] = AllIcons.Process.Step_1;
    FRAMES[1] = AllIcons.Process.Step_2;
    FRAMES[2] = AllIcons.Process.Step_3;
    FRAMES[3] = AllIcons.Process.Step_4;
    FRAMES[4] = AllIcons.Process.Step_5;
    FRAMES[5] = AllIcons.Process.Step_6;
    FRAMES[6] = AllIcons.Process.Step_7;
    FRAMES[7] = AllIcons.Process.Step_8;
    FRAMES[8] = AllIcons.Process.Step_9;
    FRAMES[9] = AllIcons.Process.Step_10;
    FRAMES[10] = AllIcons.Process.Step_11;
    FRAMES[11] = AllIcons.Process.Step_12;
  }

  public static Icon getCurrentFrame() {
    return FRAMES[((int)((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME))];
  }

}
