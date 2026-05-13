@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.android.launcher3.allapps.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.BackHandler
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneTransitionLayout
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import android.app.SearchManager
import android.content.Context
import android.content.Intent

import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView

import com.android.launcher3.allapps.AllAppsComposeController
import com.android.launcher3.util.Themes

import com.android.launcher3.BubbleTextView

import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.data.AllAppsIconProvider
import com.android.launcher3.allapps.compose.data.AppCategoryManager
import com.android.launcher3.allapps.compose.data.PinnedAppsManager
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeCallbacks
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo
import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.allapps.compose.domain.categorizeApps
import com.android.launcher3.allapps.compose.search.domain.*
import com.android.launcher3.allapps.compose.search.model.*
import com.android.launcher3.allapps.compose.search.ui.*
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.settings.PulseSettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun AllAppsComposeContent(
    state: AllAppsComposeState,
    controller: AllAppsComposeController,
    callbacks: AllAppsComposeCallbacks,
    transitionProgressProvider: () -> Float = { 0f },
    allAppsExpanded: Boolean = false,
    openCounter: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedProfileTab = controller.selectedProfileTab
    var searchQuery by remember { mutableStateOf(controller.searchQuery) }
    var expandedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var dismissRequest by remember { mutableStateOf(false) }
    
    val drawerLayoutMode = rememberPreference(PreferenceKeys.DRAWER_LAYOUT_MODE) {
        LauncherPrefs.DRAWER_LAYOUT_MODE.get(it).let { m -> if (m == "default") "dynamic" else m }
    }
    val isSmartLayout = drawerLayoutMode == "smart"
    val isSearchBarAtTop = rememberPreference(PreferenceKeys.DRAWER_SEARCH_BAR_POSITION) {
        LauncherPrefs.DRAWER_SEARCH_BAR_POSITION.get(it) == PreferenceKeys.SEARCH_BAR_POSITION_TOP
    }

    var isLaunching by remember { mutableStateOf(false) }
    var folderPickerTarget by remember { mutableStateOf<String?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<AppCategory?>(null) }
    var renameFolderTarget by remember { mutableStateOf<AppCategory?>(null) }
    var folderActionsTarget by remember { mutableStateOf<AppCategory?>(null) }

    DisposableEffect(Unit) {
        callbacks.setShowFolderPickerHandler { componentName ->
            folderPickerTarget = componentName
        }
        onDispose {
            callbacks.setShowFolderPickerHandler(null)
        }
    }

    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val isSearchActiveState by remember { derivedStateOf { searchQuery.isNotEmpty() } }
    val hasOverlayInput = renameFolderTarget != null || folderPickerTarget != null || folderActionsTarget != null
    val isSearchActive = isSearchActiveState || (isImeVisible && !hasOverlayInput)
    
    var showPrivateSpaceScreen by remember { mutableStateOf(false) }
    
    val searchManager = remember { UniversalSearchManager(context) }
    DisposableEffect(searchManager) { onDispose { searchManager.cleanup() } }
    val searchState by searchManager.searchState.collectAsStateWithLifecycle()

    val categoryManager = remember { AppCategoryManager(context) }
    val pinnedAppsManager = remember { PinnedAppsManager(context) }
    val categoryOverrides by categoryManager.overrides.collectAsStateWithLifecycle()
    val customCategories by categoryManager.customCategories.collectAsStateWithLifecycle()

    var isSearchPending by remember { mutableStateOf(false) }
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(searchManager) {
        snapshotFlow { searchQuery to latestState }
            .distinctUntilChanged()
            .collectLatest { (query, s) ->
                if (query.isNotEmpty()) {
                    isSearchPending = true
                    delay(600)
                }
                val searchableApps = s.apps + s.pinnedApps + s.workApps +
                    if (!s.isPrivateSpaceLocked) s.privateApps else emptyList()
                searchManager.search(
                    query = query,
                    apps = searchableApps,
                    hasPrivateSpace = s.hasPrivateApps,
                    isPrivateSpaceLocked = s.isPrivateSpaceLocked,
                    privateAppCount = s.privateApps.size
                )
                isSearchPending = false
            }
    }

    val topSearchResult by remember {
        derivedStateOf {
            if (searchQuery.isNotEmpty() && searchState.apps.isNotEmpty() && !searchState.isLoading) {
                searchState.apps.first()
            } else null
        }
    }
    
    val currentExpanded by rememberUpdatedState(allAppsExpanded)

    var isOpening by remember { mutableStateOf(true) }
    var reopenTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastProgress = transitionProgressProvider()
        snapshotFlow { transitionProgressProvider() }.collect { progress ->
            if (progress > lastProgress + TRANSITION_DIRECTION_EPSILON) isOpening = true
            else if (progress < lastProgress - TRANSITION_DIRECTION_EPSILON) isOpening = false
            if (lastProgress == 0f && progress > 0f) reopenTrigger++
            lastProgress = progress
            if (currentExpanded && progress == 1f) {
                if (!isLaunching) {
                    searchQuery = ""
                    callbacks.onSearchQueryChanged("")
                    searchManager.clear()
                } else {
                    isLaunching = false
                }
                expandedCategory = null
            } else if (!currentExpanded && progress == 0f) {
                isLaunching = false
                searchQuery = ""
                callbacks.onSearchQueryChanged("")
                searchManager.clear()
                expandedCategory = null
                showPrivateSpaceScreen = false
            }
        }
    }

    val customFolderCategories = remember(state.apps, state.pinnedApps, categoryOverrides, customCategories) {
        if (customCategories.isEmpty()) emptyList()
        else {
            val customCategoryApps = mutableMapOf<Int, MutableList<AppInfo>>()
            
            val allAppsToCheck = state.apps + state.pinnedApps
            allAppsToCheck.forEach { app ->
                val component = app.componentName?.flattenToString() ?: ""
                val override = categoryOverrides[component]
                if (override != null && override >= AppCategoryManager.CUSTOM_ID_START) {
                    customCategoryApps.getOrPut(override) { mutableListOf() }.add(app)
                }
            }
            customCategories.mapNotNull { (id, name) ->
                val apps = customCategoryApps[id]
                if (apps != null && apps.isNotEmpty()) AppCategory(id, name, apps, isCustom = true)
                else null
            }
        }
    }

    val (items, sections) = remember(state, searchQuery, customFolderCategories) {
        buildComposeItems(state, searchQuery, customFolderCategories)
    }

    val (workItems, workSections) = remember(state.workApps) {
        buildWorkComposeItems(state.workApps)
    }

    LaunchedEffect(customFolderCategories, expandedCategory) {
        val ec = expandedCategory ?: return@LaunchedEffect
        if (!ec.isCustom) return@LaunchedEffect
        val updated = customFolderCategories.find { it.id == ec.id }
        if (updated == null || updated.apps.isEmpty()) {
            expandedCategory = null
        } else if (updated.apps != ec.apps) {
            expandedCategory = updated
        }
    }

    LaunchedEffect(expandedCategory) {
        if (expandedCategory == null) {
            callbacks.onFolderExpandedChanged(false)
            dismissRequest = false
        }
    }

    val currentCallbacks by rememberUpdatedState(callbacks)
    val interactions = remember {
        AllAppsInteractions(
            controller = controller,
            onAppClick = { iconInfo ->
                isLaunching = true
                currentCallbacks.onAppClicked(iconInfo)
            },
            onAppLongClick = { currentCallbacks.onAppLongClicked(it) },
            onAppDragStart = { currentCallbacks.onAppDragStart(it) },
            onAppDragMove = { x, y -> currentCallbacks.onAppDragMove(x, y) },
            onAppDragEnd = { x, y -> currentCallbacks.onAppDragEnd(x, y) },
            onAppClickedFromFolder = { currentCallbacks.onAppClickedFromFolder(it) },
            onPrivateSpaceClicked = { currentCallbacks.onPrivateSpaceClicked(it) },
            onFolderExpandedChanged = { currentCallbacks.onFolderExpandedChanged(it) }
        )
    }

    val profileVersion = controller.profileVersion
    LaunchedEffect(profileVersion) {
        if (profileVersion > 0) {
            searchQuery = ""
            callbacks.onSearchQueryChanged("")
            expandedCategory = null
            showPrivateSpaceScreen = false
            dismissRequest = false
            isLaunching = false
            folderPickerTarget = null
            deleteFolderTarget = null
            renameFolderTarget = null
            folderActionsTarget = null
            callbacks.onTabSelected(TAB_PERSONAL)
        }
    }

    val iconConfig = IconConfig(themed = rememberThemedIcons(), version = state.iconVersion)
    CompositionLocalProvider(
        LocalAllAppsInteractions provides interactions,
        LocalIconConfig provides iconConfig
    ) {
    Box(modifier = modifier.fillMaxSize()) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        val targetScene = when {
            isSearchActive -> AllAppsScenes.Search
            expandedCategory != null -> AllAppsScenes.FolderExpanded
            isSmartLayout -> AllAppsScenes.SmartDrawer
            else -> AllAppsScenes.Drawer
        }
        val sceneLayoutState = rememberMutableSceneTransitionLayoutState(
            initialScene = if (isSmartLayout) AllAppsScenes.SmartDrawer else AllAppsScenes.Drawer,
            transitions = allAppsTransitions()
        )
        val sceneScope = rememberCoroutineScope()
        LaunchedEffect(targetScene) {
            val currentScene = sceneLayoutState.transitionState.currentScene
            if (currentScene != targetScene) {
                sceneLayoutState.setTargetScene(targetScene, this)
            }
        }

        val currentExpandedCategory by rememberUpdatedState(expandedCategory)
        val currentIsSearchActive by rememberUpdatedState(isSearchActive)
        val currentIsImeVisible by rememberUpdatedState(isImeVisible)

        val currentShowPrivateSpaceScreen by rememberUpdatedState(showPrivateSpaceScreen)

        var isOnPrivateSpacePagerPage by remember { mutableStateOf(false) }
        var pagerBackAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        LaunchedEffect(isSearchActive) {
            callbacks.onSearchExpandedChanged(isSearchActive)
        }

        val hasBackTarget = expandedCategory != null || isSearchActive || isImeVisible || showPrivateSpaceScreen || isOnPrivateSpacePagerPage
        val handleBack = {
            when {
                currentShowPrivateSpaceScreen -> showPrivateSpaceScreen = false
                currentIsImeVisible -> keyboardController?.hide()
                currentExpandedCategory != null -> {
                    expandedCategory = null
                    callbacks.onFolderExpandedChanged(false)
                }
                currentIsSearchActive -> {
                    searchQuery = ""
                    callbacks.onSearchQueryChanged("")
                }
                isOnPrivateSpacePagerPage -> pagerBackAction?.invoke()
            }
            Unit
        }
        LaunchedEffect(hasBackTarget) {
            controller.registerBackIntercept(hasBackTarget)
        }
        SideEffect {
            val nonFolderBack = isSearchActive || isImeVisible || showPrivateSpaceScreen || isOnPrivateSpacePagerPage
            controller.backAction = if (nonFolderBack) handleBack else null
            controller.folderBackAction = if (expandedCategory != null) {
                {
                    expandedCategory = null
                    callbacks.onFolderExpandedChanged(false)
                }
            } else null
        }

        val drawerBaseBg = drawerBaseBackgroundColor()
        val isTablet = state.isTablet
        val sheetShape = if (isTablet) {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        } else {
            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        }

        val tabletScrimColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)

        if (isTablet && controller.backProgress == 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = drawerContainerAlpha(transitionProgressProvider(), isOpening)
                    }
                    .drawBehind { drawRect(tabletScrimColor) }
            )
        }

        Box(
            modifier = (if (isTablet) {
                Modifier
                    .fillMaxWidth(0.75f)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            } else {
                Modifier.fillMaxSize()
            })
                .graphicsLayer {
                    alpha = drawerContainerAlpha(transitionProgressProvider(), isOpening)
                }
                .clip(sheetShape)
                .background(drawerBaseBg)
                .then(if (!isTablet) Modifier.statusBarsPadding() else Modifier)
        ) {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isSearchBarAtTop) Modifier else Modifier.navigationBarsPadding())
                            .padding(bottom = if (isSearchBarAtTop) 0.dp else 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                SceneTransitionLayout(
                    state = sceneLayoutState,
                    modifier = Modifier.fillMaxSize()
                        .then(if (isSearchBarAtTop) Modifier else Modifier.navigationBarsPadding())
                        .padding(
                            bottom = if (isSearchBarAtTop) 0.dp else 32.dp,
                            top = allAppsSceneTopPadding(isSearchBarAtTop, isTablet)
                        )
                        .clipToBounds()
                ) {
                    scene(AllAppsScenes.Drawer) {
                        Box(modifier = Modifier.element(AllAppsElements.DrawerRoot).fillMaxSize()) {
                        DrawerSceneContent(
                            state = state,
                            callbacks = callbacks,
                            items = items,
                            sections = sections,
                            workItems = workItems,
                            workSections = workSections,
                            categoryManager = categoryManager,
                            expandedCategory = expandedCategory,
                            onExpandedCategoryChange = {
                                expandedCategory = it
                                callbacks.onFolderExpandedChanged(it != null)
                            },
                            onCustomFolderAction = { folderActionsTarget = it },
                            selectedProfileTab = selectedProfileTab,
                            onProfileTabSelected = { tab ->
                                if (tab != selectedProfileTab) {
                                    callbacks.onTabSelected(tab)
                                }
                            },
                            transitionProgressProvider = transitionProgressProvider,
                            dismissRequest = dismissRequest,
                            onDismissRequestChange = { dismissRequest = it },
                            openCounter = openCounter,
                            allAppsExpanded = allAppsExpanded,
                            isOpening = isOpening,
                            reopenTrigger = reopenTrigger,
                            isOnPrivateSpacePagerPage = isOnPrivateSpacePagerPage,
                            onPrivateSpacePagerChanged = { isOnPrivateSpacePagerPage = it },
                            onPagerBackAction = { pagerBackAction = it },
                            onLaunch = { isLaunching = true },
                            isSearchBarAtTop = isSearchBarAtTop
                        )
                        }
                    }

                    scene(AllAppsScenes.SmartDrawer) {
                        Box(modifier = Modifier.element(AllAppsElements.SmartDrawerRoot).fillMaxSize()) {
                        SmartDrawerSceneContent(
                            state = state,
                            callbacks = callbacks,
                            workItems = workItems,
                            workSections = workSections,
                            categoryManager = categoryManager,
                            expandedCategory = expandedCategory,
                            onExpandedCategoryChange = {
                                expandedCategory = it
                                callbacks.onFolderExpandedChanged(it != null)
                            },
                            onCustomFolderAction = { folderActionsTarget = it },
                            selectedProfileTab = selectedProfileTab,
                            onProfileTabSelected = { tab ->
                                if (tab != selectedProfileTab) {
                                    callbacks.onTabSelected(tab)
                                }
                            },
                            transitionProgressProvider = transitionProgressProvider,
                            dismissRequest = dismissRequest,
                            onDismissRequestChange = { dismissRequest = it },
                            openCounter = openCounter,
                            allAppsExpanded = allAppsExpanded,
                            isOpening = isOpening,
                            reopenTrigger = reopenTrigger,
                            isOnPrivateSpacePagerPage = isOnPrivateSpacePagerPage,
                            onPrivateSpacePagerChanged = { isOnPrivateSpacePagerPage = it },
                            onPagerBackAction = { pagerBackAction = it },
                            onLaunch = { isLaunching = true },
                            isSearchBarAtTop = isSearchBarAtTop
                        )
                        }
                    }

                    scene(AllAppsScenes.FolderExpanded) {
                        var lastCategory by remember { mutableStateOf(expandedCategory) }
                        if (expandedCategory != null) lastCategory = expandedCategory
                        val category = lastCategory
                        Box(modifier = Modifier.element(AllAppsElements.FolderRoot).fillMaxSize()) {
                            if (category != null) {
                                ExpandedFolderContent(
                                    category = category,
                                    onDismiss = {
                                        expandedCategory = null
                                        callbacks.onFolderExpandedChanged(false)
                                    },
                                    iconSizePx = state.iconSizePx,
                                    cellHeightPx = state.cellHeightPx,
                                    isSearchBarAtTop = isSearchBarAtTop
                                )
                            }
                        }
                    }

                    scene(AllAppsScenes.Search) {
                        Box(modifier = Modifier.element(AllAppsElements.SearchRoot).fillMaxSize()) {
                                    val onResultClick = remember(searchQuery) {
                                        {
                                            if (searchQuery.isNotEmpty()) {
                                                searchManager.addToHistory(searchQuery)
                                            }
                                        }
                                    }

                                    AnimatedContent(
                                        targetState = isSearchPending,
                                        transitionSpec = {
                                            if (targetState) {
                                                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                            } else {
                                                (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 12 }) togetherWith
                                                    fadeOut(tween(150))
                                            }.using(SizeTransform(clip = false))
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        label = "search_scene_content"
                                    ) { pending ->
                                    if (pending) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(
                                                    if (isSearchBarAtTop) {
                                                        Modifier
                                                    } else {
                                                        Modifier.navigationBarsPadding()
                                                    }
                                                )
                                                .padding(allAppsSearchScenePadding(isSearchBarAtTop)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LoadingIndicator(
                                                modifier = Modifier.size(48.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else UniversalSearchResults(
                                        state = searchState,
                                        onAppClick = { iconInfo ->
                                            onResultClick()
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
                                            onResultClick()
                                            isLaunching = true
                                            callbacks.startActivity(searchManager.getContactIntent(contact))
                                        },
                                        onMessageClick = { message ->
                                            onResultClick()
                                            isLaunching = true
                                            callbacks.startActivity(searchManager.getMessageIntent(message))
                                        },
                                        onFileClick = { file ->
                                            try {
                                                onResultClick()
                                                isLaunching = true
                                                callbacks.startActivity(searchManager.getFileIntent(file))
                                            } catch (e: Exception) {
                                            }
                                        },
                                        onPhotoClick = { photo ->
                                            try {
                                                onResultClick()
                                                isLaunching = true
                                                callbacks.startActivity(searchManager.getPhotoIntent(photo))
                                            } catch (e: Exception) {
                                            }
                                        },
                                        onPrivateSpaceClick = { space ->
                                            if (space.isLocked) {
                                                callbacks.onPrivateSpaceClicked(true)
                                            } else {
                                                onResultClick()
                                                searchQuery = ""
                                                callbacks.onSearchQueryChanged("")
                                                keyboardController?.hide()
                                                showPrivateSpaceScreen = true
                                            }
                                        },
                                        onCalendarClick = { calendar ->
                                            onResultClick()
                                            isLaunching = true
                                            callbacks.startActivity(searchManager.getCalendarIntent(calendar))
                                        },
                                        onSettingClick = { setting ->
                                            onResultClick()
                                            isLaunching = true
                                            callbacks.startActivity(setting.intent)
                                        },
                                        onInAppSearchClick = { search ->
                                            onResultClick()
                                            val intent = if (search.appInfo?.componentName != null) {
                                                Intent(Intent.ACTION_SEARCH).apply {
                                                    setPackage(search.appInfo.componentName!!.packageName)
                                                    putExtra("query", search.query)
                                                    putExtra(SearchManager.QUERY, search.query)
                                                }
                                            } else {
                                                Intent(Intent.ACTION_SEARCH).apply {
                                                    putExtra("query", search.query)
                                                    putExtra(SearchManager.QUERY, search.query)
                                                }
                                            }
                                            isLaunching = true
                                            callbacks.startActivity(intent)
                                        },
                                        onWebActionClick = { action ->
                                            onResultClick()
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
                                        onAppActionClick = { appActions, action ->
                                            onResultClick()
                                            isLaunching = true
                                            val packageName = appActions.appInfo.componentName?.packageName
                                            val shortcutId = action.shortcutId
                                            if (packageName != null && shortcutId != null) {
                                                callbacks.startShortcut(
                                                    packageName,
                                                    shortcutId,
                                                    appActions.appInfo.user
                                                )
                                            }
                                        },
                                        onScrollStateChanged = { up, down ->
                                            controller.canScrollUp = up
                                            controller.canScrollDown = down
                                        },
                                        onRequestContactsPermission = { callbacks.requestContactsPermission() },
                                        onRequestSmsPermission = { callbacks.requestSmsPermission() },
                                        onRequestFilePermission = { callbacks.requestFilePermission() },
                                        onRequestCalendarPermission = { callbacks.requestCalendarPermission() },
                                        onHistoryClick = { keyword ->
                                            searchQuery = keyword
                                            callbacks.onSearchQueryChanged(keyword)
                                        },
                                        onHistoryDeleteClick = { keyword ->
                                            searchManager.removeFromHistory(keyword)
                                        },
                                        suggestedApps = state.predictedApps.ifEmpty { state.apps.take(10) },
                                        activeQuery = searchQuery,
                                        topResultComponent = topSearchResult?.appInfo?.componentName?.flattenToString(),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(allAppsSearchScenePadding(isSearchBarAtTop))
                                    )
                                    }
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
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    shouldAutoFocus = LauncherPrefs.DRAWER_OPEN_KEYBOARD.get(LocalContext.current),
                    focusTrigger = openCounter,
                    onSearchSubmit = { query ->
                        searchManager.addToHistory(query)
                        keyboardController?.hide()
                        topSearchResult?.let { top ->
                            isLaunching = true
                            callbacks.onAppClickedFromFolder(top.appInfo)
                        }
                    },
                    hasTopResult = topSearchResult != null,
                    containerColor = if (isSearchBarAtTop) {
                        surfaceEffectColor()
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    applyBottomInsets = !isSearchBarAtTop,
                    modifier = Modifier
                        .align(if (isSearchBarAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (isSearchBarAtTop) TopSearchBarOuterPadding else 0.dp,
                            bottom = if (isSearchBarAtTop) 0.dp else 8.dp
                        )
                )
            }
    }

    folderPickerTarget?.let { componentName ->
        FolderPickerBottomSheet(
            componentName = componentName,
            categoryManager = categoryManager,
            pinnedAppsManager = pinnedAppsManager,
            onDismiss = { folderPickerTarget = null }
        )
    }

    FolderActionsSheet(
        target = folderActionsTarget,
        onDismiss = { folderActionsTarget = null },
        onRename = { folder ->
            folderActionsTarget = null
            renameFolderTarget = folder
        },
        onDelete = { folder ->
            folderActionsTarget = null
            deleteFolderTarget = folder
        }
    )

    RenameFolderDialog(
        target = renameFolderTarget,
        onDismiss = { renameFolderTarget = null },
        onConfirm = { folder, newName ->
            categoryManager.renameCategory(folder.id, newName)
            renameFolderTarget = null
        }
    )

    deleteFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text(stringResource(R.string.folder_delete_title, target.name)) },
            text = { Text(stringResource(R.string.folder_delete_message, target.apps.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        categoryManager.deleteCategory(target.id)
                        deleteFolderTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.folder_delete_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteFolderTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
}
