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
package com.google.gct.testrecorder.util;


import com.android.tools.idea.stats.UsageTracker;

public class TestRecorderTracking {

  // categories
  public static final String TEST_RECORDER = "com.google.test.recorder";

  // actions
  public static final String LAUNCH_TEST_RECORDER = "launch.test.recorder";
  public static final String GENERATE_TEST_CLASS_EVENTS = "generate.test.class.events";
  public static final String GENERATE_TEST_CLASS_ASSERTIONS = "generate.test.class.assertions";
  public static final String MISSING_ESPRESSO_DEPENDENCIES = "missing.espresso.dependencies";
  public static final String MISSING_INSTRUMENTATION_TEST_FOLDER = "missing.instrumentation.test.folder";

  // labels
  public static final String SESSION_LABEL = UsageTracker.SESSION_ID;
}
