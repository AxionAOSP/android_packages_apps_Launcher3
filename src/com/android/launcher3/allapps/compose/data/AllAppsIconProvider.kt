package com.android.launcher3.allapps.compose.data

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.android.launcher3.allapps.compose.ui.LocalIconConfig
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
            iconSizePx: Int,
            uiMode: Int = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ): Drawable {
            val context = LocalContext.current
            val provider = remember { getInstance(context) }
            val iconConfig = LocalIconConfig.current

            return remember(appInfo.componentName, appInfo.user, iconSizePx, uiMode, iconConfig) {
                provider.getIcon(appInfo, iconSizePx, uiMode, iconConfig.themed)
            }
        }

        @Composable
        fun rememberAppIcon(
            appInfo: AppInfo,
            iconSize: Dp,
            uiMode: Int = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ): Drawable {
            val density = LocalDensity.current
            val iconSizePx = with(density) { iconSize.roundToPx() }
            return rememberAppIcon(appInfo, iconSizePx, uiMode)
        }
    }
    
    private val _cacheVersion = mutableIntStateOf(0)
    val cacheVersion: Int by _cacheVersion

    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preloadJob: Job? = null

    fun preloadIcons(apps: List<AppInfo>, iconSizePx: Int, uiMode: Int, themed: Boolean) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val startTime = System.currentTimeMillis()
            var loadedCount = 0

            apps.forEach { app ->
                ensureActive()
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
        preloadJob?.cancel()
        preloadJob = scope.launch {
            iconSizesPx.forEach { size ->
                ensureActive()
                val startTime = System.currentTimeMillis()
                var loadedCount = 0

                apps.forEach { app ->
                    ensureActive()
                    val key = getCacheKey(app, size, uiMode, themed)
                    if (!iconCache.containsKey(key)) {
                        try {
                            val icon = loadIcon(app, size, uiMode, themed)
                            iconCache[key] = icon
                            loadedCount++
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to preload icon for ${app.componentName}", e)
                        }
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Preloaded $loadedCount icons at ${size}px in ${elapsed}ms (cache size: ${iconCache.size})")
            }
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
        _cacheVersion.intValue++
    }
    
    fun invalidate(appInfo: AppInfo) {
        val prefix = "${appInfo.componentName?.flattenToString()}_${appInfo.user.hashCode()}"
        iconCache.keys.removeAll { it.startsWith(prefix) }
        _cacheVersion.intValue++
    }

    private fun getCacheKey(appInfo: AppInfo, sizePx: Int, uiMode: Int, themed: Boolean): String {
        return "${appInfo.componentName?.flattenToString()}_${appInfo.user.hashCode()}_${sizePx}_${uiMode}_${themed}"
    }
    
    private fun loadIcon(appInfo: AppInfo, iconSizePx: Int, uiMode: Int, themed: Boolean): Drawable {
        val flags = if (themed) BitmapInfo.FLAG_THEMED else 0
        return appInfo.newIcon(context, flags).apply {
            setBounds(0, 0, iconSizePx, iconSizePx)
        }
    }
}

