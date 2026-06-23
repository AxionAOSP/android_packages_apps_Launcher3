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
package com.android.launcher3

import android.content.Context
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.LauncherPrefs.Companion.nonRestorableItem
import kotlin.math.roundToInt

object LauncherPrefsExt {
    @JvmField
    val ENABLE_TWOLINE_ALLAPPS_TOGGLE = backedUpItem("pref_enable_two_line_toggle", false)
    @JvmField val ENABLE_MINUS_ONE =
        backedUpItem("pref_enable_minus_one", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val ADD_ICON_TO_HOME =
        backedUpItem(
            SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY,
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_LOCK =
        backedUpItem("pref_workspace_lock", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_GRID_COLUMNS =
        backedUpItem("pref_workspace_grid_columns", 0, EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_GRID_ROWS =
        backedUpItem("pref_workspace_grid_rows", 0, EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_HOTSEAT_ICONS =
        backedUpItem("pref_workspace_hotseat_icons", 0, EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS =
        backedUpItem(
            "pref_workspace_tablet_portrait_grid_columns",
            0,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_TABLET_PORTRAIT_GRID_ROWS =
        backedUpItem(
            "pref_workspace_tablet_portrait_grid_rows",
            0,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS =
        backedUpItem(
            "pref_workspace_tablet_portrait_hotseat_icons",
            0,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS =
        backedUpItem(
            "pref_workspace_tablet_landscape_grid_columns",
            0,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS =
        backedUpItem(
            "pref_workspace_tablet_landscape_grid_rows",
            0,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS =
        backedUpItem(
            "pref_workspace_tablet_landscape_hotseat_icons",
            0,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_ICON_SCALE =
        backedUpItem("pref_workspace_icon_scale", 100, EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_LABEL_SCALE =
        backedUpItem("pref_workspace_label_scale", 100, EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_WALLPAPER_SCROLLING =
        backedUpItem(
            "pref_workspace_wallpaper_scrolling",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_SHOW_TOP_SHADOW =
        backedUpItem(
            "pref_workspace_show_top_shadow",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val HOTSEAT_SEARCH_BAR =
        backedUpItem("pref_hotseat_search_bar", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val HOTSEAT_SEARCH_PROVIDER =
        backedUpItem("pref_hotseat_search_provider", "none", EncryptionType.SECURE_SETTINGS)
    @JvmField val WORKSPACE_DOUBLE_TAP_ACTION =
        backedUpItem(
            "pref_workspace_double_tap_action",
            "none",
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALLAPPS_THEMED_ICONS =
        backedUpItem("pref_allapps_themed_icons", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val DRAWER_OPEN_KEYBOARD =
        backedUpItem("pref_drawer_open_keyboard", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val SHOW_DESKTOP_LABELS =
        backedUpItem("pref_desktop_show_labels", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val SHOW_DRAWER_LABELS =
        backedUpItem("pref_drawer_show_labels", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_DRAWER_COLUMNS =
        backedUpItem("pref_all_apps_drawer_columns", 0, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_DRAWER_ICON_SCALE =
        backedUpItem(
            "pref_all_apps_drawer_icon_scale",
            100,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_DRAWER_LABEL_SCALE =
        backedUpItem(
            "pref_all_apps_drawer_label_scale",
            100,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_DRAWER_ROW_SCALE =
        backedUpItem(
            "pref_all_apps_drawer_row_scale",
            100,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_DRAWER_SIDE_PADDING_SCALE =
        backedUpItem(
            "pref_all_apps_drawer_side_padding_scale",
            100,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_REMEMBER_POSITION =
        backedUpItem(
            "pref_all_apps_remember_position",
            false,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SHOW_SCROLLBAR =
        backedUpItem("pref_all_apps_show_scrollbar", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_HAPTIC_FEEDBACK =
        backedUpItem("pref_all_apps_haptic_feedback", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_DRAWER_LAYOUT_MODE =
        backedUpItem("pref_app_drawer_layout_mode", 0, EncryptionType.SECURE_SETTINGS)
    @JvmField val PINNED_APPS =
        backedUpItem(
            "pref_all_apps_pinned_apps",
            emptySet<String>(),
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_FOLDERS =
        backedUpItem("pref_all_apps_folders", "[]", EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_SMART_DRAWER_FOLDERS =
        backedUpItem(
            "pref_all_apps_smart_drawer_folders",
            "[]",
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_APPS =
        backedUpItem(
            "pref_all_apps_search_result_apps",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_APP_ACTIONS =
        backedUpItem(
            "pref_all_apps_search_result_app_actions",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_QUICK_ANSWERS =
        backedUpItem(
            "pref_all_apps_search_result_quick_answers",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_SETTINGS =
        backedUpItem(
            "pref_all_apps_search_result_settings",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_CONTACTS =
        backedUpItem(
            "pref_all_apps_search_result_contacts",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_IMAGES =
        backedUpItem(
            "pref_all_apps_search_result_images",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_FILES =
        backedUpItem(
            "pref_all_apps_search_result_files",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_CALENDAR =
        backedUpItem(
            "pref_all_apps_search_result_calendar",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_WEB =
        backedUpItem(
            "pref_all_apps_search_result_web",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_IN_APPS =
        backedUpItem(
            "pref_all_apps_search_result_in_apps",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_RESULT_MEDIA =
        backedUpItem(
            "pref_all_apps_search_result_media",
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_FUZZY_APPS =
        backedUpItem("pref_all_apps_search_fuzzy_apps", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_SEARCH_MAX_APPS =
        backedUpItem("pref_all_apps_search_max_apps", 10, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALL_APPS_SEARCH_MAX_APP_ACTIONS =
        backedUpItem(
            "pref_all_apps_search_max_app_actions",
            3,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_MAX_EXTERNAL_RESULTS =
        backedUpItem(
            "pref_all_apps_search_max_external_results",
            3,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_WEB_DELAY_MS =
        backedUpItem(
            "pref_all_apps_search_web_delay_ms",
            250,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val ALL_APPS_SEARCH_HISTORY =
        nonRestorableItem(
            "pref_all_apps_search_history",
            "[]",
            EncryptionType.SECURE_SETTINGS,
        )
    const val ALL_APPS_DEFAULT_BG_OPACITY = 80
    @JvmField val ALL_APPS_BG_OPACITY =
        backedUpItem(
            "pref_all_apps_bg_opacity",
            ALL_APPS_DEFAULT_BG_OPACITY,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val SHOW_ALLAPPS_PREDICTIONS =
        backedUpItem("pref_all_apps_predictions", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val SLEEP_GESTURE =
        backedUpItem("pref_sleep_gesture", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val AXION_SETTINGS_BACKUP_ITEMS: List<Item> = listOf(
        ENABLE_MINUS_ONE,
        ADD_ICON_TO_HOME,
        WORKSPACE_LOCK,
        WORKSPACE_GRID_COLUMNS,
        WORKSPACE_GRID_ROWS,
        WORKSPACE_HOTSEAT_ICONS,
        WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS,
        WORKSPACE_TABLET_PORTRAIT_GRID_ROWS,
        WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS,
        WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS,
        WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS,
        WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS,
        WORKSPACE_ICON_SCALE,
        WORKSPACE_LABEL_SCALE,
        WORKSPACE_WALLPAPER_SCROLLING,
        WORKSPACE_SHOW_TOP_SHADOW,
        HOTSEAT_SEARCH_BAR,
        HOTSEAT_SEARCH_PROVIDER,
        WORKSPACE_DOUBLE_TAP_ACTION,
        ALLAPPS_THEMED_ICONS,
        DRAWER_OPEN_KEYBOARD,
        SHOW_DESKTOP_LABELS,
        SHOW_DRAWER_LABELS,
        ALL_APPS_DRAWER_COLUMNS,
        ALL_APPS_DRAWER_ICON_SCALE,
        ALL_APPS_DRAWER_LABEL_SCALE,
        ALL_APPS_DRAWER_ROW_SCALE,
        ALL_APPS_DRAWER_SIDE_PADDING_SCALE,
        ALL_APPS_REMEMBER_POSITION,
        ALL_APPS_SHOW_SCROLLBAR,
        ALL_APPS_HAPTIC_FEEDBACK,
        ALL_APPS_DRAWER_LAYOUT_MODE,
        PINNED_APPS,
        ALL_APPS_FOLDERS,
        ALL_APPS_SMART_DRAWER_FOLDERS,
        ALL_APPS_SEARCH_RESULT_APPS,
        ALL_APPS_SEARCH_RESULT_APP_ACTIONS,
        ALL_APPS_SEARCH_RESULT_QUICK_ANSWERS,
        ALL_APPS_SEARCH_RESULT_SETTINGS,
        ALL_APPS_SEARCH_RESULT_CONTACTS,
        ALL_APPS_SEARCH_RESULT_IMAGES,
        ALL_APPS_SEARCH_RESULT_FILES,
        ALL_APPS_SEARCH_RESULT_CALENDAR,
        ALL_APPS_SEARCH_RESULT_WEB,
        ALL_APPS_SEARCH_RESULT_IN_APPS,
        ALL_APPS_SEARCH_RESULT_MEDIA,
        ALL_APPS_SEARCH_FUZZY_APPS,
        ALL_APPS_SEARCH_MAX_APPS,
        ALL_APPS_SEARCH_MAX_APP_ACTIONS,
        ALL_APPS_SEARCH_MAX_EXTERNAL_RESULTS,
        ALL_APPS_SEARCH_WEB_DELAY_MS,
        ALL_APPS_BG_OPACITY,
        SHOW_ALLAPPS_PREDICTIONS,
        SLEEP_GESTURE,
    )
    @JvmStatic
    fun allAppsBackgroundAlpha(context: Context): Int {
        val opacity = allAppsOpacityPercent(context)
        return Utilities.boundToRange((opacity * 255f / 100f).roundToInt(), 0, 255)
    }

    @JvmStatic
    fun allAppsOpacityPercent(context: Context): Int {
        val opacity = ALL_APPS_BG_OPACITY.get(context)
        if (opacity > 100) {
            val normalized = Utilities.boundToRange(
                (opacity * 100f / 255f).roundToInt(),
                0,
                100,
            )
            LauncherPrefs.get(context).put(ALL_APPS_BG_OPACITY, normalized)
            return normalized
        }
        return Utilities.boundToRange(opacity, 0, 100)
    }

}
