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

package com.android.launcher3.allapps.compose.ui

import android.app.WallpaperColors
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.axion.blur.AxBlurSettings
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsConfiguration
import com.android.launcher3.allapps.allAppsBottomSheetBackgroundColor
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.util.OnColorHintListener
import com.android.launcher3.util.Themes
import com.android.launcher3.util.WallpaperColorHints

val LocalDrawerContentColor = staticCompositionLocalOf { Color.Unspecified }
internal val LocalAllAppsLegacyLayout = staticCompositionLocalOf { false }
internal val LocalAllAppsConfiguration = staticCompositionLocalOf { AllAppsConfiguration() }
internal val LocalLauncherBlurEnabled = staticCompositionLocalOf { false }
internal val LocalDrawerOpacity = staticCompositionLocalOf { MAX_DRAWER_OPACITY }

internal val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
internal val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

internal val TopSearchBarOuterPadding = 8.dp
internal val TopSearchBarHeight = 60.dp
internal val TopSearchBarContentGap = 8.dp
internal val LegacySearchBarHeight = 52.dp
internal val LegacySearchBarTopPadding = 48.dp
internal val LegacySearchBarContentGap = 24.dp
internal val LegacySearchBarHorizontalPadding = 16.dp
internal val BottomSearchBarBottomPadding = 8.dp
internal val LegacyDrawerCornerRadius = 32.dp
internal val LegacyDrawerHandleTopPadding = 16.dp
internal val LegacyDrawerHandleWidth = 32.dp
internal val LegacyDrawerHandleHeight = 4.dp
internal val DrawerHorizontalPadding = 16.dp
internal val LegacyDrawerHorizontalPadding = 16.dp

private val TopSearchBarScrollableBottomPadding = 48.dp

private const val MIN_DRAWER_ANIMATION_PROGRESS = 0f
private const val MAX_DRAWER_ANIMATION_PROGRESS = 1f
private const val DRAWER_CONTENT_FADE_START_PROGRESS = 0f
private const val DRAWER_CONTENT_FADE_END_PROGRESS = 1f
private const val DRAWER_COLLAPSE_BACKGROUND_FADE_START_PROGRESS = 0.42f
private const val DRAWER_COLLAPSE_BACKGROUND_FADE_END_PROGRESS = 1f
private const val DRAWER_COLLAPSE_CONTENT_FADE_START_PROGRESS = 0.48f
private const val DRAWER_COLLAPSE_CONTENT_FADE_END_PROGRESS = 0.98f
private const val SURFACE_BRIGHT_CONTAINER_MIN_ALPHA = 0.56f
private const val SURFACE_BRIGHT_CONTAINER_MAX_ALPHA = 0.84f
private const val SURFACE_BRIGHT_COLOR_MIN_ALPHA = 0.25f
private const val MAX_DRAWER_OPACITY = 255

internal fun drawerScrimAlpha(progress: Float, isCollapsing: Boolean = false): Float =
    if (isCollapsing) {
        progressRangeFraction(
            progress,
            DRAWER_COLLAPSE_BACKGROUND_FADE_START_PROGRESS,
            DRAWER_COLLAPSE_BACKGROUND_FADE_END_PROGRESS
        )
    } else {
        progressFraction(progress)
    }

internal fun drawerContentAlpha(progress: Float, isCollapsing: Boolean = false): Float {
    val start = if (isCollapsing) {
        DRAWER_COLLAPSE_CONTENT_FADE_START_PROGRESS
    } else {
        DRAWER_CONTENT_FADE_START_PROGRESS
    }
    val end = if (isCollapsing) {
        DRAWER_COLLAPSE_CONTENT_FADE_END_PROGRESS
    } else {
        DRAWER_CONTENT_FADE_END_PROGRESS
    }
    return progressRangeFraction(progress, start, end)
}

internal fun allAppsDrawerHorizontalPadding(legacyLayout: Boolean): Dp =
    if (legacyLayout) LegacyDrawerHorizontalPadding else DrawerHorizontalPadding

internal fun allAppsScrollableContentBottomPadding(
    isSearchBarAtTop: Boolean,
    extended: Boolean = false
): Dp =
    if (isSearchBarAtTop) TopSearchBarScrollableBottomPadding else if (extended) 96.dp else 72.dp

internal fun allAppsSceneContentPadding(
    isSearchBarAtTop: Boolean,
    legacyLayout: Boolean
): PaddingValues =
    PaddingValues(
        start = allAppsDrawerHorizontalPadding(legacyLayout),
        end = allAppsDrawerHorizontalPadding(legacyLayout),
        bottom = allAppsScrollableContentBottomPadding(isSearchBarAtTop)
    )

internal fun allAppsSceneTopPadding(
    isSearchBarAtTop: Boolean,
    isTablet: Boolean,
    legacyLayout: Boolean
) =
    (if (isTablet) 16.dp else 0.dp) + (
        if (isSearchBarAtTop) {
            if (legacyLayout) {
                LegacySearchBarTopPadding + LegacySearchBarHeight + LegacySearchBarContentGap
            } else {
                TopSearchBarOuterPadding + TopSearchBarHeight + TopSearchBarContentGap
            }
        } else if (legacyLayout) {
            LegacySearchBarTopPadding
        } else {
            0.dp
        }
    )

internal fun allAppsSearchScenePadding(isSearchBarAtTop: Boolean): PaddingValues =
    PaddingValues(
        bottom = allAppsScrollableContentBottomPadding(isSearchBarAtTop, extended = true)
    )

private fun progressFraction(value: Float): Float = value.coerceIn(
    MIN_DRAWER_ANIMATION_PROGRESS,
    MAX_DRAWER_ANIMATION_PROGRESS
)

private fun progressRangeFraction(value: Float, start: Float, end: Float): Float =
    progressFraction((value - start) / (end - start))

@Composable
internal fun allAppsThemeColor(attr: Int): Color {
    val colors = LocalAllAppsConfiguration.current.colors
    return Color(
        when (attr) {
            R.attr.allAppsScrimColor -> colors.panel
            R.attr.allAppsSurfaceLow -> colors.surfaceLow
            R.attr.allappsHeaderProtectionColor -> colors.headerProtection
            R.attr.bottomSheetDragHandleColor -> colors.dragHandle
            R.attr.allAppsSearchTextColor -> colors.searchText
            else -> Themes.getAttrColor(LocalContext.current, attr)
        }
    )
}

@Composable
private fun aospAllAppsPanelBaseColor(): Color {
    val context = LocalContext.current
    val alpha = rememberDrawerOpacity()
    val blurEnabled = LocalLauncherBlurEnabled.current
    return remember(context, alpha, blurEnabled) {
        Color(allAppsBottomSheetBackgroundColor(context, alpha, blurEnabled))
    }
}

@Composable
private fun allAppsSurfaceEffectBaseColor(): Color =
    if (LocalAllAppsLegacyLayout.current) {
        allAppsThemeColor(R.attr.allAppsSurfaceLow)
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

@Composable
internal fun surfaceEffectColor(): Color =
    allAppsSurfaceBrightContainerColor()

@Composable
internal fun allAppsSurfaceBrightContainerColor(): Color =
    protectedDrawerSurfaceColor(
        allAppsSurfaceEffectBaseColor(),
        SURFACE_BRIGHT_CONTAINER_MIN_ALPHA,
        SURFACE_BRIGHT_CONTAINER_MAX_ALPHA
    )

@Composable
internal fun allAppsSurfaceBrightColor(): Color {
    val drawerOpacity = rememberDrawerOpacity()
    val color = MaterialTheme.colorScheme.surfaceBright
    if (!LocalLauncherBlurEnabled.current || drawerOpacity == MAX_DRAWER_OPACITY) return color
    return protectedDrawerSurfaceColor(color, drawerOpacity, SURFACE_BRIGHT_COLOR_MIN_ALPHA)
}

@Composable
private fun protectedDrawerSurfaceColor(
    baseColor: Color,
    minAlpha: Float,
    maxAlpha: Float = 1f
): Color {
    return protectedDrawerSurfaceColor(baseColor, rememberDrawerOpacity(), minAlpha, maxAlpha)
}

private fun protectedDrawerSurfaceColor(
    baseColor: Color,
    drawerOpacity: Int,
    minAlpha: Float,
    maxAlpha: Float = 1f
): Color {
    val drawerAlpha = (drawerOpacity.coerceIn(0, MAX_DRAWER_OPACITY) /
        MAX_DRAWER_OPACITY.toFloat()).coerceIn(0f, 1f)
    val surfaceAlpha = minAlpha + (maxAlpha - minAlpha) * drawerAlpha
    return baseColor.copy(alpha = surfaceAlpha.coerceIn(0f, 1f))
}

@Composable
internal fun rememberAdaptiveContentColor(): Color {
    val opacity = rememberDrawerOpacity()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val fraction = opacity / MAX_DRAWER_OPACITY.toFloat()
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
internal fun rememberLauncherBlurEnabled(): Boolean {
    val context = LocalContext.current
    val appContext = context.applicationContext ?: context
    val settings = remember(appContext) { AxBlurSettings.launcher(appContext) }
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    DisposableEffect(settings) {
        val callback = Runnable { enabled = settings.enabled }
        settings.start(callback)
        onDispose { settings.stop() }
    }
    return enabled
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
internal fun rememberDrawerOpacityState(): Int =
    rememberPreference(LauncherPrefs.ALL_APPS_BG_OPACITY.sharedPrefKey) {
        LauncherPrefs.get(it).get(LauncherPrefs.ALL_APPS_BG_OPACITY)
    }

@Composable
internal fun rememberDrawerOpacity(): Int = LocalDrawerOpacity.current

@Composable
private fun rememberDrawerSurfaceAlpha(alphaMultiplier: Float = 1f): Float =
    (rememberDrawerOpacity().coerceIn(0, MAX_DRAWER_OPACITY) / MAX_DRAWER_OPACITY.toFloat() *
        alphaMultiplier).coerceIn(0f, 1f)

@Composable
internal fun drawerBaseBackgroundColor(
    alphaMultiplier: Float = 1f,
    legacyLayout: Boolean = false
): Color {
    if (legacyLayout) {
        val color = aospAllAppsPanelBaseColor()
        return color.copy(alpha = (color.alpha * alphaMultiplier).coerceIn(0f, 1f))
    }
    return MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = rememberDrawerSurfaceAlpha(alphaMultiplier)
    )
}

@Composable
internal fun legacyAllAppsHeaderProtectionColor(): Color =
    allAppsThemeColor(R.attr.allappsHeaderProtectionColor)

@Composable
internal fun legacyAllAppsDragHandleColor(): Color =
    allAppsThemeColor(R.attr.bottomSheetDragHandleColor)

internal fun buildComposeItems(
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
