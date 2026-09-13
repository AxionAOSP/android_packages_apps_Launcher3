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

import com.android.launcher3.Utilities;

final class AxStackLayout {
    static final int TASK_PRELOAD_RANGE = 5;

    private static final float PORTRAIT_STACK_SPACING_FACTOR = 0.23f;
    private static final float LANDSCAPE_STACK_SPACING_FACTOR = 0.18f;
    private static final float ABOVE_TASK_GAP_FACTOR = 0.076f;
    private static final float SCALE_STEP = 0.07f;
    private static final float MENU_FULL_ALPHA_DISTANCE = 0.28f;
    private static final float MENU_ZERO_ALPHA_DISTANCE = 0.72f;
    private static final float DECELERATE_70 = 0.7f;

    boolean isDistanceActive(float distance, boolean naturalLayout) {
        return distance >= -TASK_PRELOAD_RANGE && distance <= 4f;
    }

    float getTaskAlpha(float distance, boolean naturalLayout) {
        if (distance <= 2.5f) {
            return 1f;
        }
        if (distance >= 3.5f) {
            return 0f;
        }
        return 1f - (distance - 2.5f);
    }

    private float getMenuAlpha(float distance) {
        float absoluteDistance = Math.abs(distance);
        if (absoluteDistance <= MENU_FULL_ALPHA_DISTANCE) {
            return 1f;
        }
        if (absoluteDistance >= MENU_ZERO_ALPHA_DISTANCE) {
            return 0f;
        }
        float progress = (absoluteDistance - MENU_FULL_ALPHA_DISTANCE)
                / (MENU_ZERO_ALPHA_DISTANCE - MENU_FULL_ALPHA_DISTANCE);
        return 1f - decelerate(progress, DECELERATE_70);
    }

    void getTransform(float distance, float normalDelta, float reflowTranslation,
            float anchorDistance, float primarySize, boolean naturalLayout, boolean rtl,
            Transform out) {
        if (distance < -TASK_PRELOAD_RANGE || distance > 4.5f) {
            out.set(1f, 0f, 0f, 0f);
            return;
        }
        if (distance <= 0f) {
            float translation = anchorDistance <= 0f ? -reflowTranslation : 0f;
            float aboveGap = primarySize * ABOVE_TASK_GAP_FACTOR;
            float extraGap = -distance * aboveGap;
            float totalTranslation = translation + (rtl ? -extraGap : extraGap);
            float scale = getAboveTaskScale(distance);
            out.set(scale, rtl ? -totalTranslation : totalTranslation, 1f, distance < 0f ? 0f : 1f);
            return;
        }
        if (anchorDistance <= 0f) {
            float translation = -reflowTranslation;
            out.set(1f, rtl ? -translation : translation, 1f, 1f);
            return;
        }
        float stackSpacing = getStackSpacing(primarySize, naturalLayout);
        float desiredDelta = distance * stackSpacing;
        float scale = getStackScale(distance, naturalLayout);
        float alpha = getTaskAlpha(distance, naturalLayout);
        float translation = desiredDelta - normalDelta;
        out.set(scale, rtl ? -translation : translation, alpha, getMenuAlpha(distance));
    }

    private float getStackSpacing(float primarySize, boolean naturalLayout) {
        float factor = naturalLayout ? PORTRAIT_STACK_SPACING_FACTOR : LANDSCAPE_STACK_SPACING_FACTOR;
        return primarySize * factor;
    }

    float getStackDepth(float distance, boolean naturalLayout) {
        if (distance <= 0f) {
            return 1f;
        }
        return Math.max(0f, 1f - distance / 3.0f);
    }

    private float getStackScale(float distance, boolean naturalLayout) {
        if (distance <= 0f) {
            return 1f;
        }
        float step = naturalLayout ? SCALE_STEP : 0.05f;
        return Math.max(0.75f, 1f - distance * step);
    }

    private float getAboveTaskScale(float distance) {
        if (distance >= 0f) {
            return 1f;
        }
        return 1f + Math.min(SCALE_STEP, -distance * SCALE_STEP);
    }

    private static float decelerate(float progress, float factor) {
        float boundedProgress = Utilities.boundToRange(progress, 0f, 1f);
        return (float) (1f - Math.pow(1f - boundedProgress, 2f * factor));
    }

    static final class Transform {
        float scale = 1f;
        float primaryTranslation;
        float alpha = 1f;
        float iconAlpha = 1f;

        void set(float scale, float primaryTranslation, float alpha, float iconAlpha) {
            this.scale = scale;
            this.primaryTranslation = primaryTranslation;
            this.alpha = alpha;
            this.iconAlpha = iconAlpha;
        }
    }
}
