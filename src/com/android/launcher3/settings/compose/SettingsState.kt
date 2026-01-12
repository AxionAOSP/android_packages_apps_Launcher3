/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.launcher3.LauncherFiles
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.DisplayController
import kotlinx.coroutines.flow.*

class SettingsState(context: Context) : ViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
    )

    private val _workspaceLock = MutableStateFlow(prefs.getBoolean("pref_workspace_lock", false))
    val workspaceLock: StateFlow<Boolean> = _workspaceLock.asStateFlow()

    private val _notificationDots = MutableStateFlow(prefs.getBoolean("pref_icon_badging", true))
    val notificationDots: StateFlow<Boolean> = _notificationDots.asStateFlow()

    private val _autoAddIcons = MutableStateFlow(prefs.getBoolean("pref_add_icon_to_home", true))
    val autoAddIcons: StateFlow<Boolean> = _autoAddIcons.asStateFlow()

    private val _allowRotation = MutableStateFlow(
        prefs.getBoolean(
            RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY,
            RotationHelper.getAllowRotationDefaultValue(
                DisplayController.INSTANCE.get(context).info
            )
        )
    )
    val allowRotation: StateFlow<Boolean> = _allowRotation.asStateFlow()

    private val _showGoogleApp = MutableStateFlow(prefs.getBoolean("pref_enable_minus_one", true))
    val showGoogleApp: StateFlow<Boolean> = _showGoogleApp.asStateFlow()

    private val _swipeToSearch = MutableStateFlow(prefs.getBoolean("pref_drawer_open_keyboard", false))
    val swipeToSearch: StateFlow<Boolean> = _swipeToSearch.asStateFlow()

    private val _themedIconsEnabled = MutableStateFlow(prefs.getBoolean("themed_icons", false))
    val themedIconsEnabled: StateFlow<Boolean> = _themedIconsEnabled.asStateFlow()

    private val _themedIcons = MutableStateFlow(prefs.getBoolean("pref_allapps_themed_icons", false))
    val themedIcons: StateFlow<Boolean> = _themedIcons.asStateFlow()

    private val _doubleTapToSleep = MutableStateFlow(prefs.getBoolean("pref_sleep_gesture", true))
    val doubleTapToSleep: StateFlow<Boolean> = _doubleTapToSleep.asStateFlow()

    private val _desktopShowLabels = MutableStateFlow(prefs.getBoolean("pref_desktop_show_labels", true))
    val desktopShowLabels: StateFlow<Boolean> = _desktopShowLabels.asStateFlow()

    private val _drawerShowLabels = MutableStateFlow(prefs.getBoolean("pref_drawer_show_labels", true))
    val drawerShowLabels: StateFlow<Boolean> = _drawerShowLabels.asStateFlow()

    private val _drawerLayoutMode = MutableStateFlow(
        (prefs.getString("pref_drawer_layout_mode", "dynamic") ?: "dynamic").let {
            if (it == "default") "dynamic" else it
        }
    )
    val drawerLayoutMode: StateFlow<String> = _drawerLayoutMode.asStateFlow()

    private val _workspaceIconScale = MutableStateFlow(prefs.getFloat("pref_workspace_icon_scale", 1.0f))
    val workspaceIconScale: StateFlow<Float> = _workspaceIconScale.asStateFlow()

    private val _allAppsIconScale = MutableStateFlow(prefs.getFloat("pref_allapps_icon_scale", 1.0f))
    val allAppsIconScale: StateFlow<Float> = _allAppsIconScale.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "pref_workspace_lock" -> _workspaceLock.value = prefs.getBoolean(key, false)
            "pref_icon_badging" -> _notificationDots.value = prefs.getBoolean(key, true)
            "pref_add_icon_to_home" -> _autoAddIcons.value = prefs.getBoolean(key, true)
            RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY -> _allowRotation.value = prefs.getBoolean(key, false)
            "pref_enable_minus_one" -> _showGoogleApp.value = prefs.getBoolean(key, true)
            "pref_drawer_open_keyboard" -> _swipeToSearch.value = prefs.getBoolean(key, false)
            "themed_icons" -> _themedIconsEnabled.value = prefs.getBoolean(key, false)
            "pref_allapps_themed_icons" -> _themedIcons.value = prefs.getBoolean(key, false)
            "pref_sleep_gesture" -> _doubleTapToSleep.value = prefs.getBoolean(key, true)
            "pref_desktop_show_labels" -> _desktopShowLabels.value = prefs.getBoolean(key, true)
            "pref_drawer_show_labels" -> _drawerShowLabels.value = prefs.getBoolean(key, true)
            "pref_drawer_layout_mode" -> {
                val mode = prefs.getString(key, "dynamic") ?: "dynamic"
                _drawerLayoutMode.value = if (mode == "default") "dynamic" else mode
            }
            "pref_workspace_icon_scale" -> _workspaceIconScale.value = prefs.getFloat(key, 1.0f)
            "pref_allapps_icon_scale" -> _allAppsIconScale.value = prefs.getFloat(key, 1.0f)
        }
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun setDrawerLayoutMode(mode: String) {
        prefs.edit().putString("pref_drawer_layout_mode", mode).apply()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsState::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsState(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
