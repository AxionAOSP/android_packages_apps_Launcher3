package com.android.launcher3.allapps.compose.data

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.LauncherFiles
import kotlinx.coroutines.flow.*
import org.json.JSONArray

class PinnedAppsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
    )
    
    private val _pinnedApps = MutableStateFlow(loadPinnedApps())
    val pinnedApps: StateFlow<Set<String>> = _pinnedApps.asStateFlow()
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_KEY) {
            _pinnedApps.value = loadPinnedApps()
        }
    }
    
    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }
    
    private fun loadPinnedApps(): Set<String> {
        val json = prefs.getString(PREF_KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    private fun savePinnedApps(apps: Set<String>) {
        val array = JSONArray()
        apps.forEach { array.put(it) }
        prefs.edit().putString(PREF_KEY, array.toString()).apply()
    }
    
    fun isPinned(componentName: String): Boolean {
        return componentName in _pinnedApps.value
    }
    
    fun pin(componentName: String) {
        val updated = _pinnedApps.value + componentName
        savePinnedApps(updated)
        _pinnedApps.value = updated
    }
    
    fun unpin(componentName: String) {
        val updated = _pinnedApps.value - componentName
        savePinnedApps(updated)
        _pinnedApps.value = updated
    }
    
    fun togglePin(componentName: String) {
        if (isPinned(componentName)) {
            unpin(componentName)
        } else {
            pin(componentName)
        }
    }
    
    companion object {
        private const val PREF_KEY = "pinned_apps"
    }
}

