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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.util.Pair;
import android.view.View;

import com.android.app.animation.Animations;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.util.AxAnimationEngine;

import java.util.ArrayList;
import java.util.List;

final class AxContentsAnimator {
    private Token mToken;
    private boolean mUpdatesPaused;

    static boolean shouldHandleAppOpen(QuickstepLauncher launcher, boolean isAppOpening) {
        return isAppOpening && !launcher.isInState(ALL_APPS) && !launcher.isInState(OVERVIEW);
    }

    Pair<AnimatorSet, Runnable> getAppOpenAnimator(
            QuickstepLauncher launcher, DeviceProfile deviceProfile, int startDelay) {
        List<View> views = getHomeContentViews(launcher, deviceProfile);
        List<Float> startScales = new ArrayList<>(views.size());
        for (View view : views) {
            startScales.add(view.getScaleX());
        }
        views.forEach(Animations.Companion::cancelOngoingAnimation);

        Token token = new Token();
        mToken = token;
        AnimatorSet animator = new AnimatorSet();
        if (!mUpdatesPaused) {
            mUpdatesPaused = true;
            launcher.pauseExpensiveViewUpdates();
        }
        for (int i = 0; i < views.size(); i++) {
            playAppOpen(animator, views.get(i), startScales.get(i));
        }
        animator.setStartDelay(startDelay);
        Runnable endListener =
                () -> {
                    if (mToken != token) {
                        return;
                    }
                    mToken = null;
                    views.forEach(
                            view -> {
                                SCALE_PROPERTY.set(view, 1f);
                                view.setLayerType(View.LAYER_TYPE_NONE, null);
                                Animations.Companion.setOngoingAnimation(view, null);
                            });
                    if (mUpdatesPaused) {
                        mUpdatesPaused = false;
                        launcher.resumeExpensiveViewUpdates();
                    }
                };
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        endListener.run();
                    }
                });
        return Pair.create(animator, endListener);
    }

    private static List<View> getHomeContentViews(
            QuickstepLauncher launcher, DeviceProfile deviceProfile) {
        List<View> views = new ArrayList<>();
        views.add(launcher.getWorkspace());

        Hotseat hotseat = launcher.getHotseat();
        if (deviceProfile.isTaskbarPresent) {
            if (!deviceProfile.isQsbInline) {
                views.add(hotseat.getQsb());
            }
        } else {
            views.add(hotseat);
        }
        return views;
    }

    private static void playAppOpen(AnimatorSet animator, View view, float startScale) {
        SCALE_PROPERTY.set(view, startScale);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Animations.Companion.setOngoingAnimation(view, animator);

        ObjectAnimator scale =
                ObjectAnimator.ofFloat(
                        view, SCALE_PROPERTY, startScale, AxAnimationEngine.APP_OPEN_HOME_SCALE);
        scale.setDuration(AxAnimationEngine.APP_OPEN_HOME_DURATION);
        scale.setInterpolator(AxAnimationEngine.APP_OPEN_HOME_INTERPOLATOR);
        animator.play(scale);
    }

    private record Token() {}
}
