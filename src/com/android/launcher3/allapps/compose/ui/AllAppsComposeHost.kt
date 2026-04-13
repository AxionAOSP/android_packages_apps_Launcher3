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

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.launcher3.allapps.AllAppsComposeController
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.ui.viewmodel.AllAppsComposeViewModel
import kotlinx.coroutines.launch

@Composable
fun AllAppsComposeHost(
    allAppsStore: AllAppsStore,
    context: Context,
    controller: AllAppsComposeController
) {
    val viewModel = controller.viewModel

    DisposableEffect(viewModel) {
        viewModel.reinitialize()
        onDispose { }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadPreferences()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(controller, viewModel) {
        launch { controller.predictedAppsFlow.collect { viewModel.updatePredictedApps(it) } }
        launch { controller.isPrivateSpaceHiddenFlow.collect { viewModel.setPrivateSpaceHidden(it) } }
        launch {
            snapshotFlow { controller.configUpdate }.collect { config ->
                if (config.columns > 0) {
                    viewModel.onConfigChanged(
                        config.columns,
                        config.iconSizePx,
                        config.cellWidthPx,
                        config.cellHeightPx,
                        config.isTablet
                    )
                }
                if (config.uiMode > 0) {
                    viewModel.onUiModeChanged()
                }
            }
        }
        launch {
            snapshotFlow { controller.transitionProgress }.collect { progress ->
                viewModel.setTransitionProgress(progress)
            }
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val allAppsExpanded by viewModel.allAppsExpanded.collectAsStateWithLifecycle()
    val openCounter by viewModel.openCounter.collectAsStateWithLifecycle()

    val hostCallbacks = remember(controller.callbacks, viewModel) {
        ViewModelCallbacksWrapper(controller.callbacks, viewModel)
    }

    val contentColor = rememberAdaptiveContentColor()

    CompositionLocalProvider(LocalDrawerContentColor provides contentColor) {
        AllAppsComposeContent(
            state = state,
            controller = controller,
            transitionProgressProvider = { controller.transitionProgress },
            allAppsExpanded = allAppsExpanded,
            openCounter = openCounter,
            callbacks = hostCallbacks,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class ViewModelCallbacksWrapper(
    private val delegate: AllAppsComposeCallbacks,
    private val viewModel: AllAppsComposeViewModel
) : AllAppsComposeCallbacks by delegate {
    override fun onSearchQueryChanged(query: String) {
        viewModel.onSearchQueryChanged(query)
        delegate.onSearchQueryChanged(query)
    }

    override fun onTabSelected(tab: Int) {
        viewModel.onTabSelected(tab)
        delegate.onTabSelected(tab)
    }
}
