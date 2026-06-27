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
import android.content.res.Resources
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

object IconPackDrawableResolver {
    private val componentMaps = ConcurrentHashMap<String, Map<ComponentName, String>>()

    @JvmStatic
    fun loadForComponent(
        context: Context,
        packPackage: String?,
        componentName: ComponentName,
        density: Int,
    ): Drawable? {
        if (packPackage.isNullOrEmpty()) return null
        val drawableName = getDrawableName(context, packPackage, componentName) ?: return null
        return loadDrawable(context, packPackage, drawableName, density)
    }

    @JvmStatic
    fun loadDrawable(
        context: Context,
        packPackage: String,
        drawableName: String,
        density: Int,
    ): Drawable? {
        val res = IconPackEnumerator.getResources(context, packPackage) ?: return null
        val resId = getDrawableId(res, packPackage, drawableName)
        if (resId == 0) return null
        return try {
            if (density > 0) res.getDrawableForDensity(resId, density, null)
            else res.getDrawable(resId, null)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    @JvmStatic
    fun getDrawableId(res: Resources, packPackage: String, drawableName: String): Int {
        val drawable = res.getIdentifier(drawableName, "drawable", packPackage)
        if (drawable != 0) return drawable
        return res.getIdentifier(drawableName, "mipmap", packPackage)
    }

    @JvmStatic
    fun clearCache(packPackage: String?) {
        if (packPackage.isNullOrEmpty()) {
            componentMaps.clear()
        } else {
            componentMaps.remove(packPackage)
        }
    }

    private fun getDrawableName(
        context: Context,
        packPackage: String,
        componentName: ComponentName,
    ): String? {
        val map = componentMaps.computeIfAbsent(packPackage) {
            IconPackEnumerator.parseComponentMap(context, packPackage)
        }
        return map[componentName]
    }
}
