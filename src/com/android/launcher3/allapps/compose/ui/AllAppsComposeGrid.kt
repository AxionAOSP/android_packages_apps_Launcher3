@file:OptIn(ExperimentalFoundationApi::class)

package com.android.launcher3.allapps.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import android.app.AxBoostFwk
import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AppCategory

import android.content.res.Configuration
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.android.launcher3.model.data.AppInfo

private const val SCROLL_SNAP_THRESHOLD_PX = 5

private val CardCornerRadius = 24.dp
private val ShapeOnly = RoundedCornerShape(CardCornerRadius)
private val ShapeFirst = RoundedCornerShape(topStart = CardCornerRadius, topEnd = CardCornerRadius)
private val ShapeMiddle = RoundedCornerShape(0.dp)
private val ShapeLast = RoundedCornerShape(bottomStart = CardCornerRadius, bottomEnd = CardCornerRadius)
private val RowPaddingFirst = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp)
private val RowPaddingLast = PaddingValues(start = 4.dp, end = 4.dp, bottom = 12.dp)
private val RowPaddingOnly = PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
private val RowPaddingMiddle = PaddingValues(horizontal = 4.dp)

private enum class RowPosition { FIRST, MIDDLE, LAST, ONLY }

private sealed interface GridCellItem {
    val stableKey: Any

    data class App(val appInfo: AppInfo) : GridCellItem {
        override val stableKey: Any
            get() = appInfo.componentName ?: appInfo
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
                                sectionId = "${section.type}_$sectionIndex",
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
    isOpening: Boolean = false,
    reopenTrigger: Int = 0,
    keyPrefix: String = "main",
    recompositionKey: Int = 0,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val interactions = LocalAllAppsInteractions.current
    val scrollState = rememberScrollState()
    val effectiveColumns = if (numColumns > 0) numColumns else 4

    var isScrollEnabled by remember { mutableStateOf(true) }
    val onLongPressStatusChanged = remember<(Boolean) -> Unit> {
        { isLongPressed ->
            isScrollEnabled = !isLongPressed
            interactions.controller?.isLongPressing = isLongPressed
        }
    }
    LaunchedEffect(effectiveColumns) {
        scrollState.scrollTo(0)
        interactions.controller?.let {
            it.canScrollUp = false
            it.canScrollDown = scrollState.canScrollForward
        }
    }

    LaunchedEffect(items.size) {
        if (scrollState.value > 0) {
            scrollState.scrollTo(0)
        }
    }

    val canScrollBack by remember {
        derivedStateOf { scrollState.value > SCROLL_SNAP_THRESHOLD_PX }
    }
    val canScrollFwd by remember { derivedStateOf { scrollState.canScrollForward } }

    LaunchedEffect(canScrollBack, canScrollFwd) {
        interactions.controller?.let {
            it.canScrollUp = canScrollBack
            it.canScrollDown = canScrollFwd
        }
    }

    LaunchedEffect(reopenTrigger) {
        if (reopenTrigger > 0) {
            scrollState.scrollTo(0)
        }
    }

    val gridItems = remember(items, effectiveColumns) {
        buildLazyGridItems(items, effectiveColumns)
    }

    val cardBg = surfaceEffectColor()

    val isScrollingProvider = remember<() -> Boolean> { { scrollState.isScrollInProgress } }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_SCROLL_VERTICAL, -1L)
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0)
        } else {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0L)
        }
    }

    val scrollClipRadius = 32.dp
    val scrollClipShape = RoundedCornerShape(topStart = scrollClipRadius, topEnd = scrollClipRadius)

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(clip = true, shape = scrollClipShape)
                    .verticalScroll(scrollState, enabled = isScrollEnabled)
                    .padding(contentPadding)
            ) {
                var staggerRowIndex = 0
                gridItems.forEach { gridItem ->
                    val itemKey = when (gridItem) {
                        is LazyGridItem.PrivateSpace -> "${keyPrefix}_private_header"
                        is LazyGridItem.EmptySearch -> "${keyPrefix}_empty_search"
                        is LazyGridItem.SectionSpacer ->
                            "${keyPrefix}_spacer_${gridItem.sectionId}"
                        is LazyGridItem.GridRow ->
                            "${keyPrefix}_row_${gridItem.sectionId}_${gridItem.rowIndex}"
                    }
                    key(itemKey) {
                        when (gridItem) {
                            is LazyGridItem.PrivateSpace -> {
                                PrivateSpaceHeaderItem(isExpanded = gridItem.isExpanded)
                            }
                            is LazyGridItem.EmptySearch -> {
                                EmptySearchResultItem()
                            }
                            is LazyGridItem.SectionSpacer -> {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            is LazyGridItem.GridRow -> {
                                AllAppsGridRow(
                                    rowItems = gridItem.rowItems,
                                    sectionId = gridItem.sectionId,
                                    position = gridItem.position,
                                    effectiveColumns = effectiveColumns,
                                    showLabels = showLabels,
                                    iconSizePx = iconSizePx,
                                    cellWidthPx = cellWidthPx,
                                    cellHeightPx = cellHeightPx,
                                    cardBg = cardBg,
                                    isScrollingProvider = isScrollingProvider,
                                    onLongPressStatusChanged = onLongPressStatusChanged,
                                    onFolderClick = onFolderClick,
                                    onFolderLongClick = onFolderLongClick,
                                    transitionProgressProvider = transitionProgressProvider,
                                    rowIndex = staggerRowIndex,
                                    isOpening = isOpening
                                )
                                staggerRowIndex++
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

@Composable
private fun AllAppsGridRow(
    rowItems: List<GridCellItem>,
    sectionId: String,
    position: RowPosition,
    effectiveColumns: Int,
    showLabels: Boolean,
    iconSizePx: Int,
    cellWidthPx: Int,
    cellHeightPx: Int,
    cardBg: Color,
    isScrollingProvider: () -> Boolean,
    onLongPressStatusChanged: (Boolean) -> Unit,
    onFolderClick: ((AppCategory) -> Unit)?,
    onFolderLongClick: ((AppCategory) -> Unit)?,
    transitionProgressProvider: () -> Float,
    rowIndex: Int,
    isOpening: Boolean
) {
    val interactions = LocalAllAppsInteractions.current
    val rowAlpha = contentStaggerAlpha(
        progress = transitionProgressProvider(),
        rowIndex = rowIndex,
        isOpening = isOpening
    )

    val shape = when (position) {
        RowPosition.ONLY -> ShapeOnly
        RowPosition.FIRST -> ShapeFirst
        RowPosition.MIDDLE -> ShapeMiddle
        RowPosition.LAST -> ShapeLast
    }
    val padding = when (position) {
        RowPosition.ONLY -> RowPaddingOnly
        RowPosition.FIRST -> RowPaddingFirst
        RowPosition.MIDDLE -> RowPaddingMiddle
        RowPosition.LAST -> RowPaddingLast
    }

    CompositionLocalProvider(LocalSectionId provides sectionId) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (rowAlpha < 0.999f) Modifier.graphicsLayer(alpha = rowAlpha) else Modifier
                ),
            shape = shape,
            color = cardBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                horizontalArrangement = Arrangement.Start
            ) {
                for (index in 0 until effectiveColumns) {
                    val item = rowItems.getOrNull(index)
                    key(item?.stableKey ?: "empty_$index") {
                        if (item != null) {
                            val cellModifier = Modifier.weight(1f)
                            when (item) {
                                is GridCellItem.App -> {
                                    AllAppsComposeAppIcon(
                                        appInfo = item.appInfo,
                                        showLabel = showLabels,
                                        iconSizePx = iconSizePx,
                                        cellWidthPx = cellWidthPx,
                                        cellHeightPx = cellHeightPx,
                                        onClick = interactions.onAppClick,
                                        onLongClick = interactions.onAppLongClick,
                                        onDragStart = interactions.onAppDragStart,
                                        onDragMove = interactions.onAppDragMove,
                                        onDragEnd = interactions.onAppDragEnd,
                                        onLongPressStatusChanged = onLongPressStatusChanged,
                                        isScrollingProvider = isScrollingProvider,
                                        modifier = cellModifier
                                    )
                                }
                                is GridCellItem.Folder -> {
                                    AllAppsComposeFolderIcon(
                                        category = item.category,
                                        showLabel = showLabels,
                                        iconSizePx = iconSizePx,
                                        cellWidthPx = cellWidthPx,
                                        cellHeightPx = cellHeightPx,
                                        onClick = { onFolderClick?.invoke(it) },
                                        onLongClick = { onFolderLongClick?.invoke(it) },
                                        isScrollingProvider = isScrollingProvider,
                                        modifier = cellModifier
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllAppsComposeFolderIcon(
    category: AppCategory,
    showLabel: Boolean,
    iconSizePx: Int,
    cellWidthPx: Int,
    cellHeightPx: Int,
    onClick: (AppCategory) -> Unit,
    onLongClick: (AppCategory) -> Unit,
    isScrollingProvider: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val isPagerSwiping = LocalPagerSwiping.current
    val folderBgColor = MaterialTheme.colorScheme.surfaceContainer
    val folderCornerPx = with(density) { 16.dp.toPx() }
    val miniGapPx = with(density) { 2.dp.toPx() }
    val miniIconSizePx = with(density) { 20.dp.roundToPx() }
    val iconProvider = remember { AllAppsIconProvider.getInstance(context) }
    val uiMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val iconConfig = LocalIconConfig.current

    val folderBgArgb = android.graphics.Color.argb(
        (folderBgColor.alpha * 255).toInt(),
        (folderBgColor.red * 255).toInt(),
        (folderBgColor.green * 255).toInt(),
        (folderBgColor.blue * 255).toInt()
    )
    val folderBgPaint = remember(folderBgArgb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = folderBgArgb }
    }
    val miniIcons = remember(category.apps, miniIconSizePx, uiMode, iconConfig.themed) {
        category.apps.take(4).map { app ->
            iconProvider.getIcon(app, miniIconSizePx, uiMode, iconConfig.themed)
        }
    }

    val iconSizeDp = with(density) { iconSizePx.toDp() }
    val heightModifier = if (cellHeightPx > 0) {
        with(density) { Modifier.height(cellHeightPx.toDp()) }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(heightModifier)
            .pointerInput(category) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (isScrollingProvider() || isPagerSwiping) return@awaitEachGesture
                    try {
                        val up = withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                        if (up != null) {
                            up.consume()
                            onClick(category)
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick(category)
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (cellHeightPx > 0) Arrangement.Center else Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .size(iconSizeDp)
                .drawBehind {
                    val size = iconSizePx
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    nativeCanvas.drawRoundRect(
                        0f, 0f, size.toFloat(), size.toFloat(),
                        folderCornerPx, folderCornerPx, folderBgPaint
                    )
                    val gridSize = miniIconSizePx * 2f + miniGapPx
                    val gridLeft = (size - gridSize) / 2f
                    val gridTop = (size - gridSize) / 2f
                    miniIcons.forEachIndexed { mi, miniDrawable ->
                        val col = mi % 2
                        val row = mi / 2
                        val mx = (gridLeft + col * (miniIconSizePx + miniGapPx)).toInt()
                        val my = (gridTop + row * (miniIconSizePx + miniGapPx)).toInt()
                        miniDrawable.setBounds(mx, my, mx + miniIconSizePx, my + miniIconSizePx)
                        miniDrawable.draw(nativeCanvas)
                    }
                }
        )

        if (showLabel) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(iconSizeDp)
                    .padding(top = 4.dp),
                color = LocalDrawerContentColor.current
            )
        }
    }
}
