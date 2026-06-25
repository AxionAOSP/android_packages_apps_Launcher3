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

@Composable
internal fun OverviewScreen() {
    PreferenceGroup(title = stringResource(R.string.home_settings_overview_actions_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.RECENTS_SHOW_LOCK_BUTTON,
                titleRes = R.string.home_recents_lock_button_title,
                summaryRes = R.string.home_recents_lock_button_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.RECENTS_SHOW_FREEFORM_BUTTON,
                titleRes = R.string.home_recents_freeform_button_title,
                summaryRes = R.string.home_recents_freeform_button_summary,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_overview_appearance_category)) {
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.RECENTS_OVERVIEW_SCRIM_OPACITY,
                titleRes = R.string.pref_recents_overview_scrim_opacity_title,
                min = 0,
                max = 100,
                defaultValue = LauncherPrefsExt.RECENTS_OVERVIEW_SCRIM_DEFAULT_OPACITY,
                interval = 10,
                valueLabel = {
                    stringResource(R.string.pref_recents_overview_scrim_opacity_percent, it)
                },
            )
        }
    }
}
