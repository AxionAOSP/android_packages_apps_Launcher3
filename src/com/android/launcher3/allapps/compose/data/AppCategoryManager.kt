package com.android.launcher3.allapps.compose.data

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.LauncherFiles
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

class AppCategoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
    )

    private val _overrides = MutableStateFlow(loadOverrides())
    val overrides: StateFlow<Map<String, Int>> = _overrides.asStateFlow()

    private val _customCategories = MutableStateFlow(loadCustomCategories())
    val customCategories: StateFlow<Map<Int, String>> = _customCategories.asStateFlow()

    private val _categoryOrder = MutableStateFlow(loadCategoryOrder())
    val categoryOrder: StateFlow<List<Int>> = _categoryOrder.asStateFlow()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_OVERRIDES -> _overrides.value = loadOverrides()
            PREF_CUSTOM_CATEGORIES -> _customCategories.value = loadCustomCategories()
            PREF_CATEGORY_ORDER -> _categoryOrder.value = loadCategoryOrder()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun loadOverrides(): Map<String, Int> {
        val json = prefs.getString(PREF_OVERRIDES, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.getInt(key)) }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveOverrides(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(PREF_OVERRIDES, obj.toString()).apply()
    }

    fun getOverride(componentName: String): Int? = _overrides.value[componentName]

    fun setOverride(componentName: String, categoryId: Int) {
        val updated = _overrides.value.toMutableMap()
        updated[componentName] = categoryId
        saveOverrides(updated)
        _overrides.value = updated
    }

    fun setOverrides(components: Set<String>, categoryId: Int) {
        val updated = _overrides.value.toMutableMap()
        components.forEach { updated[it] = categoryId }
        saveOverrides(updated)
        _overrides.value = updated
    }

    fun removeOverride(componentName: String) {
        val updated = _overrides.value.toMutableMap()
        updated.remove(componentName)
        saveOverrides(updated)
        _overrides.value = updated
    }

    fun removeOverrides(components: Set<String>) {
        val updated = _overrides.value.toMutableMap()
        components.forEach { updated.remove(it) }
        saveOverrides(updated)
        _overrides.value = updated
    }

    private fun loadCustomCategories(): Map<Int, String> {
        val json = prefs.getString(PREF_CUSTOM_CATEGORIES, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key.toInt(), obj.getString(key)) }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveCustomCategories(map: Map<Int, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString(PREF_CUSTOM_CATEGORIES, obj.toString()).apply()
    }

    fun createCategory(name: String): Int {
        val existing = _customCategories.value
        val newId = (existing.keys.maxOrNull() ?: (CUSTOM_ID_START - 1)) + 1
        val updated = existing.toMutableMap()
        updated[newId] = name
        saveCustomCategories(updated)
        _customCategories.value = updated
        return newId
    }

    fun deleteCategory(categoryId: Int) {
        val updatedCats = _customCategories.value.toMutableMap()
        updatedCats.remove(categoryId)
        saveCustomCategories(updatedCats)
        _customCategories.value = updatedCats

        val updatedOverrides = _overrides.value.toMutableMap()
        updatedOverrides.entries.removeAll { it.value == categoryId }
        saveOverrides(updatedOverrides)
        _overrides.value = updatedOverrides
    }

    fun renameCategory(categoryId: Int, newName: String) {
        val updated = _customCategories.value.toMutableMap()
        updated[categoryId] = newName
        saveCustomCategories(updated)
        _customCategories.value = updated
    }

    private fun loadCategoryOrder(): List<Int> {
        val json = prefs.getString(PREF_CATEGORY_ORDER, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getInt(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCategoryOrder(order: List<Int>) {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        prefs.edit().putString(PREF_CATEGORY_ORDER, arr.toString()).apply()
    }

    fun setCategoryOrder(order: List<Int>) {
        saveCategoryOrder(order)
        _categoryOrder.value = order
    }

    companion object {
        private const val PREF_OVERRIDES = "app_category_overrides"
        private const val PREF_CUSTOM_CATEGORIES = "custom_app_categories"
        private const val PREF_CATEGORY_ORDER = "category_order"
        const val CUSTOM_ID_START = 100
    }
}

