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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EmptyStackException;
import java.util.Stack;

public class GoogleCloudTestSuiteStack {
  private static final Logger LOG = Logger.getInstance(GoogleCloudTestSuiteStack.class.getName());

  @NonNls private static final String EMPTY = "empty";

  private final Stack<GoogleCloudTestProxy> myStack = new Stack<GoogleCloudTestProxy>();

  public void pushSuite(@NotNull final GoogleCloudTestProxy suite) {
    myStack.push(suite);
  }

  /**
   * @return Top element of non stack or null for empty stack
   */
  @Nullable
  public GoogleCloudTestProxy getCurrentSuite() {
    if (getStackSize() != 0) {
      return myStack.peek();
    }
    return null;
  }

  /**
   * Pop element form stack and checks consistency
   * @param suiteName Predictable name of top suite in stack. May be null if 
   */
  @Nullable
  public GoogleCloudTestProxy popSuite(final String suiteName) throws EmptyStackException {
    if (myStack.isEmpty()) {
      if (GoogleCloudTestResultsConnectionUtil.isInDebugMode()) {
        LOG.error(
          "Pop error: Tests/suites stack is empty. Test runner tried to close test suite " +
          "which has been already closed or wasn't started at all. Unexpected suite name [" +
          suiteName + "]");
      }
      return null;
    }
    final GoogleCloudTestProxy topSuite = myStack.peek();

    if (!suiteName.equals(topSuite.getName())) {
      if (GoogleCloudTestResultsConnectionUtil.isInDebugMode()) {
        LOG.error("Pop error: Unexpected closing suite. Expected [" + suiteName + "] but [" + topSuite.getName() +
                  "] was found. Rest of stack: " + getSuitePathPresentation());
      } else {
        // let's try to switch to consistent state
        // 1. If expected suite name is somewhere in stack - let's find it and drop rest head of the stack
        GoogleCloudTestProxy expectedProxy = null;
        for (GoogleCloudTestProxy candidateProxy : myStack) {
          if (suiteName.equals(candidateProxy.getName())) {
            expectedProxy = candidateProxy;
            break;
          }
        }
        if (expectedProxy != null) {
          // drop all tests above it
          GoogleCloudTestProxy proxy = topSuite;
          while (proxy != expectedProxy) {
            proxy = myStack.pop();
          }

          return expectedProxy;
        } else {
          // 2. if expected suite wasn't found let's skip it and return null
          return null;
        }
      }
      return null;
    } else {
      myStack.pop();
    }

    return topSuite;
  }

  public final boolean isEmpty() {
    return getStackSize() == 0;
  }
  
  protected int getStackSize() {
    return myStack.size();
  }

  protected String[] getSuitePath() {
    final int stackSize = getStackSize();
    final String[] names = new String[stackSize];
    for (int i = 0; i < stackSize; i++) {
      names[i] = myStack.get(i).getName();
    }
    return names;
  }

  protected String getSuitePathPresentation() {
    final String[] names = getSuitePath();
    if (names.length == 0) {
      return EMPTY;
    }

    return StringUtil.join(names, new Function<String, String>() {
      @Override
      public String fun(String s) {
        return "[" + s + "]";
      }
    }, "->");
  }

  public void clear() {
    myStack.clear();
  }
}
