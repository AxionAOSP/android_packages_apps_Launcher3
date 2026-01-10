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
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.RectF
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.search.*
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.settings.PulseSettingsActivity
import kotlinx.coroutines.delay

interface AllAppsComposeCallbacks {
    fun onAppClicked(iconInfo: ComposeIconInfo)
    fun onAppClickedFromFolder(appInfo: AppInfo)
    fun onAppLongClicked(iconInfo: ComposeIconInfo)
    fun onAppDragStart(iconInfo: ComposeIconInfo)
    fun onAppDragMove(screenX: Float, screenY: Float)
    fun onAppDragEnd(screenX: Float, screenY: Float)
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
    fun onPrivateSpaceClicked(isLocked: Boolean)
    fun onWorkProfileClicked()
    fun onScrollStarted()
    fun onScrollStopped()
    fun onAllAppsTransitionStart()
    fun onAllAppsTransitionEnd()
}

private const val TAB_PERSONAL = 0
private const val TAB_WORK = 1

private val bigIconSize = 64.dp
private val smallIconSize = 28.dp

private sealed interface ContentScreen {
    data object AllApps : ContentScreen
    data object Search : ContentScreen
    data object SearchSettings : ContentScreen
}

@Composable
fun AllAppsComposeContent(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    transitionProgress: Float = 0f,
    allAppsExpanded: Boolean = false,
    openCounter: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showCategories by remember { mutableStateOf(false) }
    var showWorkApps by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var quickAccessOriginStyle by remember { mutableStateOf<String?>(null) }
    var dismissRequest by remember { mutableStateOf(false) }
    
    var drawerLayoutMode by remember { 
        mutableStateOf(
            LauncherPrefs.DRAWER_LAYOUT_MODE.get(context).let {
                if (it == "default") "dynamic" else it
            }
        )
    }
    
    val isDynamicMode = remember(drawerLayoutMode) { drawerLayoutMode == "dynamic" }
    
    var selectedLayoutStyle by remember { mutableStateOf(drawerLayoutMode) }
    
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(
            LauncherFiles.SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "pref_drawer_layout_mode") {
                val mode = LauncherPrefs.DRAWER_LAYOUT_MODE.get(context)
                drawerLayoutMode = if (mode == "default") "dynamic" else mode
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    val isSmartLayout = remember(drawerLayoutMode, selectedLayoutStyle) { 
        when {
            isDynamicMode -> selectedLayoutStyle == "smart"
            else -> drawerLayoutMode == "smart"
        }
    }

    var isLaunching by remember { mutableStateOf(false) }
    
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isSearchActive = searchQuery.isNotEmpty()
    }
    
    var isSearchSettingsOpen by remember { mutableStateOf(false) }
    
    val searchManager = remember { UniversalSearchManager(context) }
    val searchState by searchManager.searchState.collectAsState()
    
    LaunchedEffect(searchQuery, state.apps, state.pinnedApps, state.workApps, state.privateApps, state.isPrivateSpaceLocked, state.hasPrivateApps) {
        val searchableApps = buildList {
            addAll(state.apps)
            addAll(state.pinnedApps)
            addAll(state.workApps)
            if (!state.isPrivateSpaceLocked) {
                addAll(state.privateApps)
            }
        }
        searchManager.search(
            query = searchQuery,
            apps = searchableApps,
            hasPrivateSpace = state.hasPrivateApps,
            isPrivateSpaceLocked = state.isPrivateSpaceLocked,
            privateAppCount = state.privateApps.size
        )
    }
    
    DisposableEffect(Unit) {
        onDispose { searchManager.cleanup() }
    }
    
    LaunchedEffect(transitionProgress, allAppsExpanded) {
        if (allAppsExpanded && transitionProgress == 0f) {
            callbacks.onAllAppsTransitionEnd()
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
        } else if (!allAppsExpanded && transitionProgress == 1f) {
            delay(16)
            callbacks.onAllAppsTransitionStart()
            isLaunching = false
        }
    }

    val (items, sections) = remember(state, searchQuery) {
        buildComposeItems(state, searchQuery)
    }
    
    LaunchedEffect(expandedCategory) {
        if (expandedCategory == null) {
            quickAccessOriginStyle?.let {
                selectedLayoutStyle = it
                quickAccessOriginStyle = null
                isSearchActive = false
            }
            dismissRequest = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        
        val currentScreen = remember(isSearchActive) {
            when {
                isSearchActive -> ContentScreen.Search
                else -> ContentScreen.AllApps
            }
        }
        
        val currentExpandedCategory by rememberUpdatedState(expandedCategory)
        val currentIsSearchActive by rememberUpdatedState(isSearchActive)
        val currentIsImeVisible by rememberUpdatedState(isImeVisible)
        
        LaunchedEffect(isSearchActive, isImeVisible, expandedCategory) {
            callbacks.onSearchExpandedChanged(isSearchActive)
            
            val hasActiveState = isSearchActive || expandedCategory != null || isImeVisible
            
            if (hasActiveState) {
                callbacks.setDismissFolderHandler {
                    when {
                        currentIsImeVisible -> {
                            keyboardController?.hide()
                        }
                        currentExpandedCategory != null -> {
                            dismissRequest = true
                        }
                        currentIsSearchActive -> {
                            searchQuery = ""
                            callbacks.onSearchQueryChanged("")
                            isSearchActive = false
                        }
                    }
                }
            } else {
                callbacks.setDismissFolderHandler(null)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = transitionProgress }
        ) {
                Spacer(modifier = Modifier.height(48.dp))
                
                val onAppClick = remember(callbacks) {
                    { iconInfo: ComposeIconInfo ->
                        isLaunching = true
                        callbacks.onAppClicked(iconInfo)
                    }
                }
                
                if (isDynamicMode) {
                    AllAppsTabBar(
                        selectedLayout = selectedLayoutStyle,
                        onLayoutSelected = { layout -> selectedLayoutStyle = layout }
                    )
                }
            
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        when {
                            targetState == ContentScreen.Search -> {
                                (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                                 slideInVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) { height -> height / 8 }) togetherWith
                                    (fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
                            }
                            initialState == ContentScreen.Search -> {
                                (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                 slideInVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { height -> -height / 12 }) togetherWith
                                    (fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                                     slideOutVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)) { height -> height / 12 })
                            }
                            else -> {
                               fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                            }
                        }.using(SizeTransform(clip = false))
                    },
                    modifier = Modifier.weight(1f)
                ) { screen ->
                        androidx.compose.animation.Crossfade(
                            targetState = state.isLoading,
                            animationSpec = tween(200),
                            label = "loading_crossfade"
                        ) { isLoading ->
                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp
                                    )
                                }
                            } else {
                        when (screen) {
                            ContentScreen.AllApps -> {
                                if (isSmartLayout) {
                                    key("smart_layout") {
                                        AllAppsCategoriesView(
                                            state = state,
                                            expandedCategory = expandedCategory,
                                            onExpandedCategoryChange = { 
                                                expandedCategory = it
                                                callbacks.onFolderExpandedChanged(it != null)
                                            },
                                            onAppClick = onAppClick,
                                            onAppClickedFromFolder = callbacks::onAppClickedFromFolder,
                                            onAppLongClick = callbacks::onAppLongClicked,
                                            onAppDragStart = callbacks::onAppDragStart,
                                            onAppDragMove = callbacks::onAppDragMove,
                                            onAppDragEnd = callbacks::onAppDragEnd,
                                            onScrollStateChanged = callbacks::onScrollStateChanged,
                                            onPrivateSpaceClicked = callbacks::onPrivateSpaceClicked,
                                            onScrollStarted = callbacks::onScrollStarted,
                                            onScrollStopped = callbacks::onScrollStopped,
                                            transitionProgress = transitionProgress,
                                            dismissRequest = dismissRequest,
                                            onDismissRequestChange = { dismissRequest = it },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    val gridKeyPrefix = if (showWorkApps) "work" else "personal"
                                    key("grid_layout_${gridKeyPrefix}_${openCounter}_${selectedLayoutStyle}") {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            AllAppsComposeGrid(
                                                items = items,
                                                sections = sections,
                                                numColumns = state.numColumns,
                                                iconSizePx = state.iconSizePx,
                                                cellHeightPx = state.cellHeightPx,
                                                showLabels = state.showLabels,
                                                onAppClick = onAppClick,
                                                onAppLongClick = callbacks::onAppLongClicked,
                                                onAppDragStart = callbacks::onAppDragStart,
                                                onAppDragMove = callbacks::onAppDragMove,
                                                onAppDragEnd = callbacks::onAppDragEnd,
                                                onScrollStateChanged = callbacks::onScrollStateChanged,
                                                onScrollStarted = callbacks::onScrollStarted,
                                                onScrollStopped = callbacks::onScrollStopped,
                                                transitionProgress = transitionProgress,
                                                keyPrefix = gridKeyPrefix,
                                                recompositionKey = openCounter,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    top = 0.dp,
                                                    bottom = if (isDynamicMode && state.hasWorkApps) 8.dp else 16.dp
                                                )
                                            )
                                            
                                            if (isDynamicMode && (state.hasWorkApps || (state.hasPrivateApps && !state.isPrivateSpaceHidden))) {
                                                ProfileFoldersRow(
                                                    workApps = state.workApps,
                                                    privateApps = if (state.isPrivateSpaceHidden) emptyList() else state.privateApps,
                                                    isPrivateSpaceLocked = state.isPrivateSpaceLocked,
                                                    onWorkFolderClick = {
                                                        quickAccessOriginStyle = selectedLayoutStyle
                                                        selectedLayoutStyle = "smart"
                                                        expandedCategory = AppCategory(-3, "Work", state.workApps)
                                                    },
                                                    onPrivateSpaceClick = {
                                                        if (state.isPrivateSpaceLocked) {
                                                            callbacks.onPrivateSpaceClicked(true)
                                                        } else {
                                                            quickAccessOriginStyle = selectedLayoutStyle
                                                            selectedLayoutStyle = "smart"
                                                            expandedCategory = AppCategory(-5, "Private", state.privateApps)
                                                        }
                                                    },
                                                    iconSizePx = state.iconSizePx,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ContentScreen.Search -> {
                                    UniversalSearchResults(
                                        state = searchState,
                                        onAppClick = { iconInfo ->
                                            isLaunching = true
                                            callbacks.onAppClicked(iconInfo)
                                        },
                                        onAppLongClick = { iconInfo -> callbacks.onAppLongClicked(iconInfo) },
                                        onAppDragStart = { iconInfo -> callbacks.onAppDragStart(iconInfo) },
                                        onAppDragMove = { screenX, screenY -> callbacks.onAppDragMove(screenX, screenY) },
                                        onAppDragEnd = { screenX, screenY -> callbacks.onAppDragEnd(screenX, screenY) },
                                        iconSizePx = state.iconSizePx,
                                        cellHeightPx = state.cellHeightPx,
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
                                        onPrivateSpaceClick = { space ->
                                            if (space.isLocked) {
                                                callbacks.onPrivateSpaceClicked(true)
                                            } else {
                                                searchQuery = ""
                                                callbacks.onSearchQueryChanged("")
                                                isSearchActive = false
                                                keyboardController?.hide()
                                                quickAccessOriginStyle = if (isDynamicMode) "dynamic" else "smart"
                                                selectedLayoutStyle = "smart"
                                                expandedCategory = AppCategory(-5, "Private", state.privateApps)
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
                                else -> { }
                            }
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
                    shouldAutoFocus = LauncherPrefs.DRAWER_OPEN_KEYBOARD.get(LocalContext.current),
                    focusTrigger = openCounter,
                    modifier = Modifier.fillMaxWidth()
                )
            }
    }
}

private const val LAYOUT_DYNAMIC = 0
private const val LAYOUT_SMART = 1

@Composable
private fun AllAppsTabBar(
    selectedLayout: String,
    onLayoutSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        LAYOUT_DYNAMIC to "All Apps",
        LAYOUT_SMART to "Categories"
    )
    
    val selectedIndex = when (selectedLayout) {
        "smart" -> 1
        else -> 0
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            var tabWidths by remember(tabs.size) { 
                mutableStateOf(List(tabs.size) { 0 }) 
            }
            
            var isFirstLaunch by remember { mutableStateOf(true) }

            val indicatorOffset by animateDpAsState(
                targetValue = with(LocalDensity.current) {
                    var offset = 0
                    for (i in 0 until selectedIndex) {
                        offset += tabWidths[i] + 4.dp.roundToPx()
                    }
                    offset.toDp()
                },
                animationSpec = if (isFirstLaunch) snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
                finishedListener = { isFirstLaunch = false }
            )
            val indicatorWidth by animateDpAsState(
                targetValue = with(LocalDensity.current) { 
                    tabWidths.getOrNull(selectedIndex)?.toDp() ?: 0.dp
                },
                animationSpec = if (isFirstLaunch) snap() else spring(dampingRatio = 0.8f, stiffness = 400f)
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
                tabs.forEachIndexed { index, (_, text) ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                onClick = { onLayoutSelected(if (index == 0) "dynamic" else "smart") },
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
    onAppClick: (ComposeIconInfo) -> Unit,
    onAppClickedFromFolder: (AppInfo) -> Unit,
    onAppLongClick: (ComposeIconInfo) -> Unit,
    onAppDragStart: ((ComposeIconInfo) -> Unit)?,
    onAppDragMove: ((screenX: Float, screenY: Float) -> Unit)?,
    onAppDragEnd: ((screenX: Float, screenY: Float) -> Unit)?,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    onPrivateSpaceClicked: (Boolean) -> Unit,
    onScrollStarted: () -> Unit,
    onScrollStopped: () -> Unit,
    transitionProgress: Float,
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    var internalExpandedCategory by remember(expandedCategory) { mutableStateOf(expandedCategory) }
    val appCategories = remember(state.apps) {
        categorizeApps(state.apps, context)
    }
    
    val workCategory = remember(state.workApps) {
        if (state.workApps.isNotEmpty()) {
            AppCategory(-3, "Work", state.workApps)
        } else null
    }
    
    val privateCategory = remember(state.privateApps, state.isPrivateSpaceLocked, state.isPrivateSpaceHidden, state.hasPrivateApps) {
        if (state.hasPrivateApps && !state.isPrivateSpaceHidden) {
            AppCategory(-5, "Private", state.privateApps)
        } else null
    }
    
    val pinnedCategory = remember(state.pinnedApps) {
        if (state.pinnedApps.isNotEmpty()) {
            AppCategory(-2, "Pinned", state.pinnedApps)
        } else null
    }
    
    val predictionsCategory = remember(state.predictedApps) {
        if (state.predictedApps.isNotEmpty()) {
            AppCategory(-4, "Suggestions", state.predictedApps)
        } else null
    }
    
    val categories = remember(appCategories, predictionsCategory, pinnedCategory, workCategory, privateCategory) {
        buildList {
            predictionsCategory?.let { add(it) }
            pinnedCategory?.let { add(it) }
            workCategory?.let { add(it) }
            privateCategory?.let { add(it) }
            addAll(appCategories)
        }
    }

    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }

    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            onScrollStarted()
        } else {
            onScrollStopped()
        }
    }
    
    LaunchedEffect(canScrollUp, canScrollDown, expandedCategory) {
        if (expandedCategory == null) {
            onScrollStateChanged(canScrollUp, canScrollDown)
        }
    }
    
    LaunchedEffect(dismissRequest) {
        if (dismissRequest && expandedCategory != null) {
            onExpandedCategoryChange(null)
            onDismissRequestChange(false)
        }
    }

    AnimatedContent(
        targetState = expandedCategory,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(animationSpec = tween(300)) + 
                    slideInVertically(animationSpec = tween(350)) { it / 6 })
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            } else {
                (fadeIn(animationSpec = tween(200)))
                    .togetherWith(fadeOut(animationSpec = tween(150)) + 
                        slideOutVertically(animationSpec = tween(200)) { it / 6 })
            }
        },
        modifier = modifier.fillMaxSize(),
        label = "category_content"
    ) { category ->
        if (category != null) {
            ExpandedFolderContent(
                category = category,
                onDismiss = { onExpandedCategoryChange(null) },
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onAppDragStart = onAppDragStart,
                onAppDragMove = onAppDragMove,
                onAppDragEnd = onAppDragEnd,
                iconSizePx = state.iconSizePx,
                cellHeightPx = state.cellHeightPx,
                onScrollStateChanged = onScrollStateChanged
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = categories.size,
                    key = { categories[it].id }
                ) { index ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val cat = categories[index]
                        val isWorkCategory = cat.id == -3
                        val isPrivateCategory = cat.id == -5
                        val isPrivateLocked = isPrivateCategory && state.isPrivateSpaceLocked
                        
                        CategoryFolder(
                            category = cat,
                            onClick = {
                                if (isPrivateLocked) {
                                    onPrivateSpaceClicked(true)
                                } else {
                                    internalExpandedCategory = cat
                                    onExpandedCategoryChange(cat)
                                }
                            },
                            onAppClick = onAppClick,
                            onAppLongClick = onAppLongClick,
                            onAppDragStart = onAppDragStart,
                            onAppDragMove = onAppDragMove,
                            onAppDragEnd = onAppDragEnd,
                            isLocked = isPrivateLocked,
                            isWorkProfile = isWorkCategory,
                            isPrivateCategory = isPrivateCategory && !isPrivateLocked,
                            isScrolling = isScrollInProgress
                        )
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                if (isPrivateLocked) {
                                    onPrivateSpaceClicked(true)
                                } else {
                                    internalExpandedCategory = cat
                                    onExpandedCategoryChange(cat)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedFolderContent(
    category: AppCategory,
    onDismiss: () -> Unit,
    onAppClick: (ComposeIconInfo) -> Unit,
    onAppLongClick: (ComposeIconInfo) -> Unit,
    onAppDragStart: ((ComposeIconInfo) -> Unit)?,
    onAppDragMove: ((screenX: Float, screenY: Float) -> Unit)?,
    onAppDragEnd: ((screenX: Float, screenY: Float) -> Unit)?,
    iconSizePx: Int,
    cellHeightPx: Int,
    onScrollStateChanged: (canScrollUp: Boolean, canScrollDown: Boolean) -> Unit,
    isScrolling: Boolean = false
) {
    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }

    LaunchedEffect(canScrollUp, canScrollDown) {
        onScrollStateChanged(canScrollUp, canScrollDown)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(
                        onClick = onDismiss,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().weight(1f)
        ) {
            items(
                count = category.apps.size,
                key = { "expanded_${category.id}_${category.apps[it].componentName}" }
            ) { index ->
                AllAppsComposeAppIcon(
                    appInfo = category.apps[index],
                    showLabel = true,
                    iconSizePx = iconSizePx,
                    cellHeightPx = cellHeightPx,
                    onClick = onAppClick,
                    onLongClick = onAppLongClick,
                    onDragStart = onAppDragStart,
                    onDragMove = onAppDragMove,
                    onDragEnd = onAppDragEnd,
                    isScrolling = isScrollInProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CategoryFolder(
    category: AppCategory,
    onClick: () -> Unit,
    onAppClick: (ComposeIconInfo) -> Unit,
    onAppLongClick: (ComposeIconInfo) -> Unit,
    onAppDragStart: ((ComposeIconInfo) -> Unit)?,
    onAppDragMove: ((screenX: Float, screenY: Float) -> Unit)?,
    onAppDragEnd: ((screenX: Float, screenY: Float) -> Unit)?,
    isLocked: Boolean = false,
    isWorkProfile: Boolean = false,
    isPrivateCategory: Boolean = false,
    isScrolling: Boolean = false
) {
    val hasMoreThanFour = category.apps.size > 4
    val density = LocalDensity.current
    val iconSizePx = with(density) { bigIconSize.roundToPx() }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLocked -> {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
            isWorkProfile -> {
                Icon(
                    imageVector = Icons.Default.Work,
                    contentDescription = "Work",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(64.dp)
                )
            }
            isPrivateCategory -> {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Private",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(64.dp)
                )
            }
            hasMoreThanFour -> {
                Column(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        category.apps.getOrNull(0)?.let { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                                onDragStart = onAppDragStart,
                                onDragMove = onAppDragMove,
                                onDragEnd = onAppDragEnd,
                                isScrolling = isScrolling
                            )
                        }
                        category.apps.getOrNull(1)?.let { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                                onDragStart = onAppDragStart,
                                onDragMove = onAppDragMove,
                                onDragEnd = onAppDragEnd,
                                isScrolling = isScrolling
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        category.apps.getOrNull(2)?.let { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                                onDragStart = onAppDragStart,
                                onDragMove = onAppDragMove,
                                onDragEnd = onAppDragEnd,
                                isScrolling = isScrolling
                            )
                        }
                        Box(
                            modifier = Modifier.size(bigIconSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    category.apps.getOrNull(3)?.let { app ->
                                        PreviewBaseAppIcon(app, smallIconSize)
                                    }
                                    category.apps.getOrNull(4)?.let { app ->
                                        PreviewBaseAppIcon(app, smallIconSize)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    category.apps.getOrNull(5)?.let { app ->
                                        PreviewBaseAppIcon(app, smallIconSize)
                                    }
                                    category.apps.getOrNull(6)?.let { app ->
                                        PreviewBaseAppIcon(app, smallIconSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                val previewApps = category.apps.take(4)
                Column(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        previewApps.getOrNull(0)?.let { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                                onDragStart = onAppDragStart,
                                onDragMove = onAppDragMove,
                                onDragEnd = onAppDragEnd,
                                isScrolling = isScrolling
                            )
                        }
                        previewApps.getOrNull(1)?.let { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                                onDragStart = onAppDragStart,
                                onDragMove = onAppDragMove,
                                onDragEnd = onAppDragEnd,
                                isScrolling = isScrolling
                            )
                        }
                    }
                    if (previewApps.size > 2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            previewApps.getOrNull(2)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app,
                                    showLabel = false,
                                    iconSizePx = iconSizePx,
                                    onClick = onAppClick,
                                    onLongClick = onAppLongClick,
                                    onDragStart = onAppDragStart,
                                    onDragMove = onAppDragMove,
                                    onDragEnd = onAppDragEnd,
                                    isScrolling = isScrolling
                                )
                            }
                            previewApps.getOrNull(3)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app,
                                    showLabel = false,
                                    iconSizePx = iconSizePx,
                                    onClick = onAppClick,
                                    onLongClick = onAppLongClick,
                                    onDragStart = onAppDragStart,
                                    onDragMove = onAppDragMove,
                                    onDragEnd = onAppDragEnd,
                                    isScrolling = isScrolling
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBaseAppIcon(app: AppInfo, iconSize: Dp) {
    val iconDrawable = AllAppsIconProvider.rememberAppIcon(app, iconSize)
    
    Box(
        modifier = Modifier
            .size(iconSize)
            .drawBehind {
                drawContext.canvas.nativeCanvas.let { canvas ->
                    iconDrawable.draw(canvas)
                }
            }
    )
}

private fun categorizeApps(apps: List<AppInfo>, context: Context): List<AppCategory> {
    val pm = context.packageManager
    val configForceGamePackages = context.resources.getStringArray(R.array.config_categorize_force_game_packages).toSet()
    
    val categoryMap = mutableMapOf<Int, MutableList<AppInfo>>()
    
    apps.forEach { appInfo ->
        val pkg = appInfo.intent?.`package` ?: appInfo.componentName?.packageName ?: ""
        
        val androidCategory = when {
            configForceGamePackages.contains(pkg) -> ApplicationInfo.CATEGORY_GAME
            else -> try {
                pm.getApplicationInfo(pkg, 0).category
            } catch (e: Exception) {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
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
        filteredApps.forEach { items.add(AllAppsComposeItem.AppItem(it, section = "search")) }
        return items to emptyList()
    }

    if (state.predictedApps.isNotEmpty()) {
        items.add(AllAppsComposeItem.PredictionsHeader)
        state.predictedApps.take(state.numColumns)
            .forEach { items.add(AllAppsComposeItem.AppItem(it, section = "prediction")) }
    }

    if (state.pinnedApps.isNotEmpty()) {
        sections.add("\uD83D\uDCCC" to items.size)
        items.add(AllAppsComposeItem.PinnedAppsHeader)
        state.pinnedApps.forEach { items.add(AllAppsComposeItem.AppItem(it, section = "pinned")) }
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
            items.add(AllAppsComposeItem.AppItem(app, section = "main"))
        }
    }

    return items to sections
}

@Composable
private fun ProfileFoldersRow(
    workApps: List<AppInfo>,
    privateApps: List<AppInfo>,
    isPrivateSpaceLocked: Boolean,
    onWorkFolderClick: () -> Unit,
    onPrivateSpaceClick: () -> Unit,
    iconSizePx: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (workApps.isNotEmpty()) {
            ProfileFolder(
                title = "Work",
                isWork = true,
                iconSizePx = iconSizePx,
                onClick = onWorkFolderClick,
                modifier = Modifier.weight(1f)
            )
        }
        if (privateApps.isNotEmpty()) {
            ProfileFolder(
                title = "Private",
                isWork = false,
                iconSizePx = iconSizePx,
                isLocked = isPrivateSpaceLocked,
                onClick = onPrivateSpaceClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileFolder(
    title: String,
    isWork: Boolean,
    iconSizePx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false
) {
    val icon = when {
        isWork -> Icons.Default.Work
        isLocked -> Icons.Default.Lock
        else -> Icons.Default.LockOpen
    }
    val iconColor = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceBright
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isLocked) "Tap to unlock" else "Tap to open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
