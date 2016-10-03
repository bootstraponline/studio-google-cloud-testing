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

import com.android.tools.idea.lint.LintIdeUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.gct.testrecorder.codegen.MatcherBuilder.Kind.ClassName;
import static com.google.gct.testrecorder.util.StringHelper.boxString;

public class MatcherBuilder {
  public enum Kind {Id, Text, ContentDescription, ClassName}

  private final Project myProject;

  private int matcherCount = 0;
  private final StringBuilder matchers = new StringBuilder();

  public MatcherBuilder(Project project) {
    myProject = project;
  }

  public void addMatcher(Kind kind, String matchedString, boolean shouldBox, boolean isAssertionMatcher) {
    if (!isNullOrEmpty(matchedString)) {
      if (kind == ClassName && !isAssertionMatcher) {
        matchedString = getInternalName(matchedString);
      }

      if (matcherCount > 0) {
        matchers.append(", ");
      }

      if (kind == ClassName && isAssertionMatcher) {
       matchers.append("IsInstanceOf.<View>instanceOf(" + matchedString + ".class)");
      } else {
        matchers.append("with").append(kind.name()).append(kind == ClassName ? "(is(" : "(")
          .append(shouldBox ? boxString(matchedString) : matchedString).append(kind == ClassName ? "))" : ")");
      }

      matcherCount++;
    }
  }

  /**
   * Returns the name of the class that can be used in the generated test code.
   * For example, for a class foo.bar.Foo.Bar it returns foo.bar.Foo$Bar.
   */
  private String getInternalName(String className) {
    PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
    if (psiClass != null) {
      String intellijInternalName = LintIdeUtils.getInternalName(psiClass);
      if (intellijInternalName != null) {
        return intellijInternalName.replace('/', '.');
      }
    }

    // If the PsiClass was not found or its internal name was not obtained, apply a simple heuristic.
    String[] nameFragments = className.split("\\.");
    String resultClassName = "";
    for (int i = 0; i < nameFragments.length - 1; i++) {
      String fragment = nameFragments[i];
      resultClassName += fragment + (Character.isUpperCase(fragment.charAt(0)) ? "$" : ".");
    }
    resultClassName += nameFragments[nameFragments.length -1];

    return resultClassName;
  }

  public int getMatcherCount() {
    return matcherCount;
  }

  public String getMatchers() {
    return matchers.toString();
  }

}
