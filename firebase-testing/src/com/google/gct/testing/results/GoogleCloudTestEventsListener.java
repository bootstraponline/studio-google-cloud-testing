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

public interface GoogleCloudTestEventsListener {
  /**
   * On start testing, before tests and suits launching
   * @param testsRoot
   */
  void onTestingStarted(@NotNull GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRoot,
                        boolean printTestingStartedTime);
  /**
   * After test framework finish testing
   * @param testsRootNode
   */
  void onTestingFinished(@NotNull GoogleCloudTestProxy.GoogleCloudRootTestProxy testsRoot);
  /*
   * Tests count in next suite. For several suites this method will be invoked several times
   */
  void onTestsCountInSuite(int count);

  void onTestStarted(@NotNull GoogleCloudTestProxy test);
  void onTestFinished(@NotNull GoogleCloudTestProxy test);
  void onTestFailed(@NotNull GoogleCloudTestProxy test);
  void onTestIgnored(@NotNull GoogleCloudTestProxy test);

  void onSuiteFinished(@NotNull GoogleCloudTestProxy suite);
  void onSuiteStarted(@NotNull GoogleCloudTestProxy suite);

  // Custom progress statistics

  /**
   * @param categoryName If isn't empty then progress statistics will use only custom start/failed events.
   * If name is empty string statistics will be switched to normal mode
   * @param testCount - 0 will be considered as unknown tests number
   */
  void onCustomProgressTestsCategory(@Nullable final String categoryName, final int testCount);
  void onCustomProgressTestStarted();
  void onCustomProgressTestFailed();
}
