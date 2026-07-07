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

import android.graphics.Rect;
import android.graphics.RectF;

import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.AxScaleProgressUtil;

final class AxBaseCalculator {
    private static final float EPSILON = 0.0001f;

    private final int mScreenWidth;
    private final int mScreenHeight;
    private final boolean mIsPortrait;
    private final RectF mStartBounds = new RectF();
    private final Rect mEndBounds = new Rect();
    private final RectF mInsets = new RectF();
    private final AxScaleProgressUtil mScaleProgress;
    private final float mStartRadius;
    private final float mEndRadius;
    private final AxFloatingFrame mFrame = new AxFloatingFrame();

    AxBaseCalculator(
            int screenWidth,
            int screenHeight,
            RectF startBounds,
            float startRadius,
            float endRadius) {
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mIsPortrait = screenHeight > screenWidth;
        mStartBounds.set(startBounds);
        mScaleProgress = AxScaleProgressUtil.forAppOpen(startBounds, screenWidth, screenHeight);
        mStartRadius = startRadius;
        mEndRadius = endRadius;
    }

    AxFloatingFrame calculate(Rect endBounds, Rect contentInsets, float progress) {
        mEndBounds.set(endBounds);
        calculateCurrentRect(progress);
        calculateInsets(contentInsets, 1f - progress);
        calculateCropRect();
        calculateScale();
        calculateRadius();
        return mFrame;
    }

    private void calculateCurrentRect(float progress) {
        mFrame.currentRect.set(
                valueAt(mStartBounds.left, mEndBounds.left, progress),
                valueAt(mStartBounds.top, mEndBounds.top, progress),
                valueAt(mStartBounds.right, mEndBounds.right, progress),
                valueAt(mStartBounds.bottom, mEndBounds.bottom, progress));
    }

    private void calculateInsets(Rect contentInsets, float progress) {
        if (contentInsets == null) {
            mInsets.setEmpty();
        } else {
            float insetProgress = boundToUnit(progress);
            mInsets.set(
                    contentInsets.left * insetProgress,
                    contentInsets.top * insetProgress,
                    contentInsets.right * insetProgress,
                    contentInsets.bottom * insetProgress);
        }
    }

    private void calculateCropRect() {
        float baseWidth = Math.max(1f, mScreenWidth - mInsets.left - mInsets.right);
        float baseHeight = Math.max(1f, mScreenHeight - mInsets.top - mInsets.bottom);
        float currentWidth = mFrame.currentRect.width();
        float currentHeight = mFrame.currentRect.height();
        float cropWidth = baseWidth;
        float cropHeight = baseHeight;

        if (currentWidth > EPSILON && currentHeight > EPSILON) {
            if (mIsPortrait) {
                cropHeight = currentHeight * baseWidth / currentWidth;
                if (cropHeight > baseHeight) {
                    cropWidth = baseHeight * baseWidth / cropHeight;
                    cropHeight = baseHeight;
                }
            } else {
                cropWidth = currentWidth * baseHeight / currentHeight;
                if (cropWidth > baseWidth) {
                    cropHeight = baseWidth * baseHeight / cropWidth;
                    cropWidth = baseWidth;
                }
            }
        }

        mFrame.crop.set(
                Math.round(mInsets.left),
                Math.round(mInsets.top),
                Math.round(mInsets.left + cropWidth),
                Math.round(mInsets.top + cropHeight));
    }

    private void calculateScale() {
        mFrame.scale = mFrame.currentRect.width() / Math.max(1, mFrame.crop.width());
    }

    private void calculateRadius() {
        float cornerProgress =
                AxAnimationEngine.APP_OPEN_CORNER_RADIUS_INTERPOLATOR.getInterpolation(
                        mScaleProgress.getProgress(mFrame.scale));
        mFrame.floatingRadius =
                AxAnimationEngine.getAppOpenCornerRadius(
                        mStartRadius, mEndRadius, cornerProgress);
        mFrame.radius = mFrame.floatingRadius / Math.max(EPSILON, mFrame.scale);
    }

    private static float valueAt(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float boundToUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
