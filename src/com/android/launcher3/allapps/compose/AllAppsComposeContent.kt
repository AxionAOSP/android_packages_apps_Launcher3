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

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.search.*
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.settings.PulseSettingsActivity

interface AllAppsComposeCallbacks {
    fun onAppClicked(appInfo: AppInfo, icon: BubbleTextView)
    fun onAppClickedFromFolder(appInfo: AppInfo)
    fun onAppLongClicked(appInfo: AppInfo, icon: BubbleTextView)
    fun onAppDragStart(appInfo: AppInfo, icon: BubbleTextView)
    fun onSearchQueryChanged(query: String)
    fun onTabSelected(tab: Int)
    fun onScrollStateChanged(canScrollUp: Boolean, canScrollDown: Boolean)
    fun onFolderExpandedChanged(expanded: Boolean)
    fun setDismissFolderHandler(handler: (() -> Unit)?)
    fun startActivity(intent: Intent)
    fun requestContactsPermission()
    fun requestSmsPermission()
    fun requestFilePermission()
    fun requestCalendarPermission()
    fun onSearchExpandedChanged(expanded: Boolean)
}

private const val TAB_PERSONAL = 0
private const val TAB_WORK = 1
private const val TAB_CATEGORIES = 2

private sealed interface ContentScreen {
    data object AllApps : ContentScreen
    data object Categories : ContentScreen
    data object Search : ContentScreen
    data object SearchSettings : ContentScreen
}

@Composable
fun AllAppsComposeContent(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    transitionProgress: Float = 1f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showCategories by remember { mutableStateOf(false) }
    var showWorkApps by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var dismissRequest by remember { mutableStateOf(false) }
    
    var wasFullyClosed by remember { mutableStateOf(true) }
    var openCounter by remember { mutableIntStateOf(0) }
    var isLaunching by remember { mutableStateOf(false) }
    
    var isSearchActive by remember { mutableStateOf(false) }
    

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            isSearchActive = true
        }
    }
    
    var isSearchSettingsOpen by remember { mutableStateOf(false) }
    
    val searchManager = remember { UniversalSearchManager(context) }
    val searchState by searchManager.searchState.collectAsState()
    
    LaunchedEffect(searchQuery, state.apps, state.pinnedApps) {
        searchManager.search(searchQuery, state.apps + state.pinnedApps)
    }
    
    DisposableEffect(Unit) {
        onDispose { searchManager.cleanup() }
    }
    
    LaunchedEffect(transitionProgress) {
        if (transitionProgress <= 0.01f) {
            if (!wasFullyClosed) {
                wasFullyClosed = true
                if (!isLaunching) {
                    searchQuery = ""
                    callbacks.onSearchQueryChanged("")
                    isSearchActive = false
                    searchManager.clear()
                } else {
                    isLaunching = false 
                }
                isSearchSettingsOpen = false
                expandedCategory = null
            }
        } else if (wasFullyClosed && transitionProgress > 0.01f) {
            wasFullyClosed = false
            openCounter++
            isLaunching = false
        }
    }

    val (items, sections) = remember(state, searchQuery) {
        buildComposeItems(state, searchQuery)
    }
    
    LaunchedEffect(expandedCategory) {
        if (expandedCategory != null) {
            callbacks.setDismissFolderHandler {
                dismissRequest = true
            }
        } else {
            dismissRequest = false
            callbacks.setDismissFolderHandler(null)
        }
    }

    Box(modifier = modifier.fillMaxSize().alpha(transitionProgress)) {
        Column(modifier = Modifier.fillMaxSize()) {
        

        val isSearching = isSearchActive
        
        val keyboardController = LocalSoftwareKeyboardController.current
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        
        LaunchedEffect(isSearching, isImeVisible) {
            callbacks.onSearchExpandedChanged(isSearching)
            if (isSearching) {
                callbacks.setDismissFolderHandler {
                    if (isImeVisible) {
                        keyboardController?.hide()
                    } else {
                        searchQuery = ""
                        callbacks.onSearchQueryChanged("")
                        isSearchActive = false
                    }
                }
            } else if (expandedCategory == null) {
                callbacks.setDismissFolderHandler(null)
            }
        }
        
        AnimatedVisibility(
            visible = !isSearching,
            enter = fadeIn(animationSpec = tween(300, easing = EaseOutCubic)) + 
                    expandVertically(animationSpec = tween(300, easing = EaseOutCubic)),
            exit = fadeOut(animationSpec = tween(200, easing = EaseInCubic)) + 
                   shrinkVertically(animationSpec = tween(200, easing = EaseInCubic))
        ) {
        AllAppsTabBar(
            hasWorkApps = state.hasWorkApps,
            selectedTab = when {
                showCategories -> TAB_CATEGORIES
                showWorkApps -> TAB_WORK
                else -> TAB_PERSONAL
            },
            onTabSelected = { tab ->
                when (tab) {
                    TAB_PERSONAL -> {
                        showCategories = false
                        showWorkApps = false
                        callbacks.onTabSelected(AllAppsComposeState.TAB_PERSONAL)
                    }
                    TAB_WORK -> {
                        showCategories = false
                        showWorkApps = true
                        callbacks.onTabSelected(AllAppsComposeState.TAB_WORK)
                    }
                    TAB_CATEGORIES -> {
                        showCategories = true
                        showWorkApps = false
                    }
                }
            }
        )
        }

        val currentScreen = remember(showCategories, isSearching) {
            when {
                isSearching -> ContentScreen.Search
                showCategories -> ContentScreen.Categories
                else -> ContentScreen.AllApps
            }
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when {
                    targetState == ContentScreen.Search -> {
                        (fadeIn(animationSpec = tween(300)) + 
                         slideInVertically(animationSpec = tween(300)) { height -> height / 10 }) togetherWith
                            (fadeOut(animationSpec = tween(200)))
                    }
                    initialState == ContentScreen.Search -> {
                        (fadeIn(animationSpec = tween(300))) togetherWith
                            (fadeOut(animationSpec = tween(200)) + 
                             slideOutVertically(animationSpec = tween(200)) { height -> height / 10 })
                    }
                    targetState == ContentScreen.Categories -> {
                        (slideInHorizontally(animationSpec = tween(400, easing = EaseOutCubic)) { width -> width } + 
                         fadeIn(animationSpec = tween(300, easing = EaseOutCubic))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(300, easing = EaseInCubic)) { width -> -width } + 
                             fadeOut(animationSpec = tween(200, easing = EaseInCubic)))
                    }
                    else -> {
                        (slideInHorizontally(animationSpec = tween(400, easing = EaseOutCubic)) { width -> -width } + 
                         fadeIn(animationSpec = tween(300, easing = EaseOutCubic))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(300, easing = EaseInCubic)) { width -> width } + 
                             fadeOut(animationSpec = tween(200, easing = EaseInCubic)))
                    }
                }.using(SizeTransform(clip = false))
            },
            modifier = Modifier.weight(1f)
        ) { screen ->
            key(openCounter) {
            when (screen) {
                ContentScreen.AllApps -> {
                AllAppsComposeGrid(
                    items = items,
                    sections = sections,
                    numColumns = state.numColumns,
                    iconSizePx = state.iconSizePx,
                    cellHeightPx = state.cellHeightPx,
                    showLabels = state.showLabels,
                    onAppClick = { appInfo, icon ->
                         isLaunching = true
                         callbacks.onAppClicked(appInfo, icon)
                    },
                    onAppLongClick = callbacks::onAppLongClicked,
                    onAppDragStart = callbacks::onAppDragStart,
                    onScrollStateChanged = callbacks::onScrollStateChanged,
                    transitionProgress = transitionProgress,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 16.dp
                    )
                )
                }
                ContentScreen.Search -> {
                UniversalSearchResults(
                    state = searchState,
                    onAppClick = { app, view ->
                        isLaunching = true
                        val btv = view as? BubbleTextView ?: (LayoutInflater.from(context)
                            .inflate(R.layout.all_apps_icon, null) as BubbleTextView).apply {
                            applyFromItemInfoWithIcon(app.appInfo)
                        }
                        callbacks.onAppClicked(app.appInfo, btv)
                    },
                    onContactClick = { contact ->
                        isLaunching = true
                        callbacks.startActivity(searchManager.getContactIntent(contact))
                    },
                    onMessageClick = { message ->
                        isLaunching = true
                        callbacks.startActivity(searchManager.getMessageIntent(message))
                    },
                    onFileClick = { file ->
                        try {
                            isLaunching = true
                            callbacks.startActivity(searchManager.getFileIntent(file))
                        } catch (e: Exception) {
                        }
                    },
                    onPhotoClick = { photo ->
                        try {
                            isLaunching = true
                            callbacks.startActivity(searchManager.getPhotoIntent(photo))
                        } catch (e: Exception) {
                        }
                    },

                    onCalendarClick = { calendar ->
                        isLaunching = true
                        callbacks.startActivity(searchManager.getCalendarIntent(calendar))
                    },
                    onSettingClick = { setting ->
                        isLaunching = true
                        callbacks.startActivity(setting.intent)
                    },
                    onInAppSearchClick = { search ->
                        if (search.appInfo.componentName != null) {
                            val intent = Intent(Intent.ACTION_SEARCH).apply {
                                setPackage(search.appInfo.componentName!!.packageName)
                                putExtra("query", search.query)
                                putExtra(SearchManager.QUERY, search.query)
                            }
                            isLaunching = true
                            callbacks.startActivity(intent)
                        }
                    },
                    onWebActionClick = { action ->
                        isLaunching = true
                        when (action.type) {
                            WebActionType.GOOGLE -> callbacks.startActivity(searchManager.getGoogleSearchIntent(action.query))
                            WebActionType.BROWSER -> callbacks.startActivity(searchManager.getBrowserSearchIntent(action.query))
                            WebActionType.STORE -> callbacks.startActivity(searchManager.getStoreSearchIntent(action.query))
                            WebActionType.SUGGESTION -> {
                                action.packageName?.let { pkg ->
                                    callbacks.startActivity(searchManager.getAppSearchIntent(pkg, action.query))
                                }
                            }
                        }
                    },
                    onScrollStateChanged = callbacks::onScrollStateChanged,
                    onRequestContactsPermission = { callbacks.requestContactsPermission() },
                    onRequestSmsPermission = { callbacks.requestSmsPermission() },

                    onRequestFilePermission = { callbacks.requestFilePermission() },
                    onRequestCalendarPermission = { callbacks.requestCalendarPermission() },
                    modifier = Modifier.fillMaxSize()
                )
                }
                ContentScreen.Categories -> {
                    AllAppsCategoriesView(
                        state = state,
                        expandedCategory = expandedCategory,
                        onExpandedCategoryChange = { 
                            expandedCategory = it
                            callbacks.onFolderExpandedChanged(it != null)
                        },
                        onAppClick = callbacks::onAppClicked,
                        onAppClickedFromFolder = callbacks::onAppClickedFromFolder,
                        onAppLongClick = callbacks::onAppLongClicked,
                        onAppDragStart = callbacks::onAppDragStart,
                        onScrollStateChanged = callbacks::onScrollStateChanged,
                        transitionProgress = transitionProgress,
                        dismissRequest = dismissRequest,
                        onDismissRequestChange = { dismissRequest = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> { /* Noop for other screens managed elsewhere */ }
            }
            }
        }

        AllAppsComposeSearchBar(
            query = searchQuery,
            onQueryChange = { query ->
                searchQuery = query
                callbacks.onSearchQueryChanged(query)
            },
            onClearQuery = {
                searchQuery = ""
                callbacks.onSearchQueryChanged("")
            },
            onMenuClick = {
                 isLaunching = true
                 val intent = Intent(context, PulseSettingsActivity::class.java).apply {
                     putExtra("initial_page", 3)
                     flags = Intent.FLAG_ACTIVITY_NEW_TASK
                 }
                 context.startActivity(intent)
            },
            autoFocus = LauncherPrefs.DRAWER_OPEN_KEYBOARD.get(LocalContext.current),
            modifier = Modifier.fillMaxWidth()
        )
        }
    }
}

@Composable
private fun AllAppsTabBar(
    hasWorkApps: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember(hasWorkApps) {
        if (hasWorkApps) {
            listOf(
                TAB_PERSONAL to "Personal",
                TAB_WORK to "Work",
                TAB_CATEGORIES to "Categories"
            )
        } else {
            listOf(
                TAB_PERSONAL to "All",
                TAB_CATEGORIES to "Categories"
            )
        }
    }
    
    val selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            var tabWidths by remember(tabs.size) { 
                mutableStateOf(List(tabs.size) { 0 }) 
            }
            
            val indicatorOffset by animateDpAsState(
                targetValue = with(LocalDensity.current) {
                    var offset = 0
                    for (i in 0 until selectedIndex) {
                        offset += tabWidths[i] + 4.dp.roundToPx()
                    }
                    offset.toDp()
                },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            )
            val indicatorWidth by animateDpAsState(
                targetValue = with(LocalDensity.current) { 
                    tabWidths.getOrNull(selectedIndex)?.toDp() ?: 0.dp
                },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            )
            
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEachIndexed { index, (tabId, text) ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                onClick = { onTabSelected(tabId) },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .onGloballyPositioned { coordinates ->
                                val newWidths = tabWidths.toMutableList()
                                newWidths[index] = coordinates.size.width
                                if (newWidths != tabWidths) {
                                    tabWidths = newWidths
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                animationSpec = tween(200)
                            ).value,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

data class AppCategory(
    val id: Int,
    val name: String,
    val apps: List<AppInfo>
)

@Composable
private fun AllAppsCategoriesView(
    state: AllAppsComposeState,
    expandedCategory: AppCategory?,
    onExpandedCategoryChange: (AppCategory?) -> Unit,
    onAppClick: (AppInfo, BubbleTextView) -> Unit,
    onAppClickedFromFolder: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)?,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    transitionProgress: Float,
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val categories = remember(state.apps) {
        categorizeApps(state.apps, context)
    }
    
    var folderPositions by remember { mutableStateOf(mapOf<Int, Offset>()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
    
    LaunchedEffect(canScrollUp, canScrollDown, expandedCategory) {
        if (expandedCategory == null) {
            onScrollStateChanged(canScrollUp, canScrollDown)
        }
    }
    
    var expandedPinnedApps by remember { mutableStateOf(false) }
    var pinnedAppsPosition by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.pinnedApps.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    val pinnedAlpha by animateFloatAsState(
                        targetValue = if (expandedPinnedApps) 0f else 1f,
                        animationSpec = tween(durationMillis = 200),
                        label = "pinnedAlpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .alpha(pinnedAlpha)
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInParent()
                                val centerX = position.x + coordinates.size.width / 2f
                                val centerY = position.y + coordinates.size.height / 2f
                                pinnedAppsPosition = Offset(centerX, centerY)
                            }
                    ) {
                        PinnedAppsCard(
                            pinnedApps = state.pinnedApps,
                            onClick = { expandedPinnedApps = true },
                            onAppClick = onAppClick,
                            onAppLongClick = onAppLongClick,
                            onAppDragStart = onAppDragStart
                        )
                    }
                }
            }
            
            items(
                count = categories.size,
                key = { categories[it].id }
            ) { index ->
                val isExpanded = expandedCategory?.id == categories[index].id
                val folderAlpha by animateFloatAsState(
                    targetValue = if (isExpanded) 0f else 1f,
                    animationSpec = tween(durationMillis = 200),
                    label = "folderAlpha"
                )
                
                Box(
                    modifier = Modifier
                        .alpha(folderAlpha)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInParent()
                            val centerX = position.x + coordinates.size.width / 2f
                            val centerY = position.y + coordinates.size.height / 2f
                            folderPositions = folderPositions + (categories[index].id to Offset(centerX, centerY))
                        }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CategoryFolder(
                            category = categories[index],
                            onClick = { onExpandedCategoryChange(categories[index]) },
                            onAppClick = onAppClick,
                            onAppLongClick = onAppLongClick,
                            onAppDragStart = onAppDragStart
                        )
                        Text(
                            text = categories[index].name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        expandedCategory?.let { category ->
            val folderCenter = folderPositions[category.id]
            key(category.id) {
                ExpandedFolderOverlay(
                    category = category,
                    onAppClickedFromFolder = onAppClickedFromFolder,
                    onAppLongClick = onAppLongClick,
                    onAppDragStart = onAppDragStart,
                    onDismiss = { 
                        onExpandedCategoryChange(null)
                        onDismissRequestChange(false)
                    },
                    iconSizePx = state.iconSizePx,
                    cellHeightPx = state.cellHeightPx,
                    onScrollStateChanged = onScrollStateChanged,
                    originOffset = folderCenter,
                    containerSize = containerSize,
                    dismissRequest = dismissRequest
                )
            }
        }
        
        if (expandedPinnedApps && state.pinnedApps.isNotEmpty()) {
            ExpandedPinnedAppsOverlay(
                pinnedApps = state.pinnedApps,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onAppDragStart = onAppDragStart,
                onDismiss = { expandedPinnedApps = false },
                iconSizePx = state.iconSizePx,
                cellHeightPx = state.cellHeightPx,
                onScrollStateChanged = onScrollStateChanged,
                originOffset = pinnedAppsPosition,
                containerSize = containerSize
            )
        }
    }
}

@Composable
private fun CategoryFolder(
    category: AppCategory,
    onClick: () -> Unit,
    onAppClick: (AppInfo, BubbleTextView) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)?
) {
    val hasMoreThanFour = category.apps.size > 4
    val bigIconSize = 64.dp
    val smallIconSize = 28.dp
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hasMoreThanFour) {
            Column(
                modifier = Modifier.wrapContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    category.apps.getOrNull(0)?.let { app ->
                        FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                    }
                    category.apps.getOrNull(1)?.let { app ->
                        FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    category.apps.getOrNull(2)?.let { app ->
                        FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                    }
                    Box(
                        modifier = Modifier
                            .size(bigIconSize)
                            .clickable(
                                onClick = onClick,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                category.apps.getOrNull(3)?.let { app ->
                                    FolderPreviewIcon(app, { _, _ -> onClick() }, { _, _ -> }, null, iconSize = smallIconSize)
                                }
                                category.apps.getOrNull(4)?.let { app ->
                                    FolderPreviewIcon(app, { _, _ -> onClick() }, { _, _ -> }, null, iconSize = smallIconSize)
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                category.apps.getOrNull(5)?.let { app ->
                                    FolderPreviewIcon(app, { _, _ -> onClick() }, { _, _ -> }, null, iconSize = smallIconSize)
                                }
                                category.apps.getOrNull(6)?.let { app ->
                                    FolderPreviewIcon(app, { _, _ -> onClick() }, { _, _ -> }, null, iconSize = smallIconSize)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val previewApps = category.apps.take(4)
            Column(
                modifier = Modifier.wrapContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    previewApps.getOrNull(0)?.let { app ->
                        FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                    }
                    previewApps.getOrNull(1)?.let { app ->
                        FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                    }
                }
                if (previewApps.size > 2) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        previewApps.getOrNull(2)?.let { app ->
                            FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                        }
                        previewApps.getOrNull(3)?.let { app ->
                            FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart, iconSize = bigIconSize)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderPreviewIcon(
    appInfo: AppInfo,
    onAppClick: (AppInfo, BubbleTextView) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp
) {
    var bubbleTextView by remember { mutableStateOf<BubbleTextView?>(null) }
    var imageView by remember { mutableStateOf<ImageView?>(null) }
    
    val currentOnClick by rememberUpdatedState(onAppClick)
    val currentOnLongClick by rememberUpdatedState(onAppLongClick)
    val currentOnDragStart by rememberUpdatedState(onAppDragStart)
    
    val density = LocalDensity.current
    val iconSizePx = with(density) { iconSize.roundToPx() }
    
    fun syncBubbleTextViewBounds() {
        val iv = imageView ?: return
        val btv = bubbleTextView ?: return
        val location = IntArray(2)
        iv.getLocationOnScreen(location)
        btv.layout(location[0], location[1], location[0] + iconSizePx, location[1] + iconSizePx)
    }
    
    AndroidView(
        factory = { ctx ->
            val btv = (LayoutInflater.from(ctx)
                .inflate(R.layout.all_apps_icon, null) as BubbleTextView).apply {
                appInfo.container = LauncherSettings.Favorites.CONTAINER_ALL_APPS
                applyFromItemInfoWithIcon(appInfo)
                setTextVisibility(false)
                isClickable = false
                isLongClickable = false
            }
            bubbleTextView = btv
            
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(btv.icon)
                layoutParams = ViewGroup.LayoutParams(iconSizePx, iconSizePx)
                imageView = this
            }
        },
        update = { view ->
            bubbleTextView?.let { btv ->
                btv.applyFromItemInfoWithIcon(appInfo)
                view.setImageDrawable(btv.icon)
            }
            imageView = view
        },
        modifier = modifier
            .size(iconSize)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { 
                    syncBubbleTextViewBounds()
                    bubbleTextView?.let { currentOnClick(appInfo, it) } 
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
            }
    )
}

@Composable
private fun ExpandedFolderOverlay(
    category: AppCategory,
    onAppClickedFromFolder: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)?,
    onDismiss: () -> Unit,
    iconSizePx: Int,
    cellHeightPx: Int,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    originOffset: Offset?,
    containerSize: IntSize,
    dismissRequest: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    LaunchedEffect(dismissRequest) {
        if (dismissRequest) {
            visible = false
        }
    }
    
    val originX = if (containerSize.width > 0 && originOffset != null) {
        (originOffset.x / containerSize.width).coerceIn(0f, 1f)
    } else 0.5f
    
    val originY = if (containerSize.height > 0 && originOffset != null) {
        (originOffset.y / containerSize.height).coerceIn(0f, 1f)
    } else 0.5f
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "folderScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        finishedListener = { if (!visible) onDismiss() },
        label = "folderAlpha"
    )
    
    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
    
    LaunchedEffect(canScrollUp, canScrollDown) {
        onScrollStateChanged(canScrollUp, canScrollDown)
    }
    
    LaunchedEffect(Unit) { visible = true }
    
    val handleDismiss = {
        visible = false
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick = handleDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha,
                    transformOrigin = TransformOrigin(originX, originY)
                )
                .clickable(
                    enabled = false,
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(
                        count = category.apps.size,
                        key = { category.apps[it].componentName.toString() }
                    ) { index ->
                        AllAppsComposeAppIcon(
                            appInfo = category.apps[index],
                            showLabel = true,
                            iconSizePx = iconSizePx,
                            cellHeightPx = cellHeightPx,
                            onClick = { appInfo, _ -> 
                                onAppClickedFromFolder(appInfo)
                                onDismiss()
                            },
                            onLongClick = onAppLongClick,
                            onDragStart = onAppDragStart,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PinnedAppsCard(
    pinnedApps: List<AppInfo>,
    onClick: () -> Unit,
    onAppClick: (AppInfo, BubbleTextView) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewApps = pinnedApps.take(4)
                previewApps.forEach { app ->
                    FolderPreviewIcon(app, onAppClick, onAppLongClick, onAppDragStart)
                }
            }
        }
        
        Text(
            text = "Pinned",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun ExpandedPinnedAppsOverlay(
    pinnedApps: List<AppInfo>,
    onAppClick: (AppInfo, BubbleTextView) -> Unit,
    onAppLongClick: (AppInfo, BubbleTextView) -> Unit,
    onAppDragStart: ((AppInfo, BubbleTextView) -> Unit)?,
    onDismiss: () -> Unit,
    iconSizePx: Int,
    cellHeightPx: Int,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    originOffset: Offset?,
    containerSize: IntSize
) {
    var visible by remember { mutableStateOf(false) }
    
    val originX = if (containerSize.width > 0 && originOffset != null) {
        (originOffset.x / containerSize.width).coerceIn(0f, 1f)
    } else 0.5f
    
    val originY = if (containerSize.height > 0 && originOffset != null) {
        (originOffset.y / containerSize.height).coerceIn(0f, 1f)
    } else 0.5f
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "pinnedScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        finishedListener = { if (!visible) onDismiss() },
        label = "pinnedAlpha"
    )
    
    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
    
    LaunchedEffect(canScrollUp, canScrollDown) {
        onScrollStateChanged(canScrollUp, canScrollDown)
    }
    
    LaunchedEffect(Unit) { visible = true }
    
    val handleDismiss = {
        visible = false
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick = handleDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha,
                    transformOrigin = TransformOrigin(originX, originY)
                )
                .clickable(
                    enabled = false,
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(
                        count = pinnedApps.size,
                        key = { pinnedApps[it].componentName.toString() }
                    ) { index ->
                        AllAppsComposeAppIcon(
                            appInfo = pinnedApps[index],
                            showLabel = true,
                            iconSizePx = iconSizePx,
                            cellHeightPx = cellHeightPx,
                            onClick = { appInfo, view -> 
                                onAppClick(appInfo, view)
                                onDismiss()
                            },
                            onLongClick = onAppLongClick,
                            onDragStart = onAppDragStart,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun categorizeApps(apps: List<AppInfo>, context: Context): List<AppCategory> {
    val pm = context.packageManager
    
    val categoryMap = mutableMapOf<Int, MutableList<AppInfo>>()
    
    apps.forEach { appInfo ->
        val pkg = appInfo.intent?.`package` ?: appInfo.componentName?.packageName ?: ""
        val androidCategory = try {
            pm.getApplicationInfo(pkg, 0).category
        } catch (e: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        
        categoryMap.getOrPut(androidCategory) { mutableListOf() }.add(appInfo)
    }
    
    val result = mutableListOf<AppCategory>()
    
    categoryMap[ApplicationInfo.CATEGORY_GAME]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(1, "Games", appList))
    }
    
    categoryMap[ApplicationInfo.CATEGORY_SOCIAL]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(2, "Social", appList))
    }
    
    val mediaApps = mutableListOf<AppInfo>()
    categoryMap[ApplicationInfo.CATEGORY_AUDIO]?.let { mediaApps.addAll(it) }
    categoryMap[ApplicationInfo.CATEGORY_VIDEO]?.let { mediaApps.addAll(it) }
    categoryMap[ApplicationInfo.CATEGORY_IMAGE]?.let { mediaApps.addAll(it) }
    if (mediaApps.isNotEmpty()) {
        result.add(AppCategory(3, "Media", mediaApps))
    }
    
    categoryMap[ApplicationInfo.CATEGORY_NEWS]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(4, "News", appList))
    }
    
    categoryMap[ApplicationInfo.CATEGORY_MAPS]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(5, "Maps", appList))
    }
    
    categoryMap[ApplicationInfo.CATEGORY_PRODUCTIVITY]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(6, "Productivity", appList))
    }
    
    categoryMap[ApplicationInfo.CATEGORY_ACCESSIBILITY]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(7, "Accessibility", appList))
    }
    
    categoryMap[ApplicationInfo.CATEGORY_UNDEFINED]?.let { appList ->
        if (appList.isNotEmpty()) result.add(AppCategory(8, "Other", appList))
    }
    
    return result.filter { it.apps.isNotEmpty() }
}

private fun buildComposeItems(
    state: AllAppsComposeState,
    searchQuery: String
): Pair<List<AllAppsComposeItem>, List<Pair<String, Int>>> {
    val items = mutableListOf<AllAppsComposeItem>()
    val sections = mutableListOf<Pair<String, Int>>()

    if (searchQuery.isNotEmpty()) {
        val filteredApps = state.apps.filter { app ->
            app.title?.toString()?.contains(searchQuery, ignoreCase = true) == true
        }
        if (filteredApps.isEmpty()) {
            return listOf(AllAppsComposeItem.EmptySearchResult) to emptyList()
        }
        filteredApps.forEach { items.add(AllAppsComposeItem.AppItem(it)) }
        return items to emptyList()
    }

    if (state.predictedApps.isNotEmpty()) {
        items.add(AllAppsComposeItem.PredictionsHeader)
        state.predictedApps.forEach { items.add(AllAppsComposeItem.AppItem(it)) }
    }

    if (state.pinnedApps.isNotEmpty()) {
        items.add(AllAppsComposeItem.PinnedAppsHeader)
        state.pinnedApps.forEach { items.add(AllAppsComposeItem.AppItem(it)) }
    }

    if (state.apps.isNotEmpty()) {
        if (state.predictedApps.isNotEmpty() || state.pinnedApps.isNotEmpty()) {
            items.add(AllAppsComposeItem.AllAppsHeader)
        }

        var lastSection: String? = null
        state.apps.forEach { app ->
            val section = app.sectionName?.toString()?.uppercase()?.firstOrNull()?.toString() ?: "#"
            if (section != lastSection) {
                sections.add(section to items.size)
                lastSection = section
            }
            items.add(AllAppsComposeItem.AppItem(app))
        }
    }

    return items to sections
}
