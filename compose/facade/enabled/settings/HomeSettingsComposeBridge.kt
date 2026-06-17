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
package com.android.launcher3.settings.compose

import androidx.core.view.WindowCompat
import com.android.axion.compose.host.AxComposeView
import com.android.axion.compose.theme.AxionTheme
import com.android.launcher3.settings.SettingsActivity

object HomeSettingsComposeBridge {
    @JvmStatic
    fun show(activity: SettingsActivity): Boolean {
        val intent = activity.intent
        val initialRoute = HomeSettingsRoutes.fromIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val content = AxComposeView(activity)
        activity.setContentView(content)
        content.setContent {
            AxionTheme {
                HomeSettingsScreen(
                    activity = activity,
                    initialRoute = initialRoute,
                )
            }
        }
        return true
    }
}
