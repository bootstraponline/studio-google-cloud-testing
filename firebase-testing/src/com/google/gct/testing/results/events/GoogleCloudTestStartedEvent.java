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

import com.intellij.execution.testframework.sm.runner.events.BaseStartedNodeEvent;
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoogleCloudTestStartedEvent extends BaseStartedNodeEvent {

  private final String configuration;

  private final String className;

  public GoogleCloudTestStartedEvent(@NotNull TestStarted testStarted, @Nullable String locationUrl) {
    super(testStarted.getTestName(),
          TreeNodeEvent.getNodeId(testStarted),
          BaseStartedNodeEvent.getParentNodeId(testStarted),
          locationUrl,
          BaseStartedNodeEvent.getNodeType(testStarted),
          BaseStartedNodeEvent.getNodeArgs(testStarted),
          BaseStartedNodeEvent.isRunning(testStarted));
    configuration = testStarted.getAttributes().get("configuration");
    className = testStarted.getAttributes().get("className");
  }

  public GoogleCloudTestStartedEvent(@NotNull String name,
                                     @Nullable String locationUrl,
                                     @NotNull String configuration,
                                     @NotNull String className) {
    super(name, null, null, locationUrl, null, null, true);
    this.configuration = configuration;
    this.className = className;
  }

  public String getConfiguration() {
    return configuration;
  }

  public String getClassName() {
    return className;
  }
}
