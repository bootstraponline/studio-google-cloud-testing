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

public class TestName {

  private final String className;
  private final String methodName;

  public TestName(String className, String methodName) {
    this.className = className;
    this.methodName = methodName;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  @Override
  public String toString() {
    return className + "#" + methodName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TestName testName = (TestName)o;

    if (className != null ? !className.equals(testName.className) : testName.className != null) return false;
    if (methodName != null ? !methodName.equals(testName.methodName) : testName.methodName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = className != null ? className.hashCode() : 0;
    result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
    return result;
  }

  public String getDisplayName() {
    return className.substring(className.lastIndexOf('.') + 1) + "#" + methodName;
  }
}
