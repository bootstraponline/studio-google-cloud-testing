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

import com.intellij.execution.testframework.sm.runner.states.TestInProgressState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GoogleCloudTestingSuiteInProgressState extends TestInProgressState {
  private final GoogleCloudTestProxy mySuiteProxy;
  private boolean myDefectFound = false;

  public GoogleCloudTestingSuiteInProgressState(@NotNull final GoogleCloudTestProxy suiteProxy) {
    mySuiteProxy = suiteProxy;
  }

  /**
   * If any of child failed proxy also is defect
   * @return
   */
  @Override
  public boolean isDefect() {
    if (myDefectFound) {
      return true;
    }

     //Test suit fails if any of its tests fails
    final List<? extends GoogleCloudTestProxy> children = mySuiteProxy.getChildren();
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
    return Magnitude.RUNNING_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "SUITE PROGRESS";
  }
}
