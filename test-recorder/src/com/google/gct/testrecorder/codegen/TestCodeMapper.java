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

import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.collect.Maps;
import com.google.gct.testrecorder.event.ElementAction;
import com.google.gct.testrecorder.event.ElementDescriptor;
import com.google.gct.testrecorder.event.TestRecorderAssertion;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.gct.testrecorder.codegen.MatcherBuilder.Kind.*;
import static com.google.gct.testrecorder.event.TestRecorderAssertion.*;
import static com.google.gct.testrecorder.util.StringHelper.*;

public class TestCodeMapper {

  private static final String VIEW_VARIABLE_CLASS_NAME = "ViewInteraction";

  private final String myResourcePackageName;
  private final boolean myIsUsingCustomEspresso;
  private final Project myProject;
  @Nullable private final AndroidTargetData myAndroidTargetData;

  /**
   * Map of variable_name -> first_unused_index. This map is used to ensure that variable names are unique.
   */
  private final Map<String, Integer> myVariableNameIndexes = Maps.newHashMap();

  private String myLastUsedEventVariableName;


  public TestCodeMapper(
    String resourcePackageName, boolean isUsingCustomEspresso, Project project, @Nullable AndroidTargetData androidTargetData) {

    myResourcePackageName = resourcePackageName;
    myIsUsingCustomEspresso = isUsingCustomEspresso;
    myProject = project;
    myAndroidTargetData = androidTargetData;
  }

  public List<String> getTestCodeLinesForEvent(TestRecorderEvent event) {
    List<String> testCodeLines = new LinkedList<String>();

    if (event.isPressEvent()) {
      addTestCodeLinesForPressEvent(event, testCodeLines);
      return testCodeLines;
    }

    String variableName = addViewPickingStatement(event, testCodeLines);
    if (event.isClickEvent()) {
      if (event.getPositionIndex() != -1) {
        testCodeLines.add(variableName + ".perform(actionOnItemAtPosition(" + event.getPositionIndex() + ", click()));");
      } else {
        testCodeLines.add(variableName + ".perform(click());");
      }
    } else if (event.isTextChange()) {
      if (myIsUsingCustomEspresso) {
        testCodeLines.add(variableName + ".perform(clearText());");
        testCodeLines.add(variableName + ".perform(typeText(\"" + event.getReplacementText() + "\"));");
      } else {
        testCodeLines.add(variableName + ".perform(replaceText(\"" + event.getReplacementText() + "\"));");
      }
      // TODO: Closing soft keyboard should be performed explicitly (similarly to replaceText() above), if needed,
      // but probably it will be replaced with a more specialized handling of pressed keys (e.g., press search, done, etc.).
      //testCodeLines.add("closeSoftKeyboard();");
    } else {
      throw new RuntimeException("Unsupported event type: " + event.getEventType());
    }

    myLastUsedEventVariableName = variableName;

    return testCodeLines;
  }

  private void addTestCodeLinesForPressEvent(TestRecorderEvent event, List<String> testCodeLines) {
    if (event.isPressBack()) {
      testCodeLines.add("pressBack();");
    } else if (event.isPressEditorAction()) {
      // Pressing an editor action (return) key belongs to the last affected element, which is represented by myLastUsedEventVariableName.
      String variableName = myLastUsedEventVariableName == null || myLastUsedEventVariableName.isEmpty()
                            ? "unknownElement" : myLastUsedEventVariableName;
      testCodeLines.add(variableName + ".perform(pressImeActionButton());");
    } else {
      throw new RuntimeException("Unsupported press event type: " + event.getEventType());
    }
  }

  public List<String> getTestCodeLinesForAssertion(TestRecorderAssertion assertion) {
    List<String> testCodeLines = new LinkedList<String>();

    String rule = assertion.getRule();
    String variableName = addViewPickingStatement(assertion, testCodeLines);

    if (NOT_EXISTS.equals(rule)) {
      testCodeLines.add(variableName + ".check(doesNotExist());");
    } else if (EXISTS.equals(rule)) {
      testCodeLines.add(variableName + ".check(matches(isDisplayed()));");
    } else if (TEXT_IS.equals(rule)) {
      String text = assertion.getText();
      testCodeLines.add(variableName + ".check(matches(withText(\"" + text + "\")));");
    } else {
      throw new RuntimeException("Unsupported assertion rule: " + rule);
    }

    return testCodeLines;
  }

  private String addViewPickingStatement(ElementAction action, List<String> testCodeLines) {
    String variableName = generateVariableNameFromElementClassName(action.getElementClassName());
    testCodeLines.add(VIEW_VARIABLE_CLASS_NAME + " " + variableName + " = onView(\n" + generateElementHierarchyConditions(action) + ");");
    return variableName;
  }

  private String generateVariableNameFromElementClassName(@Nullable String elementClassName) {
    if (isNullOrEmpty(elementClassName)) {
      return generateVariableNameFromTemplate(VIEW_VARIABLE_CLASS_NAME);
    }
    return generateVariableNameFromTemplate(getClassName(elementClassName));
  }

  private String generateVariableNameFromTemplate(String template) {
    String variableName = lowerCaseFirstCharacter(template);
    if (JavaLexer.isKeyword(variableName, LanguageLevel.HIGHEST)) {
      variableName += "_";
    }

    Integer unusedIndex = myVariableNameIndexes.get(variableName);
    if (unusedIndex == null) {
      myVariableNameIndexes.put(variableName, 2);
      return variableName;
    }

    myVariableNameIndexes.put(variableName, unusedIndex + 1);
    return variableName + unusedIndex;
  }

  private String generateElementHierarchyConditions(ElementAction action) {
    List<ElementDescriptor> elementDescriptors = action.getElementDescriptorList();

    if (elementDescriptors.isEmpty()) {
      return "UNKNOWN";
    }
    return generateElementHierarchyConditionsRecursively(elementDescriptors, 0);
  }

  private String generateElementHierarchyConditionsRecursively(List<ElementDescriptor> elementDescriptors, int index) {
    ElementDescriptor elementDescriptor = elementDescriptors.get(index);
    MatcherBuilder matcherBuilder = new MatcherBuilder(myProject);

    // Only the first descriptor could be empty, but check the index anyway.
    if (index == 0 && (elementDescriptor.isEmpty() || isLoginRadioButton(elementDescriptors))) {
      matcherBuilder.addMatcher(ClassName, elementDescriptor.getClassName(), true);
    } else {
      // Do not use android framework ids that are not visible to the compiler.
      String resourceId = elementDescriptor.getResourceId();
      if (isAndroidFrameworkPrivateId(resourceId)) {
        matcherBuilder.addMatcher(ClassName, elementDescriptor.getClassName(), true);
      } else {
        matcherBuilder.addMatcher(Id, convertIdToTestCodeFormat(resourceId), false);
      }

      matcherBuilder.addMatcher(Text, elementDescriptor.getText(), true);
      matcherBuilder.addMatcher(ContentDescription, elementDescriptor.getContentDescription(), true);
    }

    // TODO: Consider minimizing the generated statement to improve test's readability and maintainability (e.g., by capping parent hierarchy).

    // The last element has no parent.
    if (index == elementDescriptors.size() - 1) {
      if (matcherBuilder.getMatcherCount() > 1 || index == 0) {
        return "allOf(" + matcherBuilder.getMatchers() + (index == 0 ? ", isDisplayed()" : "") + ")";
      }
      return matcherBuilder.getMatchers();
    }

    // Add isDisplayed() only to the innermost element.
    return "allOf(" + matcherBuilder.getMatchers() + ",\nwithParent("
           + generateElementHierarchyConditionsRecursively(elementDescriptors, index + 1)
           + ")" + (index == 0 ? ",\nisDisplayed()" : "") + ")";
  }

  private boolean isAndroidFrameworkPrivateId(String resourceId) {
    Pair<String, String> parsedId = parseId(resourceId);
    return myAndroidTargetData != null && parsedId != null && "android".equals(parsedId.getFirst())
           && !myAndroidTargetData.isResourcePublic(ResourceType.ID.getName(), parsedId.getSecond());
  }

  /**
   * TODO: This is a temporary workaround for picking a login option in a username-agnostic way
   * such that the generated test is generic enough to run on other devices.
   * TODO: Also, it assumes a single radio button choice (such that it could be identified by the class name).
   */
  private boolean isLoginRadioButton(List<ElementDescriptor> elementDescriptors) {
    if (elementDescriptors.size() > 1 && elementDescriptors.get(0).getClassName().endsWith(".widget.AppCompatRadioButton")
        && "R.id.welcome_account_list".equals(convertIdToTestCodeFormat(elementDescriptors.get(1).getResourceId()))) {
      return true;
    }

    return false;
  }

  private String convertIdToTestCodeFormat(String resourceId) {
    Pair<String, String> parsedId = parseId(resourceId);

    if (parsedId == null) {
      // Parsing failed, return the raw id.
      return resourceId;
    }

    String testCodeId = "R.id." + parsedId.getSecond();
    if (!parsedId.getFirst().equals(myResourcePackageName)) {
      // Only the app's resource package name will be explicitly imported, so use a fully qualified id for other packages.
      testCodeId = parsedId.getFirst() + "." + testCodeId;
    }

    return testCodeId;
  }

}
