/*
 * Copyright (C) 2025-2026 AxionOS
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
package com.android.quickstep.views

import android.app.ActivityManager
import android.content.Context
import android.text.format.Formatter
import com.android.axion.compose.preferences.SettingsFlow
import com.android.axion.compose.preferences.SettingsType
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.ThreadPoolContext
import com.android.launcher3.concurrent.annotations.UiContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.OverviewScrimUtils
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
@LauncherAppSingleton
class OverviewActionsViewExt @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @UiContext private val uiContext: CoroutineContext,
    @ThreadPoolContext private val backgroundContext: CoroutineContext,
    lifecycleTracker: DaggerSingletonTracker,
) : SafeCloseable {

    private val activityManager = appContext.getSystemService(ActivityManager::class.java)!!
    private val settingsFlow = SettingsFlow(appContext.contentResolver, SettingsType.SECURE)
    private val parentJob = SupervisorJob()
    private val bindings = mutableMapOf<OverviewActionsState, Binding>()

    init {
        lifecycleTracker.addCloseable(this)
    }

    fun setOverviewVisible(state: OverviewActionsState, visible: Boolean) {
        bindings[state]?.overviewVisible?.value = visible
    }

    fun onAttach(state: OverviewActionsState, visible: Boolean) {
        onDetach(state)
        val overviewVisible = MutableStateFlow(visible)
        val job = SupervisorJob(parentJob)
        val scope = CoroutineScope(uiContext + job)
        bindings[state] = Binding(overviewVisible, job)

        settingsFlow.observeBoolean(KEY_SHOW_LOCK, default = true)
            .onEach {
                state.showLock = it
                state.updateSettingsActionsAvailable()
            }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_SCREENSHOT, default = true)
            .onEach {
                state.showScreenshot = it
                state.updateSettingsActionsAvailable()
            }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_SELECT_TEXT, default = true)
            .onEach {
                state.showSelectText = it
                state.updateSettingsActionsAvailable()
            }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_FREEFORM, default = true)
            .onEach {
                state.showFreeform = it
                state.updateSettingsActionsAvailable()
            }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_CLEAR_ALL, default = true)
            .onEach {
                state.showClearAll = it
                state.updateSettingsActionsAvailable()
            }
            .launchIn(scope)
        val showMemoryInfoFlow = settingsFlow.observeBoolean(KEY_SHOW_MEMORY_INFO, default = true)
            .onEach {
                state.showMemoryInfo = it
                state.updateSettingsActionsAvailable()
            }
        settingsFlow.observeInt(
            OverviewScrimUtils.RECENTS_OVERVIEW_SCRIM_OPACITY,
            default = OverviewScrimUtils.DEFAULT_RECENTS_OVERVIEW_SCRIM_OPACITY,
        )
            .distinctUntilChanged()
            .onEach {
                state.memoryInfoUseWhiteText =
                    it.coerceIn(0, 100) <= LOW_SCRIM_WHITE_TEXT_OPACITY
            }
            .launchIn(scope)
        combine(
            showMemoryInfoFlow,
            overviewVisible
        ) { settingEnabled, visible -> settingEnabled && visible }
            .distinctUntilChanged()
            .flatMapLatest { active ->
                if (active) memoryInfoFlow() else emptyFlow<String>().onStart { emit("") }
            }
            .distinctUntilChanged()
            .onEach { state.memoryInfo = it }
            .launchIn(scope)
    }

    fun onDetach(state: OverviewActionsState) {
        bindings.remove(state)?.job?.cancel()
        state.memoryInfo = ""
    }

    override fun close() {
        bindings.values.forEach { it.job.cancel() }
        bindings.clear()
        parentJob.cancel()
    }

    private fun memoryInfoFlow() = flow {
        while (true) {
            emit(readMemoryInfo())
            delay(MEMORY_REFRESH_INTERVAL_MS)
        }
    }.flowOn(backgroundContext)

    private fun readMemoryInfo(): String {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val total = memoryInfo.totalMem
        val available = memoryInfo.availMem.coerceAtMost(total)
        val availableStr = Formatter.formatShortFileSize(appContext, available)
        val totalStr = Formatter.formatShortFileSize(appContext, total)
        return appContext.getString(R.string.overview_memory_usage, availableStr, totalStr)
    }

    private data class Binding(
        val overviewVisible: MutableStateFlow<Boolean>,
        val job: Job,
    )

    companion object {
        const val KEY_SHOW_LOCK = "pulse_recents_show_lock"
        const val KEY_SHOW_SCREENSHOT = "pulse_recents_show_screenshot"
        const val KEY_SHOW_SELECT_TEXT = "pulse_recents_show_select_text"
        const val KEY_SHOW_FREEFORM = "pulse_recents_show_freeform"
        const val KEY_SHOW_CLEAR_ALL = "pulse_recents_show_clear_all"
        const val KEY_SHOW_MEMORY_INFO = "pulse_recents_show_memory_info"

        private const val MEMORY_REFRESH_INTERVAL_MS = 5000L
        private const val LOW_SCRIM_WHITE_TEXT_OPACITY = 40
    }
}
