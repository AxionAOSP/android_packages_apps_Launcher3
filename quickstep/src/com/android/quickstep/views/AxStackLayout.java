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
    static final int TASK_PRELOAD_RANGE = 4;

    private static final float PORTRAIT_LEFT_NEAR_OFFSET = 0.24436f;
    private static final float PORTRAIT_LEFT_MID_OFFSET = 0.30843f;
    private static final float PORTRAIT_LEFT_FAR_OFFSET = 0.35f;
    private static final float LANDSCAPE_LEFT_NEAR_OFFSET = 0.198572f;
    private static final float LANDSCAPE_LEFT_MID_OFFSET = 0.336819f;
    private static final float LANDSCAPE_LEFT_FAR_OFFSET = 0.414775f;
    private static final float LANDSCAPE_LEFT_END_OFFSET = 0.43247f;
    private static final float LEFT_NEAR_SCALE = 0.96f;
    private static final float LEFT_MID_SCALE = 0.9216f;
    private static final float LEFT_FAR_SCALE = 0.884736f;
    private static final float LEFT_END_SCALE = 0.8493466f;
    private static final float LEFT_FULL_ALPHA_DISTANCE = 2f;
    private static final float PORTRAIT_LEFT_STACK_DISTANCE = 3f;
    private static final float LANDSCAPE_LEFT_FAR_DISTANCE = 3f;
    private static final float LANDSCAPE_LEFT_STACK_DISTANCE = 4f;
    private static final float LANDSCAPE_FAR_ALPHA = 0.2f;
    private static final float EDGE_BLEND_DISTANCE = 0.25f;
    private static final float MENU_FULL_ALPHA_DISTANCE = 0.28f;
    private static final float MENU_ZERO_ALPHA_DISTANCE = 0.72f;
    private static final float DECELERATE_55 = 0.55f;
    private static final float DECELERATE_65 = 0.65f;
    private static final float DECELERATE_70 = 0.7f;
    boolean isDistanceActive(float distance, boolean naturalLayout) {
        return distance >= -TASK_PRELOAD_RANGE
                && distance <= getLeftStackDistance(naturalLayout) + EDGE_BLEND_DISTANCE;
    }

    float getTaskAlpha(float distance, boolean naturalLayout) {
        if (distance <= LEFT_FULL_ALPHA_DISTANCE) {
            return 1f;
        }
        if (naturalLayout) {
            return 1f - decelerate(
                    distance - LEFT_FULL_ALPHA_DISTANCE, DECELERATE_65);
        }
        if (distance <= LANDSCAPE_LEFT_FAR_DISTANCE) {
            return interpolate(1f, LANDSCAPE_FAR_ALPHA,
                    decelerate(distance - LEFT_FULL_ALPHA_DISTANCE, DECELERATE_65));
        }
        if (distance <= LANDSCAPE_LEFT_STACK_DISTANCE) {
            return interpolate(LANDSCAPE_FAR_ALPHA, 0f,
                    decelerate(distance - LANDSCAPE_LEFT_FAR_DISTANCE, DECELERATE_65));
        }
        return 0f;
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
        if (distance < -TASK_PRELOAD_RANGE) {
            out.set(1f, 0f, 0f, 0f);
            return;
        }
        if (distance <= 0f) {
            float translation = anchorDistance <= 0f ? -reflowTranslation : 0f;
            out.set(1f, rtl ? -translation : translation, 1f, 1f);
            return;
        }
        if (distance > getLeftStackDistance(naturalLayout) + EDGE_BLEND_DISTANCE) {
            out.set(1f, 0f, 0f, 0f);
            return;
        }
        float desiredDelta;
        float scale = getStackScale(distance, naturalLayout);
        float alpha;
        if (distance <= 1f) {
            float progress = decelerate(distance, DECELERATE_65);
            desiredDelta = interpolate(
                    0f, getLeftNearOffset(naturalLayout), progress) * primarySize;
            alpha = 1f;
        } else if (distance <= 2f) {
            float progress = decelerate(distance - 1f, DECELERATE_65);
            desiredDelta = interpolate(
                    getLeftNearOffset(naturalLayout),
                    getLeftMidOffset(naturalLayout), progress) * primarySize;
            alpha = 1f;
        } else if (distance <= 3f) {
            float progress = decelerate(distance - 2f, DECELERATE_65);
            desiredDelta = interpolate(
                    getLeftMidOffset(naturalLayout),
                    getLeftFarOffset(naturalLayout), progress) * primarySize;
            alpha = naturalLayout
                    ? 1f - progress
                    : interpolate(1f, LANDSCAPE_FAR_ALPHA, progress);
        } else if (!naturalLayout && distance <= LANDSCAPE_LEFT_STACK_DISTANCE) {
            float progress = decelerate(
                    distance - LANDSCAPE_LEFT_FAR_DISTANCE, DECELERATE_65);
            desiredDelta = interpolate(
                    LANDSCAPE_LEFT_FAR_OFFSET,
                    LANDSCAPE_LEFT_END_OFFSET, progress) * primarySize;
            alpha = interpolate(LANDSCAPE_FAR_ALPHA, 0f, progress);
        } else {
            float stackDistance = getLeftStackDistance(naturalLayout);
            float progress = decelerate(
                    (distance - stackDistance) / EDGE_BLEND_DISTANCE, DECELERATE_55);
            float stackOffset = naturalLayout
                    ? PORTRAIT_LEFT_FAR_OFFSET : LANDSCAPE_LEFT_END_OFFSET;
            float stackScale = naturalLayout ? LEFT_FAR_SCALE : LEFT_END_SCALE;
            desiredDelta = interpolate(stackOffset * primarySize, normalDelta, progress);
            scale = interpolate(stackScale, 1f, progress);
            alpha = 0f;
        }
        float translation = desiredDelta - normalDelta;
        out.set(scale, rtl ? -translation : translation, alpha, getMenuAlpha(distance));
    }

    float getStackDepth(float distance, boolean naturalLayout) {
        if (distance <= 0f) {
            return 1f;
        }
        return Math.max(0f, 1f - distance / getLeftStackDistance(naturalLayout));
    }

    private float getStackScale(float distance, boolean naturalLayout) {
        if (distance <= 1f) {
            return interpolate(1f, LEFT_NEAR_SCALE, decelerate(distance, DECELERATE_65));
        } else if (distance <= 2f) {
            return interpolate(LEFT_NEAR_SCALE, LEFT_MID_SCALE,
                    decelerate(distance - 1f, DECELERATE_65));
        } else if (distance <= 3f) {
            return interpolate(LEFT_MID_SCALE, LEFT_FAR_SCALE,
                    decelerate(distance - 2f, DECELERATE_65));
        } else if (!naturalLayout && distance <= 4f) {
            return interpolate(LEFT_FAR_SCALE, LEFT_END_SCALE,
                    decelerate(distance - 3f, DECELERATE_65));
        }
        return naturalLayout ? LEFT_FAR_SCALE : LEFT_END_SCALE;
    }

    private float getLeftStackDistance(boolean naturalLayout) {
        return naturalLayout ? PORTRAIT_LEFT_STACK_DISTANCE : LANDSCAPE_LEFT_STACK_DISTANCE;
    }

    private float getLeftNearOffset(boolean naturalLayout) {
        return naturalLayout ? PORTRAIT_LEFT_NEAR_OFFSET : LANDSCAPE_LEFT_NEAR_OFFSET;
    }

    private float getLeftMidOffset(boolean naturalLayout) {
        return naturalLayout ? PORTRAIT_LEFT_MID_OFFSET : LANDSCAPE_LEFT_MID_OFFSET;
    }

    private float getLeftFarOffset(boolean naturalLayout) {
        return naturalLayout ? PORTRAIT_LEFT_FAR_OFFSET : LANDSCAPE_LEFT_FAR_OFFSET;
    }

    private static float interpolate(float start, float end, float progress) {
        return start + Utilities.boundToRange(progress, 0f, 1f) * (end - start);
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
