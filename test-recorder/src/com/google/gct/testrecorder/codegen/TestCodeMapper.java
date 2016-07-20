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

  private final String myApplicationId;
  private final boolean myIsUsingCustomEspresso;
  private final Project myProject;
  @Nullable private final AndroidTargetData myAndroidTargetData;
  private boolean myIsChildAtPositionAdded;
  private boolean myIsRecyclerViewActionAdded;

  /**
   * Map of variable_name -> first_unused_index. This map is used to ensure that variable names are unique.
   */
  private final Map<String, Integer> myVariableNameIndexes = Maps.newHashMap();


  public TestCodeMapper(
    String applicationId, boolean isUsingCustomEspresso, Project project, @Nullable AndroidTargetData androidTargetData) {

    myApplicationId = applicationId;
    myIsUsingCustomEspresso = isUsingCustomEspresso;
    myProject = project;
    myAndroidTargetData = androidTargetData;
  }

  public List<String> getTestCodeLinesForEvent(TestRecorderEvent event) {
    List<String> testCodeLines = new LinkedList<String>();

    if (event.isPressBack()) {
      testCodeLines.add("pressBack();");
      return testCodeLines;
    }

    String variableName = addViewPickingStatement(event, testCodeLines);
    if (event.isPressEditorAction()) {
      // TODO: If this is the same element that was just edited, consider reusing the same view interaction (i.e., variable name).
      testCodeLines.add(createActionStatement(variableName, "pressImeActionButton()", false));
    } else if (event.isClickEvent()) {
      if (event.getRecyclerViewPosition() != -1) {
        myIsRecyclerViewActionAdded = true;
        testCodeLines.add(createActionStatement(variableName, "actionOnItemAtPosition(" + event.getRecyclerViewPosition() + ", click())", false));
      } else {
        testCodeLines.add(createActionStatement(variableName, event.isViewLongClick() ? "longClick()" : "click()", event.canScrollTo()));
      }
    } else if (event.isTextChange()) {
      if (myIsUsingCustomEspresso) {
        testCodeLines.add(createActionStatement(variableName, "clearText()", event.canScrollTo()));
        testCodeLines.add(createActionStatement(variableName, "typeText(\"" + event.getReplacementText() + "\")", false));
      } else {
        testCodeLines.add(createActionStatement(variableName, "replaceText(\"" + event.getReplacementText() + "\")", event.canScrollTo()));
      }
    } else {
      throw new RuntimeException("Unsupported event type: " + event.getEventType());
    }

    return testCodeLines;
  }

  private String createActionStatement(String variableName, String action, boolean addScrollTo) {
    return variableName + ".perform(" + (addScrollTo ? "scrollTo(), " : "") + action + ");";
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
    return generateElementHierarchyConditionsRecursively(action instanceof TestRecorderAssertion, !action.canScrollTo(),
                                                         elementDescriptors, 0);
  }

  private String generateElementHierarchyConditionsRecursively(boolean isAssertionConditions, boolean checkIsDisplayed,
                                                               List<ElementDescriptor> elementDescriptors, int index) {
    // Add isDisplayed() only to the innermost element.
    boolean addIsDisplayed = checkIsDisplayed && index == 0;

    ElementDescriptor elementDescriptor = elementDescriptors.get(index);
    MatcherBuilder matcherBuilder = new MatcherBuilder(myProject);

    int lastIndex = elementDescriptors.size() - 1;

    if (elementDescriptor.isEmpty()
        // Cannot use child position for the last element, since no parent descriptor available.
        || index == lastIndex && elementDescriptor.isEmptyIgnoringChildPosition()
        || index == 0 && isLoginRadioButton(elementDescriptors)) {
      matcherBuilder.addMatcher(ClassName, elementDescriptor.getClassName(), true, isAssertionConditions);
    } else {
      // Do not use android framework ids that are not visible to the compiler.
      String resourceId = elementDescriptor.getResourceId();
      if (isAndroidFrameworkPrivateId(resourceId)) {
        matcherBuilder.addMatcher(ClassName, elementDescriptor.getClassName(), true, isAssertionConditions);
      } else {
        matcherBuilder.addMatcher(Id, convertIdToTestCodeFormat(resourceId), false, isAssertionConditions);
      }

      matcherBuilder.addMatcher(Text, elementDescriptor.getText(), true, isAssertionConditions);
      matcherBuilder.addMatcher(ContentDescription, elementDescriptor.getContentDescription(), true, isAssertionConditions);
    }

    // TODO: Consider minimizing the generated statement to improve test's readability and maintainability (e.g., by capping parent hierarchy).

    // The last element has no parent.
    if (index == lastIndex) {
      if (matcherBuilder.getMatcherCount() > 1 || addIsDisplayed) {
        return "allOf(" + matcherBuilder.getMatchers() + (addIsDisplayed ? ", isDisplayed()" : "") + ")";
      }
      return matcherBuilder.getMatchers();
    }

    boolean addAllOf = matcherBuilder.getMatcherCount() > 0 || addIsDisplayed;
    int childPosition = elementDescriptor.getChildPosition();
    myIsChildAtPositionAdded = myIsChildAtPositionAdded || childPosition != -1;

    return (addAllOf ? "allOf(" : "") + matcherBuilder.getMatchers() + (matcherBuilder.getMatcherCount() > 0 ? ",\n" : "")
           + (childPosition != -1 ? "childAtPosition(\n" : "withParent(")
           + generateElementHierarchyConditionsRecursively(isAssertionConditions, checkIsDisplayed, elementDescriptors, index + 1)
           + (childPosition != -1 ? ",\n" + childPosition : "") + ")"
           + (addIsDisplayed ? ",\nisDisplayed()" : "") + (addAllOf ? ")" : "");
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
    if (!parsedId.getFirst().equals(myApplicationId)) {
      // Only the app's resource package will be explicitly imported, so use a fully qualified id for other packages.
      testCodeId = parsedId.getFirst() + "." + testCodeId;
    }

    return testCodeId;
  }

  public boolean isChildAtPositionAdded() {
    return myIsChildAtPositionAdded;
  }

  public boolean isRecyclerViewActionAdded() {
    return myIsRecyclerViewActionAdded;
  }
}
