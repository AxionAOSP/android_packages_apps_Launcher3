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

import android.graphics.RectF;

public final class AxScaleProgressUtil {
    private static final float EPSILON = 0.0001f;

    private final float mStartScale;
    private final float mMaxScaleDiff;

    private AxScaleProgressUtil(float startScale, float endScale) {
        mStartScale = startScale;
        mMaxScaleDiff = Math.abs(endScale - startScale);
    }

    public static AxScaleProgressUtil forAppOpen(
            RectF startBounds, int screenWidth, int screenHeight) {
        return new AxScaleProgressUtil(getWindowScale(startBounds, screenWidth, screenHeight), 1f);
    }

    public static AxScaleProgressUtil forHomeGesture(float endScale) {
        return new AxScaleProgressUtil(1f, endScale);
    }

    public float getProgress(float currentScale) {
        if (mMaxScaleDiff < EPSILON) {
            return 1f;
        }
        return boundToUnit(Math.abs(currentScale - mStartScale) / mMaxScaleDiff);
    }

    private static float getWindowScale(RectF bounds, int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return 1f;
        }
        if (screenHeight > screenWidth) {
            return Math.min(1f, bounds.width() / screenWidth);
        }
        return Math.min(1f, bounds.height() / screenHeight);
    }

    private static float boundToUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
