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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.widget.util.WidgetSizes

private const val TAG = "HotseatQsb"
private const val QSB_WIDGET_HOST_ID = 1026
private const val PREF_WIDGET_ID = "qsb_widget_id"

@Composable
fun HotseatQsb() {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs.getPrefs(context) }
    
    var searchProvider by remember {
        mutableStateOf(prefs.getString(SearchWidgetHelper.KEY_SEARCH_PROVIDER, "") ?: "")
    }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == SearchWidgetHelper.KEY_SEARCH_PROVIDER) {
                val newValue = sharedPrefs.getString(key, "") ?: ""
                Log.d(TAG, "Pref changed: $newValue")
                searchProvider = newValue
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    if (searchProvider == "none") {
        Log.d(TAG, "Provider is 'none', skip")
        return
    }

    val widgetHost = remember { 
        Log.d(TAG, "Creating QsbWidgetHost, startListening")
        QsbContainerView.QsbWidgetHost(context, QSB_WIDGET_HOST_ID) { ctx ->
            QsbWidgetHostView(ctx)
        }.also { it.startListening() }
    }
    
    DisposableEffect(widgetHost) {
        onDispose { 
            Log.d(TAG, "stopListening")
            widgetHost.stopListening() 
        }
    }

    val widgetInfo = remember(searchProvider) {
        SearchWidgetHelper.getSearchWidgetProvider(context).also {
            Log.d(TAG, "WidgetInfo: ${it?.provider?.shortClassName ?: "null"}")
        }
    }

    if (widgetInfo != null) {
        QsbWidget(context, widgetHost, widgetInfo, prefs, searchProvider)
    } else {
        Log.d(TAG, "No widgetInfo, showing default")
        DefaultQsbView()
    }
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
        WidgetSizes.getWidgetSizeOptions(
            context, 
            widgetInfo.provider,
            LauncherAppState.getIDP(context).numColumns, 
            1
        )
    }
    
    val widgetId = remember(searchProvider) {
        var existingId = prefs.getInt(PREF_WIDGET_ID, -1)
        
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
        DefaultQsbView(showSetup = true, widgetInfo = widgetInfo)
    }
}

@Composable
private fun DefaultQsbView(
    showSetup: Boolean = false,
    widgetInfo: AppWidgetProviderInfo? = null
) {
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
