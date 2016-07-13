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

import com.android.SdkConstants;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.stats.UsageTracker;
import com.google.gct.testrecorder.event.TestRecorderAssertion;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import com.google.gct.testrecorder.ui.RecordingDialog;
import com.google.gct.testrecorder.util.ResourceHelper;
import com.google.gct.testrecorder.util.TestRecorderTracking;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.actions.SelectInContextImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.gct.testrecorder.util.StringHelper.getClassName;
import static com.google.gct.testrecorder.util.StringHelper.lowerCaseFirstCharacter;
import static org.jetbrains.android.util.AndroidUtils.computePackageName;

/**
 * This class generates instrumentation test and saves it to target location given the
 * test class name, the destination directory, and the list of recorded events.
 */
public class TestCodeGenerator {
  private static final String TEST_CODE_TEMPLATE_FILE_NAME = "TestCodeTemplate.vm";

  private static final String ESPRESSO_CUSTOM_PACKAGE = "com.google.android.apps.common.testing.ui";
  private static final String ESPRESSO_STANDARD_PACKAGE = "android.support.test";

  private final AndroidFacet myFacet;
  private final PsiClass myTestClass;
  private final List<Object> myEvents;
  private final Project myProject;
  private final String myLaunchedActivityName;
  private final boolean myHasCustomEspressoDependency;


  public TestCodeGenerator(AndroidFacet facet, PsiClass testClass, List<Object> events, String launchedActivityName,
                           boolean hasCustomEspressoDependency) {
    myFacet = facet;
    myTestClass = testClass;
    myEvents = events;
    myProject = myFacet.getModule().getProject();
    myLaunchedActivityName = launchedActivityName;
    myHasCustomEspressoDependency = hasCustomEspressoDependency;
  }

  public void generate() {
    final String testFilePath = myTestClass.getContainingFile().getVirtualFile().getPath();
    final VirtualFile testVirtualFile = LocalFileSystem.getInstance().findFileByPath(testFilePath);

    if (testVirtualFile == null) {
      // TODO: notify user we failed to get virtual file
      return;
    }

    // Write code to the test class file.
    Writer writer = null;
    try {
      writer = new PrintWriter(testFilePath, SdkConstants.UTF_8);

      VelocityEngine velocityEngine = new VelocityEngine();
      // Suppress creation of velocity.log file.
      velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.NullLogChute");
      velocityEngine.init();
      velocityEngine.evaluate(createVelocityContext(testVirtualFile), writer, RecordingDialog.class.getName(), readTemplateFileContent());
      writer.flush();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test class file: ", e);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }

    // TODO: Figure out why we need to do refresh two times here.
    testVirtualFile.refresh(false, true);
    OpenFileAction.openFile(testFilePath, myProject);
    testVirtualFile.refresh(true, true, new Runnable() {
      @Override
      public void run() {
        final PsiFile testPsiFile = PsiManager.getInstance(myProject).findFile(testVirtualFile);

        // Select the generated test class in the project view hierarchy tree.
        ProjectView projectView = ProjectViewImpl.getInstance(myProject);
        String currentViewId = projectView.getCurrentViewId() == null ? ProjectViewPane.ID : projectView.getCurrentViewId();
        for (SelectInTarget target : projectView.getSelectInTargets()) {
          if (currentViewId.equals(target.getMinorViewId())) {
            target.selectIn(new SelectInContextImpl(testPsiFile) {
              @Nullable
              @Override
              public FileEditorProvider getFileEditorProvider() {
                return null;
              }
            }, false);
            break;
          }
        }

        new OptimizeImportsProcessor(myProject, testPsiFile).run();
        new ReformatCodeProcessor(myProject, testPsiFile, null, false).run();

        // If the test class is generated after some Espresso dependencies were just added (and even after Gradle sync finished),
        // the initial imports optimization attempt might not do its job properly, so try again after some time
        // (hopefully, after the indexing of the newly generated file has finished).
        // TODO: Find a better solution that would not depend on time.
        JobScheduler.getScheduler().schedule(new Runnable() {
          @Override
          public void run() {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                new OptimizeImportsProcessor(myProject, testPsiFile).run();
              }
            });
          }
        }, 10, TimeUnit.SECONDS);
      }
    });
  }

  private String readTemplateFileContent() {
    File testTemplateFile = ResourceHelper.getFileForResource(this, TEST_CODE_TEMPLATE_FILE_NAME, "test_code_template_", "vm");
    try {
      return FileUtils.readFileToString(testTemplateFile);
    } catch (Exception e) {
      throw new RuntimeException("Failed to read the test template file " + testTemplateFile.getAbsolutePath(), e);
    }
  }

  @NotNull
  private VelocityContext createVelocityContext(VirtualFile testCodeVirtualFile) {
    VelocityContext velocityContext = new VelocityContext();
    velocityContext.put("TestActivityName", getClassName(myLaunchedActivityName));
    velocityContext.put("ClassName", myTestClass.getName());
    velocityContext.put("TestMethodName", lowerCaseFirstCharacter(myTestClass.getName()));
    velocityContext.put("PackageName", computePackageName(myFacet.getModule(), testCodeVirtualFile));
    velocityContext.put("EspressoPackageName", myHasCustomEspressoDependency ? ESPRESSO_CUSTOM_PACKAGE : ESPRESSO_STANDARD_PACKAGE);

    String resourcePackageName = myFacet.getManifest().getPackage().getStringValue();
    velocityContext.put("ResourcePackageName", resourcePackageName);

    // Generate test code.
    TestCodeMapper codeMapper =
      new TestCodeMapper(getApplicationId(resourcePackageName), myHasCustomEspressoDependency, myProject, getAndroidTargetData());
    ArrayList<String> testCodeLines = new ArrayList<String>();
    int eventCount = 0;
    int assertionCount = 0;
    for (Object event : myEvents) {
      if (event instanceof TestRecorderEvent) {
        testCodeLines.addAll(codeMapper.getTestCodeLinesForEvent((TestRecorderEvent)event));
        eventCount++;
      } else {
        testCodeLines.addAll(codeMapper.getTestCodeLinesForAssertion((TestRecorderAssertion)event));
        assertionCount++;
      }
      testCodeLines.add("");
    }

    velocityContext.put("AddContribImport", codeMapper.isRecyclerViewActionAdded());
    velocityContext.put("AddChildAtPositionMethod", codeMapper.isChildAtPositionAdded());
    velocityContext.put("TestCode", testCodeLines);

    UsageTracker.getInstance().trackEvent(TestRecorderTracking.TEST_RECORDER, TestRecorderTracking.GENERATE_TEST_CLASS_EVENTS,
                                          TestRecorderTracking.SESSION_LABEL, eventCount);

    UsageTracker.getInstance().trackEvent(TestRecorderTracking.TEST_RECORDER, TestRecorderTracking.GENERATE_TEST_CLASS_ASSERTIONS,
                                          TestRecorderTracking.SESSION_LABEL, assertionCount);

    return velocityContext;
  }

  private String getApplicationId(String defaultId) {
    try {
      return ApkProviderUtil.computePackageName(myFacet);
    } catch (Exception e) {
      return defaultId;
    }
  }

  @Nullable
  private AndroidTargetData getAndroidTargetData() {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(myFacet.getModule());
    if (androidPlatform == null) {
      return null;
    }

    return androidPlatform.getSdkData().getTargetData(androidPlatform.getTarget());
  }

}
