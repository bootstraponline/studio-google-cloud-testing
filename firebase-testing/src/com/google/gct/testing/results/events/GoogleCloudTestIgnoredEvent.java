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

import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import jetbrains.buildServer.messages.serviceMessages.TestIgnored;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoogleCloudTestIgnoredEvent extends TestIgnoredEvent {

  private final String configuration;

  private final String className;

  public GoogleCloudTestIgnoredEvent(@NotNull String testName,
                                     @NotNull String ignoreComment,
                                     @Nullable String stacktrace,
                                     @NotNull String configuration,
                                     @NotNull String className) {
    super(testName, ignoreComment, stacktrace);
    this.configuration = configuration;
    this.className = className;
  }

  public GoogleCloudTestIgnoredEvent(@NotNull TestIgnored testIgnored, @Nullable String stacktrace) {
    super(testIgnored, stacktrace);
    configuration = testIgnored.getAttributes().get("configuration");
    className = testIgnored.getAttributes().get("className");
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    super.appendToStringInfo(buf);
    TreeNodeEvent.append(buf, "configuration", configuration);
    TreeNodeEvent.append(buf, "className", className);
  }

  public String getConfiguration() {
    return configuration;
  }

  public String getClassName() {
    return className;
  }
}
