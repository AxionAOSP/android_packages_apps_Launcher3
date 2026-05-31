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
import android.R as AndroidR
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
import androidx.core.graphics.ColorUtils
import com.android.axion.blur.AxBlurSettings
import com.android.axion.compose.host.AxComposeView
import com.android.axion.compose.theme.rememberAxionTypography
import com.android.internal.R as InternalR
import com.android.launcher3.allapps.compose.ui.viewmodel.AllAppsComposeViewModel
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DragSource
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo
import com.android.launcher3.allapps.compose.ui.AllAppsComposeHost
import com.android.launcher3.allapps.compose.ui.AllAppsComposeLauncher
import com.android.launcher3.allapps.compose.ui.view.ComposeAppIconView
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
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
    private val composeLauncher = AllAppsComposeLauncher(activityContext, allAppsStore)

    val viewModel = AllAppsComposeViewModel(allAppsStore, activityContext as Context).also { vm ->
        val dp = activityContext.deviceProfile
        val profile = dp.allAppsProfile
        vm.onConfigChanged(dp.numShownAllAppsColumns, profile.iconSizePx, profile.cellWidthPx, profile.cellHeightPx, dp.deviceProperties.isTablet)
    }

    val callbacks: AllAppsComposeCallbacks = createCallbacks()

    var transitionProgress by mutableFloatStateOf(0f)
        internal set

    var isTransitionCollapsing by mutableStateOf(false)
        private set

    var backProgress by mutableFloatStateOf(0f)

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

    var configuration by mutableStateOf(
        AllAppsConfiguration.from(activityContext as Context, activityContext.deviceProfile)
    )
        internal set

    var profileVersion by mutableIntStateOf(0)
        private set

    var canScrollUp by mutableStateOf(false)
    var canScrollDown by mutableStateOf(false)
    var isLongPressing: Boolean = false
    var folderExpanded by mutableStateOf(false)
    var selectedProfileTab by mutableIntStateOf(TAB_PERSONAL)
    var searchQuery by mutableStateOf("")

    val lastLaunchedComposeIcon: WeakReference<View>?
        get() = composeLauncher.lastLaunchedComposeIcon
    val lastLaunchedComponent: ComponentName?
        get() = composeLauncher.lastLaunchedComponent
    val lastLaunchedSection: String?
        get() = composeLauncher.lastLaunchedSection

    var hiddenIconComponent: ComponentName? by mutableStateOf(null)
    var hiddenIconSection: String? = null


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
        val c = launcher?.dragLayer
            ?: container
            ?: launcher?.getAppsView() as? ViewGroup
            ?: throw IllegalStateException("No container available for shared host view")
        val hostView = ComposeAppIconView(c.context)
        hostView.controller = this
        hostView.visibility = View.INVISIBLE
        hostView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val lp = if (c is BaseDragLayer<*>) {
            BaseDragLayer.LayoutParams(1, 1).apply {
                customPosition = true
                ignoreInsets = true
            }
        } else {
            RelativeLayout.LayoutParams(1, 1)
        }
        c.addView(hostView, lp)
        sharedHostView = hostView
        return hostView
    }


    fun attachContainer(
        container: ComposeAllAppsContainerView,
        cv: AxComposeView,
        privateManager: PrivateProfileManager,
        workManager: WorkProfileManager
    ) {
        Log.d(TAG, "attachContainer: container=${container.javaClass.simpleName}, cv=$cv")
        this.container = container
        composeView = cv
        privateProfileManager = privateManager
        workProfileManager = workManager
        launcher?.addOnDeviceProfileChangeListener(this)
    }

    fun attachTaskbarContainer(container: ViewGroup, cv: AxComposeView) {
        Log.d(TAG, "attachTaskbarContainer: container=${container.javaClass.simpleName}, cv=$cv")
        this.container = container
        composeView = cv
        updateConfig(activityContext.deviceProfile)
        transitionProgress = 1f
    }

    fun detachContainer() {
        Log.d(TAG, "detachContainer: composeView=$composeView")
        composeLauncher.clearPendingLaunch()
        registerBackIntercept(false)
        launcher?.removeOnDeviceProfileChangeListener(this)
        showFolderPickerHandler = null
        viewModel.cleanup()
        composeView = null
        sharedHostView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        sharedHostView = null
        container = null
    }

    fun setTransitionProgressWithRefresh(progress: Float) {
        if (progress < transitionProgress - TRANSITION_DIRECTION_EPSILON) {
            isTransitionCollapsing = true
        } else if (progress > transitionProgress + TRANSITION_DIRECTION_EPSILON) {
            isTransitionCollapsing = false
        }
        transitionProgress = progress
    }

    fun resetState() {
        canScrollUp = false
        canScrollDown = false
        folderExpanded = false
        selectedProfileTab = TAB_PERSONAL
        searchQuery = ""
        hiddenIconComponent = null
        hiddenIconSection = null
        clearLaunchedState()
    }

    @Composable
    fun Content() {
        AllAppsComposeTheme {
            val host = @Composable {
                AllAppsComposeHost(allAppsStore, activityContext as Context, this@AllAppsComposeController)
            }
            val owner: OnBackPressedDispatcherOwner = (activityContext as? OnBackPressedDispatcherOwner)
                ?: object : OnBackPressedDispatcherOwner {
                    override val lifecycle get() = (activityContext as androidx.lifecycle.LifecycleOwner).lifecycle
                    override val onBackPressedDispatcher get() = androidx.activity.OnBackPressedDispatcher()
                }
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides owner,
                content = host
            )
        }
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        Log.d(TAG, "onDeviceProfileChanged: composeView=$composeView")
        (container as? ComposeAllAppsContainerView)?.onProfileChanged(dp)
        updateConfig(dp)
        resetState()
        resetComposeViewProperties()
        profileVersion++
    }

    fun onUiModeChanged(config: Configuration) {
        Log.d(TAG, "onUiModeChanged: composeView=$composeView, configuration=$configuration")
        refreshConfiguration(activityContext.deviceProfile, config)
    }

    fun onAllAppsColorsChanged() {
        refreshConfiguration(activityContext.deviceProfile)
    }

    private fun resetComposeViewProperties() {
        Log.d(TAG, "resetComposeViewProperties: composeView=$composeView")
        composeView?.let { cv ->
            cv.translationY = 0f
            cv.alpha = 1f
            cv.scaleX = 1f
            cv.scaleY = 1f
        }
    }

    fun updateConfig(dp: DeviceProfile) {
        refreshConfiguration(dp)
    }

    private fun refreshConfiguration(
        dp: DeviceProfile,
        config: Configuration = (activityContext as Context).resources.configuration
    ) {
        val updated = AllAppsConfiguration.from(activityContext as Context, dp, config)
        if (configuration != updated) {
            configuration = updated
        }
    }

    fun repositionHostView(screenBounds: RectF) {
        val hv = sharedHostView ?: return
        val parentLoc = IntArray(2)
        (hv.parent as? View)?.getLocationOnScreen(parentLoc)
        val iconSize = hv.getIconSizePx()
        val left = (screenBounds.left - parentLoc[0]).toInt()
        val top = (screenBounds.top - parentLoc[1]).toInt()
        when (val lp = hv.layoutParams) {
            is BaseDragLayer.LayoutParams -> {
                lp.width = iconSize
                lp.height = iconSize
                lp.x = left
                lp.y = top
                lp.customPosition = true
                lp.ignoreInsets = true
                hv.layoutParams = lp
            }
            is RelativeLayout.LayoutParams -> {
                lp.width = iconSize
                lp.height = iconSize
                lp.leftMargin = left
                lp.topMargin = top
                lp.rightMargin = 0
                lp.marginStart = left
                lp.marginEnd = 0
                hv.layoutParams = lp
            }
            else -> return
        }
        hv.measure(
            View.MeasureSpec.makeMeasureSpec(iconSize, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(iconSize, View.MeasureSpec.EXACTLY)
        )
        hv.layout(left, top, left + hv.getIconSizePx(), top + hv.getIconSizePx())
    }

    fun onComposeIconPositioned(
        componentName: ComponentName?,
        sectionId: String?,
        screenBounds: RectF,
        iconSizePx: Int
    ) {
        if (componentName == null) return
        val matchesHiddenIcon =
            hiddenIconComponent == componentName && hiddenIconSection == sectionId
        val matchesLaunchedIcon =
            lastLaunchedComponent == componentName && lastLaunchedSection == sectionId
        if (!matchesHiddenIcon && !matchesLaunchedIcon) return
        sharedHostView?.setIconSizePx(iconSizePx)
        repositionHostView(screenBounds)
    }

    fun clearLaunchedState() {
        composeLauncher.clearLaunchedState()
    }

    fun getComposeView(): AxComposeView? = composeView

    fun getComposeIconForClose(packageName: String, user: UserHandle): View? =
        composeLauncher.getComposeIconForClose(packageName, user)

    fun showFolderPickerForApp(componentName: String) {
        showFolderPickerHandler?.invoke(componentName)
    }

    fun handleBackKeyEvent(event: KeyEvent): Boolean = false

    private val context: Context = activityContext as Context

    private fun createCallbacks() = object : AllAppsComposeCallbacks {
        override fun onAppClicked(iconInfo: ComposeIconInfo) {
            composeLauncher.launch(iconInfo)
        }

        override fun onAppClickedFromFolder(appInfo: AppInfo) {
            composeLauncher.launchWithoutIcon(appInfo)
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
        }

        override fun onSearchExpandedChanged(expanded: Boolean) = Unit

        override fun setDismissFolderHandler(handler: (() -> Unit)?) {
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

    var backAction: (() -> Unit)? = null
    var folderBackAction: (() -> Unit)? = null

    fun registerBackIntercept(register: Boolean) {
        val cont = container ?: return
        val dispatcher = cont.findOnBackInvokedDispatcher() ?: return
        if (register) {
            val callback = onBackInvokedCallback ?: OnBackInvokedCallback {
                Log.d(TAG, "OnBackInvokedCallback: backAction=${backAction != null} folderBackAction=${folderBackAction != null}")
                (backAction ?: folderBackAction)?.invoke()
            }.also {
                onBackInvokedCallback = it
            }
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

    private fun requestPermission(permission: String, code: Int) {
        (activityContext as? Activity)?.requestPermissions(arrayOf(permission), code)
    }

    companion object {
        const val TAB_PERSONAL = 0
        const val TAB_WORK = 1
        private const val TAG = "ComposeAllApps"
        private const val TRANSITION_DIRECTION_EPSILON = 0.001f
        const val REQUEST_CODE_CONTACTS = 100
        const val REQUEST_CODE_SMS = 101
        const val REQUEST_CODE_FILES = 102
        const val REQUEST_CODE_CALENDAR = 103
    }
}

data class AllAppsConfiguration(
    val profile: AllAppsProfileConfiguration = AllAppsProfileConfiguration(),
    val colors: AllAppsColorConfiguration = AllAppsColorConfiguration(),
    val uiMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED,
    val assetsSeq: Int = 0
) {
    companion object {
        fun from(
            context: Context,
            dp: DeviceProfile,
            resourcesConfig: Configuration = context.resources.configuration
        ): AllAppsConfiguration {
            return AllAppsConfiguration(
                profile = AllAppsProfileConfiguration.from(dp),
                colors = AllAppsColorConfiguration.from(context),
                uiMode = resourcesConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                assetsSeq = resourcesConfig.assetsSeq
            )
        }
    }
}

data class AllAppsProfileConfiguration(
    val columns: Int = 0,
    val iconSizePx: Int = 0,
    val cellWidthPx: Int = 0,
    val cellHeightPx: Int = 0,
    val isTablet: Boolean = false,
    val isAllAppsOnSheet: Boolean = false,
    val allAppsTopPaddingPx: Int = 0
) {
    val hasProfile: Boolean
        get() = columns > 0

    companion object {
        fun from(dp: DeviceProfile): AllAppsProfileConfiguration {
            val profile = dp.allAppsProfile
            return AllAppsProfileConfiguration(
                columns = dp.numShownAllAppsColumns,
                iconSizePx = profile.iconSizePx,
                cellWidthPx = profile.cellWidthPx,
                cellHeightPx = profile.cellHeightPx,
                isTablet = dp.deviceProperties.isTablet,
                isAllAppsOnSheet = dp.shouldShowAllAppsOnSheet(),
                allAppsTopPaddingPx = dp.allAppsPadding.top
            )
        }
    }
}

data class AllAppsColorConfiguration(
    val panel: Int = Color.TRANSPARENT,
    val surfaceLow: Int = Color.TRANSPARENT,
    val headerProtection: Int = Color.TRANSPARENT,
    val dragHandle: Int = Color.TRANSPARENT,
    val searchText: Int = Color.TRANSPARENT
) {
    companion object {
        fun from(context: Context): AllAppsColorConfiguration {
            return AllAppsColorConfiguration(
                panel = allAppsBottomSheetBackgroundColor(context),
                surfaceLow = Themes.getAttrColor(context, R.attr.allAppsSurfaceLow),
                headerProtection = Themes.getAttrColor(
                    context,
                    R.attr.allappsHeaderProtectionColor
                ),
                dragHandle = Themes.getAttrColor(context, R.attr.bottomSheetDragHandleColor),
                searchText = Themes.getAttrColor(context, R.attr.allAppsSearchTextColor)
            )
        }
    }
}

internal fun allAppsBottomSheetBackgroundColor(
    context: Context,
    alpha: Int = LauncherPrefs.get(context).get(LauncherPrefs.ALL_APPS_BG_OPACITY)
): Int {
    if (!isLauncherBlurEnabled(context) || alpha == 255) {
        return context.getColor(InternalR.color.materialColorSurfaceContainer)
    }
    return ColorUtils.setAlphaComponent(
            context.getColor(
                if (Utilities.isDarkTheme(context)) {
                    AndroidR.color.system_accent2_800
                } else {
                    AndroidR.color.system_accent2_200
                }
            ),
            alpha
        )
}

private var launcherBlurSettings: AxBlurSettings? = null

internal fun isLauncherBlurEnabled(context: Context): Boolean {
    val appContext = context.applicationContext ?: context
    val settings = launcherBlurSettings ?: AxBlurSettings.launcher(appContext).also {
        launcherBlurSettings = it
    }
    return settings.enabled
}

private tailrec fun lookupActivityContext(context: Context): ActivityContext? =
    when (context) {
        is ActivityContext -> context
        is ContextWrapper -> lookupActivityContext(context.baseContext)
        else -> null
    }

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AllAppsComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val assetsSeq = LocalConfiguration.current.assetsSeq
    val colorScheme = remember(isDark, assetsSeq) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = rememberAxionTypography(),
        motionScheme = MotionScheme.expressive(),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            content = content
        )
    }
}
