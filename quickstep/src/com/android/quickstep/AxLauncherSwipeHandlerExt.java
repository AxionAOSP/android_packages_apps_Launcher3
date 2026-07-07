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
package com.android.quickstep;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AxAppOpenGeometry;
import com.android.launcher3.AxQuickstepTransitionManagerExt;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.AxWallpaperZoom;
import com.android.quickstep.views.FloatingWidgetView;

final class AxLauncherSwipeHandlerExt {
    private static final String TAG = "AxLauncherSwipeHandler";

    private AxLauncherSwipeHandlerExt() {}

    static boolean useHomeAnimation(
            Context context,
            boolean hasTaskView,
            boolean canEnterPip,
            boolean isSplit,
            boolean isDesktop,
            RemoteTargetHandle[] handles) {
        boolean oneHandle = handles.length == 1;
        boolean defaultHome = AxQuickstepTransitionManagerExt.isAxAnimEngineEnabled(context);
        boolean hasAppTarget =
                oneHandle
                        && AxSwipeUpAnimationLogicExt.getFirstAppTarget(
                                        handles[0].getTransformParams())
                                != null;
        boolean useAx =
                !hasTaskView
                        && !canEnterPip
                        && !isSplit
                        && !isDesktop
                        && oneHandle
                        && defaultHome;
        AxAnimationEngine.trace(
                TAG,
                "homeRoute useAx="
                        + useAx
                        + " taskView="
                        + hasTaskView
                        + " pip="
                        + canEnterPip
                        + " split="
                        + isSplit
                        + " desktop="
                        + isDesktop
                        + " handles="
                        + handles.length
                        + " defaultHome="
                        + defaultHome
                        + " appTarget="
                        + hasAppTarget);
        return useAx;
    }

    static boolean useShellHandoff(boolean canHandOff, boolean useAxAnimation) {
        boolean handOff = canHandOff && !useAxAnimation;
        AxAnimationEngine.trace(
                TAG,
                "shellHandoff canHandOff="
                        + canHandOff
                        + " useAx="
                        + useAxAnimation
                        + " result="
                        + handOff);
        return handOff;
    }

    static void setGestureRadius(
            Context context, RemoteTargetHandle[] handles, boolean active) {
        boolean enabled =
                active
                        && handles.length == 1
                        && AxQuickstepTransitionManagerExt.isAxAnimEngineEnabled(context);
        for (RemoteTargetHandle handle : handles) {
            handle.getTaskViewSimulator().setAxGestureRadius(enabled);
        }
        AxAnimationEngine.trace(
                TAG,
                "gestureRadius enabled=" + enabled + " handles=" + handles.length);
    }

    static void startHomeZoom(Context context) {
        boolean enabled = AxQuickstepTransitionManagerExt.isAxAnimEngineEnabled(context);
        AxAnimationEngine.trace(TAG, "homeZoom enabled=" + enabled);
        if (enabled) {
            AxWallpaperZoom.startHomeGesture(SystemUiProxy.INSTANCE.get(context));
        }
    }

    static float getIconAlpha(boolean axAnim, float progress, float threshold) {
        return axAnim
                ? AxAnimationEngine.getHomeGestureIconAlpha(progress)
                : Interpolators.clampToProgress(progress, 0f, threshold);
    }

    static float getIconAlphaEndProgress() {
        return AxAnimationEngine.HOME_GESTURE_ICON_ALPHA_END_PROGRESS;
    }

    static RectF getFrozenTargetBounds(QuickstepLauncher launcher, View view, RectF fallback) {
        return AxAppOpenGeometry.getFrozenBounds(launcher, view, fallback);
    }

    static float getWidgetBackgroundAlpha(boolean axAnim, float progress, float fallback) {
        return axAnim ? AxAnimationEngine.getHomeGestureWidgetBackgroundAlpha(progress) : fallback;
    }

    static float getWidgetForegroundAlpha(boolean axAnim, float progress, float fallback) {
        return axAnim ? AxAnimationEngine.getHomeGestureIconAlpha(progress) : fallback;
    }

    static float getWindowAlpha(float progress) {
        return AxAnimationEngine.getHomeGestureWindowAlpha(progress);
    }

    static void prepareFloatingIcon(
            boolean axAnim, FloatingIconView view, RectF bounds, float threshold) {
        if (axAnim) {
            AxQuickstepTransitionManagerExt.prepareHomeGestureFloatingIcon(
                    view, bounds, threshold, 0f);
        }
    }

    static boolean updateFloatingIcon(
            boolean axAnim,
            FloatingIconView view,
            float alpha,
            RectF bounds,
            float progress,
            float threshold,
            float radius) {
        if (!axAnim) {
            return false;
        }
        AxQuickstepTransitionManagerExt.updateHomeGestureFloatingIcon(
                view, alpha, bounds, progress, threshold, radius);
        return true;
    }

    static void updateFloatingWidget(
            boolean axAnim,
            FloatingWidgetView view,
            RectF bounds,
            float floatingAlpha,
            float foregroundAlpha,
            float backgroundAlpha,
            float progress,
            float windowAlpha) {
        if (axAnim) {
            view.updateForHomeGesture(
                    bounds,
                    floatingAlpha,
                    foregroundAlpha,
                    backgroundAlpha,
                    1f - progress,
                    windowAlpha);
            return;
        }
        view.update(bounds, floatingAlpha, foregroundAlpha, backgroundAlpha, 1f - progress);
    }
}
