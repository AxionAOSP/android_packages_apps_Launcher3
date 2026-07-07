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
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.AxRectFSpringAnim;
import com.android.quickstep.util.AxSpringAnimPlayer;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;

final class AxSwipeUpAnimationLogicExt {
    private static final String TAG = "AxSwipeUpAnimationLogic";

    private AxSwipeUpAnimationLogicExt() {}

    @Nullable
    static RemoteAnimationTarget getFirstAppTarget(TransformParams transformParams) {
        RemoteAnimationTargets targetSet = transformParams.getTargetSet();
        return targetSet == null ? null : targetSet.getFirstAppTarget();
    }

    static AxRectFSpringAnim createHomeGestureAnim(
            Context context,
            DeviceProfile deviceProfile,
            RectF startRect,
            RectF targetRect,
            boolean useTaskbarHotseatParams) {
        AxAnimationEngine.trace(
                TAG,
                "createHomeSpring start="
                        + startRect
                        + " target="
                        + targetRect
                        + " taskbar="
                        + useTaskbarHotseatParams);
        return AxSpringAnimPlayer.createHomeGestureAnim(
                context, deviceProfile, startRect, targetRect, useTaskbarHotseatParams);
    }

    static float getHomeRadius(Context context, TaskViewSimulator taskViewSimulator) {
        float fullscreenProgress = taskViewSimulator.fullScreenProgress.value;
        float radius =
                AxAnimationEngine.getHomeRadius(
                        context.getResources().getDisplayMetrics().density,
                        fullscreenProgress);
        AxAnimationEngine.trace(
                TAG,
                "homeRadius visible="
                        + radius
                        + " fullscreen="
                        + fullscreenProgress);
        return radius;
    }
}
