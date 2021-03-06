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

import com.google.gct.testrecorder.event.ElementDescriptor;
import com.google.gct.testrecorder.event.TestRecorderEvent;
import com.google.gct.testrecorder.event.TestRecorderEventListener;
import com.google.gct.testrecorder.settings.TestRecorderSettings;
import com.intellij.concurrency.JobScheduler;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.tools.jdi.StringReferenceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.gct.testrecorder.event.TestRecorderEvent.*;

public class BreakpointCommand extends DebuggerCommandImpl {
  private static final Logger LOGGER = Logger.getInstance(BreakpointCommand.class);

  private static final String PARENT_NODE_CALL = ".getParent()";

  // Some classes are loaded lazily, e.g., those that come from libraries like android.support.v4.view.ViewPager, so try several
  // times before giving up on scheduling a breakpoint in them (10 seconds).
  // TODO: Find a way to detect that the app is fully launched rather than estimating that it should start running in at most 10 seconds.
  private static final int MAX_SCHEDULE_ATTEMPTS = 20;
  private static final long INTER_ATTEMPTS_WAIT = 500; // milliseconds
  private static final int ESPRESSO_IDLE_DELAY = 15; // milliseconds

  private final DebugProcessImpl myDebugProcess;
  private final BreakpointDescriptor myBreakpointDescriptor;
  private volatile TestRecorderEventListener myEventListener;
  private volatile BreakpointRequest myRequest;
  private int myCurrentScheduleAttempt = 0;
  private volatile boolean myIsDisabled = false;

  private static TestRecorderEvent preparatoryTextChangeEvent;


  public BreakpointCommand(DebugProcessImpl debugProcess, BreakpointDescriptor breakpointDescriptor) {
    myDebugProcess = debugProcess;
    myBreakpointDescriptor = breakpointDescriptor;
  }

  public void disable() {
    myIsDisabled = true;
    if (myRequest != null) {
      myRequest.disable();
    }
  }

  @Override
  protected void action() throws Exception {
    if (myIsDisabled) {
      return;
    }

    final Location location = getBreakpointLocation();

    if (location == null) {
      if (myCurrentScheduleAttempt < MAX_SCHEDULE_ATTEMPTS) {
        myCurrentScheduleAttempt++;
        final BreakpointCommand thisCommand = this;
        JobScheduler.getScheduler().schedule(new Runnable() {
          @Override
          public void run() {
            myDebugProcess.getManagerThread().schedule(thisCommand);
          }
        }, INTER_ATTEMPTS_WAIT, TimeUnit.MILLISECONDS);
      } else {
        LOGGER.warn("Could not set breakpoint " + myBreakpointDescriptor);
      }
      return;
    }

    myRequest = myDebugProcess.getRequestsManager().createBreakpointRequest(new FilteredRequestorAdapter() {
      @Override
      public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
        try {
          SuspendContextImpl suspendContext = action.getSuspendContext();
          suspendContext.initExecutionStacks(suspendContext.getThread());

          JavaStackFrame stackFrame = (JavaStackFrame)suspendContext.getActiveExecutionStack().getTopFrame();
          StackFrameProxyImpl frameProxy = stackFrame.getStackFrameProxy();
          ObjectReference objectReference = frameProxy.thisObject();
          EvaluationContextImpl evalContext = new EvaluationContextImpl(suspendContext, frameProxy, objectReference);

          NodeManagerImpl nodeManager = myDebugProcess.getXdebugProcess().getNodeManager();

          if (myBreakpointDescriptor.eventType.equals(TEXT_CHANGE)) {
            if (myBreakpointDescriptor.isPreparatory) {
              preparatoryTextChangeEvent = prepareEvent(evalContext, nodeManager);
            } else {
              if (preparatoryTextChangeEvent == null) {
                throw new RuntimeException("Mandatory preparatory text change event is missing!");
              }
              String receiverReference = getReceiverReference(evalContext, nodeManager);
              Value text = evaluateExpression(receiverReference + ".getText().toString()", evalContext, nodeManager);
              // TODO: Why would text be null here?
              preparatoryTextChangeEvent.setReplacementText(text == null ? "" : getStringValue(text));
              notifyEventListener(preparatoryTextChangeEvent);
              preparatoryTextChangeEvent = null;
            }
          } else {
            notifyEventListener(prepareEvent(evalContext, nodeManager));
          }
        } catch(Exception e) {
          // Do not throw here, to avoid the app being stuck at the breakpoint.
          LOGGER.warn("Failed to process breakpoint event", e);
        }

        return false; // Return false such that the execution is immediately resumed.
      }
    }, location);

    myDebugProcess.getRequestsManager().enableRequest(myRequest);
  }

  private void notifyEventListener(TestRecorderEvent event) {
    if (myEventListener != null && event != null) {
      myEventListener.onEvent(event);
    }
  }

  @Override
  public Priority getPriority() {
    return Priority.HIGH;
  }


  public void setEventListener(TestRecorderEventListener listener) {
    myEventListener = listener;
  }

  private @Nullable Location getBreakpointLocation() {
    List<ReferenceType> referenceTypes = myDebugProcess.getVirtualMachineProxy().classesByName(myBreakpointDescriptor.className);

    if (referenceTypes.isEmpty()) {
      return null;
    }

    if (referenceTypes.size() > 1) {
      throw new RuntimeException("Found more than one copy of class " + myBreakpointDescriptor.className);
    }

    List<Method> methods = referenceTypes.get(0).methodsByName(myBreakpointDescriptor.methodName, myBreakpointDescriptor.methodSignature);

    if (methods.isEmpty()) {
      throw new RuntimeException("Could not find method " + myBreakpointDescriptor.methodName
                                 + myBreakpointDescriptor.methodSignature + " in class " + myBreakpointDescriptor.className);
    }

    if (methods.size() > 1) {
      throw new RuntimeException("Found more than one copy of method " + myBreakpointDescriptor.methodName
                                 + myBreakpointDescriptor.methodSignature + " in class " + myBreakpointDescriptor.className);
    }

    return methods.get(0).location();
  }

  @Nullable
  private TestRecorderEvent prepareEvent(EvaluationContextImpl evalContext, NodeManagerImpl nodeManager) {
    TestRecorderEvent event = new TestRecorderEvent(myBreakpointDescriptor.eventType, System.currentTimeMillis());

    if (event.isDelayedMessagePost()) {
      return prepareDelayedMessagePostEvent(event, evalContext, nodeManager);
    }

    if (event.isPressEvent()) {
      if (event.isPressBack()) {
        // The press back breakpoint corresponds to a finished input event rather than just pressed back key,
        // so detect if this is indeed a press back event (also, to avoid duplicates, consider only action up).

        Value isKeyCodeBackActionUp = evaluateExpression(
          "p.mEvent.mKeyCode == android.view.KeyEvent.KEYCODE_BACK && p.mEvent.mAction == android.view.KeyEvent.ACTION_UP",
          evalContext, nodeManager);

        if (isKeyCodeBackActionUp != null && Boolean.parseBoolean(getStringValue(isKeyCodeBackActionUp))) {
          return event; // Nothing else to collect for this event.
        }
        return null; // Not a press back action up event, so suppress.
      }

      Value actionCode = evaluateExpression("actionCode", evalContext, nodeManager);

      if (actionCode != null) {
        event.setActionCode(Integer.parseInt(getStringValue(actionCode)));
      }
    }

    String receiverReference = getReceiverReference(evalContext, nodeManager);

    populateElementDescriptors(event, evalContext, nodeManager, receiverReference, 1);

    if (event.isSwipe()) {
      // TODO: Is it always the case that detecting the need to scroll is not required for swipe actions?
      return completeSwipeEvent(event, evalContext, nodeManager);
    }

    if (event.getElementDescriptorsCount() > 0) {
      event.setReplacementText(event.getElementDescriptor(0).getText());
    }

    setScrollableState(event, evalContext, nodeManager, receiverReference + PARENT_NODE_CALL, 2);

    return event;
  }

  @Nullable
  private TestRecorderEvent prepareDelayedMessagePostEvent(TestRecorderEvent event, EvaluationContextImpl evalContext,
                                                           NodeManagerImpl nodeManager) {

    Value delayMillisValue = evaluateExpression("delayMillis", evalContext, nodeManager);
    if (delayMillisValue != null) {
      int delayMillis = Integer.parseInt(getStringValue(delayMillisValue));
      if (delayMillis > ESPRESSO_IDLE_DELAY) {
        // Do not use getCanonicalName() as it will return null for local and anonymous classes (as well as arrays).
        Value runnableClassNameValue = evaluateExpression("r.getClass().getName()", evalContext, nodeManager);
        if (runnableClassNameValue != null && !isFrameworkClass(getStringValue(runnableClassNameValue))) {
          event.setDelayTime(delayMillis);
          return event;
        }
      }
    }

    return null;
  }

  /**
   * TODO: This check is not comprehensive.
   */
  private boolean isFrameworkClass(String className) {
    if (isNullOrEmpty(className)) {
      return false;
    }

    return className.startsWith("android.support.") || className.startsWith("android.widget.") || className.startsWith("android.view.")
           || className.startsWith("android.os.");
  }

  @Nullable
  private TestRecorderEvent completeSwipeEvent(TestRecorderEvent event, EvaluationContextImpl evalContext, NodeManagerImpl nodeManager) {
    // TODO: These values might not match those computed by Android framework if the widget was already scrolling.
    Value dxValue = evaluateExpression("x - getScrollX()", evalContext, nodeManager);
    Value dyValue = evaluateExpression("y - getScrollY()", evalContext, nodeManager);

    int dx = dxValue == null ? 0 : Integer.parseInt(getStringValue(dxValue));
    int dy = dyValue == null ? 0 : Integer.parseInt(getStringValue(dyValue));

    if (dx == 0 && dy == 0) {
      // No swipe detected.
      return null;
    }

    if (Math.abs(dx) >= Math.abs(dy)) {
      event.setSwipeDirection(dx < 0 ? SwipeDirection.Right : SwipeDirection.Left);
    } else {
      event.setSwipeDirection(dy < 0 ? SwipeDirection.Down : SwipeDirection.Up);
    }

    return event;
  }

  @NotNull
  private String getReceiverReference(EvaluationContextImpl evalContext, NodeManagerImpl nodeManager) {
    if (myBreakpointDescriptor.eventType.equals(VIEW_CLICK) || myBreakpointDescriptor.eventType.equals(TEXT_CHANGE)) {
      return "this.this$0";
    } else if (myBreakpointDescriptor.eventType.equals(LIST_ITEM_CLICK)) {
      String titleViewReference = "view.mTitleView";
      Value titleView = evaluateExpression(titleViewReference, evalContext, nodeManager);

      if (titleView != null) {
        // If title view is present, use it as the receiver.
        return titleViewReference;
      }

      return "view";
    }

    return "this";
  }

  private void setScrollableState(TestRecorderEvent event, EvaluationContextImpl evalContext, NodeManagerImpl nodeManager,
                                  String objectReference, int level) {

    if (level > TestRecorderSettings.getInstance().SCROLL_DEPTH) {
      return;
    }

    Value parentElementTypeValue = evaluateExpression(objectReference + ".getClass().getCanonicalName()", evalContext, nodeManager);

    if (parentElementTypeValue != null) {
      String parentElementType = getStringValue(parentElementTypeValue);
      if ("android.widget.ScrollView".equals(parentElementType) || "android.widget.HorizontalScrollView".equals(parentElementType)) {
        event.setCanScrollTo(true);
        return;
      }
    }

    setScrollableState(event, evalContext, nodeManager, objectReference + PARENT_NODE_CALL, level + 1);
  }


  // TODO: After handling more user actions, use the appropriate structures rather than piggybacking on this method.
  private void populateElementDescriptors(TestRecorderEvent event, EvaluationContextImpl evalContext, NodeManagerImpl nodeManager,
                                          String objectReference, int level) {

    if ( level > TestRecorderSettings.getInstance().EVALUATION_DEPTH) {
      return;
    }

    // Perform the check only for clicks on the inner most (i.e., first in the hierarchy) element.
    if (event.isViewClick() && level == 1) {
      String parentReference = objectReference + PARENT_NODE_CALL;
      Value parentElementType = evaluateExpression(parentReference + ".getClass().getCanonicalName()", evalContext, nodeManager);
      if (parentElementType != null && "android.support.v7.widget.RecyclerView".equals(getStringValue(parentElementType))) {
        // Do not use getChildAdapterPosition as it is more than 20x slower!!!
        //Value positionIndex = evaluateExpression(parentReference + ".getChildAdapterPosition(" + objectReference + ")", evalContext, nodeManager);
        Value positionIndex = evaluateExpression(objectReference + ".getLayoutParams().mViewHolder.getAdapterPosition()", evalContext, nodeManager);
        if (positionIndex != null) {
          event.setRecyclerViewPosition(Integer.parseInt(getStringValue(positionIndex)));
          objectReference = parentReference; // Skip the adapter's child as it will be identified through position index.
        }
      }
    }

    if (evaluateAndAddElementDescriptor(event, evalContext, nodeManager, objectReference, false)) {
      // TODO: Consider more efficient expressions rather than growing getParent() calls sequence if this improves performance.
      populateElementDescriptors(event, evalContext, nodeManager, objectReference + PARENT_NODE_CALL, level + 1);
    } else if (level == 1) { // The immediately affected element is non-identifiable.
      // TODO: After looking at a number of examples where these heuristics fail, consider identifying the affected element by its XPath.

      // Apply heuristic to represent the action on its identifiable child, e.g., click on the tab as the click on the tab's text.
      Value childrenCount = evaluateExpression(objectReference + ".mChildrenCount", evalContext, nodeManager);
      if (childrenCount != null) {
        for (int i = 0; i < Integer.parseInt(getStringValue(childrenCount)); i++) {
          String childReference = objectReference + ".mChildren[" + i + "]";
          if (evaluateAndAddElementDescriptor(event, evalContext, nodeManager, childReference, true)) {
            // Use the first text-identifiable child (without going up the parent hierarchy as parent is not identifiable anyway).
            return;
          }
        }
      }

      Value className = evaluateExpression(objectReference + ".getClass().getCanonicalName()", evalContext, nodeManager);

      // An empty element descriptor for the non-identifiable element.
      event.addElementDescriptor(new ElementDescriptor(className == null ? "" : getStringValue(className), -1, "", "", ""));

      // In case there is no text-identifiable child, use the parent node as the means of identification.
      populateElementDescriptors(event, evalContext, nodeManager, objectReference + PARENT_NODE_CALL, level + 1);
    }
  }

  /**
   * Returns {@code true} iff the element descriptor was added (i.e., at least one of its attribute fields was not {@code null}
   * or text was present if mandatory).
   */
  private boolean evaluateAndAddElementDescriptor(TestRecorderEvent event, EvaluationContextImpl evalContext, NodeManagerImpl nodeManager,
                                                  String objectReference, boolean isTextMandatory) {

    Value text = evaluateExpression(objectReference + ".getText().toString()", evalContext, nodeManager);

    if (isTextMandatory && text == null) {
      return false;
    }

    Value childPosition = evaluateExpression(objectReference + ".getParent().getPositionForView(" + objectReference + ")",
                                             evalContext, nodeManager);

    Value resourceNumberIdValue = evaluateExpression(objectReference + ".getId()", evalContext, nodeManager);

    int resourceNumberId = resourceNumberIdValue == null ? -1 : Integer.parseInt(getStringValue(resourceNumberIdValue));

    Value resourceId = resourceNumberId == -1 ? null : evaluateExpression(objectReference + ".getResources().getResourceName("
                                                                          + resourceNumberId + ")", evalContext, nodeManager);

    Value contentDescription = evaluateExpression(objectReference + ".getContentDescription()", evalContext, nodeManager);

    if (!TestRecorderSettings.getInstance().CAP_AT_NON_IDENTIFIABLE_ELEMENTS
        || childPosition != null || resourceId != null || contentDescription != null || text != null) {
      Value className = evaluateExpression(objectReference + ".getClass().getCanonicalName()", evalContext, nodeManager);

      event.addElementDescriptor(new ElementDescriptor(className == null ? "" : getStringValue(className),
                                                       childPosition == null ? -1 : Integer.parseInt(getStringValue(childPosition)),
                                                       resourceId == null ? "" : getStringValue(resourceId),
                                                       contentDescription == null ? "" : getStringValue(contentDescription),
                                                       text == null ? "" : getStringValue(text)));
      return true;
    }

    return false;
  }

  private String getStringValue(@NotNull Value value) {
    if (value instanceof StringReferenceImpl) {
      return ((StringReferenceImpl)value).value();
    }
    return value.toString();
  }

  private Value evaluateExpression(String expression, EvaluationContextImpl evalContext, NodeManagerImpl nodeManager) {
    TextWithImports text = TextWithImportsImpl.fromXExpression(XExpressionImpl.fromText(expression));
    WatchItemDescriptor descriptor = nodeManager.getWatchItemDescriptor(null, text, null);
    descriptor.setContext(evalContext);
    return descriptor.getEvaluateException() != null ? null : descriptor.getValue();
  }

  private abstract class FilteredRequestorAdapter implements FilteredRequestor {

    @Override
    public String getSuspendPolicy() {
      return DebuggerSettings.SUSPEND_ALL;
    }

    @Override
    public boolean isInstanceFiltersEnabled() {
      return false;
    }

    @Override
    public InstanceFilter[] getInstanceFilters() {
      return new InstanceFilter[0];
    }

    @Override
    public boolean isCountFilterEnabled() {
      return false;
    }

    @Override
    public int getCountFilter() {
      return 0;
    }

    @Override
    public boolean isClassFiltersEnabled() {
      return false;
    }

    @Override
    public ClassFilter[] getClassFilters() {
      return new ClassFilter[0];
    }

    @Override
    public ClassFilter[] getClassExclusionFilters() {
      return new ClassFilter[0];
    }
  }
}
