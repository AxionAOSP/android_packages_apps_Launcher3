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

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.android.launcher3.allapps.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.app.AxBoostFwk
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState

@Composable
internal fun PrivateSpaceVeil(
    progress: Float,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme
    val veilAlpha by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "veil_alpha"
    )
    val veilScale by animateFloatAsState(
        targetValue = if (progress > 0f) 1f else 0.92f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "veil_scale"
    )
    if (veilAlpha <= 0f) return

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = veilAlpha
                scaleX = veilScale
                scaleY = veilScale
            }
            .padding(16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceBright),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val iconScale = 0.8f + (progress * 0.2f)
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Text(
                text = stringResource(R.string.private_space_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = onUnlockClick
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ps_unlock_button))
            }
        }
    }
}

@Composable
internal fun PrivateSpaceFullPage(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    onLaunch: () -> Unit = {},
    isActive: Boolean = true,
    isSearchBarAtTop: Boolean,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalSectionId provides "private") {
    val interactions = LocalAllAppsInteractions.current
    val gridState = rememberLazyGridState()

    val canScrollBack by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollFwd by remember { derivedStateOf { gridState.canScrollForward } }
    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_SCROLL_VERTICAL, -1L)
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0)
        } else {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0L)
        }
    }

    LaunchedEffect(isActive, canScrollBack, canScrollFwd) {
        if (isActive) {
            interactions.controller?.let {
                it.canScrollUp = true
                it.canScrollDown = canScrollFwd
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.private_space_label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDrawerContentColor.current
            )
            IconButton(
                onClick = {
                    onLaunch()
                    callbacks.onPrivateSpaceSettingsClicked()
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = surfaceEffectColor()
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.ps_settings_content_description),
                    tint = LocalDrawerContentColor.current,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (state.isPrivateSpaceLocked) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (state.privateApps.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ps_private_apps_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = LocalDrawerContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.numColumns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        bottom = if (isSearchBarAtTop) 0.dp else 72.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(surfaceEffectColor())
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                ) {
                    item(key = "private_add_app") {
                        val iconSizeDp = with(LocalDensity.current) { state.iconSizePx.toDp() }
                        val cellHeightDp = with(LocalDensity.current) { state.cellHeightPx.toDp() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeightDp)
                                .clickable {
                                    onLaunch()
                                    callbacks.onPrivateSpaceInstallAppClicked()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(iconSizeDp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.ps_add_app_button),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            if (state.showLabels) {
                                Text(
                                    text = stringResource(R.string.ps_add_app_short),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalDrawerContentColor.current.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .width(iconSizeDp)
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    items(
                        count = state.privateApps.size,
                        key = { "private_${state.privateApps[it].componentName}_${state.privateApps[it].user.hashCode()}" }
                    ) { index ->
                        val app = state.privateApps[index]
                        AllAppsComposeAppIcon(
                            appInfo = app,
                            showLabel = state.showLabels,
                            iconSizePx = state.iconSizePx,
                            cellHeightPx = state.cellHeightPx,
                            onClick = interactions.onAppClick,
                            onLongClick = interactions.onAppLongClick,
                            onDragStart = interactions.onAppDragStart,
                            onDragMove = interactions.onAppDragMove,
                            onDragEnd = interactions.onAppDragEnd,
                            isScrollingProvider = { gridState.isScrollInProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ps_no_private_apps),
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalDrawerContentColor.current
                        )
                        FilledTonalButton(
                            onClick = {
                                onLaunch()
                                callbacks.onPrivateSpaceInstallAppClicked()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ps_add_app_button))
                        }
                    }
                }
            }
        }
    }
    }

