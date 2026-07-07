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

import android.animation.Animator;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.quickstep.SwipeUpAnimationLogic.HomeAnimationFactory;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;

final class AxHomeSpringAnimationRunner extends AnimationSuccessListener
        implements RectFSpringAnim.OnUpdateListener, BuilderProxy {
    private static final String TRACE = "home";

    private final Rect mBaseCrop = new Rect();
    private final Rect mSurfaceCrop = new Rect();
    private final Matrix mMatrix = new Matrix();
    private final RectF mWindowRect = new RectF();
    private final RectF mSurfaceCropF = new RectF();
    private final Matrix mHomeToWindowPositionMap;
    private final TransformParams mTransformParams;
    private final HomeAnimationFactory mAnimationFactory;
    private final AnimatorPlaybackController mHomeAnimation;
    private final AxHomeGestureCalculator mCalculator;
    private final int mTaskId;
    private final int mLeashId;

    AxHomeSpringAnimationRunner(
            DeviceProfile deviceProfile,
            HomeAnimationFactory animationFactory,
            RectF cropRect,
            Matrix homeToWindowPositionMap,
            TransformParams transformParams,
            float startVisibleRadius,
            @Nullable RemoteAnimationTarget target,
            RectF targetRect) {
        mAnimationFactory = animationFactory;
        mHomeAnimation = animationFactory.createActivityAnimationToHome();
        mHomeToWindowPositionMap = homeToWindowPositionMap;
        mTransformParams = transformParams;
        mTaskId = target == null ? -1 : target.taskId;
        mLeashId = target == null || target.leash == null
                ? 0 : System.identityHashCode(target.leash);
        cropRect.roundOut(mBaseCrop);
        mSurfaceCrop.set(mBaseCrop);
        mCalculator =
                new AxHomeGestureCalculator(
                        deviceProfile,
                        cropRect,
                        homeToWindowPositionMap,
                        targetRect,
                        startVisibleRadius,
                        target == null ? null : target.contentInsets);
    }

    @Override
    public void onUpdate(RectF currentRect, float progress) {
        float alpha = AxAnimationEngine.getHomeGestureWindowAlpha(progress);
        mHomeAnimation.setPlayFraction(progress);
        mHomeToWindowPositionMap.mapRect(mWindowRect, currentRect);
        mCalculator.updateCropRect(mWindowRect, progress, mBaseCrop);
        float cornerRadius = mCalculator.getCornerRadius(mWindowRect, progress);
        mSurfaceCrop.set(mBaseCrop);
        mSurfaceCropF.set(mSurfaceCrop);
        mMatrix.setRectToRect(mSurfaceCropF, mWindowRect, Matrix.ScaleToFit.FILL);
        mTransformParams.setTargetAlpha(alpha).setCornerRadius(cornerRadius);
        if (AxAnimationEngine.isTracing()) {
            AxAnimationEngine.trace(TRACE, "frame runner=" + id(this)
                    + " task=" + mTaskId
                    + " leash=" + Integer.toHexString(mLeashId)
                    + " progress=" + progress
                    + " rect=" + currentRect
                    + " window=" + mWindowRect
                    + " crop=" + mSurfaceCrop
                    + " matrix=" + mMatrix
                    + " alpha=" + alpha
                    + " radius=" + cornerRadius);
        }
        mTransformParams.applySurfaceParams(mTransformParams.createSurfaceParams(this));
        mAnimationFactory.update(currentRect, progress, mMatrix.mapRadius(cornerRadius), 0);
    }

    @Override
    public void onBuildTargetParams(
            SurfaceProperties builder, RemoteAnimationTarget app, TransformParams params) {
        builder.setMatrix(mMatrix)
                .setWindowCrop(mSurfaceCrop)
                .setCornerRadius(params.getCornerRadius());
    }

    @Override
    public void onCancel() {
        AxAnimationEngine.trace(TRACE, "cancel runner=" + id(this) + " task=" + mTaskId);
        mAnimationFactory.onCancel();
    }

    @Override
    public void onAnimationStart(Animator animation) {
        AxAnimationEngine.trace(TRACE, "start runner=" + id(this) + " task=" + mTaskId);
        mHomeAnimation.dispatchOnStart();
    }

    @Override
    public void onAnimationSuccess(Animator animator) {
        AxAnimationEngine.trace(TRACE, "success runner=" + id(this) + " task=" + mTaskId);
        mHomeAnimation.getAnimationPlayer().end();
    }

    private static String id(Object value) {
        return Integer.toHexString(System.identityHashCode(value));
    }
}
