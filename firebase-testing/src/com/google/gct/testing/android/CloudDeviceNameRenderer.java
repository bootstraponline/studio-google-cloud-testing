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
import com.android.tools.idea.ddms.DeviceNameRendererEx;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import static com.google.gct.testing.CloudConfigurationHelper.getCloudDeviceConfiguration;
import static com.google.gct.testing.CloudConfigurationHelper.getCloudDeviceIcon;

public class CloudDeviceNameRenderer implements DeviceNameRendererEx {

  @Override
  public boolean isApplicable(@NotNull IDevice device) {
    return getCloudDeviceConfiguration(device) != null;
  }

  @Override
  public void render(@NotNull IDevice device, @NotNull ColoredTextContainer component) {
    component.setIcon(getCloudDeviceIcon());
    String cloudDeviceConfiguration = getCloudDeviceConfiguration(device);

    if (device.getState() == IDevice.DeviceState.OFFLINE) {
      component.append("Launching " + cloudDeviceConfiguration, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    } else {
      component.append(cloudDeviceConfiguration, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  @NotNull
  @Override
  public String getName(@NotNull IDevice device) {
    String deviceName = getCloudDeviceConfiguration(device);
    if (deviceName == null) { // It means this method was invoked without first checking whether the renderer is applicable.
      throw new IllegalArgumentException("This device name renderer is not applicable to the given device: " + device);
    }
    return deviceName;
  }
}
