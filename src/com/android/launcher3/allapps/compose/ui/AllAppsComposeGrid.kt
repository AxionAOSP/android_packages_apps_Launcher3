@file:OptIn(ExperimentalFoundationApi::class)

package com.android.launcher3.allapps.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo
import com.android.launcher3.allapps.compose.ui.view.ComposeAppIconView

import android.graphics.Bitmap
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.RelativeLayout
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.views.ActivityContext

private const val SCROLL_SNAP_THRESHOLD_PX = 5

private class RowCoordsHolder {
    var column: LayoutCoordinates? = null
}

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
    var wasFullyClosed by remember { mutableStateOf(true) }

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

    val gridItems = remember(items, effectiveColumns) {
        buildLazyGridItems(items, effectiveColumns)
    }

    val cardBg = surfaceEffectColor()

    val isScrollingProvider = remember<() -> Boolean> { { scrollState.isScrollInProgress } }

    val scrollClipRadius = 32.dp
    val scrollClipShape = RoundedCornerShape(topStart = scrollClipRadius, topEnd = scrollClipRadius)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.clip = true
                    this.shape = scrollClipShape
                }
                .verticalScroll(scrollState, enabled = isScrollEnabled)
                .padding(contentPadding)
        ) {
            gridItems.forEach { gridItem ->
                val itemKey = when (gridItem) {
                    is LazyGridItem.PrivateSpace -> "private_header"
                    is LazyGridItem.EmptySearch -> "empty_search"
                    is LazyGridItem.SectionSpacer -> "spacer_${gridItem.sectionId}"
                    is LazyGridItem.GridRow -> "row_${gridItem.sectionId}_${gridItem.rowIndex}"
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
                            CanvasGridRow(
                                rowItems = gridItem.rowItems,
                                sectionId = gridItem.sectionId,
                                position = gridItem.position,
                                effectiveColumns = effectiveColumns,
                                showLabels = showLabels,
                                iconSizePx = iconSizePx,
                                cellWidthPx = cellWidthPx,
                                cellHeightPx = cellHeightPx,
                                cardBg = cardBg,
                                textMeasurer = textMeasurer,
                                labelStyle = labelStyle,
                                isScrollingProvider = isScrollingProvider,
                                onLongPressStatusChanged = onLongPressStatusChanged,
                                onFolderClick = onFolderClick,
                                onFolderLongClick = onFolderLongClick
                            )
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

private val rowScreenLocCache = IntArray(2)

@Composable
private fun CanvasGridRow(
    rowItems: List<GridCellItem>,
    sectionId: String,
    position: RowPosition,
    effectiveColumns: Int,
    showLabels: Boolean,
    iconSizePx: Int,
    cellWidthPx: Int,
    cellHeightPx: Int,
    cardBg: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    isScrollingProvider: () -> Boolean,
    onLongPressStatusChanged: (Boolean) -> Unit,
    onFolderClick: ((AppCategory) -> Unit)?,
    onFolderLongClick: ((AppCategory) -> Unit)?
) {
    val interactions = LocalAllAppsInteractions.current
    val isPagerSwiping = LocalPagerSwiping.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val controller = interactions.controller
    val contentColor = LocalDrawerContentColor.current
    val folderBgColor = MaterialTheme.colorScheme.surfaceContainer
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val iconProvider = remember { AllAppsIconProvider.getInstance(context) }
    val uiMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val iconConfig = LocalIconConfig.current

    val iconDrawables = remember(rowItems, iconSizePx, uiMode, iconConfig) {
        rowItems.map { item ->
            when (item) {
                is GridCellItem.App -> iconProvider.getIcon(item.appInfo, iconSizePx, uiMode, iconConfig.themed)
                is GridCellItem.Folder -> null
            }
        }
    }

    val miniIconSizePx = with(density) { 20.dp.roundToPx() }

    val labelResults = if (showLabels) {
        remember(rowItems, iconSizePx) {
            rowItems.map { item ->
                val text = when (item) {
                    is GridCellItem.App -> item.appInfo.title?.toString() ?: ""
                    is GridCellItem.Folder -> item.category.name
                }
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = labelStyle,
                    constraints = Constraints(maxWidth = iconSizePx),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else null

    val rowCoords = remember { RowCoordsHolder() }
    val measuredRowWidth = remember { mutableFloatStateOf(0f) }
    val iconSizeF = iconSizePx.toFloat()
    val labelGapPx = with(density) { 4.dp.toPx() }
    val labelHeightPx = labelResults?.firstOrNull()?.size?.height?.toFloat() ?: 0f
    val totalContentHeight = iconSizeF + if (showLabels) labelGapPx + labelHeightPx else 0f
    val iconTopOffset = (cellHeightPx - totalContentHeight) / 2f
    val labelTopOffset = iconTopOffset + iconSizeF + labelGapPx
    val dragThreshold = with(density) { 20.dp.toPx() }

    val currentOnClick by rememberUpdatedState(interactions.onAppClick)
    val currentOnLongClick by rememberUpdatedState(interactions.onAppLongClick)
    val currentOnDragStart by rememberUpdatedState(interactions.onAppDragStart)
    val currentOnDragMove by rememberUpdatedState(interactions.onAppDragMove)
    val currentOnDragEnd by rememberUpdatedState(interactions.onAppDragEnd)
    val currentOnLongPressStatusChanged by rememberUpdatedState(onLongPressStatusChanged)

    fun effectiveCellWidth(): Float {
        val rowWidth = measuredRowWidth.floatValue
        return if (rowWidth > 0f) rowWidth / effectiveColumns else cellWidthPx.toFloat()
    }

    fun cellX(cellIndex: Int): Float {
        val cellW = effectiveCellWidth()
        val idx = if (isRtl) effectiveColumns - 1 - cellIndex else cellIndex
        return idx * cellW + (cellW - iconSizeF) / 2f
    }

    fun hitCellIndex(touchX: Float): Int {
        val cellW = effectiveCellWidth()
        val raw = (touchX / cellW).toInt().coerceIn(0, rowItems.lastIndex)
        return if (isRtl) effectiveColumns - 1 - raw else raw
    }

    fun getScreenOffset(): Offset {
        view.rootView.getLocationOnScreen(rowScreenLocCache)
        return Offset(rowScreenLocCache[0].toFloat(), rowScreenLocCache[1].toFloat())
    }

    fun getIconBoundsOnScreen(cellIndex: Int): RectF {
        val coords = rowCoords.column ?: return RectF(0f, 0f, iconSizeF, iconSizeF)
        if (!coords.isAttached) return RectF(0f, 0f, iconSizeF, iconSizeF)
        val iconLocalX = cellX(cellIndex)
        val topLeft = coords.localToWindow(Offset(iconLocalX, iconTopOffset))
        val screenOffset = getScreenOffset()
        return RectF(
            topLeft.x + screenOffset.x,
            topLeft.y + screenOffset.y,
            topLeft.x + screenOffset.x + iconSizeF,
            topLeft.y + screenOffset.y + iconSizeF
        )
    }

    fun createIconInfo(cellIndex: Int, appInfo: AppInfo): ComposeIconInfo {
        val bounds = getIconBoundsOnScreen(cellIndex)
        val drawable = iconDrawables[cellIndex]
        val ctrl = controller
        val hostView = if (ctrl != null) {
            val hv = ctrl.getSharedHostView()
            val activityContext: ActivityContext = ActivityContext.lookupContext(context)
            hv.tag = appInfo
            hv.sectionId = sectionId
            hv.iconDrawable = drawable
            hv.setIconBounds(Rect(0, 0, iconSizePx, iconSizePx))
            hv.setIconSizePx(iconSizePx)
            hv.applyDotState(activityContext.getDotInfoForItem(appInfo), false)
            val parentLoc = IntArray(2)
            (hv.parent as? View)?.getLocationOnScreen(parentLoc)
            val lp = hv.layoutParams as? RelativeLayout.LayoutParams
            if (lp != null) {
                lp.width = iconSizePx
                lp.height = iconSizePx
                lp.leftMargin = (bounds.left - parentLoc[0]).toInt()
                lp.topMargin = (bounds.top - parentLoc[1]).toInt()
                hv.layoutParams = lp
            }
            hv.measure(
                View.MeasureSpec.makeMeasureSpec(iconSizePx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(iconSizePx, View.MeasureSpec.EXACTLY)
            )
            val left = (bounds.left - parentLoc[0]).toInt()
            val top = (bounds.top - parentLoc[1]).toInt()
            hv.layout(left, top, left + iconSizePx, top + iconSizePx)
            hv.translationX = 0f
            hv.translationY = 0f
            hv
        } else {
            ComposeAppIconView(context)
        }
        return ComposeIconInfo(
            appInfo = appInfo,
            iconBoundsOnScreen = bounds,
            iconDrawable = drawable?.constantState?.newDrawable()?.mutate()?.apply {
                setBounds(0, 0, iconSizePx, iconSizePx)
            },
            iconSizePx = iconSizePx,
            hostView = hostView
        )
    }

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

    val folderCornerPx = with(density) { 16.dp.toPx() }
    val miniGapPx = with(density) { 2.dp.toPx() }
    val miniSizeF = miniIconSizePx.toFloat()
    val folderBgArgb = android.graphics.Color.argb(
        (folderBgColor.alpha * 255).toInt(),
        (folderBgColor.red * 255).toInt(),
        (folderBgColor.green * 255).toInt(),
        (folderBgColor.blue * 255).toInt()
    )

    val iconBitmaps = remember(rowItems, iconSizePx, uiMode, iconConfig, folderBgArgb) {
        rowItems.mapIndexed { index, item ->
            when (item) {
                is GridCellItem.App -> {
                    val drawable = iconDrawables[index] ?: return@mapIndexed null
                    val pic = android.graphics.Picture()
                    val c = pic.beginRecording(iconSizePx, iconSizePx)
                    drawable.setBounds(0, 0, iconSizePx, iconSizePx)
                    drawable.draw(c)
                    pic.endRecording()
                    Bitmap.createBitmap(pic, iconSizePx, iconSizePx, Bitmap.Config.HARDWARE)
                }
                is GridCellItem.Folder -> {
                    val size = iconSizePx
                    val pic = android.graphics.Picture()
                    val c = pic.beginRecording(size, size)
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = folderBgArgb }
                    c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), folderCornerPx, folderCornerPx, bgPaint)
                    val minis = item.category.apps.take(4).map { app ->
                        iconProvider.getIcon(app, miniIconSizePx, uiMode, iconConfig.themed)
                    }
                    val gridSize = miniIconSizePx * 2f + miniGapPx
                    val gridLeft = (size - gridSize) / 2f
                    val gridTop = (size - gridSize) / 2f
                    minis.forEachIndexed { mi, miniDrawable ->
                        val col = mi % 2
                        val row = mi / 2
                        val mx = (gridLeft + col * (miniIconSizePx + miniGapPx)).toInt()
                        val my = (gridTop + row * (miniIconSizePx + miniGapPx)).toInt()
                        miniDrawable.setBounds(mx, my, mx + miniIconSizePx, my + miniIconSizePx)
                        miniDrawable.draw(c)
                    }
                    pic.endRecording()
                    Bitmap.createBitmap(pic, size, size, Bitmap.Config.HARDWARE)
                }
            }
        }
    }

    val verticalPadTopPx = with(density) {
        when (position) {
            RowPosition.ONLY, RowPosition.FIRST -> 12.dp.toPx()
            else -> 0f
        }
    }
    val verticalPadBottomPx = with(density) {
        when (position) {
            RowPosition.ONLY, RowPosition.LAST -> 12.dp.toPx()
            else -> 0f
        }
    }
    val boxOuterHeightPx = cellHeightPx + verticalPadTopPx + verticalPadBottomPx

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { boxOuterHeightPx.toDp() })
            .clip(shape)
            .background(cardBg)
            .padding(padding)
            .onGloballyPositioned { coords ->
                rowCoords.column = coords
                val w = coords.size.width.toFloat()
                if (measuredRowWidth.floatValue != w) {
                    measuredRowWidth.floatValue = w
                }
                if (isScrollingProvider()) return@onGloballyPositioned
                controller?.let { ctrl ->
                    val comp = ctrl.lastLaunchedComponent ?: return@let
                    if (ctrl.lastLaunchedSection != sectionId) return@let
                    val idx = rowItems.indexOfFirst { item ->
                        (item as? GridCellItem.App)?.appInfo?.componentName == comp
                    }
                    if (idx >= 0) ctrl.repositionHostView(getIconBoundsOnScreen(idx))
                }
            }
            .pointerInput(rowItems) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (isScrollingProvider() || isPagerSwiping) return@awaitEachGesture

                    val cellIndex = hitCellIndex(down.position.x)
                    val hitItem = rowItems.getOrNull(cellIndex) ?: return@awaitEachGesture

                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    try {
                        val up = withTimeout(longPressTimeout) { waitForUpOrCancellation() }
                        if (up != null) {
                            up.consume()
                            when (hitItem) {
                                is GridCellItem.App -> currentOnClick(createIconInfo(cellIndex, hitItem.appInfo))
                                is GridCellItem.Folder -> onFolderClick?.invoke(hitItem.category)
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (isScrollingProvider() || isPagerSwiping) return@awaitEachGesture

                        when (hitItem) {
                            is GridCellItem.Folder -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onFolderLongClick?.invoke(hitItem.category)
                            }
                            is GridCellItem.App -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                currentOnLongClick(createIconInfo(cellIndex, hitItem.appInfo))
                                currentOnLongPressStatusChanged.invoke(true)

                                try {
                                    var dragStarted = false
                                    var lastScreenPos = Offset.Zero

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        change.consume()

                                        lastScreenPos = change.position.let { pos ->
                                            val coords = rowCoords.column
                                            val screenOffset = getScreenOffset()
                                            if (coords != null && coords.isAttached) {
                                                val windowPos = coords.localToWindow(pos)
                                                Offset(
                                                    windowPos.x + screenOffset.x,
                                                    windowPos.y + screenOffset.y
                                                )
                                            } else {
                                                Offset(
                                                    pos.x + screenOffset.x,
                                                    pos.y + screenOffset.y
                                                )
                                            }
                                        }

                                        if (!dragStarted) {
                                            val moveFromDown = change.position - down.position
                                            val dist = kotlin.math.sqrt(
                                                moveFromDown.x * moveFromDown.x +
                                                    moveFromDown.y * moveFromDown.y
                                            )
                                            if (dist > dragThreshold) {
                                                dragStarted = true
                                                currentOnDragStart?.invoke(
                                                    createIconInfo(cellIndex, hitItem.appInfo)
                                                )
                                            }
                                        }
                                        if (dragStarted) {
                                            currentOnDragMove?.invoke(lastScreenPos.x, lastScreenPos.y)
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (dragStarted) {
                                        currentOnDragEnd?.invoke(lastScreenPos.x, lastScreenPos.y)
                                    }
                                } finally {
                                    currentOnLongPressStatusChanged.invoke(false)
                                }
                            }
                        }
                    }
                }
            }
            .drawBehind {
                val hiddenComp = controller?.hiddenIconComponent
                val hiddenSect = controller?.hiddenIconSection
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val cellW = effectiveCellWidth()
                rowItems.forEachIndexed { index, item ->
                    val iconX = cellX(index)
                    val visualIdx = if (isRtl) effectiveColumns - 1 - index else index
                    val cellStartX = visualIdx * cellW

                    val isHidden = item is GridCellItem.App
                        && hiddenComp != null
                        && hiddenComp == item.appInfo.componentName
                        && hiddenSect == sectionId

                    if (!isHidden) {
                        iconBitmaps[index]?.let { bmp ->
                            nativeCanvas.drawBitmap(bmp, iconX, iconTopOffset, null)
                        }
                    }

                    if (showLabels) {
                        labelResults?.getOrNull(index)?.let { label ->
                            val labelX = cellStartX + (cellW - label.size.width) / 2f
                            drawText(label, contentColor, Offset(labelX, labelTopOffset))
                        }
                    }
                }
            }
    )
}

