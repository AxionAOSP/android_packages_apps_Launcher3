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
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.launch

@Composable
fun AllAppsComposeGrid(
    items: List<AllAppsComposeItem>,
    sections: List<Pair<String, Int>>,
    numColumns: Int,
    iconSizePx: Int,
    cellHeightPx: Int,
    showLabels: Boolean,
    onAppClick: (AppInfo, BubbleTextView) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)? = null,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    onScrollStarted: () -> Unit,
    onScrollStopped: () -> Unit,
    transitionProgress: Float = 1f,
    keyPrefix: String = "main",
    recompositionKey: Int = 0,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val effectiveColumns = if (numColumns > 0) numColumns else 4
    
    var isScrollEnabled by remember { mutableStateOf(true) }
    var wasFullyClosed by remember { mutableStateOf(true) }

    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }

    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }

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
    
    LaunchedEffect(transitionProgress) {
        if (transitionProgress == 0f) {
            wasFullyClosed = true
        } else if (wasFullyClosed && transitionProgress > 0f) {
            wasFullyClosed = false
            gridState.scrollToItem(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(effectiveColumns),
            state = gridState,
            contentPadding = contentPadding,
            userScrollEnabled = isScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) {
            
            items(
                count = items.size,
                key = { index ->
                    val base = when (val item = items[index]) {
                        is AllAppsComposeItem.AppItem -> "${item.section}_app_${item.appInfo.componentName}"
                        is AllAppsComposeItem.SectionHeader -> "section_${item.letter}"
                        is AllAppsComposeItem.PrivateSpaceHeader -> "private_header"
                        AllAppsComposeItem.PredictionsHeader -> "predictions_header"
                        AllAppsComposeItem.PinnedAppsHeader -> "pinned_header"
                        AllAppsComposeItem.AllAppsHeader -> "all_apps_header"
                        AllAppsComposeItem.EmptySearchResult -> "empty_search"
                    }
                    "${keyPrefix}_${recompositionKey}_$base"
                },
                span = { index ->
                    when (items[index]) {
                        is AllAppsComposeItem.SectionHeader,
                        is AllAppsComposeItem.PrivateSpaceHeader,
                        AllAppsComposeItem.PredictionsHeader,
                        AllAppsComposeItem.PinnedAppsHeader,
                        AllAppsComposeItem.AllAppsHeader,
                        AllAppsComposeItem.EmptySearchResult -> GridItemSpan(effectiveColumns)
                        is AllAppsComposeItem.AppItem -> GridItemSpan(1)
                    }
                },
                contentType = { index ->
                    when (items[index]) {
                        is AllAppsComposeItem.AppItem -> 0
                        is AllAppsComposeItem.SectionHeader -> 1
                        is AllAppsComposeItem.PrivateSpaceHeader -> 2
                        AllAppsComposeItem.PredictionsHeader -> 3
                        AllAppsComposeItem.PinnedAppsHeader -> 4
                        AllAppsComposeItem.AllAppsHeader -> 5
                        AllAppsComposeItem.EmptySearchResult -> 6
                    }
                }
            ) { index ->
                when (val item = items[index]) {
                    is AllAppsComposeItem.AppItem -> {
                        AllAppsComposeAppIcon(
                            appInfo = item.appInfo,
                            showLabel = showLabels,
                            iconSizePx = iconSizePx,
                            cellHeightPx = cellHeightPx,
                            onClick = onAppClick,
                            onLongClick = onAppLongClick,
                            onDragStart = onAppDragStart,
                            onLongPressStatusChanged = { isLongPressed ->
                                isScrollEnabled = !isLongPressed
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is AllAppsComposeItem.SectionHeader -> {
                        SectionHeaderItem(letter = item.letter)
                    }
                    AllAppsComposeItem.PredictionsHeader -> {
                        GroupHeaderItem(title = "Suggested apps")
                    }
                    AllAppsComposeItem.PinnedAppsHeader -> {
                        GroupHeaderItem(title = "Pinned apps")
                    }
                    AllAppsComposeItem.AllAppsHeader -> {
                        GroupHeaderItem(title = "All apps")
                    }
                    is AllAppsComposeItem.PrivateSpaceHeader -> {
                    }
                    AllAppsComposeItem.EmptySearchResult -> {
                    }
                }
            }
        }

        if (sections.isNotEmpty()) {
            AllAppsComposeFastScroller(
                sections = sections,
                gridState = gridState,
                totalItems = items.size,
                onSectionSelected = { index ->
                    scope.launch {
                        gridState.scrollToItem(index)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun GroupHeaderItem(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionHeaderItem(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleSmall,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
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
        color = MaterialTheme.colorScheme.surfaceVariant
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
