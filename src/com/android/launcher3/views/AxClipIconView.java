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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.DeviceProfile;

final class AxClipIconView extends ClipIconView {
    private boolean mAppOpenUpdate;
    private boolean mHomeGestureUpdate;

    AxClipIconView(Context context) {
        this(context, null);
    }

    AxClipIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    AxClipIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void updateAppOpen(
            RectF rect,
            float progress,
            float cornerRadius,
            View container,
            DeviceProfile dp,
            float foregroundScale,
            float foregroundAlpha) {
        mAppOpenUpdate = true;
        try {
            update(
                    rect,
                    progress,
                    0f,
                    cornerRadius,
                    true,
                    container,
                    dp,
                    0,
                    foregroundScale,
                    foregroundAlpha);
            setForegroundAlpha(foregroundAlpha);
        } finally {
            mAppOpenUpdate = false;
        }
    }

    void updateHomeGesture(
            RectF rect,
            float progress,
            float shapeProgressStart,
            float cornerRadius,
            View container,
            DeviceProfile dp,
            float foregroundScale,
            float foregroundAlpha) {
        mHomeGestureUpdate = true;
        try {
            update(
                    rect,
                    progress,
                    shapeProgressStart,
                    cornerRadius,
                    false,
                    container,
                    dp,
                    0,
                    foregroundScale,
                    foregroundAlpha);
        } finally {
            mHomeGestureUpdate = false;
        }
    }

    @Override
    protected boolean shouldSkipShapeReveal() {
        return mAppOpenUpdate;
    }

    @Override
    protected float getForegroundScale(float foregroundScale, float drawableScale) {
        if (!mAppOpenUpdate && !mHomeGestureUpdate) {
            return super.getForegroundScale(foregroundScale, drawableScale);
        }
        return Math.max(foregroundScale, foregroundScale * drawableScale);
    }

    @Override
    protected void updateForegroundBounds(
            Rect outBounds,
            Rect finalDrawableBounds,
            float drawableScale,
            float foregroundScale,
            boolean isLandscape,
            boolean isRtl) {
        if (!mAppOpenUpdate && !mHomeGestureUpdate) {
            super.updateForegroundBounds(
                    outBounds,
                    finalDrawableBounds,
                    drawableScale,
                    foregroundScale,
                    isLandscape,
                    isRtl);
            return;
        }
        float scaledForeground = getForegroundScale(foregroundScale, drawableScale);
        float baseSize = isLandscape ? finalDrawableBounds.height() : finalDrawableBounds.width();
        float xOffset;
        float yOffset;
        if (isLandscape) {
            float scaleOffset = (scaledForeground - 1f) * baseSize;
            float axisOffset = (foregroundScale - 1f) * baseSize / 2f;
            xOffset = isRtl ? axisOffset - scaleOffset : -axisOffset;
            yOffset = -scaleOffset / 2f;
        } else {
            xOffset = -((scaledForeground - 1f) * baseSize) / 2f;
            yOffset = -((foregroundScale - 1f) * baseSize) / 2f;
        }
        int left = Math.round(finalDrawableBounds.left + xOffset);
        int top = Math.round(finalDrawableBounds.top + yOffset);
        outBounds.set(
                left,
                top,
                left + Math.round(finalDrawableBounds.width() * scaledForeground),
                top + Math.round(finalDrawableBounds.height() * scaledForeground));
    }
}
