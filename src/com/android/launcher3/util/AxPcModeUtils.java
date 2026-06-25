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
package com.android.launcher3.util;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.Display;

public final class AxPcModeUtils {
    private static final String AX_PC_MODE_SETTING = "ax_pc_mode";
    private static final String AX_PC_MODE_PACKAGE = "com.android.axion.axpcmode";
    private static final String AX_PC_MODE_TASKS_ACTIVITY =
            "com.android.axion.axpcmode.activities.TasksOverviewActivity";
    private static final String AX_PC_MODE_SECONDARY_ACTIVITY =
            "com.android.axion.axpcmode.activities.SecondaryPcModeLauncherActivity";
    private static final String AX_PC_MODE_MOUSE_ACTIVITY =
            "com.android.axion.axpcmode.activities.MousePadActivity";

    private AxPcModeUtils() {
    }

    public static boolean isEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), AX_PC_MODE_SETTING, 0) == 1;
    }

    public static boolean startTasksOverview(Context context) {
        if (!isEnabled(context)) {
            return false;
        }
        return startActivity(context, createIntent(AX_PC_MODE_TASKS_ACTIVITY,
                Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static void startSecondaryLauncher(Context context, int displayId) {
        if (!isEnabled(context)) {
            return;
        }
        if (!startActivityOnDisplay(context, createMainIntent(AX_PC_MODE_SECONDARY_ACTIVITY,
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP), displayId)) {
            return;
        }
        startActivityOnDisplay(context, createIntent(AX_PC_MODE_MOUSE_ACTIVITY,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                Display.DEFAULT_DISPLAY);
    }

    private static Intent createMainIntent(String className, int flags) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(AX_PC_MODE_PACKAGE, className));
        intent.addFlags(flags);
        return intent;
    }

    private static Intent createIntent(String className, int flags) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(AX_PC_MODE_PACKAGE, className));
        intent.addFlags(flags);
        return intent;
    }

    private static boolean startActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
        return true;
    }

    private static boolean startActivityOnDisplay(Context context, Intent intent, int displayId) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        try {
            context.startActivity(intent, options.toBundle());
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
        return true;
    }
}
