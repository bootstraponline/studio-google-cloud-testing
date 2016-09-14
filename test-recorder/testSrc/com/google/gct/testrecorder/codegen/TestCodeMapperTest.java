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
package com.google.gct.testrecorder.codegen;

import com.google.gct.testrecorder.event.ElementDescriptor;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import org.jetbrains.android.AndroidTestCase;

import static com.google.gct.testrecorder.event.TestRecorderEvent.SwipeDirection.Right;

public class TestCodeMapperTest extends AndroidTestCase {

  public void testIsOverflowMenuButton() throws Exception {
    TestCodeMapper testCodeMapper = new TestCodeMapper("12345", false, myModule.getProject(), null);
    assertTrue(testCodeMapper.isOverflowMenuButton("android.widget.ActionMenuPresenter.OverflowMenuButton"));
    assertTrue(testCodeMapper.isOverflowMenuButton("android.support.v7.widget.ActionMenuPresenter.OverflowMenuButton"));
  }

  public void testCloseSoftKeyboardAfterTextEdit() {
    TestCodeMapper testCodeMapper = new TestCodeMapper("12345", false, myModule.getProject(), null);

    TestRecorderEvent textChangeEvent = new TestRecorderEvent(TestRecorderEvent.TEXT_CHANGE, System.currentTimeMillis());
    textChangeEvent.addElementDescriptor(new ElementDescriptor("SomeClass", -1, "", "content description", ""));
    textChangeEvent.setReplacementText("my text");

    String espressoActionStatement = testCodeMapper.getTestCodeLinesForEvent(textChangeEvent).get(1);
    assertTrue(espressoActionStatement.contains("replaceText(\"my text\")"));
    assertTrue(espressoActionStatement.contains("closeSoftKeyboard()"));
  }

  public void testSwipeAction() {
    TestCodeMapper testCodeMapper = new TestCodeMapper("12345", false, myModule.getProject(), null);

    TestRecorderEvent textChangeEvent = new TestRecorderEvent(TestRecorderEvent.VIEW_SWIPE, System.currentTimeMillis());
    textChangeEvent.addElementDescriptor(new ElementDescriptor("SomeClass", -1, "", "content description", ""));
    textChangeEvent.setSwipeDirection(Right);

    String espressoActionStatement = testCodeMapper.getTestCodeLinesForEvent(textChangeEvent).get(1);
    assertTrue(espressoActionStatement.equals("someClass.perform(swipeRight());"));
  }
}
