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
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeItem
import com.android.launcher3.allapps.compose.shared.model.AllAppsComposeState
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.util.OnColorHintListener
import com.android.launcher3.util.WallpaperColorHints

val LocalDrawerContentColor = staticCompositionLocalOf { Color.Unspecified }

internal val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
internal val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

internal const val TRANSITION_DIRECTION_EPSILON = 0.001f

internal val TopSearchBarOuterPadding = 8.dp
internal val TopSearchBarHeight = 60.dp
internal val TopSearchBarContentGap = 8.dp

private const val TOP_CONTENT_FADE_PROGRESS_START = 0.133f
private const val CONTENT_FADE_PROGRESS_DURATION = 0.083f
private const val CONTENT_STAGGER = 0.01f
private const val CLOSE_CONTENT_FADE_PROGRESS_START = 0.06f
private const val CLOSE_CONTENT_FADE_PROGRESS_DURATION = 0.08f
private const val CLOSE_CONTENT_STAGGER = 0.006f
private const val MIN_DRAWER_ANIMATION_PROGRESS = 0f
private const val MAX_DRAWER_ANIMATION_PROGRESS = 1f
private const val DRAWER_OPEN_ALPHA_ANIMATION_END_PROGRESS = 0.8333f
private const val DRAWER_OPEN_ALPHA_ANIMATION_DURATION = 0.5f
private const val DRAWER_CLOSE_ALPHA_ANIMATION_START_PROGRESS = 0.2f
private const val DRAWER_CLOSE_ALPHA_ANIMATION_DURATION = 0.3f

internal fun drawerAnimationProgress(progress: Float): Float = MAX_DRAWER_ANIMATION_PROGRESS - progress

internal fun contentStaggerAlpha(progress: Float, rowIndex: Int, isOpening: Boolean): Float {
    val animationProgress = drawerAnimationProgress(progress)
    val startFade = if (isOpening) {
        TOP_CONTENT_FADE_PROGRESS_START - CONTENT_STAGGER * rowIndex
    } else {
        CLOSE_CONTENT_FADE_PROGRESS_START - CLOSE_CONTENT_STAGGER * rowIndex
    }.coerceAtLeast(MIN_DRAWER_ANIMATION_PROGRESS)
    val duration = if (isOpening) {
        CONTENT_FADE_PROGRESS_DURATION
    } else {
        CLOSE_CONTENT_FADE_PROGRESS_DURATION
    }
    val endFade = (startFade + duration)
        .coerceAtLeast(MIN_DRAWER_ANIMATION_PROGRESS)
        .coerceAtMost(MAX_DRAWER_ANIMATION_PROGRESS)
    return MAX_DRAWER_ANIMATION_PROGRESS - progressFraction(
        (animationProgress - startFade) / (endFade - startFade)
    )
}

internal fun drawerContainerAlpha(progress: Float, isOpening: Boolean): Float {
    val animationProgress = drawerAnimationProgress(progress)
    return if (isOpening) {
        progressFraction(
            (DRAWER_OPEN_ALPHA_ANIMATION_END_PROGRESS - animationProgress) /
                DRAWER_OPEN_ALPHA_ANIMATION_DURATION
        )
    } else {
        MAX_DRAWER_ANIMATION_PROGRESS - progressFraction(
            (animationProgress - DRAWER_CLOSE_ALPHA_ANIMATION_START_PROGRESS) /
                DRAWER_CLOSE_ALPHA_ANIMATION_DURATION
        )
    }
}

internal fun allAppsSceneContentPadding(isSearchBarAtTop: Boolean): PaddingValues =
    PaddingValues(
        start = 16.dp,
        end = 16.dp,
        bottom = if (isSearchBarAtTop) 0.dp else 72.dp
    )

internal fun allAppsSceneTopPadding(isSearchBarAtTop: Boolean, isTablet: Boolean) =
    (if (isTablet) 16.dp else 0.dp) +
        if (isSearchBarAtTop) {
            TopSearchBarOuterPadding + TopSearchBarHeight + TopSearchBarContentGap
        } else {
            0.dp
        }

internal fun allAppsSearchScenePadding(isSearchBarAtTop: Boolean): PaddingValues =
    PaddingValues(
        bottom = if (isSearchBarAtTop) 0.dp else 96.dp
    )

private fun progressFraction(value: Float): Float = value.coerceIn(
    MIN_DRAWER_ANIMATION_PROGRESS,
    MAX_DRAWER_ANIMATION_PROGRESS
)

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
