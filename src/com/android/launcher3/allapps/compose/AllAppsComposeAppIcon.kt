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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
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
    val currentOnLongPressStatusChanged by rememberUpdatedState(onLongPressStatusChanged)

    var bubbleTextView by remember { mutableStateOf<BubbleTextView?>(null) }

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

    Box(
        modifier = modifier
            .then(heightModifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { 
                    bubbleTextView?.let { view -> currentOnClick(appInfo, view) } 
                },
                onLongClick = {
                    bubbleTextView?.let { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        currentOnLongClick(appInfo, view)
                    }
                }
            )
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
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
        contentAlignment = Alignment.TopCenter
    ) {
        key(appInfo.componentName, uiMode) {
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx)
                        .inflate(R.layout.all_apps_icon, null) as BubbleTextView
                    appInfo.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS
                    view.applyFromItemInfoWithIcon(appInfo)
                    if (!showLabel) {
                        view.setTextVisibility(false)
                    }
                    view.isClickable = false
                    view.isLongClickable = false
                    view.isFocusable = false
                    bubbleTextView = view
                    view
                },
                update = { view ->
                    view.reapplyItemInfo(appInfo)
                    if (!showLabel) {
                        view.setTextVisibility(false)
                    }
                    view.isLongClickable = false
                    view.isClickable = false
                    
                    view.visibility = View.VISIBLE
                    view.alpha = 1f
                    
                    bubbleTextView = view
                },
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}
