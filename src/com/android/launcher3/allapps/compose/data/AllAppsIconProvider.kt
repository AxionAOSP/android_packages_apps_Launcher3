package com.android.launcher3.allapps.compose.data

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.android.launcher3.allapps.compose.shared.model.AllAppsIconRenderState
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
            iconSizePx: Int
        ): Drawable {
            val context = LocalContext.current
            val provider = remember { getInstance(context) }
            val iconConfig = LocalIconConfig.current

            return remember(appInfo.componentName, appInfo.user, iconSizePx, iconConfig) {
                provider.getIcon(appInfo, iconSizePx, iconConfig)
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
    
    private val _cacheVersion = mutableIntStateOf(0)
    val cacheVersion: Int by _cacheVersion

    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preloadJob: Job? = null

    fun preloadIcons(
        apps: List<AppInfo>,
        iconState: AllAppsIconRenderState,
        vararg iconSizesPx: Int
    ) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            iconSizesPx.forEach { size ->
                ensureActive()
                val startTime = System.currentTimeMillis()
                var loadedCount = 0

                apps.forEach { app ->
                    ensureActive()
                    val key = getCacheKey(app, size, iconState)
                    if (!iconCache.containsKey(key)) {
                        try {
                            val icon = loadIcon(app, size, iconState)
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
    
    fun getIcon(
        appInfo: AppInfo,
        iconSizePx: Int,
        iconState: AllAppsIconRenderState
    ): Drawable {
        val key = getCacheKey(appInfo, iconSizePx, iconState)
        return iconCache.getOrPut(key) {
            loadIcon(appInfo, iconSizePx, iconState)
        }
    }
    
    fun clearCache() {
        preloadJob?.cancel()
        iconCache.clear()
        _cacheVersion.intValue++
    }
    
    fun invalidate(appInfo: AppInfo) {
        val prefix = "${appInfo.componentName?.flattenToString()}_${appInfo.user.hashCode()}"
        iconCache.keys.removeAll { it.startsWith(prefix) }
        _cacheVersion.intValue++
    }

    private fun getCacheKey(
        appInfo: AppInfo,
        sizePx: Int,
        iconState: AllAppsIconRenderState
    ): String {
        return "${appInfo.componentName?.flattenToString()}_${appInfo.user.hashCode()}_" +
            "${sizePx}_${iconState.cacheKey}"
    }
    
    private fun loadIcon(
        appInfo: AppInfo,
        iconSizePx: Int,
        iconState: AllAppsIconRenderState
    ): Drawable {
        val flags = if (iconState.themed) BitmapInfo.FLAG_THEMED else 0
        return appInfo.newIcon(context, flags).apply {
            setBounds(0, 0, iconSizePx, iconSizePx)
        }
    }
}
