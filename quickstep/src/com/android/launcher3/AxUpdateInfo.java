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

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.RemoteAnimationTarget;

final class AxUpdateInfo {
    private final Matrix mMatrix = new Matrix();
    private final Rect mCrop = new Rect();
    private RemoteAnimationTarget mTarget;
    private float mAlpha = 1f;
    private float mCornerRadius = -1f;
    private float mShadowRadius = -1f;
    private boolean mHasMatrix;
    private boolean mHasCrop;

    void set(
            RemoteAnimationTarget target,
            Matrix matrix,
            Rect crop,
            float alpha,
            float cornerRadius,
            float shadowRadius) {
        mTarget = target;
        mAlpha = alpha;
        mCornerRadius = cornerRadius;
        mShadowRadius = shadowRadius;
        mHasMatrix = matrix != null;
        if (mHasMatrix) {
            mMatrix.set(matrix);
        } else {
            mMatrix.reset();
        }
        mHasCrop = crop != null;
        if (mHasCrop) {
            mCrop.set(crop);
        } else {
            mCrop.setEmpty();
        }
    }

    void clear() {
        mTarget = null;
        mAlpha = 1f;
        mCornerRadius = -1f;
        mShadowRadius = -1f;
        mHasMatrix = false;
        mHasCrop = false;
        mMatrix.reset();
        mCrop.setEmpty();
    }

    RemoteAnimationTarget getTarget() {
        return mTarget;
    }

    Matrix getMatrix() {
        return mMatrix;
    }

    Rect getCrop() {
        return mCrop;
    }

    float getAlpha() {
        return mAlpha;
    }

    float getCornerRadius() {
        return mCornerRadius;
    }

    float getShadowRadius() {
        return mShadowRadius;
    }

    boolean hasMatrix() {
        return mHasMatrix;
    }

    boolean hasCrop() {
        return mHasCrop;
    }
}
