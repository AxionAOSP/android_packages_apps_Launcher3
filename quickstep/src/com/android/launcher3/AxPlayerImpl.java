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

import static android.provider.Settings.Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING;

import static com.android.launcher3.Flags.refactorTaskbarUiState;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.RemoteAnimationTarget;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.SurfaceTransactionApplier;

final class AxPlayerImpl {
    private final QuickstepLauncher mLauncher;
    private final View mSourceView;
    private final RemoteAnimationTargets mOpeningTargets;
    private final FloatingIconView mFloatingView;
    private final AxValueAnimPlayer mValueAnimPlayer;

    static Animator createOpeningAnimator(
            QuickstepLauncher launcher,
            DeviceProfile deviceProfile,
            SystemUiProxy systemUiProxy,
            Interpolator openingInterpolator,
            View sourceView,
            RemoteAnimationTargets openingTargets,
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
        return new AxPlayerImpl(
                        launcher,
                        deviceProfile,
                        systemUiProxy,
                        openingInterpolator,
                        sourceView,
                        openingTargets,
                        appTargets,
                        rotationChange,
                        windowTargetBounds,
                        bottomInsetPos,
                        firstTarget,
                        cropToInset,
                        appTargetsAreTranslucent,
                        floatingView,
                        launcherIconBounds,
                        crop,
                        matrix,
                        surfaceApplier,
                        navBarTarget,
                        dragLayerBounds)
                .createAnimator();
    }

    private AxPlayerImpl(
            QuickstepLauncher launcher,
            DeviceProfile deviceProfile,
            SystemUiProxy systemUiProxy,
            Interpolator openingInterpolator,
            View sourceView,
            RemoteAnimationTargets openingTargets,
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
        mLauncher = launcher;
        mSourceView = sourceView;
        mOpeningTargets = openingTargets;
        mFloatingView = floatingView;
        mValueAnimPlayer =
                new AxValueAnimPlayer(
                        launcher,
                        deviceProfile,
                        systemUiProxy,
                        openingInterpolator,
                        appTargets,
                        rotationChange,
                        windowTargetBounds,
                        bottomInsetPos,
                        firstTarget,
                        cropToInset,
                        appTargetsAreTranslucent,
                        floatingView,
                        launcherIconBounds,
                        crop,
                        matrix,
                        surfaceApplier,
                        navBarTarget,
                        dragLayerBounds);
    }

    private Animator createAnimator() {
        ValueAnimator appAnimator = mValueAnimPlayer.createAnimator();
        appAnimator.addListener(mFloatingView);
        appAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (shouldShowEduOnAppLaunch()) {
                            Settings.Secure.putInt(
                                    mLauncher.getContentResolver(),
                                    LAUNCHER_TASKBAR_EDUCATION_SHOWING,
                                    1);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mSourceView instanceof BubbleTextView icon) {
                            icon.setStayPressed(false);
                        }
                        TaskbarInteractor taskbarInteractor = mLauncher.getTaskbarInteractor();
                        if (taskbarInteractor != null) {
                            taskbarInteractor.showEduOnAppLaunch();
                        }
                        mOpeningTargets.release();
                    }
                });

        AnimatorSet animator = new AnimatorSet();
        Animator wallpaperAnimator = mValueAnimPlayer.createWallpaperAnimator();
        if (wallpaperAnimator == null) {
            animator.play(appAnimator);
        } else {
            animator.playTogether(appAnimator, wallpaperAnimator);
        }
        return animator;
    }

    private boolean shouldShowEduOnAppLaunch() {
        if (refactorTaskbarUiState()) {
            boolean showEdu = newShouldShowEduOnAppLaunch();
            if (BuildConfig.IS_STUDIO_BUILD && showEdu != legacyShouldShowEduOnAppLaunch()) {
                throw new IllegalStateException("shouldShowEduOnAppLaunch() doesn't match");
            }
            return showEdu;
        }
        return legacyShouldShowEduOnAppLaunch();
    }

    private boolean legacyShouldShowEduOnAppLaunch() {
        return mLauncher.getTaskbarInteractor() != null
                && mLauncher.getTaskbarInteractor().shouldShowEduOnAppLaunch();
    }

    private boolean newShouldShowEduOnAppLaunch() {
        return mLauncher.getTaskbarUiState().getShouldShowEduOnAppLaunchRef().getValue();
    }
}
