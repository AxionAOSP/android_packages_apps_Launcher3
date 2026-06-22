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

import static com.android.launcher3.LauncherPrefsExt.SLEEP_GESTURE;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_DOUBLE_TAP_ACTION;

import android.content.Context;
import android.view.MotionEvent;

import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.util.DaggerSingletonObject;

import java.util.List;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxWorkspaceGesturePrefs extends AxPreferenceFeature {

    public static final String ACTION_NONE = "none";
    public static final String ACTION_SLEEP = "sleep";

    public static final DaggerSingletonObject<AxWorkspaceGesturePrefs> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getWorkspaceGesturePrefs);
    private static final List<Item> WORKSPACE_GESTURE_ITEMS = List.of(
            WORKSPACE_DOUBLE_TAP_ACTION,
            SLEEP_GESTURE);

    @Inject
    public AxWorkspaceGesturePrefs() {
        super(WORKSPACE_GESTURE_ITEMS);
    }

    public String getDoubleTapAction(Context context) {
        LauncherPrefs prefs = LauncherPrefs.get(context);
        String action = prefs.get(WORKSPACE_DOUBLE_TAP_ACTION);
        boolean sleepEnabled = prefs.get(SLEEP_GESTURE);
        if (sleepEnabled) {
            return ACTION_SLEEP;
        }
        if (!isSupportedAction(action)) {
            action = ACTION_NONE;
        }
        if (ACTION_SLEEP.equals(action)) {
            return ACTION_NONE;
        }
        return action;
    }

    public boolean handleDoubleTap(Launcher launcher, MotionEvent event) {
        switch (getDoubleTapAction(launcher)) {
            case ACTION_SLEEP:
                launcher.onSleepEvent(event);
                return true;
            case ACTION_NONE:
            default:
                return false;
        }
    }

    public static boolean isSupportedAction(String action) {
        return ACTION_NONE.equals(action)
                || ACTION_SLEEP.equals(action);
    }
}
