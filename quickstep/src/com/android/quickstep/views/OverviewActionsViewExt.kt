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
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ActivityManager.RunningServiceInfo
import android.app.RunningAppProcessInfo as RunningAppProcessInfoAidl
import android.content.Context
import android.text.format.Formatter
import android.util.Log
import com.android.axion.compose.preferences.SettingsFlow
import com.android.axion.compose.preferences.SettingsType
import com.android.internal.util.MemInfoReader
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.ThreadPoolContext
import com.android.launcher3.concurrent.annotations.UiContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val memInfoReader = MemInfoReader()
    private var scope: CoroutineScope = CoroutineScope(uiContext + SupervisorJob())
    private val overviewVisible = MutableStateFlow(false)

    init {
        lifecycleTracker.addCloseable(this)
    }

    fun setOverviewVisible(visible: Boolean) {
        overviewVisible.value = visible
    }

    fun onAttach(state: OverviewActionsState) {
        settingsFlow.observeBoolean(KEY_SHOW_LOCK, default = true)
            .onEach { state.showLock = it }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_SCREENSHOT, default = true)
            .onEach { state.showScreenshot = it }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_SELECT_TEXT, default = true)
            .onEach { state.showSelectText = it }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_FREEFORM, default = true)
            .onEach { state.showFreeform = it }
            .launchIn(scope)
        settingsFlow.observeBoolean(KEY_SHOW_CLEAR_ALL, default = true)
            .onEach { state.showClearAll = it }
            .launchIn(scope)
        combine(
            settingsFlow.observeBoolean(KEY_SHOW_MEMORY_INFO, default = true),
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

    fun onDetach() {
        scope.cancel()
    }

    override fun close() {
        onDetach()
    }

    private fun memoryInfoFlow() = flow {
        while (true) {
            emit(readMemoryInfo())
            delay(MEMORY_REFRESH_INTERVAL_MS)
        }
    }.flowOn(backgroundContext)

    private fun readMemoryInfo(): String {
        memInfoReader.readMemInfo()
        val total = memInfoReader.totalSize
        val kernelFree = memInfoReader.freeSize + memInfoReader.cachedSize
        val backgroundProcMem = computeBackgroundProcessMemory()
        val available = (kernelFree + backgroundProcMem).coerceAtMost(total)
        val availableStr = Formatter.formatShortFileSize(appContext, available)
        val totalStr = Formatter.formatShortFileSize(appContext, total)
        return appContext.getString(R.string.overview_memory_usage, availableStr, totalStr)
    }

    private fun computeBackgroundProcessMemory(): Long {
        val processes = try {
            activityManager.runningAppProcesses
        } catch (e: SecurityException) {
            Log.w(TAG, "runningAppProcesses denied", e)
            return 0L
        } ?: return 0L
        if (processes.isEmpty()) return 0L

        val services = try {
            activityManager.getRunningServices(MAX_SERVICES)
        } catch (e: SecurityException) {
            Log.w(TAG, "getRunningServices denied", e)
            null
        }

        val procByPid = HashMap<Int, RunningAppProcessInfo>(processes.size)
        for (p in processes) {
            procByPid[p.pid] = p
        }

        val markedPids = HashSet<Int>()

        if (services != null) {
            for (s in services) {
                if (!s.started && s.clientLabel == 0) continue
                if ((s.flags and RunningServiceInfo.FLAG_PERSISTENT_PROCESS) != 0) continue
                if (s.restarting != 0L || s.pid <= 0) continue
                markedPids.add(s.pid)
            }
        }

        for (p in processes) {
            if (isInterestingProcess(p)) {
                markedPids.add(p.pid)
            }
        }

        val visited = HashSet<Int>()
        for (p in processes) {
            if (p.pid in markedPids) continue
            visited.clear()
            var cur: RunningAppProcessInfo? = p
            while (cur != null && cur.pid !in visited) {
                visited.add(cur.pid)
                val reasonPid = cur.importanceReasonPid
                if (reasonPid == 0 || reasonPid == cur.pid) break
                if (reasonPid in markedPids) {
                    markedPids.add(p.pid)
                    break
                }
                cur = procByPid[reasonPid]
            }
        }

        val bgPids = processes
            .filter {
                it.importance >= RunningAppProcessInfo.IMPORTANCE_CACHED &&
                    it.pid !in markedPids
            }
            .map { it.pid }
        if (bgPids.isEmpty()) return 0L

        val pidArr = bgPids.toIntArray()
        return try {
            val pssArr = ActivityManager.getService().getProcessPss(pidArr)
            var sum = 0L
            for (pss in pssArr) {
                sum += pss * 1024L
            }
            sum
        } catch (e: Exception) {
            Log.w(TAG, "getProcessPss failed", e)
            0L
        }
    }

    private fun isInterestingProcess(pi: RunningAppProcessInfo): Boolean {
        if ((pi.flags and RunningAppProcessInfoAidl.FLAG_CANT_SAVE_STATE) != 0) {
            return true
        }
        return (pi.flags and RunningAppProcessInfoAidl.FLAG_PERSISTENT) == 0 &&
            pi.importance >= RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            pi.importance < RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE &&
            pi.importanceReasonCode == RunningAppProcessInfo.REASON_UNKNOWN
    }

    companion object {
        const val KEY_SHOW_LOCK = "pulse_recents_show_lock"
        const val KEY_SHOW_SCREENSHOT = "pulse_recents_show_screenshot"
        const val KEY_SHOW_SELECT_TEXT = "pulse_recents_show_select_text"
        const val KEY_SHOW_FREEFORM = "pulse_recents_show_freeform"
        const val KEY_SHOW_CLEAR_ALL = "pulse_recents_show_clear_all"
        const val KEY_SHOW_MEMORY_INFO = "pulse_recents_show_memory_info"

        private const val MEMORY_REFRESH_INTERVAL_MS = 5000L
        private const val MAX_SERVICES = 100
        private const val TAG = "OverviewActionsViewExt"
    }
}
