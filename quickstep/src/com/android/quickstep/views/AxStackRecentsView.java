/*
 * Copyright 2025-2026 AxionOS
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
package com.android.quickstep.views;

import static com.android.launcher3.PagedView.INVALID_PAGE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.quickstep.GestureState;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.AxWallpaperZoom;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.wm.shell.shared.GroupedTaskInfo;

public abstract class AxStackRecentsView<
        CONTAINER_TYPE extends Context & RecentsViewContainer & StatefulContainer<STATE_TYPE>,
        STATE_TYPE extends BaseState<STATE_TYPE>> extends RecentsView<CONTAINER_TYPE, STATE_TYPE> {
    private static final float MIN_SIZE = 1f;
    private static final float MAX_STACK_DEPTH = 0.05f;
    private static final float MAX_STACK_TILT = 8f;
    private static final float TASK_DEPTH = 0.001f;
    private static final long STACK_ENTRANCE_DURATION_MS = 320L;
    private static final float HOME_ENTRANCE_STAGGER = 0.1f;
    private static final String TRACE = "stack";

    private static final AxStackLayout STACK_LAYOUT = new AxStackLayout();

    private final AxStackLayout.Transform mStackTransform = new AxStackLayout.Transform();
    private final Rect mStackTaskHitRect = new Rect();
    private final ArraySet<TaskView> mLaunchSiblings = new ArraySet<>();

    private boolean mOverviewEnabled;
    private boolean mGestureActive;
    private boolean mStackTransformsActive;
    private final float mIconChipElevation = getResources().getDimension(
            R.dimen.task_thumbnail_icon_menu_elevation);
    private boolean mStackEntranceActive;
    private boolean mStackEntranceFromHome;
    private float mStackEntranceProgress;
    @Nullable
    private ValueAnimator mStackEntranceAnimation;
    @Nullable
    private TaskView mLaunchTask;
    @Nullable
    private AnimatorSet mLaunchAnimation;
    private boolean mLaunchStarted;
    private boolean mLaunchClipChildren;
    private boolean mLaunchClipToPadding;
    private boolean mLaunchPinnedLiveTile;
    private boolean mLaunchMovedLiveTile;
    private boolean mForceLiveTileTransformUpdate;
    private float mLaunchTaskTranslationZ;
    @Nullable
    private TaskView mLiveTileTask;

    protected AxStackRecentsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOverviewStateEnabled(boolean enabled) {
        boolean stackWasActive = mStackTransformsActive;
        mOverviewEnabled = enabled;
        super.setOverviewStateEnabled(enabled);
        updateCurveProperties();
        if (enabled && !mGestureActive && !stackWasActive && mStackTransformsActive) {
            startStackEntranceAnimation(true, null);
        }
        if (enabled && stackWasActive && mStackTransformsActive) {
            loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
        }
    }

    @Override
    public void setOverviewFullscreenEnabled(boolean enabled) {
        super.setOverviewFullscreenEnabled(enabled);
        updateCurveProperties();
    }

    @Override
    public void setSelectedTask(int taskId) {
        super.setSelectedTask(taskId);
        updateCurveProperties();
    }

    @Override
    public void onGestureAnimationStart(GroupedTaskInfo taskInfo) {
        cancelStackEntranceAnimation();
        setRecentsWallpaperZoomOverride(Float.NaN);
        mGestureActive = true;
        AxAnimationEngine.trace(TRACE, "gestureStart view=" + id(this));
        super.onGestureAnimationStart(taskInfo);
        updateCurveProperties();
    }

    @Override
    public void onGestureAnimationEnd() {
        GestureState.GestureEndTarget endTarget = mCurrentGestureEndTarget;
        AxAnimationEngine.trace(TRACE, "gestureEnd view=" + id(this)
                + " target=" + endTarget);
        super.onGestureAnimationEnd();
        mGestureActive = false;
        if (endTarget != GestureState.GestureEndTarget.HOME
                && endTarget != GestureState.GestureEndTarget.REJECT_HOME) {
            updateCurveProperties();
        } else {
            setRecentsWallpaperZoomOverride(Float.NaN);
            cancelStackEntranceAnimation();
            updateCurveProperties();
        }
    }

    @Override
    public void onPrepareGestureEndAnimation(AnimatorSet animatorSet,
            GestureState.GestureEndTarget endTarget, RemoteTargetHandle[] remoteTargetHandles,
            boolean isHandlingAtomicEvent) {
        super.onPrepareGestureEndAnimation(
                animatorSet, endTarget, remoteTargetHandles, isHandlingAtomicEvent);
        AxAnimationEngine.trace(TRACE, "prepareEnd view=" + id(this)
                + " target=" + endTarget + " handles=" + count(remoteTargetHandles));
        if (endTarget == GestureState.GestureEndTarget.RECENTS) {
            setRecentsWallpaperZoomOverride(0f);
            SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(getContext());
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    AxWallpaperZoom.startZoomIn(systemUiProxy);
                }
            });
            startStackEntranceAnimation(false, animatorSet);
        } else {
            setRecentsWallpaperZoomOverride(Float.NaN);
            updateCurveProperties();
        }
    }

    @Override
    public void reset() {
        mOverviewEnabled = false;
        mGestureActive = false;
        setRecentsWallpaperZoomOverride(Float.NaN);
        cancelStackEntranceAnimation();
        cancelLaunchAnimation();
        super.reset();
        resetStackTransforms();
    }

    @Override
    protected void onDetachedFromWindow() {
        mOverviewEnabled = false;
        mGestureActive = false;
        setRecentsWallpaperZoomOverride(Float.NaN);
        cancelStackEntranceAnimation();
        cancelLaunchAnimation();
        resetStackTransforms();
        super.onDetachedFromWindow();
    }

    @Override
    public void updateCurveProperties() {
        super.updateCurveProperties();
        applyStackTransforms();
    }

    @Override
    protected void onPageScrollsInitialized() {
        super.onPageScrollsInitialized();
        updateCurveProperties();
    }

    @Override
    public void setRecentsAnimationTargets(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        super.setRecentsAnimationTargets(controller, targets);
        AxAnimationEngine.trace(TRACE, "targets view=" + id(this)
                + " handles=" + count(getRemoteTargetHandles()));
        mForceLiveTileTransformUpdate = true;
        updateCurveProperties();
    }

    @Override
    public void resetTaskVisuals() {
        mForceLiveTileTransformUpdate = true;
        super.resetTaskVisuals();
    }

    @Override
    public boolean isTaskViewVisible(TaskView taskView) {
        if (!mStackTransformsActive || !isStackTask(taskView)) {
            return super.isTaskViewVisible(taskView);
        }
        return taskView.getAxStackAlpha() > 0f;
    }

    @Override
    protected boolean isTaskViewWithinBounds(TaskView taskView, int screenStart, int screenEnd,
            int taskViewTranslation) {
        if (!mStackTransformsActive || !isStackTask(taskView)) {
            return super.isTaskViewWithinBounds(
                    taskView, screenStart, screenEnd, taskViewTranslation);
        }
        float alpha = taskView.getAxStackAlpha();
        if (alpha > 0f || taskViewTranslation == 0) {
            return alpha > 0f;
        }
        return isStackTaskVisible(taskView, taskViewTranslation);
    }

    @Override
    protected int getTaskViewVisibleRange() {
        return mStackTransformsActive ? AxStackLayout.TASK_PRELOAD_RANGE
                : super.getTaskViewVisibleRange();
    }

    @Override
    protected @Nullable TaskView getTaskViewForTouch() {
        if (!mStackTransformsActive) {
            return super.getTaskViewForTouch();
        }
        TaskView taskView = getTaskViewAt(getStackCenterTaskIndex(getHomeTaskView()));
        return taskView != null && taskView.isAxStackIconVisible() ? taskView : null;
    }

    @Override
    protected void updateTaskViewDeadZoneRects(
            Rect taskViewRect, Rect topRowRect, Rect bottomRowRect) {
        if (!mStackTransformsActive) {
            super.updateTaskViewDeadZoneRects(taskViewRect, topRowRect, bottomRowRect);
            return;
        }
        taskViewRect.setEmpty();
        topRowRect.setEmpty();
        bottomRowRect.setEmpty();
        for (TaskView taskView : getTaskViews()) {
            if (taskView.getVisibility() != View.VISIBLE
                    || taskView.getAxStackAlpha() <= 0f) {
                continue;
            }
            taskView.getHitRect(mStackTaskHitRect);
            taskViewRect.union(mStackTaskHitRect);
        }
    }

    @Override
    boolean snapToTaskIfNeeded(TaskView taskView) {
        if (mLaunchTask != null) {
            return true;
        }
        if (!mStackTransformsActive || !isStackTask(taskView)) {
            return false;
        }
        TaskView homeTask = getHomeTaskView();
        int taskIndex = indexOfChild(taskView);
        int centerIndex = getStackCenterTaskIndex(homeTask);
        if (taskIndex == INVALID_PAGE || centerIndex == INVALID_PAGE) {
            return false;
        }
        boolean settling = isPageInTransition() || !mScroller.isFinished();
        boolean currentPageIsHome = homeTask != null
                && getTaskViewAt(getCurrentPage()) == homeTask;
        boolean centered = taskIndex == centerIndex
                && (getCurrentPage() == taskIndex || currentPageIsHome);
        if (!centered || getCurrentPageScrollDiff() != 0
                || (settling && getNextPage() != taskIndex)) {
            if (taskIndex != getCurrentPage()) {
                setCurrentPageScrollDiff(0);
            }
            snapToPage(taskIndex);
            return true;
        }
        return settling;
    }

    @Override
    public void prepareTaskForLaunch(TaskView taskView) {
        if (!mStackTransformsActive || !isStackTask(taskView)) {
            return;
        }
        int taskIndex = indexOfChild(taskView);
        if (taskIndex == INVALID_PAGE) {
            return;
        }
        if (taskIndex != getCurrentPage()
                || isPageInTransition() || !mScroller.isFinished()
                || getCurrentPageScrollDiff() != 0) {
            setCurrentPageScrollDiff(0);
            setCurrentPage(taskIndex);
            updateScrollSynchronously();
            updateCurveProperties();
        }
    }

    @Override
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView taskView) {
        cancelLaunchAnimation();
        boolean useStackAnimation = mStackTransformsActive && isStackTask(taskView)
                && indexOfChild(taskView) != INVALID_PAGE;
        if (useStackAnimation) {
            prepareTaskForLaunch(taskView);
        }
        AnimatorSet animation = super.createAdjacentPageAnimForTaskLaunch(taskView);
        boolean zoomWallpaper = getDepthController() != null
                && !(taskView instanceof DesktopTaskView);
        if (!useStackAnimation && !zoomWallpaper) {
            return animation;
        }
        if (useStackAnimation) {
            mLaunchTask = taskView;
            mLaunchAnimation = animation;
            collectLaunchSiblings(taskView);
        }
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (zoomWallpaper) {
                    AxWallpaperZoom.startZoomOut(SystemUiProxy.INSTANCE.get(getContext()));
                }
                if (!useStackAnimation || mLaunchTask != taskView) {
                    return;
                }
                mLaunchStarted = true;
                mLaunchClipChildren = getClipChildren();
                mLaunchClipToPadding = getClipToPadding();
                setClipChildren(false);
                setClipToPadding(false);
                pinLaunchTaskTransforms();
                mLaunchTaskTranslationZ = taskView.getTranslationZ();
                taskView.setTranslationZ(Math.max(
                        mLaunchTaskTranslationZ, MAX_STACK_DEPTH + TASK_DEPTH));
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (useStackAnimation) {
                    finishLaunchAnimation(taskView);
                }
            }
        });
        return animation;
    }

    @Override
    protected void onDismissAnimationEnds() {
        super.onDismissAnimationEnds();
        boolean stackWasActive = mStackTransformsActive;
        updateCurveProperties();
        if (stackWasActive || mStackTransformsActive) {
            loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
        }
    }

    @Override
    protected int getTaskDismissPrimaryTranslation(
            @Nullable TaskView dismissedTaskView, View child, int defaultTranslation) {
        TaskView homeTask = getHomeTaskView();
        if (homeTask != null && mStackTransformsActive && dismissedTaskView == homeTask
                && child instanceof TaskView taskView && isStackTask(taskView, homeTask)) {
            return 0;
        }
        return defaultTranslation;
    }

    @Override
    boolean needsTaskDismissReflowUpdates() {
        return mStackTransformsActive;
    }

    @Override
    void onTaskDismissReflowUpdated(TaskView taskView) {
        boolean updateLiveTile = taskView.isRunningTask() && getEnableDrawingLiveTile();
        if (updateLiveTile) {
            runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle
                    .getTaskViewSimulator().taskPrimaryTranslation.value =
                    getTaskDismissLiveTilePrimaryTranslation(taskView));
        }
        boolean stackUpdatesLiveTile = mStackTransformsActive
                && indexOfChild(taskView) != INVALID_PAGE;
        applyStackTransforms(taskView);
        if (updateLiveTile && !stackUpdatesLiveTile) {
            redrawLiveTile();
        }
    }

    @Override
    protected float getTaskDismissLiveTilePrimaryTranslation(View view) {
        if (!(view instanceof TaskView taskView) || !isStackTask(taskView)) {
            return super.getTaskDismissLiveTilePrimaryTranslation(view);
        }
        return getPagedOrientationHandler().getPrimaryValue(
                taskView.getTranslationX() - taskView.getAppliedAxStackTranslationX(),
                taskView.getTranslationY() - taskView.getAppliedAxStackTranslationY());
    }

    private void applyStackTransforms() {
        boolean forceLiveTileUpdate = mForceLiveTileTransformUpdate;
        mForceLiveTileTransformUpdate = false;
        applyStackTransforms(null, forceLiveTileUpdate);
    }

    private void applyStackTransforms(@Nullable TaskView targetTask) {
        applyStackTransforms(targetTask, true);
    }

    private void applyStackTransforms(
            @Nullable TaskView targetTask, boolean forceLiveTileUpdate) {
        boolean stackWasActive = mStackTransformsActive;
        if (targetTask != null && !mStackTransformsActive) {
            return;
        }
        TaskView homeTask = getHomeTaskView();
        if (targetTask == null && !canUseStackLayout(homeTask)) {
            resetStackTransforms(forceLiveTileUpdate);
            return;
        }

        int centerIndex = getStackCenterTaskIndex(homeTask);
        TaskView centerTask = getTaskViewAt(centerIndex);
        if (centerTask == null) {
            resetStackTransforms(forceLiveTileUpdate);
            return;
        }
        RecentsPagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        boolean naturalLayout = orientationHandler.isLayoutNaturalToLauncher();
        float primarySize = orientationHandler.getPrimarySize(centerTask);
        if (primarySize <= 0f) {
            resetStackTransforms(forceLiveTileUpdate);
            return;
        }
        float pageDistance = Math.max(MIN_SIZE, primarySize + getPageSpacing());
        int scroll = orientationHandler.getPrimaryScroll(this);
        int anchorScroll = getAnchorScroll(centerIndex, scroll, pageDistance);
        float focusedDisplacement = Math.min(1f,
                Math.abs(getVisualDelta(getScrollForPage(centerIndex) - anchorScroll) / pageDistance));
        boolean redrawLiveTile = false;
        boolean liveTileFound = false;
        RemoteTargetHandle[] remoteTargetHandles = getRemoteTargetHandles();
        boolean hasRemoteTargets = remoteTargetHandles != null && remoteTargetHandles.length > 0;
        int childCount = getChildCount();
        for (int index = 0; index < childCount; index++) {
            if (!(getChildAt(index) instanceof TaskView taskView)) {
                continue;
            }
            if (targetTask != null && taskView != targetTask) {
                continue;
            }
            if (!isStackTask(taskView, homeTask)) {
                boolean transformChanged = taskView.resetAxStackTransform();
                if (transformChanged) {
                    taskView.setRotationY(0f);
                    taskView.setAxStackIconElevation(mIconChipElevation);
                }
                if (taskView.isRunningTask()) {
                    liveTileFound = true;
                    if (transformChanged || forceLiveTileUpdate || taskView != mLiveTileTask) {
                        setLiveTileStackTransform(taskView, remoteTargetHandles);
                        redrawLiveTile = hasRemoteTargets;
                    }
                }
                if (targetTask != null) {
                    break;
                }
                continue;
            }
            float reflowTranslation = getDismissReflowTranslation(taskView);
            int pageScroll = getScrollForPage(index);
            float anchorDelta = getVisualDelta(pageScroll - anchorScroll);
            float normalDelta = anchorDelta + reflowTranslation;
            float distance = (anchorDelta + reflowTranslation) / pageDistance;
            STACK_LAYOUT.getTransform(distance, normalDelta, reflowTranslation,
                    anchorDelta / pageDistance, primarySize, naturalLayout, mIsRtl,
                    mStackTransform);
            float entranceProgress = getStackEntranceProgress(index, centerIndex);
            float stackScale = interpolate(1f, mStackTransform.scale, entranceProgress);
            float stackTranslation = mStackTransform.primaryTranslation * entranceProgress;
            float stackAlpha = mStackEntranceFromHome
                    ? mStackTransform.alpha * entranceProgress
                    : interpolate(1f, mStackTransform.alpha, entranceProgress);
            float stackIconAlpha = interpolate(1f, mStackTransform.iconAlpha, entranceProgress);
            boolean visible = stackAlpha > 0f;
            float tilt = visible && distance > 0f
                    ? MAX_STACK_TILT * focusedDisplacement
                    : 0f;
            float handoffTranslationX = getPageSpacing() * focusedDisplacement
                    * (mIsRtl ? -Math.signum(distance) : Math.signum(distance));
            float stackDepth = visible
                    ? MAX_STACK_DEPTH * STACK_LAYOUT.getStackDepth(distance, naturalLayout)
                    : 0f;
            taskView.setRotationY(tilt);
            boolean transformChanged = taskView.setAxStackTransform(
                    stackScale,
                    naturalLayout ? stackTranslation + handoffTranslationX : 0f,
                    naturalLayout ? 0f : stackTranslation,
                    stackDepth,
                    stackAlpha,
                    stackIconAlpha);
            taskView.setAxStackIconElevation(
                    mIconChipElevation * Math.max(0f, Math.min(1f,
                            1f - Math.abs(distance))));
            if (taskView.isRunningTask()) {
                liveTileFound = true;
                if (transformChanged || forceLiveTileUpdate || taskView != mLiveTileTask) {
                    setLiveTileStackTransform(taskView, remoteTargetHandles);
                    redrawLiveTile |= hasRemoteTargets;
                }
            }
            if (targetTask != null) {
                break;
            }
        }
        if (targetTask == null && !liveTileFound
                && (forceLiveTileUpdate || mLiveTileTask != null)) {
            runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle
                    .getTaskViewSimulator().setAxStackTransform(1f, 0f, 0f, 1f));
            mLiveTileTask = null;
            redrawLiveTile = hasRemoteTargets;
        }
        mStackTransformsActive = true;
        if (!stackWasActive) {
            loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
        }
        if (redrawLiveTile && canDrawStack() && !getEnableDrawingLiveTile()) {
            redrawLiveTile();
        }
    }

    private void resetStackTransforms() {
        resetStackTransforms(false);
    }

    private void resetStackTransforms(boolean forceLiveTileUpdate) {
        AxAnimationEngine.trace(TRACE, "reset view=" + id(this)
                + " active=" + mStackTransformsActive
                + " force=" + forceLiveTileUpdate
                + " gesture=" + mGestureActive
                + " target=" + mCurrentGestureEndTarget);
        if (!mStackTransformsActive) {
            if (forceLiveTileUpdate) {
                runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle
                        .getTaskViewSimulator().setAxStackTransform(1f, 0f, 0f, 1f));
                mLiveTileTask = null;
                if (canDrawStack()) {
                    redrawLiveTile();
                }
            }
            return;
        }
        for (TaskView taskView : getTaskViews()) {
            taskView.setRotationY(0f);
            taskView.resetAxStackTransform();
            taskView.setAxStackIconElevation(mIconChipElevation);
        }
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                .setAxStackTransform(1f, 0f, 0f, 1f));
        mLiveTileTask = null;
        mStackTransformsActive = false;
        if (canDrawStack()) {
            redrawLiveTile();
        }
    }

    private boolean canDrawStack() {
        if (mGestureActive) {
            return mCurrentGestureEndTarget != null
                    && mCurrentGestureEndTarget != GestureState.GestureEndTarget.HOME
                    && mCurrentGestureEndTarget != GestureState.GestureEndTarget.REJECT_HOME;
        }
        return mOverviewEnabled;
    }

    @Override
    public void redrawLiveTile() {
        AxAnimationEngine.trace(TRACE, "redraw view=" + id(this)
                + " active=" + mStackTransformsActive
                + " gesture=" + mGestureActive
                + " target=" + mCurrentGestureEndTarget
                + " handles=" + count(getRemoteTargetHandles()));
        super.redrawLiveTile();
    }

    private static String id(Object value) {
        return Integer.toHexString(System.identityHashCode(value));
    }

    private static int count(@Nullable Object[] values) {
        return values == null ? 0 : values.length;
    }

    private void setLiveTileStackTransform(TaskView taskView,
            @Nullable RemoteTargetHandle[] remoteTargetHandles) {
        mLiveTileTask = taskView;
        boolean pinForLaunch = mLaunchStarted && taskView != mLaunchTask
                && mLaunchSiblings.contains(taskView);
        if (pinForLaunch) {
            mLaunchPinnedLiveTile = true;
        }
        if (remoteTargetHandles == null) {
            return;
        }
        for (RemoteTargetHandle remoteTargetHandle : remoteTargetHandles) {
            TaskViewSimulator simulator = remoteTargetHandle.getTaskViewSimulator();
            if (pinForLaunch) {
                simulator.setAxStackTransformPinned(true);
                simulator.taskPrimaryTranslation.value =
                        getTaskDismissLiveTilePrimaryTranslation(taskView);
            }
            simulator.setAxStackTransform(
                    taskView.getAxStackScale(),
                    taskView.getAxStackTranslationX(),
                    taskView.getAxStackTranslationY(),
                    taskView.getAxStackAlpha());
            simulator.setAxLaunchAlpha(taskView.getAxLaunchAlphaValue());
        }
    }

    private void collectLaunchSiblings(TaskView launchTask) {
        int childCount = getChildCount();
        for (int index = 0; index < childCount; index++) {
            if (!(getChildAt(index) instanceof TaskView taskView)) {
                continue;
            }
            if (taskView == launchTask) {
                continue;
            }
            mLaunchSiblings.add(taskView);
            if (taskView.getVisibility() == View.VISIBLE && taskView.getAlpha() > 0f
                    && taskView.isRunningTask()) {
                mLaunchMovedLiveTile = true;
            }
        }
    }

    private void pinLaunchTaskTransforms() {
        RemoteTargetHandle[] remoteTargetHandles = getRemoteTargetHandles();
        mLaunchPinnedLiveTile = mLaunchTask != null && !mLaunchTask.isRunningTask()
                && remoteTargetHandles != null && remoteTargetHandles.length > 0;
        for (int index = mLaunchSiblings.size() - 1; index >= 0; index--) {
            TaskView taskView = mLaunchSiblings.valueAt(index);
            taskView.setAxStackTransformPinned(true);
        }
        if (mLaunchPinnedLiveTile && remoteTargetHandles != null) {
            for (RemoteTargetHandle remoteTargetHandle : remoteTargetHandles) {
                TaskViewSimulator simulator = remoteTargetHandle.getTaskViewSimulator();
                simulator.setAxStackTransformPinned(true);
                simulator.setAxLaunchAlpha(1f);
            }
            redrawLiveTile();
        }
    }

    private void cancelLaunchAnimation() {
        AnimatorSet launchAnimation = mLaunchAnimation;
        if (launchAnimation != null && launchAnimation.isStarted()) {
            launchAnimation.cancel();
        } else {
            finishLaunchAnimation(mLaunchTask);
        }
    }

    private void finishLaunchAnimation(@Nullable TaskView launchTask) {
        if (launchTask == null || mLaunchTask != launchTask) {
            return;
        }
        if (mLaunchStarted) {
            setClipChildren(mLaunchClipChildren);
            setClipToPadding(mLaunchClipToPadding);
            launchTask.setTranslationZ(mLaunchTaskTranslationZ);
        }
        for (int index = mLaunchSiblings.size() - 1; index >= 0; index--) {
            TaskView taskView = mLaunchSiblings.valueAt(index);
            taskView.setAxLaunchAlphaValue(1f);
            taskView.setAxStackTransformPinned(false);
        }
        mLaunchSiblings.clear();
        boolean movedLiveTile = mLaunchMovedLiveTile;
        boolean pinnedLiveTile = mLaunchPinnedLiveTile;
        boolean redrawLiveTile = movedLiveTile || pinnedLiveTile;
        if (redrawLiveTile) {
            TaskView runningTask = movedLiveTile ? getRunningTaskView() : null;
            float translation = runningTask == null ? 0f
                    : getTaskDismissLiveTilePrimaryTranslation(runningTask);
            runActionOnRemoteHandles(remoteTargetHandle -> {
                TaskViewSimulator simulator = remoteTargetHandle.getTaskViewSimulator();
                if (pinnedLiveTile) {
                    simulator.setAxStackTransformPinned(false);
                }
                if (movedLiveTile) {
                    simulator.taskPrimaryTranslation.value = translation;
                }
                simulator.setAxLaunchAlpha(1f);
            });
        }
        mLaunchTask = null;
        mLaunchAnimation = null;
        mLaunchStarted = false;
        mLaunchPinnedLiveTile = false;
        mLaunchMovedLiveTile = false;
        mLaunchTaskTranslationZ = 0f;
        mForceLiveTileTransformUpdate |= redrawLiveTile;
        applyStackTransforms();
    }

    private boolean canUseStackLayout(@Nullable TaskView homeTask) {
        boolean stackGestureActive = mGestureActive && mStackEntranceActive
                && (mCurrentGestureEndTarget == null
                        || mCurrentGestureEndTarget == GestureState.GestureEndTarget.RECENTS);
        boolean gestureLeavingStack = mGestureActive && mCurrentGestureEndTarget != null
                && mCurrentGestureEndTarget != GestureState.GestureEndTarget.RECENTS;
        boolean keepForStateTransition = mStackTransformsActive
                && getStateManager().isInTransition()
                && (!mGestureActive || stackGestureActive);
        boolean stackStateActive = mLaunchTask != null || mStackEntranceActive
                || (!mGestureActive && mOverviewEnabled)
                || (!gestureLeavingStack && keepForStateTransition);
        if (!stackStateActive
                || mContainer.getDisplayId() != Display.DEFAULT_DISPLAY
                || mContainer.getDeviceProfile().getDeviceProperties().isTablet()
                || getAddDeskButton() != null
                || getWidth() <= 0 || getHeight() <= 0 || !isPageScrollsInitialized()
                || showAsGrid() || isSplitSelectionActive()
                || getSelectedTaskView() != null) {
            return false;
        }

        int taskCount = 0;
        int childCount = getChildCount();
        for (int index = 0; index < childCount; index++) {
            if (!(getChildAt(index) instanceof TaskView taskView)) {
                continue;
            }
            if (taskView == homeTask) {
                continue;
            }
            if (!isStackTask(taskView, homeTask)) {
                return false;
            }
            taskCount++;
        }
        return taskCount > 1;
    }

    private void startStackEntranceAnimation(boolean fromHome, @Nullable AnimatorSet settleAnimation) {
        cancelStackEntranceAnimation();
        mStackEntranceActive = true;
        mStackEntranceFromHome = fromHome;
        mStackEntranceProgress = 0f;
        updateCurveProperties();
        ValueAnimator animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(STACK_ENTRANCE_DURATION_MS);
        animation.addUpdateListener(animator -> {
            mStackEntranceProgress = animator.getAnimatedFraction();
            updateCurveProperties();
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mStackEntranceProgress = 1f;
                mStackEntranceAnimation = null;
                updateCurveProperties();
            }
        });
        mStackEntranceAnimation = animation;
        if (settleAnimation == null) {
            animation.start();
        } else {
            settleAnimation.play(animation);
        }
    }

    private void cancelStackEntranceAnimation() {
        if (mStackEntranceAnimation != null) {
            mStackEntranceAnimation.cancel();
            mStackEntranceAnimation = null;
        }
        mStackEntranceActive = false;
        mStackEntranceFromHome = false;
        mStackEntranceProgress = 0f;
    }

    private void setRecentsWallpaperZoomOverride(float zoom) {
        DepthController depthController = getDepthController();
        if (depthController != null) {
            depthController.setWallpaperZoomOverride(zoom);
        }
    }

    private float getStackEntranceProgress(int index, int centerIndex) {
        if (!mStackEntranceActive || !mStackEntranceFromHome) {
            return mStackEntranceProgress;
        }
        float delay = Math.min(
                Math.abs(index - centerIndex), AxStackLayout.TASK_PRELOAD_RANGE)
                * HOME_ENTRANCE_STAGGER;
        return Utilities.boundToRange(
                (mStackEntranceProgress - delay) / (1f - delay), 0f, 1f);
    }

    private static float interpolate(float start, float end, float progress) {
        return start + Utilities.boundToRange(progress, 0f, 1f) * (end - start);
    }

    private boolean isStackTask(@Nullable TaskView taskView) {
        return isStackTask(taskView, getHomeTaskView());
    }

    private static boolean isStackTask(@Nullable TaskView taskView,
            @Nullable TaskView homeTask) {
        GroupTask groupTask = taskView == null ? null : taskView.getGroupTask();
        return taskView != null && taskView != homeTask
                && !(taskView instanceof DesktopTaskView)
                && taskView.getDisplayId() == Display.DEFAULT_DISPLAY
                && groupTask != null && groupTask.matchesDisplayId(Display.DEFAULT_DISPLAY);
    }

    private int getStackCenterTaskIndex(@Nullable TaskView homeTask) {
        if (!isPageScrollsInitialized()) {
            return INVALID_PAGE;
        }
        int centerIndex;
        if (!mScroller.isFinished()) {
            centerIndex = mCurrentScrollOverPage;
        } else if (isHandlingTouch() || isPageInTransition()) {
            centerIndex = getPageNearestToCenterOfScreen();
        } else {
            centerIndex = getCurrentPage();
        }
        if (isStackTask(getTaskViewAt(centerIndex), homeTask)) {
            return centerIndex;
        }
        if (centerIndex >= 0 && centerIndex < getChildCount()
                && getChildAt(centerIndex) == getClearAllButton()
                && isStackTask(getTaskViewAt(centerIndex - 1), homeTask)) {
            return centerIndex - 1;
        }

        int scroll = getPagedOrientationHandler().getPrimaryScroll(this);
        int nearestIndex = INVALID_PAGE;
        int nearestDistance = Integer.MAX_VALUE;
        int childCount = getChildCount();
        for (int index = 0; index < childCount; index++) {
            if (!(getChildAt(index) instanceof TaskView taskView)) {
                continue;
            }
            if (!isStackTask(taskView, homeTask)) {
                continue;
            }
            int distance = Math.abs(getScrollForPage(index) - scroll);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }
        return nearestIndex;
    }

    private int getAnchorScroll(int centerIndex, int scroll, float pageDistance) {
        int centerScroll = getScrollForPage(centerIndex);
        int clearAllIndex = centerIndex + 1;
        if (clearAllIndex >= getChildCount()
                || getChildAt(clearAllIndex) != getClearAllButton()) {
            return scroll;
        }
        int clearAllScroll = getScrollForPage(clearAllIndex);
        int clearAllDelta = clearAllScroll - centerScroll;
        if (clearAllDelta != 0) {
            float progress = Utilities.boundToRange(
                    (scroll - centerScroll) / (float) clearAllDelta, 0f, 1f);
            if (progress > 0f) {
                int direction = clearAllDelta < 0 ? -1 : 1;
                return centerScroll + Math.round(direction * pageDistance * progress);
            }
        }
        return scroll;
    }

    private boolean isStackTaskVisible(TaskView taskView, int taskViewTranslation) {
        TaskView homeTask = getHomeTaskView();
        int centerIndex = getStackCenterTaskIndex(homeTask);
        TaskView centerTask = getTaskViewAt(centerIndex);
        int taskIndex = indexOfChild(taskView);
        if (centerTask == null || taskIndex == INVALID_PAGE) {
            return false;
        }
        RecentsPagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        float primarySize = Math.max(MIN_SIZE, orientationHandler.getPrimarySize(centerTask));
        float pageDistance = Math.max(MIN_SIZE, primarySize + getPageSpacing());
        int scroll = orientationHandler.getPrimaryScroll(this);
        int anchorScroll = getAnchorScroll(centerIndex, scroll, pageDistance);
        float distance = (getVisualDelta(getScrollForPage(taskIndex) - anchorScroll)
                + getDismissReflowTranslation(taskView)) / pageDistance;
        boolean naturalLayout = orientationHandler.isLayoutNaturalToLauncher();
        if (isDistanceVisible(distance, naturalLayout)) {
            return true;
        }
        float translatedDistance = distance
                + getVisualDelta(taskViewTranslation) / pageDistance;
        return isDistanceVisible(translatedDistance, naturalLayout);
    }

    private static boolean isDistanceVisible(float distance, boolean naturalLayout) {
        return STACK_LAYOUT.isDistanceActive(distance, naturalLayout)
                && STACK_LAYOUT.getTaskAlpha(distance, naturalLayout) > 0f;
    }

    private float getDismissReflowTranslation(TaskView taskView) {
        if (mTaskViewsDismissPrimaryTranslations.isEmpty()
                || !mTaskViewsDismissPrimaryTranslations.containsKey(taskView)) {
            return 0f;
        }
        return getVisualDelta(taskView.getPrimaryDismissTranslationProperty().get(taskView));
    }

    private float getVisualDelta(float scrollDelta) {
        return mIsRtl ? -scrollDelta : scrollDelta;
    }

    private static float getPrimaryCenter(RecentsPagedOrientationHandler orientationHandler,
            TaskView taskView) {
        return orientationHandler.getPrimaryValue(
                taskView.getX() + taskView.getWidth() / 2f,
                taskView.getY() + taskView.getHeight() / 2f);
    }

}
