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

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.axion.compose.color.AxColorSwatch
import com.android.axion.compose.color.AxCustomColorSwatch
import com.android.axion.compose.color.AxSplitColorSwatch
import com.android.axion.compose.color.ColorPickerDialog
import com.android.axion.compose.preferences.LocalPreferencePosition
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.preferenceShape
import com.android.axion.util.PackageManagerUtils
import com.android.launcher3.Item
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.IconChangeTracker
import com.android.launcher3.icons.ThemedIconSettings
import com.android.launcher3.icons.customicon.IconPackDrawableResolver
import com.android.launcher3.icons.customicon.IconPackEnumerator
import com.android.launcher3.icons.customicon.IconPackInfo
import com.android.launcher3.icons.customicon.IconPackPreferenceStore
import com.android.launcher3.util.painterResource as drawablePainter

@Composable
internal fun IconSettingsScreen() {
    val preview = rememberHomePreviewState()
    val previewKey = rememberIconPreviewKey()
    HomeSettingsPreview(
        rows = preview.rows,
        columns = preview.columns,
        iconPercent = preview.iconPercent,
        labelPercent = preview.labelPercent,
        showLabels = preview.showLabels,
        hotseatIcons = preview.hotseatIcons,
        showSearchBar = preview.showSearchBar,
        extraKey = previewKey,
    )

    val packs = rememberInstalledIconPacks()
    val themedIconsPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICONS_ENABLED)
    var selectedSection by remember { mutableStateOf<IconSettingsSection?>(null) }
    val section = selectedSection ?: if (themedIconsPreference.value) {
        IconSettingsSection.THEMED_ICONS
    } else {
        IconSettingsSection.ICON_PACK
    }
    IconSettingsSectionChips(section, onSectionChange = { selectedSection = it })
    when (section) {
        IconSettingsSection.ICON_PACK -> IconPackSourceGroup(packs)
        IconSettingsSection.THEMED_ICONS -> {
            val themedIconsEnabled = ThemedIconSourceGroup(packs)
            if (themedIconsEnabled) {
                ThemedIconCustomizationGroup()
            }
        }
    }
}

@Composable
private fun rememberIconPreviewKey(): String {
    val context = LocalContext.current
    val iconPackPreference = rememberLauncherPreference(
        item = LauncherPrefsExt.ICON_PACK_PACKAGE,
        read = { IconPackPreferenceStore.getIconPackPackage(context) },
        write = { prefs, value ->
            IconPackPreferenceStore.setIconPackPackage(context, prefs, value)
        },
    )
    val themedIconsPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICONS_ENABLED)
    val themedPackPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICON_PACK)
    val scalePreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICON_SCALE)
    val presetPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICON_COLOR_PRESET)
    val backgroundSourcePreference = rememberThemedIconColorSourcePreference(
        LauncherPrefsExt.THEMED_ICON_BACKGROUND_COLOR_SOURCE,
        ThemedIconSettings::getBackgroundColorSource,
    )
    val backgroundPreference = rememberLauncherPreference(
        LauncherPrefsExt.THEMED_ICON_BACKGROUND_COLOR,
    )
    val foregroundSourcePreference = rememberThemedIconColorSourcePreference(
        LauncherPrefsExt.THEMED_ICON_FOREGROUND_COLOR_SOURCE,
        ThemedIconSettings::getForegroundColorSource,
    )
    val foregroundPreference = rememberLauncherPreference(
        LauncherPrefsExt.THEMED_ICON_FOREGROUND_COLOR,
    )
    return listOf(
        iconPackPreference.value,
        themedIconsPreference.value,
        themedPackPreference.value,
        scalePreference.value,
        presetPreference.value,
        backgroundSourcePreference.value,
        backgroundPreference.value,
        foregroundSourcePreference.value,
        foregroundPreference.value,
    ).joinToString(separator = ":")
}

@Composable
private fun IconSettingsSectionChips(
    section: IconSettingsSection,
    onSectionChange: (IconSettingsSection) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconSettingsSection.values().forEach { option ->
            FilterChip(
                selected = option == section,
                onClick = { onSectionChange(option) },
                label = { Text(stringResource(option.titleRes)) },
            )
        }
    }
}

@Composable
private fun IconPackSourceGroup(packs: List<IconPackInfo>) {
    val context = LocalContext.current
    val iconPackPreference = rememberLauncherPreference(
        item = LauncherPrefsExt.ICON_PACK_PACKAGE,
        read = { IconPackPreferenceStore.getIconPackPackage(context) },
        write = { prefs, value ->
            IconPackPreferenceStore.setIconPackPackage(context, prefs, value)
        },
    )
    val themedIconsPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICONS_ENABLED)
    val systemIcon = rememberSystemIcon()
    val options = remember(context, packs, systemIcon) {
        buildList {
            add(
                IconSourceOption(
                    value = ICON_SOURCE_SYSTEM,
                    label = context.getString(R.string.icon_pack_system_icons),
                    icon = systemIcon,
                )
            )
            packs.forEach { pack ->
                add(IconSourceOption(pack.packageName, pack.label, pack.icon))
            }
        }
    }
    IconSourceCardRow(
        options = options,
        selectedValue = if (themedIconsPreference.value) null else iconPackPreference.value,
        onSelected = {
            iconPackPreference.onChange(it)
            themedIconsPreference.onChange(false)
            ThemeManager.INSTANCE.get(context).isMonoThemeEnabled = false
            IconPackDrawableResolver.clearCache(null)
            IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
        },
    )
}

@Composable
private fun ThemedIconSourceGroup(packs: List<IconPackInfo>): Boolean {
    val context = LocalContext.current
    val themedIconsPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICONS_ENABLED)
    val themedPackPreference = rememberLauncherPreference(LauncherPrefsExt.THEMED_ICON_PACK)
    val iconPackPreference = rememberLauncherPreference(
        item = LauncherPrefsExt.ICON_PACK_PACKAGE,
        read = { IconPackPreferenceStore.getIconPackPackage(context) },
        write = { prefs, value ->
            IconPackPreferenceStore.setIconPackPackage(context, prefs, value)
        },
    )
    val systemIcon = rememberSystemIcon()
    val selectedSource = themedPackPreference.value.ifEmpty { THEMED_SOURCE_DEFAULT }
    val options = remember(context, packs, systemIcon) {
        buildList {
            add(
                IconSourceOption(
                    value = THEMED_SOURCE_DEFAULT,
                    label = context.getString(R.string.themed_icon_source_default),
                    icon = systemIcon,
                )
            )
            packs.forEach { pack ->
                add(IconSourceOption(pack.packageName, pack.label, pack.icon))
            }
        }
    }
    IconSourceCardRow(
        options = options,
        selectedValue = if (themedIconsPreference.value) selectedSource else null,
        onSelected = {
            applyThemedIconSource(
                context = context,
                enabledPreference = themedIconsPreference,
                iconPackPreference = iconPackPreference,
                packPreference = themedPackPreference,
                source = it,
            )
        },
    )
    return themedIconsPreference.value
}

@Composable
private fun ThemedIconCustomizationGroup() {
    val context = LocalContext.current
    val backgroundSourcePreference = rememberThemedIconColorSourcePreference(
        LauncherPrefsExt.THEMED_ICON_BACKGROUND_COLOR_SOURCE,
        ThemedIconSettings::getBackgroundColorSource,
    )
    val backgroundPreference = rememberLauncherPreference(
        LauncherPrefsExt.THEMED_ICON_BACKGROUND_COLOR,
    )
    val foregroundSourcePreference = rememberThemedIconColorSourcePreference(
        LauncherPrefsExt.THEMED_ICON_FOREGROUND_COLOR_SOURCE,
        ThemedIconSettings::getForegroundColorSource,
    )
    val foregroundPreference = rememberLauncherPreference(
        LauncherPrefsExt.THEMED_ICON_FOREGROUND_COLOR,
    )
    val backgroundSource = resolveColorSource(backgroundSourcePreference.value)
    val foregroundSource = resolveColorSource(foregroundSourcePreference.value)
    val backgroundColor = resolveBackgroundColor(
        context,
        backgroundSource,
        backgroundPreference.value,
    )
    val foregroundColor = resolveForegroundColor(
        context,
        foregroundSource,
        foregroundPreference.value,
    )
    var colorTarget by remember { mutableStateOf<IconColorTarget?>(null) }

    colorTarget?.let { target ->
        val initialColor = if (target == IconColorTarget.BACKGROUND) {
            backgroundColor
        } else {
            foregroundColor
        }
        ColorPickerDialog(
            initialColor = Color(initialColor),
            title = stringResource(target.titleRes),
            onDismiss = { colorTarget = null },
            onColorSelected = { color ->
                val argb = color.toArgb()
                if (target == IconColorTarget.BACKGROUND) {
                    backgroundPreference.onChange(argb)
                    backgroundSourcePreference.onChange(ThemedIconSettings.COLOR_PRESET_CUSTOM)
                } else {
                    foregroundPreference.onChange(argb)
                    foregroundSourcePreference.onChange(ThemedIconSettings.COLOR_PRESET_CUSTOM)
                }
                IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
                colorTarget = null
            },
        )
    }

    PreferenceGroup(title = stringResource(R.string.themed_icon_customization_title)) {
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.THEMED_ICON_SCALE,
                titleRes = R.string.themed_icon_size_title,
                min = ThemedIconSettings.MIN_ICON_SCALE,
                max = ThemedIconSettings.MAX_ICON_SCALE,
                defaultValue = ThemedIconSettings.DEFAULT_ICON_SCALE,
                valueLabel = { stringResource(R.string.themed_icon_size_percent, it) },
            )
        }
        item {
            IconColorPalette(
                titleRes = R.string.themed_icon_background_title,
                source = backgroundSource,
                selectedColor = backgroundColor,
                presetColors = rememberThemedIconBackgroundPresets(),
                onPresetSelected = { source ->
                    backgroundSourcePreference.onChange(source)
                    IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
                },
                onColorSelected = { color ->
                    backgroundPreference.onChange(color)
                    backgroundSourcePreference.onChange(ThemedIconSettings.COLOR_PRESET_CUSTOM)
                    IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
                },
                onCustomClick = { colorTarget = IconColorTarget.BACKGROUND },
            )
        }
        item {
            IconColorPalette(
                titleRes = R.string.themed_icon_foreground_title,
                source = foregroundSource,
                selectedColor = foregroundColor,
                presetColors = rememberThemedIconForegroundPresets(),
                onPresetSelected = { source ->
                    foregroundSourcePreference.onChange(source)
                    IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
                },
                onColorSelected = { color ->
                    foregroundPreference.onChange(color)
                    foregroundSourcePreference.onChange(ThemedIconSettings.COLOR_PRESET_CUSTOM)
                    IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
                },
                onCustomClick = { colorTarget = IconColorTarget.FOREGROUND },
            )
        }
    }
}

@Composable
private fun IconSourceCardRow(
    options: List<IconSourceOption>,
    selectedValue: String?,
    onSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(options, key = { it.value }) { option ->
            IconSourceCard(
                option = option,
                selected = option.value == selectedValue,
                onClick = { onSelected(option.value) },
            )
        }
    }
}

@Composable
private fun IconSourceCard(
    option: IconSourceOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .width(88.dp)
            .height(96.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            option.icon?.let { icon ->
                IconSourceImage(icon, size = 40.dp, radius = 12.dp)
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (option.icon != null) 6.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun IconColorPalette(
    @StringRes titleRes: Int,
    source: String,
    selectedColor: Int,
    presetColors: List<ThemedIconColorOption>,
    onPresetSelected: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
    onCustomClick: () -> Unit,
) {
    val shape = preferenceShape(LocalPreferencePosition.current)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val hasPaletteSelection = source == ThemedIconSettings.COLOR_PRESET_CUSTOM &&
                ICON_COLOR_PALETTE.any { it == selectedColor }
        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(presetColors, key = { it.source }) { preset ->
                AxSplitColorSwatch(
                    label = preset.label,
                    lightColor = Color(preset.lightColor),
                    darkColor = Color(preset.darkColor),
                    selected = source == preset.source,
                    onClick = { onPresetSelected(preset.source) },
                )
            }
            items(ICON_COLOR_PALETTE) { color ->
                AxColorSwatch(
                    color = Color(color),
                    selected = source == ThemedIconSettings.COLOR_PRESET_CUSTOM &&
                            color == selectedColor,
                    onClick = { onColorSelected(color) },
                )
            }
            item {
                AxCustomColorSwatch(
                    color = if (source == ThemedIconSettings.COLOR_PRESET_CUSTOM &&
                            !hasPaletteSelection) {
                        Color(selectedColor)
                    } else {
                        null
                    },
                    selected = source == ThemedIconSettings.COLOR_PRESET_CUSTOM &&
                            !hasPaletteSelection,
                    onClick = onCustomClick,
                )
            }
        }
    }
}

@Composable
private fun IconSourceImage(
    icon: Drawable,
    size: Dp = 18.dp,
    radius: Dp = 5.dp,
) {
    Image(
        painter = drawablePainter(icon),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius)),
    )
}

@Composable
private fun rememberInstalledIconPacks(): List<IconPackInfo> {
    val context = LocalContext.current
    val resumeVersion = rememberResumeVersion()
    var packs by remember { mutableStateOf<List<IconPackInfo>>(emptyList()) }
    LaunchedEffect(context, resumeVersion) {
        packs = IconPackEnumerator.listInstalledIconPacks(context)
    }
    return packs
}

@Composable
private fun rememberSystemIcon(): Drawable? {
    val context = LocalContext.current
    return remember(context) {
        PackageManagerUtils.getApplicationIconOrDefault(context, ANDROID_PACKAGE)
    }
}

@Composable
private fun rememberThemedIconBackgroundPresets(): List<ThemedIconColorOption> {
    val context = LocalContext.current
    return remember(context) {
        listOf(
            ThemedIconColorOption(
                ThemedIconSettings.COLOR_PRESET_AXICONS,
                context.getString(R.string.themed_icon_color_preset_axicons),
                ThemedIconSettings.getAxBackgroundColor(context, false),
                ThemedIconSettings.getAxBackgroundColor(context, true),
            ),
            ThemedIconColorOption(
                ThemedIconSettings.COLOR_PRESET_AOSP,
                context.getString(R.string.themed_icon_color_preset_aosp),
                ThemedIconSettings.getAospBackgroundColor(context, false),
                ThemedIconSettings.getAospBackgroundColor(context, true),
            ),
        )
    }
}

@Composable
private fun rememberThemedIconForegroundPresets(): List<ThemedIconColorOption> {
    val context = LocalContext.current
    return remember(context) {
        listOf(
            ThemedIconColorOption(
                ThemedIconSettings.COLOR_PRESET_AXICONS,
                context.getString(R.string.themed_icon_color_preset_axicons),
                ThemedIconSettings.getAxForegroundColor(context, false),
                ThemedIconSettings.getAxForegroundColor(context, true),
            ),
            ThemedIconColorOption(
                ThemedIconSettings.COLOR_PRESET_AOSP,
                context.getString(R.string.themed_icon_color_preset_aosp),
                ThemedIconSettings.getAospForegroundColor(context, false),
                ThemedIconSettings.getAospForegroundColor(context, true),
            ),
        )
    }
}

@Composable
private fun rememberThemedIconColorSourcePreference(
    item: Item,
    read: (Context) -> String,
): PreferenceState<String> {
    val context = LocalContext.current
    return rememberLauncherPreference(
        item = item,
        read = { read(context) },
        write = { prefs, value -> prefs.put(item, value) },
    )
}

private fun applyThemedIconSource(
    context: Context,
    enabledPreference: PreferenceState<Boolean>,
    iconPackPreference: PreferenceState<String>,
    packPreference: PreferenceState<String>,
    source: String,
) {
    val packPackage = if (source == THEMED_SOURCE_DEFAULT) {
        ""
    } else {
        source
    }
    enabledPreference.onChange(true)
    iconPackPreference.onChange(ICON_SOURCE_SYSTEM)
    packPreference.onChange(packPackage)
    ThemeManager.INSTANCE.get(context).isMonoThemeEnabled = true
    IconPackDrawableResolver.clearCache(null)
    IconChangeTracker.INSTANCE.get(context).notifyAllIconsChanged()
}

private fun resolveColorSource(source: String): String = when (source) {
    ThemedIconSettings.COLOR_PRESET_AOSP -> ThemedIconSettings.COLOR_PRESET_AOSP
    ThemedIconSettings.COLOR_PRESET_CUSTOM -> ThemedIconSettings.COLOR_PRESET_CUSTOM
    else -> ThemedIconSettings.COLOR_PRESET_AXICONS
}

private fun resolveBackgroundColor(context: Context, source: String, custom: Int): Int {
    return when (source) {
        ThemedIconSettings.COLOR_PRESET_AOSP -> ThemedIconSettings.getAospBackgroundColor(context)
        ThemedIconSettings.COLOR_PRESET_CUSTOM -> custom.takeIf { it != 0 }
            ?: ThemedIconSettings.getAxBackgroundColor(context)
        else -> ThemedIconSettings.getAxBackgroundColor(context)
    }
}

private fun resolveForegroundColor(context: Context, source: String, custom: Int): Int {
    return when (source) {
        ThemedIconSettings.COLOR_PRESET_AOSP -> ThemedIconSettings.getAospForegroundColor(context)
        ThemedIconSettings.COLOR_PRESET_CUSTOM -> custom.takeIf { it != 0 }
            ?: ThemedIconSettings.getAxForegroundColor(context)
        else -> ThemedIconSettings.getAxForegroundColor(context)
    }
}

private data class IconSourceOption(
    val value: String,
    val label: String,
    val icon: Drawable? = null,
)

private data class ThemedIconColorOption(
    val source: String,
    val label: String,
    val lightColor: Int,
    val darkColor: Int,
)

private enum class IconColorTarget(@StringRes val titleRes: Int) {
    BACKGROUND(R.string.themed_icon_background_title),
    FOREGROUND(R.string.themed_icon_foreground_title),
}

private enum class IconSettingsSection(@StringRes val titleRes: Int) {
    ICON_PACK(R.string.icon_pack_title),
    THEMED_ICONS(R.string.themed_icon_source_section_title),
}

private const val ANDROID_PACKAGE = "android"
private const val ICON_SOURCE_SYSTEM = ""
private const val THEMED_SOURCE_DEFAULT = "__default__"
private val ICON_COLOR_PALETTE = listOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFFFF453A.toInt(),
    0xFFFF9F0A.toInt(),
    0xFFFFD60A.toInt(),
    0xFF34C759.toInt(),
    0xFF0A84FF.toInt(),
    0xFF5856D6.toInt(),
    0xFFBF5AF2.toInt(),
)
