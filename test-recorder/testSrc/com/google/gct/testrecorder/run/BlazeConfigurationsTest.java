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
package com.google.gct.testrecorder.run;

import com.google.common.base.Predicate;
import com.google.gct.testrecorder.ui.TestRecorderAction;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.picocontainer.MutablePicoContainer;

import java.util.List;

public class BlazeConfigurationsTest extends IdeaTestCase {

  public void testSuitableRunConfigurations() {
    Project project = myModule.getProject();
    addConfigurationsForProject(project);

    List<RunConfiguration> allConfigurations = RunManagerEx.getInstanceEx(project).getAllConfigurationsList();
    assertEquals("Wrong number of blaze configurations", 2, allConfigurations.size());

    List<RunConfiguration> suitableConfigurations = TestRecorderAction.getSuitableRunConfigurations(project);
    assertEquals("Wrong number of suitable blaze configurations", 1, suitableConfigurations.size());
    assertEquals("Wrong suitable configuration", "AndroidBinaryConfiguration", suitableConfigurations.get(0).getName());
  }

  public void testLaunchActivityClass() {
    BlazeCommandRunConfiguration blazeConfiguration =
      BlazeCommandRunConfigurationType.getInstance().getFactory().createTemplateConfiguration(myModule.getProject());
    blazeConfiguration.setTarget(new Label("//label:android_binary_rule"));
    BlazeAndroidBinaryRunConfigurationState configurationState =
      ((BlazeAndroidBinaryRunConfigurationHandler)blazeConfiguration.getHandler()).getState();
    configurationState.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    configurationState.setActivityClass("MyAppMainActivity");

    assertEquals("Unexpected launch activity", "MyAppMainActivity",
                 TestRecorderRunConfigurationProxy.getInstance(blazeConfiguration).getLaunchActivityClass());
  }

  private void addConfigurationsForProject(Project project) {
    MutablePicoContainer applicationContainer = (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();

    String componentKey = TargetFinder.class.getName();
    // Unregister the default rule finder.
    applicationContainer.unregisterComponent(componentKey);
    applicationContainer.registerComponentInstance(componentKey, new MockTargetFinder());

    String modulePath = myModule.getModuleFilePath();
    String blazeConfigurationModulePath = modulePath.substring(0, modulePath.lastIndexOf('/')) + "/label.android_binary_rule.iml";

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleManager.getInstance(myModule.getProject()).newModule(blazeConfigurationModulePath, ModuleTypeId.JAVA_MODULE);
    });

    RunManagerImpl runManager = (RunManagerImpl)RunManagerEx.getInstanceEx(project);
    BlazeCommandRunConfigurationType.BlazeCommandRunConfigurationFactory configurationFactory =
      BlazeCommandRunConfigurationType.getInstance().getFactory();

    BlazeCommandRunConfiguration blazeAndroidBinaryConfiguration = configurationFactory.createTemplateConfiguration(project);
    blazeAndroidBinaryConfiguration.setName("AndroidBinaryConfiguration");
    blazeAndroidBinaryConfiguration.setTarget(new Label("//label:android_binary_rule"));

    BlazeCommandRunConfiguration blazeAndroidTestConfiguration = configurationFactory.createTemplateConfiguration(project);
    blazeAndroidTestConfiguration.setName("AndroidTestConfiguration");
    blazeAndroidTestConfiguration.setTarget(new Label("//label:android_test_rule"));

    runManager.addConfiguration(runManager.createConfiguration(blazeAndroidBinaryConfiguration, configurationFactory), true);
    runManager.addConfiguration(runManager.createConfiguration(blazeAndroidTestConfiguration, configurationFactory), true);
  }

  private static class MockTargetFinder extends TargetFinder {
    @Override
    public List<TargetIdeInfo> findTargets(Project project, Predicate<TargetIdeInfo> predicate) {
      return null;
    }

    @Override
    public TargetIdeInfo targetForLabel(Project project, final Label label) {
      TargetIdeInfo.Builder builder = TargetIdeInfo.builder().setLabel(label);
      if (label.targetName().toString().equals("android_binary_rule")) {
        builder.setKind(Kind.ANDROID_BINARY);
      } else {
        builder.setKind(Kind.ANDROID_TEST);
      }
      return builder.build();
    }
  }

}
