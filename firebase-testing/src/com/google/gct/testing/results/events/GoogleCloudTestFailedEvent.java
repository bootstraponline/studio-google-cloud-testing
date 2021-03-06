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

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoogleCloudTestFailedEvent extends TestFailedEvent {

  private final String configuration;

  private final String className;

  public GoogleCloudTestFailedEvent(@NotNull TestFailed testFailed, boolean testError) {
    super(testFailed, testError);
    configuration = testFailed.getAttributes().get("configuration");
    className = testFailed.getAttributes().get("className");
  }

  public GoogleCloudTestFailedEvent(@NotNull String testName,
                                    @NotNull String localizedFailureMessage,
                                    @Nullable String stackTrace,
                                    boolean testError,
                                    @Nullable String comparisonFailureActualText,
                                    @Nullable String comparisonFailureExpectedText,
                                    @NotNull String configuration,
                                    @NotNull String className) {
    super(testName, localizedFailureMessage, stackTrace, testError, comparisonFailureActualText, comparisonFailureExpectedText);
    this.configuration = configuration;
    this.className = className;
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
