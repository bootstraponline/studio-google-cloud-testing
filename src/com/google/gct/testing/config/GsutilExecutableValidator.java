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
package com.google.gct.testing.config;

import com.intellij.execution.ExecutableValidator;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

public class GsutilExecutableValidator extends ExecutableValidator {

  private final Project project;
  private final GsutilConfigurable gsutilConfigurable;


  public GsutilExecutableValidator(@NotNull Project project, @NotNull GsutilConfigurable gsutilConfigurable) {
    super(project, "Can't start GSUtil! ", "Looks like the path to gsutil executable is not valid.");
    this.project = project;
    this.gsutilConfigurable = gsutilConfigurable;
  }

  @Override
  protected String getCurrentExecutable() {
    return GsutilSettings.getGsutilExecutable(project);
  }

  @NotNull
  @Override
  protected Configurable getConfigurable() {
    return gsutilConfigurable;
  }

  @Override
  public boolean isExecutableValid(@NotNull String executable) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
      ProcessOutput result = handler.runProcess(60 * 1000);
      return !result.isTimeout() && (result.getExitCode() == 0);
    }
    catch (Throwable ignored) {
      return false;
    }
  }
}
