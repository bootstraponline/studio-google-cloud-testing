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
package com.google.gct.testing.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.jetbrains.annotations.Nullable;

@State(
    name = "GoogleCloudTestingDeveloperSettings",
    storages = {
      @Storage(file = StoragePathMacros.PROJECT_FILE),
      @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gct_developer_settings.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GoogleCloudTestingDeveloperSettings implements PersistentStateComponent<GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState> {

  private final PsiModificationTrackerImpl myTracker;

  private GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState myGoogleCloudTestingDeveloperState;

  protected GoogleCloudTestingDeveloperSettings(Project project) {
    myTracker = (PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker();
  }

  public static GoogleCloudTestingDeveloperSettings getInstance(Project project) {
    return ServiceManager.getService(project, GoogleCloudTestingDeveloperSettings.class);
  }

  @Nullable
  @Override
  public GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState getState() {
    if (myGoogleCloudTestingDeveloperState == null) {
      myGoogleCloudTestingDeveloperState = new GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState();
    }
    return myGoogleCloudTestingDeveloperState;
  }

  @Override
  public void loadState(GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState state) {
    GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState oldState = myGoogleCloudTestingDeveloperState;
    myGoogleCloudTestingDeveloperState = state;
    if (oldState != null) {
      myTracker.incCounter();
    }
  }

}
