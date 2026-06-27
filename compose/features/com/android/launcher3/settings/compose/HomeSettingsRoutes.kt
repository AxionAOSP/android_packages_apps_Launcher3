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

import android.content.Intent
import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity

internal object HomeSettingsRoutes {
    const val ROOT = "root"
    const val GENERAL = "general"
    const val HOME = "home"
    const val ICONS = "icons"
    const val HOME_GRID = "home_grid"
    const val OVERVIEW = "overview"
    const val ALL_APPS = "all_apps"
    const val ALL_APPS_FOLDERS = "all_apps_folders"
    const val ALL_APPS_SMART_DRAWER = "all_apps_smart_drawer"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val PRIVACY = "privacy"
    const val BACKUP = "backup"
    const val ABOUT = "about"

    fun fromRootKey(rootKey: String?): String? = when (rootKey) {
        null -> ROOT
        KEY_SCREEN_GENERAL -> GENERAL
        KEY_SCREEN_HOME -> HOME
        KEY_SCREEN_ICONS -> ICONS
        HomeGridSettingsKeys.OPTIONS -> HOME_GRID
        KEY_SCREEN_OVERVIEW -> OVERVIEW
        KEY_SCREEN_ALL_APPS -> ALL_APPS
        KEY_ALL_APPS_SEARCH_RESULTS -> SEARCH
        KEY_ALL_APPS_FOLDER_SETTINGS -> ALL_APPS_FOLDERS
        KEY_ALL_APPS_DRAWER_OPTIONS -> ALL_APPS
        KEY_ALL_APPS_SMART_DRAWER_FOLDERS -> ALL_APPS_SMART_DRAWER
        KEY_SCREEN_SEARCH -> SEARCH
        KEY_SCREEN_NOTIFICATIONS -> NOTIFICATIONS
        KEY_SCREEN_PRIVACY -> PRIVACY
        KEY_SCREEN_BACKUP -> BACKUP
        KEY_SCREEN_ABOUT -> ABOUT
        else -> null
    }

    fun fromIntent(intent: Intent): String {
        val args = intent.getBundleExtra(SettingsActivity.EXTRA_FRAGMENT_ARGS)
        val rootKey = intent.getStringExtra(SettingsActivity.EXTRA_FRAGMENT_ROOT_KEY)
            ?: args?.getString(SettingsActivity.EXTRA_FRAGMENT_ROOT_KEY)
        fromRootKey(rootKey)?.let { return it }
        val highlightKey = intent.getStringExtra(SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY)
            ?: args?.getString(SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY)
        return fromLegacyPreferenceKey(highlightKey) ?: ROOT
    }

    private fun fromLegacyPreferenceKey(key: String?): String? {
        if (HomeGridSettingsKeys.contains(key)) {
            return HOME
        }
        return when (key) {
            KEY_SCREEN_GENERAL,
            KEY_LAUNCHER_BLUR_ENABLED,
            KEY_LAUNCHER_BLUR_RADIUS -> GENERAL
            KEY_ICON_PACK_PACKAGE,
            KEY_THEMED_ICON_PACK,
            KEY_THEMED_ICONS,
            KEY_THEMED_ICON_SCALE,
            KEY_THEMED_ICON_BACKGROUND_COLOR,
            KEY_THEMED_ICON_BACKGROUND_COLOR_SOURCE,
            KEY_THEMED_ICON_FOREGROUND_COLOR,
            KEY_THEMED_ICON_FOREGROUND_COLOR_SOURCE,
            KEY_THEMED_ICON_COLOR_PRESET,
            KEY_ICON_OVERRIDES -> ICONS
            KEY_WORKSPACE_LOCK,
            KEY_WORKSPACE_ICON_SCALE,
            KEY_WORKSPACE_LABEL_SCALE,
            KEY_WORKSPACE_WALLPAPER_SCROLLING,
            KEY_WORKSPACE_SHOW_TOP_SHADOW,
            KEY_WORKSPACE_ROUNDED_WIDGETS,
            KEY_WORKSPACE_ALLOW_WIDGET_OVERLAP,
            KEY_WORKSPACE_FORCE_WIDGET_RESIZE,
            KEY_WORKSPACE_WIDGET_UNLIMITED_SIZE,
            KEY_WORKSPACE_DOUBLE_TAP_ACTION,
            KEY_MINUS_ONE,
            KEY_SLEEP_GESTURE,
            KEY_ADD_ICON_TO_HOME,
            KEY_ALLOW_ROTATION,
            KEY_DESKTOP_LABELS -> HOME
            KEY_SCREEN_OVERVIEW,
            KEY_RECENTS_SHOW_LOCK_BUTTON,
            KEY_RECENTS_SHOW_FREEFORM_BUTTON,
            KEY_RECENTS_OVERVIEW_SCRIM_OPACITY -> OVERVIEW
            KEY_SCREEN_ALL_APPS -> ALL_APPS
            KEY_ALL_APPS_FOLDER_SETTINGS -> ALL_APPS_FOLDERS
            KEY_ALL_APPS_SEARCH_SETTINGS,
            KEY_ALL_APPS_SEARCH_RESULTS,
            KEY_SEARCH_RESULT_APPS,
            KEY_SEARCH_RESULT_APP_ACTIONS,
            KEY_SEARCH_RESULT_QUICK_ANSWERS,
            KEY_SEARCH_RESULT_SETTINGS,
            KEY_SEARCH_RESULT_CONTACTS,
            KEY_SEARCH_RESULT_IMAGES,
            KEY_SEARCH_RESULT_FILES,
            KEY_SEARCH_RESULT_CALENDAR,
            KEY_SEARCH_RESULT_WEB,
            KEY_SEARCH_RESULT_IN_APPS,
            KEY_SEARCH_RESULT_MEDIA,
            KEY_SEARCH_FUZZY_APPS,
            KEY_SEARCH_MAX_APPS,
            KEY_SEARCH_MAX_APP_ACTIONS,
            KEY_SEARCH_MAX_EXTERNAL_RESULTS,
            KEY_SEARCH_WEB_DELAY_MS,
            KEY_SEARCH_HISTORY_CLEAR,
            KEY_SEARCH_PERMISSION_CONTACTS,
            KEY_SEARCH_PERMISSION_STORAGE,
            KEY_SEARCH_PERMISSION_CALENDAR,
            KEY_DRAWER_OPEN_KEYBOARD,
            KEY_HOTSEAT_SEARCH_BAR,
            KEY_HOTSEAT_SEARCH_PROVIDER,
            KEY_SUGGESTIONS -> SEARCH
            KEY_ALL_APPS_DRAWER_SETTINGS,
            KEY_ALL_APPS_DRAWER_OPTIONS,
            KEY_ALL_APPS_SMART_DRAWER_FOLDERS,
            KEY_ALLAPPS_THEMED_ICONS,
            KEY_DRAWER_LABELS,
            KEY_ALL_APPS_BG_OPACITY,
            KEY_ALL_APPS_DRAWER_COLUMNS,
            KEY_ALL_APPS_DRAWER_ICON_SCALE,
            KEY_ALL_APPS_DRAWER_LABEL_SCALE,
            KEY_ALL_APPS_DRAWER_ROW_SCALE,
            KEY_ALL_APPS_DRAWER_SIDE_PADDING_SCALE,
            KEY_ALL_APPS_REMEMBER_POSITION,
            KEY_ALL_APPS_SHOW_SCROLLBAR,
            KEY_ALL_APPS_HAPTIC_FEEDBACK,
            KEY_ALL_APPS_DRAWER_LAYOUT_MODE,
            KEY_ALL_APPS_PREDICTIONS -> ALL_APPS
            KEY_NOTIFICATION_DOTS -> NOTIFICATIONS
            KEY_TRUST_APPS -> PRIVACY
            KEY_BACKUP_EXPORT,
            KEY_BACKUP_IMPORT -> BACKUP
            KEY_SCREEN_ABOUT -> ABOUT
            else -> fromRootKey(key)
        }
    }
}

internal fun parentRoute(route: String?): String? = when (route) {
    HomeSettingsRoutes.GENERAL,
    HomeSettingsRoutes.HOME,
    HomeSettingsRoutes.OVERVIEW,
    HomeSettingsRoutes.ALL_APPS,
    HomeSettingsRoutes.SEARCH,
    HomeSettingsRoutes.PRIVACY,
    HomeSettingsRoutes.BACKUP,
    HomeSettingsRoutes.ABOUT -> HomeSettingsRoutes.ROOT
    HomeSettingsRoutes.NOTIFICATIONS,
    HomeSettingsRoutes.ICONS -> HomeSettingsRoutes.GENERAL
    HomeSettingsRoutes.HOME_GRID -> HomeSettingsRoutes.HOME
    HomeSettingsRoutes.ALL_APPS_FOLDERS,
    HomeSettingsRoutes.ALL_APPS_SMART_DRAWER -> HomeSettingsRoutes.ALL_APPS
    else -> null
}

@StringRes
internal fun routeTitle(route: String): Int = when (route) {
    HomeSettingsRoutes.GENERAL -> R.string.home_settings_general_title
    HomeSettingsRoutes.HOME -> R.string.home_screen
    HomeSettingsRoutes.ICONS -> R.string.icon_settings_title
    HomeSettingsRoutes.HOME_GRID -> R.string.home_grid_title
    HomeSettingsRoutes.OVERVIEW -> R.string.home_settings_overview_category
    HomeSettingsRoutes.ALL_APPS -> R.string.all_apps_drawer_settings_title
    HomeSettingsRoutes.ALL_APPS_FOLDERS -> R.string.all_apps_folders_title
    HomeSettingsRoutes.ALL_APPS_SMART_DRAWER -> R.string.smart_drawer_folders_title
    HomeSettingsRoutes.SEARCH -> R.string.home_settings_search_title
    HomeSettingsRoutes.NOTIFICATIONS -> R.string.notifications_header
    HomeSettingsRoutes.PRIVACY -> R.string.home_settings_privacy_title
    HomeSettingsRoutes.BACKUP -> R.string.home_settings_backup_title
    HomeSettingsRoutes.ABOUT -> R.string.home_settings_about_title
    else -> R.string.settings_button_text
}
