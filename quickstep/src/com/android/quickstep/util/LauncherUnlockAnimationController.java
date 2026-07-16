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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.graphics.Rect;
import android.util.Log;

import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController;

public final class LauncherUnlockAnimationController
        extends ILauncherUnlockAnimationController.Stub {

    private static final String TAG = "LauncherUnlock";
    private static final long DEFAULT_TOTAL_DURATION_MS = 500L;

    private final QuickstepLauncher mLauncher;

    private long mStartDelay;
    private long mTotalDuration = DEFAULT_TOTAL_DURATION_MS;
    private boolean mDestroyed;

    public LauncherUnlockAnimationController(QuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void prepareForUnlock(boolean animateSmartspace, Rect lockscreenSmartspaceBounds,
            int selectedPage) {
        MAIN_EXECUTOR.execute(() -> {
            if (!mDestroyed) {
                prepareAnimation(false);
            }
        });
    }

    @Override
    public void playUnlockAnimation(boolean unlocked, long duration, long startDelay) {
        MAIN_EXECUTOR.execute(() -> {
            if (mDestroyed) {
                return;
            }
            long totalDuration = duration + startDelay;
            if (totalDuration != mTotalDuration || startDelay != mStartDelay) {
                mStartDelay = startDelay;
                mTotalDuration = totalDuration;
                if (getPlaybackController() != null) {
                    prepareAnimation(false);
                }
            }
            AnimatorPlaybackController controller = getPlaybackController();
            if (controller != null) {
                controller.start();
            }
        });
    }

    @Override
    public void setUnlockAmount(float amount, boolean forceIfAnimating) {
        MAIN_EXECUTOR.execute(() -> {
            if (mDestroyed) {
                return;
            }
            if (amount != 1f) {
                Log.e(TAG, "setUnlockAmount called with unsupported value " + amount);
                return;
            }
            AnimatorPlaybackController controller = getPlaybackController();
            if (controller == null) {
                return;
            }
            if (!controller.getAnimationPlayer().isRunning() || forceIfAnimating) {
                controller.pause();
                controller.setPlayFraction(1f);
                controller.dispatchOnEnd();
            }
        });
    }

    @Override
    public void setSmartspaceSelectedPage(int selectedPage) { }

    @Override
    public void setSmartspaceVisibility(int visibility) { }

    @Override
    public void dispatchSmartspaceStateToSysui() { }

    public void destroy() {
        MAIN_EXECUTOR.execute(() -> {
            mDestroyed = true;
            AnimatorPlaybackController controller = getPlaybackController();
            if (controller != null) {
                controller.dispatchOnCancel();
            }
        });
    }

    private void prepareAnimation(boolean force) {
        if (mLauncher.isInState(LauncherState.NORMAL) && (!mLauncher.isStarted() || force)) {
            mLauncher.getAnimationCoordinator().setAnimation(
                    this, this::addUnlockAnimators, mTotalDuration);
        }
    }

    private void addUnlockAnimators(PendingAnimation animation) {
        float startFraction = mTotalDuration == 0 ? 0f : (float) mStartDelay / mTotalDuration;
        RingAppearAnimation.addAnimators(animation, mLauncher, startFraction);
    }

    private AnimatorPlaybackController getPlaybackController() {
        return mLauncher.getAnimationCoordinator().getPlaybackController(this);
    }
}
