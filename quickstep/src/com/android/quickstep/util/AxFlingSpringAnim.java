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

import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

final class AxFlingSpringAnim {
    private final FlingAnimation mFlingAnim;
    private final boolean mSkipFlingAnim;
    private final float mTargetPosition;
    private SpringAnimation mSpringAnim;

    <T> AxFlingSpringAnim(
            T object,
            FloatPropertyCompat<T> property,
            float startPosition,
            float targetPosition,
            float startVelocityPxPerS,
            float minVisibleChange,
            float minValue,
            float maxValue,
            float damping,
            float stiffness,
            float friction,
            OnAnimationEndListener onEndListener) {
        mFlingAnim =
                new FlingAnimation(object, property)
                        .setFriction(friction)
                        .setMinimumVisibleChange(minVisibleChange)
                        .setStartVelocity(startVelocityPxPerS)
                        .setMinValue(minValue)
                        .setMaxValue(maxValue);
        mTargetPosition = targetPosition;
        mSkipFlingAnim =
                startPosition <= minValue && startVelocityPxPerS < 0f
                        || startPosition >= maxValue && startVelocityPxPerS > 0f;
        mFlingAnim.addEndListener(
                (animation, canceled, value, velocity) -> {
                    mSpringAnim =
                            new SpringAnimation(object, property)
                                    .setStartValue(value)
                                    .setStartVelocity(velocity)
                                    .setSpring(
                                            new SpringForce(mTargetPosition)
                                                    .setStiffness(stiffness)
                                                    .setDampingRatio(damping));
                    mSpringAnim.addEndListener(onEndListener);
                    mSpringAnim.animateToFinalPosition(mTargetPosition);
                });
    }

    void start() {
        mFlingAnim.start();
        if (mSkipFlingAnim) {
            mFlingAnim.cancel();
        }
    }

    void end() {
        mFlingAnim.cancel();
        if (mSpringAnim != null && mSpringAnim.canSkipToEnd()) {
            mSpringAnim.skipToEnd();
        }
    }
}
