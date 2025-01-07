/*
 * Copyright (C) 2023 The Android Open Source Project
 *               2023-2024 The risingOS Android Project
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
package com.android.quickstep.inputconsumers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.NavHandle;
import com.android.quickstep.util.ImageActionUtils;
import com.android.quickstep.TopTaskTracker;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.List;

/**
 * Class for extending nav handle long press behavior
 */
public class NavHandleLongPressHandler implements ResourceBasedOverride {

    private final String TAG = "NavHandleLongPressHandler";
    private boolean DEBUG = false;

    private Context mContext;
    private ThumbnailData mThumbnailData;
    private TopTaskTracker mTopTaskTracker;
    private VibratorWrapper mVibratorWrapper;

    private static final Handler mHandler = new Handler(Looper.getMainLooper());

    /** Creates NavHandleLongPressHandler as specified by overrides */
    public NavHandleLongPressHandler(Context context, TopTaskTracker topTaskTracker) {
        mContext = context.getApplicationContext();
        mTopTaskTracker = topTaskTracker;
        mVibratorWrapper = VibratorWrapper.INSTANCE.get(mContext);
    }

    /**
     * Called when nav handle is long pressed to get the Runnable that should be executed by the
     * caller to invoke long press behavior. If null is returned that means long press couldn't be
     * handled.
     * <p>
     * A Runnable is returned here to ensure the InputConsumer can call
     * {@link android.view.InputMonitor#pilferPointers()} before invoking the long press behavior
     * since pilfering can break the long press behavior.
     *
     * @param navHandle to handle this long press
     */
    public @Nullable Runnable getLongPressRunnable(NavHandle navHandle) {
        final boolean isCTSEnabled = Utilities.isLongPressSearchEnabled(mContext);
        return () -> {
            mHandler.postDelayed(() -> {
                if (isCTSEnabled) {
                    handleContextualSearch();
                } else {
                    handleLensSearch();
                }
            }, isCTSEnabled ? ViewConfiguration.getLongPressTimeout() : 0);
        };
    }

    private void handleContextualSearch() {
        if (Utilities.startContextualSearch(mContext, 
                android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE)) {
            mVibratorWrapper.cancelVibrate();
            mVibratorWrapper.vibrateForSearchHint();
        }
    }

    private void handleLensSearch() {
        if (!Utilities.isVelvetInstalled(mContext)) return;
        updateThumbnail();
        if (mThumbnailData != null && mThumbnailData.getThumbnail() != null) {
            if (ImageActionUtils.startLensSearchActivity(mContext, mThumbnailData.getThumbnail(), null, TAG)) {
                mVibratorWrapper.cancelVibrate();
                mVibratorWrapper.vibrateForSearchHint();
            }
        }
    }

    /**
     * Called when nav handle gesture starts.
     *
     * @param navHandle to handle the animation for this touch
     */
    public void onTouchStarted(NavHandle navHandle) {
        updateThumbnail();
    }

    private void updateThumbnail() {
        if (Utilities.isLongPressSearchEnabled(mContext)) return;
        String runningPackage = mTopTaskTracker.getCachedTopTask(
                /* filterOnlyVisibleRecents */ true).getPackageName();
        int taskId = mTopTaskTracker.getCachedTopTask(
                /* filterOnlyVisibleRecents */ true).getTaskId();
        mThumbnailData = ActivityManagerWrapper.getInstance().takeTaskThumbnail(taskId);
    }

    /**
     * Called when nav handle gesture is finished by the user lifting their finger or the system
     * cancelling the touch for some other reason.
     *
     * @param navHandle to handle the animation for this touch
     * @param reason why the touch ended
     */
    public void onTouchFinished(NavHandle navHandle, String reason) {
        mThumbnailData = null;
    }
}
