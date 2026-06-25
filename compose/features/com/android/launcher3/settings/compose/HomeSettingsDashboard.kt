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
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity

@Composable
internal fun DashboardScreen(
    activity: SettingsActivity,
    onNavigate: (String) -> Unit,
) {
    PreferenceGroup {
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_general_title,
                summaryRes = R.string.home_settings_general_summary,
                iconRes = R.drawable.ic_home_settings_general,
                onClick = { onNavigate(HomeSettingsRoutes.GENERAL) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_screen,
                summaryRes = R.string.home_settings_home_summary,
                iconRes = R.drawable.ic_home_settings_home,
                onClick = { onNavigate(HomeSettingsRoutes.HOME) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_overview_category,
                summaryRes = R.string.home_settings_overview_summary,
                iconRes = R.drawable.ic_home_settings_overview,
                onClick = { onNavigate(HomeSettingsRoutes.OVERVIEW) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.all_apps_drawer_settings_title,
                summaryRes = R.string.home_settings_all_apps_summary,
                iconRes = R.drawable.ic_home_settings_all_apps,
                onClick = { onNavigate(HomeSettingsRoutes.ALL_APPS) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_search_title,
                summaryRes = R.string.home_settings_search_summary,
                iconRes = R.drawable.ic_home_settings_search,
                onClick = { onNavigate(HomeSettingsRoutes.SEARCH) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_privacy_title,
                summaryRes = R.string.home_settings_privacy_summary,
                iconRes = R.drawable.ic_home_settings_privacy,
                onClick = { onNavigate(HomeSettingsRoutes.PRIVACY) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_backup_title,
                summaryRes = R.string.home_settings_backup_summary,
                iconRes = R.drawable.ic_ps_settings,
                onClick = { onNavigate(HomeSettingsRoutes.BACKUP) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_about_title,
                summaryRes = R.string.home_settings_about_summary,
                iconRes = R.drawable.ic_info_no_shadow,
                onClick = { onNavigate(HomeSettingsRoutes.ABOUT) },
            )
        }
    }
}
