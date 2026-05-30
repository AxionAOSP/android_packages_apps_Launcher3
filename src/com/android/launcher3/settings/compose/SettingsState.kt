package com.android.launcher3.settings.compose

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.DisplayController
import com.android.launcher3.settings.SettingsActivity
import kotlinx.coroutines.flow.*

import com.android.launcher3.LauncherPrefChangeListener

class SettingsState(private val context: Context) : ViewModel(), LauncherPrefChangeListener {

    private val launcherPrefs: LauncherPrefs = LauncherPrefs.get(context)

    private val _workspaceLock = MutableStateFlow(launcherPrefs.get(LauncherPrefs.WORKSPACE_LOCK))
    val workspaceLock: StateFlow<Boolean> = _workspaceLock.asStateFlow()

    private val _notificationDots = MutableStateFlow(
        Settings.Secure.getInt(context.contentResolver, "notification_badging", 0) == 1
    )
    val notificationDots: StateFlow<Boolean> = _notificationDots.asStateFlow()

    private val notificationDotsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            _notificationDots.value = Settings.Secure.getInt(
                context.contentResolver,
                "notification_badging",
                1
            ) == 1
        }
    }

    private val _autoAddIcons = MutableStateFlow(launcherPrefs.get(LauncherPrefs.backedUpItem("pref_add_icon_to_home", true)))
    val autoAddIcons: StateFlow<Boolean> = _autoAddIcons.asStateFlow()

    private val _allowRotation = MutableStateFlow(launcherPrefs.get(LauncherPrefs.ALLOW_ROTATION))
    val allowRotation: StateFlow<Boolean> = _allowRotation.asStateFlow()

    private val _fixedLandscape = MutableStateFlow(launcherPrefs.get(LauncherPrefs.FIXED_LANDSCAPE_MODE))
    val fixedLandscape: StateFlow<Boolean> = _fixedLandscape.asStateFlow()

    private val _showGoogleApp = MutableStateFlow(launcherPrefs.get(LauncherPrefs.backedUpItem("pref_enable_minus_one", true)))
    val showGoogleApp: StateFlow<Boolean> = _showGoogleApp.asStateFlow()

    private val _swipeToSearch = MutableStateFlow(launcherPrefs.get(LauncherPrefs.DRAWER_OPEN_KEYBOARD))
    val swipeToSearch: StateFlow<Boolean> = _swipeToSearch.asStateFlow()

    private val _themedIconsEnabled = MutableStateFlow(launcherPrefs.get(LauncherPrefs.backedUpItem("themed_icons", false)))
    val themedIconsEnabled: StateFlow<Boolean> = _themedIconsEnabled.asStateFlow()

    private val _themedIcons = MutableStateFlow(launcherPrefs.get(LauncherPrefs.ALLAPPS_THEMED_ICONS))
    val themedIcons: StateFlow<Boolean> = _themedIcons.asStateFlow()

    private val _doubleTapToSleep = MutableStateFlow(launcherPrefs.get(LauncherPrefs.backedUpItem("pref_sleep_gesture", false)))
    val doubleTapToSleep: StateFlow<Boolean> = _doubleTapToSleep.asStateFlow()

    private val _desktopShowLabels = MutableStateFlow(launcherPrefs.get(LauncherPrefs.SHOW_DESKTOP_LABELS))
    val desktopShowLabels: StateFlow<Boolean> = _desktopShowLabels.asStateFlow()

    private val _drawerShowLabels = MutableStateFlow(launcherPrefs.get(LauncherPrefs.SHOW_DRAWER_LABELS))
    val drawerShowLabels: StateFlow<Boolean> = _drawerShowLabels.asStateFlow()

    private val _drawerLayoutMode = MutableStateFlow(
        launcherPrefs.get(LauncherPrefs.DRAWER_LAYOUT_MODE)
    )
    val drawerLayoutMode: StateFlow<Int> = _drawerLayoutMode.asStateFlow()

    private val _drawerSearchBarPosition =
        MutableStateFlow(launcherPrefs.get(LauncherPrefs.DRAWER_SEARCH_BAR_POSITION))
    val drawerSearchBarPosition: StateFlow<String> = _drawerSearchBarPosition.asStateFlow()

    private val _workspaceIconScale = MutableStateFlow(launcherPrefs.get(LauncherPrefs.WORKSPACE_ICON_SCALE))
    val workspaceIconScale: StateFlow<Float> = _workspaceIconScale.asStateFlow()

    private val _allAppsIconScale = MutableStateFlow(launcherPrefs.get(LauncherPrefs.ALLAPPS_ICON_SCALE))
    val allAppsIconScale: StateFlow<Float> = _allAppsIconScale.asStateFlow()

    private val _allAppsBgOpacity = MutableStateFlow(launcherPrefs.get(LauncherPrefs.ALL_APPS_BG_OPACITY))
    val allAppsBgOpacity: StateFlow<Int> = _allAppsBgOpacity.asStateFlow()

    private val _disableWallpaperZoom = MutableStateFlow(launcherPrefs.get(LauncherPrefs.DISABLE_WALLPAPER_ZOOM))
    val disableWallpaperZoom: StateFlow<Boolean> = _disableWallpaperZoom.asStateFlow()

    private val _allAppsPredictions = MutableStateFlow(launcherPrefs.get(LauncherPrefs.SHOW_ALLAPPS_PREDICTIONS))
    val allAppsPredictions: StateFlow<Boolean> = _allAppsPredictions.asStateFlow()

    init {
        launcherPrefs.addListener(this,
            LauncherPrefs.WORKSPACE_LOCK,
            LauncherPrefs.ALLOW_ROTATION,
            LauncherPrefs.FIXED_LANDSCAPE_MODE,
            LauncherPrefs.DRAWER_OPEN_KEYBOARD,
            LauncherPrefs.ALLAPPS_THEMED_ICONS,
            LauncherPrefs.SHOW_DESKTOP_LABELS,
            LauncherPrefs.SHOW_DRAWER_LABELS,
            LauncherPrefs.DRAWER_LAYOUT_MODE,
            LauncherPrefs.DRAWER_SEARCH_BAR_POSITION,
            LauncherPrefs.WORKSPACE_ICON_SCALE,
            LauncherPrefs.ALLAPPS_ICON_SCALE,
            LauncherPrefs.ALL_APPS_BG_OPACITY,
            LauncherPrefs.DISABLE_WALLPAPER_ZOOM,
            LauncherPrefs.SHOW_ALLAPPS_PREDICTIONS,
            LauncherPrefs.backedUpItem("pref_add_icon_to_home", true),
            LauncherPrefs.backedUpItem("pref_enable_minus_one", true),
            LauncherPrefs.backedUpItem("themed_icons", false),
            LauncherPrefs.backedUpItem("pref_sleep_gesture", false)
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("notification_badging"),
            false,
            notificationDotsObserver
        )
    }

    override fun onPrefChanged(key: String?) {
        when (key) {
            LauncherPrefs.WORKSPACE_LOCK.sharedPrefKey -> _workspaceLock.value = launcherPrefs.get(LauncherPrefs.WORKSPACE_LOCK)
            RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY -> _allowRotation.value = launcherPrefs.get(LauncherPrefs.ALLOW_ROTATION)
            SettingsActivity.FIXED_LANDSCAPE_MODE -> _fixedLandscape.value = launcherPrefs.get(LauncherPrefs.FIXED_LANDSCAPE_MODE)
            LauncherPrefs.DRAWER_OPEN_KEYBOARD.sharedPrefKey -> _swipeToSearch.value = launcherPrefs.get(LauncherPrefs.DRAWER_OPEN_KEYBOARD)
            LauncherPrefs.ALLAPPS_THEMED_ICONS.sharedPrefKey -> _themedIcons.value = launcherPrefs.get(LauncherPrefs.ALLAPPS_THEMED_ICONS)
            LauncherPrefs.SHOW_DESKTOP_LABELS.sharedPrefKey -> _desktopShowLabels.value = launcherPrefs.get(LauncherPrefs.SHOW_DESKTOP_LABELS)
            LauncherPrefs.SHOW_DRAWER_LABELS.sharedPrefKey -> _drawerShowLabels.value = launcherPrefs.get(LauncherPrefs.SHOW_DRAWER_LABELS)
            LauncherPrefs.DRAWER_LAYOUT_MODE.sharedPrefKey -> {
                _drawerLayoutMode.value = launcherPrefs.get(LauncherPrefs.DRAWER_LAYOUT_MODE)
            }
            LauncherPrefs.DRAWER_SEARCH_BAR_POSITION.sharedPrefKey ->
                _drawerSearchBarPosition.value = launcherPrefs.get(LauncherPrefs.DRAWER_SEARCH_BAR_POSITION)
            LauncherPrefs.WORKSPACE_ICON_SCALE.sharedPrefKey -> _workspaceIconScale.value = launcherPrefs.get(LauncherPrefs.WORKSPACE_ICON_SCALE)
            LauncherPrefs.ALLAPPS_ICON_SCALE.sharedPrefKey -> _allAppsIconScale.value = launcherPrefs.get(LauncherPrefs.ALLAPPS_ICON_SCALE)
            LauncherPrefs.ALL_APPS_BG_OPACITY.sharedPrefKey -> _allAppsBgOpacity.value = launcherPrefs.get(LauncherPrefs.ALL_APPS_BG_OPACITY)
            LauncherPrefs.DISABLE_WALLPAPER_ZOOM.sharedPrefKey -> _disableWallpaperZoom.value = launcherPrefs.get(LauncherPrefs.DISABLE_WALLPAPER_ZOOM)
            LauncherPrefs.SHOW_ALLAPPS_PREDICTIONS.sharedPrefKey -> _allAppsPredictions.value = launcherPrefs.get(LauncherPrefs.SHOW_ALLAPPS_PREDICTIONS)
            "pref_add_icon_to_home" -> _autoAddIcons.value = launcherPrefs.get(LauncherPrefs.backedUpItem("pref_add_icon_to_home", true))
            "pref_enable_minus_one" -> _showGoogleApp.value = launcherPrefs.get(LauncherPrefs.backedUpItem("pref_enable_minus_one", true))
            "themed_icons" -> _themedIconsEnabled.value = launcherPrefs.get(LauncherPrefs.backedUpItem("themed_icons", false))
            "pref_sleep_gesture" -> _doubleTapToSleep.value = launcherPrefs.get(LauncherPrefs.backedUpItem("pref_sleep_gesture", true))
        }
    }

    fun setBoolean(key: String, value: Boolean) {
        launcherPrefs.put(LauncherPrefs.backedUpItem(key, value), value)
    }

    fun setFloat(key: String, value: Float) {
        launcherPrefs.put(LauncherPrefs.backedUpItem(key, value), value)
    }

    fun setInt(key: String, value: Int) {
        launcherPrefs.put(LauncherPrefs.backedUpItem(key, value), value)
    }

    fun setAllAppsBgOpacity(value: Int) {
        launcherPrefs.put(LauncherPrefs.ALL_APPS_BG_OPACITY, value)
    }

    fun setDrawerLayoutMode(mode: Int) {
        launcherPrefs.put(LauncherPrefs.DRAWER_LAYOUT_MODE, mode)
    }

    fun setDrawerSearchBarPosition(position: String) {
        launcherPrefs.put(LauncherPrefs.DRAWER_SEARCH_BAR_POSITION, position)
    }

    override fun onCleared() {
        super.onCleared()
        launcherPrefs.removeListener(this,
            LauncherPrefs.WORKSPACE_LOCK,
            LauncherPrefs.ALLOW_ROTATION,
            LauncherPrefs.FIXED_LANDSCAPE_MODE,
            LauncherPrefs.DRAWER_OPEN_KEYBOARD,
            LauncherPrefs.ALLAPPS_THEMED_ICONS,
            LauncherPrefs.SHOW_DESKTOP_LABELS,
            LauncherPrefs.SHOW_DRAWER_LABELS,
            LauncherPrefs.DRAWER_LAYOUT_MODE,
            LauncherPrefs.DRAWER_SEARCH_BAR_POSITION,
            LauncherPrefs.WORKSPACE_ICON_SCALE,
            LauncherPrefs.ALLAPPS_ICON_SCALE,
            LauncherPrefs.ALL_APPS_BG_OPACITY,
            LauncherPrefs.DISABLE_WALLPAPER_ZOOM,
            LauncherPrefs.SHOW_ALLAPPS_PREDICTIONS,
            LauncherPrefs.backedUpItem("pref_add_icon_to_home", true),
            LauncherPrefs.backedUpItem("pref_enable_minus_one", true),
            LauncherPrefs.backedUpItem("themed_icons", false),
            LauncherPrefs.backedUpItem("pref_sleep_gesture", true)
        )
        context.contentResolver.unregisterContentObserver(notificationDotsObserver)
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsState::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsState(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
