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

public class GoogleCloudTestingTimeoutState extends AbstractState {
  private final GoogleCloudTestProxy myTestProxy;

  public GoogleCloudTestingTimeoutState(@NotNull final GoogleCloudTestProxy testProxy) {
    myTestProxy = testProxy;
  }

  @Override
  public boolean wasLaunched() {
    return true;
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean isInProgress() {
    return false;
  }

  @Override
  public boolean isDefect() {
    return true;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.TIMEOUT_INDEX;
  }

  @Override
  public String toString() {
    return "TIMED OUT";
  }
}
