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

import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.Launcher
import com.android.launcher3.views.ActivityContext
import android.util.Log

data class ComposeIconInfo(
    val appInfo: AppInfo,
    val iconBoundsOnScreen: RectF,
    val iconDrawable: Drawable?,
    val iconSizePx: Int,
    val hostView: View? = null
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val context = LocalContext.current

    val heightModifier = if (cellHeightPx > 0) {
        with(LocalDensity.current) { Modifier.height(cellHeightPx.toDp()) }
    } else {
        Modifier
    }
    
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val density = LocalDensity.current
    val view = LocalView.current
    
    val effectiveIconSizePx = remember(iconSizePx) {
        if (iconSizePx > 0) iconSizePx else with(density) { 48.dp.roundToPx() }
    }
    
    val iconDrawable = AllAppsIconProvider.rememberAppIcon(appInfo, effectiveIconSizePx)

    var iconLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var columnLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    
    fun getIconBoundsOnScreen(): RectF {
        val coords = iconLayoutCoordinates
        if (coords == null || !coords.isAttached) {
            Log.d("ComposePopup", "getIconBoundsOnScreen: coords null or not attached")
            return RectF(0f, 0f, effectiveIconSizePx.toFloat(), effectiveIconSizePx.toFloat())
        }
        val boundsInWindow = coords.boundsInWindow()
        val rootView = view.rootView
        val windowLoc = IntArray(2)
        rootView.getLocationOnScreen(windowLoc)
        Log.d("ComposePopup", "boundsInWindow=$boundsInWindow, windowLoc=[${windowLoc[0]}, ${windowLoc[1]}]")
        val result = RectF(
            boundsInWindow.left + windowLoc[0],
            boundsInWindow.top + windowLoc[1],
            boundsInWindow.right + windowLoc[0],
            boundsInWindow.bottom + windowLoc[1]
        )
        Log.d("ComposePopup", "screenBounds=$result")
        return result
    }
    
    fun createIconInfo(hostView: View? = null): ComposeIconInfo {
        val bounds = getIconBoundsOnScreen()
        Log.d("ComposePopup", "createIconInfo: bounds=$bounds for ${appInfo.title}")
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
    
    val interactionSource = remember { MutableInteractionSource() }

    var dragStarted by remember { mutableStateOf(false) }
    var lastDragScreenPos by remember { mutableStateOf(Offset.Zero) }
    val dragThreshold = with(density) { 20.dp.toPx() }

    var composeAppIconView by remember { mutableStateOf<ComposeAppIconView?>(null) }
    val cellWidth = with(density) { 
        if (cellWidthPx > 0) cellWidthPx.toDp() else effectiveIconSizePx.toDp() 
    }
    
    Column(
        modifier = modifier
            .then(heightModifier)
            .width(cellWidth)
            .onGloballyPositioned { coords ->
                columnLayoutCoordinates = coords
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    currentOnClick(createIconInfo(composeAppIconView))
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    currentOnLongClick(createIconInfo(composeAppIconView))
                }
            )
            .pointerInput(isScrolling) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var longPressTriggered = false
                    var localDragStarted = false
                    
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val startTime = System.currentTimeMillis()
                    
                    while (true) {
                        if (isScrolling) break
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        
                        val screenPos = change.position.let { pos ->
                            val coords = columnLayoutCoordinates
                            if (coords != null && coords.isAttached) {
                                val windowPos = coords.localToWindow(pos)
                                val rootView = view.rootView
                                val windowLoc = IntArray(2)
                                rootView.getLocationOnScreen(windowLoc)
                                Offset(
                                    windowPos.x + windowLoc[0],
                                    windowPos.y + windowLoc[1]
                                )
                            } else {
                                val rootView = view.rootView
                                val windowLoc = IntArray(2)
                                rootView.getLocationOnScreen(windowLoc)
                                Offset(
                                    pos.x + windowLoc[0],
                                    pos.y + windowLoc[1]
                                )
                            }
                        }
                        
                        if (event.changes.all { !it.pressed }) {
                            Log.d("ComposePopup", "pointer released, localDragStarted=$localDragStarted")
                            if (localDragStarted) {
                                currentOnDragEnd?.invoke(screenPos.x, screenPos.y)
                                localDragStarted = false
                                dragStarted = false
                            }
                            break
                        }
                        
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed > longPressTimeout && !longPressTriggered) {
                            longPressTriggered = true
                            Log.d("ComposePopup", "longPressTriggered after ${elapsed}ms")
                        }
                        
                        if (longPressTriggered) {
                            val drag = change.position - down.position
                            val dragDistance = kotlin.math.sqrt(drag.x * drag.x + drag.y * drag.y)
                            if (!localDragStarted && dragDistance > dragThreshold) {
                                Log.d("ComposePopup", "onDragStart: ${appInfo.title}, distance=$dragDistance, threshold=$dragThreshold")
                                localDragStarted = true
                                dragStarted = true
                                currentOnDragStart?.invoke(createIconInfo(composeAppIconView))
                            }
                            if (localDragStarted) {
                                currentOnDragMove?.invoke(screenPos.x, screenPos.y)
                                change.consume()
                            }
                        }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (cellHeightPx > 0) Arrangement.Center else Arrangement.Top
    ) {
        val iconSizeDp = with(density) { effectiveIconSizePx.toDp() }
        
        Box(
            modifier = Modifier
                .size(iconSizeDp)
                .onGloballyPositioned { coords ->
                    iconLayoutCoordinates = coords
                }
        ) {
            AndroidView<ComposeAppIconView>(
                factory = { ctx ->
                    ComposeAppIconView(ctx).apply {
                        composeAppIconView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.tag = appInfo
                    view.iconDrawable = iconDrawable
                    view.setIconBounds(Rect(0, 0, effectiveIconSizePx, effectiveIconSizePx))
                    view.setIconSizePx(effectiveIconSizePx)
                    val dotInfo = ActivityContext.lookupContext<Launcher>(view.context).getDotInfoForItem(appInfo)
                    view.applyDotState(dotInfo, false)
                }
            )
        }
        
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
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
