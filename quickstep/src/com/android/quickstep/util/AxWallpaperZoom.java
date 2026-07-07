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
package com.android.quickstep.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.Interpolator;

import com.android.quickstep.SystemUiProxy;

public final class AxWallpaperZoom {
    private static final String TAG = "AxWallpaperZoom";
    private static final float EPSILON = 0.0001f;

    private static ValueAnimator sAnimator;
    private static float sZoomOut;
    private static Token sToken;

    private AxWallpaperZoom() {}

    public static Animator createAppOpenAnimator(SystemUiProxy systemUiProxy, boolean enabled) {
        return enabled ? createZoomOutAnimator(systemUiProxy) : null;
    }

    public static void startZoomOut(SystemUiProxy systemUiProxy) {
        createZoomOutAnimator(systemUiProxy).start();
    }

    public static void startHomeGesture(SystemUiProxy systemUiProxy) {
        AxAnimationEngine.trace(TAG, "homeGesture start");
        startZoomIn(systemUiProxy);
    }

    public static void startZoomIn(SystemUiProxy systemUiProxy) {
        createAnimator(
                systemUiProxy,
                0f,
                AxAnimationEngine.WALLPAPER_HOME_GESTURE_DURATION,
                AxAnimationEngine.WALLPAPER_HOME_GESTURE_INTERPOLATOR).start();
    }

    private static ValueAnimator createZoomOutAnimator(SystemUiProxy systemUiProxy) {
        return createAnimator(
                systemUiProxy,
                AxAnimationEngine.WALLPAPER_APP_OPEN_ZOOM_OUT,
                AxAnimationEngine.WALLPAPER_APP_OPEN_DURATION,
                AxAnimationEngine.WALLPAPER_APP_OPEN_INTERPOLATOR);
    }

    private static ValueAnimator createAnimator(
            SystemUiProxy systemUiProxy,
            float endZoomOut,
            long duration,
            Interpolator interpolator) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        ZoomState state = new ZoomState(systemUiProxy, animator, endZoomOut);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addListener(state);
        animator.addUpdateListener(animation -> state.update((float) animation.getAnimatedValue()));
        return animator;
    }

    private static void begin(ZoomState state) {
        ValueAnimator animator = sAnimator;
        boolean continuing = animator != null && animator != state.mAnimator;
        if (continuing) {
            sAnimator = null;
            animator.cancel();
        }
        sAnimator = state.mAnimator;
        state.mStartZoomOut = continuing ? sZoomOut : getCurrentVisualZoomOut(state.mSystemUiProxy);
        state.mToken = new Token();
        sToken = state.mToken;
        AxAnimationEngine.trace(
                TAG,
                "begin start="
                        + state.mStartZoomOut
                        + " end="
                        + state.mEndZoomOut
                        + " continuing="
                        + continuing);
        setZoom(state.mSystemUiProxy, state.mStartZoomOut, true);
    }

    private static void end(ZoomState state, float zoomOut) {
        if (!isActive(state)) {
            return;
        }
        AxAnimationEngine.trace(TAG, "end zoom=" + zoomOut);
        setZoom(state.mSystemUiProxy, zoomOut);
        sAnimator = null;
        sToken = null;
    }

    private static boolean isActive(ZoomState state) {
        return sAnimator == state.mAnimator && sToken == state.mToken;
    }

    private static float getCurrentVisualZoomOut(SystemUiProxy systemUiProxy) {
        sZoomOut = boundToUnit(systemUiProxy.getLauncherWallpaperZoom());
        return sZoomOut;
    }

    private static void setZoom(SystemUiProxy systemUiProxy, float zoomOut) {
        setZoom(systemUiProxy, zoomOut, false);
    }

    private static void setZoom(SystemUiProxy systemUiProxy, float zoomOut, boolean force) {
        float boundedZoom = boundToUnit(zoomOut);
        if (!force && Math.abs(sZoomOut - boundedZoom) < EPSILON) {
            return;
        }
        sZoomOut = boundedZoom;
        systemUiProxy.setLauncherWallpaperZoom(boundedZoom);
    }

    private static float valueAt(float start, float end, float progress) {
        return start + (end - start) * boundToUnit(progress);
    }

    private static float boundToUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class ZoomState extends AnimatorListenerAdapter {
        private final SystemUiProxy mSystemUiProxy;
        private final ValueAnimator mAnimator;
        private final float mEndZoomOut;
        private float mStartZoomOut;
        private Token mToken;
        private boolean mCancelled;

        private ZoomState(SystemUiProxy systemUiProxy, ValueAnimator animator, float endZoomOut) {
            mSystemUiProxy = systemUiProxy;
            mAnimator = animator;
            mEndZoomOut = endZoomOut;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCancelled = false;
            begin(this);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCancelled = true;
            end(this, sZoomOut);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCancelled) {
                end(this, mEndZoomOut);
            }
        }

        private void update(float progress) {
            if (!isActive(this)) {
                return;
            }
            setZoom(mSystemUiProxy, valueAt(mStartZoomOut, mEndZoomOut, progress));
        }
    }

    private record Token() {}
}
