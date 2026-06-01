/*
 * Copyright (C) 2026 AxionOS
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

package com.android.launcher3.views

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent.ACTION_WALLPAPER_CHANGED
import android.graphics.drawable.Drawable
import android.util.Log
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SimpleBroadcastReceiver
import javax.inject.Inject

@LauncherAppSingleton
class BlurredSnapshotManager
@Inject
constructor(@ApplicationContext context: Context, tracker: DaggerSingletonTracker) : SafeCloseable {
    private val wallpaperManager = context.getSystemService(WallpaperManager::class.java)!!
    private val snapshotInvalidationCallbacks = ArrayList<Runnable>()
    private val wallpaperChangeReceiver = SimpleBroadcastReceiver(
        context,
        UI_HELPER_EXECUTOR,
        MAIN_EXECUTOR,
    ) { _ ->
        onWallpaperChanged()
    }

    private var destroyed = false
    private var wallpaperState = WallpaperSnapshotState()

    init {
        wallpaperChangeReceiver.register(
            SimpleBroadcastReceiver.actionsFilter(ACTION_WALLPAPER_CHANGED)
        )
        tracker.addCloseable(this)
        requestWallpaperDrawable()
    }

    fun attach(activityContext: ActivityContext, onSnapshotInvalidated: Runnable): SafeCloseable {
        snapshotInvalidationCallbacks.add(onSnapshotInvalidated)
        val listener = OnDeviceProfileChangeListener { _ ->
            onDeviceProfileChanged()
        }
        activityContext.addOnDeviceProfileChangeListener(listener)
        requestWallpaperDrawable()
        return SafeCloseable {
            activityContext.removeOnDeviceProfileChangeListener(listener)
            snapshotInvalidationCallbacks.remove(onSnapshotInvalidated)
        }
    }

    fun shouldUseDefaultBlur(maxBlurRadius: Int, minSnapshotBlurRadius: Int): Boolean =
        maxBlurRadius < minSnapshotBlurRadius || wallpaperState.hasLiveWallpaper

    fun captureWallpaper(
        snapshotView: BlurredSnapshotView,
        wallpaperOffset: Float,
        blurRadius: Int,
    ): Boolean {
        val state = wallpaperState
        if (state.hasLiveWallpaper) {
            return false
        }
        val wallpaper = state.drawable ?: run {
            requestWallpaperDrawable()
            return false
        }
        return snapshotView.captureWallpaper(wallpaper, wallpaperOffset, blurRadius)
    }

    private fun onWallpaperChanged() {
        wallpaperState = wallpaperState.invalidate(clearDrawable = true)
        notifySnapshotInvalidated()
        requestWallpaperDrawable()
    }

    private fun onDeviceProfileChanged() {
        notifySnapshotInvalidated()
        requestWallpaperDrawable()
    }

    private fun requestWallpaperDrawable() {
        if (destroyed || wallpaperState.loadAttempted) {
            return
        }
        wallpaperState = wallpaperState.loadRequested()
        val loadId = wallpaperState.loadId
        UI_HELPER_EXECUTOR.execute {
            var liveWallpaper = false
            var wallpaper: Drawable? = null
            try {
                liveWallpaper = wallpaperManager.getWallpaperInfo() != null
                if (!liveWallpaper) {
                    wallpaper = wallpaperManager.drawable
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Unable to load wallpaper drawable", e)
            }
            MAIN_EXECUTOR.execute {
                if (destroyed || loadId != wallpaperState.loadId) {
                    return@execute
                }
                wallpaperState = wallpaperState.loaded(liveWallpaper, wallpaper)
                notifySnapshotInvalidated()
            }
        }
    }

    private fun notifySnapshotInvalidated() {
        for (i in snapshotInvalidationCallbacks.size - 1 downTo 0) {
            snapshotInvalidationCallbacks[i].run()
        }
    }

    override fun close() {
        destroyed = true
        wallpaperState = wallpaperState.closed()
        snapshotInvalidationCallbacks.clear()
        wallpaperChangeReceiver.close()
    }

    private data class WallpaperSnapshotState(
        val hasLiveWallpaper: Boolean = false,
        val loadAttempted: Boolean = false,
        val loadId: Int = 0,
        val drawable: Drawable? = null,
    ) {
        fun invalidate(clearDrawable: Boolean) = copy(
            loadAttempted = false,
            loadId = loadId + 1,
            drawable = if (clearDrawable) null else drawable,
        )

        fun loadRequested() = copy(
            loadAttempted = true,
            loadId = loadId + 1,
        )

        fun loaded(hasLiveWallpaper: Boolean, drawable: Drawable?) = copy(
            hasLiveWallpaper = hasLiveWallpaper,
            drawable = drawable,
        )

        fun closed() = copy(
            loadId = loadId + 1,
            drawable = null,
        )
    }

    companion object {
        private const val TAG = "BlurredSnapshotManager"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getBlurredSnapshotManager)
    }
}
