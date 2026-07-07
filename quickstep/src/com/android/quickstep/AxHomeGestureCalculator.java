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

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.DeviceProfile;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.AxScaleProgressUtil;

final class AxHomeGestureCalculator {
    private static final float EPSILON = 0.0001f;
    private static final String TAG = "AxHomeGestureCalculator";

    private final RectF mCropRectF = new RectF();
    private final RectF mStartInsets = new RectF();
    private final RectF mEndInsets = new RectF();
    private final RectF mTmpInsets = new RectF();
    private final RectF mTmpCropRectF = new RectF();
    private final int mScreenWidth;
    private final int mScreenHeight;
    private final boolean mIsPortrait;
    private final float mStartVisibleRadius;
    private final float mEndRadius;
    private final AxScaleProgressUtil mScaleProgress;
    private float mStartRadius = Float.NaN;
    private float mRadiusDiff = Float.NaN;
    private float mCurrentCropWidth;
    private float mCurrentCropHeight;
    private boolean mHasStartInsets;

    AxHomeGestureCalculator(
            DeviceProfile dp,
            RectF cropRectF,
            Matrix homeToWindowPositionMap,
            RectF targetRect,
            float startVisibleRadius,
            Rect targetInsets) {
        mCropRectF.set(cropRectF);
        mScreenWidth = dp.getDeviceProperties().getWidthPx();
        mScreenHeight = dp.getDeviceProperties().getHeightPx();
        mIsPortrait = mScreenHeight > mScreenWidth;
        if (targetInsets != null) {
            mEndInsets.set(targetInsets);
        }
        mStartVisibleRadius = startVisibleRadius;
        mEndRadius =
                AxAnimationEngine.getAppOpenFullCornerRadius(Math.min(mScreenWidth, mScreenHeight));
        RectF targetRectForScale = new RectF(targetRect);
        homeToWindowPositionMap.mapRect(targetRectForScale);
        mScaleProgress = AxScaleProgressUtil.forHomeGesture(getWindowScale(targetRectForScale));
    }

    void updateCropRect(RectF currentRect, float progress, Rect outCrop) {
        if (!mHasStartInsets) {
            calculateStartContentsInsets(currentRect);
            mHasStartInsets = true;
        }
        float boundedProgress = boundToUnit(progress);
        mTmpInsets.set(
                valueAt(mStartInsets.left, mEndInsets.left, boundedProgress),
                valueAt(mStartInsets.top, mEndInsets.top, boundedProgress),
                valueAt(mStartInsets.right, mEndInsets.right, boundedProgress),
                valueAt(mStartInsets.bottom, mEndInsets.bottom, boundedProgress));
        calculateCropRect(currentRect, mTmpInsets, outCrop);
        mCurrentCropWidth = outCrop.width();
        mCurrentCropHeight = outCrop.height();
    }

    float getCornerRadius(RectF currentRect, float progress) {
        float currentScale = getWindowScale(currentRect);
        if (Float.isNaN(mStartRadius)) {
            mStartRadius =
                    currentScale > EPSILON
                            ? mStartVisibleRadius / currentScale
                            : mStartVisibleRadius;
            AxAnimationEngine.trace(
                    TAG,
                    "homeRadius visible="
                            + mStartVisibleRadius
                            + " scale="
                            + currentScale
                            + " local="
                            + mStartRadius);
        }
        float radiusProgress = mScaleProgress.getProgress(currentScale);
        float radius =
                AxAnimationEngine.getAppOpenCornerRadius(mStartRadius, mEndRadius, radiusProgress);
        if (Float.isNaN(mRadiusDiff)) {
            mRadiusDiff = Math.abs(mStartRadius - radius);
        }
        return Math.max(0f, radius - ((1f - boundToUnit(progress)) * mRadiusDiff));
    }

    private void calculateStartContentsInsets(RectF animatedRect) {
        mTmpCropRectF.set(mCropRectF);
        float cropWidth = mCropRectF.width();
        float cropHeight = mCropRectF.height();
        if (animatedRect.width() <= EPSILON
                || animatedRect.height() <= EPSILON
                || cropWidth <= EPSILON
                || cropHeight <= EPSILON) {
            mStartInsets.set(mEndInsets);
            return;
        }
        if (mIsPortrait) {
            float height = animatedRect.height() * cropWidth / animatedRect.width();
            mTmpCropRectF.right = mTmpCropRectF.left + cropWidth;
            mTmpCropRectF.bottom = mTmpCropRectF.top + height;
            if (mTmpCropRectF.height() > mScreenHeight) {
                float width = animatedRect.width() * cropHeight / animatedRect.height();
                mTmpCropRectF.right = mTmpCropRectF.left + width;
                mTmpCropRectF.bottom = mTmpCropRectF.top + cropHeight;
            }
        } else {
            float width = animatedRect.width() * cropHeight / animatedRect.height();
            mTmpCropRectF.right = mTmpCropRectF.left + width;
            mTmpCropRectF.bottom = mTmpCropRectF.top + cropHeight;
            if (mTmpCropRectF.width() > mScreenWidth) {
                float height = animatedRect.height() * cropWidth / animatedRect.width();
                mTmpCropRectF.right = mTmpCropRectF.left + cropWidth;
                mTmpCropRectF.bottom = mTmpCropRectF.top + height;
            }
        }
        setContentsInset(mStartInsets, mTmpCropRectF);
    }

    private void calculateCropRect(RectF currentRect, RectF insets, Rect outCrop) {
        float baseWidth = Math.max(1f, mScreenWidth - insets.left - insets.right);
        float baseHeight = Math.max(1f, mScreenHeight - insets.top - insets.bottom);
        float cropWidth = baseWidth;
        float cropHeight = baseHeight;
        if (currentRect.width() > EPSILON && currentRect.height() > EPSILON) {
            if (mIsPortrait) {
                cropHeight = currentRect.height() * baseWidth / currentRect.width();
                if (cropHeight > baseHeight) {
                    cropWidth = baseHeight * baseWidth / cropHeight;
                    cropHeight = baseHeight;
                }
            } else {
                cropWidth = currentRect.width() * baseHeight / currentRect.height();
                if (cropWidth > baseWidth) {
                    cropHeight = baseWidth * baseHeight / cropWidth;
                    cropWidth = baseWidth;
                }
            }
        }
        outCrop.set(
                Math.round(insets.left),
                Math.round(insets.top),
                Math.round(insets.left + cropWidth),
                Math.round(insets.top + cropHeight));
    }

    private float getWindowScale(RectF rect) {
        float cropWidth = mCurrentCropWidth > 0f ? mCurrentCropWidth : mCropRectF.width();
        float cropHeight = mCurrentCropHeight > 0f ? mCurrentCropHeight : mCropRectF.height();
        if (cropWidth <= 0 || cropHeight <= 0) {
            return 1f;
        }
        if (cropHeight > cropWidth) {
            return Math.min(1f, rect.width() / cropWidth);
        }
        return Math.min(1f, rect.height() / cropHeight);
    }

    private void setContentsInset(RectF outInsets, RectF rect) {
        outInsets.set(rect.left, rect.top, mScreenWidth - rect.right, mScreenHeight - rect.bottom);
    }

    private static float valueAt(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float boundToUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
