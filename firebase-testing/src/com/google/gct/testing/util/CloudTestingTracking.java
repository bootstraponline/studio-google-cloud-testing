/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.gct.testing.util;

import java.util.UUID;

public class CloudTestingTracking {

  // categories
  public static final String CLOUD_TESTING = "com.google.cloud.testing";

  // actions
  public static final String RUN_TEST_MATRIX = "run.test.matrix";
  public static final String LAUNCH_CLOUD_DEVICE = "launch.cloud.device";
  public static final String COMPARE_SCREENSHOTS_CLICKED = "compare.screenshots.clicked";
  public static final String COMPARE_SCREENSHOTS_OPENED = "compare.screenshots.opened";
  public static final String DEBUG_FROM_RESULTS = "debug.from.results";
  public static final String BACKEND_ERROR = "backend.error";
  public static final String CONFIGURE_MATRIX = "configure.matrix";
  public static final String CONFIGURE_CLOUD_DEVICE = "configure.cloud.device";

  // labels
  public static final String SESSION_LABEL = UUID.randomUUID().toString();
}
