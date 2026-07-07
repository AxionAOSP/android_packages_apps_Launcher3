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

import android.animation.Animator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;
import android.view.View;

import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.views.AxFloatingIconView;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.SurfaceTransactionApplier;

public final class AxQuickstepTransitionManagerExt {
    public static final boolean AX_ANIM_ENGINE_ENABLED = true;
    private static final String TRACE = "icon";

    private AxQuickstepTransitionManagerExt() {}

    public static boolean isAxAnimEngineEnabled(Context context) {
        return AX_ANIM_ENGINE_ENABLED
                && AxHomePackageObserver.INSTANCE
                        .get(context)
                        .isDefaultHome(context.getPackageName());
    }

    public static Animator getOpeningWindowAnimators(
            QuickstepLauncher launcher,
            DeviceProfile deviceProfile,
            SystemUiProxy systemUiProxy,
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
        RectF startBounds =
                AxAppOpenGeometry.getFrozenBounds(launcher, sourceView, launcherIconBounds);
        return AxPlayerImpl.createOpeningAnimator(
                launcher,
                deviceProfile,
                systemUiProxy,
                AxAnimationEngine.APP_OPEN_WINDOW_POSITION_INTERPOLATOR,
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
                startBounds,
                crop,
                matrix,
                surfaceApplier,
                navBarTarget,
                dragLayerBounds);
    }

    public static void updateAppOpenFloatingIcon(
            FloatingIconView view,
            boolean appTargetsAreTranslucent,
            RectF rect,
            float progress,
            float cornerRadius,
            float foregroundAlpha) {
        traceIcon("open", view, rect, progress, foregroundAlpha, cornerRadius);
        AxFloatingIconView.updateAppOpen(
                view,
                appTargetsAreTranslucent,
                rect,
                progress,
                cornerRadius,
                AxAnimationEngine.APP_OPEN_ICON_FOREGROUND_SCALE,
                foregroundAlpha);
    }

    public static void updateHomeGestureFloatingIcon(
            FloatingIconView view,
            float alpha,
            RectF rect,
            float progress,
            float shapeProgressStart,
            float cornerRadius) {
        traceIcon("home", view, rect, progress, alpha, cornerRadius);
        AxFloatingIconView.updateHomeGesture(
                view,
                alpha,
                rect,
                progress,
                shapeProgressStart,
                cornerRadius,
                AxAnimationEngine.getHomeGestureIconForegroundScale(progress));
    }

    public static void prepareHomeGestureFloatingIcon(
            FloatingIconView view, RectF rect, float shapeProgressStart, float cornerRadius) {
        traceIcon("prepareHome", view, rect, 0f, 0f, cornerRadius);
        AxFloatingIconView.prepareHomeGesture(
                view,
                rect,
                shapeProgressStart,
                cornerRadius,
                AxAnimationEngine.getHomeGestureIconForegroundScale(0f));
    }

    private static void traceIcon(
            String phase,
            FloatingIconView view,
            RectF rect,
            float progress,
            float alpha,
            float radius) {
        if (!AxAnimationEngine.isTracing()) {
            return;
        }
        AxAnimationEngine.trace(TRACE, phase
                + " view=" + Integer.toHexString(System.identityHashCode(view))
                + " progress=" + progress
                + " rect=" + rect
                + " alpha=" + alpha
                + " radius=" + radius
                + " visibility=" + view.getVisibility());
    }
}
