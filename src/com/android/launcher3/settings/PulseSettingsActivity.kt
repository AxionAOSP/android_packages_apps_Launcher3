package com.android.launcher3.settings

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.android.launcher3.settings.compose.PulseSettingsScreen
import com.android.launcher3.settings.compose.PulseTheme
import com.android.launcher3.settings.compose.SettingsDestination
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.DisplayController

class PulseSettingsActivity : ComponentActivity(), LauncherPrefChangeListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val launcherPrefs = LauncherPrefs.get(this)
        launcherPrefs.addListener(this, LauncherPrefs.ALLOW_ROTATION, LauncherPrefs.FIXED_LANDSCAPE_MODE)
        updateOrientation()

        val initialDestination = try {
            val name = intent.getStringExtra("destination")
            if (name != null) SettingsDestination.valueOf(name) else SettingsDestination.ROOT
        } catch (e: IllegalArgumentException) {
            SettingsDestination.ROOT
        }

        setContent {
            PulseTheme {
                PulseSettingsScreen(
                    onBack = { finish() },
                    initialDestination = initialDestination
                )
            }
        }
    }

    override fun onPrefChanged(key: String?) {
        updateOrientation()
    }

    private fun updateOrientation() {
        val launcherPrefs = LauncherPrefs.get(this)
        val allowRotation = launcherPrefs.get(LauncherPrefs.ALLOW_ROTATION)
        val fixedLandscape = launcherPrefs.get(LauncherPrefs.FIXED_LANDSCAPE_MODE)

        if (fixedLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else if (allowRotation) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            val info = DisplayController.INSTANCE.get(this).info
            if (info.isTablet(info.realBounds)) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LauncherPrefs.get(this).removeListener(this, LauncherPrefs.ALLOW_ROTATION, LauncherPrefs.FIXED_LANDSCAPE_MODE)
    }
}
