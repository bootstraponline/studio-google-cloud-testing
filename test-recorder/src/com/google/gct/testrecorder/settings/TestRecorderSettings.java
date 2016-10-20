/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.gct.testrecorder.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name="TestRecorderSettings",
  storages = {
  @Storage("test.recorder.xml")}
)
public class TestRecorderSettings implements PersistentStateComponent<TestRecorderSettings> {
  /**
   * The number of UI hierarchy nodes, including the immediately affected one, used to identify the affected element.
   */
  public int EVALUATION_DEPTH = 3;

  /**
   * The number of UI hierarchy nodes, including the immediately affected one, that are checked for being a scroll view.
   */
  public int SCROLL_DEPTH = 5;

  /**
   * The number of UI hierarchy nodes, including the immediately affected one, used to identify the asserted element.
   */
  public int ASSERTION_DEPTH = 3;

  /**
   * Determines whether UI hierarchy nodes considered for evaluation should be capped at the first non-identifiable node,
   * i.e., a node whose only information is its class name.
   */
  public boolean CAP_AT_NON_IDENTIFIABLE_ELEMENTS = true;

  /**
   * Determines whether the value of the text attribute of UI elements should be used for matching them in Espresso statements.
   */
  public boolean USE_TEXT_FOR_ELEMENT_MATCHING = true;

  /**
   * Determines whether the app's state should be cleared before the recording starts.
   */
  public boolean CLEAN_BEFORE_START = true;

  /**
   * Determines whether the app's state should be cleared after the recording finishes.
   */
  public boolean CLEAN_AFTER_FINISH = true;

  /**
   * Determines whether the app should be stopped after the recording finishes.
   */
  public boolean STOP_APP_AFTER_RECORDING = true;


  @NotNull
  public static TestRecorderSettings getInstance() {
    return ServiceManager.getService(TestRecorderSettings.class);
  }

  @Override
  @NotNull
  public TestRecorderSettings getState() {
    return this;
  }

  @Override
  public void loadState(TestRecorderSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
