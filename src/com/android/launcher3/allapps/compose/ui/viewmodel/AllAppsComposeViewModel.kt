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

package com.android.launcher3.allapps.compose.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.util.Log
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.data.AllAppsRepository
import com.android.launcher3.allapps.compose.data.PinnedAppsManager
import com.android.launcher3.allapps.compose.domain.UsagePredictionInteractor
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AllAppsIconRenderState
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ItemInfoMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AllAppsComposeVM"
private const val TABLET_MAX_COLUMNS = 6
private const val ALL_APPS_EXPANDED_PROGRESS_THRESHOLD = 0.999f

class AllAppsComposeViewModel(
    allAppsStore: AllAppsStore,
    private val context: Context
) {
    private val repository = AllAppsRepository(allAppsStore, context)
    val pinnedAppsManager = PinnedAppsManager(context)
    private val usagePredictionProvider = UsagePredictionInteractor(context)
    private val personalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle())

    private var viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null
    private var predictionJob: Job? = null
    private var externalPredictions: List<ItemInfo>? = null
    private var cachedPredictions: List<ItemInfo> = emptyList()
    private var latestAppsData: AllAppsRepository.AppsData = AllAppsRepository.AppsData()
    private var latestPrefs: AllAppsRepository.DrawerPreferences = AllAppsRepository.DrawerPreferences()

    private val _state = MutableStateFlow(
        AllAppsComposeState(iconRenderState = createIconRenderState(0))
    )
    val state: StateFlow<AllAppsComposeState> = _state.asStateFlow()

    private val _allAppsExpanded = MutableStateFlow(false)
    val allAppsExpanded: StateFlow<Boolean> = _allAppsExpanded.asStateFlow()

    private val _openCounter = MutableStateFlow(0)
    val openCounter: StateFlow<Int> = _openCounter.asStateFlow()

    init {
        observeData()
    }

    private var pendingIconRefresh = false

    private fun observeData() {
        Log.d(TAG, "observeData: registering AllAppsStore listener, current scope active=${viewModelScope.isActive}")
        repository.apps.onEach { data ->
            if (data == latestAppsData) return@onEach
            Log.d(TAG, "apps update: personal=${data.personalApps.size} work=${data.workApps.size} private=${data.privateApps.size}")
            latestAppsData = data
            AllAppsIconProvider.getInstance(context).clearCache()
            pendingIconRefresh = true
            rebuildState()
        }.launchIn(viewModelScope)

        repository.preferences.onEach { prefs ->
            val iconsChanged = prefs.themedIcons != latestPrefs.themedIcons
            latestPrefs = prefs
            if (iconsChanged) {
                AllAppsIconProvider.getInstance(context).clearCache()
            }
            _state.update { it.copy(
                showLabels = prefs.showLabels,
                showPredictions = prefs.showPredictions,
                iconRenderState = createIconRenderState(it.iconRenderState.version),
            ) }
            rebuildState()
        }.launchIn(viewModelScope)

        pinnedAppsManager.pinnedApps.onEach {
            rebuildState()
        }.launchIn(viewModelScope)
    }

    private fun rebuildState() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            val data = latestAppsData
            Log.d(TAG, "rebuildState: personal=${data.personalApps.size} work=${data.workApps.size} private=${data.privateApps.size} iconSizePx=${_state.value.iconSizePx}")
            if (data.personalApps.isEmpty() && data.workApps.isEmpty() && data.privateApps.isEmpty()) {
                Log.d(TAG, "rebuildState: no apps, setting isLoading=false and returning")
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val pinnedComponents = pinnedAppsManager.pinnedApps.value
                .mapNotNull { ComponentName.unflattenFromString(it) }.toSet()

            val pinnedApps = data.personalApps.filter { it.componentName in pinnedComponents }
            val nonPinnedApps = data.personalApps.filter { it.componentName !in pinnedComponents }

            _state.update { current ->
                current.copy(
                    apps = nonPinnedApps,
                    workApps = data.workApps,
                    privateApps = data.privateApps,
                    pinnedApps = pinnedApps,
                    hasWorkApps = data.workApps.isNotEmpty() || data.isWorkPaused,
                    hasPrivateApps = data.privateApps.isNotEmpty() || data.isPrivateLocked,
                    isPrivateSpaceLocked = data.isPrivateLocked,
                    isPrivateSpaceHidden = data.isPrivateHidden,
                    isWorkProfilePaused = data.isWorkPaused,
                    isLoading = false
                )
            }

            val iconRefresh = pendingIconRefresh
            pendingIconRefresh = false
            val snapshotNonPinned = nonPinnedApps
            val snapshotPinned = pinnedApps
            val snapshotData = data

            predictionJob?.cancel()
            predictionJob = viewModelScope.launch {
                val allNonPrivate = snapshotNonPinned + snapshotPinned + snapshotData.workApps
                val predictedApps = if (!_state.value.showPredictions) {
                    emptyList()
                } else {
                    val external = externalPredictions
                    if (!external.isNullOrEmpty()) {
                        external
                    } else {
                        withContext(Dispatchers.Default) {
                            usagePredictionProvider.getPredictions(allNonPrivate, _state.value.numColumns)
                        }
                    }
                }
                cachedPredictions = predictedApps

                _state.update { current ->
                    val filteredPredictions = filterPredictionsForTab(current.currentTab, current.showPredictions)
                    val iconStateVersion = if (iconRefresh) {
                        current.iconRenderState.version + 1
                    } else {
                        current.iconRenderState.version
                    }
                    current.copy(
                        predictedApps = filteredPredictions,
                        iconRenderState = if (iconRefresh) {
                            createIconRenderState(iconStateVersion)
                        } else {
                            current.iconRenderState
                        },
                    )
                }
                preloadIconsForCurrentState(snapshotData)
            }
        }
    }

    fun updatePredictedApps(items: List<ItemInfo>) {
        externalPredictions = items.ifEmpty { null }
        rebuildState()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query, isSearching = query.isNotEmpty()) }
    }

    fun onTabSelected(tab: Int) {
        if (_state.value.currentTab == tab) return
        _state.update { current ->
            current.copy(
                currentTab = tab,
                predictedApps = filterPredictionsForTab(tab, current.showPredictions)
            )
        }
        rebuildState()
    }

    private fun filterPredictionsForTab(tab: Int, showPredictions: Boolean): List<AppInfo> {
        if (!showPredictions) return emptyList()
        val predictions = cachedPredictions.ifEmpty { externalPredictions ?: emptyList() }
        val appInfos = predictions.filterIsInstance<AppInfo>()
        if (appInfos.isEmpty()) return emptyList()
        val pinnedComponents = pinnedAppsManager.pinnedApps.value
            .mapNotNull { ComponentName.unflattenFromString(it) }.toSet()
        return when (tab) {
            AllAppsComposeState.TAB_PERSONAL -> appInfos.filter {
                personalMatcher.test(it) && it.componentName !in pinnedComponents
            }
            AllAppsComposeState.TAB_WORK -> appInfos.filter {
                !personalMatcher.test(it) && it.componentName !in pinnedComponents
            }
            else -> emptyList()
        }
    }

    fun onConfigChanged(columns: Int, iconSizePx: Int, cellWidthPx: Int, cellHeightPx: Int, isTablet: Boolean = false) {
        val effectiveColumns = if (isTablet) columns.coerceAtMost(TABLET_MAX_COLUMNS) else columns
        _state.update { it.copy(
            numColumns = effectiveColumns,
            iconSizePx = iconSizePx,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            isTablet = isTablet
        ) }
        rebuildState()
    }

    fun setTransitionProgress(progress: Float) {
        val expanded = progress >= ALL_APPS_EXPANDED_PROGRESS_THRESHOLD
        if (allAppsExpanded.value != expanded) {
            _allAppsExpanded.value = expanded
            if (expanded) {
                _openCounter.value++
            }
        }
    }

    fun onUiModeChanged() {
        Log.d(TAG, "onUiModeChanged: latestAppsData.personal=${latestAppsData.personalApps.size} isLoading=${_state.value.isLoading} iconSizePx=${_state.value.iconSizePx} scopeActive=${viewModelScope.isActive}")
        AllAppsIconProvider.getInstance(context).clearCache()
        pendingIconRefresh = true
        rebuildState()
    }

    fun onIconsChanged() {
        AllAppsIconProvider.getInstance(context).clearCache()
        _state.update {
            it.copy(iconRenderState = createIconRenderState(it.iconRenderState.version + 1))
        }
        preloadIconsForCurrentState()
    }

    fun setPrivateSpaceHidden(hidden: Boolean) {
        _state.update { it.copy(isPrivateSpaceHidden = hidden) }
    }

    fun reloadPreferences() {
        // Preferences flow handles this reactively now
    }

    fun cleanup() {
        updateJob?.cancel()
        predictionJob?.cancel()
        viewModelScope.cancel()
    }

    fun reinitialize() {
        Log.d(TAG, "reinitialize: scopeActive=${viewModelScope.isActive} latestApps=${latestAppsData.personalApps.size}")
        if (!viewModelScope.isActive) {
            viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            observeData()
        }
    }

    private fun preloadIconsForCurrentState(data: AllAppsRepository.AppsData = latestAppsData) {
        val currentIconSize = _state.value.iconSizePx
        if (currentIconSize <= 0) return
        AllAppsIconProvider.getInstance(context).preloadIcons(
            data.personalApps + data.workApps + data.privateApps,
            _state.value.iconRenderState,
            currentIconSize,
            (currentIconSize * 0.75).toInt()
        )
    }

    private fun createIconRenderState(version: Int) =
        AllAppsIconRenderState.from(
            context = context,
            themedIconsEnabled = latestPrefs.themedIcons,
            version = version,
        )

}
