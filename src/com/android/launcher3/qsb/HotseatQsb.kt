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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherConstants
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider

private const val TAG = "HotseatQsb"
private const val QSB_WIDGET_HOST_ID = 1026
private const val PREF_WIDGET_ID = "qsb_widget_id"

@Composable
fun HotseatQsb() {
    val context = LocalContext.current
    val launcherPrefs = remember { LauncherPrefs.get(context) }

    var searchProvider by remember {
        mutableStateOf(launcherPrefs.get(LauncherPrefs.SEARCH_PROVIDER) ?: "none")
    }

    DisposableEffect(Unit) {
        val listener = LauncherPrefChangeListener { key ->
            if (key == LauncherPrefs.SEARCH_PROVIDER.sharedPrefKey) {
                val newValue = launcherPrefs.get(LauncherPrefs.SEARCH_PROVIDER) ?: "none"
                Log.d(TAG, "Pref changed: $newValue")
                searchProvider = newValue
            }
        }
        launcherPrefs.addListener(listener, LauncherPrefs.SEARCH_PROVIDER)
        onDispose {
            launcherPrefs.removeListener(listener, LauncherPrefs.SEARCH_PROVIDER)
        }
    }

    val widgetInfo = remember(searchProvider) {
        SearchWidgetHelper.getSearchWidgetProvider(context).also {
            Log.d(TAG, "WidgetInfo: ${it?.provider?.shortClassName ?: "null"}")
        }
    }

    if (searchProvider == "none" || searchProvider.isBlank() || widgetInfo == null) {
        Log.d(TAG, "Provider is not available, skip")
        return
    }

    val widgetHost = remember {
        Log.d(TAG, "Creating QsbWidgetHost, startListening")
        QsbContainerView.QsbWidgetHost(context, QSB_WIDGET_HOST_ID) { ctx ->
            object : QsbWidgetHostView(ctx) {
                private val gestureDetector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: MotionEvent) {
                            performLongClick()
                        }
                    }
                )

                override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(ev)
                    return super.dispatchTouchEvent(ev)
                }
            }
        }.also { it.startListening() }
    }

    DisposableEffect(widgetHost) {
        onDispose {
            Log.d(TAG, "stopListening")
            widgetHost.stopListening()
        }
    }

    QsbWidget(context, widgetHost, widgetInfo, launcherPrefs.backedUpPrefs, searchProvider)
}

@Composable
private fun QsbWidget(
    context: Context,
    widgetHost: AppWidgetHost,
    widgetInfo: AppWidgetProviderInfo,
    prefs: SharedPreferences,
    searchProvider: String
) {
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }

    val bindOpts = remember(widgetInfo) {
        val idp = LauncherAppState.getIDP(context)
        LauncherComponentProvider.get(context)
            .widgetSizeHandler
            .getWidgetSizeOptions(idp.numColumns, 1)
    }

    val widgetId = remember(searchProvider) {
        val existingId = prefs.getInt(PREF_WIDGET_ID, -1)

        Log.d(TAG, "Checking widget ID: existing=$existingId")

        if (existingId > -1) {
            Log.d(TAG, "Deleting old widget ID $existingId to force fresh bind")
            try {
                widgetHost.deleteAppWidgetId(existingId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete old widget ID", e)
            }
        }

        val newId = widgetHost.allocateAppWidgetId()
        Log.d(TAG, "Allocated new ID=$newId")

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(
            newId, widgetInfo.profile, widgetInfo.provider, bindOpts
        )
        Log.d(TAG, "Bind result=$bound")

        if (bound) {
            prefs.edit().putInt(PREF_WIDGET_ID, newId).apply()
            Log.d(TAG, "Saved new widget ID $newId")
            newId
        } else {
            Log.e(TAG, "BIND FAILED! Launcher may not have BIND_APPWIDGET permission")
            widgetHost.deleteAppWidgetId(newId)
            -1
        }
    }

    Log.d(TAG, "Final widgetId=$widgetId")

    val qsbHeight = context.resources.getDimensionPixelSize(R.dimen.qsb_widget_height)

    if (widgetId > -1) {
        key(widgetId) {
            AndroidView(
                factory = { ctx ->
                    Log.d(TAG, "Creating AppWidgetHostView")
                    widgetHost.createView(ctx, widgetId, widgetInfo).apply {
                        setId(R.id.qsb_widget)
                        setPadding(0, 0, 0, 0)
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            qsbHeight
                        )

                        setOnLongClickListener { view ->
                            val launcher = Launcher.getLauncher(view.context)
                            if (widgetInfo.configure != null) {
                                launcher.appWidgetHolder?.startConfigActivity(
                                    launcher,
                                    widgetId,
                                    LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET
                                )
                                true
                            } else {
                                false
                            }
                        }

                        Log.d(TAG, "View created: w=${layoutParams.width}, h=${layoutParams.height}")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { qsbHeight.toDp() })
            )
        }
    } else {
        Log.d(TAG, "widgetId=-1, showing setup view")
        DefaultQsbView(showSetup = true)
    }
}

@Composable
private fun DefaultQsbView(showSetup: Boolean = false) {
    val context = LocalContext.current
    Log.d(TAG, "DefaultQsbView: showSetup=$showSetup")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                try {
                    Launcher.getLauncher(context).startSearch("", false, null, true)
                } catch (e: Exception) {
                    Log.e(TAG, "startSearch failed", e)
                }
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showSetup) "Tap to setup search" else "Search",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
