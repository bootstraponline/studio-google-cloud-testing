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
import com.intellij.execution.testframework.sm.runner.ui.statistics.ColoredRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

public class GoogleCloudTestingColumnTest extends GoogleCloudTestingBaseColumn implements Comparator<GoogleCloudTestProxy>{
  public GoogleCloudTestingColumnTest() {
    super(SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.test.title"));
  }

  @Override
  @NotNull
  public String valueOf(final GoogleCloudTestProxy testProxy) {
    return testProxy.getPresentableName();
  }

  @Override
  @Nullable
  public Comparator<GoogleCloudTestProxy> getComparator(){
    return this;
  }

  @Override
  public int compare(final GoogleCloudTestProxy proxy1, final GoogleCloudTestProxy proxy2) {
    return proxy1.getName().compareTo(proxy2.getName());
  }

  @Override
  public TableCellRenderer getRenderer(final GoogleCloudTestProxy proxy) {
    return new TestsCellRenderer();
  }

  public static class TestsCellRenderer extends ColoredTableCellRenderer implements ColoredRenderer {
    @Override
    public void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      assert value != null;

      append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
