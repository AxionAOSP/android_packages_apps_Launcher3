/*
 * Copyright 2025-2026 AxionOS
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
package com.android.launcher3.settings.compose

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.compose.applist.AppEntry as AxAppEntry
import com.android.axion.compose.applist.AppFilter as AxAppFilter
import com.android.axion.compose.applist.rememberAppList
import com.android.axion.compose.navigation.AxRouteAnimatedContent
import com.android.axion.compose.navigation.rememberAxRouteNavigator
import com.android.axion.compose.preferences.BasePreference
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.DraggablePreferenceGroup
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsFolderStore
import com.android.launcher3.allapps.AllAppsFolderStore.FolderRecord
import com.android.launcher3.allapps.AxSmartDrawerFolderStore
import com.android.launcher3.allapps.AxSmartDrawerManager
import com.android.launcher3.util.painterResource as drawablePainterResource

@Composable
internal fun AllAppsFoldersScreen(smartDrawer: Boolean = false) {
    val context = LocalContext.current
    val navigator = rememberAxRouteNavigator()
    val sdkApps by rememberAppList(AxAppFilter.LAUNCHABLE_ONLY, AxAppFilter.NO_OVERLAYS)
    val apps = remember(context, sdkApps) { loadAllAppsFolderEntries(context, sdkApps) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(context, smartDrawer, apps) {
        if (smartDrawer && seedSmartDrawerFoldersIfNeeded(context, apps)) {
            version++
        }
    }
    val folders = remember(context, version, smartDrawer) {
        if (smartDrawer) AxSmartDrawerFolderStore.getFolderRecords(context)
        else AllAppsFolderStore.getFolderRecords(context)
    }
    val selectedFolderId = navigator.route?.toIntOrNull()

    if (selectedFolderId != null) {
        BackHandler { navigator.goBack() }
    }

    AxRouteAnimatedContent(
        targetRoute = navigator.route,
        isForward = navigator.isForward,
        modifier = Modifier.fillMaxWidth(),
        label = "allAppsFoldersRoute",
    ) { targetRoute ->
        SettingsContentColumn {
            targetRoute?.toIntOrNull()?.let { folderId ->
                AllAppsFolderDetailScreen(
                    folderId = folderId,
                    folders = folders,
                    apps = apps,
                    smartDrawer = smartDrawer,
                    version = version,
                    onVersionChange = { version++ },
                )
            } ?: AllAppsFolderListScreen(
                folders = folders,
                smartDrawer = smartDrawer,
                onSelectFolder = { navigator.navigateToNested(it.toString()) },
                onVersionChange = { version++ },
            )
        }
    }
}

@Composable
private fun AllAppsFolderListScreen(
    folders: List<FolderRecord>,
    smartDrawer: Boolean,
    onSelectFolder: (Int) -> Unit,
    onVersionChange: () -> Unit,
) {
    val context = LocalContext.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteFolderId by rememberSaveable {
        mutableIntStateOf(AllAppsFolderStore.NO_FOLDER_ID)
    }
    val pendingDeleteFolder = folders.firstOrNull { it.id == pendingDeleteFolderId }

    if (showCreateDialog) {
        FolderNameDialog(
            titleRes = R.string.all_apps_new_folder,
            initialName = stringResource(R.string.all_apps_default_folder_name),
            confirmRes = R.string.all_apps_create_folder,
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                val folderId = if (smartDrawer) {
                    AxSmartDrawerFolderStore.createFolder(context, it)
                } else {
                    AllAppsFolderStore.createFolder(context, it)
                }
                if (folderId != AllAppsFolderStore.NO_FOLDER_ID) {
                    onVersionChange()
                    onSelectFolder(folderId)
                }
                showCreateDialog = false
            },
        )
    }

    if (pendingDeleteFolder != null) {
        DeleteFolderDialog(
            folder = pendingDeleteFolder,
            smartDrawer = smartDrawer,
            onDismiss = { pendingDeleteFolderId = AllAppsFolderStore.NO_FOLDER_ID },
            onDeleted = {
                pendingDeleteFolderId = AllAppsFolderStore.NO_FOLDER_ID
                onVersionChange()
            },
        )
    }

    val deleteLabel = stringResource(R.string.all_apps_delete_folder)
    DraggablePreferenceGroup(
        items = folders,
        itemKey = { it.id },
        itemTitle = { it.title.toString() },
        itemSummary = { folderAppCount(context, it.appCount) },
        deleteContentDescription = deleteLabel,
        onItemClick = { onSelectFolder(it.id) },
        onItemDelete = { pendingDeleteFolderId = it.id },
        onMove = { fromIndex, toIndex ->
            moveFolder(context, fromIndex, toIndex, smartDrawer)
            onVersionChange()
        },
    ) {
        item {
            CategoryPreference(
                titleRes = R.string.all_apps_add_folder_title,
                summaryRes = R.string.all_apps_add_folder_summary,
                onClick = { showCreateDialog = true },
            )
        }
    }
}

@Composable
private fun AllAppsFolderDetailScreen(
    folderId: Int,
    folders: List<FolderRecord>,
    apps: List<AllAppsFolderAppEntry>,
    smartDrawer: Boolean,
    version: Int,
    onVersionChange: () -> Unit,
) {
    val context = LocalContext.current
    val folder = folders.firstOrNull { it.id == folderId } ?: return
    val assignedTitles = remember(context, version) {
        if (smartDrawer) AxSmartDrawerFolderStore.getFolderTitlesByAppKey(context)
        else AllAppsFolderStore.getFolderTitlesByAppKey(context)
    }
    val activeKeys = remember(context, folderId, version) {
        if (smartDrawer) AxSmartDrawerFolderStore.getFolderAppKeys(context, folderId)
        else AllAppsFolderStore.getFolderAppKeys(context, folderId)
    }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    if (showRenameDialog) {
        FolderNameDialog(
            titleRes = R.string.all_apps_folder_name_hint,
            initialName = folder.title.toString(),
            confirmRes = R.string.all_apps_save_folder,
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                if (smartDrawer) AxSmartDrawerFolderStore.setFolderTitle(context, folderId, it)
                else AllAppsFolderStore.setFolderTitle(context, folderId, it)
                onVersionChange()
                showRenameDialog = false
            },
        )
    }

    PreferenceGroup {
        item {
            ClickablePreference(
                title = folder.title.toString(),
                summary = stringResource(R.string.all_apps_folder_name_hint),
                onClick = { showRenameDialog = true },
            )
        }
    }

    FolderAppsGroup(
        titleRes = R.string.all_apps_folder_active_apps,
        apps = apps.filter { activeKeys.contains(it.key) },
        activeKeys = activeKeys,
        assignedTitles = assignedTitles,
        onAppChecked = { key, checked ->
            setFolderAppChecked(context, folderId, activeKeys, key, checked, smartDrawer)
            onVersionChange()
        },
    )
    FolderAppsGroup(
        titleRes = R.string.all_apps_folder_other_apps,
        apps = apps.filterNot { activeKeys.contains(it.key) },
        activeKeys = activeKeys,
        assignedTitles = assignedTitles,
        onAppChecked = { key, checked ->
            setFolderAppChecked(context, folderId, activeKeys, key, checked, smartDrawer)
            onVersionChange()
        },
    )
}

@Composable
private fun FolderAppsGroup(
    @StringRes titleRes: Int,
    apps: List<AllAppsFolderAppEntry>,
    activeKeys: List<String>,
    assignedTitles: Map<String, CharSequence>,
    onAppChecked: (String, Boolean) -> Unit,
) {
    PreferenceGroup(title = stringResource(titleRes)) {
        if (apps.isEmpty()) {
            item {
                BasePreference(title = stringResource(R.string.all_apps_folder_no_apps))
            }
        } else {
            apps.forEach { app ->
                item {
                    val active = activeKeys.contains(app.key)
                    SwitchPreference(
                        title = app.label,
                        summary = folderAppSummary(app.key, active, assignedTitles),
                        customIcon = { FolderAppIcon(app.icon) },
                        checked = active,
                        onCheckedChange = { onAppChecked(app.key, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderAppIcon(icon: Drawable) {
    Image(
        painter = drawablePainterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun DeleteFolderDialog(
    folder: FolderRecord,
    smartDrawer: Boolean,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.title.toString()) },
        text = { Text(stringResource(R.string.all_apps_delete_folder_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (smartDrawer) AxSmartDrawerFolderStore.deleteFolder(context, folder.id)
                    else AllAppsFolderStore.deleteFolder(context, folder.id)
                    onDeleted()
                },
            ) {
                Text(stringResource(R.string.all_apps_delete_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun FolderNameDialog(
    @StringRes titleRes: Int,
    initialName: String,
    @StringRes confirmRes: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmedName = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.all_apps_folder_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotEmpty(),
                onClick = { onConfirm(trimmedName) },
            ) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun folderAppSummary(
    key: String,
    active: Boolean,
    assignedTitles: Map<String, CharSequence>,
): String? {
    if (active) {
        return stringResource(R.string.all_apps_folder_in_this_folder)
    }
    val assignedTitle = assignedTitles[key] ?: return null
    return stringResource(R.string.all_apps_folder_assigned_to, assignedTitle)
}

private fun folderAppCount(context: Context, count: Int): String {
    return context.resources.getQuantityString(R.plurals.all_apps_folder_app_count, count, count)
}

private fun setFolderAppChecked(
    context: Context,
    folderId: Int,
    activeKeys: List<String>,
    key: String,
    checked: Boolean,
    smartDrawer: Boolean,
) {
    val updatedKeys = if (checked) {
        (activeKeys + key).distinct()
    } else {
        activeKeys.filterNot { it == key }
    }
    if (smartDrawer) AxSmartDrawerFolderStore.setFolderAppKeys(context, folderId, updatedKeys)
    else AllAppsFolderStore.setFolderAppKeys(context, folderId, updatedKeys)
}

private fun moveFolder(
    context: Context,
    fromPosition: Int,
    toPosition: Int,
    smartDrawer: Boolean,
) {
    if (smartDrawer) AxSmartDrawerFolderStore.moveFolder(context, fromPosition, toPosition)
    else AllAppsFolderStore.moveFolder(context, fromPosition, toPosition)
}

private fun seedSmartDrawerFoldersIfNeeded(
    context: Context,
    apps: List<AllAppsFolderAppEntry>,
): Boolean {
    if (AxSmartDrawerFolderStore.getFolderRecords(context).isNotEmpty()) {
        return false
    }
    val pinnedKeys = LauncherPrefs.get(context).get(LauncherPrefsExt.PINNED_APPS)
    var changed = false
    for (category in AxSmartDrawerManager.getCategoryOrder()) {
        val appKeys = apps
            .filter { it.category == category && !pinnedKeys.contains(it.key) }
            .map { it.key }
        if (appKeys.isEmpty()) {
            continue
        }
        val folderId = AxSmartDrawerFolderStore.createFolder(
            context,
            context.getText(AxSmartDrawerManager.getCategoryTitleRes(category)),
        )
        if (folderId != AllAppsFolderStore.NO_FOLDER_ID) {
            AxSmartDrawerFolderStore.setFolderAppKeys(context, folderId, appKeys)
            changed = true
        }
    }
    return changed
}

private fun loadAllAppsFolderEntries(
    context: Context,
    sdkApps: List<AxAppEntry>,
): List<AllAppsFolderAppEntry> {
    val appFilter = AppFilter(context)
    return sdkApps.mapNotNull { entry ->
        if (entry.className.isEmpty()) {
            return@mapNotNull null
        }
        val componentName = ComponentName(entry.packageName, entry.className)
        if (!appFilter.shouldShowApp(componentName)) {
            return@mapNotNull null
        }
        val key = AllAppsFolderStore.encodeAppKey(
            context,
            componentName,
            Process.myUserHandle(),
        ) ?: return@mapNotNull null
        val category = AxSmartDrawerManager.resolveCategory(context, entry.packageName)
        AllAppsFolderAppEntry(
            key = key,
            label = entry.label,
            icon = entry.icon,
            category = category,
        )
    }.sortedBy { it.label.lowercase() }
}

private data class AllAppsFolderAppEntry(
    val key: String,
    val label: String,
    val icon: Drawable,
    val category: Int,
)
