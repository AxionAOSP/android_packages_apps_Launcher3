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
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import com.android.axion.util.PackageManagerUtils
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

data class IconPackDrawableInfo(
    val packPackage: String,
    val drawableName: String,
    val label: String,
)

object IconPackEnumerator {
    private const val TAG = "IconPackEnumerator"
    private const val XML_APPFILTER = "appfilter"
    private const val TAG_ITEM = "item"
    private const val ATTR_COMPONENT = "component"
    private const val ATTR_DRAWABLE = "drawable"

    private val ICON_PACK_ACTIONS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME",
    )

    suspend fun listInstalledIconPacks(context: Context): List<IconPackInfo> =
        withContext(Dispatchers.IO) { listInstalledIconPacksBlocking(context) }

    fun listInstalledIconPacksBlocking(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packages = LinkedHashSet<String>()
        ICON_PACK_ACTIONS.forEach { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
                .forEach { packages.add(it.activityInfo.packageName) }
        }
        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { app ->
            if (hasAppFilter(pm, app.packageName)) {
                packages.add(app.packageName)
            }
        }
        return packages.mapNotNull { packageName ->
            val appInfo = PackageManagerUtils.getApplicationInfo(context, packageName)
                ?: return@mapNotNull null
            IconPackInfo(
                packageName = packageName,
                label = PackageManagerUtils.loadApplicationLabel(pm, appInfo).toString(),
                icon = PackageManagerUtils.loadApplicationIcon(pm, appInfo),
            )
        }.sortedBy { it.label.lowercase() }
    }

    suspend fun listDrawables(context: Context, packPackage: String): List<IconPackDrawableInfo> =
        withContext(Dispatchers.IO) {
            val packRes = getResources(context, packPackage) ?: return@withContext emptyList()
            parseAppFilter(packRes, packPackage)
        }

    fun parseComponentMap(context: Context, packPackage: String): Map<ComponentName, String> {
        val packRes = getResources(context, packPackage) ?: return emptyMap()
        val resId = packRes.getIdentifier(XML_APPFILTER, "xml", packPackage)
        if (resId == 0) return emptyMap()
        val result = LinkedHashMap<ComponentName, String>()
        try {
            val parser = packRes.getXml(resId)
            var type: Int
            while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || parser.name != TAG_ITEM) continue
                val component = parser.getAttributeValue(null, ATTR_COMPONENT) ?: continue
                val drawable = parser.getAttributeValue(null, ATTR_DRAWABLE) ?: continue
                parseComponent(component)?.let { result.putIfAbsent(it, drawable) }
            }
            parser.close()
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "Failed to parse icon pack $packPackage", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read icon pack $packPackage", e)
        }
        return result
    }

    fun getResources(context: Context, packPackage: String): Resources? {
        if (packPackage.isEmpty()) return null
        return try {
            context.packageManager.getResourcesForApplication(packPackage)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun parseAppFilter(
        packRes: Resources,
        packPackage: String,
    ): List<IconPackDrawableInfo> {
        val resId = packRes.getIdentifier(XML_APPFILTER, "xml", packPackage)
        if (resId == 0) return emptyList()
        val result = mutableListOf<IconPackDrawableInfo>()
        val seen = HashSet<String>()
        try {
            val parser = packRes.getXml(resId)
            var type: Int
            while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || parser.name != TAG_ITEM) continue
                val drawableName = parser.getAttributeValue(null, ATTR_DRAWABLE) ?: continue
                if (!seen.add(drawableName)) continue
                val component = parser.getAttributeValue(null, ATTR_COMPONENT).orEmpty()
                val label = parseComponent(component)?.className
                    ?.substringAfterLast('.')
                    ?.ifEmpty { drawableName } ?: drawableName
                result.add(
                    IconPackDrawableInfo(
                        packPackage = packPackage,
                        drawableName = drawableName,
                        label = label,
                    )
                )
            }
            parser.close()
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "Failed to parse appfilter for $packPackage", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read appfilter for $packPackage", e)
        }
        return result.sortedBy { it.label.lowercase() }
    }

    private fun hasAppFilter(pm: PackageManager, packageName: String): Boolean {
        return try {
            val res = pm.getResourcesForApplication(packageName)
            res.getIdentifier(XML_APPFILTER, "xml", packageName) != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun parseComponent(component: String): ComponentName? {
        if (!component.startsWith("ComponentInfo{") || !component.endsWith("}")) return null
        val flattened = component.substring("ComponentInfo{".length, component.length - 1)
        return ComponentName.unflattenFromString(flattened)
    }
}
