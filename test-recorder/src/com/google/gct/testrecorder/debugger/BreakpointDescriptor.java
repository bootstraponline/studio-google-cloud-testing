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
package com.google.gct.testrecorder.debugger;

public class BreakpointDescriptor {
  public final String eventType; // e.g., VIEW_CLICKED
  public final String className; // Fully qualified class name to set breakpoint in.
  // TODO: If the need arises, identify methods by signature, not just by name.
  public final String methodName; // The name of the method, whose first location is used to place the breakpoint.

  // A breakpoint is preparatory if it does not immediately result in a Test Recorder event, but instead, is used to collect
  // some information to combine with the subsequent breakpoint's information for a single Test Recorder event for both breakpoints.
  // E.g., beforeTextChanged breakpoint is preparatory and is combined with onTextChanged breakpoint to produce TEXT_CHANGE event.
  public final boolean isPreparatory;


  public BreakpointDescriptor(String eventType, String className, String methodName, boolean isPreparatory) {
    this.eventType = eventType;
    this.className = className;
    this.methodName = methodName;
    this.isPreparatory = isPreparatory;
  }
}
