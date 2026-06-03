/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.os.Trace.TRACE_TAG_APP;

import static com.android.launcher3.Flags.enableOverviewBackgroundWallpaperBlur;

import android.app.AxBoostFwk;
import android.app.WallpaperManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.gui.EarlyWakeupInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Trace;
import android.util.FloatProperty;
import android.util.Log;
import android.view.CrossWindowBlurListeners;
import android.view.SurfaceControl;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.axion.blur.AxBlurSettings;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.BlurredSnapshotManager;
import com.android.launcher3.views.BlurredSnapshotView;
import com.android.systemui.shared.system.BlurUtils;

import java.util.List;

/**
 * Utility class for applying depth effect
 */
public class BaseDepthController implements LauncherPrefChangeListener {
    public static final float DEPTH_0_PERCENT = 0f;
    public static final float DEPTH_60_PERCENT = 0.6f;
    public static final float DEPTH_70_PERCENT = 0.7f;

    private static final FloatProperty<BaseDepthController> DEPTH =
            new FloatProperty<BaseDepthController>("depth") {
                @Override
                public void setValue(BaseDepthController depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(BaseDepthController depthController) {
                    return depthController.mDepth;
                }
            };

    private static final int DEPTH_INDEX_STATE_TRANSITION = 0;
    private static final int DEPTH_INDEX_WIDGET = 1;
    private static final int DEPTH_INDEX_COUNT = 2;

    // b/291401432
    private static final String TAG = "BaseDepthController";

    protected final QuickstepLauncher mLauncher;
    /** Property to set the depth for state transition. */
    public final MultiProperty stateDepth;
    /** Property to set the depth for widget picker. */
    public final MultiProperty widgetDepth;

    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    protected int mMaxBlurRadius;
    protected final WallpaperManager mWallpaperManager;
    protected boolean mCrossWindowBlursEnabled;

    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     *
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;

    protected SurfaceControl mBaseSurface;
    protected SurfaceControl mBaseSurfaceOverride;
    // May be temporarily null while the Launcher is being created, in which case all blur
    // requests will be applied immediately rather than synced to the RenderThread. This shouldn't
    // really happen in practice since we won't apply blur until the Launcher is interactive.
    @Nullable protected SurfaceTransactionApplier mSurfaceTransactionApplier;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;

    /** Pause blur but allow transparent, can be used when launch something behind the Launcher. */
    protected boolean mPauseBlurs;

    /**
     * Last blur value, in pixels, that was applied.
     */
    protected int mCurrentBlur;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    protected boolean mInEarlyWakeUp;

    protected boolean mWaitingOnSurfaceValidity;

    private SurfaceControl mBlurSurface = null;
    /**
     * Info for early wakeup requests to SurfaceFlinger.
     */
    private EarlyWakeupInfo mEarlyWakeupInfo = new EarlyWakeupInfo();

    private static final int MIN_SNAPSHOT_BLUR_RADIUS = 24;
    private static final float SNAPSHOT_COLLAPSE_FADE_START_PROGRESS = 0.42f;
    private static final float SNAPSHOT_COLLAPSE_FADE_END_PROGRESS = 1f;
    private static final boolean DEBUG = false;
    private final LauncherPrefs mLauncherPrefs;
    private final AxBlurSettings mBlurSettings;
    private final float mWallpaperMaxScale;
    private final BlurredSnapshotManager mBlurredSnapshotManager;
    private final SafeCloseable mBlurredSnapshotRegistration;
    private final boolean mSupportsBlursOnWindows;
    private final int mBlurSkipThresholdPx;

    private float mLastWallpaperZoom = -1f;
    private boolean mDisableWallpaperZoom;
    private boolean mUsingBlurredSnapshot;
    private boolean mPreferBlurredSnapshot;
    @Nullable private LauncherState mGestureTargetState;
    private int mAppliedSurfaceBlur = -1;
    private boolean mAppliedSurfaceOpaque;
    @Nullable private SurfaceControl mAppliedBlurSurface;
    private int mAppliedWorkspaceBlur = -1;
    private float mBlurredSnapshotWallpaperOffset = Float.NaN;

    private void updateMaxBlurRadius() {
        mMaxBlurRadius = Math.round(mBlurSettings.getBlurRadiusPx());
    }

    public BaseDepthController(QuickstepLauncher activity) {
        mLauncher = activity;
        mLauncherPrefs = LauncherPrefs.get(activity);
        mDisableWallpaperZoom = mLauncherPrefs.get(LauncherPrefs.DISABLE_WALLPAPER_ZOOM);
        mLauncherPrefs.addListener(this, LauncherPrefs.DISABLE_WALLPAPER_ZOOM);
        mSupportsBlursOnWindows = BlurUtils.supportsBlursOnWindows();
        if (Flags.allAppsBlur() || enableOverviewBackgroundWallpaperBlur()) {
            mCrossWindowBlursEnabled =
                    CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled();
        }
        mWallpaperManager = activity.getSystemService(WallpaperManager.class);
        mBlurredSnapshotManager = BlurredSnapshotManager.INSTANCE.get(activity);
        mBlurredSnapshotRegistration = mBlurredSnapshotManager.attach(
                activity, this::onBlurredSnapshotInvalidated);

        mBlurSettings = AxBlurSettings.launcher(activity);
        mWallpaperMaxScale = Math.max(
                1f, activity.getResources().getFloat(R.dimen.config_wallpaperMaxScale));
        mBlurSkipThresholdPx = Utilities.dpToPx(1);
        updateMaxBlurRadius();
        mBlurSettings.start(() -> {
            updateMaxBlurRadius();
            applyDepthAndBlur();
        });
        MultiPropertyFactory<BaseDepthController> depthProperty =
                new MultiPropertyFactory<>(this, DEPTH, DEPTH_INDEX_COUNT, Float::max);
        stateDepth = depthProperty.get(DEPTH_INDEX_STATE_TRANSITION);
        widgetDepth = depthProperty.get(DEPTH_INDEX_WIDGET);
        mEarlyWakeupInfo.token = new Binder();
        mEarlyWakeupInfo.trace = BaseDepthController.class.getName();
    }

    public void destroy() {
        mLauncherPrefs.removeListener(this, LauncherPrefs.DISABLE_WALLPAPER_ZOOM);
        mBlurredSnapshotRegistration.close();
        clearBlurredSnapshot();
        mBlurSettings.stop();
    }

    @Override
    public void onPrefChanged(String key) {
        if (!LauncherPrefs.DISABLE_WALLPAPER_ZOOM.getSharedPrefKey().equals(key)) {
            return;
        }
        boolean disableWallpaperZoom = mLauncherPrefs.get(LauncherPrefs.DISABLE_WALLPAPER_ZOOM);
        if (mDisableWallpaperZoom == disableWallpaperZoom) {
            return;
        }
        mDisableWallpaperZoom = disableWallpaperZoom;
        applyDepthAndBlur();
    }

    /**
     * Sets the applier to use for syncing surface transactions to the RenderThread.
     *
     * @param rootView The root view of the surface to apply the surface transactions to.
     */
    public void setSurfaceTransactionApplier(View rootView) {
        mSurfaceTransactionApplier = new SurfaceTransactionApplier(rootView);
    }

    /**
     * Returns if cross window blurs are enabled. In other words, whether launcher should use blurs
     * style UI or fallback style UI.
     */
    public boolean isCrossWindowBlursEnabled() {
        return mCrossWindowBlursEnabled;
    }

    protected void setCrossWindowBlursEnabled(boolean isEnabled) {
        if (mCrossWindowBlursEnabled == isEnabled) {
            return;
        }
        mCrossWindowBlursEnabled = isEnabled;
        mLauncher.updateBlurStyle();
        applyDepthAndBlur();
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
    }

    public void onAllAppsTransitionProgressChanged() {
        if (mUsingBlurredSnapshot || Flags.allAppsBlur()) {
            applyDepthAndBlur();
        }
    }

    public void setGestureTargetState(@Nullable LauncherState targetState) {
        if (mGestureTargetState == targetState) {
            return;
        }
        mGestureTargetState = targetState;
        mBlurredSnapshotWallpaperOffset = Float.NaN;
        applyDepthAndBlur();
    }

    public boolean isUsingBlurredSnapshot() {
        return mUsingBlurredSnapshot || mPreferBlurredSnapshot;
    }

    public void pauseBlursOnWindows(boolean pause) {
        if (mPauseBlurs == pause) {
            return;
        }
        mPauseBlurs = pause;
        applyDepthAndBlur();
    }

    protected void onInvalidSurface() { }

    protected void applyDepthAndBlur() {
        applyDepthAndBlur(null, /* applyImmediately */ false, /* skipSimilarBlur */ true);
    }

    /**
     * Applies depth and blur to the launcher.
     *
     * @param surfaceTransaction optional SurfaceTransaction to apply the blur to.
     * @param applyImmediately whether to apply the blur immediately or defer to the next frame.
     * @param skipSimilarBlur whether to skip applying blur if the change is minimal.
     */
    private void applyDepthAndBlur(@Nullable SurfaceTransaction surfaceTransaction,
            boolean applyImmediately, boolean skipSimilarBlur) {
        float depth = mDepth;
        float wallpaperZoom = mDisableWallpaperZoom ? 0f : depth;
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            if (Math.abs(mLastWallpaperZoom - wallpaperZoom) >= 0.03f
                    || (wallpaperZoom == 0f && mLastWallpaperZoom != 0f)
                    || (wallpaperZoom == 1f && mLastWallpaperZoom != 1f)) {
                final float finalZoom = wallpaperZoom;
                Executors.UI_HELPER_EXECUTOR.execute(() -> {
                    mWallpaperManager.setWallpaperZoomOut(windowToken, finalZoom);
                });
                mLastWallpaperZoom = wallpaperZoom;
            }
        }

        if (!mSupportsBlursOnWindows) {
            return;
        }
        if (mBaseSurface == null) {
            if (DEBUG) {
                Log.d(TAG, "mSurface is null and mCurrentBlur is: " + mCurrentBlur);
            }
            return;
        }
        if (!mBaseSurface.isValid()) {
            if (DEBUG) {
                Log.d(TAG, "mSurface is not valid");
            }
            mWaitingOnSurfaceValidity = true;
            onInvalidSurface();
            return;
        }
        mWaitingOnSurfaceValidity = false;
        boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
        boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg && !mPauseBlurs;

        float blurAmount = mapDepthToBlur(depth);
        LauncherState targetState = getTargetState();
        boolean useDefaultBlur = shouldUseDefaultBlur();
        float snapshotProgress = !hasOpaqueBg && !mPauseBlurs && mMaxBlurRadius > 0
                ? blurAmount : 0f;
        float snapshotWallpaperZoom = mLastWallpaperZoom >= 0f ? mLastWallpaperZoom : wallpaperZoom;
        float snapshotContentScale = mapWallpaperZoomToScale(snapshotWallpaperZoom);
        boolean shouldBlurWorkspace = shouldBlurWorkspace(targetState);
        float snapshotAlpha = mapSnapshotAlpha(targetState, shouldBlurWorkspace, snapshotProgress,
                depth);
        boolean canUseBlurredSnapshot = snapshotProgress > 0f && !useDefaultBlur;
        boolean wantsBlurredSnapshot = canUseBlurredSnapshot
                && (shouldUseBlurredSnapshot(targetState, shouldBlurWorkspace)
                        || (mPreferBlurredSnapshot && targetState == LauncherState.NORMAL));
        mPreferBlurredSnapshot = wantsBlurredSnapshot;
        float wallpaperOffset = wantsBlurredSnapshot
                ? getBlurredSnapshotWallpaperOffset()
                : 0f;
        SurfaceControl blurSurface =
                enableOverviewBackgroundWallpaperBlur() && mBlurSurface != null ? mBlurSurface
                        : mBaseSurface;

        int previousBlur = mCurrentBlur;
        int newBlur = mCrossWindowBlursEnabled && !hasOpaqueBg && !mPauseBlurs ? (int) (blurAmount
                * mMaxBlurRadius) : 0;
        int delta = Math.abs(newBlur - previousBlur);
        if (skipSimilarBlur && delta < mBlurSkipThresholdPx && newBlur != 0 && previousBlur != 0
                && blurAmount != 1f && !wantsBlurredSnapshot && !mUsingBlurredSnapshot) {
            return;
        }
        mCurrentBlur = newBlur;
        boolean wasUsingBlurredSnapshot = mUsingBlurredSnapshot;
        boolean blurredSnapshotVisible = updateBlurredSnapshot(
                shouldBlurWorkspace, wantsBlurredSnapshot, snapshotAlpha, snapshotContentScale,
                wallpaperOffset);
        int surfaceBlur = wantsBlurredSnapshot ? 0 : mCurrentBlur;
        if (wasUsingBlurredSnapshot && !blurredSnapshotVisible && surfaceBlur > 0) {
            applyImmediately = true;
        }
        int previousSurfaceBlur = mAppliedSurfaceBlur < 0 ? 0 : mAppliedSurfaceBlur;
        if (previousSurfaceBlur == 0 && surfaceBlur > 0) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_SHADE, -1L);
        } else if (previousSurfaceBlur > 0 && surfaceBlur == 0) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_SHADE, 0L);
        }

        boolean hasExternalTransaction = surfaceTransaction != null;
        boolean surfaceChanged = mAppliedBlurSurface != blurSurface
                || mAppliedSurfaceBlur != surfaceBlur
                || mAppliedSurfaceOpaque != isSurfaceOpaque;
        boolean wantsEarlyWakeUp = surfaceBlur > 0 && blurAmount > 0 && blurAmount < 1;
        boolean earlyWakeupChanged = wantsEarlyWakeUp != mInEarlyWakeUp;
        if (!hasExternalTransaction && !surfaceChanged && !earlyWakeupChanged) {
            return;
        }
        if (!hasExternalTransaction) {
            surfaceTransaction = new SurfaceTransaction();
        }

        if (hasExternalTransaction || surfaceChanged) {
            surfaceTransaction.forSurface(blurSurface)
                    .setBackgroundBlurRadius(surfaceBlur)
                    .setOpaque(isSurfaceOpaque);
            mAppliedBlurSurface = blurSurface;
            mAppliedSurfaceBlur = surfaceBlur;
            mAppliedSurfaceOpaque = isSurfaceOpaque;
        }
        // Set early wake-up flags when we know we're executing an expensive operation, this way
        // SurfaceFlinger will adjust its internal offsets to avoid jank.
        if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
            setEarlyWakeup(surfaceTransaction.getTransaction(), true);
        } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
            setEarlyWakeup(surfaceTransaction.getTransaction(), false);
        }

        if (applyImmediately || mSurfaceTransactionApplier == null) {
            surfaceTransaction.getTransaction().apply();
        } else {
            mSurfaceTransactionApplier.scheduleApply(surfaceTransaction);
        }
    }

    /**
     * Sets the early wakeup state.
     *
     * @param inEarlyWakeUp whether SurfaceFlinger's early wakeup timing should be active.
     */
    public void setEarlyWakeup(boolean inEarlyWakeUp) {
        if (mInEarlyWakeUp == inEarlyWakeUp) {
            return;
        }
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            setEarlyWakeup(transaction, inEarlyWakeUp);
            transaction.apply();
        }
    }

    /**
     * Sets the early wakeup state.
     *
     * @param transaction transaction to apply to.
     * @param start whether to start or end the early wakeup.
     */
    protected void setEarlyWakeup(@NonNull SurfaceControl.Transaction transaction, boolean start) {
        if (mInEarlyWakeUp == start) {
            return;
        }
        if (start) {
            Trace.instantForTrack(TRACE_TAG_APP, TAG, "notifyRendererForGpuLoadUp");
            mLauncher.getRootView().getViewRootImpl().notifyRendererForGpuLoadUp("applyBlur");
            transaction.setEarlyWakeupStart(mEarlyWakeupInfo);
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0);
        } else {
            transaction.setEarlyWakeupEnd(mEarlyWakeupInfo);
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0L);
        }
        mInEarlyWakeUp = start;
    }

    /** @return {@code true} if the workspace should be blurred. */
    @VisibleForTesting
    public boolean blurWorkspaceDepthTargets() {
        return blurWorkspaceDepthTargets(shouldUseDefaultBlur());
    }

    private boolean blurWorkspaceDepthTargets(boolean useDefaultBlur) {
        LauncherState targetState = getTargetState();
        // Only blur workspace if the current state wants to blur based on the target state.
        boolean shouldBlurWorkspace = shouldBlurWorkspace(targetState);

        applyWorkspaceDepthTargetEffects(shouldBlurWorkspace && useDefaultBlur);
        return shouldBlurWorkspace;
    }

    private boolean updateBlurredSnapshot(boolean shouldBlurWorkspace, boolean wantsBlurredSnapshot,
            float snapshotAlpha, float contentScale, float wallpaperOffset) {
        BlurredSnapshotView snapshotView = mLauncher.getBlurredSnapshotView();
        applyWorkspaceDepthTargetEffects(shouldBlurWorkspace && !wantsBlurredSnapshot);
        if (snapshotView == null) {
            mUsingBlurredSnapshot = false;
            return false;
        }
        if (!wantsBlurredSnapshot) {
            mUsingBlurredSnapshot = false;
            mPreferBlurredSnapshot = false;
            mBlurredSnapshotWallpaperOffset = Float.NaN;
            snapshotView.hideSnapshot();
            return false;
        }
        boolean hasSnapshot = snapshotView.hasSnapshot(
                BlurredSnapshotView.SNAPSHOT_WALLPAPER, wallpaperOffset, mMaxBlurRadius);
        if (!hasSnapshot
                && !captureWallpaperSnapshot(snapshotView, wallpaperOffset, mMaxBlurRadius)) {
            applyWorkspaceDepthTargetEffects(false);
            snapshotView.clearSnapshot();
            mUsingBlurredSnapshot = false;
            mBlurredSnapshotWallpaperOffset = Float.NaN;
            return false;
        }
        snapshotView.setSnapshotProgress(snapshotAlpha, contentScale);
        mUsingBlurredSnapshot = true;
        mBlurredSnapshotWallpaperOffset = wallpaperOffset;
        return true;
    }

    private float getBlurredSnapshotWallpaperOffset() {
        if (mUsingBlurredSnapshot && !Float.isNaN(mBlurredSnapshotWallpaperOffset)) {
            return mBlurredSnapshotWallpaperOffset;
        }
        return mLauncher.getWorkspace().getWallpaperOffsetForCenterPage();
    }

    private boolean captureWallpaperSnapshot(BlurredSnapshotView snapshotView,
            float wallpaperOffset, int blurRadius) {
        return mBlurredSnapshotManager.captureWallpaper(snapshotView, wallpaperOffset, blurRadius);
    }

    private void clearBlurredSnapshot() {
        BlurredSnapshotView snapshotView = mLauncher.getBlurredSnapshotView();
        if (snapshotView != null) {
            snapshotView.clearSnapshot();
        }
        mUsingBlurredSnapshot = false;
        mPreferBlurredSnapshot = false;
        mBlurredSnapshotWallpaperOffset = Float.NaN;
    }

    private void onBlurredSnapshotInvalidated() {
        clearBlurredSnapshot();
        applyDepthAndBlur();
    }

    private void applyWorkspaceDepthTargetEffects(boolean useRenderEffect) {
        int blur = useRenderEffect ? mCurrentBlur : 0;
        if (mAppliedWorkspaceBlur == blur) {
            return;
        }
        mAppliedWorkspaceBlur = blur;
        RenderEffect blurEffect = blur > 0
                ? RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL)
                : null;
        List<View> targets = mLauncher.getDepthBlurTargets();
        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).setRenderEffect(blurEffect);
        }
    }

    private LauncherState getTargetState() {
        if (mGestureTargetState != null) {
            return mGestureTargetState;
        }
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        LauncherState targetState = stateManager.getTargetState();
        return targetState != null ? targetState : stateManager.getState();
    }

    private boolean shouldUseOverviewWallpaperSnapshot(LauncherState targetState) {
        return targetState != null && targetState.isRecentsViewVisible;
    }

    private boolean shouldUseBlurredSnapshot(LauncherState targetState,
            boolean shouldBlurWorkspace) {
        return shouldUseOverviewWallpaperSnapshot(targetState)
                || shouldBlurWorkspace;
    }

    private boolean shouldBlurWorkspace(LauncherState targetState) {
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        return Flags.allAppsBlur()
                && stateManager.getCurrentStableState().shouldBlurWorkspace(targetState);
    }

    private boolean shouldUseDefaultBlur() {
        return mBlurredSnapshotManager.shouldUseDefaultBlur(
                mMaxBlurRadius, MIN_SNAPSHOT_BLUR_RADIUS);
    }

    private float mapWallpaperZoomToScale(float wallpaperZoom) {
        return Utilities.mapRange(1f - Utilities.boundToRange(wallpaperZoom, 0f, 1f),
                1f, mWallpaperMaxScale);
    }

    private float mapSnapshotAlpha(LauncherState targetState, boolean shouldBlurWorkspace,
            float progress, float depth) {
        if (shouldBlurWorkspace) {
            float allAppsProgress = Utilities.boundToRange(
                    mLauncher.getAppsView().getAllAppsTransitionProgress(), 0f, 1f);
            if (mLauncher.getAppsView().isAllAppsTransitionCollapsing()) {
                return Interpolators.clampToProgress(
                        allAppsProgress,
                        SNAPSHOT_COLLAPSE_FADE_START_PROGRESS,
                        SNAPSHOT_COLLAPSE_FADE_END_PROGRESS);
            }
            return allAppsProgress;
        }
        if (targetState == LauncherState.NORMAL) {
            return Utilities.boundToRange(depth / DEPTH_70_PERCENT, 0f, 1f);
        }
        return Utilities.boundToRange(
                progress * mMaxBlurRadius / MIN_SNAPSHOT_BLUR_RADIUS, 0f, 1f);
    }

    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Depth of the Launcher state we are in or transitioning to.
        float targetStateDepth = mLauncher.getStateManager().getState().getDepth(mLauncher);

        float depthF;
        if (depth == targetStateDepth) {
            // Always apply the target state depth.
            depthF = depth;
        } else {
            // Round out the depth to dedupe frequent, non-perceptable updates
            int depthI = (int) (depth * 256);
            depthF = depthI / 256f;
        }
        if (Float.compare(mDepth, depthF) == 0) {
            return;
        }
        mDepth = depthF;
        applyDepthAndBlur();
    }

    /**
     * Sets the lowest surface that should not be blurred.
     * <p>
     * Blur is applied to below {@link #mBaseSurfaceOverride}. When set to {@code null}, blur is
     * applied to below {@link #mBaseSurface}.
     * </p>
     */
    public void setBaseSurfaceOverride(@Nullable SurfaceControl baseSurfaceOverride,
            boolean applyOnDraw) {
        if (mBaseSurfaceOverride != baseSurfaceOverride) {
            boolean applyImmediately = mBaseSurfaceOverride != null && baseSurfaceOverride == null
                    && !applyOnDraw;
            mBaseSurfaceOverride = baseSurfaceOverride;
            if (DEBUG) {
                Log.d(TAG,
                        "setBaseSurfaceOverride: applying blur behind leash "
                                + baseSurfaceOverride);
            }
            SurfaceTransaction transaction = setupBlurSurface();
            applyDepthAndBlur(transaction, applyImmediately, /* skipSimilarBlur */ false);
        }
    }

    private @Nullable SurfaceTransaction setupBlurSurface() {
        SurfaceTransaction surfaceTransaction = null;

        if (mBaseSurface != null && mBaseSurfaceOverride != null) {
            surfaceTransaction = new SurfaceTransaction();
            surfaceTransaction.forSurface(mBaseSurface).setBackgroundBlurRadius(0).setOpaque(false);
            if (mBlurSurface == null) {
                mBlurSurface = new SurfaceControl.Builder()
                        .setName("Overview Blur")
                        .setHidden(false)
                        .build();
                if (DEBUG) {
                    Log.d(TAG,
                            "setupBlurSurface: creating Overview Blur surface " + mBlurSurface);
                }
                surfaceTransaction.forSurface(mBlurSurface).reparent(mBaseSurface);
                if (DEBUG) {
                    Log.d(TAG,
                            "setupBlurSurface: reparenting " + mBlurSurface + " to "
                                    + mBaseSurface);
                }
            }
            surfaceTransaction.forSurface(mBlurSurface).setRelativeLayer(mBaseSurfaceOverride, -1);
            if (DEBUG) {
                Log.d(TAG, "setupBlurSurface: relayering to leash " + mBaseSurfaceOverride);
            }
        } else if (mBlurSurface != null) {
            if (DEBUG) {
                Log.d(TAG, "setupBlurSurface: removing blur surface " + mBlurSurface);
            }
            surfaceTransaction = new SurfaceTransaction();
            surfaceTransaction.forSurface(mBlurSurface).setRemove();
            mBlurSurface = null;
        }
        return surfaceTransaction;
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    protected void setBaseSurface(SurfaceControl baseSurface) {
        if (mBaseSurface != baseSurface || mWaitingOnSurfaceValidity) {
            mBaseSurface = baseSurface;
            if (DEBUG) {
                Log.d(TAG,
                        "setSurface:\n\tmWaitingOnSurfaceValidity: " + mWaitingOnSurfaceValidity
                                + "\n\tmBaseSurface: " + mBaseSurface);
            }
            SurfaceTransaction transaction = null;
            if (enableOverviewBackgroundWallpaperBlur()) {
                transaction = setupBlurSurface();
            }
            applyDepthAndBlur(transaction, /* applyImmediately */ false,
                    /* skipSimilarBlur */ false);
        }
    }

    /**
     * Maps depth values to blur amounts as a percentage of the max blur.
     * The blur percentage grows linearly with depth, and maxes out at 30% depth.
     */
    private static float mapDepthToBlur(float depth) {
        return Interpolators.clampToProgress(depth, 0, 0.3f);
    }
}
