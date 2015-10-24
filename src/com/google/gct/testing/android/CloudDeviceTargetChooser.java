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
package com.google.gct.testing.android;

import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.DeployTarget;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A target chooser for selecting and launching a cloud device.
 */
public class CloudDeviceTargetChooser {

  @NotNull private final AndroidFacet myFacet;
  private final int myCloudDeviceConfigurationId;
  @NotNull private final String myCloudDeviceProjectId;

  public CloudDeviceTargetChooser(@NotNull AndroidFacet facet,
                                  int cloudDeviceConfigurationId,
                                  @NotNull String cloudDeviceProjectId) {
    myFacet = facet;
    myCloudDeviceConfigurationId = cloudDeviceConfigurationId;
    myCloudDeviceProjectId = cloudDeviceProjectId;
  }

  @Nullable
  public DeployTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    // TODO: Actually launch the chosen device in here and return a DeviceTarget.
    //return new CloudDeviceLaunchTarget(myCloudDeviceConfigurationId, myCloudDeviceProjectId);
    return null;
  }

}
