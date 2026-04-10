package com.android.launcher3.allapps.compose.ui

import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.ui.view.ComposeAppIconView

import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.RelativeLayout
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.Launcher
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo

private val windowLocCache = IntArray(2)

private class LayoutCoordsHolder {
    var icon: LayoutCoordinates? = null
    var column: LayoutCoordinates? = null
}

@Composable
fun AllAppsComposeAppIcon(
    appInfo: AppInfo,
    showLabel: Boolean,
    iconSizePx: Int,
    cellWidthPx: Int = 0,
    cellHeightPx: Int = 0,
    onClick: (ComposeIconInfo) -> Unit = {},
    onLongClick: (ComposeIconInfo) -> Unit = {},
    onDragStart: ((ComposeIconInfo) -> Unit)? = null,
    onDragMove: ((screenX: Float, screenY: Float) -> Unit)? = null,
    onDragEnd: ((screenX: Float, screenY: Float) -> Unit)? = null,
    onLongPressStatusChanged: ((Boolean) -> Unit)? = null,
    interceptClicks: Boolean = true,
    isScrollingProvider: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val isPagerSwiping = LocalPagerSwiping.current
    val sectionId = LocalSectionId.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    val heightModifier = if (cellHeightPx > 0) {
        with(density) { Modifier.height(cellHeightPx.toDp()) }
    } else {
        Modifier
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnLongPressStatusChanged by rememberUpdatedState(onLongPressStatusChanged)

    val effectiveIconSizePx = remember(iconSizePx) {
        if (iconSizePx > 0) iconSizePx else with(density) { 48.dp.roundToPx() }
    }

    val iconDrawable = AllAppsIconProvider.rememberAppIcon(appInfo, effectiveIconSizePx)

    val layoutCoords = remember { LayoutCoordsHolder() }

    fun getScreenOffset(): Offset {
        view.rootView.getLocationOnScreen(windowLocCache)
        return Offset(windowLocCache[0].toFloat(), windowLocCache[1].toFloat())
    }

    fun getIconBoundsOnScreen(): RectF {
        val coords = layoutCoords.icon
        if (coords == null || !coords.isAttached) {
            return RectF(0f, 0f, effectiveIconSizePx.toFloat(), effectiveIconSizePx.toFloat())
        }
        val boundsInWindow = coords.boundsInWindow()
        val screenOffset = getScreenOffset()
        return RectF(
            boundsInWindow.left + screenOffset.x,
            boundsInWindow.top + screenOffset.y,
            boundsInWindow.right + screenOffset.x,
            boundsInWindow.bottom + screenOffset.y
        )
    }

    val controller = LocalAllAppsInteractions.current.controller

    fun configureSharedHostView(): ComposeAppIconView {
        val ctrl = controller ?: return ComposeAppIconView(context)
        val hostView = ctrl.getSharedHostView()
        val activityContext: ActivityContext = ActivityContext.lookupContext(context)

        hostView.tag = appInfo
        hostView.sectionId = sectionId
        hostView.iconDrawable = iconDrawable
        hostView.setIconBounds(Rect(0, 0, effectiveIconSizePx, effectiveIconSizePx))
        hostView.setIconSizePx(effectiveIconSizePx)
        hostView.applyDotState(activityContext.getDotInfoForItem(appInfo), false)

        val iconBounds = getIconBoundsOnScreen()
        val parentLoc = IntArray(2)
        (hostView.parent as? View)?.getLocationOnScreen(parentLoc)

        val lp = hostView.layoutParams as? RelativeLayout.LayoutParams
        if (lp != null) {
            lp.width = effectiveIconSizePx
            lp.height = effectiveIconSizePx
            lp.leftMargin = (iconBounds.left - parentLoc[0]).toInt()
            lp.topMargin = (iconBounds.top - parentLoc[1]).toInt()
            hostView.layoutParams = lp
        }

        hostView.measure(
            View.MeasureSpec.makeMeasureSpec(effectiveIconSizePx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(effectiveIconSizePx, View.MeasureSpec.EXACTLY)
        )
        val left = (iconBounds.left - parentLoc[0]).toInt()
        val top = (iconBounds.top - parentLoc[1]).toInt()
        hostView.layout(left, top, left + effectiveIconSizePx, top + effectiveIconSizePx)

        hostView.translationX = 0f
        hostView.translationY = 0f

        return hostView
    }

    fun createIconInfo(): ComposeIconInfo {
        val bounds = getIconBoundsOnScreen()
        val hostView = configureSharedHostView()
        return ComposeIconInfo(
            appInfo = appInfo,
            iconBoundsOnScreen = bounds,
            iconDrawable = iconDrawable.constantState?.newDrawable()?.mutate()?.apply {
                setBounds(0, 0, effectiveIconSizePx, effectiveIconSizePx)
            },
            iconSizePx = effectiveIconSizePx,
            hostView = hostView
        )
    }

    val dragThreshold = with(density) { 20.dp.toPx() }

    val cellWidth = with(density) {
        if (cellWidthPx > 0) cellWidthPx.toDp() else effectiveIconSizePx.toDp()
    }

    Column(
        modifier = modifier
            .then(heightModifier)
            .width(cellWidth)
            .onGloballyPositioned { coords ->
                layoutCoords.column = coords
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (isScrollingProvider() || isPagerSwiping) return@awaitEachGesture

                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                    try {
                        val up = withTimeout(longPressTimeout) {
                            waitForUpOrCancellation()
                        }
                        if (up != null) {
                            up.consume()
                            currentOnClick(createIconInfo())
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (isScrollingProvider() || isPagerSwiping) return@awaitEachGesture

                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        currentOnLongClick(createIconInfo())
                        currentOnLongPressStatusChanged?.invoke(true)

                        try {
                            var dragStarted = false
                            var lastScreenPos = Offset.Zero

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                change.consume()

                                lastScreenPos = change.position.let { pos ->
                                    val coords = layoutCoords.column
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
                                        currentOnDragStart?.invoke(createIconInfo())
                                    }
                                }
                                if (dragStarted) {
                                    currentOnDragMove?.invoke(
                                        lastScreenPos.x,
                                        lastScreenPos.y
                                    )
                                }
                            } while (event.changes.any { it.pressed })

                            if (dragStarted) {
                                currentOnDragEnd?.invoke(lastScreenPos.x, lastScreenPos.y)
                            }
                        } finally {
                            currentOnLongPressStatusChanged?.invoke(false)
                        }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (cellHeightPx > 0) Arrangement.Center else Arrangement.Top
    ) {
        val iconSizeDp = with(density) { effectiveIconSizePx.toDp() }

        val componentName = appInfo.componentName

        Box(
            modifier = Modifier
                .size(iconSizeDp)
                .onGloballyPositioned { coords ->
                    layoutCoords.icon = coords
                }
                .drawBehind {
                    val hiddenComp = controller?.hiddenIconComponent
                    val hiddenSect = controller?.hiddenIconSection
                    val isHidden = hiddenComp != null
                        && hiddenComp == componentName
                        && hiddenSect == sectionId
                    if (!isHidden) {
                        iconDrawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        iconDrawable.draw(drawContext.canvas.nativeCanvas)
                    }
                }
        )

        if (showLabel) {
            Text(
                text = appInfo.title?.toString() ?: "",
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
