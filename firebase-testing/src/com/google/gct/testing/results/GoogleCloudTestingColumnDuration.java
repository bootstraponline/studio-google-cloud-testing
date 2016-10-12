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
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;

public class GoogleCloudTestingColumnDuration extends GoogleCloudTestingBaseColumn implements Comparator<GoogleCloudTestProxy> {
  public GoogleCloudTestingColumnDuration() {
    super(SMTestsRunnerBundle.message("sm.test.runner.ui.tabs.statistics.columns.duration.title"));
  }

  @Override
  public String valueOf(final GoogleCloudTestProxy testProxy) {
    return GoogleCloudTestsPresentationUtil.getDurationPresentation(testProxy);
  }

  @Override
  @Nullable
  public Comparator<GoogleCloudTestProxy> getComparator(){
    return this;
  }

  @Override
  public int compare(final GoogleCloudTestProxy proxy1, final GoogleCloudTestProxy proxy2) {
    final Long duration1 = proxy1.getDuration();
    final Long duration2 = proxy2.getDuration();

    if (duration1 == null) {
      return duration2 == null ? 0 : -1;
    }
    if (duration2 == null) {
      return +1;
    }
    return duration1.compareTo(duration2);
  }


  @Override
  public TableCellRenderer getRenderer(final GoogleCloudTestProxy proxy) {
    return new DurationCellRenderer();
  }

  public static class DurationCellRenderer extends ColoredTableCellRenderer {

    public DurationCellRenderer() {
    }

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
