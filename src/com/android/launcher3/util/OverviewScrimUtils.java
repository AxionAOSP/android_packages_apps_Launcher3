/*
 * Copyright (C) 2025-2026 AxionOS
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

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Utilities;

public final class OverviewScrimUtils {

    public static final String RECENTS_OVERVIEW_SCRIM_OPACITY =
            "pulse_recents_overview_scrim_opacity";
    public static final int DEFAULT_RECENTS_OVERVIEW_SCRIM_OPACITY = 70;

    private OverviewScrimUtils() {}

    public static int getOverviewScrimOpacity(Context context) {
        return Utilities.boundToRange(Settings.Secure.getInt(
                context.getContentResolver(),
                RECENTS_OVERVIEW_SCRIM_OPACITY,
                DEFAULT_RECENTS_OVERVIEW_SCRIM_OPACITY), 0, 100);
    }

    public static int applyOverviewScrimOpacity(int color, int opacity) {
        return ColorUtils.setAlphaComponent(color,
                Math.round(Color.alpha(color) * Utilities.boundToRange(opacity, 0, 100) / 100f));
    }
}
