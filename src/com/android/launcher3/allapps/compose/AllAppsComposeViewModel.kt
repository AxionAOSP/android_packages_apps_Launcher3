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

import android.content.ComponentName

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import com.android.launcher3.LauncherFiles
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ItemInfoMatcher
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.search.StringMatcherUtility
import com.android.launcher3.views.ActivityContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AllAppsComposeViewModel<T>(
    private val allAppsStore: AllAppsStore<T>,
    private val context: Context
) where T : Context, T : ActivityContext {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
    )
    
    val pinnedAppsManager = PinnedAppsManager(context)
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateAppsJob: Job? = null

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

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pref_drawer_show_labels") {
            _state.update { it.copy(showLabels = prefs.getBoolean(key, true)) }
        }
    }

    fun reloadPreferences() {
        val showLabels = prefs.getBoolean("pref_drawer_show_labels", true)
        _state.update { it.copy(showLabels = showLabels) }
    }

    private fun observePreferences() {
        reloadPreferences()
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
    
    private fun observePinnedApps() {
        pinnedAppsManager.pinnedApps.onEach { pinnedSet ->
            updateApps()
        }.launchIn(viewModelScope)
    }

    private fun updateApps() {
        updateAppsJob?.cancel()
        updateAppsJob = viewModelScope.launch {
            val allApps = allAppsStore.apps?.toList() ?: emptyList()
            val pinnedSet = pinnedAppsManager.pinnedApps.value
            val pinnedComponents = pinnedSet.mapNotNull { ComponentName.unflattenFromString(it) }.toSet()
            
            if (allApps.isEmpty() && _state.value.apps.isEmpty()) {
                _state.update { it.copy(isLoading = true) }
                return@launch
            }
            
            val result = withContext(Dispatchers.Default) {
                processApps(allApps, pinnedComponents)
            }
            
            _state.update { currentState ->
                currentState.copy(
                    apps = result.nonPinnedApps,
                    workApps = result.workApps,
                    privateApps = result.privateApps,
                    pinnedApps = result.pinnedApps,
                    hasWorkApps = result.workApps.isNotEmpty(),
                    hasPrivateApps = result.privateApps.isNotEmpty() || result.isPrivateLocked,
                    isPrivateSpaceLocked = result.isPrivateLocked,
                    isPrivateSpaceHidden = result.isPrivateHidden,
                    isLoading = false,
                    filteredPredictedApps = if (currentState.currentTab == AllAppsComposeState.TAB_PERSONAL) {
                        currentState.predictedApps.filter { 
                            personalMatcher.test(it) && it.componentName !in pinnedComponents 
                        }
                    } else if (currentState.currentTab == AllAppsComposeState.TAB_WORK) {
                        currentState.predictedApps.filter { 
                            !personalMatcher.test(it) && it.componentName !in pinnedComponents 
                        }
                    } else {
                        emptyList()
                    }
                )
            }
        }
    }
    
    private data class ProcessedApps(
        val nonPinnedApps: List<AppInfo>,
        val pinnedApps: List<AppInfo>,
        val workApps: List<AppInfo>,
        val privateApps: List<AppInfo>,
        val isPrivateLocked: Boolean,
        val isPrivateHidden: Boolean
    )
    
    private fun processApps(allApps: List<AppInfo>, pinnedComponents: Set<ComponentName>): ProcessedApps {
        val userCache = UserCache.getInstance(context)
        
        val personalApps = mutableListOf<AppInfo>()
        val workApps = mutableListOf<AppInfo>()
        val privateApps = mutableListOf<AppInfo>()
        
        for (app in allApps) {
            val userInfo = userCache.getUserInfo(app.user)
            when {
                userInfo.isPrivate() -> privateApps.add(app)
                personalMatcher.test(app) -> personalApps.add(app)
                else -> workApps.add(app)
            }
        }
        
        val filteredApps = filterAppsForCurrentTab(personalApps)
        
        val pinnedApps = filteredApps.filter { 
            it.componentName in pinnedComponents 
        }
        val nonPinnedApps = filteredApps.filter { 
            it.componentName !in pinnedComponents 
        }
        
        val isPrivateLocked = allAppsStore.hasModelFlag(
            com.android.launcher3.model.data.AppsListData.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
        )
        
        val isPrivateHidden = isPrivateLocked && com.android.launcher3.util.SettingsCache.INSTANCE
            .get(context)
            .getValue(com.android.launcher3.util.SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI, 0)
            
        return ProcessedApps(
            nonPinnedApps = nonPinnedApps,
            pinnedApps = pinnedApps,
            workApps = workApps.sortedBy { it.title?.toString()?.lowercase() ?: "" },
            privateApps = privateApps.sortedBy { it.title?.toString()?.lowercase() ?: "" },
            isPrivateLocked = isPrivateLocked,
            isPrivateHidden = isPrivateHidden
        )
    }



    fun updatePredictedApps(items: List<com.android.launcher3.model.data.ItemInfo>) {
        val predictedApps = items.filterIsInstance<com.android.launcher3.model.data.WorkspaceItemInfo>()
            .mapNotNull { wsItem ->
                allAppsStore.getApp(ComponentKey(wsItem.targetComponent, wsItem.user))
            }
        
        _state.update { it.copy(predictedApps = predictedApps) }
        updateApps()
    }

    private fun filterAppsForCurrentTab(apps: List<AppInfo>): List<AppInfo> {
        val currentTab = _state.value.currentTab
        val query = _searchQuery.value
        val predicted = _state.value.predictedApps
        
        val filteredPredicted = when (currentTab) {
            AllAppsComposeState.TAB_PERSONAL -> predicted.filter { personalMatcher.test(it) }
            AllAppsComposeState.TAB_WORK -> predicted.filter { !personalMatcher.test(it) }
            else -> emptyList()
        }
        
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

    fun setPrivateSpaceHidden(hidden: Boolean) {
        _state.update { it.copy(isPrivateSpaceHidden = hidden) }
    }

    fun cleanup() {
        allAppsStore.removeUpdateListener(appsUpdateListener)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        viewModelScope.cancel()
    }
}
