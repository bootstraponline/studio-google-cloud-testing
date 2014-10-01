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

import com.intellij.openapi.components.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

@State(
    name = "GoogleCloudTestingDefaultPersistentConfigurations",
    storages = {
      @Storage(file = "$MODULE_FILE$")
    }
)
public class GoogleCloudTestingDefaultPersistentConfigurations implements PersistentStateComponent<GoogleCloudTestingPersistentState> {

  private GoogleCloudTestingPersistentState myGoogleCloudTestingPersistentState;

  protected GoogleCloudTestingDefaultPersistentConfigurations() {
  }

  public static GoogleCloudTestingDefaultPersistentConfigurations getInstance(Module module) {
    return ModuleServiceManager.getService(module, GoogleCloudTestingDefaultPersistentConfigurations.class);
  }

  @Nullable
  @Override
  public GoogleCloudTestingPersistentState getState() {
    if (myGoogleCloudTestingPersistentState == null) {
      myGoogleCloudTestingPersistentState = new GoogleCloudTestingPersistentState();
    }
    return myGoogleCloudTestingPersistentState;
  }

  @Override
  public void loadState(GoogleCloudTestingPersistentState googleCloudPersistenState) {
    this.myGoogleCloudTestingPersistentState = googleCloudPersistenState;
  }
}
