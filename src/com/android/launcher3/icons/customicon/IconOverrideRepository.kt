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

package com.android.launcher3.icons.customicon

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconChangeTracker
import com.android.launcher3.util.DaggerSingletonObject
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

@LauncherAppSingleton
class IconOverrideRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val iconChangeTracker: IconChangeTracker,
) {

    @Volatile private var cache: HashMap<String, IconOverride>? = null

    @Synchronized
    private fun ensureLoaded(): HashMap<String, IconOverride> {
        cache?.let { return it }
        val map = HashMap<String, IconOverride>()
        val json = Settings.Secure.getString(context.contentResolver, SETTINGS_KEY) ?: ""
        if (json.isNotEmpty()) {
            try {
                val obj = JSONObject(json)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entry = obj.getJSONObject(key)
                    map[key] = IconOverride(
                        packPackage = entry.getString(FIELD_PACK),
                        drawableName = entry.getString(FIELD_DRAWABLE),
                    )
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse icon overrides", e)
            }
        }
        cache = map
        return map
    }

    @Synchronized
    fun get(componentName: ComponentName): IconOverride? =
        ensureLoaded()[componentName.flattenToString()]

    fun set(componentName: ComponentName, override: IconOverride, user: UserHandle) {
        synchronized(this) {
            val map = ensureLoaded()
            map[componentName.flattenToString()] = override
            persist(map)
        }
        iconChangeTracker.notifyIconChanged(componentName.packageName, user)
    }

    fun clear(componentName: ComponentName, user: UserHandle) {
        val changed: Boolean
        synchronized(this) {
            val map = ensureLoaded()
            changed = map.remove(componentName.flattenToString()) != null
            if (changed) persist(map)
        }
        if (changed) iconChangeTracker.notifyIconChanged(componentName.packageName, user)
    }

    @Synchronized
    fun hasOverride(componentName: ComponentName): Boolean =
        ensureLoaded().containsKey(componentName.flattenToString())

    @Synchronized
    private fun persist(map: HashMap<String, IconOverride>) {
        val obj = JSONObject()
        map.forEach { (key, override) ->
            val entry = JSONObject()
            entry.put(FIELD_PACK, override.packPackage)
            entry.put(FIELD_DRAWABLE, override.drawableName)
            obj.put(key, entry)
        }
        Settings.Secure.putString(context.contentResolver, SETTINGS_KEY, obj.toString())
    }

    companion object {
        private const val TAG = "IconOverrideRepository"
        private const val SETTINGS_KEY = "launcher_icon_overrides"
        private const val FIELD_PACK = "pack"
        private const val FIELD_DRAWABLE = "drawable"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconOverrideRepository)
    }
}
