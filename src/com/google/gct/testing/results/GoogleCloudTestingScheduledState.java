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
package com.google.gct.testing.results;

import com.intellij.execution.testframework.sm.runner.states.AbstractState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GoogleCloudTestingScheduledState extends AbstractState {
  private final GoogleCloudTestProxy myTestProxy;
  private boolean myDefectFound = false;
  private boolean isActive = false;

  public GoogleCloudTestingScheduledState(@NotNull final GoogleCloudTestProxy testProxy) {
    myTestProxy = testProxy;
  }

  public void makeActive() {
    isActive = true;
  }

  public boolean isActive() {
    return isActive;
  }

  @Override
  public boolean wasLaunched() {
    return false;
  }

  @Override
  public boolean isFinal() {
    return false;
  }

  @Override
  public boolean isInProgress() {
    return false;
  }

  @Override
  public boolean isDefect() {
    if (myDefectFound) {
      return true;
    }

    //Test suit fails if any of its tests fails
    final List<? extends GoogleCloudTestProxy> children = myTestProxy.getChildren();
    for (GoogleCloudTestProxy child : children) {
      if (child.isDefect()) {
        myDefectFound = true;
        return true;
      }
    }

    //cannot cache because one of child tests may fail in future
    return false;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.SCHEDULED_INDEX;
  }

  @Override
  public String toString() {
    return "SCHEDULED";
  }
}
