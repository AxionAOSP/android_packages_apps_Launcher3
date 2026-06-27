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

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconChangeTracker
import com.android.launcher3.util.DaggerSingletonObject
import javax.inject.Inject
import org.json.JSONObject

@LauncherAppSingleton
class IconOverrideRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LauncherPrefs,
    private val iconChangeTracker: IconChangeTracker,
) {
    fun get(componentName: ComponentName): IconOverride? {
        return getOverride(context, componentName)
    }

    fun hasOverride(componentName: ComponentName): Boolean {
        return get(componentName) != null
    }

    fun set(componentName: ComponentName, override: IconOverride, user: UserHandle) {
        val root = readRoot()
        root.put(
            componentName.flattenToString(),
            JSONObject()
                .put(KEY_PACK_PACKAGE, override.packPackage)
                .put(KEY_DRAWABLE_NAME, override.drawableName),
        )
        persist(root)
        notifyChanged(componentName, user)
    }

    fun clear(componentName: ComponentName, user: UserHandle) {
        val root = readRoot()
        root.remove(componentName.flattenToString())
        persist(root)
        notifyChanged(componentName, user)
    }

    private fun readRoot(): JSONObject {
        return readRoot(context)
    }

    private fun persist(root: JSONObject) {
        prefs.put(LauncherPrefsExt.ICON_OVERRIDES, root.toString())
    }

    private fun notifyChanged(componentName: ComponentName, user: UserHandle) {
        IconPackDrawableResolver.clearCache(null)
        iconChangeTracker.notifyIconChanged(componentName.packageName, user)
    }

    companion object {
        private const val KEY_PACK_PACKAGE = "packPackage"
        private const val KEY_DRAWABLE_NAME = "drawableName"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconOverrideRepository)

        @JvmStatic
        fun getOverride(context: Context, componentName: ComponentName): IconOverride? {
            val objectValue = readRoot(context).optJSONObject(componentName.flattenToString())
                ?: return null
            val packPackage = objectValue.optString(KEY_PACK_PACKAGE, "")
            val drawableName = objectValue.optString(KEY_DRAWABLE_NAME, "")
            if (packPackage.isEmpty() || drawableName.isEmpty()) return null
            return IconOverride(packPackage, drawableName)
        }

        private fun readRoot(context: Context): JSONObject {
            return runCatching { JSONObject(LauncherPrefsExt.ICON_OVERRIDES.get(context)) }
                .getOrDefault(JSONObject())
        }
    }
}
