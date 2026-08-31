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

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.util.PackageManagerUtils
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.allapps.search.AxSearchHistory
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.qsb.OseWidgetManager

@Composable
internal fun SearchResultsSettings(activity: Activity) {
    val context = LocalContext.current
    val permissionVersion = rememberResumeVersion()
    val showContactsPermission = remember(context, permissionVersion) {
        !hasPermission(context, Manifest.permission.READ_CONTACTS)
    }
    val showStoragePermission = remember(context, permissionVersion) {
        !hasStorageSearchPermission(context)
    }
    val showCalendarPermission = remember(context, permissionVersion) {
        !hasPermission(context, Manifest.permission.READ_CALENDAR)
    }
    SearchResultsBehaviorGroup()
    SearchResultsCoreGroup()
    SearchResultsPersonalGroup()
    SearchResultsWebMediaGroup()
    SearchResultsMaximumGroup()
    if (showContactsPermission || showStoragePermission || showCalendarPermission) {
        PermissionGroup(
            activity = activity,
            showContactsPermission = showContactsPermission,
            showStoragePermission = showStoragePermission,
            showCalendarPermission = showCalendarPermission,
        )
    }
}

@Composable
private fun SearchResultsBehaviorGroup() {
    val context = LocalContext.current
    val resumeVersion = rememberResumeVersion()
    var historyVersion by remember { mutableIntStateOf(0) }
    val hasHistory = remember(context, resumeVersion, historyVersion) {
        AxSearchHistory.hasHistory(context)
    }
    val clearHistorySuccess = stringResource(R.string.search_history_clear_success)
    PreferenceGroup(title = stringResource(R.string.search_results_category_behavior)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SEARCH_HISTORY_ENABLED,
                titleRes = R.string.search_history_title,
                summaryOnRes = R.string.search_history_summary_on,
                summaryOffRes = R.string.search_history_summary_off,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_FUZZY_APPS,
                titleRes = R.string.search_results_fuzzy_apps_title,
                summaryRes = R.string.search_results_fuzzy_apps_summary,
            )
        }
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_WEB_DELAY_MS,
                titleRes = R.string.search_results_web_delay_title,
                min = 0,
                max = 1000,
                defaultValue = 250,
                interval = 50,
                valueLabel = { stringResource(R.string.home_settings_ms_value, it) },
            )
        }
        if (hasHistory) {
            item {
                CategoryPreference(
                    titleRes = R.string.search_history_clear_title,
                    summaryRes = R.string.search_history_clear_summary,
                    onClick = {
                        AxSearchHistory.clear(context)
                        historyVersion++
                        showToast(context, clearHistorySuccess)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultsMaximumGroup() {
    PreferenceGroup(title = stringResource(R.string.search_results_category_limits)) {
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_MAX_APPS,
                titleRes = R.string.search_results_max_apps_title,
                min = 1,
                max = 20,
                defaultValue = 10,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_MAX_APP_ACTIONS,
                titleRes = R.string.search_results_max_app_actions_title,
                min = 0,
                max = 20,
                defaultValue = 3,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_MAX_EXTERNAL_RESULTS,
                titleRes = R.string.search_results_max_external_title,
                min = 1,
                max = 20,
                defaultValue = 3,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
    }
}

@Composable
private fun SearchResultsCoreGroup() {
    PreferenceGroup(title = stringResource(R.string.search_results_category_core)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_APPS,
                titleRes = R.string.search_results_apps_title,
                summaryRes = R.string.search_results_apps_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_APP_ACTIONS,
                titleRes = R.string.search_results_app_actions_title,
                summaryRes = R.string.search_results_app_actions_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_QUICK_ANSWERS,
                titleRes = R.string.search_results_quick_answers_title,
                summaryRes = R.string.search_results_quick_answers_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_SETTINGS,
                titleRes = R.string.search_results_settings_type_title,
                summaryRes = R.string.search_results_settings_type_summary,
            )
        }
    }
}

@Composable
private fun SearchResultsPersonalGroup() {
    PreferenceGroup(title = stringResource(R.string.search_results_category_personal)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_CONTACTS,
                titleRes = R.string.search_results_contacts_title,
                summaryRes = R.string.search_results_contacts_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_IMAGES,
                titleRes = R.string.search_results_images_title,
                summaryRes = R.string.search_results_images_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_FILES,
                titleRes = R.string.search_results_files_title,
                summaryRes = R.string.search_results_files_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_CALENDAR,
                titleRes = R.string.search_results_calendar_title,
                summaryRes = R.string.search_results_calendar_summary,
            )
        }
    }
}

@Composable
private fun SearchResultsWebMediaGroup() {
    PreferenceGroup(title = stringResource(R.string.search_results_category_web_media)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_WEB,
                titleRes = R.string.search_results_web_title,
                summaryRes = R.string.search_results_web_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_IN_APPS,
                titleRes = R.string.search_results_in_apps_title,
                summaryRes = R.string.search_results_in_apps_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_MEDIA,
                titleRes = R.string.search_results_media_title,
                summaryRes = R.string.search_results_media_summary,
            )
        }
    }
}

@Composable
private fun PermissionGroup(
    activity: Activity,
    showContactsPermission: Boolean,
    showStoragePermission: Boolean,
    showCalendarPermission: Boolean,
) {
    PreferenceGroup(title = stringResource(R.string.search_permissions_category_title)) {
        if (showContactsPermission) {
            item {
                CategoryPreference(
                    titleRes = R.string.search_permissions_contacts_title,
                    summaryRes = R.string.search_permissions_contacts_summary,
                    onClick = {
                        activity.requestPermissions(
                            arrayOf(Manifest.permission.READ_CONTACTS),
                            REQUEST_SEARCH_CONTACTS_PERMISSION,
                        )
                    },
                )
            }
        }
        if (showStoragePermission) {
            item {
                CategoryPreference(
                    titleRes = R.string.search_permissions_storage_title,
                    summaryRes = R.string.search_permissions_storage_summary,
                    onClick = {
                        activity.requestPermissions(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                            ),
                            REQUEST_SEARCH_STORAGE_PERMISSION,
                        )
                    },
                )
            }
        }
        if (showCalendarPermission) {
            item {
                CategoryPreference(
                    titleRes = R.string.search_permissions_calendar_title,
                    summaryRes = R.string.search_permissions_calendar_summary,
                    onClick = {
                        activity.requestPermissions(
                            arrayOf(Manifest.permission.READ_CALENDAR),
                            REQUEST_SEARCH_CALENDAR_PERMISSION,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SearchScreen(activity: Activity) {
    val context = LocalContext.current
    val resumeVersion = rememberResumeVersion()
    val dockSearchPreference = rememberLauncherPreference(LauncherPrefsExt.HOTSEAT_SEARCH_BAR)
    val dockSearchProviderPreference = rememberLauncherPreference(
        LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER,
    )
    val oseWidgetManager = remember(context) { context.appComponent.oseWidgetManager }
    val canConfigureSearchWidget = remember(
        dockSearchPreference.value,
        dockSearchProviderPreference.value,
        oseWidgetManager,
        resumeVersion,
    ) {
        dockSearchPreference.value &&
            dockSearchProviderPreference.value != OseWidgetManager.SEARCH_PROVIDER_NONE &&
            oseWidgetManager.canConfigure()
    }
    val suggestionsVisible = rememberPackageEnabled(SUGGESTIONS_PACKAGE)
    PreferenceGroup(title = stringResource(R.string.home_settings_search_bar_category)) {
        item {
            DockSearchProviderPreference(resumeVersion)
        }
        if (canConfigureSearchWidget) {
            item {
                ClickablePreference(
                    title = stringResource(R.string.dock_search_provider_settings_title),
                    summary = stringResource(R.string.dock_search_provider_settings_summary),
                    onClick = { oseWidgetManager.startConfigActivity(activity) },
                )
            }
        }
    }
    PreferenceGroup(title = stringResource(R.string.search_app_drawer_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.DRAWER_OPEN_KEYBOARD,
                titleRes = R.string.drawer_open_keyboard_title,
                summaryRes = R.string.drawer_open_keyboard_summary,
            )
        }
    }
    SearchResultsSettings(activity)
    if (suggestionsVisible) {
        PreferenceGroup(title = stringResource(R.string.home_settings_suggestions_category)) {
            item {
                CategoryPreference(
                    titleRes = R.string.pref_suggestions_title,
                    summaryRes = R.string.pref_suggestions_summary,
                    onClick = { activity.startActivity(Intent(CONTENT_SUGGESTIONS_ACTION)) },
                )
            }
        }
    }
}

@Composable
private fun DockSearchProviderPreference(resumeVersion: Int) {
    val context = LocalContext.current
    val enabledPreference = rememberLauncherPreference(LauncherPrefsExt.HOTSEAT_SEARCH_BAR)
    val providerPreference = rememberLauncherPreference(LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER)
    val disabledLabel = stringResource(R.string.dock_search_bar_disabled)
    val availableProviders = remember(context, resumeVersion) {
        OseWidgetManager.getAvailableSearchWidgets(context)
            .sortedBy { searchProviderLabel(context, it).lowercase() }
    }
    val options = remember(context, availableProviders, disabledLabel) {
        listOf(OseWidgetManager.SEARCH_PROVIDER_NONE to disabledLabel) +
            availableProviders.map {
                it.provider.flattenToString() to searchProviderLabel(context, it)
            }
    }
    val selectedProvider = providerPreference.value
    val selectedValue = remember(
        enabledPreference.value,
        selectedProvider,
        availableProviders,
        options,
    ) {
        when {
            !enabledPreference.value -> OseWidgetManager.SEARCH_PROVIDER_NONE
            selectedProvider == OseWidgetManager.SEARCH_PROVIDER_NONE ->
                OseWidgetManager.SEARCH_PROVIDER_NONE
            options.any { it.first == selectedProvider } -> selectedProvider
            else -> selectedOptionForPackage(availableProviders, selectedProvider)
        }
    }
    ListPreference(
        title = stringResource(R.string.dock_search_bar_title),
        summary = stringResource(R.string.dock_search_bar_summary),
        options = options,
        value = selectedValue,
        enabled = availableProviders.isNotEmpty(),
        onValueChange = {
            if (it == OseWidgetManager.SEARCH_PROVIDER_NONE) {
                enabledPreference.onChange(false)
                providerPreference.onChange(OseWidgetManager.SEARCH_PROVIDER_NONE)
            } else {
                providerPreference.onChange(it)
                enabledPreference.onChange(true)
            }
        },
    )
}

private fun searchProviderLabel(context: Context, info: AppWidgetProviderInfo): String {
    PackageManagerUtils.getApplicationInfo(context, info.provider.packageName)?.let {
        return PackageManagerUtils.loadApplicationLabel(context.packageManager, it).toString()
    }
    return PackageManagerUtils.loadAppWidgetProviderLabel(context, info).toString()
}

private fun selectedOptionForPackage(
    providers: List<AppWidgetProviderInfo>,
    selectedProvider: String,
): String {
    val packageName = ComponentName.unflattenFromString(selectedProvider)?.packageName
        ?: return OseWidgetManager.SEARCH_PROVIDER_NONE
    return providers.firstOrNull { it.provider.packageName == packageName }
        ?.provider
        ?.flattenToString()
        ?: OseWidgetManager.SEARCH_PROVIDER_NONE
}

private fun hasStorageSearchPermission(context: Context): Boolean {
    return Environment.isExternalStorageManager() ||
            hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ||
            hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ||
            hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ||
            hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ||
            hasPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
