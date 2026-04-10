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

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.android.launcher3.allapps.compose.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.launcher3.allapps.compose.data.AppCategoryManager
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState.Companion.TAB_WORK
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import kotlinx.coroutines.launch

@Composable
internal fun DrawerSceneContent(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    items: List<AllAppsComposeItem>,
    sections: List<Pair<String, Int>>,
    workItems: List<AllAppsComposeItem>,
    workSections: List<Pair<String, Int>>,
    categoryManager: AppCategoryManager,
    expandedCategory: AppCategory?,
    onExpandedCategoryChange: (AppCategory?) -> Unit,
    onCustomFolderAction: (AppCategory) -> Unit,
    selectedProfileTab: Int,
    onProfileTabSelected: (Int) -> Unit,
    transitionProgressProvider: () -> Float,
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    openCounter: Int,
    allAppsExpanded: Boolean,
    isOnPrivateSpacePagerPage: Boolean,
    onPrivateSpacePagerChanged: (Boolean) -> Unit,
    onPagerBackAction: ((() -> Unit)?) -> Unit,
    onLaunch: () -> Unit
) {
    val interactions = LocalAllAppsInteractions.current
    DrawerPagerWrapper(
        state = state,
        callbacks = callbacks,
        allAppsExpanded = allAppsExpanded,
        onPrivateSpacePagerChanged = onPrivateSpacePagerChanged,
        onPagerBackAction = onPagerBackAction,
        onLaunch = onLaunch
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.hasWorkApps) {
                PersonalWorkTabs(
                    selectedTab = selectedProfileTab,
                    onTabSelected = onProfileTabSelected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            AnimatedContent(
                targetState = selectedProfileTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(250, easing = EmphasizedDecelerateEasing)) + slideInHorizontally(tween(300, easing = EmphasizedDecelerateEasing)) { it / 6 * direction })
                        .togetherWith(fadeOut(tween(150, easing = EmphasizedAccelerateEasing)) + slideOutHorizontally(tween(200, easing = EmphasizedAccelerateEasing)) { -it / 6 * direction })
                },
                modifier = Modifier.weight(1f),
                label = "drawer_tab"
            ) { tab ->
                when (tab) {
                    TAB_WORK -> WorkTabContent(
                        state = state, workItems = workItems, workSections = workSections,
                        onPauseWork = { callbacks.onWorkProfileToggle(false) },
                        onResumeWork = { callbacks.onWorkProfileToggle(true) },
                        transitionProgressProvider = transitionProgressProvider,
                        openCounter = openCounter
                    )
                    else -> AllAppsComposeGrid(
                        items = items, sections = sections,
                        numColumns = state.numColumns, iconSizePx = state.iconSizePx,
                        cellWidthPx = state.cellWidthPx, cellHeightPx = state.cellHeightPx,
                        showLabels = state.showLabels,
                        onFolderClick = { onExpandedCategoryChange(it) },
                        onFolderLongClick = { if (it.isCustom) onCustomFolderAction(it) },
                        transitionProgressProvider = transitionProgressProvider,
                        keyPrefix = "personal", recompositionKey = openCounter,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 72.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DrawerPagerWrapper(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    allAppsExpanded: Boolean,
    onPrivateSpacePagerChanged: (Boolean) -> Unit,
    onPagerBackAction: ((() -> Unit)?) -> Unit,
    onLaunch: () -> Unit,
    content: @Composable () -> Unit
) {
    val showPrivateSpacePage = state.hasPrivateApps && !state.isPrivateSpaceHidden
    val pagerPageCount = if (showPrivateSpacePage) 2 else 1
    val defaultPage = if (showPrivateSpacePage) 1 else 0
    val pagerState = rememberPagerState(initialPage = defaultPage, pageCount = { pagerPageCount })
    var pagerReady by remember { mutableStateOf(!showPrivateSpacePage) }

    LaunchedEffect(showPrivateSpacePage) {
        if (showPrivateSpacePage && pagerState.pageCount > 1) {
            pagerState.scrollToPage(1)
            pagerReady = true
        } else if (!showPrivateSpacePage) {
            if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
            pagerReady = true
        }
    }

    LaunchedEffect(allAppsExpanded) {
        if (!allAppsExpanded && showPrivateSpacePage && pagerState.currentPage != defaultPage) {
            pagerState.scrollToPage(defaultPage)
        }
    }

    val pagerScope = rememberCoroutineScope()
    val isOnPrivateSpacePage = pagerReady && showPrivateSpacePage && pagerState.settledPage == 0

    LaunchedEffect(isOnPrivateSpacePage) {
        onPrivateSpacePagerChanged(isOnPrivateSpacePage)
        onPagerBackAction(if (isOnPrivateSpacePage) {{ pagerScope.launch { pagerState.animateScrollToPage(defaultPage) } }} else null)
    }

    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.6f
    )

    val isPagerSwiping = pagerState.isScrollInProgress

    CompositionLocalProvider(LocalPagerSwiping provides isPagerSwiping) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            flingBehavior = pagerFlingBehavior
        ) { page ->
            if (showPrivateSpacePage && page == 0) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PrivateSpaceFullPage(
                        state = state, callbacks = callbacks,
                        onLaunch = onLaunch,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (state.isPrivateSpaceLocked) {
                        val scrollProgress = if (showPrivateSpacePage && pagerState.currentPage <= 1) {
                            (1f - pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                                .let { if (pagerState.currentPage == 0) 1f else it }
                        } else 0f
                        PrivateSpaceVeil(
                            progress = scrollProgress,
                            onUnlockClick = { callbacks.onPrivateSpaceClicked(true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                content()
            }
        }
    }
}
