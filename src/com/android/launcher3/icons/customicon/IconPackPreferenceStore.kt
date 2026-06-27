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
package com.android.launcher3.icons.customicon

import android.content.Context
import android.provider.Settings
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import org.json.JSONObject

object IconPackPreferenceStore {
    private const val KEY_THEME_ENGINE_DATA = "theme_engine_data"
    private const val THEME_ROOT = "themes"
    private const val CATEGORY_ICON_PACK = "icon_pack"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PACKAGE_NAME = "packageName"

    @JvmStatic
    fun getIconPackPackage(context: Context): String {
        readThemeEngineIconPack(context)?.let { return it }
        return LauncherPrefsExt.ICON_PACK_PACKAGE.get(context)
    }

    @JvmStatic
    fun setIconPackPackage(context: Context, prefs: LauncherPrefs, packageName: String) {
        prefs.put(LauncherPrefsExt.ICON_PACK_PACKAGE, packageName)
        writeThemeEngineIconPack(context, packageName)
        IconPackDrawableResolver.clearCache(packageName.takeIf { it.isNotEmpty() })
    }

    @JvmStatic
    fun getThemedIconPackPackage(context: Context): String {
        return LauncherPrefsExt.THEMED_ICON_PACK.get(context)
    }

    @JvmStatic
    fun hasActiveIconPack(context: Context): Boolean {
        return getIconPackPackage(context).isNotEmpty()
    }

    @JvmStatic
    fun hasAnyIconCustomization(context: Context): Boolean {
        return hasActiveIconPack(context) || hasIconOverrides(context)
    }

    @JvmStatic
    fun hasIconOverrides(context: Context): Boolean {
        val overrides = LauncherPrefsExt.ICON_OVERRIDES.get(context)
        return overrides.length > 2
    }

    private fun readThemeEngineIconPack(context: Context): String? {
        val json = Settings.Secure.getString(context.contentResolver, KEY_THEME_ENGINE_DATA)
            ?: return null
        return runCatching {
            val config = JSONObject(json)
            val themes = config.optJSONObject(THEME_ROOT) ?: return null
            val iconPack = themes.optJSONObject(CATEGORY_ICON_PACK) ?: return null
            val enabled = iconPack.optBoolean(KEY_ENABLED, false)
            val packageName = iconPack.optString(KEY_PACKAGE_NAME, "")
            if (enabled && packageName.isNotEmpty()) packageName else ""
        }.getOrNull()
    }

    private fun writeThemeEngineIconPack(context: Context, packageName: String) {
        val resolver = context.contentResolver
        val json = Settings.Secure.getString(resolver, KEY_THEME_ENGINE_DATA)
        val config = runCatching {
            if (json.isNullOrEmpty()) JSONObject() else JSONObject(json)
        }.getOrDefault(JSONObject())
        val themes = config.optJSONObject(THEME_ROOT) ?: JSONObject()
        val iconPack = JSONObject()
            .put(KEY_ENABLED, packageName.isNotEmpty())
            .put(KEY_PACKAGE_NAME, packageName)
        themes.put(CATEGORY_ICON_PACK, iconPack)
        config.put(THEME_ROOT, themes)
        Settings.Secure.putString(resolver, KEY_THEME_ENGINE_DATA, config.toString())
    }
}
