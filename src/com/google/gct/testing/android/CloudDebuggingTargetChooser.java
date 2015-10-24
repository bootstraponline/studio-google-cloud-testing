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


import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.*;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class CloudDebuggingTargetChooser {
  @NotNull
  private final String myCloudDeviceSerialNumber;

  public CloudDebuggingTargetChooser(@NotNull String cloudDeviceSerialNumber) {
    myCloudDeviceSerialNumber = cloudDeviceSerialNumber;
  }

  @NotNull
  public DeviceTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    // TODO: Prompt the user to launch a cloud device if none found.
    // TODO: Assert that we don't get multiple devices out here?
    return DeviceTarget.forDevices(DeviceSelectionUtils.getAllCompatibleDevices(new CloudDebuggingFilter(myCloudDeviceSerialNumber)));
  }

  @NotNull
  public List<ValidationError> validate() {
    return ImmutableList.of();
  }

  public static class CloudDebuggingFilter extends TargetDeviceFilter {
    @NotNull
    private final String myCloudDeviceSerialNumber;

    public CloudDebuggingFilter(@NotNull String cloudDeviceSerialNumber) {
      myCloudDeviceSerialNumber = cloudDeviceSerialNumber;
    }

    @Override
    public boolean matchesDevice(@NotNull IDevice device) {
      return device.getSerialNumber().equals(myCloudDeviceSerialNumber);
    }
  }

}
