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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi


import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.AxBoostFwk
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.data.AppCategoryManager
import com.android.launcher3.allapps.compose.domain.categorizeApps
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState.Companion.TAB_WORK
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.model.data.AppInfo
internal val bigIconSize = 64.dp
internal val smallIconSize = 28.dp

@Composable
internal fun AllAppsCategoriesView(
    state: AllAppsComposeState,
    categoryManager: AppCategoryManager,
    expandedCategory: AppCategory?,
    onExpandedCategoryChange: (AppCategory?) -> Unit,
    onCustomFolderAction: (AppCategory) -> Unit,
    transitionProgressProvider: () -> Float,
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    isSearchBarAtTop: Boolean,
    modifier: Modifier = Modifier
) {
    val interactions = LocalAllAppsInteractions.current

    val context = LocalContext.current
    var internalExpandedCategory by remember(expandedCategory) { mutableStateOf(expandedCategory) }
    val overrides by categoryManager.overrides.collectAsStateWithLifecycle()
    val customCats by categoryManager.customCategories.collectAsStateWithLifecycle()
    val appCategories = remember(state.apps, overrides, customCats) {
        categorizeApps(state.apps, context, categoryManager)
    }
    
    val pinnedCategory = remember(state.pinnedApps) {
        if (state.pinnedApps.isNotEmpty()) {
            AppCategory(-2, "Pinned", state.pinnedApps)
        } else null
    }

    val predictionsCategory = remember(state.predictedApps) {
        if (state.predictedApps.isNotEmpty()) {
            AppCategory(-4, "Suggestions", state.predictedApps)
        } else null
    }

    val categories = remember(appCategories, predictionsCategory, pinnedCategory) {
        buildList {
            predictionsCategory?.let { add(it) }
            pinnedCategory?.let { add(it) }
            addAll(appCategories)
        }
    }

    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }

    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }
    val isScrollingProvider = remember<() -> Boolean> { { gridState.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_SCROLL_VERTICAL, -1L)
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0)
        } else {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0L)
        }
    }

    LaunchedEffect(expandedCategory) {
        interactions.controller?.let {
            if (expandedCategory != null) {
                it.canScrollUp = true
                it.canScrollDown = false
            } else {
                it.canScrollUp = gridState.canScrollBackward
                it.canScrollDown = gridState.canScrollForward
            }
        }
    }

    LaunchedEffect(canScrollUp, canScrollDown) {
        if (expandedCategory == null) {
            interactions.controller?.let {
                it.canScrollUp = canScrollUp
                it.canScrollDown = canScrollDown
            }
        }
    }

    LaunchedEffect(dismissRequest) {
        if (dismissRequest && expandedCategory != null) {
            onExpandedCategoryChange(null)
            onDismissRequestChange(false)
        }
    }

    var wasFullyClosed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        snapshotFlow { transitionProgressProvider() }.collect { progress ->
            if (progress == 0f) {
                wasFullyClosed = true
            } else if (wasFullyClosed && progress > 0f) {
                wasFullyClosed = false
                gridState.scrollToItem(0)
            }
        }
    }

    val currentExpandedCategory by rememberUpdatedState(expandedCategory)

    val categoryOrder by categoryManager.categoryOrder.collectAsStateWithLifecycle()
    val topCards = remember(categories) { categories.filter { it.id == -4 || it.id == -2 } }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    var gridSlotDelta by remember { mutableStateOf(Offset.Zero) }
    var hasDragged by remember { mutableStateOf(false) }
    val isDragging = draggedId != null
    val view = LocalView.current

    LaunchedEffect(isDragging) {
        interactions.controller?.let {
            if (isDragging) {
                it.canScrollUp = true
                it.canScrollDown = false
            } else {
                it.canScrollUp = gridState.canScrollBackward
                it.canScrollDown = gridState.canScrollForward
            }
        }
    }

    val sortedGridCards = remember(categories, categoryOrder) {
        val base = categories.filter { it.id != -4 && it.id != -2 }
        if (categoryOrder.isEmpty()) base
        else {
            val orderMap = categoryOrder.withIndex().associate { (i, id) -> id to i }
            base.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
        }
    }

    val gridCards = remember { mutableStateListOf<AppCategory>() }
    LaunchedEffect(sortedGridCards) {
        if (!isDragging) {
            gridCards.clear()
            gridCards.addAll(sortedGridCards)
        }
    }
    if (gridCards.isEmpty() && sortedGridCards.isNotEmpty()) {
        gridCards.clear()
        gridCards.addAll(sortedGridCards)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        userScrollEnabled = !isDragging,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = if (isSearchBarAtTop) 0.dp else 8.dp,
            bottom = if (isSearchBarAtTop) 0.dp else 72.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        topCards.forEach { cat ->
            item(key = cat.id, span = { GridItemSpan(maxLineSpan) }) {
                SmartDrawerRowCard(
                    category = cat,
                    iconSizePx = state.iconSizePx,
                    maxPerRow = state.numColumns,
                    wrapRows = cat.id == -2,
                    onClick = {
                        internalExpandedCategory = cat
                        onExpandedCategoryChange(cat)
                    },
                    isScrollingProvider = isScrollingProvider
                )
            }
        }
        items(count = gridCards.size, key = { gridCards[it].id }) { index ->
            val cat = gridCards[index]
            val isDraggedItem = draggedId == cat.id
            Box(
                modifier = Modifier
                    .zIndex(if (isDraggedItem) 1f else 0f)
                    .graphicsLayer {
                        if (isDraggedItem) {
                            translationX = dragTotal.x - gridSlotDelta.x
                            translationY = dragTotal.y - gridSlotDelta.y
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 16f
                            alpha = 0.92f
                        }
                    }
                    .animateItem(
                        placementSpec = if (isDraggedItem) null
                            else spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    .pointerInput(cat.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = cat.id
                                dragTotal = Offset.Zero
                                gridSlotDelta = Offset.Zero
                                hasDragged = false
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            },
                            onDrag = { change, offset ->
                                change.consume()
                                dragTotal += offset
                                hasDragged = true
                                val currentIdx = gridCards.indexOfFirst { it.id == cat.id }
                                if (currentIdx < 0) return@detectDragGesturesAfterLongPress
                                val layoutInfo = gridState.layoutInfo
                                val topCardsCount = topCards.size
                                val draggedItem = layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == currentIdx + topCardsCount }
                                    ?: return@detectDragGesturesAfterLongPress
                                val visualCenter = Offset(
                                    draggedItem.offset.x + draggedItem.size.width / 2f + dragTotal.x - gridSlotDelta.x,
                                    draggedItem.offset.y + draggedItem.size.height / 2f + dragTotal.y - gridSlotDelta.y
                                )
                                var targetIdx = currentIdx
                                for (item in layoutInfo.visibleItemsInfo) {
                                    val gridIdx = item.index - topCardsCount
                                    if (gridIdx < 0 || gridIdx >= gridCards.size || gridIdx == currentIdx) continue
                                    val itemCenterX = item.offset.x + item.size.width / 2f
                                    val itemCenterY = item.offset.y + item.size.height / 2f
                                    val dx = (visualCenter.x - itemCenterX).toDouble()
                                    val dy = (visualCenter.y - itemCenterY).toDouble()
                                    if (dx * dx + dy * dy < (item.size.width * 0.4f).let { r -> r * r }.toDouble()) {
                                        targetIdx = gridIdx
                                        break
                                    }
                                }
                                if (targetIdx != currentIdx) {
                                    val targetItem = layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == targetIdx + topCardsCount }
                                    if (targetItem != null) {
                                        gridSlotDelta += Offset(
                                            (targetItem.offset.x - draggedItem.offset.x).toFloat(),
                                            (targetItem.offset.y - draggedItem.offset.y).toFloat()
                                        )
                                    }
                                    gridCards.add(targetIdx, gridCards.removeAt(currentIdx))
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            },
                            onDragEnd = {
                                if (!hasDragged && cat.isCustom) {
                                    onCustomFolderAction(cat)
                                }
                                if (hasDragged) {
                                    categoryManager.setCategoryOrder(gridCards.map { it.id })
                                }
                                draggedId = null
                                dragTotal = Offset.Zero
                                gridSlotDelta = Offset.Zero
                                hasDragged = false
                            },
                            onDragCancel = {
                                draggedId = null
                                dragTotal = Offset.Zero
                                gridSlotDelta = Offset.Zero
                                hasDragged = false
                            }
                        )
                    }
            ) {
                CategoryFolder(
                    category = cat,
                    onClick = {
                        if (!isDragging) {
                            internalExpandedCategory = cat
                            onExpandedCategoryChange(cat)
                        }
                    },
                    isScrollingProvider = isScrollingProvider
                )
            }
        }
    }

}

@Composable
internal fun SmartDrawerRowCard(
    category: AppCategory,
    iconSizePx: Int,
    maxPerRow: Int,
    wrapRows: Boolean = false,
    onClick: () -> Unit,
    isScrollingProvider: () -> Boolean = { false }
) {
    CompositionLocalProvider(LocalSectionId provides "smartcard_${category.id}") {
    val interactions = LocalAllAppsInteractions.current
    val displayApps = if (wrapRows) category.apps else category.apps.take(maxPerRow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(surfaceEffectColor())
            .combinedClickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(16.dp)
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalDrawerContentColor.current
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (wrapRows) {
            val rows = displayApps.chunked(maxPerRow)
            Column {
                rows.forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowApps.forEach { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = interactions.onAppClick,
                                onLongClick = interactions.onAppLongClick,
                                onDragStart = interactions.onAppDragStart,
                                onDragMove = interactions.onAppDragMove,
                                onDragEnd = interactions.onAppDragEnd,
                                isScrollingProvider = isScrollingProvider
                            )
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                displayApps.forEach { app ->
                    AllAppsComposeAppIcon(
                        appInfo = app,
                        showLabel = false,
                        iconSizePx = iconSizePx,
                        onClick = interactions.onAppClick,
                        onLongClick = interactions.onAppLongClick,
                        onDragStart = interactions.onAppDragStart,
                        onDragMove = interactions.onAppDragMove,
                        onDragEnd = interactions.onAppDragEnd,
                        isScrollingProvider = isScrollingProvider
                    )
                }
            }
        }
    }
    }
}

@Composable
internal fun CategoryFolder(
    category: AppCategory,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isLocked: Boolean = false,
    isWorkProfile: Boolean = false,
    isPrivateCategory: Boolean = false,
    isScrollingProvider: () -> Boolean = { false }
) {
    CompositionLocalProvider(LocalSectionId provides "catfolder_${category.id}") {
    val interactions = LocalAllAppsInteractions.current
    val hasMoreThanFour = category.apps.size > 4
    val density = LocalDensity.current
    val iconSizePx = with(density) { bigIconSize.roundToPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(surfaceEffectColor())
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(16.dp)
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalDrawerContentColor.current
        )
        Spacer(modifier = Modifier.height(12.dp))

        val folderContentHeight = bigIconSize * 2 + 12.dp
        Box(
            modifier = Modifier.fillMaxWidth().height(folderContentHeight),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLocked -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.ps_container_lock_button_content_description),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                isWorkProfile -> {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = stringResource(R.string.all_apps_work_tab),
                        tint = LocalDrawerContentColor.current,
                        modifier = Modifier.size(64.dp)
                    )
                }
                isPrivateCategory -> {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.private_space_label),
                        tint = LocalDrawerContentColor.current,
                        modifier = Modifier.size(64.dp)
                    )
                }
                hasMoreThanFour -> {
                    Column(
                        modifier = Modifier.wrapContentSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            category.apps.getOrNull(0)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                            category.apps.getOrNull(1)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            category.apps.getOrNull(2)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                            Box(
                                modifier = Modifier.size(bigIconSize),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        category.apps.getOrNull(3)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                        category.apps.getOrNull(4)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        category.apps.getOrNull(5)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                        category.apps.getOrNull(6)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val previewApps = category.apps.take(4)
                    Column(
                        modifier = Modifier.wrapContentSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            previewApps.getOrNull(0)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                            previewApps.getOrNull(1)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                        }
                        if (previewApps.size > 2) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                previewApps.getOrNull(2)?.let { app ->
                                    AllAppsComposeAppIcon(
                                        appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                        onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                        onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                        onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                    )
                                }
                                previewApps.getOrNull(3)?.let { app ->
                                    AllAppsComposeAppIcon(
                                        appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                        onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                        onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                        onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun PreviewBaseAppIcon(app: AppInfo, iconSize: Dp) {
    val iconDrawable = AllAppsIconProvider.rememberAppIcon(app, iconSize)

    Box(
        modifier = Modifier
            .size(iconSize)
            .drawBehind {
                drawContext.canvas.nativeCanvas.let { canvas ->
                    iconDrawable.draw(canvas)
                }
            }
    )
}

@Composable
internal fun SmartDrawerSceneContent(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
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
    isOpening: Boolean = false,
    reopenTrigger: Int = 0,
    isOnPrivateSpacePagerPage: Boolean,
    onPrivateSpacePagerChanged: (Boolean) -> Unit,
    onPagerBackAction: ((() -> Unit)?) -> Unit,
    onLaunch: () -> Unit,
    isSearchBarAtTop: Boolean
) {
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
                    tabsModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    personalContent = {
                        AllAppsCategoriesView(
                            state = state,
                            categoryManager = categoryManager,
                            expandedCategory = expandedCategory,
                            onExpandedCategoryChange = onExpandedCategoryChange,
                            onCustomFolderAction = onCustomFolderAction,
                            transitionProgressProvider = transitionProgressProvider,
                            dismissRequest = dismissRequest,
                            onDismissRequestChange = onDismissRequestChange,
                            isSearchBarAtTop = isSearchBarAtTop,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    workContent = {
                        WorkTabContent(
                            state = state, workItems = workItems, workSections = workSections,
                            onPauseWork = { callbacks.onWorkProfileToggle(false) },
                            onResumeWork = { callbacks.onWorkProfileToggle(true) },
                            transitionProgressProvider = transitionProgressProvider,
                            openCounter = openCounter,
                            isSearchBarAtTop = isSearchBarAtTop,
                            isOpening = isOpening,
                            reopenTrigger = reopenTrigger
                        )
                    }
                )
            } else {
                AllAppsCategoriesView(
                    state = state,
                    categoryManager = categoryManager,
                    expandedCategory = expandedCategory,
                    onExpandedCategoryChange = onExpandedCategoryChange,
                    onCustomFolderAction = onCustomFolderAction,
                    transitionProgressProvider = transitionProgressProvider,
                    dismissRequest = dismissRequest,
                    onDismissRequestChange = onDismissRequestChange,
                    isSearchBarAtTop = isSearchBarAtTop,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
    }
}

