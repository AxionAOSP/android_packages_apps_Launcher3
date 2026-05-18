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

package com.android.launcher3.allapps.compose.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.os.UserHandle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo
import com.android.launcher3.allapps.compose.ui.view.ComposeAppIconView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.FloatingIconView
import java.lang.ref.WeakReference

class AllAppsComposeLauncher(
    private val activityContext: ActivityContext,
    private val allAppsStore: AllAppsStore,
) {
    private val launcher = activityContext as? Launcher
    private val context = activityContext as Context

    var lastLaunchedComposeIcon: WeakReference<View>? = null
        private set
    var lastLaunchedComponent: ComponentName? = null
        private set
    var lastLaunchedSection: String? = null
        private set

    private var pendingLaunchRunnable: Runnable? = null
    private var pendingLaunchView: View? = null
    private var pendingLaunchId = 0

    fun launch(iconInfo: ComposeIconInfo) = launchWhenReady { launchApp(iconInfo) }

    fun launchWithoutIcon(appInfo: AppInfo) = launchWhenReady { launchAppWithoutIcon(appInfo) }

    fun clearLaunchedState() {
        lastLaunchedSection = null
        lastLaunchedComponent = null
        lastLaunchedComposeIcon = null
    }

    fun clearPendingLaunch() {
        pendingLaunchRunnable?.let { pendingLaunchView?.removeCallbacks(it) }
        pendingLaunchRunnable = null
        pendingLaunchView = null
        pendingLaunchId++
    }

    fun getComposeIconForClose(packageName: String, user: UserHandle): View? {
        if (lastLaunchedSection == null) return null
        lastLaunchedComponent?.let { comp ->
            if (comp.packageName == packageName) {
                lastLaunchedComposeIcon?.get()?.let { icon ->
                    if (icon.isAttachedToWindow && hasValidComposeIconBounds(icon)) {
                        return icon
                    }
                }
            }
        }
        val found = allAppsStore.findIconView { v ->
            (v is ComposeAppIconView || v is TextView) &&
                (v.tag as? ItemInfo)?.let { info ->
                    info.user == user && TextUtils.equals(info.targetPackage, packageName)
                } == true
        }
        return found?.takeIf { hasValidComposeIconBounds(it) }
    }

    private fun launchWhenReady(action: () -> Unit) {
        val l = launcher
        if (l == null || l.workspace.isFinishedSwitchingState) {
            action()
            return
        }
        val postView = l.dragLayer?.takeIf { it.isAttachedToWindow }
        if (postView == null) {
            action()
            return
        }
        clearPendingLaunch()
        val launchId = ++pendingLaunchId
        val startTime = SystemClock.uptimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                if (launchId != pendingLaunchId) return
                val currentLauncher = launcher
                val timedOut =
                    SystemClock.uptimeMillis() - startTime >= PENDING_LAUNCH_TIMEOUT_MS
                val launchNow = currentLauncher == null
                    || currentLauncher.workspace.isFinishedSwitchingState
                    || timedOut
                if (launchNow) {
                    pendingLaunchRunnable = null
                    pendingLaunchView = null
                    action()
                } else {
                    postView.postOnAnimation(this)
                }
            }
        }
        pendingLaunchRunnable = runnable
        pendingLaunchView = postView
        postView.postOnAnimation(runnable)
    }

    private fun launchApp(iconInfo: ComposeIconInfo) {
        val appInfo = resolveLaunchAppInfo(iconInfo.appInfo)
        val hostView = iconInfo.hostView
        val launchView = if (hasValidComposeIconBounds(hostView)) hostView else null
        launchView?.tag = appInfo
        lastLaunchedComposeIcon = launchView?.let { WeakReference(it) }
        lastLaunchedComponent = appInfo.componentName
        lastLaunchedSection = (launchView as? ComposeAppIconView)?.sectionId
        startApp(launchView, appInfo)
    }

    private fun launchAppWithoutIcon(appInfo: AppInfo) {
        val launchInfo = resolveLaunchAppInfo(appInfo)
        lastLaunchedComposeIcon = null
        lastLaunchedComponent = launchInfo.componentName
        lastLaunchedSection = null
        launcher?.setSkipFloatingIconReturnAnimation(true)
        startApp(null, launchInfo)
    }

    private fun startApp(view: View?, appInfo: AppInfo) {
        val l = launcher
        if (l != null) {
            ItemClickHandler.startAppShortcutOrInfoActivity(view, appInfo, l)
            return
        }
        val intent = appInfo.intent
        if (intent == null) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            clearLaunchedState()
            return
        }
        activityContext.startActivitySafely(view, Intent(intent), appInfo)
    }

    private fun resolveLaunchAppInfo(appInfo: AppInfo): AppInfo {
        val componentKey = appInfo.componentKey ?: return appInfo
        return allAppsStore.getApp(componentKey) ?: appInfo
    }

    private fun hasValidComposeIconBounds(v: View?): Boolean {
        if (v == null) return false
        if (v !is ComposeAppIconView) return true
        val l = launcher ?: return false
        val bounds = RectF()
        FloatingIconView.getLocationBoundsForView(l, v, true, bounds, Rect())
        return bounds.width() > 0 && bounds.height() > 0
            && (bounds.left > 0 || bounds.top > 0)
    }

    companion object {
        private const val PENDING_LAUNCH_TIMEOUT_MS = 1000L
    }
}
