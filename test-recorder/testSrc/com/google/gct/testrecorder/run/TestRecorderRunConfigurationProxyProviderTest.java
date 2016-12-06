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

import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.testFramework.IdeaTestCase;

public class TestRecorderRunConfigurationProxyProviderTest extends IdeaTestCase {

  public void testTestRecorderAndroidRunConfigurationProxy() {
    assertTrue("Too few Test Recorder run configuration providers", TestRecorderRunConfigurationProxy.EP_NAME.getExtensions().length > 0);

    RunConfiguration runConfiguration =
      AndroidRunConfigurationType.getInstance().getFactory().createTemplateConfiguration(myModule.getProject());

    TestRecorderRunConfigurationProxy runConfigurationProxy = TestRecorderRunConfigurationProxy.getInstance(runConfiguration);

    assertTrue("Wrong instance of TestRecorderRunConfigurationProxy", runConfigurationProxy instanceof TestRecorderAndroidRunConfigurationProxy);
  }
}
