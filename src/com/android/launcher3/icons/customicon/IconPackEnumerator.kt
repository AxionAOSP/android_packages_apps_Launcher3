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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

data class IconPackDrawable(
    val packPackage: String,
    val drawableName: String,
    val label: String,
)

object IconPackEnumerator {

    private const val TAG = "IconPackEnumerator"

    private val ICON_PACK_ACTIONS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME",
    )

    suspend fun listInstalledIconPacks(context: Context): List<IconPackInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val seen = LinkedHashSet<String>()
            val result = mutableListOf<IconPackInfo>()
            for (action in ICON_PACK_ACTIONS) {
                val intent = Intent(action)
                val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo.packageName
                    if (seen.add(pkg)) {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            result.add(
                                IconPackInfo(
                                    packageName = pkg,
                                    label = pm.getApplicationLabel(appInfo).toString(),
                                    icon = pm.getApplicationIcon(appInfo),
                                )
                            )
                        } catch (_: PackageManager.NameNotFoundException) {}
                    }
                }
            }
            result
        }

    suspend fun listDrawables(context: Context, packPackage: String): List<IconPackDrawable> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                val packRes = pm.getResourcesForApplication(packPackage)
                parseAppFilter(packRes, packPackage)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Icon pack not found: $packPackage")
                emptyList()
            }
        }

    private fun parseAppFilter(packRes: Resources, packPackage: String): List<IconPackDrawable> {
        val resId = packRes.getIdentifier("appfilter", "xml", packPackage)
        if (resId == 0) return emptyList()

        val result = mutableListOf<IconPackDrawable>()
        val seen = HashSet<String>()
        try {
            val parser = packRes.getXml(resId)
            var type: Int
            while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) continue
                if (parser.name != "item") continue
                val drawableName = parser.getAttributeValue(null, "drawable") ?: continue
                if (!seen.add(drawableName)) continue
                val component = parser.getAttributeValue(null, "component") ?: ""
                val label = extractLabel(component).ifEmpty { drawableName }
                result.add(
                    IconPackDrawable(
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
        return result
    }

    private fun extractLabel(component: String): String {
        if (!component.startsWith("ComponentInfo{")) return ""
        val inner = component.removePrefix("ComponentInfo{").removeSuffix("}")
        val slash = inner.indexOf('/')
        return if (slash > 0) inner.substring(slash + 1).substringAfterLast('.') else ""
    }
}
