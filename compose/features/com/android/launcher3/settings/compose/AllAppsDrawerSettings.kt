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
internal fun AllAppsDrawerScreen() {
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_appearance_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALLAPPS_THEMED_ICONS,
                titleRes = R.string.pref_themed_icons_title,
                summaryRes = R.string.pref_themed_icons_summary,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_labels_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SHOW_DRAWER_LABELS,
                titleRes = R.string.drawer_show_labels,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ENABLE_TWOLINE_ALLAPPS_TOGGLE,
                titleRes = R.string.drawer_two_line_labels_title,
                summaryRes = R.string.drawer_two_line_labels_summary,
            )
        }
    }
}
