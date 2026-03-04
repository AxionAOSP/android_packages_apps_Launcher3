package com.android.launcher3.allapps.compose.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.app.WallpaperColors
import androidx.compose.ui.graphics.lerp
import com.android.launcher3.allapps.AllAppsComposeController
import com.android.launcher3.util.Themes
import com.android.launcher3.util.OnColorHintListener
import com.android.launcher3.util.WallpaperColorHints
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherFiles
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
import kotlinx.coroutines.launch

val LocalDrawerContentColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
internal fun surfaceEffectColor(): Color {
    val opacity = rememberDrawerOpacity()
    return MaterialTheme.colorScheme.surfaceBright.copy(alpha = opacity / 255f)
}

@Composable
internal fun rememberAdaptiveContentColor(): Color {
    val opacity = rememberDrawerOpacity()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val fraction = opacity / 255f
    if (fraction > 0.5f) return onSurface
    val context = LocalContext.current
    val colorHints = WallpaperColorHints.get(context)
    var supportsDarkText by remember {
        mutableStateOf((colorHints.hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0)
    }
    DisposableEffect(colorHints) {
        val listener = object : OnColorHintListener {
            override fun onColorHintsChanged(colorHints: Int) {
                supportsDarkText = (colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            }
        }
        colorHints.registerOnColorHintsChangedListener(listener)
        onDispose { colorHints.unregisterOnColorsChangedListener(listener) }
    }
    val wallpaperColor = if (supportsDarkText) Color.Black else Color.White
    return lerp(wallpaperColor, onSurface, fraction * 2f)
}

@Composable
internal fun <T> rememberPreference(key: String, read: (Context) -> T): T {
    val context = LocalContext.current
    val state = remember { mutableStateOf(read(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(
            LauncherFiles.SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) state.value = read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state.value
}

@Composable
internal fun rememberDrawerOpacity(): Int =
    rememberPreference("pref_all_apps_bg_opacity") {
        LauncherPrefs.get(it).get(LauncherPrefs.ALL_APPS_BG_OPACITY)
    }

@Composable
internal fun rememberThemedIcons(): Boolean =
    rememberPreference(PreferenceKeys.ALLAPPS_THEMED_ICONS) {
        LauncherPrefs.ALLAPPS_THEMED_ICONS.get(it)
    }

@Composable
internal fun drawerBaseBackgroundColor(): Color {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val opacity = rememberDrawerOpacity()
    return remember(surfaceContainer, opacity) {
        surfaceContainer.copy(alpha = opacity / 255f)
    }
}

private val bigIconSize = 64.dp
private val smallIconSize = 28.dp

private val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

private const val TAB_PERSONAL = 0
private const val TAB_WORK = 1

private sealed interface ContentScreen {
    data object AllApps : ContentScreen
    data object Search : ContentScreen
    data object PrivateSpace : ContentScreen
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    LaunchedEffect(searchQuery, state.apps, state.pinnedApps, state.workApps, state.privateApps, state.isPrivateSpaceLocked, state.hasPrivateApps) {
        val searchableApps = state.apps + state.pinnedApps + state.workApps +
            if (!state.isPrivateSpaceLocked) state.privateApps else emptyList()
        searchManager.search(
            query = searchQuery,
            apps = searchableApps,
            hasPrivateSpace = state.hasPrivateApps,
            isPrivateSpaceLocked = state.isPrivateSpaceLocked,
            privateAppCount = state.privateApps.size
        )
    }

    val topSearchResult by remember {
        derivedStateOf {
            if (searchQuery.isNotEmpty() && searchState.apps.isNotEmpty() && !searchState.isLoading) {
                searchState.apps.first()
            } else null
        }
    }
    
    val currentExpanded by rememberUpdatedState(allAppsExpanded)

    LaunchedEffect(Unit) {
        snapshotFlow { transitionProgressProvider() }.collect { progress ->
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
                callbacks.onTabSelected(TAB_PERSONAL)
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

        val currentScreen = remember(isSearchActive, showPrivateSpaceScreen) {
            when {
                showPrivateSpaceScreen -> ContentScreen.PrivateSpace
                isSearchActive -> ContentScreen.Search
                else -> ContentScreen.AllApps
            }
        }

        val currentExpandedCategory by rememberUpdatedState(expandedCategory)
        val currentIsSearchActive by rememberUpdatedState(isSearchActive)
        val currentIsImeVisible by rememberUpdatedState(isImeVisible)

        val currentShowPrivateSpaceScreen by rememberUpdatedState(showPrivateSpaceScreen)

        var isOnPrivateSpacePagerPage by remember { mutableStateOf(false) }
        var pagerBackAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        LaunchedEffect(isSearchActive, isImeVisible, expandedCategory, showPrivateSpaceScreen, isOnPrivateSpacePagerPage) {
            callbacks.onSearchExpandedChanged(isSearchActive)

            val hasActiveState = isSearchActive || expandedCategory != null || isImeVisible || showPrivateSpaceScreen || isOnPrivateSpacePagerPage

            if (hasActiveState) {
                callbacks.setDismissFolderHandler {
                    when {
                        currentShowPrivateSpaceScreen -> {
                            showPrivateSpaceScreen = false
                        }
                        currentIsImeVisible -> {
                            keyboardController?.hide()
                        }
                        currentExpandedCategory != null -> {
                            if (isSmartLayout) {
                                dismissRequest = true
                            } else {
                                expandedCategory = null
                            }
                        }
                        currentIsSearchActive -> {
                            searchQuery = ""
                            callbacks.onSearchQueryChanged("")
                        }
                        isOnPrivateSpacePagerPage -> {
                            pagerBackAction?.invoke()
                        }
                    }
                }
            } else {
                callbacks.setDismissFolderHandler(null)
            }
        }

        val drawerBaseBg = drawerBaseBackgroundColor()
        val radiusDp = 12.dp
        val sheetShape = RoundedCornerShape(topStart = radiusDp, topEnd = radiusDp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = transitionProgressProvider()
                    alpha = if (progress < 0.7f) 0f
                          else ((progress - 0.7f) / (0.3f)).coerceIn(0f, 1f)
                }
                .clip(sheetShape)
                .background(drawerBaseBg)
                .statusBarsPadding()
        ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        when {
                            targetState == ContentScreen.Search -> {
                                (fadeIn(animationSpec = tween(250, easing = EmphasizedDecelerateEasing)) +
                                 slideInVertically(animationSpec = tween(300, easing = EmphasizedDecelerateEasing)) { height -> height / 8 }) togetherWith
                                    (fadeOut(animationSpec = tween(150, easing = EmphasizedAccelerateEasing)))
                            }
                            initialState == ContentScreen.Search -> {
                                (fadeIn(animationSpec = tween(250, easing = EmphasizedDecelerateEasing)) +
                                 slideInVertically(animationSpec = tween(250, easing = EmphasizedDecelerateEasing)) { height -> -height / 12 }) togetherWith
                                    (fadeOut(animationSpec = tween(150, easing = EmphasizedAccelerateEasing)) +
                                     slideOutVertically(animationSpec = tween(150, easing = EmphasizedAccelerateEasing)) { height -> height / 12 })
                            }
                            else -> {
                               (fadeIn(animationSpec = tween(250, easing = EmphasizedDecelerateEasing)) +
                                scaleIn(animationSpec = tween(250, easing = EmphasizedDecelerateEasing), initialScale = 0.95f)) togetherWith
                                   (fadeOut(animationSpec = tween(150, easing = EmphasizedAccelerateEasing)) +
                                    scaleOut(animationSpec = tween(150, easing = EmphasizedAccelerateEasing), targetScale = 0.95f))
                            }
                        }.using(SizeTransform(clip = true))
                    },
                    modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 32.dp).clipToBounds()
                ) { screen ->
                        if (state.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                        when (screen) {
                            ContentScreen.AllApps -> {
                                val showPrivateSpacePage = state.hasPrivateApps && !state.isPrivateSpaceHidden
                                val pagerPageCount = if (showPrivateSpacePage) 2 else 1
                                val defaultPage = if (showPrivateSpacePage) 1 else 0
                                val pagerState = rememberPagerState(
                                    initialPage = defaultPage,
                                    pageCount = { pagerPageCount }
                                )
                                var pagerReady by remember { mutableStateOf(!showPrivateSpacePage) }

                                LaunchedEffect(showPrivateSpacePage) {
                                    if (showPrivateSpacePage && pagerState.pageCount > 1) {
                                        pagerState.scrollToPage(1)
                                        pagerReady = true
                                    } else if (!showPrivateSpacePage) {
                                        if (pagerState.currentPage != 0) {
                                            pagerState.scrollToPage(0)
                                        }
                                        pagerReady = true
                                    }
                                }

                                LaunchedEffect(allAppsExpanded) {
                                    if (!allAppsExpanded && showPrivateSpacePage && pagerState.currentPage != defaultPage) {
                                        pagerState.scrollToPage(defaultPage)
                                    }
                                }

                                val pagerScope = rememberCoroutineScope()
                                val isOnPrivateSpacePage = pagerReady && showPrivateSpacePage && pagerState.settledPage == 0

                                LaunchedEffect(isOnPrivateSpacePage) {
                                    isOnPrivateSpacePagerPage = isOnPrivateSpacePage
                                    pagerBackAction = if (isOnPrivateSpacePage) {
                                        {
                                            pagerScope.launch {
                                                pagerState.animateScrollToPage(defaultPage)
                                            }
                                        }
                                    } else null
                                }

                                val pagerFlingBehavior = PagerDefaults.flingBehavior(
                                    state = pagerState,
                                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                                    snapPositionalThreshold = 0.6f
                                )

                                val isPagerSwiping = pagerState.isScrollInProgress

                                CompositionLocalProvider(LocalPagerSwiping provides isPagerSwiping) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 1,
                                    flingBehavior = pagerFlingBehavior
                                ) { page ->
                                    val isPrivateSpacePage = showPrivateSpacePage && page == 0
                                    if (isPrivateSpacePage) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            PrivateSpaceFullPage(
                                                state = state,
                                                callbacks = callbacks,
                                                onLaunch = { isLaunching = true },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (state.isPrivateSpaceLocked) {
                                                val scrollProgress = if (showPrivateSpacePage && pagerState.currentPage <= 1) {
                                                    (1f - pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                                                        .let { if (pagerState.currentPage == 0) 1f else it }
                                                } else 0f

                                                PrivateSpaceVeil(
                                                    progress = scrollProgress,
                                                    onUnlockClick = { callbacks.onPrivateSpaceClicked(true) },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    } else {
                                        MainDrawerContent(
                                            state = state,
                                            callbacks = callbacks,
                                            items = items,
                                            sections = sections,
                                            workItems = workItems,
                                            workSections = workSections,
                                            isSmartLayout = isSmartLayout,
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
                                            openCounter = openCounter
                                        )
                                    }
                                }
                                }
                            }
                            ContentScreen.Search -> {
                                    val onResultClick = remember(searchQuery) {
                                        {
                                            if (searchQuery.isNotEmpty()) {
                                                searchManager.addToHistory(searchQuery)
                                            }
                                        }
                                    }

                                    UniversalSearchResults(
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
                                        topResultComponent = topSearchResult?.appInfo?.componentName?.flattenToString(),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                ContentScreen.PrivateSpace -> {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        PrivateSpaceFullPage(
                                            state = state,
                                            callbacks = callbacks,
                                            onLaunch = { isLaunching = true },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (state.isPrivateSpaceLocked) {
                                            PrivateSpaceVeil(
                                                progress = 1f,
                                                onUnlockClick = { callbacks.onPrivateSpaceClicked(true) },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                else -> { }
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
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

@Composable
private fun AllAppsCategoriesView(
    state: AllAppsComposeState,
    categoryManager: AppCategoryManager,
    expandedCategory: AppCategory?,
    onExpandedCategoryChange: (AppCategory?) -> Unit,
    onCustomFolderAction: (AppCategory) -> Unit,
    transitionProgressProvider: () -> Float,
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactions = LocalAllAppsInteractions.current

    val context = LocalContext.current
    var internalExpandedCategory by remember(expandedCategory) { mutableStateOf(expandedCategory) }
    val overrides by categoryManager.overrides.collectAsStateWithLifecycle()
    val customCats by categoryManager.customCategories.collectAsStateWithLifecycle()
    val appCategories = remember(state.apps, overrides, customCats) {
        categorizeApps(state.apps, context, categoryManager)
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

    val categories = remember(appCategories, predictionsCategory, pinnedCategory) {
        buildList {
            predictionsCategory?.let { add(it) }
            pinnedCategory?.let { add(it) }
            addAll(appCategories)
        }
    }

    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }

    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }
    val isScrollingProvider = remember<() -> Boolean> { { gridState.isScrollInProgress } }

    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expandedCategory) {
        if (expandedCategory != null) {
            wasExpanded = true
            interactions.controller?.let {
                it.canScrollUp = true
                it.canScrollDown = false
            }
        } else if (wasExpanded) {
            interactions.controller?.let {
                it.canScrollUp = true
                it.canScrollDown = true
            }
            wasExpanded = false
        }
    }

    LaunchedEffect(canScrollUp, canScrollDown, isScrollInProgress) {
        if (expandedCategory == null) {
            val effectiveCanScrollUp = canScrollUp || isScrollInProgress
            interactions.controller?.let {
                it.canScrollUp = effectiveCanScrollUp
                it.canScrollDown = canScrollDown
            }
        }
    }

    LaunchedEffect(dismissRequest) {
        if (dismissRequest && expandedCategory != null) {
            onExpandedCategoryChange(null)
            onDismissRequestChange(false)
        }
    }

    val currentExpandedCategory by rememberUpdatedState(expandedCategory)

    AnimatedContent(
        targetState = expandedCategory?.id,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(animationSpec = tween(300, easing = EmphasizedDecelerateEasing)) +
                    scaleIn(animationSpec = tween(300, easing = EmphasizedDecelerateEasing), initialScale = 0.92f))
                    .togetherWith(fadeOut(animationSpec = snap()))
            } else {
                (fadeIn(animationSpec = tween(300, easing = EmphasizedDecelerateEasing)) +
                    slideInVertically(animationSpec = tween(350, easing = EmphasizedDecelerateEasing)) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = snap()))
            }
        },
        modifier = modifier.fillMaxSize(),
        label = "category_content"
    ) { categoryId ->
        val category = currentExpandedCategory
        if (categoryId != null && category != null) {
            ExpandedFolderContent(
                category = category,
                onDismiss = { onExpandedCategoryChange(null) },
                iconSizePx = state.iconSizePx,
                cellHeightPx = state.cellHeightPx
            )
        } else {
            
            val topCards = categories.filter { it.id == -4 || it.id == -2 }
            val gridCards = categories.filter { it.id != -4 && it.id != -2 }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                
                topCards.forEach { cat ->
                    item(
                        key = cat.id,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        SmartDrawerRowCard(
                            category = cat,
                            iconSizePx = state.iconSizePx,
                            maxPerRow = state.numColumns,
                            wrapRows = cat.id == -2,
                            onClick = {
                                internalExpandedCategory = cat
                                onExpandedCategoryChange(cat)
                            },
                            isScrollingProvider = isScrollingProvider
                        )
                    }
                }

                items(
                    count = gridCards.size,
                    key = { gridCards[it].id }
                ) { index ->
                    val cat = gridCards[index]
                    CategoryFolder(
                        category = cat,
                        onClick = {
                            internalExpandedCategory = cat
                            onExpandedCategoryChange(cat)
                        },
                        onLongClick = if (cat.isCustom) {{ onCustomFolderAction(cat) }} else null,
                        isScrollingProvider = isScrollingProvider
                    )
                }
            }
        }
    }

}

@Composable
private fun ExpandedFolderContent(
    category: AppCategory,
    onDismiss: () -> Unit,
    iconSizePx: Int,
    cellHeightPx: Int
) {
    val interactions = LocalAllAppsInteractions.current
    val gridState = rememberLazyGridState()
    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
    val isScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }
    val isScrollingProvider = remember<() -> Boolean> { { gridState.isScrollInProgress } }

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderPickerBottomSheet(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartDrawerRowCard(
    category: AppCategory,
    iconSizePx: Int,
    maxPerRow: Int,
    wrapRows: Boolean = false,
    onClick: () -> Unit,
    isScrollingProvider: () -> Boolean = { false }
) {
    val interactions = LocalAllAppsInteractions.current
    val displayApps = if (wrapRows) category.apps else category.apps.take(maxPerRow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(surfaceEffectColor())
            .combinedClickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(16.dp)
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalDrawerContentColor.current
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (wrapRows) {
            val rows = displayApps.chunked(maxPerRow)
            Column {
                rows.forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowApps.forEach { app ->
                            AllAppsComposeAppIcon(
                                appInfo = app,
                                showLabel = false,
                                iconSizePx = iconSizePx,
                                onClick = interactions.onAppClick,
                                onLongClick = interactions.onAppLongClick,
                                onDragStart = interactions.onAppDragStart,
                                onDragMove = interactions.onAppDragMove,
                                onDragEnd = interactions.onAppDragEnd,
                                isScrollingProvider = isScrollingProvider
                            )
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                displayApps.forEach { app ->
                    AllAppsComposeAppIcon(
                        appInfo = app,
                        showLabel = false,
                        iconSizePx = iconSizePx,
                        onClick = interactions.onAppClick,
                        onLongClick = interactions.onAppLongClick,
                        onDragStart = interactions.onAppDragStart,
                        onDragMove = interactions.onAppDragMove,
                        onDragEnd = interactions.onAppDragEnd,
                        isScrollingProvider = isScrollingProvider
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFolder(
    category: AppCategory,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isLocked: Boolean = false,
    isWorkProfile: Boolean = false,
    isPrivateCategory: Boolean = false,
    isScrollingProvider: () -> Boolean = { false }
) {
    val interactions = LocalAllAppsInteractions.current
    val hasMoreThanFour = category.apps.size > 4
    val density = LocalDensity.current
    val iconSizePx = with(density) { bigIconSize.roundToPx() }

    @OptIn(ExperimentalFoundationApi::class)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(surfaceEffectColor())
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(16.dp)
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = LocalDrawerContentColor.current
        )
        Spacer(modifier = Modifier.height(12.dp))

        val folderContentHeight = bigIconSize * 2 + 12.dp
        Box(
            modifier = Modifier.fillMaxWidth().height(folderContentHeight),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLocked -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.ps_container_lock_button_content_description),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                isWorkProfile -> {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = stringResource(R.string.all_apps_work_tab),
                        tint = LocalDrawerContentColor.current,
                        modifier = Modifier.size(64.dp)
                    )
                }
                isPrivateCategory -> {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.private_space_label),
                        tint = LocalDrawerContentColor.current,
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
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                            category.apps.getOrNull(1)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            category.apps.getOrNull(2)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        category.apps.getOrNull(3)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                        category.apps.getOrNull(4)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        category.apps.getOrNull(5)?.let { PreviewBaseAppIcon(it, smallIconSize) }
                                        category.apps.getOrNull(6)?.let { PreviewBaseAppIcon(it, smallIconSize) }
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
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                )
                            }
                            previewApps.getOrNull(1)?.let { app ->
                                AllAppsComposeAppIcon(
                                    appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                    onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                    onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                    onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
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
                                        appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                        onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                        onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                        onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                    )
                                }
                                previewApps.getOrNull(3)?.let { app ->
                                    AllAppsComposeAppIcon(
                                        appInfo = app, showLabel = false, iconSizePx = iconSizePx,
                                        onClick = interactions.onAppClick, onLongClick = interactions.onAppLongClick,
                                        onDragStart = interactions.onAppDragStart, onDragMove = interactions.onAppDragMove,
                                        onDragEnd = interactions.onAppDragEnd, isScrollingProvider = isScrollingProvider
                                    )
                                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionsSheet(
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
private fun RenameFolderDialog(
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

private fun buildComposeItems(
    state: AllAppsComposeState,
    searchQuery: String,
    customFolders: List<AppCategory> = emptyList()
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

    val folderAppComponents = mutableSetOf<String>()
    customFolders.forEach { folder ->
        folder.apps.forEach { app ->
            app.componentName?.flattenToString()?.let { folderAppComponents.add(it) }
        }
    }

    if (state.predictedApps.isNotEmpty()) {
        items.add(AllAppsComposeItem.PredictionsHeader)
        state.predictedApps.take(state.numColumns)
            .forEach { items.add(AllAppsComposeItem.AppItem(it, section = "prediction")) }
    }

    if (state.pinnedApps.isNotEmpty()) {
        val filteredPinned = state.pinnedApps.filter { app ->
            val comp = app.componentName?.flattenToString() ?: ""
            comp !in folderAppComponents
        }
        if (filteredPinned.isNotEmpty()) {
            sections.add("\uD83D\uDCCC" to items.size)
            items.add(AllAppsComposeItem.PinnedAppsHeader)
            filteredPinned.forEach { items.add(AllAppsComposeItem.AppItem(it, section = "pinned")) }
        }
    }

    if (state.apps.isNotEmpty()) {
        if (state.predictedApps.isNotEmpty() || state.pinnedApps.isNotEmpty() || customFolders.isNotEmpty()) {
            items.add(AllAppsComposeItem.AllAppsHeader)
        }

        customFolders.forEach { folder ->
            items.add(AllAppsComposeItem.FolderItem(folder))
        }

        var lastSection: String? = null
        state.apps.forEach { app ->
            
            val component = app.componentName?.flattenToString() ?: ""
            if (component in folderAppComponents) return@forEach

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonalWorkTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceBright),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            TAB_PERSONAL to R.string.all_apps_personal_tab,
            TAB_WORK to R.string.all_apps_work_tab
        ).forEach { (tab, labelRes) ->
            val selected = selectedTab == tab
            val bgColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceBright,
                animationSpec = motionScheme.fastEffectsSpec(),
                label = "tabBg_$tab"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                animationSpec = motionScheme.fastEffectsSpec(),
                label = "tabText_$tab"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(bgColor)
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

@Composable
private fun WorkTabContent(
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
private fun WorkEduCard(modifier: Modifier = Modifier) {
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
private fun PauseWorkFab(
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
private fun WorkPausedContent(
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

private fun buildWorkComposeItems(
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

@Composable
private fun MainDrawerContent(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    items: List<AllAppsComposeItem>,
    sections: List<Pair<String, Int>>,
    workItems: List<AllAppsComposeItem>,
    workSections: List<Pair<String, Int>>,
    isSmartLayout: Boolean,
    categoryManager: AppCategoryManager,
    expandedCategory: AppCategory?,
    onExpandedCategoryChange: (AppCategory?) -> Unit,
    onCustomFolderAction: (AppCategory) -> Unit,
    selectedProfileTab: Int,
    onProfileTabSelected: (Int) -> Unit,
    transitionProgressProvider: () -> Float,
    dismissRequest: Boolean,
    onDismissRequestChange: (Boolean) -> Unit,
    openCounter: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.hasWorkApps) {
            PersonalWorkTabs(
                selectedTab = selectedProfileTab,
                onTabSelected = onProfileTabSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        AnimatedContent(
            targetState = selectedProfileTab,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(tween(250, easing = EmphasizedDecelerateEasing)) + slideInHorizontally(tween(300, easing = EmphasizedDecelerateEasing)) { it / 6 * direction })
                    .togetherWith(fadeOut(tween(150, easing = EmphasizedAccelerateEasing)) + slideOutHorizontally(tween(200, easing = EmphasizedAccelerateEasing)) { -it / 6 * direction })
            },
            modifier = Modifier.weight(1f),
            label = "main_drawer_tab_content"
        ) { tab ->
            val isActiveTab = tab == selectedProfileTab

            val tabContent = @Composable {
                when (tab) {
                    TAB_WORK -> {
                        WorkTabContent(
                            state = state,
                            workItems = workItems,
                            workSections = workSections,
                            onPauseWork = { callbacks.onWorkProfileToggle(false) },
                            onResumeWork = { callbacks.onWorkProfileToggle(true) },
                            transitionProgressProvider = transitionProgressProvider,
                            openCounter = openCounter
                        )
                    }
                    else -> {
                        if (isSmartLayout) {
                            key("smart_layout") {
                                AllAppsCategoriesView(
                                    state = state,
                                    categoryManager = categoryManager,
                                    expandedCategory = expandedCategory,
                                    onExpandedCategoryChange = onExpandedCategoryChange,
                                    onCustomFolderAction = onCustomFolderAction,
                                    transitionProgressProvider = transitionProgressProvider,
                                    dismissRequest = dismissRequest,
                                    onDismissRequestChange = onDismissRequestChange,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            val currentExpanded by rememberUpdatedState(expandedCategory)
                            AnimatedContent(
                                targetState = expandedCategory?.id,
                                transitionSpec = {
                                    if (targetState != null) {
                                        (fadeIn(animationSpec = tween(300, easing = EmphasizedDecelerateEasing)) +
                                            scaleIn(animationSpec = tween(300, easing = EmphasizedDecelerateEasing), initialScale = 0.92f))
                                            .togetherWith(fadeOut(animationSpec = snap()))
                                    } else {
                                        (fadeIn(animationSpec = tween(300, easing = EmphasizedDecelerateEasing)) +
                                            slideInVertically(animationSpec = tween(350, easing = EmphasizedDecelerateEasing)) { it / 4 })
                                            .togetherWith(fadeOut(animationSpec = snap()))
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                label = "default_folder_expand"
                            ) { categoryId ->
                                val category = currentExpanded
                                if (categoryId != null && category != null) {
                                    ExpandedFolderContent(
                                        category = category,
                                        onDismiss = { onExpandedCategoryChange(null) },
                                        iconSizePx = state.iconSizePx,
                                        cellHeightPx = state.cellHeightPx
                                    )
                                } else {
                                    AllAppsComposeGrid(
                                        items = items,
                                        sections = sections,
                                        numColumns = state.numColumns,
                                        iconSizePx = state.iconSizePx,
                                        cellWidthPx = state.cellWidthPx,
                                        cellHeightPx = state.cellHeightPx,
                                        showLabels = state.showLabels,
                                        onFolderClick = { folder ->
                                            onExpandedCategoryChange(folder)
                                        },
                                        onFolderLongClick = { folder ->
                                            if (folder.isCustom) {
                                                onCustomFolderAction(folder)
                                            }
                                        },
                                        transitionProgressProvider = transitionProgressProvider,
                                        keyPrefix = "personal",
                                        recompositionKey = openCounter,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 0.dp,
                                            bottom = 72.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isActiveTab) {
                tabContent()
            } else {
                val baseInteractions = LocalAllAppsInteractions.current
                val mutedInteractions = remember(baseInteractions) {
                    baseInteractions.withoutController()
                }
                CompositionLocalProvider(LocalAllAppsInteractions provides mutedInteractions) {
                    tabContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrivateSpaceVeil(
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
private fun PrivateSpaceFullPage(
    state: AllAppsComposeState,
    callbacks: AllAppsComposeCallbacks,
    onLaunch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactions = LocalAllAppsInteractions.current
    val gridState = rememberLazyGridState()

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
                    contentPadding = PaddingValues(bottom = 72.dp),
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeightDp)
                                .clickable {
                                    onLaunch()
                                    callbacks.onPrivateSpaceInstallAppClicked()
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(4.dp))
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
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
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

