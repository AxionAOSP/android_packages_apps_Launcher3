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
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.BubbleTextView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.views.ActivityContext

@Composable
fun <T> AllAppsComposeHost(
    allAppsStore: AllAppsStore<T>,
    activityContext: T,
    callbacks: AllAppsComposeCallbacks,
    controller: AllAppsComposeController? = null
) where T : Context, T : ActivityContext {
    val viewModel = remember(allAppsStore) {
        AllAppsComposeViewModel(allAppsStore, activityContext)
    }

    val state by viewModel.state.collectAsState()

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cleanup()
        }
    }

    val numColumns = remember(activityContext) {
        activityContext.deviceProfile?.numShownAllAppsColumns ?: 4
    }

    val allAppsProfile = remember(activityContext) {
        activityContext.deviceProfile?.allAppsProfile
    }

    LaunchedEffect(numColumns, allAppsProfile) {
        viewModel.setNumColumns(numColumns)
        allAppsProfile?.let {
            viewModel.setIconSizing(it.iconSizePx, it.cellWidthPx, it.cellHeightPx)
        }
    }
    
    DisposableEffect(controller) {
        controller?.setUpdateNumColumnsAction { cols ->
            viewModel.setNumColumns(cols)
        }
        controller?.setUpdateTransitionProgressAction { progress ->
            viewModel.setTransitionProgress(progress)
        }
        controller?.setUpdatePredictedAppsAction { apps ->
            viewModel.updatePredictedApps(apps)
        }
        controller?.setUpdatePrivateSpaceHiddenAction { hidden ->
            viewModel.setPrivateSpaceHidden(hidden)
        }
        controller?.setUpdateIconSizingAction { iconSizePx, cellWidthPx, cellHeightPx ->
            viewModel.setIconSizing(iconSizePx, cellWidthPx, cellHeightPx)
        }
        onDispose { 
            controller?.setUpdateNumColumnsAction {} 
            controller?.setUpdateTransitionProgressAction {}
            controller?.setUpdatePredictedAppsAction {}
            controller?.setUpdatePrivateSpaceHiddenAction {}
            controller?.setUpdateIconSizingAction { _, _, _ -> }
        }
    }
    
    val transitionProgress by viewModel.transitionProgress.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    AllAppsComposeContent(
        state = state,
        transitionProgress = transitionProgress,
        callbacks = object : AllAppsComposeCallbacks {
            override fun onAppClicked(appInfo: AppInfo, view: BubbleTextView) {
                callbacks.onAppClicked(appInfo, view)
            }
            
            override fun onAppClickedFromFolder(appInfo: AppInfo) {
                callbacks.onAppClickedFromFolder(appInfo)
            }

            override fun onAppLongClicked(appInfo: AppInfo, view: BubbleTextView) {
                callbacks.onAppLongClicked(appInfo, view)
            }
            
            override fun onAppDragStart(appInfo: AppInfo, view: BubbleTextView) {
                callbacks.onAppDragStart(appInfo, view)
            }

            override fun onSearchQueryChanged(query: String) {
                viewModel.onSearchQueryChanged(query)
                callbacks.onSearchQueryChanged(query)
            }

            override fun onTabSelected(tab: Int) {
                viewModel.onTabSelected(tab)
                callbacks.onTabSelected(tab)
            }

            override fun onScrollStateChanged(canScrollUp: Boolean, canScrollDown: Boolean) {
                callbacks.onScrollStateChanged(canScrollUp, canScrollDown)
            }
            
            override fun onFolderExpandedChanged(expanded: Boolean) {
                callbacks.onFolderExpandedChanged(expanded)
            }
            
            override fun onSearchExpandedChanged(expanded: Boolean) {
                callbacks.onSearchExpandedChanged(expanded)
            }
            
            override fun setDismissFolderHandler(handler: (() -> Unit)?) {
                callbacks.setDismissFolderHandler(handler)
            }
            
            override fun startActivity(intent: Intent) {
                callbacks.startActivity(intent)
            }
            
            override fun requestContactsPermission() {
                callbacks.requestContactsPermission()
            }
            
            override fun requestSmsPermission() {
                callbacks.requestSmsPermission()
            }
            
            override fun requestFilePermission() {
                callbacks.requestFilePermission()
            }

            override fun requestCalendarPermission() {
                callbacks.requestCalendarPermission()
            }
            
            override fun onPrivateSpaceClicked(isLocked: Boolean) {
                callbacks.onPrivateSpaceClicked(isLocked)
            }
            
            override fun onWorkProfileClicked() {
                callbacks.onWorkProfileClicked()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
