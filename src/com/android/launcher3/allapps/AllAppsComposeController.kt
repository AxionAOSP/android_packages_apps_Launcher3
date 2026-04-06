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

package com.android.launcher3.allapps

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.android.axion.compose.host.AxComposeView
import com.android.launcher3.allapps.compose.ui.viewmodel.AllAppsComposeViewModel
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DragSource
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo
import com.android.launcher3.allapps.compose.ui.AllAppsComposeHost

import com.android.launcher3.allapps.compose.ui.view.ComposeAppIconView
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.FloatingIconView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import javax.inject.Inject

@Stable
@ActivityContextSingleton
class AllAppsComposeController @Inject constructor(
    private val activityContext: ActivityContext,
    private val allAppsStore: AllAppsStore,
) : DeviceProfile.OnDeviceProfileChangeListener {

    private val launcher: Launcher? = activityContext as? Launcher

    val viewModel = AllAppsComposeViewModel(allAppsStore, activityContext as Context).also { vm ->
        val dp = activityContext.deviceProfile
        val profile = dp.allAppsProfile
        vm.onConfigChanged(dp.numShownAllAppsColumns, profile.iconSizePx, profile.cellWidthPx, profile.cellHeightPx)
    }

    val callbacks: AllAppsComposeCallbacks = createCallbacks()

    var transitionProgress by mutableFloatStateOf(0f)
        internal set

    private val _predictedApps = MutableStateFlow<List<ItemInfo>>(emptyList())
    var predictedApps: List<ItemInfo>
        get() = _predictedApps.value
        internal set(value) { _predictedApps.value = value }
    val predictedAppsFlow: StateFlow<List<ItemInfo>> = _predictedApps.asStateFlow()

    private val _isPrivateSpaceHidden = MutableStateFlow(false)
    var isPrivateSpaceHidden: Boolean
        get() = _isPrivateSpaceHidden.value
        internal set(value) { _isPrivateSpaceHidden.value = value }
    val isPrivateSpaceHiddenFlow: StateFlow<Boolean> = _isPrivateSpaceHidden.asStateFlow()

    var configUpdate by mutableStateOf(ConfigUpdate())
        internal set

    var profileVersion by mutableIntStateOf(0)
        private set

    var canScrollUp by mutableStateOf(false)
    var canScrollDown by mutableStateOf(false)
    var isLongPressing: Boolean = false
    var folderExpanded by mutableStateOf(false)
    var selectedProfileTab by mutableIntStateOf(TAB_PERSONAL)
    var searchQuery by mutableStateOf("")

    var lastLaunchedComposeIcon: WeakReference<View>? = null
        private set
    var lastLaunchedComponent: ComponentName? = null
        private set

    var hiddenIconComponent: ComponentName? by mutableStateOf(null)

    private var dismissFolderHandler: (() -> Unit)? = null
    private var showFolderPickerHandler: ((String) -> Unit)? = null
    private var onBackInvokedCallback: OnBackInvokedCallback? = null
    private var isBackCallbackRegistered = false

    private var container: ViewGroup? = null
    private var composeView: AxComposeView? = null
    private var privateProfileManager: PrivateProfileManager? = null
    private var workProfileManager: WorkProfileManager? = null
    private var sharedHostView: ComposeAppIconView? = null

    fun getSharedHostView(): ComposeAppIconView {
        val existing = sharedHostView
        if (existing != null) return existing
        val c = container
            ?: launcher?.getAppsView() as? ViewGroup
            ?: throw IllegalStateException("No container available for shared host view")
        val hostView = ComposeAppIconView(c.context)
        hostView.controller = this
        hostView.visibility = View.INVISIBLE
        c.addView(hostView, RelativeLayout.LayoutParams(1, 1))
        sharedHostView = hostView
        return hostView
    }


    fun attachContainer(
        container: ComposeAllAppsContainerView,
        cv: AxComposeView,
        privateManager: PrivateProfileManager,
        workManager: WorkProfileManager
    ) {
        Log.d(TAG, "attachContainer: container=${container.javaClass.simpleName}, cv=$cv", Throwable())
        this.container = container
        composeView = cv
        privateProfileManager = privateManager
        workProfileManager = workManager
        launcher?.addOnDeviceProfileChangeListener(this)
    }

    fun attachTaskbarContainer(container: ViewGroup, cv: AxComposeView) {
        Log.d(TAG, "attachTaskbarContainer: container=${container.javaClass.simpleName}, cv=$cv", Throwable())
        this.container = container
        composeView = cv
        updateConfig(activityContext.deviceProfile)
        transitionProgress = 1f
    }

    fun detachContainer() {
        Log.d(TAG, "detachContainer: composeView=$composeView", Throwable())
        unregisterBackCallback()
        launcher?.removeOnDeviceProfileChangeListener(this)
        dismissFolderHandler = null
        showFolderPickerHandler = null
        viewModel.cleanup()
        composeView = null
        sharedHostView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        sharedHostView = null
        container = null
    }

    fun setTransitionProgressWithRefresh(progress: Float) {
        transitionProgress = progress
    }

    fun resetState() {
        canScrollUp = false
        canScrollDown = false
        folderExpanded = false
        selectedProfileTab = TAB_PERSONAL
        searchQuery = ""
        hiddenIconComponent = null
    }

    @Composable
    fun Content() {
        AllAppsComposeTheme {
            val host = @Composable {
                AllAppsComposeHost(allAppsStore, activityContext as Context, this@AllAppsComposeController)
            }
            (activityContext as? OnBackPressedDispatcherOwner)?.let {
                CompositionLocalProvider(
                    LocalOnBackPressedDispatcherOwner provides it,
                    content = host
                )
            } ?: host()
        }
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        Log.d(TAG, "onDeviceProfileChanged: composeView=$composeView", Throwable())
        (container as? ComposeAllAppsContainerView)?.onProfileChanged(dp)
        updateConfig(dp)
        resetState()
        resetComposeViewProperties()
        profileVersion++
    }

    fun onUiModeChanged() {
        Log.d(TAG, "onUiModeChanged: composeView=$composeView, configUpdate=$configUpdate", Throwable())
        configUpdate = configUpdate.copy(uiMode = configUpdate.uiMode + 1)
    }

    private fun resetComposeViewProperties() {
        Log.d(TAG, "resetComposeViewProperties: composeView=$composeView")
        composeView?.let { cv ->
            cv.translationY = 0f
            cv.alpha = 1f
        }
    }

    fun updateConfig(dp: DeviceProfile) {
        val profile = dp.allAppsProfile
        configUpdate = ConfigUpdate(
            dp.numShownAllAppsColumns,
            profile.iconSizePx,
            profile.cellWidthPx,
            profile.cellHeightPx,
            configUpdate.uiMode
        )
    }

    fun getComposeView(): AxComposeView? = composeView

    fun getComposeIconForClose(packageName: String, user: UserHandle): View? {
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

    fun showFolderPickerForApp(componentName: String) {
        showFolderPickerHandler?.invoke(componentName)
    }

    fun handleBackKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK
            && folderExpanded
            && dismissFolderHandler != null
        ) {
            if (event.action == KeyEvent.ACTION_UP) {
                dismissFolderHandler?.invoke()
            }
            return true
        }
        return false
    }

    private val context: Context = activityContext as Context

    private fun createCallbacks() = object : AllAppsComposeCallbacks {
        override fun onAppClicked(iconInfo: ComposeIconInfo) {
            val appInfo = iconInfo.appInfo
            val hostView = iconInfo.hostView
            val validBounds = hasValidComposeIconBounds(hostView)
            launcher?.let { if (validBounds) FloatingIconView.fetchIcon(it, hostView, appInfo, true) }
            lastLaunchedComposeIcon = if (validBounds && hostView != null) {
                WeakReference(hostView)
            } else null
            lastLaunchedComponent = appInfo.componentName
            activityContext.startActivitySafely(
                if (validBounds) hostView else null,
                appInfo.intent,
                appInfo
            )
        }

        override fun onAppClickedFromFolder(appInfo: AppInfo) {
            lastLaunchedComposeIcon = null
            lastLaunchedComponent = appInfo.componentName
            launcher?.setSkipFloatingIconReturnAnimation(true)
            activityContext.startActivitySafely(null, appInfo.intent, appInfo)
        }

        override fun onAppLongClicked(iconInfo: ComposeIconInfo) {
            val cav = iconInfo.hostView as? ComposeAppIconView ?: return
            val l = launcher ?: return
            PopupContainerWithArrow.showForCompose(
                l, cav, iconInfo.iconBoundsOnScreen, iconInfo.appInfo
            )
        }

        override fun onAppDragStart(iconInfo: ComposeIconInfo) {
            val cav = iconInfo.hostView as? ComposeAppIconView ?: return
            val cont = container as? DragSource ?: return
            val l = launcher ?: return
            AbstractFloatingView.closeAllOpenViews(l)
            l.workspace.beginDragFromCompose(
                iconInfo.appInfo, cav, iconInfo.iconBoundsOnScreen, cont, DragOptions()
            )
        }

        override fun onAppDragMove(screenX: Float, screenY: Float) {
            dispatchDragEvent(MotionEvent.ACTION_MOVE, screenX, screenY)
        }

        override fun onAppDragEnd(screenX: Float, screenY: Float) {
            dispatchDragEvent(MotionEvent.ACTION_UP, screenX, screenY)
        }

        override fun onSearchQueryChanged(query: String) {
            searchQuery = query
        }

        override fun onTabSelected(tab: Int) {
            selectedProfileTab = tab
            canScrollUp = false
            canScrollDown = false
        }

        override fun onFolderExpandedChanged(expanded: Boolean) {
            folderExpanded = expanded
            registerBackCallback(expanded)
        }

        override fun onSearchExpandedChanged(expanded: Boolean) {
            if (expanded || !folderExpanded) {
                registerBackCallback(expanded)
            }
        }

        override fun setDismissFolderHandler(handler: (() -> Unit)?) {
            dismissFolderHandler = handler
        }

        override fun startActivity(intent: Intent) {
            try {
                launcher?.setSkipFloatingIconReturnAnimation(true)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            }
        }

        override fun startShortcut(packageName: String, shortcutId: String, user: UserHandle) {
            launcher?.setSkipFloatingIconReturnAnimation(true)
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            try {
                launcherApps?.startShortcut(packageName, shortcutId, null, null, user)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start shortcut", e)
            }
        }

        override fun requestContactsPermission() {
            requestPermission(Manifest.permission.READ_CONTACTS, REQUEST_CODE_CONTACTS)
        }

        override fun requestSmsPermission() {
            requestPermission(Manifest.permission.READ_SMS, REQUEST_CODE_SMS)
        }

        override fun requestFilePermission() {
            (activityContext as? Activity)?.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                ), REQUEST_CODE_FILES
            )
        }

        override fun requestCalendarPermission() {
            requestPermission(Manifest.permission.READ_CALENDAR, REQUEST_CODE_CALENDAR)
        }

        override fun onPrivateSpaceClicked(isLocked: Boolean) {
            if (isLocked) privateProfileManager?.setQuietMode(false)
        }

        override fun onPrivateSpaceSettingsClicked() {
            try {
                ApiWrapper.INSTANCE.get(context)
                    .privateSpaceSettingsIntent
                    ?.let { context.startActivity(it) }
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Failed to open private space settings", e)
            }
        }

        override fun onPrivateSpaceInstallAppClicked() {
            try {
                val apiWrapper = ApiWrapper.INSTANCE.get(context)
                val privateUser = privateProfileManager?.profileUser ?: return
                apiWrapper.getAppMarketActivityIntent(context.packageName, privateUser)
                    ?.let { context.startActivity(it) }
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Failed to open app market for private space", e)
            }
        }

        override fun onWorkProfileToggle(enable: Boolean) {
            workProfileManager?.setWorkProfileEnabled(enable)
        }

        override fun setShowFolderPickerHandler(handler: ((String) -> Unit)?) {
            showFolderPickerHandler = handler
        }
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

    private fun dispatchDragEvent(action: Int, screenX: Float, screenY: Float) {
        val l = launcher ?: return
        val loc = IntArray(2)
        l.dragLayer.getLocationOnScreen(loc)
        val ev = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            action, screenX - loc[0], screenY - loc[1], 0
        )
        try {
            l.dragController.onControllerTouchEvent(ev)
        } finally {
            ev.recycle()
        }
    }

    private fun registerBackCallback(register: Boolean) {
        val cont = container ?: return
        val dispatcher = cont.findOnBackInvokedDispatcher() ?: return
        if (register) {
            val callback = onBackInvokedCallback ?: OnBackInvokedCallback {
                dismissFolderHandler?.invoke()
            }.also { onBackInvokedCallback = it }
            if (isBackCallbackRegistered) {
                dispatcher.unregisterOnBackInvokedCallback(callback)
            }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback
            )
            isBackCallbackRegistered = true
        } else if (isBackCallbackRegistered) {
            onBackInvokedCallback?.let {
                dispatcher.unregisterOnBackInvokedCallback(it)
            }
            isBackCallbackRegistered = false
        }
    }

    private fun unregisterBackCallback() {
        if (!isBackCallbackRegistered) return
        val callback = onBackInvokedCallback ?: return
        container?.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
        isBackCallbackRegistered = false
    }

    private fun requestPermission(permission: String, code: Int) {
        (activityContext as? Activity)?.requestPermissions(arrayOf(permission), code)
    }

    data class ConfigUpdate(
        val columns: Int = 0,
        val iconSizePx: Int = 0,
        val cellWidthPx: Int = 0,
        val cellHeightPx: Int = 0,
        val uiMode: Int = 0
    )

    companion object {
        const val TAB_PERSONAL = 0
        const val TAB_WORK = 1
        private const val TAG = "ComposeAllApps"
        const val REQUEST_CODE_CONTACTS = 100
        const val REQUEST_CODE_SMS = 101
        const val REQUEST_CODE_FILES = 102
        const val REQUEST_CODE_CALENDAR = 103
    }
}


@Composable
private fun AllAppsComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val assetsSeq = LocalConfiguration.current.assetsSeq
    val colorScheme = remember(isDark, assetsSeq) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
