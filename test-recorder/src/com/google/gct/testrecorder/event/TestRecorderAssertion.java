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
package com.google.gct.testrecorder.event;

public class TestRecorderAssertion extends ElementAction {
  public static final String EXISTS = "exists";
  public static final String NOT_EXISTS = "does not exist";
  public static final String TEXT_IS = "text is";


  public static final String[] ASSERTION_RULES_WITHOUT_TEXT = {EXISTS, NOT_EXISTS};
  public static final String[] ASSERTION_RULES_WITH_TEXT = {TEXT_IS, EXISTS, NOT_EXISTS};

  /**
   * Rule for the assertion (exists, not exists, etc.).
   */
  private final String rule;

  /**
   * Text of the assertion (valid for TextView, EditText, etc.).
   */
  private String text;


  public TestRecorderAssertion(String rule) {
    this.rule = rule;
  }

  public String getRule() {
    return rule;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

}
