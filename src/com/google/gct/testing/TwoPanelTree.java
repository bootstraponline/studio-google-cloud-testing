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

import com.google.api.client.util.Maps;
import com.google.common.base.Function;
import com.google.gct.testing.dimension.CloudConfigurationDimension;
import com.google.gct.testing.dimension.CloudTestingType;
import com.google.gct.testing.dimension.CloudTestingTypeGroup;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTreeBase.CheckPolicy;
import com.intellij.ui.CheckboxTreeBase.NodeState;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.android.tools.idea.run.CloudConfiguration.Kind.SINGLE_DEVICE;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
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
  private Map<CloudConfigurationDimension, CheckboxTree> treeMap;
  private final Map<CheckboxTree, Boolean> userToggledTrees = Maps.newHashMap();
  private boolean isAutoToggled = false;
  private JBList list;

  // The configuration which we need to keep in sync with this view
  private CloudConfigurationImpl configuration;

  // Listeners
  List<TwoPanelTreeSelectionListener> listeners = new LinkedList<TwoPanelTreeSelectionListener>();
  private JLabel myConfigurationCountLabel = new JLabel();

  public TwoPanelTree(CloudConfigurationImpl configuration) {
    this.configuration = configuration;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setPreferredSize(new Dimension(520, 240));
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
    if (configuration.getKind() != SINGLE_DEVICE) {
      populateTopPanel();
      bottomPanel.setBackground(UIUtil.getListBackground());
      myPanel.add(bottomPanel, BorderLayout.SOUTH);
      populateBottomPanel();
    }

    mySplitter.setFirstComponent(leftPanel);
    JScrollPane scrollPane = new JScrollPane(rightPanel);
    scrollPane.setBorder(null);
    mySplitter.setSecondComponent(scrollPane);
    mySplitterPanel.add(mySplitter, BorderLayout.CENTER);
    myPanel.add(mySplitterPanel, BorderLayout.CENTER);

    treeMap = new HashMap<CloudConfigurationDimension, CheckboxTree>();
    list = new JBList();

    // Add the dimensions
    for (CloudConfigurationDimension dimension : configuration.getDimensions()) {
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
    updateConfigurationCountLabel();
    bottomPanel.add(myConfigurationCountLabel);
  }

  double linearGrowth = 0.05;
  double logGrowth = 0.02;
  Random rand = new Random(42);

  // TODO: This blows off for a reasonably big number of combinations (e.g., >15K).
  private double computeCoverage(int combinations) {
    if (combinations == 0) {
      return 0;
    }
    double growthFactor = 1d / combinations * 0.1;
    double prevValue = computeCoverage(combinations - 1);
    return prevValue + (1d - prevValue) * growthFactor;
  }

  private void updateConfigurationCountLabel() {
    int numberOfConfigurations = configuration.getDeviceConfigurationCount();
    //double coverage = computeCoverage(configuration.countCombinationsCollapsingOrientation()) * 100d;
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
  }

  private void updateCurrentCheckboxTree(Function<CheckedTreeNode, Void> updateFunction) {
    CloudConfigurationDimension selectedDimension = getSelectedDimension();
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
  }

  private JLabel makeTextButton(final String text, MouseAdapter mouseAdapter) {
    JLabel label = new JLabel(text);
    label.setForeground(JBColor.BLUE);
    label.setFont(new Font("Sans", Font.PLAIN, 11));
    label.setCursor(new Cursor(Cursor.HAND_CURSOR));

    label.addMouseListener(mouseAdapter);
    return label;
  }

  public void addDimension(CloudConfigurationDimension dimension) {

    // Add this dimension as a list item.
    listModel.addElement(dimension);

    CheckedTreeNode rootNode = new CheckedTreeNode(dimension); // Not really necessary since parent not visible
    CheckPolicy radioButtonsCheckPolicy = new CheckPolicy(false, false, false, false);
    // Create a new tree with root node for this dimension.
    final CheckboxTree tree = configuration.getKind() == SINGLE_DEVICE
                              ? new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(){}, rootNode, radioButtonsCheckPolicy)
                              : new CheckboxTree();
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    tree.setModel(treeModel);

    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        if (!isAutoToggled) {
          userToggledTrees.put(tree, true);
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        if (!isAutoToggled) {
          userToggledTrees.put(tree, true);
        }
      }
    });

    // Tweak tree appearence
    tree.setRootVisible(false);
    updateUI(tree);

    tree.getSelectionModel().setSelectionMode(SINGLE_TREE_SELECTION);

    // Add each supported type to the tree.
    List<? extends CloudTestingTypeGroup> supportedGroups = dimension.getSupportedGroups();
    for (CloudTestingTypeGroup group : supportedGroups) {
      List<CloudTestingType> types = group.getTypes();
      if (types.size() == 1 && !dimension.shouldBeAlwaysGrouped()) {
        addChildNode(rootNode, types.get(0), dimension);
      } else {
        CheckedTreeNode groupNode = new CheckedTreeNode(group);
        rootNode.add(groupNode);
        groupNode.setEnabled(dimension.isEditable() && configuration.getKind() != SINGLE_DEVICE);
        for (CloudTestingType type : types) {
          addChildNode(groupNode, type, dimension);
        }
      }
    }

    // Record the tree.
    treeMap.put(dimension, tree);

    tree.setCellRenderer(configuration.getKind() == SINGLE_DEVICE ? new RadioButtonRenderer() : new CheckBoxRenderer());

    treeModel.reload();

    // add listeners
    tree.addMouseListener(this);
    tree.addKeyListener(this);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private void addChildNode(CheckedTreeNode parentNode, CloudTestingType type, CloudConfigurationDimension dimension) {
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

  private CloudConfigurationDimension getSelectedDimension() {
    return (CloudConfigurationDimension) list.getModel().getElementAt(list.getSelectedIndex());
  }

  public void addSelectionListener(TwoPanelTreeSelectionListener treeSelectionListener) {
    listeners.add(treeSelectionListener);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    CheckboxTree currentTree = treeMap.get(getSelectedDimension());

    if (currentTree == null || currentTree.getSelectionPath() == null) {
      return;
    }

    CheckedTreeNode selectedNode = (CheckedTreeNode) currentTree.getSelectionPath().getLastPathComponent();
    if (!selectedNode.isEnabled() && selectedNode.getUserObject() instanceof CloudTestingTypeGroup) {
      if (userToggledTrees.get(currentTree) != Boolean.TRUE) {
        isAutoToggled = true;
        TreePath selectedPath = new TreePath(selectedNode.getPath());
        if (currentTree.isExpanded(selectedPath)) {
          currentTree.collapsePath(selectedPath);
        } else {
          currentTree.expandPath(selectedPath);
        }
        isAutoToggled = false;
      }
    }
    userToggledTrees.put(currentTree, false);

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
    CheckboxTree currentTree = treeMap.get(getSelectedDimension());

    if (currentTree == null || currentTree.getSelectionPath() == null) {
      return;
    }

    if (configuration.getKind() == SINGLE_DEVICE) {
      updateRadioButtonTreeState(currentTree);
    } else {
      updateCheckBoxTreeState(currentTree);
    }

    updateConfigurationCountLabel();
    list.updateUI();
  }

  private void updateCheckBoxTreeState(CheckboxTree currentTree) {
    CloudConfigurationDimension currentDimension = getSelectedDimension();
    CheckedTreeNode selectedNode = (CheckedTreeNode) currentTree.getSelectionPath().getLastPathComponent();
    Object userObject = selectedNode.getUserObject();
    if (userObject instanceof CloudTestingType) {
      CloudTestingType clickedType = (CloudTestingType)userObject;
      if (selectedNode.isEnabled()) {
        currentDimension.setEnabled(clickedType, selectedNode.isChecked());
        currentDimension.dimensionChanged();
      }
      for (TwoPanelTreeSelectionListener listener : listeners) {
        listener.typeSelectionChanged(new TwoPanelTreeTypeSelectionEvent(currentDimension, clickedType));
      }
    } else {
      CloudTestingTypeGroup clickedGroup = (CloudTestingTypeGroup) userObject;
      if (selectedNode.isEnabled()) {
        for (int childIndex = 0; childIndex < selectedNode.getChildCount(); childIndex++) {
          CheckedTreeNode child = (CheckedTreeNode)selectedNode.getChildAt(childIndex);
          currentDimension.setEnabled((CloudTestingType) child.getUserObject(), child.isChecked());
        }
        currentDimension.dimensionChanged();
      }
      for (TwoPanelTreeSelectionListener listener : listeners) {
        listener.groupSelectionChanged(new TwoPanelTreeTypeGroupSelectionEvent(currentDimension, clickedGroup));
      }
    }
  }

  private void updateRadioButtonTreeState(CheckboxTree currentTree) {
    CloudConfigurationDimension currentDimension = getSelectedDimension();
    CheckedTreeNode selectedNode = (CheckedTreeNode) currentTree.getSelectionPath().getLastPathComponent();
    Object userObject = selectedNode.getUserObject();
    if (userObject instanceof CloudTestingType) {
      CloudTestingType clickedType = (CloudTestingType)userObject;
      if (selectedNode.isEnabled() && (selectedNode.isChecked() || !isAnyLeafChecked(currentTree))) {
        uncheckAll(currentTree);
        selectedNode.setChecked(true);
        currentDimension.disableAll();
        currentDimension.enable(clickedType);
        currentDimension.dimensionChanged();
      }
      for (TwoPanelTreeSelectionListener listener : listeners) {
        listener.typeSelectionChanged(new TwoPanelTreeTypeSelectionEvent(currentDimension, clickedType));
      }
    } else {
      CloudTestingTypeGroup clickedGroup = (CloudTestingTypeGroup) userObject;
      for (TwoPanelTreeSelectionListener listener : listeners) {
        listener.groupSelectionChanged(new TwoPanelTreeTypeGroupSelectionEvent(currentDimension, clickedGroup));
      }
    }
  }

  private void uncheckAll(CheckboxTree currentTree) {
    TreeNode root = (TreeNode)currentTree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      CheckedTreeNode firstLevelChild = (CheckedTreeNode)root.getChildAt(i);
      firstLevelChild.setChecked(false);
      if (!firstLevelChild.isLeaf()) {
        for (int j = 0; j < firstLevelChild.getChildCount(); j++) {
          ((CheckedTreeNode)firstLevelChild.getChildAt(j)).setChecked(false);
        }
      }
    }
  }

  private boolean isAnyLeafChecked(CheckboxTree currentTree) {
    TreeNode root = (TreeNode)currentTree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      CheckedTreeNode firstLevelChild = (CheckedTreeNode)root.getChildAt(i);
      if (firstLevelChild.isChecked()) {
        return true;
      }
      if (!firstLevelChild.isLeaf()) {
        for (int j = 0; j < firstLevelChild.getChildCount(); j++) {
          if (((CheckedTreeNode)firstLevelChild.getChildAt(j)).isChecked()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static class CheckBoxRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(final JTree tree, Object value, final boolean selected, final boolean expanded, final boolean leaf,
                                  final int row, final boolean hasFocus) {

      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof CloudTestingType) {
          CloudTestingType cloudTestingType = (CloudTestingType)userObject;
          getTextRenderer().append(cloudTestingType.getConfigurationDialogDisplayName(),
                                   ((CheckedTreeNode) value).isChecked() ? REGULAR_BOLD_ATTRIBUTES : REGULAR_ATTRIBUTES);
          updateTreeState(tree, (CheckedTreeNode)value, cloudTestingType);
        } else if (userObject instanceof CloudTestingTypeGroup) {
          CloudTestingTypeGroup group = (CloudTestingTypeGroup)userObject;
          getTextRenderer().append(group.getName(),
                                   hasCheckedChildren((CheckedTreeNode) value) ? REGULAR_BOLD_ATTRIBUTES : REGULAR_ATTRIBUTES);
          CloudTestingType type = (CloudTestingType) ((CheckedTreeNode) ((DefaultMutableTreeNode)value).getChildAt(0)).getUserObject();
          updateTreeState(tree, (CheckedTreeNode)value, type);
        }
      }
    }

    private boolean hasCheckedChildren(CheckedTreeNode node) {
      for (int i = 0; i < node.getChildCount(); i++) {
        if (((CheckedTreeNode)node.getChildAt(i)).isChecked()) {
          return true;
        }
      }
      return false;
    }

    private void updateTreeState(JTree tree, CheckedTreeNode node, CloudTestingType cloudTestingType) {
      if (node.isEnabled()) {
        //getTextRenderer().setForeground(Color.BLACK);
      } else {
        //getTextRenderer().setForeground(Color.LIGHT_GRAY);
        CloudConfigurationDimension dimension = (CloudConfigurationDimension)((CheckedTreeNode) tree.getModel().getRoot()).getUserObject();
        boolean isChecked = dimension.getEnabledTypes().contains(cloudTestingType);
        node.setChecked(isChecked);
        for (int childIndex = 0; childIndex < node.getChildCount(); childIndex++) {
          ((CheckedTreeNode) node.getChildAt(childIndex)).setChecked(isChecked);
        }
      }
    }
  }

  private static class RadioButtonRenderer extends JPanel implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myTextRenderer;
    public final JRadioButton myRadioButton;
    private final boolean myUsePartialStatusForParentNodes;

    public RadioButtonRenderer() {
      this(true);
    }

    public RadioButtonRenderer(boolean opaque) {
      this(opaque, true);
    }

    public RadioButtonRenderer(boolean opaque, final boolean usePartialStatusForParentNodes) {
      super(new BorderLayout());
      myUsePartialStatusForParentNodes = usePartialStatusForParentNodes;
      myRadioButton = new JRadioButton();
      myTextRenderer = new ColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                          boolean hasFocus) {}
      };
      myTextRenderer.setOpaque(opaque);
      add(myRadioButton, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public void updateTreeState(JTree tree, CheckedTreeNode node, CloudTestingType cloudTestingType) {
      if (node.isEnabled()) {
        //getTextRenderer().setForeground(Color.BLACK);
      } else {
        //getTextRenderer().setForeground(Color.LIGHT_GRAY);
        CloudConfigurationDimension dimension = (CloudConfigurationDimension)((CheckedTreeNode) tree.getModel().getRoot()).getUserObject();
        boolean isChecked = dimension.getEnabledTypes().contains(cloudTestingType);
        node.setChecked(isChecked);
      }
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {

      invalidate();
      if (value instanceof CheckedTreeNode) {
        CheckedTreeNode node = (CheckedTreeNode)value;

        NodeState state = getNodeStatus(node);

        myRadioButton.setVisible(true);
        myRadioButton.setMargin(new Insets(-5, -3, -5, -5));
        myRadioButton.setSelected(state != NodeState.CLEAR);
        myRadioButton.setEnabled(node.isEnabled() && state != NodeState.PARTIAL);
        myRadioButton.setOpaque(false);
        myRadioButton.setBackground(null);
        setBackground(null);
      } else {
        myRadioButton.setVisible(false);
      }

      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }
      else if (UIUtil.isUnderNimbusLookAndFeel()) {
        UIUtil.changeBackGround(this, UIUtil.TRANSPARENT_COLOR);
      }

      if (!leaf) {
        // Disable group radio buttons.
        myRadioButton.setEnabled(false);
      }

      if (!myRadioButton.isEnabled() && UIUtil.isUnderDarcula()) {
        // Show visually that radio buttons are disabled (should be done explicitly in Darcula mode).
        myRadioButton.setBackground(CloudTestingUtils.makeDarker(Color.LIGHT_GRAY, 7));
      }

      revalidate();

      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof CloudTestingType) {
          CloudTestingType googleCloudTestingType = (CloudTestingType)userObject;

          myTextRenderer.append(googleCloudTestingType.getConfigurationDialogDisplayName(),
                                myRadioButton.isSelected() ? REGULAR_BOLD_ATTRIBUTES : REGULAR_ATTRIBUTES);
          updateTreeState(tree, (CheckedTreeNode)value, googleCloudTestingType);
        } else if (userObject instanceof CloudTestingTypeGroup) {
          CloudTestingTypeGroup group = (CloudTestingTypeGroup)userObject;
          myTextRenderer.append(group.getName(), myRadioButton.isSelected() ? REGULAR_BOLD_ATTRIBUTES : REGULAR_ATTRIBUTES);
          CloudTestingType type = (CloudTestingType)((CheckedTreeNode)((DefaultMutableTreeNode)value).getChildAt(0)).getUserObject();
          updateTreeState(tree, (CheckedTreeNode)value, type);
        }
      }
      return this;
    }

    private NodeState getNodeStatus(final CheckedTreeNode node) {
      final boolean checked = node.isChecked();
      if (node.getChildCount() == 0 || !myUsePartialStatusForParentNodes) return checked ? NodeState.FULL : NodeState.CLEAR;

      NodeState result = null;

      for (int i = 0; i < node.getChildCount(); i++) {
        TreeNode child = node.getChildAt(i);
        NodeState childStatus = child instanceof CheckedTreeNode ? getNodeStatus((CheckedTreeNode)child) :
                                checked ? NodeState.FULL : NodeState.CLEAR;
        if (childStatus == NodeState.PARTIAL) return NodeState.PARTIAL;
        if (result == null) {
          result = childStatus;
        } else if (result != childStatus) {
          return NodeState.PARTIAL;
        }
      }
      return result == null ? NodeState.CLEAR : result;
    }
  }


  private static class EnhancedCellRenderer implements ListCellRenderer {
    protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

    public Component getListCellRendererComponent(JList list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {

      JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, index,
                                                                              isSelected, cellHasFocus);
      CloudConfigurationDimension dimension = (CloudConfigurationDimension) value;

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

