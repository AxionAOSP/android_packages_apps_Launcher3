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

import android.content.Context
import com.android.axion.compose.preferences.SettingsFlow
import com.android.axion.compose.preferences.SettingsType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class OverviewActionsViewExt {

    private var settingsFlow: SettingsFlow? = null
    private var state: OverviewActionsState? = null
    private var scope: CoroutineScope? = null

    fun init(context: Context, state: OverviewActionsState) {
        this.state = state
        settingsFlow = SettingsFlow(context.contentResolver, SettingsType.SECURE)
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
    }

    fun onDetach() {
        scope?.cancel()
        scope = null
    }

    companion object {
        const val KEY_SHOW_LOCK = "pulse_recents_show_lock"
        const val KEY_SHOW_SCREENSHOT = "pulse_recents_show_screenshot"
        const val KEY_SHOW_SELECT_TEXT = "pulse_recents_show_select_text"
        const val KEY_SHOW_FREEFORM = "pulse_recents_show_freeform"
        const val KEY_SHOW_CLEAR_ALL = "pulse_recents_show_clear_all"
    }
}
