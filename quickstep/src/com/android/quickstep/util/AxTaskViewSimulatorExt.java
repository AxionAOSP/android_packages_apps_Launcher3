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

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.android.launcher3.Utilities;

final class AxTaskViewSimulatorExt {
    private static final float EPSILON = 0.0001f;
    private static final String TRACE = "sim";

    private final float mDensity;
    private final boolean mDesktop;
    private final int mTraceId = System.identityHashCode(this);
    private boolean mEnabled;
    private boolean mSplit;
    private float mStackScale = 1f;
    private float mStackTranslationX;
    private float mStackTranslationY;
    private float mStackAlpha = 1f;
    private float mLaunchAlpha = 1f;
    private boolean mStackPinned;

    AxTaskViewSimulatorExt(Context context, boolean desktop) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mDesktop = desktop;
    }

    void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    void setSplit(boolean split) {
        mSplit = split;
    }

    void setStackTransform(float scale, float translationX, float translationY, float alpha) {
        mStackScale = scale;
        mStackTranslationX = translationX;
        mStackTranslationY = translationY;
        mStackAlpha = alpha;
        if (AxAnimationEngine.isTracing()) {
            trace("stack scale=" + scale + " x=" + translationX + " y=" + translationY
                    + " alpha=" + alpha);
        }
    }

    void setStackTransformPinned(boolean pinned) {
        mStackPinned = pinned;
        if (AxAnimationEngine.isTracing()) {
            trace("pinned=" + pinned);
        }
    }

    void setLaunchAlpha(float alpha) {
        mLaunchAlpha = Utilities.boundToRange(alpha, 0f, 1f);
        if (AxAnimationEngine.isTracing()) {
            trace("launchAlpha=" + mLaunchAlpha);
        }
    }

    void traceApply(
            Matrix matrix,
            Rect crop,
            float progress,
            float alpha,
            float radius,
            boolean batched) {
        if (!AxAnimationEngine.isTracing()) {
            return;
        }
        trace("frame progress=" + progress
                + " crop=" + crop
                + " matrix=" + matrix
                + " alpha=" + alpha
                + " radius=" + radius
                + " batched=" + batched
                + " stack=" + mStackScale + "," + mStackTranslationX + ","
                + mStackTranslationY + "," + mStackAlpha
                + " pinned=" + mStackPinned);
    }

    void applyStackScale(Matrix matrix, Rect fullTaskRect, float fullscreenProgress) {
        float scale = getStackScale(fullscreenProgress);
        if (Math.abs(scale - 1f) <= EPSILON) {
            return;
        }
        matrix.postScale(scale, scale,
                fullTaskRect.exactCenterX(), fullTaskRect.exactCenterY());
    }

    void applyStackTranslation(Matrix matrix, float fullscreenProgress) {
        if (Math.abs(mStackTranslationX) <= EPSILON
                && Math.abs(mStackTranslationY) <= EPSILON) {
            return;
        }
        float progress = getStackProgress(fullscreenProgress);
        if (progress <= EPSILON) {
            return;
        }
        matrix.postTranslate(mStackTranslationX * progress, mStackTranslationY * progress);
    }

    float getRadius(
            float fallback,
            float fullscreenProgress,
            float recentsScale,
            float carouselScale) {
        if (!mEnabled && isStackTransformIdentity()) {
            return fallback;
        }
        float stackScale = Math.abs(getStackScale(fullscreenProgress));
        return stackScale <= EPSILON ? fallback : fallback / stackScale;
    }

    float getAlpha(float fullscreenProgress) {
        float progress = getStackProgress(fullscreenProgress);
        return (1f + (mStackAlpha - 1f) * progress) * mLaunchAlpha;
    }

    private float getStackScale(float fullscreenProgress) {
        return 1f + (mStackScale - 1f) * getStackProgress(fullscreenProgress);
    }

    private float getStackProgress(float fullscreenProgress) {
        if (mDesktop) {
            return 0f;
        }
        if (mStackPinned) {
            return 1f;
        }
        return 1f - Utilities.boundToRange(fullscreenProgress, 0f, 1f);
    }

    private boolean isStackTransformIdentity() {
        return Math.abs(mStackScale - 1f) <= EPSILON
                && Math.abs(mStackTranslationX) <= EPSILON
                && Math.abs(mStackTranslationY) <= EPSILON;
    }

    private void trace(String message) {
        AxAnimationEngine.trace(TRACE, "sim=" + Integer.toHexString(mTraceId) + " " + message);
    }
}
