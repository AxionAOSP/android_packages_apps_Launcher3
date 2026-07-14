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
package com.android.launcher3.settings.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity

@Composable
internal fun GeneralScreen(
    activity: SettingsActivity,
    onNavigate: (String) -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.home_settings_icons_category)) {
        item {
            CategoryPreference(
                titleRes = R.string.icon_settings_title,
                summaryRes = R.string.icon_settings_summary,
                onClick = { onNavigate(HomeSettingsRoutes.ICONS) },
            )
        }
    }
    if (BuildConfig.NOTIFICATION_DOTS_ENABLED) {
        PreferenceGroup(title = stringResource(R.string.home_settings_notifications_category)) {
            item {
                NotificationDotsPreferenceItem(activity)
            }
        }
    }
    PreferenceGroup(title = stringResource(R.string.pref_launcher_blur_category)) {
        item {
            val blurEnabled = rememberLauncherPreference(LauncherPrefsExt.LAUNCHER_BLUR_ENABLED)
            SwitchPreference(
                title = stringResource(R.string.pref_launcher_blur_title),
                summary = stringResource(R.string.pref_launcher_blur_summary),
                checked = blurEnabled.value,
                onCheckedChange = blurEnabled.onChange,
            )
        }
        item {
            val blurEnabled = rememberLauncherPreference(LauncherPrefsExt.LAUNCHER_BLUR_ENABLED)
            IntSliderPreference(
                item = LauncherPrefsExt.LAUNCHER_BLUR_RADIUS_PCT,
                titleRes = R.string.pref_launcher_blur_strength_title,
                min = LauncherPrefsExt.LAUNCHER_BLUR_MIN_RADIUS_PCT,
                max = LauncherPrefsExt.LAUNCHER_BLUR_MAX_RADIUS_PCT,
                defaultValue = LauncherPrefsExt.LAUNCHER_BLUR_DEFAULT_RADIUS_PCT,
                interval = 1,
                enabled = blurEnabled.value,
                valueLabel = { stringResource(R.string.home_settings_percent_value, it) },
            )
        }
    }
}
