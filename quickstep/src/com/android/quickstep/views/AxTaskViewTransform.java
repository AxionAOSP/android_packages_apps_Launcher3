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

final class AxTaskViewTransform {
    static final int SCALE_CHANGED = 1;
    static final int TRANSLATION_X_CHANGED = 1 << 1;
    static final int TRANSLATION_Y_CHANGED = 1 << 2;
    static final int DEPTH_CHANGED = 1 << 3;
    static final int ALPHA_CHANGED = 1 << 4;
    static final int ICON_ALPHA_CHANGED = 1 << 5;
    static final int SURFACE_CHANGED = SCALE_CHANGED | TRANSLATION_X_CHANGED
            | TRANSLATION_Y_CHANGED | ALPHA_CHANGED;

    private float mScale = 1f;
    private float mTranslationX;
    private float mTranslationY;
    private float mDepth;
    private float mAlpha = 1f;
    private float mIconAlpha = 1f;
    private boolean mPinnedDuringFullscreen;

    int set(float scale, float translationX, float translationY, float depth, float alpha,
            float iconAlpha) {
        float boundedAlpha = Utilities.boundToRange(alpha, 0f, 1f);
        float boundedIconAlpha = Utilities.boundToRange(iconAlpha, 0f, 1f);
        int changes = 0;
        if (mScale != scale) {
            changes |= SCALE_CHANGED;
        }
        if (mTranslationX != translationX) {
            changes |= TRANSLATION_X_CHANGED;
        }
        if (mTranslationY != translationY) {
            changes |= TRANSLATION_Y_CHANGED;
        }
        if (mDepth != depth) {
            changes |= DEPTH_CHANGED;
        }
        if (mAlpha != boundedAlpha) {
            changes |= ALPHA_CHANGED;
        }
        if (mIconAlpha != boundedIconAlpha) {
            changes |= ICON_ALPHA_CHANGED;
        }
        mScale = scale;
        mTranslationX = translationX;
        mTranslationY = translationY;
        mDepth = depth;
        mAlpha = boundedAlpha;
        mIconAlpha = boundedIconAlpha;
        return changes;
    }

    boolean setPinnedDuringFullscreen(boolean pinned) {
        if (mPinnedDuringFullscreen == pinned) {
            return false;
        }
        mPinnedDuringFullscreen = pinned;
        return true;
    }

    float getScale() {
        return mScale;
    }

    float getTranslationX() {
        return mTranslationX;
    }

    float getTranslationY() {
        return mTranslationY;
    }

    float getAlpha() {
        return mAlpha;
    }

    float getIconAlpha() {
        return mIconAlpha;
    }

    float getAppliedScale(float fullscreenProgress) {
        float progress = getProgress(fullscreenProgress);
        return 1f + (mScale - 1f) * progress;
    }

    float getAppliedTranslationX(float fullscreenProgress) {
        return mTranslationX * getProgress(fullscreenProgress);
    }

    float getAppliedTranslationY(float fullscreenProgress) {
        return mTranslationY * getProgress(fullscreenProgress);
    }

    float getAppliedDepth(float fullscreenProgress) {
        return mDepth * getProgress(fullscreenProgress);
    }

    float getAppliedAlpha(float fullscreenProgress) {
        float progress = getProgress(fullscreenProgress);
        return 1f + (mAlpha - 1f) * progress;
    }

    boolean isActive() {
        return mScale != 1f || mTranslationX != 0f || mTranslationY != 0f
                || mDepth != 0f || mAlpha != 1f || mIconAlpha != 1f;
    }

    private float getProgress(float fullscreenProgress) {
        if (mPinnedDuringFullscreen) {
            return 1f;
        }
        return Utilities.boundToRange(1f - fullscreenProgress, 0f, 1f);
    }
}
