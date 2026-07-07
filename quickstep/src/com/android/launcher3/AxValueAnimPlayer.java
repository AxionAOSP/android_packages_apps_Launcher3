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
package com.android.launcher3;

import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;

import static com.android.quickstep.util.AnimUtils.clampToDuration;
import static com.android.systemui.shared.system.QuickStepContract.getWindowCornerRadius;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.AxWallpaperZoom;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.SurfaceTransactionApplier;

final class AxValueAnimPlayer {
    private static final String TRACE = "open";

    private final DeviceProfile mDeviceProfile;
    private final SystemUiProxy mSystemUiProxy;
    private final Interpolator mOpeningInterpolator;
    private final RemoteAnimationTarget[] mAppTargets;
    private final int mRotationChange;
    private final Rect mWindowTargetBounds;
    private final int[] mBottomInsetPos;
    private final Rect mTargetContentInsets = new Rect();
    private final boolean mCropToInset;
    private final boolean mAppTargetsAreTranslucent;
    private final FloatingIconView mFloatingView;
    private final Rect mCrop;
    private final Matrix mMatrix;
    private final RemoteAnimationTarget mNavBarTarget;
    private final int[] mDragLayerBounds;
    private final Point mTmpPos = new Point();
    private final RectF mFloatingIconBounds = new RectF();
    private final Rect mClosingCrop = new Rect();
    private final AxBaseCalculator mCalculator;
    private final AxMultiTargetsUpdateInfo mTargetUpdates = new AxMultiTargetsUpdateInfo();
    private final AxRemoteTargetUpdater mTargetUpdater;
    private final boolean mPlayWallpaperZoom;
    private boolean mFinished;

    AxValueAnimPlayer(
            QuickstepLauncher launcher,
            DeviceProfile deviceProfile,
            SystemUiProxy systemUiProxy,
            Interpolator openingInterpolator,
            RemoteAnimationTarget[] appTargets,
            int rotationChange,
            Rect windowTargetBounds,
            int[] bottomInsetPos,
            RemoteAnimationTarget firstTarget,
            boolean cropToInset,
            boolean appTargetsAreTranslucent,
            FloatingIconView floatingView,
            RectF launcherIconBounds,
            Rect crop,
            Matrix matrix,
            SurfaceTransactionApplier surfaceApplier,
            RemoteAnimationTarget navBarTarget,
            int[] dragLayerBounds) {
        mDeviceProfile = deviceProfile;
        mSystemUiProxy = systemUiProxy;
        mOpeningInterpolator = openingInterpolator;
        mAppTargets = appTargets;
        mRotationChange = rotationChange;
        mWindowTargetBounds = windowTargetBounds;
        mBottomInsetPos = bottomInsetPos;
        updateTargetContentInsets(firstTarget);
        mCropToInset = cropToInset;
        mAppTargetsAreTranslucent = appTargetsAreTranslucent;
        mFloatingView = floatingView;
        mCrop = crop;
        mMatrix = matrix;
        mTargetUpdater = new AxRemoteTargetUpdater(surfaceApplier);
        mNavBarTarget = navBarTarget;
        mDragLayerBounds = dragLayerBounds;
        mPlayWallpaperZoom =
                !launcher.isInState(LauncherState.ALL_APPS) && !appTargetsAreTranslucent;

        RectF launcherIconScreenBounds = new RectF(launcherIconBounds);
        launcherIconScreenBounds.offset(dragLayerBounds[0], dragLayerBounds[1]);
        float initialWindowRadius = AxAnimationEngine.getAppOpenStartRadius(
                launcher.getResources().getDisplayMetrics().density);
        float finalWindowRadius = getWindowCornerRadius(launcher);
        mCalculator =
                new AxBaseCalculator(
                        deviceProfile.getDeviceProperties().getWidthPx(),
                        deviceProfile.getDeviceProperties().getHeightPx(),
                        launcherIconScreenBounds,
                        initialWindowRadius,
                        finalWindowRadius);
    }

    ValueAnimator createAnimator() {
        ValueAnimator appAnimator = ValueAnimator.ofFloat(0f, 1f);
        appAnimator.setDuration(AxAnimationEngine.APP_LAUNCH_DURATION);
        appAnimator.setInterpolator(Interpolators.LINEAR);

        float iconAlphaStart = mAppTargetsAreTranslucent ? 0f : 1f;
        MultiValueUpdateListener listener =
                new MultiValueUpdateListener() {
                    FloatProp mIconAlpha =
                            new FloatProp(
                                    iconAlphaStart,
                                    0f,
                                    Interpolators.clampToProgress(
                                            AxAnimationEngine.APP_OPEN_ICON_ALPHA_INTERPOLATOR,
                                            AxAnimationEngine.APP_OPEN_ICON_ALPHA_START_PROGRESS,
                                            AxAnimationEngine.APP_OPEN_ICON_ALPHA_END_PROGRESS));
                    FloatProp mWindowAlpha =
                            new FloatProp(
                                    0f,
                                    1f,
                                    clampToDuration(
                                            AxAnimationEngine.APP_OPEN_WINDOW_ALPHA_INTERPOLATOR,
                                            AxAnimationEngine.APP_OPEN_WINDOW_ALPHA_START_DELAY,
                                            AxAnimationEngine.APP_OPEN_WINDOW_ALPHA_DURATION,
                                            AxAnimationEngine.APP_LAUNCH_DURATION));
                    FloatProp mNavFadeOut =
                            new FloatProp(
                                    1f,
                                    0f,
                                    clampToDuration(
                                            QuickstepTransitionManager.NAV_FADE_OUT_INTERPOLATOR,
                                            0,
                                            QuickstepTransitionManager
                                                    .ANIMATION_NAV_FADE_OUT_DURATION,
                                            AxAnimationEngine.APP_LAUNCH_DURATION));
                    FloatProp mNavFadeIn =
                            new FloatProp(
                                    0f,
                                    1f,
                                    clampToDuration(
                                            QuickstepTransitionManager.NAV_FADE_IN_INTERPOLATOR,
                                            AxAnimationEngine.APP_LAUNCH_DURATION
                                                    - QuickstepTransitionManager
                                                            .ANIMATION_NAV_FADE_IN_DURATION,
                                            QuickstepTransitionManager
                                                    .ANIMATION_NAV_FADE_IN_DURATION,
                                            AxAnimationEngine.APP_LAUNCH_DURATION));

                    @Override
                    public void onUpdate(float percent, boolean initOnly) {
                        applyFrame(
                                percent,
                                initOnly,
                                mWindowAlpha.value,
                                mIconAlpha.value,
                                mNavFadeIn.value,
                                mNavFadeOut.value);
                    }
                };
        appAnimator.addUpdateListener(listener);
        appAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        AxAnimationEngine.trace(
                                TRACE, "start player=" + id(AxValueAnimPlayer.this));
                        listener.onUpdate(0f, false);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        AxAnimationEngine.trace(
                                TRACE, "cancel player=" + id(AxValueAnimPlayer.this));
                        finish();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        AxAnimationEngine.trace(
                                TRACE, "end player=" + id(AxValueAnimPlayer.this));
                        finish();
                    }
                });
        listener.onUpdate(0f, true);
        return appAnimator;
    }

    Animator createWallpaperAnimator() {
        return AxWallpaperZoom.createAppOpenAnimator(mSystemUiProxy, mPlayWallpaperZoom);
    }

    private void applyFrame(
            float percent,
            boolean initOnly,
            float windowAlpha,
            float iconAlpha,
            float navFadeIn,
            float navFadeOut) {
        if (mFinished && !initOnly) {
            return;
        }
        updateInsetTarget();
        float positionProgress = mOpeningInterpolator.getInterpolation(percent);
        AxFloatingFrame frame =
                mCalculator.calculate(
                        mWindowTargetBounds,
                        mTargetContentInsets.isEmpty() ? null : mTargetContentInsets,
                        positionProgress);
        mCrop.set(frame.crop);
        int windowCropWidth = mCrop.width();
        int windowCropHeight = mCrop.height();
        if (mRotationChange != 0) {
            Utilities.rotateBounds(
                    mCrop,
                    mDeviceProfile.getDeviceProperties().getWidthPx(),
                    mDeviceProfile.getDeviceProperties().getHeightPx(),
                    mRotationChange);
        }

        float scaledCropWidth = windowCropWidth * frame.scale;
        float scaledCropHeight = windowCropHeight * frame.scale;
        float windowTransX = frame.currentRect.left - mCrop.left * frame.scale;
        float windowTransY = frame.currentRect.top - mCrop.top * frame.scale;

        mFloatingIconBounds.set(frame.currentRect);
        mFloatingIconBounds.offset(-mDragLayerBounds[0], -mDragLayerBounds[1]);
        AxQuickstepTransitionManagerExt.updateAppOpenFloatingIcon(
                mFloatingView,
                mAppTargetsAreTranslucent,
                mFloatingIconBounds,
                percent,
                frame.floatingRadius,
                iconAlpha);

        if (initOnly) {
            return;
        }

        mTargetUpdates.clear();
        for (int i = mAppTargets.length - 1; i >= 0; i--) {
            RemoteAnimationTarget target = mAppTargets[i];
            if (target.mode == MODE_OPENING) {
                addOpeningTargetUpdate(
                        target,
                        frame,
                        percent,
                        positionProgress,
                        scaledCropWidth,
                        scaledCropHeight,
                        windowTransX,
                        windowTransY,
                        windowAlpha);
            } else if (target.mode == MODE_CLOSING) {
                addClosingTargetUpdate(target);
            }
        }
        addNavBarUpdate(frame, windowTransX, windowTransY, navFadeIn, navFadeOut);
        mTargetUpdater.apply(mTargetUpdates);
    }

    private void updateInsetTarget() {
        if (!mCropToInset
                || mBottomInsetPos[0]
                        == mSystemUiProxy.getHomeVisibilityState().getNavbarInsetPosition()) {
            return;
        }
        RemoteAnimationTarget target = getOpeningTarget();
        mBottomInsetPos[0] = mSystemUiProxy.getHomeVisibilityState().getNavbarInsetPosition();
        Rect bounds = target != null ? target.screenSpaceBounds : mWindowTargetBounds;
        mWindowTargetBounds.bottom = Math.min(mBottomInsetPos[0], bounds.bottom);
    }

    private RemoteAnimationTarget getOpeningTarget() {
        for (int i = mAppTargets.length - 1; i >= 0; i--) {
            RemoteAnimationTarget target = mAppTargets[i];
            if (target.mode == MODE_OPENING) {
                return target;
            }
        }
        return null;
    }

    private void addOpeningTargetUpdate(
            RemoteAnimationTarget target,
            AxFloatingFrame frame,
            float progress,
            float positionProgress,
            float scaledCropWidth,
            float scaledCropHeight,
            float windowTransX,
            float windowTransY,
            float windowAlpha) {
        setOpeningMatrix(frame, scaledCropWidth, scaledCropHeight, windowTransX, windowTransY);
        if (AxAnimationEngine.isTracing()) {
            AxAnimationEngine.trace(TRACE, "frame player=" + id(this)
                    + " task=" + target.taskId
                    + " leash=" + id(target.leash)
                    + " progress=" + progress
                    + " position=" + positionProgress
                    + " rect=" + frame.currentRect
                    + " crop=" + mCrop
                    + " matrix=" + mMatrix
                    + " alpha=" + windowAlpha
                    + " radius=" + frame.radius
                    + " visibleRadius=" + frame.floatingRadius);
        }
        mTargetUpdates.add(
                target,
                mMatrix,
                mCrop,
                windowAlpha,
                frame.radius,
                AxAnimationEngine.APP_OPEN_SHADOW_RADIUS);
    }

    private void setOpeningMatrix(
            AxFloatingFrame frame,
            float scaledCropWidth,
            float scaledCropHeight,
            float windowTransX,
            float windowTransY) {
        mMatrix.setScale(frame.scale, frame.scale);
        if (mRotationChange == 1) {
            mMatrix.postTranslate(
                    windowTransY,
                    mDeviceProfile.getDeviceProperties().getWidthPx()
                            - (windowTransX + scaledCropWidth));
        } else if (mRotationChange == 2) {
            mMatrix.postTranslate(
                    mDeviceProfile.getDeviceProperties().getWidthPx()
                            - (windowTransX + scaledCropWidth),
                    mDeviceProfile.getDeviceProperties().getHeightPx()
                            - (windowTransY + scaledCropHeight));
        } else if (mRotationChange == 3) {
            mMatrix.postTranslate(
                    mDeviceProfile.getDeviceProperties().getHeightPx()
                            - (windowTransY + scaledCropHeight),
                    windowTransX);
        } else {
            mMatrix.postTranslate(windowTransX, windowTransY);
        }
    }

    private void addClosingTargetUpdate(RemoteAnimationTarget target) {
        if (target.localBounds != null) {
            mTmpPos.set(target.localBounds.left, target.localBounds.top);
        } else {
            mTmpPos.set(target.position.x, target.position.y);
        }
        mClosingCrop.set(target.screenSpaceBounds);
        mClosingCrop.offsetTo(0, 0);

        if ((mRotationChange % 2) == 1) {
            int tmp = mClosingCrop.right;
            mClosingCrop.right = mClosingCrop.bottom;
            mClosingCrop.bottom = tmp;
            tmp = mTmpPos.x;
            mTmpPos.x = mTmpPos.y;
            mTmpPos.y = tmp;
        }
        mMatrix.setTranslate(mTmpPos.x, mTmpPos.y);
        mTargetUpdates.add(target, mMatrix, mClosingCrop, 1f, -1f, -1f);
    }

    private void addNavBarUpdate(
            AxFloatingFrame frame,
            float windowTransX,
            float windowTransY,
            float navFadeIn,
            float navFadeOut) {
        if (mNavBarTarget == null) {
            return;
        }
        if (navFadeIn > 0f) {
            mMatrix.setScale(frame.scale, frame.scale);
            mMatrix.postTranslate(windowTransX, windowTransY);
            mTargetUpdates.add(mNavBarTarget, mMatrix, mCrop, navFadeIn, -1f, -1f);
            return;
        }
        mTargetUpdates.add(mNavBarTarget, null, null, navFadeOut, -1f, -1f);
    }

    private void finish() {
        if (mFinished) {
            return;
        }
        mFinished = true;
        AxAnimationEngine.trace(TRACE, "finish player=" + id(this));
    }

    private static String id(Object value) {
        return value == null ? "0" : Integer.toHexString(System.identityHashCode(value));
    }

    private void updateTargetContentInsets(RemoteAnimationTarget target) {
        if (target == null || target.contentInsets == null) {
            mTargetContentInsets.setEmpty();
            return;
        }
        mTargetContentInsets.set(target.contentInsets);
    }
}
