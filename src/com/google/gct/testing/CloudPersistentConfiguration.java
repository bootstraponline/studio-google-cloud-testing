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
package com.google.gct.testing;

import com.android.tools.idea.run.CloudConfiguration.Kind;

import java.util.LinkedList;
import java.util.List;

import static com.android.tools.idea.run.CloudConfiguration.Kind.MATRIX;

public class CloudPersistentConfiguration {
  public int id = 1;
  public String name = "";
  public Kind kind = MATRIX;
  public List<String> devices = new LinkedList<String>();
  public List<String> apiLevels = new LinkedList<String>();
  public List<String> languages = new LinkedList<String>();
  public List<String> orientations = new LinkedList<String>();
}
