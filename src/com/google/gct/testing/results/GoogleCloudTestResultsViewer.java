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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GoogleCloudTestResultsViewer extends Disposable {
  /**
   * Fake Root for toplevel test suits/tests
   *
   * @return root
   */
  GoogleCloudTestProxy getTestsRootNode();

  /**
   * Selects test or suite in Tests tree and notify about selection changed
   *
   * @param proxy
   */
  void selectAndNotify(@Nullable AbstractTestProxy proxy);

  void addEventsListener(EventsListener listener);

  void setShowStatisticForProxyHandler(GoogleCloudTestingPropagateSelectionHandler handler);

  /**
   * If handler for statistics was set this method will execute it
   */
  void showStatisticsForSelectedProxy();

  interface EventsListener extends GoogleCloudTestProxyTreeSelectionListener {
    void onTestingStarted(GoogleCloudTestResultsViewer sender);

    void onTestingFinished(GoogleCloudTestResultsViewer sender);

    void onTestNodeAdded(GoogleCloudTestResultsViewer sender, GoogleCloudTestProxy test);
  }

  class SMEventsAdapter implements EventsListener {

    @Override
    public void onTestingStarted(GoogleCloudTestResultsViewer sender) {
    }

    @Override
    public void onTestingFinished(GoogleCloudTestResultsViewer sender) {
    }

    @Override
    public void onTestNodeAdded(GoogleCloudTestResultsViewer sender, GoogleCloudTestProxy test) {
    }

    @Override
    public void onSelected(@Nullable GoogleCloudTestProxy selectedTestProxy,
                           @NotNull GoogleCloudTestResultsViewer viewer,
                           @NotNull TestFrameworkRunningModel model) {
    }
  }
}
