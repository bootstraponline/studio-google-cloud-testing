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

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;

public class TestRecorderBlazeCommandRunConfiguration extends BlazeCommandRunConfiguration {
  private static final Logger LOGGER = Logger.getInstance(TestRecorderBlazeCommandRunConfiguration.class);


  public TestRecorderBlazeCommandRunConfiguration(BlazeCommandRunConfiguration baseConfiguration) {
    super(baseConfiguration.getProject(), baseConfiguration.getFactory(), "TestRecorder" + baseConfiguration.getName());
    Element element = new Element("toClone");
    try {
      baseConfiguration.writeExternal(element);
      this.readExternal(element);
    } catch (Exception e) {
      LOGGER.error(e);
    }
  }

}
