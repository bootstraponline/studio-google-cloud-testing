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
package com.google.gct.testing;

import com.google.common.base.Function;
import com.google.gct.testing.dimension.GoogleCloudTestingDimension;
import com.google.gct.testing.dimension.GoogleCloudTestingType;
import com.google.gct.testing.dimension.GoogleCloudTestingTypeGroup;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION;


@SuppressWarnings("ALL")
public class TwoPanelTree extends MouseAdapter implements ListSelectionListener, KeyListener {

  private final DefaultListModel listModel;
  // Main Panel which contains everything
  private final JPanel myPanel;
  // Contains a list of the roots
  private final JPanel leftPanel;
  // Contains the tree view
  private final JPanel rightPanel;
  // Contains the left and right panels
  private final Splitter mySplitter;
  // Panel to contain the splitter to display the border.
  private final JPanel mySplitterPanel;
  // Contains controls
  private final JPanel topPanel;
  // Contains additional information
  private final JPanel bottomPanel;

  // Trees
  private Map<GoogleCloudTestingDimension,CheckboxTree> treeMap;
  private JBList list;

  // The configuration which we need to keep in sync with this view
  private GoogleCloudTestingConfiguration configuration;

  // Listeners
  List<TwoPanelTreeSelectionListener> listeners = new LinkedList<TwoPanelTreeSelectionListener>();
  private JLabel myConfigurationCountLabel;

  public TwoPanelTree(GoogleCloudTestingConfiguration configuration) {
    this.configuration = configuration;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setPreferredSize(new Dimension(UIUtil.isUnderDarcula() ? 535 : 520, 240));
    mySplitterPanel = new JPanel(new BorderLayout());
    mySplitterPanel.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    mySplitter = new Splitter(false);
    mySplitter.getDivider().setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    mySplitter.setProportion(0.3f);
    leftPanel = new JPanel(new BorderLayout());
    rightPanel = new JPanel(new BorderLayout());

    FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
    flowLayout.setHgap(15);
    flowLayout.setVgap(10);
    topPanel = new JPanel(flowLayout);
    bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    listModel = new DefaultListModel();

    init();
  }

  public void init() {
    myPanel.add(topPanel, BorderLayout.NORTH);
    populateTopPanel();
    bottomPanel.setBackground(UIUtil.getListBackground());
    myPanel.add(bottomPanel, BorderLayout.SOUTH);
    populateBottomPanel();

    mySplitter.setFirstComponent(leftPanel);
    JScrollPane scrollPane = new JScrollPane(rightPanel);
    scrollPane.setBorder(null);
    mySplitter.setSecondComponent(scrollPane);
    mySplitterPanel.add(mySplitter, BorderLayout.CENTER);
    myPanel.add(mySplitterPanel, BorderLayout.CENTER);

    treeMap = new HashMap<GoogleCloudTestingDimension, CheckboxTree>();
    list = new JBList();

    // Add the dimensions
    for (GoogleCloudTestingDimension dimension : configuration.getDimensions()) {
      addDimension(dimension);
    }

    leftPanel.add(list, BorderLayout.CENTER);
    list.setModel(listModel);
    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    list.setCellRenderer(new EnhancedCellRenderer());

    list.addListSelectionListener(this);

    // Make an inital selection
    list.setSelectedIndex(0);
  }

  private void populateBottomPanel() {
    bottomPanel.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor(), 1));
    // TODO: Update number of configurations when appropriate
    myConfigurationCountLabel = new JLabel();
    updateConfigurationCountLabel();
    bottomPanel.add(myConfigurationCountLabel);
  }

  double linearGrowth = 0.05;
  double logGrowth = 0.02;
  Random rand = new Random(42);

  private double computeCoverage(int combinations) {
    if (combinations == 0) {
      return 0;
    }
    double growthFactor = 1d / combinations * 0.1;
    double prevValue = computeCoverage(combinations - 1);
    return prevValue + (1d - prevValue) * growthFactor;
  }

  private void updateConfigurationCountLabel() {
    int numberOfConfigurations = configuration.countCombinations();
    double coverage = computeCoverage(configuration.countCombinationsCollapsingOrientation()) * 100d;
    //TODO: Put back the covered % after it is not fake (or it is needed for demo purposes).
    //myConfigurationCountLabel.setText("<html>Tests will run on <b>" + numberOfConfigurations + " configurations</b> and cover <b>" +
    //                                  (int) Math.ceil(coverage) + "%</b> of current users.</html>");
    myConfigurationCountLabel.setText("<html>Tests will run on <b>" + numberOfConfigurations + " configurations</b>.</html>");
    if (numberOfConfigurations < 1) {
      myConfigurationCountLabel.setForeground(JBColor.RED);
    } else {
      myConfigurationCountLabel.setForeground(UIUtil.getActiveTextColor());
    }
  }

  private void populateTopPanel() {
    Component add = topPanel.add(makeTextButton("Select all", new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (getSelectedDimension().isEditable()) {
          getSelectedDimension().enableAll();
          updateCurrentCheckboxTree(new Function<CheckedTreeNode, Void>() {
            @Override
            public Void apply(CheckedTreeNode treeNode) {
              treeNode.setChecked(true);
              return null;
            }
          });
        }
      }
    }));
    topPanel.add(makeTextButton("Select none", new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (getSelectedDimension().isEditable()) {
          getSelectedDimension().disableAll();
          updateCurrentCheckboxTree(new Function<CheckedTreeNode, Void>() {
            @Override
            public Void apply(CheckedTreeNode treeNode) {
              treeNode.setChecked(false);
              return null;
            }
          });
        }
      }
    }));
    //topPanel.add(makeTextButton("Hide unselected", new MouseAdapter() {
    //  public void mouseClicked(final MouseEvent e) {
    //    System.out.println("Clicked " + "Hide unselected");
    //  }
    //}));
  }

  private void updateCurrentCheckboxTree(Function<CheckedTreeNode, Void> updateFunction) {
    GoogleCloudTestingDimension selectedDimension = getSelectedDimension();
    CheckboxTree currentCheckboxTree = treeMap.get(selectedDimension);
    CheckedTreeNode rootNode = (CheckedTreeNode) currentCheckboxTree.getModel().getRoot();
    updateNode(rootNode, updateFunction);
    updateUI(currentCheckboxTree);
    selectedDimension.dimensionChanged();
    updateConfigurationCountLabel();
    list.updateUI();
  }

  private void updateNode(CheckedTreeNode node, Function<CheckedTreeNode, Void> updateFunction) {
    for (int childIndex = 0; childIndex < node.getChildCount(); childIndex++) {
      CheckedTreeNode child = (CheckedTreeNode)node.getChildAt(childIndex);
      updateFunction.apply(child);
      updateNode(child, updateFunction);
    }
  }

  private void updateUI(CheckboxTree checkboxTree) {
    checkboxTree.updateUI();
    BasicTreeUI ui = (BasicTreeUI) checkboxTree.getUI();
    //ui.setRightChildIndent(0);
    //ui.setLeftChildIndent(0);
  }

  private JLabel makeTextButton(final String text, MouseAdapter mouseAdapter) {
    JLabel label = new JLabel(text);
    label.setForeground(JBColor.BLUE);
    label.setFont(new Font("Sans", Font.PLAIN, 11));
    label.setCursor(new Cursor(Cursor.HAND_CURSOR));

    label.addMouseListener(mouseAdapter);
    return label;
  }

  public void addDimension(GoogleCloudTestingDimension dimension) {

    // add this dimension as a list item
    listModel.addElement(dimension);

    // create a new tree with root node for this dimension
    CheckboxTree tree = new CheckboxTree();
    CheckedTreeNode rootNode = new CheckedTreeNode(dimension); // Not really necessary since parent not visible
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    tree.setModel(treeModel);

    // Tweak tree appearence
    tree.setRootVisible(false);
    updateUI(tree);

    tree.getSelectionModel().setSelectionMode(SINGLE_TREE_SELECTION);

    // Add each supported type to the tree.
    List<? extends GoogleCloudTestingTypeGroup> supportedGroups = dimension.getSupportedGroups();
    for (GoogleCloudTestingTypeGroup group : supportedGroups) {
      List<GoogleCloudTestingType> types = group.getTypes();
      if (types.size() == 1) {
        addChildNode(rootNode, types.get(0), dimension);
      } else {
        CheckedTreeNode groupNode = new CheckedTreeNode(group);
        rootNode.add(groupNode);
        groupNode.setEnabled(dimension.isEditable());
        for (GoogleCloudTestingType type : types) {
          addChildNode(groupNode, type, dimension);
        }
      }
    }

    // record the tree
    treeMap.put(dimension, tree);

    tree.setCellRenderer(new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(final JTree tree,
                                    Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {

        if (value instanceof DefaultMutableTreeNode) {
          Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof GoogleCloudTestingType) {
            GoogleCloudTestingType googleCloudTestingType = (GoogleCloudTestingType)userObject;
            getTextRenderer().append(googleCloudTestingType.getConfigurationDialogDisplayName());
            updateTreeState(tree, (CheckedTreeNode)value, googleCloudTestingType);
          } else if (userObject instanceof GoogleCloudTestingTypeGroup) {
            GoogleCloudTestingTypeGroup group = (GoogleCloudTestingTypeGroup)userObject;
            getTextRenderer().append(group.getName());
            GoogleCloudTestingType type = (GoogleCloudTestingType) ((CheckedTreeNode) ((DefaultMutableTreeNode)value).getChildAt(0)).getUserObject();
            updateTreeState(tree, (CheckedTreeNode)value, type);
          }
        }
      }

      private void updateTreeState(JTree tree, CheckedTreeNode node, GoogleCloudTestingType googleCloudTestingType) {
        if (node.isEnabled()) {
          //getTextRenderer().setForeground(Color.BLACK);
        } else {
          //getTextRenderer().setForeground(Color.LIGHT_GRAY);
          GoogleCloudTestingDimension googleCloudTestingDimension = (GoogleCloudTestingDimension)((CheckedTreeNode) tree.getModel().getRoot()).getUserObject();
          boolean isChecked = googleCloudTestingDimension.getEnabledTypes().contains(googleCloudTestingType);
          node.setChecked(isChecked);
          for (int childIndex = 0; childIndex < node.getChildCount(); childIndex++) {
            ((CheckedTreeNode) node.getChildAt(childIndex)).setChecked(isChecked);
          }
        }
      }
    });

    treeModel.reload();

    // add listeners
    tree.addMouseListener(this);
    tree.addKeyListener(this);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void addChildNode(CheckedTreeNode parentNode, GoogleCloudTestingType type, GoogleCloudTestingDimension dimension) {
    CheckedTreeNode newNode = new CheckedTreeNode(type);
    parentNode.add(newNode);
    newNode.setChecked(dimension.getEnabledTypes().contains(type));
    newNode.setEnabled(dimension.isEditable());
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    // Switch to the tree corresponding to the selected dimension
    rightPanel.removeAll();
    CheckboxTree currentTree = treeMap.get(getSelectedDimension());
    rightPanel.add(currentTree, BorderLayout.CENTER);
    rightPanel.updateUI();
    for (TwoPanelTreeSelectionListener listener : listeners) {
      listener.dimensionSelectionChanged(new TwoPanelTreeDimensionSelectionEvent(getSelectedDimension()));
    }
  }

  private GoogleCloudTestingDimension getSelectedDimension() {
    return (GoogleCloudTestingDimension) list.getModel().getElementAt(list.getSelectedIndex());
  }

  public void addSelectionListener(TwoPanelTreeSelectionListener treeSelectionListener) {
    listeners.add(treeSelectionListener);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    updateState();
  }

  @Override
  public void keyTyped(KeyEvent e) {
    updateState();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    updateState();
  }

  @Override
  public void keyReleased(KeyEvent e) {
    updateState();
  }

  private void updateState() {
    GoogleCloudTestingDimension currentDimension = getSelectedDimension();
    CheckboxTree currentTree = treeMap.get(currentDimension);

    if (currentTree == null || currentTree.getSelectionPath() == null) {
      return;
    }

    CheckedTreeNode selectedNode = (CheckedTreeNode) currentTree.getSelectionPath().getLastPathComponent();
    Object userObject = selectedNode.getUserObject();
    if (userObject instanceof GoogleCloudTestingType) {
      GoogleCloudTestingType clickedType = (GoogleCloudTestingType)userObject;
      if (selectedNode.isEnabled()) {
        currentDimension.setEnabled(clickedType, selectedNode.isChecked());
        currentDimension.dimensionChanged();
      }
      for (TwoPanelTreeSelectionListener listener : listeners) {
        listener.typeSelectionChanged(new TwoPanelTreeTypeSelectionEvent(currentDimension, clickedType));
      }
    } else {
      GoogleCloudTestingTypeGroup clickedGroup = (GoogleCloudTestingTypeGroup) userObject;
      if (selectedNode.isEnabled()) {
        for (int childIndex = 0; childIndex < selectedNode.getChildCount(); childIndex++) {
          CheckedTreeNode child = (CheckedTreeNode)selectedNode.getChildAt(childIndex);
          currentDimension.setEnabled((GoogleCloudTestingType) child.getUserObject(), child.isChecked());
        }
        currentDimension.dimensionChanged();
      }
      for (TwoPanelTreeSelectionListener listener : listeners) {
        listener.groupSelectionChanged(new TwoPanelTreeTypeGroupSelectionEvent(currentDimension, clickedGroup));
      }
    }

    updateConfigurationCountLabel();
    list.updateUI();
  }

  class EnhancedCellRenderer implements ListCellRenderer {
    protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

    public Component getListCellRendererComponent(JList list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {

      JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, index,
                                                                              isSelected, cellHasFocus);
      GoogleCloudTestingDimension dimension = (GoogleCloudTestingDimension) value;

      renderer.setIcon(dimension.getIcon());
      renderer.setText(dimension.getDisplayName() +
                       " (" + dimension.getEnabledTypes().size() + "/" + dimension.getSupportedDomain().size() + ")");

      if (dimension.getEnabledTypes().size() < 1) {
        renderer.setForeground(JBColor.RED);
        renderer.setFont(renderer.getFont().deriveFont(Font.BOLD));
      }

      return renderer;
    }
  }
}

