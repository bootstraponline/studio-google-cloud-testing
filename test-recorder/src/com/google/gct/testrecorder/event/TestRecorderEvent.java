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
package com.google.gct.testrecorder.event;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class TestRecorderEvent extends ElementAction {
  public enum SwipeDirection {Left, Right, Up, Down}

  public static final String VIEW_CLICK = "VIEW_CLICKED";
  public static final String VIEW_LONG_CLICK = "VIEW_LONG_CLICKED";
  public static final String LIST_ITEM_CLICK = "LIST_ITEM_CLICKED";
  public static final String TEXT_CHANGE = "VIEW_TEXT_CHANGED";
  public static final String PRESS_BACK = "PRESSED_BACK";
  public static final String PRESS_EDITOR_ACTION = "PRESSED_EDITOR_ACTION"; // RETURN key on the soft keyboard.
  public static final String VIEW_SWIPE = "VIEW_SWIPED";
  public static final String DELAYED_MESSAGE_POST = "DELAYED_MESSAGE_POSTED";

  public static final HashSet<String> SUPPORTED_EVENTS =
    Sets.newHashSet(VIEW_CLICK, VIEW_LONG_CLICK, LIST_ITEM_CLICK, TEXT_CHANGE, PRESS_BACK, PRESS_EDITOR_ACTION, VIEW_SWIPE,
                    DELAYED_MESSAGE_POST);

  /**
   * View click, menu item click, text change, etc.
   */
  private final String eventType;

  /**
   * When this event happened (in milliseconds).
   */
  private final long timestamp;

  /**
   *  Whether the affected element is checked.
   */
  private boolean isChecked;

  /**
   * Relevant for text editing events. Keeps the resulting edited text.
   */
  private String replacementText;

  /**
   * Represents the element's position in its RecyclerView (if any).
   * The default value of -1 signifies that there is no RecyclerView container.
   */
  private int recyclerViewPosition = -1;

  /**
   * Represents the action kind for pressing editor action (return) key.
   */
  private int actionCode = -1;

  /**
   * Represents the direction of swipe performed by the user, if any.
   */
  private SwipeDirection swipeDirection = null;

  /**
   * Represents the delay time (in milliseconds) of posted delayed messages.
   */
  private long delayTime;


  public TestRecorderEvent(String eventType, long timestamp) {
    this.eventType = eventType;
    this.timestamp = timestamp;
  }

  public String getEventType() {
    return eventType;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public boolean isChecked() {
    return isChecked;
  }

  public String getReplacementText() {
    return replacementText;
  }

  public int getRecyclerViewPosition() {
    return recyclerViewPosition;
  }

  public int getActionCode() {
    return actionCode;
  }

  public SwipeDirection getSwipeDirection() {
    return swipeDirection;
  }

  public long getDelayTime() {
    return delayTime;
  }

  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  public void setReplacementText(String replacementText) {
    this.replacementText = replacementText;
  }

  public void setRecyclerViewPosition(int recyclerViewPosition) {
    this.recyclerViewPosition = recyclerViewPosition;
  }

  public void setActionCode(int actionCode) {
    this.actionCode = actionCode;
  }

  public void setSwipeDirection(SwipeDirection swipeDirection) {
    this.swipeDirection = swipeDirection;
  }

  public void setDelayTime(long delayTime) {
    this.delayTime = delayTime;
  }

  public boolean isViewClick() {
    return VIEW_CLICK.equals(eventType);
  }

  public boolean isViewLongClick() {
    return VIEW_LONG_CLICK.equals(eventType);
  }

  public boolean isListItemClick() {
    return LIST_ITEM_CLICK.equals(eventType);
  }

  public boolean isClickEvent() {
    return isViewClick() || isViewLongClick() || isListItemClick();
  }

  public boolean isTextChange() {
    return TEXT_CHANGE.equals(eventType);
  }

  public boolean isPressBack() {
    return PRESS_BACK.equals(eventType);
  }

  public boolean isPressEditorAction() {
    return PRESS_EDITOR_ACTION.equals(eventType);
  }

  public boolean isPressEvent() {
    return isPressBack() || isPressEditorAction();
  }

  public boolean isSwipe() {
    return VIEW_SWIPE.equals(eventType);
  }

  public boolean isDelayedMessagePost() {
    return DELAYED_MESSAGE_POST.equals(eventType);
  }

  @Override
  public String getRendererString() {
    if (isDelayedMessagePost()) {
      return getIdAttributeDisplayPresentation("", String.valueOf(getDelayTime()));
    }

    if (isSwipe()) {
      return getIdAttributeDisplayPresentation("", getSwipeDirection().name());
    }

    if (isPressEvent()) {
      return getIdAttributeDisplayPresentation("", isPressBack() ? "Back" : getRendererActionCode());
    }

    if (recyclerViewPosition != -1) {
      return getRendererString(getIdAttributeDisplayPresentation("element position", String.valueOf(recyclerViewPosition)));
    }

    return super.getRendererString();
  }

  private String getRendererActionCode() {
    switch (actionCode) {
      case 2 : return "Go"; // EditorInfo.IME_ACTION_GO
      case 3 : return "Search"; // EditorInfo.IME_ACTION_SEARCH
      case 4 : return "Send"; // EditorInfo.IME_ACTION_SEND
      case 5 : return "Next"; // EditorInfo.IME_ACTION_NEXT
      case 6 : return "Done"; // EditorInfo.IME_ACTION_DONE
      case 7 : return "Previous"; // EditorInfo.IME_ACTION_PREVIOUS
      default: return "Return";
    }
  }

  @Override
  @NotNull
  protected String getRendererString(String displayElementAttribute) {
    String prefix = isTextChange() ? "into " : "";
    return prefix + super.getRendererString(displayElementAttribute);
  }

  /**
   *  Returns {@code true} iff two events can be merged for the visualization and test generation purposes.
   */
  public boolean canMerge(TestRecorderEvent eventToMergeWith) {
    // TODO: Consider also matching content descriptors and parents hierarchy.
    return isTextChange() && eventToMergeWith.isTextChange()
           && getElementResourceId().equals(eventToMergeWith.getElementResourceId())
           && getReplacementText().equals(eventToMergeWith.getElementText())
           || isDelayedMessagePost() && eventToMergeWith.isDelayedMessagePost();
  }

  public void merge(TestRecorderEvent eventToMergeWith) {
    if (isTextChange()) {
      setReplacementText(eventToMergeWith.getReplacementText());
    } else { // delayed message post
      // TODO: Maybe sum instead of max?
      setDelayTime(Math.max(getDelayTime(), eventToMergeWith.getDelayTime()));
    }
  }

}
