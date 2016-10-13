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

import com.google.common.collect.Lists;
import com.google.gct.testrecorder.event.ElementDescriptor;
import com.google.gct.testrecorder.event.TestRecorderAssertion;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import com.google.gct.testrecorder.util.ResourceHelper;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.apache.commons.io.FileUtils;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.util.List;

import static com.google.gct.testrecorder.event.TestRecorderAssertion.EXISTS;
import static com.google.gct.testrecorder.event.TestRecorderAssertion.TEXT_IS;
import static com.google.gct.testrecorder.event.TestRecorderEvent.SwipeDirection.Right;

public class TestCodeGeneratorTest extends AndroidTestCase {

  public void testCodeGeneration() throws Exception {
    PsiClass testClass = createTestClass();

    TestCodeGenerator testCodeGenerator = new TestCodeGenerator(myFacet, testClass, createEvents(), "p1.p2.MyActivity", false, false);

    String testFilePath = testClass.getContainingFile().getVirtualFile().getPath();
    VirtualFile testVirtualFile = LocalFileSystem.getInstance().findFileByPath(testFilePath);

    testCodeGenerator.writeCode(testFilePath, testVirtualFile);

    testVirtualFile.refresh(false, true);

    Project project = myModule.getProject();
    testVirtualFile.refresh(true, true, new Runnable() {
      @Override
      public void run() {
        new OptimizeImportsProcessor(project, testClass.getContainingFile()).run();
        new ReformatCodeProcessor(project, testClass.getContainingFile(), null, false).run();

        String actualTestClassContent = FileDocumentManager.getInstance().getDocument(testVirtualFile).getText();
        assertEquals(getExpectedTestClassContent(), actualTestClassContent);
      }
    });
  }

  private PsiClass createTestClass() {
    PsiDirectory containingDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(myModule.getProject().getBaseDir());

    PsiClass testClass = ApplicationManager.getApplication().runWriteAction(new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        PsiClass testClass = JavaDirectoryService.getInstance()
          .createClass(containingDirectory, "MyTest", JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME, false);

        // To avoid concurrent modification warning which will break the test with a NullPointerException.
        PsiManager.getInstance(myModule.getProject()).reloadFromDisk(testClass.getContainingFile());

        return testClass;
      }
    });

    if (testClass == null) {
      throw new RuntimeException("Failed to create the test class!");
    }

    return testClass;
  }

  private String getExpectedTestClassContent() {
    File expectedTestClass = ResourceHelper.getFileForResource(this, "ExpectedTestClass.txt", "expected_test_class_", "txt");
    try {
      return FileUtils.readFileToString(expectedTestClass);
    } catch (Exception e) {
      throw new RuntimeException("Failed to read the expected test class content " + expectedTestClass.getAbsolutePath(), e);
    }
  }

  private List<Object> createEvents() {
    List<Object> events = Lists.newLinkedList();

    TestRecorderEvent viewClickEvent = new TestRecorderEvent(TestRecorderEvent.VIEW_CLICK, System.currentTimeMillis());
    viewClickEvent.addElementDescriptor(new ElementDescriptor("ElementClass", 0, "resourceId1", "content description 1", ""));
    viewClickEvent.addElementDescriptor(new ElementDescriptor("ParentClass1", 1, "parentResourceId1", "", ""));
    viewClickEvent.addElementDescriptor(new ElementDescriptor("ParentClass2", 2, "parentResourceId2", "parent content description 1", ""));
    events.add(viewClickEvent);

    TestRecorderEvent viewClickEvent2 = new TestRecorderEvent(TestRecorderEvent.VIEW_CLICK, System.currentTimeMillis());
    viewClickEvent2.addElementDescriptor(new ElementDescriptor("ElementClass", 1, "", "", ""));
    viewClickEvent2.addElementDescriptor(new ElementDescriptor("ParentClass1", 0, "", "", ""));
    events.add(viewClickEvent2);

    TestRecorderEvent viewClickEvent3 = new TestRecorderEvent(TestRecorderEvent.VIEW_CLICK, System.currentTimeMillis());
    viewClickEvent3.addElementDescriptor(new ElementDescriptor("GridClass", 0, "gridResourceId", "", ""));
    viewClickEvent3.addElementDescriptor(new ElementDescriptor("ParentClass1", 0, "gridParentResourceId", "", ""));
    viewClickEvent3.setRecyclerViewPosition(5);
    events.add(viewClickEvent3);

    TestRecorderAssertion assertion = new TestRecorderAssertion(EXISTS);
    assertion.addElementDescriptor(new ElementDescriptor("ElementClass", -1, "resourceId2", "content description 2", ""));
    events.add(assertion);

    TestRecorderEvent viewLongClickEvent = new TestRecorderEvent(TestRecorderEvent.VIEW_LONG_CLICK, System.currentTimeMillis());
    viewLongClickEvent.addElementDescriptor(new ElementDescriptor("ElementClass", -1, "resourceId2", "content description 2", ""));
    events.add(viewLongClickEvent);

    TestRecorderEvent listItemClickEvent = new TestRecorderEvent(TestRecorderEvent.LIST_ITEM_CLICK, System.currentTimeMillis());
    listItemClickEvent.addElementDescriptor(new ElementDescriptor("ElementClass", 2, "resourceId3", "", ""));
    listItemClickEvent.addElementDescriptor(new ElementDescriptor("ParentClass1", 0, "parentResourceId3", "", ""));
    listItemClickEvent.addElementDescriptor(new ElementDescriptor("ParentClass2", 1, "parentResourceId4", "", ""));
    events.add(listItemClickEvent);

    TestRecorderEvent pressBackEvent = new TestRecorderEvent(TestRecorderEvent.PRESS_BACK, System.currentTimeMillis());
    events.add(pressBackEvent);

    TestRecorderEvent delayedMessagePostEvent = new TestRecorderEvent(TestRecorderEvent.DELAYED_MESSAGE_POST, System.currentTimeMillis());
    delayedMessagePostEvent.setDelayTime(1500);
    events.add(delayedMessagePostEvent);

    TestRecorderEvent textChangeEvent = new TestRecorderEvent(TestRecorderEvent.TEXT_CHANGE, System.currentTimeMillis());
    textChangeEvent.addElementDescriptor(new ElementDescriptor("ElementClass", 1, "resourceId4", "content description 3", "original text"));
    textChangeEvent.addElementDescriptor(new ElementDescriptor("ParentClass1", 0, "parentResourceId5", "", ""));
    textChangeEvent.addElementDescriptor(new ElementDescriptor("ParentClass2", 2, "parentResourceId6", "parent content description 2", ""));
    textChangeEvent.setReplacementText("replacement text");
    events.add(textChangeEvent);

    TestRecorderAssertion assertion2 = new TestRecorderAssertion(TEXT_IS);
    assertion2.addElementDescriptor(new ElementDescriptor("ElementClass", 1, "resourceId4", "content description 3", "replacement text"));
    assertion2.addElementDescriptor(new ElementDescriptor("ParentClass1", 0, "parentResourceId5", "", ""));
    assertion2.addElementDescriptor(new ElementDescriptor("ParentClass2", 2, "parentResourceId6", "parent content description 2", ""));
    assertion2.setText("replacement text");
    events.add(assertion2);

    TestRecorderEvent pressEditorActionEvent = new TestRecorderEvent(TestRecorderEvent.PRESS_EDITOR_ACTION, System.currentTimeMillis());
    pressEditorActionEvent.addElementDescriptor(new ElementDescriptor("ElementClass", 1, "resourceId4", "content description 3", "replacement text"));
    pressEditorActionEvent.addElementDescriptor(new ElementDescriptor("ParentClass1", 0, "parentResourceId5", "", ""));
    pressEditorActionEvent.addElementDescriptor(new ElementDescriptor("ParentClass2", 2, "parentResourceId6", "parent content description 2", ""));
    events.add(pressEditorActionEvent);

    TestRecorderEvent swipeEvent = new TestRecorderEvent(TestRecorderEvent.VIEW_SWIPE, System.currentTimeMillis());
    swipeEvent.addElementDescriptor(new ElementDescriptor("ElementClass", 0, "resourceId5", "content description 4", ""));
    swipeEvent.addElementDescriptor(new ElementDescriptor("ParentClass1", 1, "parentResourceId7", "", "parent text"));
    swipeEvent.setSwipeDirection(Right);
    events.add(swipeEvent);

    TestRecorderEvent trailingDelayedMessagePostEvent = new TestRecorderEvent(TestRecorderEvent.DELAYED_MESSAGE_POST, System.currentTimeMillis());
    trailingDelayedMessagePostEvent.setDelayTime(2500);
    events.add(trailingDelayedMessagePostEvent);

    return events;
  }

}
