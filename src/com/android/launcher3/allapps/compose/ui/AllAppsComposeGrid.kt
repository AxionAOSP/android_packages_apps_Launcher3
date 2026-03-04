package com.android.launcher3.allapps.compose.ui

import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AppCategory

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.android.launcher3.model.data.AppInfo

private enum class RowPosition { FIRST, MIDDLE, LAST, ONLY }

private sealed interface GridCellItem {
    val stableKey: Any

    data class App(val appInfo: AppInfo) : GridCellItem {
        override val stableKey: Any
            get() = appInfo.componentName?.flattenToString() ?: appInfo.hashCode()
    }

    data class Folder(val category: AppCategory) : GridCellItem {
        override val stableKey: Any get() = "folder_${category.id}"
    }
}

private sealed interface LazyGridItem {
    data class GridRow(
        val sectionId: String,
        val rowIndex: Int,
        val rowItems: List<GridCellItem>,
        val position: RowPosition
    ) : LazyGridItem

    data class PrivateSpace(val isExpanded: Boolean) : LazyGridItem
    data object EmptySearch : LazyGridItem
    data class SectionSpacer(val sectionId: String) : LazyGridItem
}

private fun buildLazyGridItems(
    items: List<AllAppsComposeItem>,
    columns: Int
): List<LazyGridItem> {
    data class RawSection(val type: String, val items: List<AllAppsComposeItem>)

    val rawSections = mutableListOf<RawSection>()
    var currentType = "allapps"
    var currentItems = mutableListOf<AllAppsComposeItem>()

    fun flushSection() {
        if (currentItems.isNotEmpty()) {
            rawSections.add(RawSection(currentType, currentItems.toList()))
            currentItems = mutableListOf()
        }
    }

    items.forEach { item ->
        when (item) {
            is AllAppsComposeItem.PredictionsHeader -> {
                flushSection()
                currentType = "predictions"
            }
            is AllAppsComposeItem.PinnedAppsHeader -> {
                flushSection()
                currentType = "pinned"
            }
            is AllAppsComposeItem.AllAppsHeader -> {
                flushSection()
                currentType = "allapps"
            }
            is AllAppsComposeItem.PrivateSpaceHeader -> {
                flushSection()
                rawSections.add(RawSection("private", listOf(item)))
            }
            is AllAppsComposeItem.EmptySearchResult -> {
                flushSection()
                rawSections.add(RawSection("empty", listOf(item)))
            }
            is AllAppsComposeItem.SectionHeader -> currentItems.add(item)
            else -> currentItems.add(item)
        }
    }
    flushSection()

    val result = mutableListOf<LazyGridItem>()
    rawSections.forEachIndexed { sectionIndex, section ->
        if (sectionIndex > 0) {
            result.add(LazyGridItem.SectionSpacer("spacer_$sectionIndex"))
        }
        when (section.type) {
            "private" -> {
                val header = section.items.firstOrNull() as? AllAppsComposeItem.PrivateSpaceHeader
                if (header != null) result.add(LazyGridItem.PrivateSpace(header.isExpanded))
            }
            "empty" -> result.add(LazyGridItem.EmptySearch)
            else -> {
                val gridItems = section.items.mapNotNull { item ->
                    when (item) {
                        is AllAppsComposeItem.AppItem -> GridCellItem.App(item.appInfo)
                        is AllAppsComposeItem.FolderItem -> GridCellItem.Folder(item.category)
                        else -> null
                    }
                }
                if (gridItems.isNotEmpty()) {
                    val rows = gridItems.chunked(columns)
                    rows.forEachIndexed { rowIdx, rowItems ->
                        val position = when {
                            rows.size == 1 -> RowPosition.ONLY
                            rowIdx == 0 -> RowPosition.FIRST
                            rowIdx == rows.lastIndex -> RowPosition.LAST
                            else -> RowPosition.MIDDLE
                        }
                        result.add(
                            LazyGridItem.GridRow(
                                sectionId = section.type,
                                rowIndex = rowIdx,
                                rowItems = rowItems,
                                position = position
                            )
                        )
                    }
                }
            }
        }
    }
    return result
}

@Composable
fun AllAppsComposeGrid(
    items: List<AllAppsComposeItem>,
    sections: List<Pair<String, Int>>,
    numColumns: Int,
    iconSizePx: Int,
    cellWidthPx: Int,
    cellHeightPx: Int,
    showLabels: Boolean,
    onFolderClick: ((AppCategory) -> Unit)? = null,
    onFolderLongClick: ((AppCategory) -> Unit)? = null,
    transitionProgressProvider: () -> Float = { 1f },
    keyPrefix: String = "main",
    recompositionKey: Int = 0,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val interactions = LocalAllAppsInteractions.current
    val listState = rememberLazyListState()
    val effectiveColumns = if (numColumns > 0) numColumns else 4

    var isScrollEnabled by remember { mutableStateOf(true) }
    val onLongPressStatusChanged = remember<(Boolean) -> Unit> { { isLongPressed -> isScrollEnabled = !isLongPressed } }
    var wasFullyClosed by remember { mutableStateOf(true) }
    var prevItemCount by remember { mutableIntStateOf(0) }

    if (items.size != prevItemCount) {
        prevItemCount = items.size
    }

    LaunchedEffect(effectiveColumns) {
        listState.scrollToItem(0)
        interactions.controller?.let {
            it.canScrollUp = false
            it.canScrollDown = listState.canScrollForward
        }
    }

    LaunchedEffect(items.size) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.canScrollBackward to listState.canScrollForward
        }.collect { (canScrollUp, canScrollDown) ->
            interactions.controller?.let {
                it.canScrollUp = canScrollUp
                it.canScrollDown = canScrollDown
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { transitionProgressProvider() }.collect { progress ->
            if (progress == 0f) {
                wasFullyClosed = true
            } else if (wasFullyClosed && progress > 0f) {
                wasFullyClosed = false
                listState.scrollToItem(0)
            }
        }
    }

    val lazyItems = remember(items, effectiveColumns) {
        buildLazyGridItems(items, effectiveColumns)
    }

    val cardBg = surfaceEffectColor()
    val cornerRadius = 24.dp

    val isScrollingProvider = remember<() -> Boolean> { { listState.isScrollInProgress } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            userScrollEnabled = isScrollEnabled,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            lazyItems.forEach { lazyItem ->
                when (lazyItem) {
                    is LazyGridItem.PrivateSpace -> {
                        item(key = "private_header", contentType = "private") {
                            PrivateSpaceHeaderItem(isExpanded = lazyItem.isExpanded)
                        }
                    }
                    is LazyGridItem.EmptySearch -> {
                        item(key = "empty_search", contentType = "empty") {
                            EmptySearchResultItem()
                        }
                    }
                    is LazyGridItem.SectionSpacer -> {
                        item(key = "spacer_${lazyItem.sectionId}", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    is LazyGridItem.GridRow -> {
                        item(key = "row_${lazyItem.sectionId}_${lazyItem.rowIndex}", contentType = "row") {
                            val shape = when (lazyItem.position) {
                                RowPosition.ONLY -> RoundedCornerShape(cornerRadius)
                                RowPosition.FIRST -> RoundedCornerShape(
                                    topStart = cornerRadius, topEnd = cornerRadius
                                )
                                RowPosition.MIDDLE -> RoundedCornerShape(0.dp)
                                RowPosition.LAST -> RoundedCornerShape(
                                    bottomStart = cornerRadius, bottomEnd = cornerRadius
                                )
                            }

                            val topPad = if (lazyItem.position == RowPosition.FIRST || lazyItem.position == RowPosition.ONLY) 12.dp else 0.dp
                            val bottomPad = if (lazyItem.position == RowPosition.LAST || lazyItem.position == RowPosition.ONLY) 12.dp else 0.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(cardBg)
                                    .padding(
                                        start = 4.dp,
                                        end = 4.dp,
                                        top = topPad,
                                        bottom = bottomPad
                                    )
                            ) {
                                lazyItem.rowItems.forEach { gridItem ->
                                    key(gridItem.stableKey) {
                                        when (gridItem) {
                                            is GridCellItem.App -> {
                                                AllAppsComposeAppIcon(
                                                    appInfo = gridItem.appInfo,
                                                    showLabel = showLabels,
                                                    iconSizePx = iconSizePx,
                                                    cellWidthPx = cellWidthPx,
                                                    cellHeightPx = cellHeightPx,
                                                    onClick = interactions.onAppClick,
                                                    onLongClick = interactions.onAppLongClick,
                                                    onDragStart = interactions.onAppDragStart,
                                                    onDragMove = interactions.onAppDragMove,
                                                    onDragEnd = interactions.onAppDragEnd,
                                                    isScrollingProvider = isScrollingProvider,
                                                    onLongPressStatusChanged = onLongPressStatusChanged,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            is GridCellItem.Folder -> {
                                                CustomFolderIcon(
                                                    category = gridItem.category,
                                                    iconSizePx = iconSizePx,
                                                    cellHeightPx = cellHeightPx,
                                                    onClick = { onFolderClick?.invoke(gridItem.category) },
                                                    onLongClick = { onFolderLongClick?.invoke(gridItem.category) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (lazyItem.rowItems.size < effectiveColumns) {
                                    repeat(effectiveColumns - lazyItem.rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
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
private fun PrivateSpaceHeaderItem(isExpanded: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = surfaceEffectColor()
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
            color = LocalDrawerContentColor.current
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomFolderIcon(
    category: AppCategory,
    iconSizePx: Int,
    cellHeightPx: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val previewIconSize = 20.dp
    val previewApps = category.apps.take(4)

    Column(
        modifier = modifier
            .height(with(density) { cellHeightPx.toDp() })
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(with(density) { iconSizePx.toDp() })
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    previewApps.getOrNull(0)?.let { app ->
                        MiniAppIcon(app, previewIconSize)
                    }
                    previewApps.getOrNull(1)?.let { app ->
                        MiniAppIcon(app, previewIconSize)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    previewApps.getOrNull(2)?.let { app ->
                        MiniAppIcon(app, previewIconSize)
                    }
                    previewApps.getOrNull(3)?.let { app ->
                        MiniAppIcon(app, previewIconSize)
                    } ?: if (previewApps.size <= 2) {
                        Spacer(modifier = Modifier.size(previewIconSize))
                    } else Unit
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = LocalDrawerContentColor.current,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniAppIcon(app: AppInfo, iconSize: Dp) {
    val iconDrawable = AllAppsIconProvider.rememberAppIcon(app, iconSize)
    Box(
        modifier = Modifier
            .size(iconSize)
            .drawBehind {
                iconDrawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                iconDrawable.draw(drawContext.canvas.nativeCanvas)
            }
    )
}

