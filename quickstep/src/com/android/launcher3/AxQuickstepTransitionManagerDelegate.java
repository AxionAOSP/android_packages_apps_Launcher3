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

import static com.android.launcher3.QuickstepTransitionManager.APP_LAUNCH_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_PRE_DELAY;

import android.animation.AnimatorSet;
import android.util.Pair;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.util.AxAnimationEngine;
import com.android.quickstep.views.TaskView;

final class AxQuickstepTransitionManagerDelegate {
    private final QuickstepLauncher mLauncher;
    private final AxContentsAnimator mContentsAnimator = new AxContentsAnimator();

    AxQuickstepTransitionManagerDelegate(QuickstepLauncher launcher) {
        mLauncher = launcher;
        AxHomePackageObserver.INSTANCE.get(launcher);
    }

    boolean useAppOpenAnimation(View sourceView) {
        ItemInfo itemInfo = sourceView.getTag() instanceof ItemInfo info ? info : null;
        return AxQuickstepTransitionManagerExt.isAxAnimEngineEnabled(mLauncher)
                && (itemInfo == null || !itemInfo.shouldUseBackgroundAnimation())
                && !(sourceView instanceof LauncherAppWidgetHostView)
                && !(sourceView instanceof TaskView);
    }

    long getDuration(boolean useAxAnimation, boolean fromRecents) {
        if (fromRecents) {
            return RECENTS_LAUNCH_DURATION;
        }
        return useAxAnimation ? AxAnimationEngine.APP_LAUNCH_DURATION : APP_LAUNCH_DURATION;
    }

    long getStatusBarTransitionDelay(boolean useAxAnimation, boolean fromRecents, long duration) {
        return useAxAnimation && !fromRecents
                ? AxAnimationEngine.APP_OPEN_STATUS_BAR_TRANSITION_DELAY
                : duration - STATUS_BAR_TRANSITION_DURATION - STATUS_BAR_TRANSITION_PRE_DELAY;
    }

    @Nullable
    Pair<AnimatorSet, Runnable> getAppOpenContentAnimator(
            boolean useAxAnimation,
            boolean isAppOpening,
            DeviceProfile deviceProfile,
            int startDelay) {
        if (!useAxAnimation || !AxContentsAnimator.shouldHandleAppOpen(mLauncher, isAppOpening)) {
            return null;
        }
        return mContentsAnimator.getAppOpenAnimator(mLauncher, deviceProfile, startDelay);
    }
}
