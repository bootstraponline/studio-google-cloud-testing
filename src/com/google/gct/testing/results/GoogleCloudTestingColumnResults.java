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

import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.ProxyFilters;
import com.intellij.execution.testframework.sm.runner.ui.statistics.ColoredRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

public class GoogleCloudTestingColumnResults extends GoogleCloudTestingBaseColumn implements Comparator<GoogleCloudTestProxy> {
  @NonNls public static final String UNDEFINED = SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.results.undefined");

  public GoogleCloudTestingColumnResults() {
    super(SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.results.title"));
  }

  @Override
  public Comparator<GoogleCloudTestProxy> getComparator() {
    return this;
  }

  @Override
  public int compare(final GoogleCloudTestProxy proxy1, final GoogleCloudTestProxy proxy2) {
    // Rule0. Test < Suite
    // Rule1. For tests: NotRun < Ignored, etc < Passed < Failure < Error < Progress < Terminated
    // Rule2. For suites: Checks count of passed, failures and errors tests: passed < failures < errors

    if (proxy1.isSuite()) {
      if (proxy2.isSuite()) {
        //proxy1 - suite
        //proxy2 - suite

        return compareSuites(proxy1,  proxy2);
      } else {
        //proxy1 - suite
        //proxy2 - test
        return +1;
      }
    } else {
      if (proxy2.isSuite()) {
        //proxy1 - test
        //proxy2 - suite
        return -1;
      } else {
        //proxy1 - test
        //proxy2 - test
        return compareTests(proxy1,  proxy2);
      }
    }
  }

  private int compareTests(final GoogleCloudTestProxy test1, final GoogleCloudTestProxy test2) {
    // Rule1. For tests: NotRun < Ignored, etc < Passed < Failure < Error < Progress < Terminated

    final int weight1 = test1.getMagnitudeInfo().getSortWeight();
    final int weight2 = test2.getMagnitudeInfo().getSortWeight();

    return compareInt(weight1, weight2);
  }

  private int compareSuites(final GoogleCloudTestProxy suite1,
                            final GoogleCloudTestProxy suite2) {
    // Compare errors
    final int errors1 = suite1.getChildren(ProxyFilters.FILTER_ERRORS).size();
    final int errors2 = suite2.getChildren(ProxyFilters.FILTER_ERRORS).size();
    final int errorsComparison = compareInt(errors1, errors2);
    // If not equal return it
    if (errorsComparison != 0) {
      return errorsComparison;
    }

    // Compare failures
    final int failures1 = suite1.getChildren(ProxyFilters.FILTER_FAILURES).size();
    final int failures2 = suite2.getChildren(ProxyFilters.FILTER_FAILURES).size();
    final int failuresComparison = compareInt(failures1, failures2);
    // If not equal return it
    if (failuresComparison != 0) {
      return failuresComparison;
    }

    // otherwise check passed count
    final int passed1 = suite1.getChildren(ProxyFilters.FILTER_PASSED).size();
    final int passed2 = suite2.getChildren(ProxyFilters.FILTER_PASSED).size();

    return compareInt(passed1, passed2);
  }

  private int compareInt(final int first, final int second) {
    if (first < second) {
      return -1;
    } else if (first > second) {
      return +1;
    } else {
      return 0;
    }
  }

  @Override
  public String valueOf(final GoogleCloudTestProxy testProxy) {
    return UNDEFINED;
  }

  @Override
  public TableCellRenderer getRenderer(final GoogleCloudTestProxy proxy) {
    return new ResultsCellRenderer(proxy);
  }

  public static class ResultsCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    private final GoogleCloudTestProxy myProxy;

    public ResultsCellRenderer(final GoogleCloudTestProxy proxy) {
      myProxy = proxy;
    }

    @Override
    public void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      if (myProxy.isSuite()) {
        // for suite returns brief statistics
        GoogleCloudTestsPresentationUtil.appendSuiteStatusColorPresentation(myProxy, this);
      } else {
        // for test returns test status string
        GoogleCloudTestsPresentationUtil.appendTestStatusColorPresentation(myProxy, this);
      }
    }
  }
}
