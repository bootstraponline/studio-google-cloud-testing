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
package com.google.gct.testrecorder.debugger;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidRunConfigContext;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.activity.DefaultActivityLocator;
import com.android.tools.idea.run.editor.LaunchOptionState;
import com.android.tools.idea.run.editor.SpecificActivityLaunch;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gct.testrecorder.ui.RecordingDialog;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.impl.DebuggerManagerAdapter;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.gct.testrecorder.event.TestRecorderEvent.*;

public class SessionInitializer implements Runnable {
  private static final Logger LOGGER = Logger.getInstance(SessionInitializer.class);

  private final Set<BreakpointDescriptor> myBreakpointDescriptors = Sets.newHashSet();

  private final AndroidFacet myFacet;
  private final Project myProject;
  private final ExecutionEnvironment myEnvironment;
  private final LaunchOptionState myLaunchOptionState;
  private final int myConfigurationId;
  private IDevice myDevice;
  private String myPackageName;
  private volatile DebuggerSession myDebuggerSession;
  private volatile DebuggerManagerListener myDebuggerManagerListener;
  private volatile RecordingDialog myRecordingDialog;

  public SessionInitializer(AndroidFacet facet, ExecutionEnvironment environment, LaunchOptionState launchOptionState, int configurationId) {
    myFacet = facet;
    myProject = myFacet.getModule().getProject();
    myEnvironment = environment;
    myLaunchOptionState = launchOptionState;
    myConfigurationId = configurationId;
    // TODO: Although more robust than android.view.View#performClick() breakpoint, this might miss "contrived" clicks,
    // originating from the View object itself (e.g., as a result of processing a touch event).
    myBreakpointDescriptors.add(new BreakpointDescriptor(VIEW_CLICK, "android.view.View$PerformClick", "run", false));
    myBreakpointDescriptors.add(new BreakpointDescriptor(MENU_ITEM_CLICK, "android.widget.AbsListView", "performItemClick", false));
    myBreakpointDescriptors.add(new BreakpointDescriptor(TEXT_CHANGE, "android.widget.TextView$ChangeWatcher", "beforeTextChanged", true));
    myBreakpointDescriptors.add(new BreakpointDescriptor(TEXT_CHANGE, "android.widget.TextView$ChangeWatcher", "onTextChanged", false));

    // TODO: This breakpoint is for a finished input event rather than just press back,
    // so some filtering is required when the breakpoint is hit.
    myBreakpointDescriptors.add(new BreakpointDescriptor(PRESS_BACK, "android.view.inputmethod.InputMethodManager",
                                                         "invokeFinishedInputEventCallback", false));

    myBreakpointDescriptors.add(new BreakpointDescriptor(PRESS_EDITOR_ACTION, "android.widget.TextView", "onEditorAction", false));
  }

  @Override
  public void run() {
    myDebuggerManagerListener = new DebuggerManagerAdapter() {
      @Override
      public void sessionCreated(DebuggerSession session) {
        myDebuggerSession = session;
        myDebuggerSession.getProcess().addDebugProcessListener(createDebugProcessListener());
      }

      @Override
      public void sessionDetached(DebuggerSession session) {
        if (myDebuggerSession == session) {
          DebuggerManagerEx.getInstanceEx(myProject).removeDebuggerManagerListener(myDebuggerManagerListener);
        }
      }
    };

    DebuggerManagerEx.getInstanceEx(myProject).addDebuggerManagerListener(myDebuggerManagerListener);

    try {
      assignDeviceAndClearAppData();
    } catch (final Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(myProject, e.getMessage(), "Test Recorder startup failure");
        }
      });
      // TODO: Note that if the problem happened before the debugger was available,
      // it might still get eventually started even after Test Recorder shut down, which is undesired behavior.
      stopDebugger();
    }
  }

  @NotNull
  private DebugProcessListener createDebugProcessListener() {
    return new DebugProcessAdapter() {
      @Override
      public void processAttached(DebugProcess process) {
        AndroidSessionInfo sessionInfo = process.getProcessHandler().getUserData(AndroidSessionInfo.KEY);
        if (sessionInfo != null && sessionInfo.getRunConfigurationId() != myConfigurationId) {
          // Not my debugger session (probably, my session failed midway) => stop listening.
          DebuggerManagerEx.getInstanceEx(myProject).removeDebuggerManagerListener(myDebuggerManagerListener);
          return;
        }

        // Mute any user-defined breakpoints to avoid Test Recorder hanging the app when such a breakpoint gets hit.
        // This event arrives before initBreakpoints is called in DebugProcessEvents,
        // but after XDebugSession is supposed to be initialized, so looks like a perfect time to mute breakpoints.
        final XDebugSession xDebugSession = myDebuggerSession.getXDebugSession();
        if (xDebugSession != null) {
          // Apparently, muting breakpoints requires read access.
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              xDebugSession.setBreakpointMuted(true);
            }
          });
        }

        final Set<BreakpointCommand> breakpointCommands = scheduleBreakpointCommands();
        if (myRecordingDialog == null) { // The initial debug process, open Test Recorder dialog.
          // Detect the launched activity name outside the dispatch thread to avoid pausing it until dumb mode is over.
          String launchedActivityName = detectLaunchedActivityName();

          // TODO: Open the dialog after all breakpoints are set up (i.e., the scheduled actions are actually executed).
          // Also, consider waiting for the app to be ready first (e.g., such that we can take a screenshot).
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              //Show Test Recorder dialog after adding and enabling breakpoints.
              myRecordingDialog = new RecordingDialog(myFacet, myDevice, myPackageName, launchedActivityName);
              for (BreakpointCommand breakpointCommand : breakpointCommands) {
                breakpointCommand.setEventListener(myRecordingDialog);
              }
              myRecordingDialog.show();
              stopDebugger();
            }
          });
        } else {
          // The restarted debug process, reuse the already shown Test Recorder dialog.
          for (BreakpointCommand breakpointCommand : breakpointCommands) {
            breakpointCommand.setEventListener(myRecordingDialog);
          }
        }
      }

      @Override
      public void processDetached(DebugProcess process, boolean closedByUser) {
        if (myRecordingDialog != null && myRecordingDialog.isShowing()) {
          // Since the recoding dialog is still up, the process has detached accidentally, so try to restart debugging.
          promptToRestartDebugging();
        }
      }
    };
  }

  /**
   * There are two major uses for the fully qualified launched activity name:
   * 1) As a template value for the base class of the generated instrumentation test and
   * 2) For establishing the package name and suggested class name of the generated test class.
   */
  @NotNull
  private String detectLaunchedActivityName() {
    if (myLaunchOptionState instanceof SpecificActivityLaunch.State) {
      return ((SpecificActivityLaunch.State)myLaunchOptionState).ACTIVITY_CLASS;
    }

    return DumbService.getInstance(myProject).runReadActionInSmartMode(new Computable<String>() {
      @Override
      public String compute() {
        String activityName = "unknownPackage.unknownActivity";
        try {
          activityName = new DefaultActivityLocator(myFacet).getQualifiedActivityName(myDevice);
        } catch (Exception e) {
          return activityName;
        }

        // If alias, replace with the actual activity.

        if (myFacet.getManifest() == null || myFacet.getManifest().getApplication() == null) {
          return activityName;
        }

        Application application = myFacet.getManifest().getApplication();

        for (Activity activity : application.getActivities()) {
          if (activityName.equals(ActivityLocatorUtils.getQualifiedName(activity))) {
            return activityName; // Not an alias, return as is.
          }
        }

        for (ActivityAlias activityAlias : application.getActivityAliass()) {
          if (activityName.equals(ActivityLocatorUtils.getQualifiedName(activityAlias))) {
            // It is an alias, return the actual activity name.
            PsiClass psiClass = activityAlias.getTargetActivity().getValue();
            if (psiClass != null) {
              String qualifiedName = psiClass.getQualifiedName();
              if (qualifiedName != null) {
                return qualifiedName;
              }
            }
            // Could not establish the actual activity, so return the alias (should not really happen).
            return activityName;
          }
        }

        // Neither actual activity nor alias - should not happen, but return the originally found activity for the sake of completeness.
        return activityName;
      }
    });
  }

  private void promptToRestartDebugging() {
    // Do NOT use ApplicationManager.getApplication().invokeLater(...) here
    // as the prompt dialog will not show up until the main dialog is closed.
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        String message = "Test Recorder stopped recording your actions because it has detached from the device VM.\n" +
                         "Please fix the connection and click Resume to continue.";
        // Keep trying until a successful reconnection or the user explicitly stops attempting to reconnect.
        while (message != null) {
          myDebuggerSession = null;
          int userChoice = Messages.showDialog(myProject, message, "Test Recorder has detached from the device VM",
                                               new String[]{"Stop", "Resume"}, 1, null);
          message = null;
          if (userChoice != 0) {
            try {
              restartDebugging();
            } catch (Exception e) {
              message = "Could not reattach the debugger: " + e.getMessage();
            }
          }
        }
      }
    });
  }

  private void restartDebugging() throws ExecutionException {
    reconnectToDevice();

    String debugPort = Integer.toString(myDevice.getClient(myPackageName).getDebuggerListenPort());
    RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);

    RunProfileState state = new RunProfileState() {
      @Nullable
      @Override
      public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        return new DefaultExecutionResult();
      }
    };

    final RemoteDebugProcessHandler processHandler = new RemoteDebugProcessHandler(myProject);

    DefaultDebugEnvironment debugEnvironment = new DefaultDebugEnvironment(myEnvironment, state, connection, false) {
      @Override
      public ExecutionResult createExecutionResult() throws ExecutionException {
        return new DefaultExecutionResult(null, processHandler);
      }
    };

    DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(debugEnvironment);

    if (myDebuggerSession == null) {
      throw new RuntimeException("Could not attach the virtual machine!");
    }

    XDebuggerManager.getInstance(myProject).startSession(myEnvironment, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull XDebugSession session) {
        return JavaDebugProcess.create(session, myDebuggerSession);
      }
    });

    // Notify that debugging has started.
    processHandler.startNotify();
  }

  private Set<BreakpointCommand> scheduleBreakpointCommands() {
    Set<BreakpointCommand> breakpointCommands = Sets.newHashSet();
    DebugProcessImpl debugProcess = myDebuggerSession.getProcess();
    for (BreakpointDescriptor breakpointDescriptor : myBreakpointDescriptors) {
      BreakpointCommand breakpointCommand = new BreakpointCommand(debugProcess, breakpointDescriptor);
      breakpointCommands.add(breakpointCommand);
      debugProcess.getManagerThread().schedule(breakpointCommand);
    }
    return breakpointCommands;
  }

  private void stopDebugger() {
    if (myDebuggerSession != null) {
      XDebugSession xDebugSession = myDebuggerSession.getXDebugSession();
      if (xDebugSession != null) {
        xDebugSession.stop();
      }
    }
    if (myDevice != null) {
      try {
        // Clear app data such that there is no stale state => the generated test can run (pass) immediately.
        myDevice.executeShellCommand("pm clear " + myPackageName, new CollectingOutputReceiver(), 5, TimeUnit.SECONDS);
      } catch (Exception e) {
        LOGGER.warn("Exception stopping the app", e);
      }
    }
  }

  private void assignDeviceAndClearAppData() {
    List<ListenableFuture<IDevice>> listenableFutures =
      myEnvironment.getCopyableUserData(AndroidRunConfigContext.KEY).getTargetDevices().get();

    if (listenableFutures.size() != 1) {
      throw new RuntimeException("Test Recorder should be launched on a single device!");
    }

    try {
      myDevice = listenableFutures.get(0).get();
    } catch (Exception e) {
      throw new RuntimeException("Exception while waiting for the device to become ready ", e);
    }

    try {
      myPackageName = ApkProviderUtil.computePackageName(myFacet);
    } catch (Exception e) {
      throw new RuntimeException("Could not compute package name!");
    }

    try {
      // Clear app data such that the test recording starts from the initial app state.
      myDevice.executeShellCommand("pm clear " + myPackageName, new CollectingOutputReceiver(), 5, TimeUnit.SECONDS);
    } catch (Exception e) {
      // It is unfortunate that the command to clear app data might have failed, but it is not a blocker, so proceed.
      LOGGER.warn("Exception clearing app data", e);
    }
  }

  private void reconnectToDevice() {
    AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myProject);
    if (debugBridge == null) {
      throw new RuntimeException("Could not obtain the debug bridge!");
    }

    for (IDevice device : debugBridge.getDevices()) {
      if (myDevice.getSerialNumber().equals(device.getSerialNumber())) {
        myDevice = device;
        return;
      }
    }

    throw new RuntimeException("Could not find the original device to reconnect to!");
  }

}
