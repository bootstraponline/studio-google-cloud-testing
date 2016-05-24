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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoogleCloudTestEventsAdapter implements GoogleCloudTestEventsListener {
  @Override
  public void onTestingStarted(@NotNull GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRoot, boolean printTestingStartedTime){}
  @Override
  public void onTestingFinished(@NotNull GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRoot){}
  @Override
  public void onTestsCountInSuite(final int count) {}

  @Override
  public void onTestStarted(@NotNull final GoogleCloudTestProxy test) {}
  @Override
  public void onTestFinished(@NotNull final GoogleCloudTestProxy test) {}
  @Override
  public void onTestFailed(@NotNull final GoogleCloudTestProxy test) {}
  @Override
  public void onTestIgnored(@NotNull final GoogleCloudTestProxy test) {}

  @Override
  public void onSuiteStarted(@NotNull final GoogleCloudTestProxy suite) {}
  @Override
  public void onSuiteFinished(@NotNull final GoogleCloudTestProxy suite) {}

  // Custom progress status

  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, final int testCount) {}
  @Override
  public void onCustomProgressTestStarted() {}
  @Override
  public void onCustomProgressTestFailed() {}
}
