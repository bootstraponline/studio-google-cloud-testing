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
package com.google.gct.testing.results.events;

import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import org.jetbrains.annotations.NotNull;

import static com.google.gct.testing.CloudTestingUtils.ConfigurationStopReason;

public class TestConfigurationStoppedEvent extends TreeNodeEvent {

  private final ConfigurationStopReason stopReason;
  private final long testDuration;

  public TestConfigurationStoppedEvent(@NotNull String configurationName, @NotNull ConfigurationStopReason stopReason, long testDuration) {
    super(configurationName, -1);
    this.stopReason = stopReason;
    this.testDuration = testDuration;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
  }

  public ConfigurationStopReason getStopReason() {
    return stopReason;
  }

  public long getTestDuration() {
    return testDuration;
  }
}