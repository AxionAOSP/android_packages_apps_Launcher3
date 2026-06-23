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

import android.app.Activity
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.os.Process.myUserHandle
import android.provider.Settings
import android.util.Log
import android.window.SplashScreen.SPLASH_SCREEN_STYLE_UNDEFINED
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.android.launcher3.BaseActivity
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.qsb.OSEManager.Companion.OSE_LOOPER
import com.android.launcher3.qsb.OSEManager.OSEInfo
import com.android.launcher3.qsb.QsbAppWidgetHost.Callbacks
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.util.WidgetSizeHandler
import javax.inject.Inject

/**
 * Manager for default search widget
 *
 * Listens to OSEManager for any OSE changes and provides the updated widget configurations
 */
@LauncherAppSingleton
class OseWidgetManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    oseManager: OSEManager,
    private val widgetHost: QsbAppWidgetHost,
    private val sizeHandler: WidgetSizeHandler,
    private val idp: InvariantDeviceProfile,
    private val launcherPrefs: LauncherPrefs,
    tracker: DaggerSingletonTracker,
) {

    private val mutableProviderInfo = MutableListenableRef<AppWidgetProviderInfo?>(null)
    val providerInfo = mutableProviderInfo.asListenable()

    private val executor = OSE_LOOPER
    private var lastOseInfo = OSEInfo()
    private val searchPreferenceListener =
        LauncherPrefChangeListener {
            executor.execute { updateSearchWidget(lastOseInfo) }
        }

    init {
        widgetHost.setCallbacks(
            object : Callbacks {

                override fun onProviderChanged(appWidget: AppWidgetProviderInfo?) =
                    mutableProviderInfo.dispatchValue(appWidget)
            }
        )
        widgetHost.startListening()

        tracker.addCloseable(oseManager.oseInfo.forEach(executor, this::handleOseInfoUpdate))
        launcherPrefs.addListener(
            searchPreferenceListener,
            LauncherPrefsExt.HOTSEAT_SEARCH_BAR,
            LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER,
        )

        val idpListener = OnIDPChangeListener { updateWidgetSizeAsync() }
        idp.addOnChangeListener(idpListener)
        tracker.addCloseable {
            launcherPrefs.removeListener(
                searchPreferenceListener,
                LauncherPrefsExt.HOTSEAT_SEARCH_BAR,
                LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER,
            )
            idp.removeOnChangeListener(idpListener)
            widgetHost.stopListening()
        }
    }

    private fun handleOseInfoUpdate(info: OSEInfo) {
        lastOseInfo = info
        updateSearchWidget(info)
    }

    private fun updateSearchWidget(info: OSEInfo) {
        val searchWidget = resolveSearchWidget(context, launcherPrefs, info.pkg)
        val currentWidgetId = widgetHost.getBoundWidgetId()
        val currentInfo =
            if (currentWidgetId != INVALID_APPWIDGET_ID)
                AppWidgetManager.getInstance(context).getAppWidgetInfo(currentWidgetId)
            else null

        // Everything is in order
        if (currentInfo?.provider == searchWidget?.provider) {
            widgetHost.setActiveWidget(currentWidgetId, currentInfo)
            updateWidgetSizeAsync()
            return
        }

        // If there is no possible search widget, switch to a null view
        if (searchWidget == null) {
            widgetHost.setActiveWidget(INVALID_APPWIDGET_ID, null)
            dispatchNullValues()
            return
        }

        // Try to bind a new search widget
        val widgetId = widgetHost.allocateAppWidgetId()
        val bindOptions = sizeHandler.getHotseatQsbSizeOptions()
        val bindSuccess =
            AppWidgetManager.getInstance(context)
                .bindAppWidgetIdIfAllowed(
                    widgetId,
                    searchWidget.profile,
                    searchWidget.provider,
                    bindOptions,
                )

        if (bindSuccess) {
            widgetHost.setActiveWidget(widgetId, searchWidget)
            updateWidgetSizeAsync()
        } else {
            widgetHost.deleteAppWidgetId(widgetId)
            widgetHost.setActiveWidget(INVALID_APPWIDGET_ID, null)
            dispatchNullValues()
        }
    }

    private fun updateWidgetSizeAsync() {
        val widgetId = widgetHost.getActiveWidgetId()
        if (widgetId != INVALID_APPWIDGET_ID) {
            sizeHandler.updateHotseatQsbSizeRangesAsync(widgetId, executor)
        }
    }

    private fun dispatchNullValues() {
        if (mutableProviderInfo.value != null) mutableProviderInfo.dispatchValue(null)
    }

    fun getActiveWidgetId() = widgetHost.getActiveWidgetId()

    fun createWidgetView(context: Context): QsbWidgetHostView? =
        widgetHost.createActiveWidgetView(context)

    fun canConfigure(): Boolean =
        widgetHost.getActiveWidgetId() != INVALID_APPWIDGET_ID &&
            providerInfo.value?.configure != null

    fun startConfigActivity(activity: Activity): Boolean {
        val widgetId = widgetHost.getActiveWidgetId()
        if (widgetId == INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Couldn't find a valid widgetId")
            return false
        }
        try {
            val options =
                (activity as? BaseActivity)
                    ?.makeDefaultActivityOptions(SPLASH_SCREEN_STYLE_UNDEFINED)
                    ?.toBundle()
            widgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                widgetId,
                0,
                REQUEST_RECONFIGURE_APPWIDGET,
                options,
            )
            return true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security Exception " + e)
        }
        return false
    }

    companion object {
        private const val TAG = "OseWidgetManager"
        const val SEARCH_PROVIDER_NONE = "none"

        @JvmStatic
        fun isSearchBarEnabled(context: Context): Boolean =
            isSearchBarEnabled(LauncherPrefs.get(context))

        @JvmStatic
        fun isSearchBarEnabled(launcherPrefs: LauncherPrefs): Boolean {
            val provider = launcherPrefs.get(LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER)
            return launcherPrefs.get(LauncherPrefsExt.HOTSEAT_SEARCH_BAR) &&
                provider.isNotBlank() &&
                provider != SEARCH_PROVIDER_NONE
        }

        @JvmStatic
        fun getAvailableSearchWidgets(context: Context): List<AppWidgetProviderInfo> =
            getSearchWidgets(context, null)
                .distinctBy { it.provider.packageName }

        @JvmStatic
        fun getSearchWidgetPackageName(context: Context): String? {
            if (!isSearchBarEnabled(context)) return null
            return getSearchWidgetProviderInfo(context)?.provider?.packageName
                ?: getSelectedSearchPackageName(context)
        }

        @JvmStatic
        fun getSearchWidgetProviderInfo(context: Context): AppWidgetProviderInfo? =
            resolveSearchWidget(
                context,
                LauncherPrefs.get(context),
                getSelectedSearchPackageName(context),
            )

        @VisibleForTesting
        fun findSearchWidgetForPackage(context: Context, pkg: String): AppWidgetProviderInfo? {
            return getSearchWidgets(context, PackageUserKey(pkg, myUserHandle())).firstOrNull()
        }

        private fun resolveSearchWidget(
            context: Context,
            launcherPrefs: LauncherPrefs,
            fallbackPackage: String?,
        ): AppWidgetProviderInfo? {
            if (!isSearchBarEnabled(launcherPrefs)) return null
            val selectedProvider = launcherPrefs.get(LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER)
            ComponentName.unflattenFromString(selectedProvider)?.let {
                findSearchWidgetForPackage(context, it.packageName)?.let { widget -> return widget }
            }
            return fallbackPackage?.let { findSearchWidgetForPackage(context, it) }
                ?: getAvailableSearchWidgets(context).firstOrNull()
        }

        private fun getSelectedSearchPackageName(context: Context): String? {
            Settings.Secure.getString(
                context.contentResolver,
                OSEManager.SEARCH_ENGINE_SETTINGS_KEY,
            )?.let { return it }
            return context.getSystemService(SearchManager::class.java)
                ?.globalSearchActivity
                ?.packageName
        }

        private fun isEligibleSearchWidget(info: AppWidgetProviderInfo): Boolean =
            isEligibleWidget(info) && (info.widgetCategory and WIDGET_CATEGORY_SEARCHBOX) != 0

        private fun getSearchWidgets(
            context: Context,
            packageUserKey: PackageUserKey?,
        ): List<AppWidgetProviderInfo> =
            WidgetManagerHelper(context)
                .getAllProviders(packageUserKey)
                .filter { isEligibleSearchWidget(it) }

        private fun isEligibleWidget(info: AppWidgetProviderInfo): Boolean =
            info.configure == null ||
                ((info.widgetFeatures and WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0)
    }
}
