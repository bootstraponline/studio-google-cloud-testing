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

import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.BaseTableView;
import com.intellij.ui.table.TableView;
import com.intellij.util.config.Storage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class GoogleCloudTestingStatisticsPanel implements DataProvider {
  public static final DataKey<GoogleCloudTestingStatisticsPanel> SM_TEST_RUNNER_STATISTICS  = DataKey.create("SM_TEST_RUNNER_STATISTICS");

  private TableView<GoogleCloudTestProxy> myStatisticsTableView;
  private JPanel myContentPane;
  private final Storage.PropertiesComponentStorage myStorage = new Storage.PropertiesComponentStorage("sm_test_statistics_table_columns");

  private final GoogleCloudTestingStatisticsTableModel myTableModel;
  private final List<GoogleCloudTestingPropagateSelectionHandler> myPropagateSelectionHandlers = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Project myProject;
  private final TestFrameworkRunningModel myFrameworkRunningModel;

  public GoogleCloudTestingStatisticsPanel(final Project project, final TestFrameworkRunningModel model) {
    myProject = project;
    myTableModel = new GoogleCloudTestingStatisticsTableModel();
    myStatisticsTableView.setModelAndUpdateColumns(myTableModel);
    myFrameworkRunningModel = model;

    final Runnable gotoSuiteOrParentAction = createGotoSuiteOrParentAction();
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        gotoSuiteOrParentAction.run();
        return true;
      }
    }.installOn(myStatisticsTableView);

    // Fire selection changed and move focus on SHIFT+ENTER
    final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    SMRunnerUtil.registerAsAction(shiftEnterKey, "select-test-proxy-in-test-view",
                            new Runnable() {
                              @Override
                              public void run() {
                                showSelectedProxyInTestsTree();
                              }
                            },
                            myStatisticsTableView);

    // Expand selected or go to parent on ENTER
    final KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    SMRunnerUtil.registerAsAction(enterKey, "go-to-selected-suite-or-parent",
                            gotoSuiteOrParentAction,
                            myStatisticsTableView);
    // Contex menu in Table
    PopupHandler.installPopupHandler(myStatisticsTableView, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
    // set this statistic tab as dataprovider for test's table view
    DataManager.registerDataProvider(myStatisticsTableView, this);
  }

  public void addPropagateSelectionListener(final GoogleCloudTestingPropagateSelectionHandler handler) {
    myPropagateSelectionHandlers.add(handler);
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public GoogleCloudTestEventsListener createTestEventsListener() {
    return new GoogleCloudTestEventsAdapter() {
      @Override
      public void onSuiteStarted(@NotNull final GoogleCloudTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onSuiteFinished(@NotNull final GoogleCloudTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestStarted(@NotNull final GoogleCloudTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestFinished(@NotNull final GoogleCloudTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      private void updateAndRestoreSelection() {
        SMRunnerUtil.addToInvokeLater(new Runnable() {
          @Override
          public void run() {
            BaseTableView.restore(myStorage, myStatisticsTableView);
            // statisticsTableView can be null in JUnit tests
            final GoogleCloudTestProxy oldSelection = myStatisticsTableView.getSelectedObject();

            // update module
            myTableModel.updateModel();

            // restore selection if it is possible
            if (oldSelection != null) {
              final int newRow = myTableModel.getIndexOf(oldSelection);
              if (newRow > -1) {
                myStatisticsTableView.setRowSelectionInterval(newRow, newRow);
              }
            }
          }
        });
      }
    };
  }

  @Override
  public Object getData(@NonNls final String dataId) {
    if (SM_TEST_RUNNER_STATISTICS.is(dataId)) {
      return this;
    }
    return TestsUIUtil.getData(getSelectedItem(), dataId, myFrameworkRunningModel);
  }

  /**
   * On event - change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   * @return Listener
   */
  public GoogleCloudTestingPropagateSelectionHandler createSelectMeListener() {
    return new GoogleCloudTestingPropagateSelectionHandler() {
      @Override
      public void handlePropagateSelectionRequest(@Nullable final GoogleCloudTestProxy selectedTestProxy,
                                    @NotNull final Object sender,
                                    final boolean requestFocus) {
        selectProxy(selectedTestProxy, sender, requestFocus);
      }
    };
  }

  public void selectProxy(@Nullable final GoogleCloudTestProxy selectedTestProxy,
                          @NotNull final Object sender,
                          final boolean requestFocus) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        // Select tab if focus was requested
        if (requestFocus) {
          IdeFocusManager.getInstance(myProject).requestFocus(myStatisticsTableView, true);
        }

        // Select proxy in table
        selectProxy(selectedTestProxy);
      }
    });
  }

  protected void showSelectedProxyInTestsTree() {
    final Collection<GoogleCloudTestProxy> proxies = myStatisticsTableView.getSelection();
    if (proxies.isEmpty()) {
      return;
    }
    final GoogleCloudTestProxy proxy = proxies.iterator().next();
    myStatisticsTableView.clearSelection();
    fireOnPropagateSelection(proxy);
  }

  protected Runnable createGotoSuiteOrParentAction() {
    // Expand selected or go to parent
    return new Runnable() {
      @Override
      public void run() {
        final GoogleCloudTestProxy selectedProxy = getSelectedItem();
        if (selectedProxy == null) {
          return;
        }

        final int i = myStatisticsTableView.getSelectedRow();
        assert i >= 0; //because something is selected

        // if selected element is suite - we should expand it
        if (selectedProxy.isSuite()) {
          // expand and select first (Total) row
          showInTableAndSelectRow(selectedProxy, selectedProxy);
        }
      }
    };
  }

  protected void selectProxy(@Nullable final GoogleCloudTestProxy selectedTestProxy) {
    // Send event to model
    myTableModel.updateModelOnProxySelected(selectedTestProxy);

    // Now we want to select proxy in table (if it is possible)
    if (selectedTestProxy != null) {
      findAndSelectInTable(selectedTestProxy);
    }
  }
  /**
   * Selects row in table
   * @param rowIndex Row's index
   */
  protected void selectRow(final int rowIndex) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        // updates model
        myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);

        // Scroll to visible
        TableUtil.scrollSelectionToVisible(myStatisticsTableView);
      }
    });
  }

  /**
   * Selects row in table
   */
  protected void selectRowOf(final GoogleCloudTestProxy proxy) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final int indexOf = myTableModel.getIndexOf(proxy);
        if (indexOf > -1) {
          final int rowIndex = myStatisticsTableView.convertRowIndexToView(indexOf);
          myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex >= 0 ? rowIndex : 0);
          // Scroll to visible
          TableUtil.scrollSelectionToVisible(myStatisticsTableView);
        }
      }
    });
  }

  @Nullable
  protected GoogleCloudTestProxy getSelectedItem() {
    return myStatisticsTableView.getSelectedObject();
  }

  protected List<GoogleCloudTestProxy> getTableItems() {
    return myTableModel.getItems();
  }

  private void findAndSelectInTable(final GoogleCloudTestProxy proxy) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final int rowIndex = myTableModel.getIndexOf(proxy);
        if (rowIndex >= 0) {
          myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);
        }
      }
    });
  }

  private void fireOnPropagateSelection(final GoogleCloudTestProxy selectedTestProxy) {
    for (GoogleCloudTestingPropagateSelectionHandler handler : myPropagateSelectionHandlers) {
      handler.handlePropagateSelectionRequest(selectedTestProxy, this, true);
    }
  }

  private void createUIComponents() {
    myStatisticsTableView = new TableView<GoogleCloudTestProxy>();
  }

  private void showInTableAndSelectRow(final GoogleCloudTestProxy suite, final GoogleCloudTestProxy suiteProxy) {
    selectProxy(suite);
    selectRowOf(suiteProxy);
  }

  public void doDispose() {
    BaseTableView.store(myStorage, myStatisticsTableView);
  }
}
