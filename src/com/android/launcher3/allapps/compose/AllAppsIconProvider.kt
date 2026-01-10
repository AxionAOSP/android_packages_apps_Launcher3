/*
 * Copyright (C) 2025 AxionOS
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

package com.android.launcher3.allapps.compose

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class AllAppsIconProvider private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AllAppsIconProvider"
        
        @Volatile
        private var instance: AllAppsIconProvider? = null
        
        fun getInstance(context: Context): AllAppsIconProvider {
            return instance ?: synchronized(this) {
                instance ?: AllAppsIconProvider(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        @Composable
        fun rememberAppIcon(
            appInfo: AppInfo,
            iconSizePx: Int
        ): Drawable {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val provider = remember { getInstance(context) }
            val themed = LauncherPrefs.ALLAPPS_THEMED_ICONS.get(context)

            return remember(appInfo.componentName, iconSizePx, uiMode, themed) {
                provider.getIcon(appInfo, iconSizePx, uiMode, themed)
            }
        }
        
        @Composable
        fun rememberAppIcon(
            appInfo: AppInfo,
            iconSize: Dp
        ): Drawable {
            val density = LocalDensity.current
            val iconSizePx = with(density) { iconSize.roundToPx() }
            return rememberAppIcon(appInfo, iconSizePx)
        }
    }
    
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun preloadIcons(apps: List<AppInfo>, iconSizePx: Int, uiMode: Int, themed: Boolean) {
        scope.launch {
            val startTime = System.currentTimeMillis()
            var loadedCount = 0

            apps.forEach { app ->
                val key = getCacheKey(app, iconSizePx, uiMode, themed)
                if (!iconCache.containsKey(key)) {
                    try {
                        val icon = loadIcon(app, iconSizePx, uiMode, themed)
                        iconCache[key] = icon
                        loadedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to preload icon for ${app.componentName}", e)
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Preloaded $loadedCount icons in ${elapsed}ms (cache size: ${iconCache.size})")
        }
    }
    
    fun preloadIcons(apps: List<AppInfo>, uiMode: Int, themed: Boolean, vararg iconSizesPx: Int) {
        iconSizesPx.forEach { size ->
            preloadIcons(apps, size, uiMode, themed)
        }
    }
    
    fun getIcon(appInfo: AppInfo, iconSizePx: Int, uiMode: Int, themed: Boolean): Drawable {
        val key = getCacheKey(appInfo, iconSizePx, uiMode, themed)
        return iconCache.getOrPut(key) {
            loadIcon(appInfo, iconSizePx, uiMode, themed)
        }
    }
    
    fun clearCache() {
        iconCache.clear()
        Log.d(TAG, "Icon cache cleared")
    }
    
    fun invalidate(appInfo: AppInfo) {
        val prefix = appInfo.componentName?.flattenToString() ?: return
        iconCache.keys.removeAll { it.startsWith(prefix) }
    }
    
    private fun getCacheKey(appInfo: AppInfo, sizePx: Int, uiMode: Int, themed: Boolean): String {
        return "${appInfo.componentName?.flattenToString()}_${sizePx}_${uiMode}_${themed}"
    }
    
    private fun loadIcon(appInfo: AppInfo, iconSizePx: Int, uiMode: Int, themed: Boolean): Drawable {
        val flags = if (themed) BitmapInfo.FLAG_THEMED else 0
        return appInfo.newIcon(context, flags).apply {
            setBounds(0, 0, iconSizePx, iconSizePx)
        }
    }
}
