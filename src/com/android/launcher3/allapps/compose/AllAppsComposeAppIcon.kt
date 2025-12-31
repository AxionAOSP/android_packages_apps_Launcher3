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
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AllAppsComposeAppIcon(
    appInfo: AppInfo,
    showLabel: Boolean,
    iconSizePx: Int,
    cellHeightPx: Int = 0,
    onClick: (AppInfo, BubbleTextView) -> Unit,
    onLongClick: (AppInfo, BubbleTextView) -> Unit,
    onDragStart: ((AppInfo, BubbleTextView) -> Unit)? = null,
    onLongPressStatusChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    val heightModifier = if (cellHeightPx > 0) {
        with(LocalDensity.current) { Modifier.height(cellHeightPx.toDp()) }
    } else {
        Modifier
    }
    
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDragStart by rememberUpdatedState(onDragStart)

    var bubbleTextView by remember { mutableStateOf<BubbleTextView?>(null) }
    var imageView by remember { mutableStateOf<ImageView?>(null) }
    
    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface
    
    var textSizePx by remember { mutableFloatStateOf(with(density) { 12.dp.toPx() }) }
    var iconPaddingPx by remember { mutableIntStateOf(with(density) { 4.dp.roundToPx() }) }
    var labelText by remember { mutableStateOf("") }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    bubbleTextView?.setPressed(true)
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    bubbleTextView?.setPressed(false)
                }
            }
        }
    }

    fun syncBubbleTextViewBounds() {
        val iv = imageView ?: return
        val btv = bubbleTextView ?: return
        val location = IntArray(2)
        iv.getLocationOnScreen(location)
        btv.layout(location[0], location[1], location[0] + iconSizePx, location[1] + iconSizePx)
    }

    Column(
        modifier = modifier
            .then(heightModifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { 
                    syncBubbleTextViewBounds()
                    bubbleTextView?.let { view -> currentOnClick(appInfo, view) } 
                },
                onLongClick = {
                    syncBubbleTextViewBounds()
                    bubbleTextView?.let { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        currentOnLongClick(appInfo, view)
                    }
                }
            )
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        syncBubbleTextViewBounds()
                        bubbleTextView?.let { view ->
                            view.setPressed(false)
                            currentOnDragStart?.invoke(appInfo, view)
                        }
                    },
                    onDrag = { _, _ -> },
                    onDragEnd = {},
                    onDragCancel = {}
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (cellHeightPx > 0) Arrangement.Center else Arrangement.Top
    ) {
        val iconSizeDp = with(density) { iconSizePx.toDp() }
        
        key(appInfo.componentName, uiMode) {
            AndroidView(
                factory = { ctx ->
                    val btv = LayoutInflater.from(ctx)
                        .inflate(R.layout.all_apps_icon, null) as BubbleTextView
                    appInfo.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS
                    btv.applyFromItemInfoWithIcon(appInfo)
                    
                    textSizePx = btv.textSize
                    iconPaddingPx = btv.compoundDrawablePadding
                    labelText = appInfo.title?.toString() ?: ""
                    
                    btv.setTextVisibility(false)
                    btv.isClickable = false
                    btv.isLongClickable = false
                    btv.isFocusable = false
                    
                    bubbleTextView = btv
                    
                    ImageView(ctx).apply {
                         scaleType = ImageView.ScaleType.FIT_CENTER
                         setImageDrawable(btv.icon)
                    }.also { imageView = it }
                },
                update = { view ->
                    val btv = bubbleTextView ?: return@AndroidView
                    btv.reapplyItemInfo(appInfo)
                    view.setImageDrawable(btv.icon)
                    
                    textSizePx = btv.textSize
                    iconPaddingPx = btv.compoundDrawablePadding
                    labelText = appInfo.title?.toString() ?: ""
                    
                    view.visibility = View.VISIBLE
                    view.alpha = 1f
                    view.invalidate()
                },
                modifier = Modifier.size(iconSizeDp)
            )
        }
        
        if (showLabel) {
            Spacer(modifier = Modifier.height(with(density) { iconPaddingPx.toDp() }))
            
            Text(
                text = labelText.ifEmpty { appInfo.title?.toString() ?: "" },
                color = textColor,
                fontSize = with(density) { textSizePx.toSp() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(iconSizeDp)
            )
        }
    }
}
