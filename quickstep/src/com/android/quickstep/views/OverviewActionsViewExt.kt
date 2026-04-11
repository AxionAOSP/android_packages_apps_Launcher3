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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewActionsViewExt {

    private var appContext: Context? = null
    private var settingsFlow: SettingsFlow? = null
    private var state: OverviewActionsState? = null
    private var scope: CoroutineScope? = null
    private var activityManager: ActivityManager? = null
    private val memInfoReader = MemInfoReader()

    fun init(context: Context, state: OverviewActionsState) {
        val app = context.applicationContext
        this.state = state
        this.appContext = app
        settingsFlow = SettingsFlow(app.contentResolver, SettingsType.SECURE)
        activityManager = app.getSystemService(ActivityManager::class.java)
    }

    fun onAttach() {
        if (scope != null) return
        val flow = settingsFlow ?: return
        val target = state ?: return
        val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = newScope

        flow.observeBoolean(KEY_SHOW_LOCK, default = true)
            .onEach { target.showLock = it }
            .launchIn(newScope)
        flow.observeBoolean(KEY_SHOW_SCREENSHOT, default = true)
            .onEach { target.showScreenshot = it }
            .launchIn(newScope)
        flow.observeBoolean(KEY_SHOW_SELECT_TEXT, default = true)
            .onEach { target.showSelectText = it }
            .launchIn(newScope)
        flow.observeBoolean(KEY_SHOW_FREEFORM, default = true)
            .onEach { target.showFreeform = it }
            .launchIn(newScope)
        flow.observeBoolean(KEY_SHOW_CLEAR_ALL, default = true)
            .onEach { target.showClearAll = it }
            .launchIn(newScope)

        flow.observeBoolean(KEY_SHOW_MEMORY_INFO, default = true)
            .flatMapLatest { enabled ->
                if (enabled) memoryInfoFlow() else emptyFlow<String>().onStart { emit("") }
            }
            .distinctUntilChanged()
            .onEach { target.memoryInfo = it }
            .launchIn(newScope)
    }

    fun onDetach() {
        scope?.cancel()
        scope = null
    }

    private fun memoryInfoFlow() = flow {
        while (true) {
            emit(readMemoryInfo())
            delay(MEMORY_REFRESH_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.Default)

    private fun readMemoryInfo(): String {
        val ctx = appContext ?: return ""
        memInfoReader.readMemInfo()
        val total = memInfoReader.totalSize
        val kernelFree = memInfoReader.freeSize + memInfoReader.cachedSize
        val backgroundProcMem = computeBackgroundProcessMemory()
        val available = (kernelFree + backgroundProcMem).coerceAtMost(total)
        val availableStr = Formatter.formatShortFileSize(ctx, available)
        val totalStr = Formatter.formatShortFileSize(ctx, total)
        return ctx.getString(R.string.overview_memory_usage, availableStr, totalStr)
    }

    private fun computeBackgroundProcessMemory(): Long {
        val am = activityManager ?: return 0L
        val processes = try {
            am.runningAppProcesses
        } catch (e: SecurityException) {
            Log.w(TAG, "runningAppProcesses denied", e)
            return 0L
        } ?: return 0L
        if (processes.isEmpty()) return 0L

        val services = try {
            am.getRunningServices(MAX_SERVICES)
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

        private const val MEMORY_REFRESH_INTERVAL_MS = 2000L
        private const val MAX_SERVICES = 100
        private const val TAG = "OverviewActionsViewExt"
    }
}
