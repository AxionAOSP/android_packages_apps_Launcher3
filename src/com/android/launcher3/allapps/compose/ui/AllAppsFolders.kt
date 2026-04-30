/*
 * Copyright (C) 2025-2026 The AxionOS Project
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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.AxBoostFwk
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.data.AppCategoryManager
import com.android.launcher3.allapps.compose.data.PinnedAppsManager
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.delay

@Composable
internal fun ExpandedFolderContent(
    category: AppCategory,
    onDismiss: () -> Unit,
    iconSizePx: Int,
    cellHeightPx: Int
) {
    CompositionLocalProvider(LocalSectionId provides "folder_${category.id}") {
    val interactions = LocalAllAppsInteractions.current
    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }
    val isScrollingProvider = remember<() -> Boolean> { { gridState.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_SCROLL_VERTICAL, -1L)
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0)
        } else {
            AxBoostFwk.acquireHint(AxBoostFwk.OP_RENDER_EARLY_WAKEUP, 0L)
        }
    }

    LaunchedEffect(canScrollUp, canScrollDown) {
        interactions.controller?.let {
            it.canScrollUp = canScrollUp
            it.canScrollDown = canScrollDown
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = surfaceEffectColor()
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = LocalDrawerContentColor.current
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDrawerContentColor.current
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().weight(1f)
        ) {
            items(
                count = category.apps.size,
                key = { "expanded_${category.id}_${category.apps[it].componentName}_${category.apps[it].user.hashCode()}" }
            ) { index ->
                val app = category.apps[index]
                AllAppsComposeAppIcon(
                    appInfo = app,
                    showLabel = true,
                    iconSizePx = iconSizePx,
                    cellHeightPx = cellHeightPx,
                    onClick = interactions.onAppClick,
                    onLongClick = interactions.onAppLongClick,
                    onDragStart = interactions.onAppDragStart,
                    onDragMove = interactions.onAppDragMove,
                    onDragEnd = interactions.onAppDragEnd,
                    isScrollingProvider = isScrollingProvider,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    }
}

@Composable
internal fun FolderPickerBottomSheet(
    componentName: String,
    categoryManager: AppCategoryManager,
    pinnedAppsManager: PinnedAppsManager,
    onDismiss: () -> Unit
) {
    val customCats by categoryManager.customCategories.collectAsStateWithLifecycle()
    val currentOverride = categoryManager.getOverride(componentName)
    var showNewFolderField by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val folderColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.folder_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (customCats.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val catEntries = customCats.entries.toList()
                    for (index in catEntries.indices) {
                        val (id, name) = catEntries[index]
                        val isCurrentFolder = currentOverride == id
                        val chipColor = folderColors[index % folderColors.size]
                        val motionScheme = MaterialTheme.motionScheme
                        val animatedContainerColor by animateColorAsState(
                            targetValue = if (isCurrentFolder) chipColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = motionScheme.fastEffectsSpec(),
                            label = "folderChipColor"
                        )
                        val animatedContentColor by animateColorAsState(
                            targetValue = if (isCurrentFolder)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = motionScheme.fastEffectsSpec(),
                            label = "folderChipContentColor"
                        )

                        Surface(
                            onClick = {
                                if (!isCurrentFolder) {
                                    categoryManager.setOverride(componentName, id)
                                    if (pinnedAppsManager.isPinned(componentName)) {
                                        pinnedAppsManager.unpin(componentName)
                                    }
                                }
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = animatedContainerColor,
                            tonalElevation = if (isCurrentFolder) 0.dp else 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentFolder) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = animatedContentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isCurrentFolder) FontWeight.Bold else FontWeight.Medium,
                                    color = animatedContentColor
                                )
                                if (isCurrentFolder) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.folder_selected_description),
                                        tint = animatedContentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (currentOverride != null && currentOverride >= AppCategoryManager.CUSTOM_ID_START) {
                Surface(
                    onClick = {
                        categoryManager.removeOverride(componentName)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.folder_remove_button),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            AnimatedContent(
                targetState = showNewFolderField,
                transitionSpec = {
                    (fadeIn(tween(200, easing = EmphasizedDecelerateEasing)) + expandVertically(tween(300, easing = EmphasizedDecelerateEasing))) togetherWith
                        (fadeOut(tween(150, easing = EmphasizedAccelerateEasing)) + shrinkVertically(tween(200, easing = EmphasizedAccelerateEasing)))
                },
                label = "newFolderTransition"
            ) { showField ->
                if (showField) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text(stringResource(R.string.folder_name_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                        FilledTonalButton(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    val newId = categoryManager.createCategory(newFolderName.trim())
                                    categoryManager.setOverride(componentName, newId)
                                    if (pinnedAppsManager.isPinned(componentName)) {
                                        pinnedAppsManager.unpin(componentName)
                                    }
                                    onDismiss()
                                }
                            },
                            enabled = newFolderName.isNotBlank()
                        ) {
                            Text(stringResource(R.string.folder_create_button))
                        }
                    }
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                } else {
                    Surface(
                        onClick = { showNewFolderField = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.folder_new_button),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FolderActionsSheet(
    target: AppCategory?,
    onDismiss: () -> Unit,
    onRename: (AppCategory) -> Unit,
    onDelete: (AppCategory) -> Unit
) {
    if (target == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = target.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.folder_rename_confirm)) },
                leadingContent = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onRename(target) }
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.folder_delete_confirm), color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onDelete(target) }
            )
        }
    }
}

@Composable
internal fun RenameFolderDialog(
    target: AppCategory?,
    onDismiss: () -> Unit,
    onConfirm: (AppCategory, String) -> Unit
) {
    if (target == null) return
    var name by remember(target) { mutableStateOf(target.name) }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                delay(100)
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { if (name.isNotBlank()) onConfirm(target, name.trim()) },
                enabled = name.isNotBlank() && name.trim() != target.name
            ) {
                Text(stringResource(R.string.folder_rename_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
