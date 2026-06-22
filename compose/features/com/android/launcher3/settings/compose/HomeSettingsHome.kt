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

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.Flags
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.util.DisplayController

@Composable
internal fun HomeScreen(
    activity: Activity,
    onNavigate: (String) -> Unit,
) {
    val showRotation = remember { shouldShowRotationPreference(activity) }
    val googleSearchVisible = rememberPackageEnabled(SEARCH_PACKAGE)
    PreferenceGroup(title = stringResource(R.string.home_settings_layout_category)) {
        item {
            CategoryPreference(
                titleRes = R.string.home_grid_title,
                summaryRes = R.string.home_grid_summary,
                onClick = { onNavigate(HomeSettingsRoutes.HOME_GRID) },
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_labels_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SHOW_DESKTOP_LABELS,
                titleRes = R.string.desktop_show_labels,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_gestures_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SLEEP_GESTURE,
                titleRes = R.string.pref_sleep_gesture_title,
                summaryRes = R.string.pref_sleep_gesture_summary,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_behavior_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_LOCK,
                titleRes = R.string.settings_lock_layout_title,
                summaryOnRes = R.string.settings_lock_layout_summary_on,
                summaryOffRes = R.string.settings_lock_layout_summary_off,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ADD_ICON_TO_HOME,
                titleRes = R.string.auto_add_shortcuts_label,
                summaryRes = R.string.auto_add_shortcuts_description,
            )
        }
        if (googleSearchVisible) {
            item {
                BooleanPreference(
                    item = LauncherPrefsExt.ENABLE_MINUS_ONE,
                    titleRes = R.string.title_show_google_app,
                    summaryRes = R.string.pref_show_google_now_summary,
                )
            }
        }
        if (showRotation) {
            item {
                BooleanPreference(
                    item = LauncherPrefs.ALLOW_ROTATION,
                    titleRes = R.string.allow_rotation_title,
                    summaryRes = R.string.allow_rotation_desc,
                )
            }
        }
    }
}

private fun shouldShowRotationPreference(context: Context): Boolean {
    val info = DisplayController.INSTANCE.get(context).getInfo()
    if (Flags.oneGridSpecs() && !info.isRotationAllowed()) {
        return false
    }
    if (info.isTablet(info.realBounds)) {
        return false
    }
    return true
}
