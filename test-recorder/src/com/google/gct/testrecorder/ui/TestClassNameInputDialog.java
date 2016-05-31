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
package com.google.gct.testrecorder.ui;

import com.android.tools.idea.stats.UsageTracker;
import com.google.common.collect.Lists;
import com.google.gct.testrecorder.util.TestRecorderTracking;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.List;

public class TestClassNameInputDialog extends DialogWrapper {
  private final AndroidFacet myFacet;
  private final Project myProject;
  private PsiDirectory myTestClassParent;
  private PsiClass myTestClass;
  private String myClassName;
  private String myLaunchedActivityName;

  private JPanel myRootPanel;
  private JTextField myClassNameField;
  private JLabel myErrorMessageLabel;


  protected TestClassNameInputDialog(AndroidFacet facet, String launchedActivityName) {
    super(facet.getModule().getProject(), true);
    myFacet = facet;
    myProject = facet.getModule().getProject();
    myLaunchedActivityName = launchedActivityName;

    // Initialize dialog
    init();

    setTitle("Pick a test class name for your test");

    setOKButtonText("Save");

    prepareEnvironment();

    SwingUtilities.invokeLater(new Runnable(){
      @Override
      public void run() {
        updateOKButton();
      }
    });
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    // Put "Save" button on the right regardless of Mac or not.
    return new Action[]{getCancelAction(), getOKAction()};
  }

  private void prepareEnvironment() {
    final VirtualFile testSourceDirectory = detectOrCreateTestSourceDirectory();

    if (testSourceDirectory == null) {
      throw new RuntimeException("Could not detect or create the test source directory!");
    }

    String[] activityNameFragments = myLaunchedActivityName.split("\\.");

    VirtualFile testFileParent = getOrCreateSubdirectory(testSourceDirectory, activityNameFragments, false);

    // Generate a unique test class name based on the name of the launched activity.
    String activityTestNameBase = activityNameFragments[activityNameFragments.length - 1] + "Test";
    String activityTestName = activityTestNameBase;
    int counter = 2;
    while (testFileParent.findChild(appendJavaExtension(activityTestName)) != null) {
      activityTestName = activityTestNameBase + counter++;
    }

    myTestClassParent = PsiManager.getInstance(myProject).findDirectory(testFileParent);
    myClassName = activityTestName;
    myClassNameField.setText(myClassName);
  }

  private VirtualFile detectOrCreateTestSourceDirectory() {
    List<VirtualFile> instrumentationTestSourceRoots = getInstrumentationTestSourceRoots();
    if (instrumentationTestSourceRoots.isEmpty()) {
      UsageTracker.getInstance().trackEvent(TestRecorderTracking.TEST_RECORDER, TestRecorderTracking.MISSING_INSTRUMENTATION_TEST_FOLDER,
                                            TestRecorderTracking.SESSION_LABEL, null);

      // Create a test source root following naming convention (i.e., $MODULE_DIR$/src/androidTest/java).
      // TODO: If there are examples when naming convention fails, consider updating .iml as well,
      // e.g., using contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(parentSourceRoot.getCanonicalPath() + "/androidTest/java"), true);

      final VirtualFile moduleFile = myFacet.getModule().getModuleFile();
      if (moduleFile == null) {
        throw new RuntimeException("Could not find module file for module " + myFacet.getModule().getName());
      }

      return getOrCreateSubdirectory(moduleFile.getParent(), new String[] {"src", "androidTest", "java"}, true);
    } else {
      String launchedActivityFileRelativePath = appendJavaExtension(myLaunchedActivityName.replace('.', '/'));
      return findClosestInstrumentationTestSourceRoot(
        getContainingSourceRoot(launchedActivityFileRelativePath), instrumentationTestSourceRoots);
    }
  }

  private VirtualFile getOrCreateSubdirectory(final VirtualFile parentDirectory, final String[] subdirectoriesPath,
                                              final boolean includeLastPathElement) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        VirtualFile currentDirectory = parentDirectory;
        int subdirectoriesPathLength = includeLastPathElement ? subdirectoriesPath.length : subdirectoriesPath.length - 1;
        for (int i = 0; i < subdirectoriesPathLength; i++) {
          String subdirectory = subdirectoriesPath[i];
          VirtualFile child = currentDirectory.findChild(subdirectory);
          if (child == null) {
            try {
              currentDirectory = currentDirectory.createChildDirectory(this, subdirectory);
            } catch (Exception e) {
              throw new RuntimeException("Failed to create subdirectory " + subdirectory, e);
            }
          } else {
            currentDirectory = child;
          }
        }
        return currentDirectory;
      }
    });
  }

  @NotNull
  private VirtualFile findClosestInstrumentationTestSourceRoot(@Nullable VirtualFile sourceRoot,
                                                               List<VirtualFile> instrumentationTestSourceRoots) {

    // instrumentationTestSourceRoots should never be empty in this method.
    VirtualFile closestInstrumentationTestSourceRoot = instrumentationTestSourceRoots.get(0);

    if (sourceRoot == null) {
      return closestInstrumentationTestSourceRoot;
    }

    int maxOverlapSize = computeOverlapSize(sourceRoot.getCanonicalPath(), closestInstrumentationTestSourceRoot.getCanonicalPath());
    for (int i = 1; i < instrumentationTestSourceRoots.size(); i++) {
      VirtualFile instrumentationTestSourceRoot = instrumentationTestSourceRoots.get(i);
      int overlapSize = computeOverlapSize(sourceRoot.getCanonicalPath(), instrumentationTestSourceRoot.getCanonicalPath());
      if (overlapSize > maxOverlapSize) {
        maxOverlapSize = overlapSize;
        closestInstrumentationTestSourceRoot = instrumentationTestSourceRoot;
      }
    }

    return closestInstrumentationTestSourceRoot;
  }

  private int computeOverlapSize(String path1, String path2) {
    char[] pathChars1 = path1.toCharArray();
    char[] pathChars2 = path2.toCharArray();
    int overlapSize = 0;
    for (int i = 0; i < Math.min(pathChars1.length, pathChars2.length); i++) {
      if (pathChars1[i] == pathChars2[i]) {
        overlapSize++;
      } else {
        break;
      }
    }
    return overlapSize;
  }

  @Nullable
  private VirtualFile getContainingSourceRoot(String fileRelativePath) {
    for (VirtualFile sourceRoot : ModuleRootManager.getInstance(myFacet.getModule()).getSourceRoots(JavaSourceRootType.SOURCE)) {
      if (!GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(sourceRoot, myProject)
          && sourceRoot.findFileByRelativePath(fileRelativePath) != null) {
        return sourceRoot;
      }
    }
    return null;
  }

  private List<VirtualFile> getInstrumentationTestSourceRoots() {
    List<VirtualFile> instrumentationTestSourceRoots = Lists.newLinkedList();
    for (VirtualFile testSourceRoot : ModuleRootManager.getInstance(myFacet.getModule()).getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
      if (!GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(testSourceRoot, myProject)
          && testSourceRoot.getCanonicalPath().endsWith("/androidTest/java")) {
        instrumentationTestSourceRoots.add(testSourceRoot);
      }
    }
    return instrumentationTestSourceRoots;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myErrorMessageLabel.setText("");
    myErrorMessageLabel.setForeground(JBColor.RED);

    // Set up document listener for class name text field.
    // Update OK button based on the entered class name.
    myClassNameField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent documentEvent) {
        update();
      }

      @Override
      public void removeUpdate(DocumentEvent documentEvent) {
        update();
      }

      @Override
      public void changedUpdate(DocumentEvent documentEvent) {
        update();
      }

      private void update() {
        myClassName = myClassNameField.getText().trim();
        updateOKButton();
      }
    });

    return myRootPanel;
  }

  private void updateOKButton(){
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(myClassName));
  }

  public PsiClass getTestClass(){
    return myTestClass;
  }

  @Override
  protected void doOKAction() {
    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return myTestClassParent.findFile(appendJavaExtension(myClassName)) != null;
      }
    })) {
      myErrorMessageLabel.setText("File already exists.");
      return;
    }

    String errorMessage = ApplicationManager.getApplication().runWriteAction(new Computable<String>() {
      @Override
      public String compute() {
        try {
          myTestClass = JavaDirectoryService.getInstance().createClass(
            myTestClassParent, myClassName, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME, false);
        } catch (Exception e) {
          return e.getMessage();
        }
        return null;
      }
    });

    if (errorMessage != null || myTestClass == null) {
      // Do not use the raw errorMessage as it could be quite lengthy.
      myErrorMessageLabel.setText("File creation failed.");
    } else {
      super.doOKAction();
    }
  }

  @NotNull
  private String appendJavaExtension(String appendToString) {
    return appendToString + "." + StdFileTypes.JAVA.getDefaultExtension();
  }

  @Override
  public void doCancelAction() {
    myClassName = null;
    myTestClass = null;
    super.doCancelAction();
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.google.gct.testrecorder.ui.TestClassNameInputDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myClassNameField;
  }

}
