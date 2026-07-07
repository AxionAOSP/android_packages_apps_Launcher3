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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.DynamicResource;
import com.android.quickstep.util.RectFSpringAnim.DefaultSpringConfig;
import com.android.quickstep.util.RectFSpringAnim.TaskbarHotseatSpringConfig;
import com.android.systemui.plugins.ResourceProvider;

import java.util.ArrayList;
import java.util.List;

public final class AxRectFSpringAnim extends RectFSpringAnim {
    private static final FloatPropertyCompat<AxRectFSpringAnim> RECT_CENTER_X =
            new FloatPropertyCompat<>("rectCenterX") {
                @Override
                public float getValue(AxRectFSpringAnim animation) {
                    return animation.mCurrentCenterX;
                }

                @Override
                public void setValue(AxRectFSpringAnim animation, float value) {
                    animation.mCurrentCenterX = value;
                    animation.dispatchOnUpdate();
                }
            };
    private static final FloatPropertyCompat<AxRectFSpringAnim> RECT_Y =
            new FloatPropertyCompat<>("rectY") {
                @Override
                public float getValue(AxRectFSpringAnim animation) {
                    return animation.mCurrentY;
                }

                @Override
                public void setValue(AxRectFSpringAnim animation, float value) {
                    animation.mCurrentY = value;
                    animation.dispatchOnUpdate();
                }
            };
    private static final FloatPropertyCompat<AxRectFSpringAnim> RECT_SCALE_PROGRESS =
            new FloatPropertyCompat<>("rectScaleProgress") {
                @Override
                public float getValue(AxRectFSpringAnim animation) {
                    return animation.mCurrentScaleProgress;
                }

                @Override
                public void setValue(AxRectFSpringAnim animation, float value) {
                    animation.mCurrentScaleProgress = value;
                    animation.dispatchOnUpdate();
                }
            };

    private final RectF mStartRect;
    private final RectF mTargetRect;
    private final RectF mCurrentRect = new RectF();
    private final List<OnUpdateListener> mOnUpdateListeners = new ArrayList<>();
    private final List<Animator.AnimatorListener> mAnimatorListeners = new ArrayList<>();
    private final float mTrackingTopRatio;
    private final float mMinVisibleChange;
    private final int mMaxVelocityPxPerS;

    private float mCurrentCenterX;
    private float mCurrentY;
    private float mCurrentScaleProgress;
    private AxFlingSpringAnim mRectXSpring;
    private AxFlingSpringAnim mRectYSpring;
    private SpringAnimation mRectScaleAnim;
    private boolean mAnimsStarted;
    private boolean mRectXAnimEnded;
    private boolean mRectYAnimEnded;
    private boolean mRectScaleAnimEnded;
    private float mTargetOffsetX;
    private float mTargetOffsetY;
    private float mTargetScale = 1f;
    private Runnable mTargetUpdateCallback;

    AxRectFSpringAnim(
            Context context,
            DeviceProfile deviceProfile,
            RectF startRect,
            RectF targetRect,
            boolean useTaskbarHotseatParams) {
        super(
                useTaskbarHotseatParams
                        ? new TaskbarHotseatSpringConfig(context, startRect, targetRect)
                        : new DefaultSpringConfig(context, deviceProfile, startRect, targetRect));
        mStartRect = new RectF(startRect);
        mTargetRect = new RectF(targetRect);
        mCurrentCenterX = startRect.centerX();
        int displayHeight = Math.max(deviceProfile.getDeviceProperties().getHeightPx(), 1);
        mTrackingTopRatio =
                Utilities.boundToRange(targetRect.bottom / displayHeight, 0f, 1f)
                        * AxAnimationEngine.HOME_GESTURE_ICON_TRACKING_POSITION;
        mCurrentY = trackedY(startRect);
        ResourceProvider resources = DynamicResource.provider(context);
        mMinVisibleChange = resources.getDimension(R.dimen.swipe_up_fling_min_visible_change);
        mMaxVelocityPxPerS = (int) resources.getDimension(R.dimen.swipe_up_max_velocity);
        setCanRelease(true);
    }

    @Override
    public RectF getTargetRect() {
        return mTargetRect;
    }

    @Override
    public void addOnUpdateListener(OnUpdateListener listener) {
        mOnUpdateListeners.add(listener);
    }

    @Override
    public void addAnimatorListener(Animator.AnimatorListener listener) {
        mAnimatorListeners.add(listener);
    }

    void updateTargetTracking(float offsetX, float offsetY, float targetScale) {
        float boundedScale = Utilities.boundToRange(targetScale, 0f, 1f);
        if (mTargetOffsetX == offsetX
                && mTargetOffsetY == offsetY
                && mTargetScale == boundedScale) {
            return;
        }
        mTargetOffsetX = offsetX;
        mTargetOffsetY = offsetY;
        mTargetScale = boundedScale;
    }

    void setTargetUpdateCallback(@Nullable Runnable callback) {
        mTargetUpdateCallback = callback;
    }

    @Override
    public void start(Context context, @Nullable DeviceProfile profile, PointF velocityPxPerMs) {
        OnAnimationEndListener xEndListener =
                (animation, canceled, value, velocity) -> {
                    mRectXAnimEnded = true;
                    maybeOnEnd();
                };
        OnAnimationEndListener yEndListener =
                (animation, canceled, value, velocity) -> {
                    mRectYAnimEnded = true;
                    maybeOnEnd();
                };
        float gestureXVelocity = velocityPxPerMs.x * 1000f;
        float gestureYVelocity = velocityPxPerMs.y * 1000f;
        float xVelocity = clampVelocity(gestureXVelocity, mMaxVelocityPxPerS);
        float yVelocity = clampVelocity(gestureYVelocity, mMaxVelocityPxPerS);
        float endX = mTargetRect.centerX();
        float endY = trackedY(mTargetRect);
        mRectXSpring =
                createPositionAnimation(
                        RECT_CENTER_X, mCurrentCenterX, endX, xVelocity, xEndListener);
        mRectYSpring = createPositionAnimation(RECT_Y, mCurrentY, endY, yVelocity, yEndListener);
        float scaleVisibleChange = Math.abs(1f / Math.max(1f, mStartRect.height()));
        mRectScaleAnim =
                new SpringAnimation(this, RECT_SCALE_PROGRESS)
                        .setSpring(
                                new SpringForce(1f)
                                        .setStiffness(
                                                AxAnimationEngine.HOME_GESTURE_ICON_SCALE_STIFFNESS)
                                        .setDampingRatio(
                                                AxAnimationEngine.HOME_GESTURE_ICON_SCALE_DAMPING))
                        .setStartVelocity(velocityPxPerMs.y * scaleVisibleChange)
                        .setMaxValue(1f)
                        .setMinimumVisibleChange(scaleVisibleChange)
                        .addEndListener(
                                (animation, canceled, value, velocity) -> {
                                    mRectScaleAnimEnded = true;
                                    maybeOnEnd();
                                });
        setCanRelease(false);
        mAnimsStarted = true;
        mRectXSpring.start();
        mRectYSpring.start();
        mRectScaleAnim.start();
        for (Animator.AnimatorListener listener : mAnimatorListeners) {
            listener.onAnimationStart(null);
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            end();
        }
    }

    @Override
    public void end() {
        if (mAnimsStarted) {
            mRectXSpring.end();
            mRectYSpring.end();
            if (mRectScaleAnim.canSkipToEnd()) {
                mRectScaleAnim.skipToEnd();
            }
            mCurrentCenterX = mTargetRect.centerX();
            mCurrentY = trackedY(mTargetRect);
            mCurrentScaleProgress = 1f;
            mRectXAnimEnded = false;
            mRectYAnimEnded = false;
            mRectScaleAnimEnded = false;
            dispatchOnUpdate();
        }
        mRectXAnimEnded = true;
        mRectYAnimEnded = true;
        mRectScaleAnimEnded = true;
        maybeOnEnd();
    }

    @Override
    public void cancel() {
        if (mAnimsStarted) {
            for (OnUpdateListener listener : mOnUpdateListeners) {
                listener.onCancel();
            }
        }
        end();
    }

    private AxFlingSpringAnim createPositionAnimation(
            FloatPropertyCompat<AxRectFSpringAnim> property,
            float start,
            float end,
            float velocity,
            OnAnimationEndListener endListener) {
        return new AxFlingSpringAnim(
                this,
                property,
                start,
                end,
                velocity,
                mMinVisibleChange,
                Math.min(start, end),
                Math.max(start, end),
                AxAnimationEngine.HOME_GESTURE_ICON_MOVE_DAMPING,
                AxAnimationEngine.HOME_GESTURE_ICON_MOVE_STIFFNESS,
                AxAnimationEngine.HOME_GESTURE_ICON_MOVE_FRICTION,
                endListener);
    }

    private float trackedY(RectF rect) {
        return rect.top + rect.height() * mTrackingTopRatio;
    }

    private static float clampVelocity(float velocity, int maxVelocity) {
        return Math.signum(velocity) * Math.min(Math.abs(velocity), maxVelocity);
    }

    private void dispatchOnUpdate() {
        if (mOnUpdateListeners.isEmpty()) {
            return;
        }
        if (mTargetUpdateCallback != null) {
            mTargetUpdateCallback.run();
        }
        float currentWidth =
                Utilities.mapRange(mCurrentScaleProgress, mStartRect.width(), mTargetRect.width());
        float heightProgress =
                AxAnimationEngine.getHomeGestureIconScaleProgress(mCurrentScaleProgress);
        float currentHeight =
                Utilities.mapRange(heightProgress, mStartRect.height(), mTargetRect.height());
        float trackedScale = 1f - (1f - mTargetScale) * mCurrentScaleProgress;
        mCurrentRect.set(
                mCurrentCenterX - currentWidth / 2f + mTargetOffsetX * mCurrentScaleProgress,
                mCurrentY
                        - currentHeight * mTrackingTopRatio
                        + mTargetOffsetY * mCurrentScaleProgress,
                mCurrentCenterX + currentWidth / 2f + mTargetOffsetX * mCurrentScaleProgress,
                mCurrentY
                        + currentHeight * (1f - mTrackingTopRatio)
                        + mTargetOffsetY * mCurrentScaleProgress);
        mCurrentRect.right = mCurrentRect.left + mCurrentRect.width() * trackedScale;
        mCurrentRect.bottom = mCurrentRect.top + mCurrentRect.height() * trackedScale;
        for (OnUpdateListener listener : mOnUpdateListeners) {
            listener.onUpdate(mCurrentRect, mCurrentScaleProgress);
        }
    }

    private void maybeOnEnd() {
        if (!mAnimsStarted || !mRectXAnimEnded || !mRectYAnimEnded || !mRectScaleAnimEnded) {
            return;
        }
        dispatchOnUpdate();
        mAnimsStarted = false;
        setCanRelease(true);
        mTargetUpdateCallback = null;
        for (Animator.AnimatorListener listener : mAnimatorListeners) {
            listener.onAnimationEnd(null);
        }
    }
}
