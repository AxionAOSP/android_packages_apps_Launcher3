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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.allapps.AxAllAppsDisplayPrefs

@Composable
internal fun AllAppsDrawerScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val defaultColumns = remember(context) {
        LauncherAppState.getIDP(context).getDeviceProfile(context).numShownAllAppsColumns
    }
    val opacityPreference = rememberLauncherPreference(
        item = LauncherPrefsExt.ALL_APPS_BG_OPACITY,
        read = { LauncherPrefsExt.allAppsOpacityPercent(context) },
        write = { prefs, value ->
            prefs.put(LauncherPrefsExt.ALL_APPS_BG_OPACITY, value.coerceIn(0, 100))
        },
    )
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_layout_category)) {
        item {
            CategoryPreference(
                titleRes = R.string.all_apps_folders_title,
                summaryRes = R.string.all_apps_folders_summary,
                onClick = { onNavigate(HomeSettingsRoutes.ALL_APPS_FOLDERS) },
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_behavior_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SHOW_ALLAPPS_PREDICTIONS,
                titleRes = R.string.drawer_predictions_title,
                summaryRes = R.string.drawer_predictions_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_HAPTIC_FEEDBACK,
                titleRes = R.string.drawer_haptic_feedback_title,
                summaryRes = R.string.drawer_haptic_feedback_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_REMEMBER_POSITION,
                titleRes = R.string.drawer_remember_position_title,
                summaryRes = R.string.drawer_remember_position_summary,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_grid_category)) {
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_COLUMNS,
                titleRes = R.string.drawer_columns_title,
                min = AxAllAppsDisplayPrefs.MIN_COLUMNS_FOR_SETTINGS,
                max = AxAllAppsDisplayPrefs.MAX_COLUMNS_FOR_SETTINGS,
                defaultValue = defaultColumns,
                valueOverride = { if (it > 0) it else defaultColumns },
                resetValue = 0,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_ROW_SCALE,
                titleRes = R.string.drawer_row_height_title,
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_SIDE_PADDING_SCALE,
                titleRes = R.string.drawer_side_padding_title,
                min = 0,
                max = 150,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_appearance_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALLAPPS_THEMED_ICONS,
                titleRes = R.string.pref_themed_icons_title,
                summaryRes = R.string.pref_themed_icons_summary,
            )
        }
        item {
            IntSliderPreference(
                preference = opacityPreference,
                titleRes = R.string.drawer_opacity_title,
                min = 0,
                max = 100,
                defaultValue = LauncherPrefsExt.ALL_APPS_DEFAULT_BG_OPACITY,
                interval = 10,
                valueLabel = { stringResource(R.string.home_settings_percent_value, it) },
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
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_LABEL_SCALE,
                titleRes = R.string.drawer_label_size_title,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_advanced_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SHOW_SCROLLBAR,
                titleRes = R.string.drawer_show_scrollbar_title,
                summaryRes = R.string.drawer_show_scrollbar_summary,
            )
        }
    }
}
