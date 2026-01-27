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
package com.android.launcher3.allapps.compose

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.android.axion.compose.lifecycle.repeatWhenAttached
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.views.ActivityContext

object AllAppsComposeSetup {
    @JvmStatic
    fun <T> setupComposeView(
        composeView: ComposeView,
        allAppsStore: AllAppsStore<T>,
        activityContext: T,
        callbacks: AllAppsComposeCallbacks,
        controller: AllAppsComposeController? = null,
        rebindKey: Int = 0
    ) where T : Context, T : ActivityContext {
        composeView.apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
                    )
                    setContent {
                        key(rebindKey) {
                            AllAppsComposeTheme {
                                val host = @Composable {
                                    AllAppsComposeHost(allAppsStore, activityContext, callbacks, controller)
                                }
                                (activityContext as? OnBackPressedDispatcherOwner)?.let {
                                    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides it, content = host)
                                } ?: host()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllAppsComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
