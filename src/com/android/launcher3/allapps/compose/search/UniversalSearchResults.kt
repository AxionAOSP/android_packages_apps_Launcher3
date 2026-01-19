package com.android.launcher3.allapps.compose.search

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.compose.AllAppsComposeAppIcon
import com.android.launcher3.allapps.compose.ComposeIconInfo
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UniversalSearchResults(
    state: UniversalSearchState,
    onAppClick: (ComposeIconInfo) -> Unit,
    onAppLongClick: (ComposeIconInfo) -> Unit,
    onAppDragStart: ((ComposeIconInfo) -> Unit)?,
    onAppDragMove: ((screenX: Float, screenY: Float) -> Unit)?,
    onAppDragEnd: ((screenX: Float, screenY: Float) -> Unit)?,
    iconSizePx: Int,
    cellHeightPx: Int,
    onContactClick: (UniversalSearchResult.Contact) -> Unit,
    onMessageClick: (UniversalSearchResult.Message) -> Unit,
    onFileClick: (UniversalSearchResult.File) -> Unit,
    onPhotoClick: (UniversalSearchResult.Photo) -> Unit,
    onCalendarClick: (UniversalSearchResult.Calendar) -> Unit,
    onSettingClick: (UniversalSearchResult.Setting) -> Unit,
    onWebActionClick: (UniversalSearchResult.WebAction) -> Unit,
    onInAppSearchClick: (UniversalSearchResult.InAppSearch) -> Unit,
    onPrivateSpaceClick: (UniversalSearchResult.PrivateSpace) -> Unit,
    onAppActionClick: (UniversalSearchResult.AppActions, UniversalSearchResult.AppActions.Action) -> Unit,
    onScrollStateChanged: (Boolean, Boolean) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestFilePermission: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onHistoryDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }

    LaunchedEffect(canScrollUp, canScrollDown) {
        onScrollStateChanged(canScrollUp, canScrollDown)
    }

    LaunchedEffect(state.query) {
        listState.scrollToItem(0)
    }

    val stateHistory = remember(state.query, state.history) {
        if (state.query.isEmpty()) {
            state.history
        } else {
            state.history.filter { it.contains(state.query, ignoreCase = true) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        if (state.privateSpace != null) {
            item(key = "private_space_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it / 4 }
                ) {
                    PrivateSpaceResultItem(
                        privateSpace = state.privateSpace,
                        onClick = { onPrivateSpaceClick(state.privateSpace) }
                    )
                }
            }
        }

        if (state.apps.isNotEmpty()) {
            item(key = "apps_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 25)) + slideInVertically(animationSpec = tween(300, delayMillis = 25)) { -it / 4 }
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        items(state.apps, key = { "search_${it.appInfo.componentName}" }) { app ->
                            AppResultIconItem(
                                app = app,
                                iconSizePx = iconSizePx,
                                cellHeightPx = cellHeightPx,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                                onDragStart = onAppDragStart,
                                onDragMove = onAppDragMove,
                                onDragEnd = onAppDragEnd
                            )
                        }
                    }
                }
            }
        }

        if (state.appActions != null) {
            item(key = "app_actions_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 40)) + slideInVertically(animationSpec = tween(300, delayMillis = 40)) { -it / 4 }
                ) {
                    AppActionsResultItem(
                        appActions = state.appActions,
                        onActionClick = { action -> onAppActionClick(state.appActions, action) }
                    )
                }
            }
        }


        if (stateHistory.isNotEmpty()) {
            item(key = "history_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 50)) + slideInVertically(animationSpec = tween(300, delayMillis = 50)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Recent") {
                        stateHistory.forEachIndexed { index, keyword ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            HistoryResultItem(
                                keyword = keyword,
                                onClick = { onHistoryClick(keyword) },
                                onDelete = { onHistoryDeleteClick(keyword) }
                            )
                        }
                    }
                }
            }
        }
        
        if (state.settings.isNotEmpty()) {
            item(key = "settings_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 50)) + slideInVertically(animationSpec = tween(300, delayMillis = 50)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Settings") {
                        state.settings.forEachIndexed { index, setting ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            SettingResultItem(setting, onClick = { onSettingClick(setting) })
                        }
                    }
                }
            }
        }

        if (state.contacts.isNotEmpty()) {
            item(key = "contacts_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) + slideInVertically(animationSpec = tween(300, delayMillis = 100)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Contacts") {
                        state.contacts.forEachIndexed { index, contact ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            ContactResultItem(contact, onClick = { onContactClick(contact) })
                        }
                    }
                }
            }
        } else if (state.query.isNotEmpty() && !state.hasContactsPermission && !state.isLoading) {
            item(key = "contacts_permission") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 100))
                ) {
                    PermissionRequestItem(
                        title = "Search Contacts",
                        description = "Grant permission to search your contacts",
                        icon = Icons.Default.Person,
                        onClick = onRequestContactsPermission
                    )
                }
            }
        }
        
        if (state.messages.isNotEmpty()) {
            item(key = "messages_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 150)) + slideInVertically(animationSpec = tween(300, delayMillis = 150)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Messages") {
                        state.messages.forEachIndexed { index, message ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            MessageResultItem(message, onClick = { onMessageClick(message) })
                        }
                    }
                }
            }
        } else if (state.query.isNotEmpty() && !state.hasSmsPermission && !state.isLoading) {
            item(key = "messages_permission") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 150))
                ) {
                    PermissionRequestItem(
                        title = "Search Messages",
                        description = "Grant permission to search your messages",
                        icon = Icons.Default.Message,
                        onClick = onRequestSmsPermission
                    )
                }
            }
        }
        
        if (state.files.isNotEmpty()) {
            item(key = "files_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 200)) + slideInVertically(animationSpec = tween(300, delayMillis = 200)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Files") {
                        state.files.forEachIndexed { index, file ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            FileResultItem(file, context, onClick = { onFileClick(file) })
                        }
                    }
                }
            }
        } else if (state.query.isNotEmpty() && !state.hasFilesPermission && !state.isLoading) {
            item(key = "files_permission") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 200))
                ) {
                    PermissionRequestItem(
                        title = "Search Files",
                        description = "Grant permission to search all files",
                        icon = Icons.Default.Folder,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:" + context.packageName)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                onRequestFilePermission()
                            }
                        }
                    )
                }
            }
        }
        
        if (state.photos.isNotEmpty()) {
            item(key = "photos_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 250)) + slideInVertically(animationSpec = tween(300, delayMillis = 250)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Photos") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            items(state.photos) { photo ->
                                PhotoResultItem(photo, context, onClick = { onPhotoClick(photo) })
                            }
                        }
                    }
                }
            }
        } else if (state.query.isNotEmpty() && !state.hasPhotosPermission && !state.isLoading) {
            item(key = "photos_permission") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 250))
                ) {
                    PermissionRequestItem(
                        title = "Search Photos",
                        description = "Grant permission to search your photos",
                        icon = Icons.Default.Image,
                        onClick = onRequestFilePermission
                    )
                }
            }
        }
        if (state.calendar.isNotEmpty()) {
            item(key = "calendar_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 300)) + slideInVertically(animationSpec = tween(300, delayMillis = 300)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Calendar") {
                        state.calendar.forEachIndexed { index, event ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            CalendarResultItem(event, onClick = { onCalendarClick(event) })
                        }
                    }
                }
            }
        } else if (state.query.isNotEmpty() && !state.hasCalendarPermission && !state.isLoading) {
            item(key = "calendar_permission") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 300))
                ) {
                    PermissionRequestItem(
                        title = "Search Calendar",
                        description = "Grant permission to search your calendar",
                        icon = Icons.Default.Event,
                        onClick = onRequestCalendarPermission
                    )
                }
            }
        }

        if (state.webActions.isNotEmpty()) {
            item(key = "web_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 350)) + slideInVertically(animationSpec = tween(300, delayMillis = 350)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Web Search") {
                        state.webActions.forEachIndexed { index, action ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            WebActionItem(action, onClick = { onWebActionClick(action) })
                        }
                    }
                }
            }
        }

        if (state.inAppSearches.isNotEmpty()) {
            item(key = "inapp_section") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 400)) + slideInVertically(animationSpec = tween(300, delayMillis = 400)) { -it / 4 }
                ) {
                    ResultGroupSection(title = "Search In Apps") {
                        state.inAppSearches.forEachIndexed { index, search ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            InAppSearchResultItem(search, onClick = { onInAppSearchClick(search) })
                        }
                    }
                }
            }
        }
        
        val hasNoResults = state.apps.isEmpty() && 
                           state.contacts.isEmpty() && 
                           state.messages.isEmpty() && 
                           state.files.isEmpty() && 
                           state.photos.isEmpty() && 
                           state.calendar.isEmpty() && 
                           state.settings.isEmpty() && 
                           state.webActions.isEmpty() && 
                           state.inAppSearches.isEmpty() &&
                           !state.isLoading
        
        if (hasNoResults) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        NoResultsIllustration(
                            modifier = Modifier.size(120.dp)
                        )
                        Text(
                            text = if (state.query.isEmpty()) "Start typing to search" else "No results found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun InAppSearchResultItem(
    search: UniversalSearchResult.InAppSearch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val context = LocalContext.current
        val icon = remember(search.appInfo?.componentName) {
            val component = search.appInfo?.componentName
            if (component != null) {
                try {
                    context.packageManager.getActivityIcon(component).toBitmap().asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        } else if (search.appInfo != null) {
            Image(
                bitmap = search.appInfo.bitmap.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            val title = search.appInfo?.title ?: "Apps"
            Text(
                text = "Search \"${search.query}\" in $title",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResultGroupSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f)),
            content = content
        )
    }
}

@Composable
private fun AppResultIconItem(
    app: UniversalSearchResult.App,
    iconSizePx: Int,
    cellHeightPx: Int,
    onClick: (ComposeIconInfo) -> Unit,
    onLongClick: (ComposeIconInfo) -> Unit,
    onDragStart: ((ComposeIconInfo) -> Unit)?,
    onDragMove: ((screenX: Float, screenY: Float) -> Unit)?,
    onDragEnd: ((screenX: Float, screenY: Float) -> Unit)?
) {
    Box(
        modifier = Modifier.width(80.dp),
        contentAlignment = Alignment.Center
    ) {
        AllAppsComposeAppIcon(
            appInfo = app.appInfo,
            showLabel = true,
            iconSizePx = iconSizePx,
            cellHeightPx = cellHeightPx,
            onClick = onClick,
            onLongClick = onLongClick,
            onDragStart = onDragStart,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd,
            isScrolling = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ContactResultItem(
    contact: UniversalSearchResult.Contact,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val photoBitmap = remember(contact.photoUri) {
                contact.photoUri?.let { uri ->
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        inputStream?.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) { null }
                }
            }
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            contact.phoneNumber?.let { phone ->
                Text(
                    text = phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageResultItem(
    message: UniversalSearchResult.Message,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceBright),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (message.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.address,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        message.date,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileResultItem(
    file: UniversalSearchResult.File,
    context: Context,
    onClick: () -> Unit
) {
    val icon = when {
        file.mimeType.startsWith("image/") -> Icons.Default.Image
        file.mimeType.startsWith("video/") -> Icons.Default.VideoFile
        file.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        file.mimeType.startsWith("text/") -> Icons.Default.Description
        file.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
        else -> Icons.Default.InsertDriveFile
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceBright),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = Formatter.formatFileSize(context, file.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingResultItem(
    setting: UniversalSearchResult.Setting,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceBright),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = setting.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
         Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WebActionItem(
    action: UniversalSearchResult.WebAction,
    onClick: () -> Unit
) {
    val (icon, title) = when (action.type) {
        WebActionType.GOOGLE -> Pair(Icons.Default.Search, "Search \"${action.query}\" on Google")
        WebActionType.BROWSER -> Pair(Icons.Default.Language, "Search \"${action.query}\" on the web")
        WebActionType.STORE -> Pair(Icons.Default.ShoppingBag, "Search \"${action.query}\" in Play Store")
        WebActionType.SUGGESTION -> Pair(Icons.Default.Search, action.subtitle ?: action.packageName ?: "Search suggestion")
    }
    
    val displayTitle = if (action.type == WebActionType.SUGGESTION) action.subtitle ?: title else title

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val packageIcon = remember(action) {
            val pkg = action.packageName ?: when (action.type) {
                WebActionType.STORE -> "com.android.vending"
                WebActionType.GOOGLE -> "com.google.android.googlequicksearchbox"
                WebActionType.BROWSER -> "com.android.chrome"
                else -> null
            }

            if (pkg != null) {
                try {
                    context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()
                } catch (e: Exception) { null }
            } else null
        }

        if (packageIcon != null) {
             Image(
                bitmap = packageIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.NorthWest,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun HistoryResultItem(
    keyword: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PermissionRequestItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PhotoResultItem(
    photo: UniversalSearchResult.Photo,
    context: Context,
    onClick: () -> Unit
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(photo.uri, Size(256, 256), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = photo.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    } else {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
@Composable
private fun CalendarResultItem(
    event: UniversalSearchResult.Calendar,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val context = LocalContext.current
            val dateStr = DateUtils.formatDateTime(context, event.startTime, DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_DATE)
            val timeString = if (event.isAllDay) {
                "$dateStr - All Day"
            } else {
                "$dateStr - " + DateUtils.formatDateRange(context, event.startTime, event.endTime, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)
            }
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NoResultsIllustration(
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val accentColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "noResults")
    
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    val documentScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation
            translationY = -floatOffset
        }
    ) {
        val canvasSize = size.minDimension
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        val baseDocumentWidth = canvasSize * 0.35f
        val baseDocumentHeight = canvasSize * 0.45f
        val documentWidth = baseDocumentWidth * documentScale
        val documentHeight = baseDocumentHeight * documentScale
        val documentX = centerX - canvasSize * 0.15f - (documentWidth - baseDocumentWidth) / 2
        val documentY = centerY - canvasSize * 0.1f - (documentHeight - baseDocumentHeight) / 2
        
        drawRoundRect(
            color = secondaryColor,
            topLeft = Offset(documentX + 8f, documentY + 8f),
            size = androidx.compose.ui.geometry.Size(documentWidth, documentHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = secondaryColor.copy(alpha = 0.5f),
            topLeft = Offset(documentX + 4f, documentY + 4f),
            size = androidx.compose.ui.geometry.Size(documentWidth, documentHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(documentX, documentY),
            size = androidx.compose.ui.geometry.Size(documentWidth, documentHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        
        val lineStartX = documentX + documentWidth * 0.2f
        val lineEndX = documentX + documentWidth * 0.8f
        for (i in 0..2) {
            val lineY = documentY + documentHeight * (0.35f + i * 0.18f)
            drawLine(
                color = secondaryColor,
                start = Offset(lineStartX, lineY),
                end = Offset(lineEndX - (i * 10f), lineY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        
        val searchRadius = canvasSize * 0.22f
        val searchCenterX = centerX + canvasSize * 0.12f
        val searchCenterY = centerY - canvasSize * 0.12f
        
        drawCircle(
            color = primaryColor,
            radius = searchRadius,
            center = Offset(searchCenterX, searchCenterY),
            style = Stroke(width = canvasSize * 0.06f, cap = StrokeCap.Round)
        )
        
        val handleLength = canvasSize * 0.18f
        val handleAngle = Math.toRadians(45.0)
        val handleStartX = searchCenterX + (searchRadius * kotlin.math.cos(handleAngle)).toFloat()
        val handleStartY = searchCenterY + (searchRadius * kotlin.math.sin(handleAngle)).toFloat()
        drawLine(
            color = primaryColor,
            start = Offset(handleStartX, handleStartY),
            end = Offset(handleStartX + (handleLength * kotlin.math.cos(handleAngle)).toFloat(), 
                         handleStartY + (handleLength * kotlin.math.sin(handleAngle)).toFloat()),
            strokeWidth = canvasSize * 0.07f,
            cap = StrokeCap.Round
        )
        
        val xSize = searchRadius * 0.4f
        drawLine(
            color = secondaryColor.copy(alpha = 0.8f),
            start = Offset(searchCenterX - xSize, searchCenterY - xSize),
            end = Offset(searchCenterX + xSize, searchCenterY + xSize),
            strokeWidth = canvasSize * 0.04f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = secondaryColor.copy(alpha = 0.8f),
            start = Offset(searchCenterX + xSize, searchCenterY - xSize),
            end = Offset(searchCenterX - xSize, searchCenterY + xSize),
            strokeWidth = canvasSize * 0.04f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PrivateSpaceResultItem(
    privateSpace: UniversalSearchResult.PrivateSpace,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (privateSpace.isLocked) Icons.Default.Lock else Icons.Default.LockOpen
        val color = if (privateSpace.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceBright),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Private Space",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (privateSpace.isLocked) "Locked" else "${privateSpace.appCount} apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AppActionsResultItem(
    appActions: UniversalSearchResult.AppActions,
    onActionClick: (UniversalSearchResult.AppActions.Action) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = appActions.appInfo.title.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            appActions.actions.forEach { action ->
                Surface(
                    onClick = { onActionClick(action) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (action.icon != null) {
                            Image(
                                bitmap = action.icon.toBitmap().asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
