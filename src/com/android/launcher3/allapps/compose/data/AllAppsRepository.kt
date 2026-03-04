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

package com.android.launcher3.allapps.compose.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import com.android.launcher3.LauncherFiles
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.model.data.PrivateSpaceInstallAppButtonInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ItemInfoMatcher
import com.android.launcher3.util.SettingsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class AllAppsRepository(
    private val allAppsStore: AllAppsStore,
    private val context: Context
) {
    private val userCache = UserCache.getInstance(context)
    private val personalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle())

    val apps: Flow<AppsData> = callbackFlow {
        val listener = AllAppsStore.OnUpdateListener {
            trySend(buildAppsData())
        }
        allAppsStore.addUpdateListener(listener)
        trySend(buildAppsData())
        awaitClose { allAppsStore.removeUpdateListener(listener) }
    }.flowOn(Dispatchers.Default)

    val preferences: Flow<DrawerPreferences> = callbackFlow {
        val prefs = context.getSharedPreferences(
            LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key in PREFERENCE_KEYS) {
                trySend(readPreferences(sp))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(readPreferences(prefs))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun buildAppsData(): AppsData {
        val allApps = allAppsStore.getApps()?.toList() ?: emptyList()
        if (allApps.isEmpty()) return AppsData()

        val personal = mutableListOf<AppInfo>()
        val work = mutableListOf<AppInfo>()
        val private = mutableListOf<AppInfo>()

        for (app in allApps) {
            val userInfo = userCache.getUserInfo(app.user)
            when {
                userInfo.isPrivate -> {
                    if (app !is PrivateSpaceInstallAppButtonInfo) private.add(app)
                }
                personalMatcher.test(app) -> personal.add(app)
                else -> work.add(app)
            }
        }

        val sortKey: (AppInfo) -> String = { it.title?.toString()?.lowercase() ?: "" }

        val isWorkPaused = allAppsStore.hasModelFlag(
            AppsListData.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
        )
        val isPrivateLocked = allAppsStore.hasModelFlag(
            AppsListData.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
        )
        val isPrivateHidden = SettingsCache.INSTANCE
            .get(context)
            .getValue(SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI)

        return AppsData(
            personalApps = personal.sortedBy(sortKey),
            workApps = work.sortedBy(sortKey),
            privateApps = private.sortedBy(sortKey),
            isWorkPaused = isWorkPaused,
            isPrivateLocked = isPrivateLocked,
            isPrivateHidden = isPrivateHidden
        )
    }

    private fun readPreferences(prefs: SharedPreferences) = DrawerPreferences(
        showLabels = prefs.getBoolean(PreferenceKeys.DRAWER_SHOW_LABELS, true),
        showPredictions = prefs.getBoolean(PreferenceKeys.ALL_APPS_PREDICTIONS, true),
        themedIcons = prefs.getBoolean(PreferenceKeys.ALLAPPS_THEMED_ICONS, false)
    )

    data class AppsData(
        val personalApps: List<AppInfo> = emptyList(),
        val workApps: List<AppInfo> = emptyList(),
        val privateApps: List<AppInfo> = emptyList(),
        val isWorkPaused: Boolean = false,
        val isPrivateLocked: Boolean = false,
        val isPrivateHidden: Boolean = false
    )

    data class DrawerPreferences(
        val showLabels: Boolean = true,
        val showPredictions: Boolean = true,
        val themedIcons: Boolean = false
    )

    private companion object {
        val PREFERENCE_KEYS = setOf(
            PreferenceKeys.DRAWER_SHOW_LABELS,
            PreferenceKeys.ALL_APPS_PREDICTIONS,
            PreferenceKeys.ALLAPPS_THEMED_ICONS
        )
    }
}
