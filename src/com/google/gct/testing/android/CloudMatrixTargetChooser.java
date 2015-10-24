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
 * A target chooser for picking cloud matrix test deploy targets.
 */
public class CloudMatrixTargetChooser {

  @NotNull private final AndroidFacet myFacet;
  private final int myMatrixConfigurationId;
  @NotNull private final String myCloudProjectId;
  @NotNull private final ManualTargetChooser myFallback;

  public CloudMatrixTargetChooser(@NotNull AndroidFacet facet,
                                  int matrixConfigurationId,
                                  @NotNull String cloudProjectId,
                                  @NotNull ManualTargetChooser fallback) {
    myFacet = facet;
    myMatrixConfigurationId = matrixConfigurationId;
    myCloudProjectId = cloudProjectId;
    myFallback = fallback;
  }

  @Nullable
  public DeployTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    //if (debug) {
    //  // It does not make sense to debug a matrix of devices on the cloud. In debug mode, fall back to manual chooser.
    //  // TODO: Consider making the debug executor unavailable in this case rather than popping the extended chooser dialog.
    //  return myFallback.getTarget(printer, deviceCount, debug);
    //}
    //return new CloudMatrixTarget(myMatrixConfigurationId, myCloudProjectId);
    return null;
  }

}
