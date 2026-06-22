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
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity

@Composable
internal fun GeneralScreen(activity: SettingsActivity) {
    PreferenceGroup(title = stringResource(R.string.home_settings_icons_category)) {
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.WORKSPACE_ICON_SCALE,
                titleRes = R.string.home_icon_size_title,
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_ICON_SCALE,
                titleRes = R.string.drawer_icon_size_title,
            )
        }
    }
    if (com.android.launcher3.BuildConfig.NOTIFICATION_DOTS_ENABLED) {
        PreferenceGroup(title = stringResource(R.string.home_settings_notifications_category)) {
            item {
                NotificationDotsPreferenceItem(activity)
            }
        }
    }
}
