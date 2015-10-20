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

import com.android.annotations.Nullable;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gct.testing.android.CloudConfiguration;
import com.google.gct.testing.dimension.ConfigurationChangeEvent;
import com.google.gct.testing.dimension.ConfigurationChangeListener;
import com.google.gct.testing.util.CloudTestingTracking;
import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.google.gct.testing.CloudTestingUtils.createConfigurationChooserGbc;

public class CloudConfigurationChooserDialog extends DialogWrapper implements ConfigurationChangeListener {

  private final List<CloudConfigurationImpl> editableConfigurations;
  private final List<CloudConfigurationImpl> defaultConfigurations;
  private final MyAddAction myAddAction = new MyAddAction();
  private final MyRemoveAction myRemoveAction = new MyRemoveAction();
  private final MyCopyActionButton myCopyActionButton = new MyCopyActionButton();
  private final MyMoveUpAction myMoveUpAction = new MyMoveUpAction();
  private final MyMoveDownAction myMoveDownAction = new MyMoveDownAction();

  private final DefaultMutableTreeNode customRoot = new DefaultMutableTreeNode("Custom");
  private final DefaultMutableTreeNode defaultsRoot = new DefaultMutableTreeNode("Defaults");

  private final CloudConfiguration.Kind configurationKind;

  private JPanel myPanel;
  private final Splitter mySplitter = new Splitter(false);
  private JPanel myConfigurationPanel;
  private JTextField myConfigurationName;
  private Tree myConfigurationTree;
  private JPanel myConfigurationTreePanel;
  private JLabel myConfigurationNameLabel;
  private JLabel myGroupDescriptionLabel;
  private JPanel myConfigurationEditorPanel;
  private JPanel myConfigurationInfoPanel;
  private JLabel myConfigurationInfoLabel;
  private JLabel myDimensionNameLabel;

  private ToolbarDecorator myToolbarDecorator;

  // The configuration that is currently selected in the tree
  @Nullable
  private CloudConfigurationImpl selectedConfiguration;

  private final AndroidFacet facet;

  public CloudConfigurationChooserDialog(Module module,
                                         List<CloudConfigurationImpl> editableConfigurations,
                                         List<CloudConfigurationImpl> defaultConfigurations,
                                         @Nullable final CloudConfigurationImpl initiallySelectedConfiguration,
                                         @NotNull CloudConfiguration.Kind configurationKind) {

    super(module.getProject(), true);

    this.configurationKind = configurationKind;

    myConfigurationInfoPanel.setPreferredSize(new Dimension(470, UIUtil.isUnderDarcula() ? 166 : 169));
    // Note that we are editing the list we were given.
    // This could be dangerous.
    this.editableConfigurations = editableConfigurations;
    this.defaultConfigurations = defaultConfigurations;

    facet = AndroidFacet.getInstance(module);

    if (configurationKind == CloudConfiguration.Kind.SINGLE_DEVICE) {
      setTitle("Single Device Configurations");
      UsageTracker.getInstance().trackEvent(CloudTestingTracking.CLOUD_TESTING, CloudTestingTracking.CONFIGURE_CLOUD_DEVICE,
                                            CloudTestingTracking.SESSION_LABEL, null);
    } else {
      setTitle("Matrix Configurations");
      UsageTracker.getInstance().trackEvent(CloudTestingTracking.CLOUD_TESTING, CloudTestingTracking.CONFIGURE_MATRIX,
                                            CloudTestingTracking.SESSION_LABEL, null);
    }

    getOKAction().setEnabled(true);

    myConfigurationTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {

        TreePath selectedPath = e.getPath();
        if (selectedPath.getPathCount() == 2) {
          updateConfigurationControls(false);
          DefaultMutableTreeNode selectedGroup = (DefaultMutableTreeNode)selectedPath.getPath()[1];
          if (selectedGroup.equals(customRoot)) {
            myGroupDescriptionLabel.setText("User-defined configurations. To add a new one click ");
            myGroupDescriptionLabel.setIcon(IconUtil.getAddIcon());
            myGroupDescriptionLabel.setHorizontalTextPosition(SwingConstants.LEFT);
          } else {
            myGroupDescriptionLabel.setText("Default configurations");
            myGroupDescriptionLabel.setIcon(null);
          }
          selectedConfiguration = null;
        } else {
          updateConfigurationControls(true);
          selectedConfiguration = (CloudConfigurationImpl) ((DefaultMutableTreeNode)selectedPath.getPath()[2]).getUserObject();
          myConfigurationName.setText(selectedConfiguration.getName());
          myConfigurationName.setEnabled(selectedConfiguration.isEditable());
        }
        updateConfigurationDetailsPanel(selectedConfiguration);
      }
    });
    myConfigurationTree.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myDimensionNameLabel.setText("");
        myConfigurationInfoLabel.setText("");
      }

      @Override
      public void focusLost(FocusEvent e) {
        //ignore
      }
    });
    myConfigurationTree.setCellRenderer(new ColoredTreeCellRenderer() {

      @Override
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                        boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;

          if (node == customRoot) {
            append("Custom", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            setIcon(AndroidIcons.Android);
          } else if (node == defaultsRoot) {
            append("Defaults", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            setIcon(AllIcons.General.Settings);
          } else if (node.getUserObject() instanceof CloudConfigurationImpl) {
            CloudConfigurationImpl config = (CloudConfigurationImpl) node.getUserObject();
            boolean isInvalidConfiguration = config.getDeviceConfigurationCount() < 1;
            boolean oldMySelected = mySelected;
            // This is a trick to avoid using white color for the selected element if it has to be red.
            if (isInvalidConfiguration) {
              mySelected = false;
            }
            append(config.getDisplayName(), isInvalidConfiguration
                                            ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.RED)
                                            : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            mySelected = oldMySelected;
            setIcon(config.getIcon());
          }

          setIconTextGap(2);
        }
      }
    });

    //Disposer.register(myDisposable, ???);

    if (initiallySelectedConfiguration != null) {
      myConfigurationName.setText(initiallySelectedConfiguration.getName());
    }

    myConfigurationName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        selectedConfiguration.setName(myConfigurationName.getText());
      }
    });

    myDimensionNameLabel = new JLabel("", SwingConstants.LEFT);
    Font currentFont = myDimensionNameLabel.getFont();
    myDimensionNameLabel.setFont(new Font(currentFont.getFontName(), Font.BOLD, currentFont.getSize()));
    myConfigurationInfoPanel.add(myDimensionNameLabel, createConfigurationChooserGbc(0, 0));
    myConfigurationInfoLabel = new JLabel("", SwingConstants.LEFT);
    myConfigurationInfoPanel.add(myConfigurationInfoLabel, createConfigurationChooserGbc(0, 1));

    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myConfigurationTree.setModel(treeModel);
    myConfigurationTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    treeModel.insertNodeInto(customRoot, rootNode, 0);
    treeModel.insertNodeInto(defaultsRoot, rootNode, 1);
    selectTreeNode(customRoot);
    for (CloudConfigurationImpl configuration : editableConfigurations) {
      addConfigurationToTree(-1, configuration, configuration.equals(initiallySelectedConfiguration));
    }
    for (CloudConfigurationImpl configuration : defaultConfigurations) {
      addConfigurationToTree(-1, configuration, configuration.equals(initiallySelectedConfiguration));
    }

    Dimension preferredSize = myConfigurationTreePanel.getPreferredSize();
    preferredSize.width = Math.max(preferredSize.width, 340);
    preferredSize.height = Math.max(preferredSize.height, 440);
    myConfigurationTreePanel.setPreferredSize(preferredSize);
    myConfigurationTreePanel.add(createLeftPanel(), BorderLayout.CENTER);

    mySplitter.setProportion(0.35f);

    mySplitter.setFirstComponent(myConfigurationTreePanel);
    mySplitter.setSecondComponent(myConfigurationPanel);
    myPanel.add(mySplitter, BorderLayout.CENTER);

    treeModel.reload();

    expandAllRows(myConfigurationTree);

    init();
  }

  /**
   * Adds the given configuration to the appropriate place in the tree and selects it if desired.
   */
  private void addConfigurationToTree(int index, CloudConfigurationImpl configuration, boolean makeSelected) {
    configuration.addConfigurationChangeListener(this);

    final DefaultMutableTreeNode newChild = new DefaultMutableTreeNode(configuration);
    if (configuration.isEditable()) {
      addChildToNode(index, newChild, customRoot);
    } else {
      addChildToNode(index, newChild, defaultsRoot);
    }

    if (makeSelected) {
      selectTreeNode(newChild);
    }
  }

  private void addChildToNode(int index, DefaultMutableTreeNode child, DefaultMutableTreeNode node) {
    if (index == -1) {
      node.add(child);
    } else {
      node.insert(child, index);
    }
  }

  private void selectTreeNode(final DefaultMutableTreeNode node) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        TreeUtil.selectInTree(node, true, myConfigurationTree);
      }
    });
  }

  /**
   * Removes the given configuration from the tree.  We only look under the custom root.
   */
  private void removeConfigurationFromTree(CloudConfigurationImpl configuration) {
    configuration.removeConfigurationChangeListener(this);
    MutableTreeNode toRemove = (MutableTreeNode) TreeUtil.findNodeWithObject(configuration, myConfigurationTree.getModel(), customRoot);
    customRoot.remove(toRemove);
  }

  private JPanel createLeftPanel() {
    myToolbarDecorator = ToolbarDecorator.createDecorator(myConfigurationTree).setAsUsualTopToolbar()
      .setAddAction(myAddAction).setAddActionUpdater(myAddAction).setAddActionName(
        ExecutionBundle.message("add.new.run.configuration.acrtion.name"))
      .setRemoveAction(myRemoveAction).setRemoveActionUpdater(myRemoveAction).setRemoveActionName(
        ExecutionBundle.message("remove.run.configuration.action.name"))
      .setMoveUpAction(myMoveUpAction).setMoveUpActionUpdater(myMoveUpAction).setMoveUpActionName(
        ExecutionBundle.message("move.up.action.name"))
      .setMoveDownAction(myMoveDownAction).setMoveDownActionUpdater(myMoveDownAction).setMoveDownActionName(
        ExecutionBundle.message("move.down.action.name"))
      .addExtraAction(myCopyActionButton)
      //.addExtraAction(AnActionButton.fromAction(new MySaveAction()))
      .setButtonComparator(ExecutionBundle.message("add.new.run.configuration.acrtion.name"),
                           ExecutionBundle.message("remove.run.configuration.action.name"),
                           ExecutionBundle.message("copy.configuration.action.name"),
                           ExecutionBundle.message("action.name.save.configuration"),
                           ExecutionBundle.message("run.configuration.edit.default.configuration.settings.text"),
                           ExecutionBundle.message("move.up.action.name"), ExecutionBundle.message("move.down.action.name"),
                           ExecutionBundle.message("run.configuration.create.folder.text")).setForcedDnD();
    return myToolbarDecorator.createPanel();
  }

  LoadingCache<CloudConfigurationImpl, TwoPanelTree> dimensionTreeCache = CacheBuilder.newBuilder()
    .build(new CacheLoader<CloudConfigurationImpl, TwoPanelTree>() {
      @Override
      public TwoPanelTree load(CloudConfigurationImpl configuration) throws Exception {
        final TwoPanelTree tree = new TwoPanelTree(configuration);
        final TwoPanelTreeSelectionListener treeSelectionListener = new TwoPanelTreeSelectionListener() {
          @Override
          public void dimensionSelectionChanged(TwoPanelTreeDimensionSelectionEvent e) {
            myDimensionNameLabel.setText("");
            myConfigurationInfoLabel.setText("");
          }

          @Override
          public void typeSelectionChanged(TwoPanelTreeTypeSelectionEvent e) {
            myDimensionNameLabel.setText(e.getType().getConfigurationDialogDisplayName());
            String details = "<html><table cellpadding='0'>";
            for (Map.Entry<String, String> entry : e.getType().getDetails().entrySet()) {
              details += "<tr><td>" + entry.getKey() + ":&nbsp;</td><td>" + entry.getValue() + "</td></tr>";
            }
            details += "</table>";
            if (!e.getCurrentDimension().getSupportedDomain().contains(e.getType())) {
              details += "<br><i>This option is not currently supported by Google Cloud.</i>";
            }
            details += "</html>";
            myConfigurationInfoLabel.setText(details);
          }

          @Override
          public void groupSelectionChanged(TwoPanelTreeTypeGroupSelectionEvent e) {
            myDimensionNameLabel.setText(e.getGroup().getDescription());
            myConfigurationInfoLabel.setText("");
          }
        };

        tree.addSelectionListener(treeSelectionListener);
        return tree;
      }
    });


  private void updateConfigurationDetailsPanel(CloudConfigurationImpl configuration) {

    myConfigurationEditorPanel.removeAll();

    if (configuration != null) {
      myConfigurationInfoPanel.setVisible(true);
      try {
        myConfigurationEditorPanel.add(dimensionTreeCache.get(configuration).getPanel());
      }
      catch (ExecutionException e) {
        e.printStackTrace();
      }
      myConfigurationEditorPanel.updateUI();
    } else {
      myConfigurationInfoPanel.setVisible(false);
    }
  }

  private void updateConfigurationControls(boolean isRealConfiguration) {
    myConfigurationName.setVisible(isRealConfiguration);
    myConfigurationNameLabel.setVisible(isRealConfiguration);
    myGroupDescriptionLabel.setVisible(!isRealConfiguration);
  }

  private void expandAllRows(Tree tree) {
    for (int i = 0; i < tree.getRowCount(); i++) {
      tree.expandRow(i);
    }
  }

  public CloudConfigurationImpl getSelectedConfiguration() {
    return selectedConfiguration;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myConfigurationTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent e) {
    DefaultTreeModel model = (DefaultTreeModel)myConfigurationTree.getModel();
    model.nodeChanged(TreeUtil.findNodeWithObject(e.getConfiguration(), model, customRoot));
    myConfigurationTreePanel.updateUI();
  }

  private class MyRemoveAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

    @Override
    public void actionPerformed(AnActionEvent e) {
      doRemove();
    }

    @Override
    public void run(AnActionButton button) {
      doRemove();
    }

    private void doRemove() {
      if (selectedConfiguration == null || !selectedConfiguration.isEditable()) {
        // TODO: This button should be disabled in these cases
        return;
      }

      int removedIndex = editableConfigurations.indexOf(selectedConfiguration);
      editableConfigurations.remove(selectedConfiguration);
      removeConfigurationFromTree(selectedConfiguration);

      DefaultMutableTreeNode treeNode;
      if (editableConfigurations.size() == 0) {
        if (defaultConfigurations.isEmpty()) {
          treeNode = customRoot;
        } else {
          treeNode = TreeUtil.findNodeWithObject(defaultsRoot, defaultConfigurations.get(0));
        }
      } else {
        if (removedIndex > editableConfigurations.size() - 1) {
          removedIndex = editableConfigurations.size() - 1;
        }
        treeNode = TreeUtil.findNodeWithObject(customRoot, editableConfigurations.get(removedIndex));
      }
      selectTreeNode(treeNode);

      updateConfigurationTree();
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      return selectedConfiguration != null && selectedConfiguration.isEditable();
    }
  }

  private class MyCopyActionButton extends AnActionButton implements AnActionButtonUpdater {

    public MyCopyActionButton() {
      super(ExecutionBundle.message("copy.configuration.action.name"), ExecutionBundle.message("copy.configuration.action.name"),
            PlatformIcons.COPY_ICON);
      addCustomUpdater(this);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      // selectedConfiguration should not be null here, but handle this scenario just in case.
      CloudConfigurationImpl newConfiguration = selectedConfiguration != null
                                                    ? selectedConfiguration.copy("Copy of ")
                                                    : new CloudConfigurationImpl(facet, configurationKind);

      addNewConfiguration(newConfiguration);
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      return selectedConfiguration != null;
    }
  }

  private class MyAddAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

    @Override
    public void actionPerformed(AnActionEvent e) {
      doAdd();
    }

    @Override
    public void run(AnActionButton button) {
      doAdd();
    }

    private void doAdd() {
      addNewConfiguration(new CloudConfigurationImpl(facet, configurationKind));
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      return true;
    }
  }

  private void addNewConfiguration(CloudConfigurationImpl newConfiguration) {
    newConfiguration.setIcon(CloudConfigurationProviderImpl.DEFAULT_ICON);
    int addIndex = selectedConfiguration == null
                   ? editableConfigurations.size()
                   : editableConfigurations.indexOf(selectedConfiguration) + 1;
    editableConfigurations.add(addIndex, newConfiguration);
    addConfigurationToTree(addIndex, newConfiguration, true);
    selectedConfiguration = newConfiguration;

    updateConfigurationTree();
  }

  private class MyMoveUpAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

    @Override
    public void actionPerformed(AnActionEvent e) {
      moveUp();
    }

    @Override
    public void run(AnActionButton button) {
      moveUp();
    }

    private void moveUp() {
      if (selectedConfiguration != null) {
        int index = editableConfigurations.indexOf(selectedConfiguration);
        if (index > 0) {
          moveConfigurationToIndex(index - 1, selectedConfiguration);
        }
      }
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      return selectedConfiguration != null
             && selectedConfiguration.isEditable()
             && editableConfigurations.indexOf(selectedConfiguration) > 0;
    }
  }

  private class MyMoveDownAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

    @Override
    public void actionPerformed(AnActionEvent e) {
      moveDown();
    }

    @Override
    public void run(AnActionButton button) {
      moveDown();
    }

    private void moveDown() {
      if (selectedConfiguration != null) {
        int index = editableConfigurations.indexOf(selectedConfiguration);
        if (index != -1 && index < editableConfigurations.size() - 1) {
          moveConfigurationToIndex(index + 1, selectedConfiguration);
        }
      }
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      return selectedConfiguration != null
             && selectedConfiguration.isEditable()
             && editableConfigurations.indexOf(selectedConfiguration) < editableConfigurations.size() - 1;
    }
  }

  private void moveConfigurationToIndex(int index, CloudConfigurationImpl configuration) {
    editableConfigurations.remove(configuration);
    editableConfigurations.add(index, configuration);
    removeConfigurationFromTree(configuration);
    addConfigurationToTree(index, configuration, true);
    updateConfigurationTree();
  }

  private void updateConfigurationTree() {
    ((DefaultTreeModel) myConfigurationTree.getModel()).reload();
    expandAllRows(myConfigurationTree);
    myConfigurationTree.updateUI();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
  }
}
