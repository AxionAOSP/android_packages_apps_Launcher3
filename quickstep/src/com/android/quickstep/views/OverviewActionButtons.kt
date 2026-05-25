/*
 * Copyright (C) 2025-2026 AxionOS
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
package com.android.quickstep.views

import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.axion.compose.host.AxComposeView
import com.android.launcher3.R

class OverviewActionsState {
    private val settingsActionsAvailableState = mutableStateOf(true)
    var freeformVisible by mutableStateOf(false)
    var splitVisible by mutableStateOf(false)
    var isLocked by mutableStateOf(false)
    var lockHint by mutableStateOf("")
    var clearAllEnabled by mutableStateOf(true)
    var splitIconRes by mutableIntStateOf(R.drawable.ic_split_vertical)
    var showLock by mutableStateOf(true)
    var showScreenshot by mutableStateOf(true)
    var showSelectText by mutableStateOf(true)
    var showFreeform by mutableStateOf(true)
    var showClearAll by mutableStateOf(true)
    var showMemoryInfo by mutableStateOf(true)
    var memoryInfo by mutableStateOf("")
    var memoryInfoUseWhiteText by mutableStateOf(false)
    var onActionsContentChanged: Runnable? = null
    var onScreenshot: Runnable? = null
    var onSelectText: Runnable? = null
    var onFreeform: Runnable? = null
    var onSplit: Runnable? = null
    var onClearAll: Runnable? = null
    var onLock: Runnable? = null
    var settingsActionsAvailable: Boolean
        get() = settingsActionsAvailableState.value
        set(value) {
            if (settingsActionsAvailableState.value == value) return
            settingsActionsAvailableState.value = value
            onActionsContentChanged?.run()
        }

    fun updateSettingsActionsAvailable() {
        settingsActionsAvailable = showLock ||
            showScreenshot ||
            showSelectText ||
            showFreeform ||
            showClearAll ||
            showMemoryInfo
    }
}

object OverviewActionButtonsBridge {
    @JvmStatic
    fun setup(view: View, state: OverviewActionsState) {
        if (view !is AxComposeView) return
        view.setContent {
            OverviewActionButtons(state)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverviewActionButtons(state: OverviewActionsState) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = remember(darkTheme) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(colorScheme = colorScheme) {
        OverviewActionButtonsContent(state)
    }
}

@Composable
private fun OverviewActionButtonsContent(state: OverviewActionsState) {
    if (!state.settingsActionsAvailable && !state.splitVisible) return

    val context = LocalContext.current
    val containerColor = remember(context) {
        val ta = context.obtainStyledAttributes(intArrayOf(R.attr.overviewActionButtonContainerColor))
        val color = ta.getColor(0, android.graphics.Color.TRANSPARENT)
        ta.recycle()
        Color(color)
    }
    val buttonSize = dimensionResource(R.dimen.overview_actions_btn_size)
    val buttonSpacing = dimensionResource(R.dimen.overview_actions_button_spacing)
    val clearAllWidth = dimensionResource(R.dimen.overview_actions_clear_all_width)
    val clearAllTopMargin = dimensionResource(R.dimen.overview_actions_clear_all_top_margin)
    val contentColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .height(buttonSize),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.showLock) {
                OverviewCircleButton(
                    iconRes = if (state.isLocked) R.drawable.ic_app_locked else R.drawable.ic_app_unlocked,
                    contentDescRes = R.string.accessibility_lock_task,
                    onClick = { state.onLock?.run() },
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            }
            if (state.showScreenshot) {
                OverviewCircleButton(
                    iconRes = R.drawable.ic_screenshot,
                    contentDescRes = R.string.action_screenshot,
                    onClick = { state.onScreenshot?.run() },
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            }
            if (state.showSelectText) {
                OverviewCircleButton(
                    iconRes = R.drawable.ic_select_text,
                    contentDescRes = R.string.action_select_text,
                    onClick = { state.onSelectText?.run() },
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            }
            if (state.showFreeform) {
                OverviewCircleButton(
                    iconRes = R.drawable.ic_overview_freeform,
                    contentDescRes = R.string.action_freeform,
                    onClick = { state.onFreeform?.run() },
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            }
            if (state.splitVisible) {
                FilledTonalButton(
                    onClick = { state.onSplit?.run() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                ) {
                    Icon(
                        painter = painterResource(state.splitIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_split))
                }
            }
        }

        val lockHintBoxHeight = if (state.showClearAll) 24.dp + clearAllTopMargin else 24.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lockHintBoxHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.memoryInfo,
                modifier = Modifier.alpha(if (state.memoryInfo.isNotEmpty()) 1f else 0f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (state.memoryInfoUseWhiteText) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.showClearAll) {
            FilledTonalButton(
                onClick = { state.onClearAll?.run() },
                enabled = state.clearAllEnabled,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier
                    .width(clearAllWidth)
                    .height(buttonSize)
            ) {
                Text(stringResource(R.string.recents_clear_all))
            }
        }
    }
}

@Composable
private fun OverviewCircleButton(
    iconRes: Int,
    contentDescRes: Int,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val buttonSize = dimensionResource(R.dimen.overview_actions_btn_size)
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier.size(buttonSize)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescRes)
        )
    }
}
