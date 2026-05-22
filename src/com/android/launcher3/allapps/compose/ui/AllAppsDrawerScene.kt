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

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.app.AxBoostFwk
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.data.AppCategoryManager
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState.Companion.TAB_PERSONAL
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState.Companion.TAB_WORK
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import kotlinx.coroutines.launch

private const val SETTING_PRIVATE_SPACE_HINT_DISMISSED = "axion_private_space_swipe_hint_dismissed"

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
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    openCounter: Int,
    allAppsExpanded: Boolean,
    reopenTrigger: Int = 0,
    isOnPrivateSpacePagerPage: Boolean,
    onPrivateSpacePagerChanged: (Boolean) -> Unit,
    onPagerBackAction: ((() -> Unit)?) -> Unit,
    onLaunch: () -> Unit,
    isSearchBarAtTop: Boolean
) {
    val interactions = LocalAllAppsInteractions.current
    val legacyLayout = LocalAllAppsLegacyLayout.current
    val horizontalPadding = allAppsDrawerHorizontalPadding(legacyLayout)
    DrawerPagerWrapper(
        state = state,
        callbacks = callbacks,
        allAppsExpanded = allAppsExpanded,
        onPrivateSpacePagerChanged = onPrivateSpacePagerChanged,
        onPagerBackAction = onPagerBackAction,
        onLaunch = onLaunch,
        isSearchBarAtTop = isSearchBarAtTop
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.hasWorkApps) {
                ProfileTabsPager(
                    selectedTab = selectedProfileTab,
                    onTabSelected = onProfileTabSelected,
                    modifier = Modifier.weight(1f),
                    tabsModifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                    personalContent = {
                        AllAppsComposeGrid(
                            items = items, sections = sections,
                            numColumns = state.numColumns, iconSizePx = state.iconSizePx,
                            cellWidthPx = state.cellWidthPx, cellHeightPx = state.cellHeightPx,
                            showLabels = state.showLabels,
                            onFolderClick = { onExpandedCategoryChange(it) },
                            onFolderLongClick = { if (it.isCustom) onCustomFolderAction(it) },
                            reopenTrigger = reopenTrigger,
                            keyPrefix = "personal", recompositionKey = openCounter,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = allAppsSceneContentPadding(
                                isSearchBarAtTop,
                                legacyLayout
                            )
                        )
                    },
                    workContent = {
                        WorkTabContent(
                            state = state, workItems = workItems, workSections = workSections,
                            onPauseWork = { callbacks.onWorkProfileToggle(false) },
                            onResumeWork = { callbacks.onWorkProfileToggle(true) },
                            openCounter = openCounter,
                            isSearchBarAtTop = isSearchBarAtTop,
                            reopenTrigger = reopenTrigger
                        )
                    }
                )
            } else {
                AllAppsComposeGrid(
                    items = items, sections = sections,
                    numColumns = state.numColumns, iconSizePx = state.iconSizePx,
                    cellWidthPx = state.cellWidthPx, cellHeightPx = state.cellHeightPx,
                    showLabels = state.showLabels,
                    onFolderClick = { onExpandedCategoryChange(it) },
                    onFolderLongClick = { if (it.isCustom) onCustomFolderAction(it) },
                    reopenTrigger = reopenTrigger,
                    keyPrefix = "personal", recompositionKey = openCounter,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentPadding = allAppsSceneContentPadding(isSearchBarAtTop, legacyLayout)
                )
            }
        }
    }
}

@Composable
internal fun ProfileTabsPager(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    personalContent: @Composable () -> Unit,
    workContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tabsModifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(TAB_PERSONAL, TAB_WORK),
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab && selectedTab in TAB_PERSONAL..TAB_WORK) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != selectedTab && page in TAB_PERSONAL..TAB_WORK) {
                onTabSelected(page)
            }
        }
    }

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.4f
    )

    Column(modifier = modifier) {
        PersonalWorkTabs(
            selectedTab = pagerState.currentPage,
            onTabSelected = { tab ->
                if (tab in TAB_PERSONAL..TAB_WORK && pagerState.currentPage != tab) {
                    scope.launch { pagerState.animateScrollToPage(tab) }
                }
            },
            modifier = tabsModifier,
            pillFractionProvider = {
                pagerState.currentPage + pagerState.currentPageOffsetFraction
            }
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxSize(),
            beyondViewportPageCount = 1,
            flingBehavior = flingBehavior
        ) { page ->
            when (page) {
                TAB_WORK -> workContent()
                else -> personalContent()
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
    isSearchBarAtTop: Boolean,
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

    val pagerScope = rememberCoroutineScope()
    val isOnPrivateSpacePage = pagerReady && showPrivateSpacePage && pagerState.settledPage == 0

    LaunchedEffect(isOnPrivateSpacePage) {
        onPrivateSpacePagerChanged(isOnPrivateSpacePage)
        onPagerBackAction(if (isOnPrivateSpacePage) {{ pagerScope.launch { pagerState.animateScrollToPage(defaultPage) } }} else null)
    }

    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.5f
    )

    val isPagerSwiping = pagerState.isScrollInProgress

    LaunchedEffect(isPagerSwiping) {
        if (isPagerSwiping) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0)
        } else {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0L)
        }
    }

    CompositionLocalProvider(LocalPagerSwiping provides isPagerSwiping) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            isActive = pagerState.settledPage == 0,
                            isSearchBarAtTop = isSearchBarAtTop,
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

            if (showPrivateSpacePage) {
                val context = LocalContext.current
                val resolver = remember { context.contentResolver }
                var hintDismissed by remember {
                    mutableStateOf(Settings.Secure.getInt(resolver, SETTING_PRIVATE_SPACE_HINT_DISMISSED, 0) == 1)
                }
                LaunchedEffect(pagerState, hintDismissed) {
                    if (hintDismissed) return@LaunchedEffect
                    var hasVisitedMain = false
                    snapshotFlow { pagerState.settledPage }.collect { page ->
                        if (page == 1) {
                            hasVisitedMain = true
                        } else if (page == 0 && hasVisitedMain) {
                            hintDismissed = true
                            Settings.Secure.putInt(resolver, SETTING_PRIVATE_SPACE_HINT_DISMISSED, 1)
                        }
                    }
                }
                val isOnMainPage by remember {
                    derivedStateOf { pagerState.settledPage == 1 && !pagerState.isScrollInProgress }
                }
                if (!hintDismissed) {
                    PrivateSpaceSwipeHint(
                        visible = isOnMainPage,
                        onDismiss = {
                            hintDismissed = true
                            Settings.Secure.putInt(resolver, SETTING_PRIVATE_SPACE_HINT_DISMISSED, 1)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivateSpaceSwipeHint(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ps_hint_pulse")
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ps_hint_offset"
    )
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it },
        exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            onClick = onDismiss,
            modifier = Modifier.graphicsLayer {
                translationX = with(density) { pulseOffset.dp.toPx() }
            }
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.ps_swipe_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
