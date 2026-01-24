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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.launch

@Composable
fun AllAppsComposeGrid(
    items: List<AllAppsComposeItem>,
    sections: List<Pair<String, Int>>,
    numColumns: Int,
    iconSizePx: Int,
    cellWidthPx: Int,
    cellHeightPx: Int,
    showLabels: Boolean,
    onAppClick: (ComposeIconInfo) -> Unit,
    onAppLongClick: (ComposeIconInfo) -> Unit,
    onAppDragStart: ((ComposeIconInfo) -> Unit)? = null,
    onAppDragMove: ((screenX: Float, screenY: Float) -> Unit)? = null,
    onAppDragEnd: ((screenX: Float, screenY: Float) -> Unit)? = null,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    onScrollStarted: () -> Unit,
    onScrollStopped: () -> Unit,
    transitionProgressProvider: () -> Float = { 1f },
    keyPrefix: String = "main",
    recompositionKey: Int = 0,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val scrollState = rememberScrollState()
    val effectiveColumns = if (numColumns > 0) numColumns else 4

    var isScrollEnabled by remember { mutableStateOf(true) }
    var wasFullyClosed by remember { mutableStateOf(true) }

    val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }
    val canScrollDown by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

    val isScrollInProgress by remember { derivedStateOf { scrollState.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            onScrollStarted()
        } else {
            onScrollStopped()
        }
    }

    LaunchedEffect(canScrollUp, canScrollDown) {
        onScrollStateChanged(canScrollUp, canScrollDown)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { transitionProgressProvider() }.collect { progress ->
            if (progress == 0f) {
                wasFullyClosed = true
            } else if (wasFullyClosed && progress > 0f) {
                wasFullyClosed = false
                scrollState.scrollTo(0)
            }
        }
    }

    val gridWidth = with(LocalDensity.current) { (effectiveColumns * cellWidthPx).toDp() }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = isScrollEnabled)
                .padding(contentPadding)
        ) {
            val chunkedItems = remember(items, effectiveColumns) {
                val result = mutableListOf<List<AllAppsComposeItem>>()
                var currentGroup = mutableListOf<AllAppsComposeItem>()
                
                items.forEach { item ->
                    if (item is AllAppsComposeItem.AppItem) {
                        currentGroup.add(item)
                        if (currentGroup.size == effectiveColumns) {
                            result.add(currentGroup)
                            currentGroup = mutableListOf()
                        }
                    } else {
                        if (currentGroup.isNotEmpty()) {
                            result.add(currentGroup)
                            currentGroup = mutableListOf()
                        }
                        result.add(listOf(item))
                    }
                }
                if (currentGroup.isNotEmpty()) {
                    result.add(currentGroup)
                }
                result
            }

            chunkedItems.forEachIndexed { rowIndex, rowItems ->
                if (rowItems.size == 1 && rowItems[0] !is AllAppsComposeItem.AppItem) {
                    val item = rowItems[0]
                    when (item) {
                        is AllAppsComposeItem.SectionHeader -> {
                            if (rowIndex > 0) SectionHeaderItem(letter = item.letter, gridWidth = gridWidth)
                        }
                        AllAppsComposeItem.PredictionsHeader -> {
                            if (rowIndex > 0) GroupHeaderItem(title = "Suggested apps", gridWidth = gridWidth)
                        }
                        AllAppsComposeItem.PinnedAppsHeader -> {
                            if (rowIndex > 0) GroupHeaderItem(title = "Pinned apps", gridWidth = gridWidth)
                        }
                        AllAppsComposeItem.AllAppsHeader -> {
                            if (rowIndex > 0) GroupHeaderItem(title = "All apps", gridWidth = gridWidth)
                        }
                        is AllAppsComposeItem.PrivateSpaceHeader -> PrivateSpaceHeaderItem(isExpanded = item.isExpanded)
                        AllAppsComposeItem.EmptySearchResult -> EmptySearchResultItem()
                        else -> {}
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            if (item is AllAppsComposeItem.AppItem) {
                                AllAppsComposeAppIcon(
                                    appInfo = item.appInfo,
                                    showLabel = showLabels,
                                    iconSizePx = iconSizePx,
                                    cellWidthPx = cellWidthPx,
                                    cellHeightPx = cellHeightPx,
                                    onClick = onAppClick,
                                    onLongClick = onAppLongClick,
                                    onDragStart = onAppDragStart,
                                    onDragMove = onAppDragMove,
                                    onDragEnd = onAppDragEnd,
                                    isScrolling = isScrollInProgress,
                                    onLongPressStatusChanged = { isLongPressed ->
                                        isScrollEnabled = !isLongPressed
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (rowItems.size < effectiveColumns) {
                            repeat(effectiveColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeaderItem(title: String, gridWidth: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier
                .width(if (gridWidth > 0.dp) gridWidth - 16.dp else 300.dp)
                .clip(CircleShape),
            thickness = 2.dp
        )
    }
}

@Composable
private fun SectionHeaderItem(letter: String, gridWidth: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier
                .width(if (gridWidth > 0.dp) gridWidth - 16.dp else 300.dp)
                .clip(CircleShape),
            thickness = 2.dp
        )
    }
}

@Composable
private fun PrivateSpaceHeaderItem(isExpanded: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Private Space",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptySearchResultItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No apps found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
