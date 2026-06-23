/*
 * Copyright (C) 2025 The Android Open Source Project
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
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import javax.inject.Inject

@LauncherAppSingleton
class QsbAppWidgetHost @Inject constructor(@ApplicationContext private val ctx: Context) :
    AppWidgetHost(ctx, HOST_ID) {

    private var callbacks: Callbacks? = null
    @Volatile
    private var activeWidgetId = INVALID_APPWIDGET_ID
    @Volatile
    private var activeProviderInfo: AppWidgetProviderInfo? = null

    fun setCallbacks(c: Callbacks) {
        callbacks = c
    }

    fun setActiveWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        val isSameWidget = activeWidgetId == appWidgetId
        val isSameProvider = activeProviderInfo?.provider == info?.provider
        if (isSameWidget && isSameProvider) return
        if (activeWidgetId != INVALID_APPWIDGET_ID && activeWidgetId != appWidgetId) {
            deleteAppWidgetId(activeWidgetId)
        }

        activeWidgetId = appWidgetId
        activeProviderInfo = info
        callbacks?.onProviderChanged(info)
    }

    fun getActiveWidgetId() = activeWidgetId

    fun createActiveWidgetView(context: Context): QsbWidgetHostView? {
        val widgetId = activeWidgetId
        val info = activeProviderInfo ?: return null
        if (widgetId == INVALID_APPWIDGET_ID) return null
        return createView(context, widgetId, info) as? QsbWidgetHostView
    }

    fun getBoundWidgetId(): Int {
        val currentWidgets = appWidgetIds
        if (currentWidgets.isNotEmpty()) {
            for (i in 0..(currentWidgets.size - 2)) deleteAppWidgetId(currentWidgets[i])
            return currentWidgets.last()
        } else {
            return INVALID_APPWIDGET_ID
        }
    }

    override fun onCreateView(
        context: Context?,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = HostView(context ?: ctx)

    private class HostView(context: Context) : QsbWidgetHostView(context) {
        init {
            id = R.id.qsb_widget
        }

        private val gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent) {
                        performLongClick()
                    }
                },
            )

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            gestureDetector.onTouchEvent(ev)
            return super.dispatchTouchEvent(ev)
        }
    }

    interface Callbacks {

        fun onProviderChanged(appWidget: AppWidgetProviderInfo?)
    }

    companion object {
        const val HOST_ID = 1025
    }
}
