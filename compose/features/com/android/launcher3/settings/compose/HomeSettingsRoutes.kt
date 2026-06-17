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
    const val HOME = "home"
    const val ALL_APPS = "all_apps"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val PRIVACY = "privacy"

    fun fromRootKey(rootKey: String?): String? = when (rootKey) {
        null -> ROOT
        KEY_SCREEN_HOME -> HOME
        KEY_SCREEN_ALL_APPS -> ALL_APPS
        KEY_SCREEN_SEARCH -> SEARCH
        KEY_SCREEN_NOTIFICATIONS -> NOTIFICATIONS
        KEY_SCREEN_PRIVACY -> PRIVACY
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
        return when (key) {
            KEY_WORKSPACE_LOCK,
            KEY_SLEEP_GESTURE,
            KEY_ADD_ICON_TO_HOME,
            KEY_ALLOW_ROTATION,
            KEY_DESKTOP_LABELS,
            KEY_MINUS_ONE -> HOME
            KEY_SCREEN_ALL_APPS,
            KEY_ALLAPPS_THEMED_ICONS,
            KEY_DRAWER_LABELS -> ALL_APPS
            KEY_DRAWER_OPEN_KEYBOARD,
            KEY_SUGGESTIONS -> SEARCH
            KEY_NOTIFICATION_DOTS -> NOTIFICATIONS
            KEY_TRUST_APPS -> PRIVACY
            else -> fromRootKey(key)
        }
    }
}

internal fun parentRoute(route: String?): String? = when (route) {
    HomeSettingsRoutes.HOME,
    HomeSettingsRoutes.ALL_APPS,
    HomeSettingsRoutes.SEARCH,
    HomeSettingsRoutes.PRIVACY,
    HomeSettingsRoutes.NOTIFICATIONS -> HomeSettingsRoutes.ROOT
    else -> null
}

@StringRes
internal fun routeTitle(route: String): Int = when (route) {
    HomeSettingsRoutes.HOME -> R.string.home_screen
    HomeSettingsRoutes.ALL_APPS -> R.string.all_apps_drawer_settings_title
    HomeSettingsRoutes.SEARCH -> R.string.home_settings_search_title
    HomeSettingsRoutes.NOTIFICATIONS -> R.string.notifications_header
    HomeSettingsRoutes.PRIVACY -> R.string.home_settings_privacy_title
    else -> R.string.settings_button_text
}
