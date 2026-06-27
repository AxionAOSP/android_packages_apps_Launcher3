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
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.AxWorkspaceGesturePrefs
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
            PercentSliderPreference(
                item = LauncherPrefsExt.WORKSPACE_ICON_SCALE,
                titleRes = R.string.home_icon_size_title,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SHOW_DESKTOP_LABELS,
                titleRes = R.string.desktop_show_labels,
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.WORKSPACE_LABEL_SCALE,
                titleRes = R.string.home_label_size_title,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_style_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_WALLPAPER_SCROLLING,
                titleRes = R.string.home_wallpaper_scrolling_title,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.DISABLE_WALLPAPER_ZOOM,
                titleRes = R.string.pref_disable_wallpaper_zoom_title,
                summaryRes = R.string.pref_disable_wallpaper_zoom_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_SHOW_TOP_SHADOW,
                titleRes = R.string.home_top_bar_shadow_title,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.widget_button_text)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_ROUNDED_WIDGETS,
                titleRes = R.string.home_widgets_rounded_title,
                summaryRes = R.string.home_widgets_rounded_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_ALLOW_WIDGET_OVERLAP,
                titleRes = R.string.home_widgets_overlap_title,
                summaryRes = R.string.home_widgets_overlap_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_WIDGET_UNLIMITED_SIZE,
                titleRes = R.string.home_widgets_unlimited_size_title,
                summaryRes = R.string.home_widgets_unlimited_size_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.WORKSPACE_FORCE_WIDGET_RESIZE,
                titleRes = R.string.home_widgets_force_resize_title,
                summaryRes = R.string.home_widgets_force_resize_summary,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_gestures_category)) {
        item {
            DoubleTapActionPreference()
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

@Composable
private fun DoubleTapActionPreference() {
    val actionPreference = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_DOUBLE_TAP_ACTION)
    val sleepPreference = rememberLauncherPreference(LauncherPrefsExt.SLEEP_GESTURE)
    val storedAction = if (actionPreference.value == AxWorkspaceGesturePrefs.ACTION_NONE ||
        actionPreference.value == AxWorkspaceGesturePrefs.ACTION_SLEEP) {
        actionPreference.value
    } else {
        AxWorkspaceGesturePrefs.ACTION_NONE
    }
    val value = when {
        sleepPreference.value ->
            AxWorkspaceGesturePrefs.ACTION_SLEEP
        storedAction == AxWorkspaceGesturePrefs.ACTION_SLEEP ->
            AxWorkspaceGesturePrefs.ACTION_NONE
        else -> storedAction
    }
    ListPreference(
        title = stringResource(R.string.home_double_tap_action_title),
        options = listOf(
            AxWorkspaceGesturePrefs.ACTION_NONE to
                stringResource(R.string.home_double_tap_action_none),
            AxWorkspaceGesturePrefs.ACTION_SLEEP to
                stringResource(R.string.home_double_tap_action_sleep),
        ),
        value = value,
        onValueChange = {
            actionPreference.onChange(it)
            sleepPreference.onChange(it == AxWorkspaceGesturePrefs.ACTION_SLEEP)
        },
    )
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
