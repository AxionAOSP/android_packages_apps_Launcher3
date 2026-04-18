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

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.model.data.AppInfo

internal const val TAB_PERSONAL = 0
internal const val TAB_WORK = 1

@Composable
internal fun PersonalWorkTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pillFractionProvider: (() -> Float)? = null
) {
    val motionScheme = MaterialTheme.motionScheme
    val animatedPill by animateFloatAsState(
        targetValue = selectedTab.coerceIn(0, 1).toFloat(),
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "pill_offset"
    )
    val pillOffsetFractionProvider: () -> Float = pillFractionProvider ?: { animatedPill }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .graphicsLayer { translationX = pillOffsetFractionProvider().coerceIn(0f, 1f) * size.width }
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                TAB_PERSONAL to R.string.all_apps_personal_tab,
                TAB_WORK to R.string.all_apps_work_tab
            ).forEach { (tab, labelRes) ->
                val selected = selectedTab == tab
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    animationSpec = motionScheme.fastEffectsSpec(),
                    label = "tabText_$tab"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkTabContent(
    state: AllAppsComposeState,
    workItems: List<AllAppsComposeItem>,
    workSections: List<Pair<String, Int>>,
    onPauseWork: () -> Unit,
    onResumeWork: () -> Unit,
    transitionProgressProvider: () -> Float,
    openCounter: Int,
    modifier: Modifier = Modifier
) {
    if (state.isWorkProfilePaused) {
        WorkPausedContent(
            onResumeWork = onResumeWork,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                WorkEduCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AllAppsComposeGrid(
                    items = workItems,
                    sections = workSections,
                    numColumns = state.numColumns,
                    iconSizePx = state.iconSizePx,
                    cellWidthPx = state.cellWidthPx,
                    cellHeightPx = state.cellHeightPx,
                    showLabels = state.showLabels,
                    onFolderClick = {},
                    onFolderLongClick = {},
                    transitionProgressProvider = transitionProgressProvider,
                    keyPrefix = "work",
                    recompositionKey = openCounter,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 96.dp
                    )
                )
            }

            PauseWorkFab(
                onClick = onPauseWork,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
internal fun WorkEduCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE
        )
    }
    var dismissed by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.WORK_EDU_DISMISSED, false))
    }
    if (dismissed) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = surfaceEffectColor()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.work_profile_edu_work_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDrawerContentColor.current,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    dismissed = true
                    prefs.edit().putBoolean(PreferenceKeys.WORK_EDU_DISMISSED, true).apply()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_dismiss_notification),
                    tint = LocalDrawerContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun PauseWorkFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.WorkOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        text = {
            Text(
                text = stringResource(R.string.work_apps_pause_btn_text),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    )
}

@Composable
internal fun WorkPausedContent(
    onResumeWork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = stringResource(R.string.work_apps_paused_title),
                style = MaterialTheme.typography.titleMedium,
                color = LocalDrawerContentColor.current
            )
            Text(
                text = stringResource(R.string.work_apps_paused_body),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDrawerContentColor.current.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            FilledTonalButton(
                onClick = onResumeWork
            ) {
                Icon(
                    imageVector = Icons.Default.Work,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.work_apps_enable_btn_text))
            }
        }
    }
}

internal fun buildWorkComposeItems(
    workApps: List<AppInfo>
): Pair<List<AllAppsComposeItem>, List<Pair<String, Int>>> {
    val items = mutableListOf<AllAppsComposeItem>()
    val sections = mutableListOf<Pair<String, Int>>()

    var lastSection: String? = null
    workApps.forEach { app ->
        val section = app.sectionName?.toString()?.uppercase()?.firstOrNull()?.toString() ?: "#"
        if (section != lastSection) {
            sections.add(section to items.size)
            lastSection = section
        }
        items.add(AllAppsComposeItem.AppItem(app, section = "work"))
    }

    return items to sections
}
