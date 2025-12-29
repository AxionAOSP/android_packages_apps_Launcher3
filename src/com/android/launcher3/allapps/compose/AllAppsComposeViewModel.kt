/*
 * Copyright (C) 2025 AxionOS
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

package com.android.launcher3.allapps.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import com.android.launcher3.LauncherFiles
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ItemInfoMatcher
import com.android.launcher3.views.ActivityContext
import kotlinx.coroutines.flow.*

class AllAppsComposeViewModel<T>(
    private val allAppsStore: AllAppsStore<T>,
    private val context: Context
) where T : Context, T : ActivityContext {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
    )
    
    val pinnedAppsManager = PinnedAppsManager(context)

    private val _state = MutableStateFlow(AllAppsComposeState())
    val state: StateFlow<AllAppsComposeState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transitionProgress = MutableStateFlow(1f)
    val transitionProgress: StateFlow<Float> = _transitionProgress.asStateFlow()

    private val personalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle())

    private val appsUpdateListener = AllAppsStore.OnUpdateListener {
        updateApps()
    }

    init {
        allAppsStore.addUpdateListener(appsUpdateListener)
        updateApps()
        observePreferences()
        observePinnedApps()
    }

    private fun observePreferences() {
        val showLabels = prefs.getBoolean("pref_drawer_show_labels", true)
        _state.update { it.copy(showLabels = showLabels) }

        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "pref_drawer_show_labels") {
                _state.update { it.copy(showLabels = prefs.getBoolean(key, true)) }
            }
        }
    }
    
    private fun observePinnedApps() {
        pinnedAppsManager.pinnedApps.onEach { pinnedSet ->
            updateApps()
        }.launchIn(kotlinx.coroutines.GlobalScope)
    }

    private fun updateApps() {
        val allApps = allAppsStore.apps?.toList() ?: emptyList()
        val pinnedSet = pinnedAppsManager.pinnedApps.value
        val filteredApps = filterAppsForCurrentTab(allApps)
        
        val pinnedApps = filteredApps.filter { 
            it.componentName?.flattenToString() in pinnedSet 
        }
        val nonPinnedApps = filteredApps.filter { 
            it.componentName?.flattenToString() !in pinnedSet 
        }

        _state.update { currentState ->
            currentState.copy(
                apps = nonPinnedApps,
                pinnedApps = pinnedApps,
                hasWorkApps = allApps.any { !personalMatcher.test(it) },
                hasPrivateApps = false
            )
        }
    }

    private fun filterAppsForCurrentTab(apps: List<AppInfo>): List<AppInfo> {
        val currentTab = _state.value.currentTab
        val query = _searchQuery.value

        var filtered = when (currentTab) {
            AllAppsComposeState.TAB_PERSONAL -> apps.filter { personalMatcher.test(it) }
            AllAppsComposeState.TAB_WORK -> apps.filter { !personalMatcher.test(it) }
            else -> apps
        }

        if (query.isNotEmpty()) {
            filtered = filtered.filter { app ->
                app.title?.toString()?.contains(query, ignoreCase = true) == true
            }
        }

        return filtered.sortedBy { it.title?.toString()?.lowercase() ?: "" }
    }

    private fun buildComposeItems(apps: List<AppInfo>): List<AllAppsComposeItem> {
        if (apps.isEmpty()) {
            return if (_searchQuery.value.isNotEmpty()) {
                listOf(AllAppsComposeItem.EmptySearchResult)
            } else {
                emptyList()
            }
        }

        val items = mutableListOf<AllAppsComposeItem>()
        var lastSection: String? = null

        for (app in apps) {
            val section = app.sectionName?.toString()?.uppercase()?.firstOrNull()?.toString() ?: "#"
            if (section != lastSection) {
                items.add(AllAppsComposeItem.SectionHeader(section))
                lastSection = section
            }
            items.add(AllAppsComposeItem.AppItem(app))
        }

        return items
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _state.update { it.copy(searchQuery = query, isSearching = query.isNotEmpty()) }
        updateApps()
    }

    fun onTabSelected(tab: Int) {
        _state.update { it.copy(currentTab = tab) }
        updateApps()
    }

    fun clearSearch() {
        onSearchQueryChanged("")
    }

    fun setNumColumns(columns: Int) {
        _state.update { it.copy(numColumns = columns) }
    }

    fun setTransitionProgress(progress: Float) {
        _transitionProgress.value = progress
    }

    fun setIconSizing(iconSizePx: Int, cellWidthPx: Int, cellHeightPx: Int) {
        _state.update { it.copy(
            iconSizePx = iconSizePx,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx
        ) }
    }

    fun cleanup() {
        allAppsStore.removeUpdateListener(appsUpdateListener)
    }
}
