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

package com.android.launcher3.settings

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.android.launcher3.settings.compose.PulseSettingsScreen
import com.android.launcher3.settings.compose.PulseTheme
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

        val initialPage = intent.getIntExtra("initial_page", 0)
        
        setContent {
            PulseTheme {
                PulseSettingsScreen(
                    onBack = { finish() },
                    initialPage = initialPage
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
