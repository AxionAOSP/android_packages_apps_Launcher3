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

package com.android.launcher3.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.ThemeEngine
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.compose.color.ColorPickerDialog
import com.android.axion.compose.preferences.rememberSecureSettingBooleanState
import com.android.axion.compose.preferences.rememberSecureSettingIntState
import com.android.axion.compose.preferences.rememberSecureSettingStringState
import com.android.axion.compose.sheet.BottomSheetDialog
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.shapes.IconShapeModel
import com.android.launcher3.shapes.ShapesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

private const val SETTING_THEMED_ICONS = "themed_icons"
private const val SETTING_THEMED_ICON_PACK = "themed_icon_pack"
private const val SETTINGS_THEME_ENGINE_DATA = "theme_engine_data"
private const val CATEGORY_ICON_PACK = "icon_pack"
private const val ACTION_THEMED_ICON = "app.lawnchair.icons.THEMED_ICON"

object IconStyleSheet {

    @JvmStatic
    fun show(launcher: Launcher) {
        val composeView = ComposeView(launcher).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        launcher.dragLayer.addView(composeView)

        composeView.setContent {
            IconStyleTheme {
                BottomSheetDialog(
                    onDismiss = { launcher.dragLayer.removeView(composeView) },
                    heightFraction = 0.5f,
                ) {
                    IconStyleContent()
                }
            }
        }
    }
}

private data class PackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
)

private data class PackLoadResult(
    val iconPacks: List<PackInfo>,
    val activeIconPack: String?,
    val themedIconPacks: List<PackInfo>,
    val activeThemedIconPack: String?,
)

private data class ThemedIconColorDefault(
    val label: String,
    val preset: String,
    val backgroundLight: Int,
    val backgroundDark: Int,
    val foregroundLight: Int,
    val foregroundDark: Int,
)

private enum class ColorTarget {
    Background,
    Foreground,
}

private val ClockFacePaletteColors = listOf(
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

private fun resolveColorPreset(preset: String) = when (preset) {
    ThemedIconSettings.COLOR_PRESET_AOSP -> ThemedIconSettings.COLOR_PRESET_AOSP
    ThemedIconSettings.COLOR_PRESET_CUSTOM -> ThemedIconSettings.COLOR_PRESET_CUSTOM
    else -> ThemedIconSettings.COLOR_PRESET_AXICONS
}

@Composable
private fun IconStyleContent() {
    val context = LocalContext.current
    val resolver = context.contentResolver

    var iconPacks by remember { mutableStateOf<List<PackInfo>>(emptyList()) }
    var activeIconPack by remember { mutableStateOf<String?>(null) }
    var themedIconPacks by remember { mutableStateOf<List<PackInfo>>(emptyList()) }
    var activeThemedIconPack by remember { mutableStateOf<String?>(null) }

    val (themedEnabled, setThemedEnabled) = rememberSecureSettingBooleanState(
        key = SETTING_THEMED_ICONS,
        defaultValue = false,
    )
    val (scaleSetting, setScale) = rememberSecureSettingIntState(
        key = ThemedIconSettings.KEY_ICON_SCALE,
        defaultValue = ThemedIconSettings.DEFAULT_ICON_SCALE,
    )
    val (colorPreset, setColorPreset) = rememberSecureSettingStringState(
        key = ThemedIconSettings.KEY_COLOR_PRESET,
        defaultValue = ThemedIconSettings.getColorPreset(context),
    )
    val defaultBackground = remember { ThemedIconSettings.getAxBackgroundColor(context) }
    val defaultForeground = remember { ThemedIconSettings.getAxForegroundColor(context) }
    val (customBackgroundColor, setBackgroundColor) = rememberSecureSettingIntState(
        key = ThemedIconSettings.KEY_BACKGROUND_COLOR,
        defaultValue = defaultBackground,
    )
    val (customForegroundColor, setForegroundColor) = rememberSecureSettingIntState(
        key = ThemedIconSettings.KEY_FOREGROUND_COLOR,
        defaultValue = defaultForeground,
    )
    val resolvedColorPreset = resolveColorPreset(colorPreset)
    val backgroundColor = when (resolvedColorPreset) {
        ThemedIconSettings.COLOR_PRESET_AOSP -> ThemedIconSettings.getAospBackgroundColor(context)
        ThemedIconSettings.COLOR_PRESET_CUSTOM -> customBackgroundColor
        else -> ThemedIconSettings.getAxBackgroundColor(context)
    }
    val foregroundColor = when (resolvedColorPreset) {
        ThemedIconSettings.COLOR_PRESET_AOSP -> ThemedIconSettings.getAospForegroundColor(context)
        ThemedIconSettings.COLOR_PRESET_CUSTOM -> customForegroundColor
        else -> ThemedIconSettings.getAxForegroundColor(context)
    }

    val shapes = remember { ShapesProvider.iconShapes }
    var selectedShape by remember {
        mutableStateOf(LauncherPrefs.get(context).get(ThemeManager.PREF_ICON_SHAPE))
    }
    val iconScale = scaleSetting.coerceIn(
        ThemedIconSettings.MIN_ICON_SCALE,
        ThemedIconSettings.MAX_ICON_SCALE,
    )

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { loadPacks(context) }
        iconPacks = result.iconPacks
        activeIconPack = result.activeIconPack
        themedIconPacks = result.themedIconPacks
        activeThemedIconPack = result.activeThemedIconPack
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.icon_pack_picker_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            textAlign = TextAlign.Center,
        )

        if (iconPacks.size > 1) {
            SectionLabel(stringResource(R.string.icon_pack_title))
            Spacer(modifier = Modifier.height(10.dp))
            PackCarousel(
                packs = iconPacks,
                activePack = activeIconPack,
                onSelect = { pkg ->
                    applyIconPack(context, pkg ?: "")
                    activeIconPack = pkg
                },
            )
            SectionGap()
        }

        SectionLabel(stringResource(R.string.themed_icons_card_title))
        Spacer(modifier = Modifier.height(10.dp))
        ThemedIconsSection(
            enabled = themedEnabled,
            scale = iconScale,
            colorPreset = resolvedColorPreset,
            backgroundColor = backgroundColor,
            foregroundColor = foregroundColor,
            onEnabledChange = setThemedEnabled,
            onScaleChange = { setScale(it) },
            onColorPresetChange = setColorPreset,
            onBackgroundChange = { color ->
                if (resolvedColorPreset != ThemedIconSettings.COLOR_PRESET_CUSTOM) {
                    setForegroundColor(foregroundColor)
                }
                setBackgroundColor(color)
                setColorPreset(ThemedIconSettings.COLOR_PRESET_CUSTOM)
            },
            onForegroundChange = { color ->
                if (resolvedColorPreset != ThemedIconSettings.COLOR_PRESET_CUSTOM) {
                    setBackgroundColor(backgroundColor)
                }
                setForegroundColor(color)
                setColorPreset(ThemedIconSettings.COLOR_PRESET_CUSTOM)
            },
        )

        if (themedIconPacks.size > 1) {
            SectionGap()
            SectionLabel(stringResource(R.string.themed_icon_pack_title))
            Spacer(modifier = Modifier.height(10.dp))
            PackCarousel(
                packs = themedIconPacks,
                activePack = activeThemedIconPack,
                onSelect = { pkg ->
                    Settings.Secure.putString(resolver, SETTING_THEMED_ICON_PACK, pkg)
                    activeThemedIconPack = pkg
                },
            )
        }

        if (shapes.size > 1) {
            SectionGap()
            SectionLabel(stringResource(R.string.icon_shape_title))
            Spacer(modifier = Modifier.height(10.dp))
            ShapeCarousel(
                shapes = shapes,
                selectedKey = selectedShape,
                onSelect = { key ->
                    selectedShape = key
                    LauncherPrefs.get(context).put(ThemeManager.PREF_ICON_SHAPE, key)
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThemedIconsSection(
    enabled: Boolean,
    scale: Int,
    colorPreset: String,
    backgroundColor: Int,
    foregroundColor: Int,
    onEnabledChange: (Boolean) -> Unit,
    onScaleChange: (Int) -> Unit,
    onColorPresetChange: (String) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    onForegroundChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scaleRange = remember {
        val minScale = ThemedIconSettings.MIN_ICON_SCALE.toFloat()
        val maxScale = ThemedIconSettings.MAX_ICON_SCALE.toFloat()
        minScale..maxScale
    }
    val presets = remember(context) {
        listOf(
            ThemedIconColorDefault(
                context.getString(R.string.themed_icon_color_preset_axicons),
                ThemedIconSettings.COLOR_PRESET_AXICONS,
                ThemedIconSettings.getAxBackgroundColor(context, false),
                ThemedIconSettings.getAxBackgroundColor(context, true),
                ThemedIconSettings.getAxForegroundColor(context, false),
                ThemedIconSettings.getAxForegroundColor(context, true),
            ),
            ThemedIconColorDefault(
                context.getString(R.string.themed_icon_color_preset_aosp),
                ThemedIconSettings.COLOR_PRESET_AOSP,
                ThemedIconSettings.getAospBackgroundColor(context, false),
                ThemedIconSettings.getAospBackgroundColor(context, true),
                ThemedIconSettings.getAospForegroundColor(context, false),
                ThemedIconSettings.getAospForegroundColor(context, true),
            ),
        )
    }

    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ThemedIconSwitchRow(
            enabled = enabled,
            onEnabledChange = onEnabledChange,
        )
        if (enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            ThemedIconScaleControl(
                scale = scale,
                scaleRange = scaleRange,
                onScaleChange = onScaleChange,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ThemedIconColorPalette(
                presets = presets,
                colorPreset = colorPreset,
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
                onPresetSelect = { onColorPresetChange(it.preset) },
                onBackgroundSelect = onBackgroundChange,
                onForegroundSelect = onForegroundChange,
                onCustomBackground = { colorTarget = ColorTarget.Background },
                onCustomForeground = { colorTarget = ColorTarget.Foreground },
            )
        }
    }

    colorTarget?.let { target ->
        val isBackground = target == ColorTarget.Background
        val pickerTitle = stringResource(
            if (isBackground) R.string.themed_icon_background_title
            else R.string.themed_icon_foreground_title,
        )
        ColorPickerDialog(
            initialColor = Color(if (isBackground) backgroundColor else foregroundColor),
            title = pickerTitle,
            onDismiss = { colorTarget = null },
            onColorSelected = { color ->
                if (isBackground) {
                    onBackgroundChange(color.toArgb())
                } else {
                    onForegroundChange(color.toArgb())
                }
                colorTarget = null
            },
        )
    }
}

@Composable
private fun ThemedIconSwitchRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.themed_icons_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.primary,
                checkedTrackColor = colors.primaryContainer,
            ),
        )
    }
}

@Composable
private fun ThemedIconScaleControl(
    scale: Int,
    scaleRange: ClosedFloatingPointRange<Float>,
    onScaleChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = stringResource(R.string.themed_icon_size_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.themed_icon_size_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.themed_icon_size_percent, scale),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = scale.toFloat(),
            onValueChange = { onScaleChange(it.roundToInt()) },
            valueRange = scaleRange,
            steps = ThemedIconSettings.MAX_ICON_SCALE - ThemedIconSettings.MIN_ICON_SCALE - 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThemedIconColorPalette(
    presets: List<ThemedIconColorDefault>,
    colorPreset: String,
    backgroundColor: Int,
    foregroundColor: Int,
    onPresetSelect: (ThemedIconColorDefault) -> Unit,
    onBackgroundSelect: (Int) -> Unit,
    onForegroundSelect: (Int) -> Unit,
    onCustomBackground: () -> Unit,
    onCustomForeground: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ColorTargetPalette(
            title = stringResource(R.string.themed_icon_background_title),
            presets = presets,
            colorPreset = colorPreset,
            selectedColor = backgroundColor,
            lightColorForPreset = { it.backgroundLight },
            darkColorForPreset = { it.backgroundDark },
            onPresetSelect = onPresetSelect,
            onColorSelect = onBackgroundSelect,
            onCustomClick = onCustomBackground,
        )
        ColorTargetPalette(
            title = stringResource(R.string.themed_icon_foreground_title),
            presets = presets,
            colorPreset = colorPreset,
            selectedColor = foregroundColor,
            lightColorForPreset = { it.foregroundLight },
            darkColorForPreset = { it.foregroundDark },
            onPresetSelect = onPresetSelect,
            onColorSelect = onForegroundSelect,
            onCustomClick = onCustomForeground,
        )
    }
}

@Composable
private fun ColorTargetPalette(
    title: String,
    presets: List<ThemedIconColorDefault>,
    colorPreset: String,
    selectedColor: Int,
    lightColorForPreset: (ThemedIconColorDefault) -> Int,
    darkColorForPreset: (ThemedIconColorDefault) -> Int,
    onPresetSelect: (ThemedIconColorDefault) -> Unit,
    onColorSelect: (Int) -> Unit,
    onCustomClick: () -> Unit,
) {
    val customSelected = colorPreset == ThemedIconSettings.COLOR_PRESET_CUSTOM
    val hasPaletteSelection = customSelected && ClockFacePaletteColors.any { it == selectedColor }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(presets) { preset ->
                SplitPaletteSwatch(
                    label = preset.label,
                    lightColor = lightColorForPreset(preset),
                    darkColor = darkColorForPreset(preset),
                    selected = colorPreset == preset.preset,
                    onClick = { onPresetSelect(preset) },
                )
            }
            items(ClockFacePaletteColors) { color ->
                SinglePaletteSwatch(
                    color = color,
                    selected = customSelected && color == selectedColor,
                    onClick = { onColorSelect(color) },
                )
            }
            item {
                CustomPaletteSwatch(
                    color = if (customSelected && !hasPaletteSelection) selectedColor else null,
                    selected = customSelected && !hasPaletteSelection,
                    onClick = onCustomClick,
                )
            }
        }
    }
}

@Composable
private fun SplitPaletteSwatch(
    label: String,
    lightColor: Int,
    darkColor: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .padding(4.dp)
            .clip(CircleShape),
    ) {
        Row(modifier = Modifier.size(32.dp).clip(CircleShape)) {
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 32.dp)
                    .background(Color(lightColor)),
            )
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 32.dp)
                    .background(Color(darkColor)),
            )
        }
    }
}

@Composable
private fun SinglePaletteSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

@Composable
private fun CustomPaletteSwatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .padding(4.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (color != null) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(color)))
        } else {
            RainbowCircle()
        }
    }
}

@Composable
private fun RainbowCircle() {
    Canvas(modifier = Modifier.size(32.dp)) {
        val colors = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
        )
        val sweep = 360f / colors.size
        colors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = index * sweep - 90f,
                sweepAngle = sweep + 1f,
                useCenter = true,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PackCarousel(
    packs: List<PackInfo>,
    activePack: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(packs) { pack ->
            val isActive = if (pack.packageName.isEmpty()) {
                activePack.isNullOrEmpty()
            } else {
                pack.packageName == activePack
            }

            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isActive) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        else Modifier
                    )
                    .clickable { onSelect(pack.packageName.ifEmpty { null }) }
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pack.icon != null) {
                    Image(
                        bitmap = pack.icon.toBitmap(48, 48).asImageBitmap(),
                        contentDescription = pack.label,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = pack.label.take(1),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = pack.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ShapeCarousel(
    shapes: Array<IconShapeModel>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(shapes) { shape ->
            val isActive = shape.key == selectedKey ||
                (selectedKey.isEmpty() && shape.key == "circle")

            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isActive) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        else Modifier
                    )
                    .clickable { onSelect(shape.key) }
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShapePreview(
                    pathData = shape.pathString,
                    isActive = isActive,
                    modifier = Modifier.size(40.dp),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = context.getString(shape.titleId),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ShapePreview(pathData: String, isActive: Boolean, modifier: Modifier = Modifier) {
    val fillColor = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val shapePath = remember(pathData) {
        try { PathParser.createPathFromPathData(pathData) } catch (_: Exception) { null }
    }

    Canvas(modifier = modifier) {
        val path = shapePath ?: return@Canvas
        val scaleFactor = size.width / 100f
        val transformed = Path(path)
        val matrix = Matrix()
        matrix.setScale(scaleFactor, scaleFactor)
        transformed.transform(matrix)

        drawContext.canvas.nativeCanvas.drawPath(
            transformed,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor.toArgb()
                style = Paint.Style.FILL
            },
        )
    }
}

@Composable
private fun IconStyleTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun loadPacks(context: Context): PackLoadResult {
    val iconPacks = loadIconPacks(context)
    val themedIconPacks = loadThemedIconPacks(context)
    return PackLoadResult(
        iconPacks = iconPacks.first,
        activeIconPack = iconPacks.second,
        themedIconPacks = themedIconPacks,
        activeThemedIconPack = Settings.Secure.getString(
            context.contentResolver,
            SETTING_THEMED_ICON_PACK,
        ),
    )
}

private fun loadIconPacks(context: Context): Pair<List<PackInfo>, String?> {
    return try {
        val engine = ThemeEngine.getInstance(context)
        val pm = context.packageManager
        val packs = mutableListOf(PackInfo("", context.getString(R.string.icon_pack_picker_default)))
        val installed = engine?.getInstalledIconPacks() ?: emptyList()
        for (pkg in installed) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                packs.add(PackInfo(
                    pkg,
                    pm.getApplicationLabel(appInfo).toString(),
                    pm.getApplicationIcon(appInfo),
                ))
            } catch (_: NameNotFoundException) {
            }
        }
        packs to engine?.getIconPackPackage()
    } catch (_: Throwable) {
        listOf(PackInfo("", context.getString(R.string.icon_pack_picker_default))) to null
    }
}

private fun loadThemedIconPacks(context: Context): List<PackInfo> {
    val pm = context.packageManager
    val themed = mutableListOf(PackInfo("", context.getString(R.string.themed_icon_pack_none)))
    val seen = mutableSetOf<String>()

    try {
        val results = pm.queryIntentActivities(Intent(ACTION_THEMED_ICON), 0)
        for (ri in results) {
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                themed.add(PackInfo(
                    pkg,
                    pm.getApplicationLabel(appInfo).toString(),
                    pm.getApplicationIcon(appInfo),
                ))
            } catch (_: NameNotFoundException) {
            }
        }
    } catch (_: Throwable) {
    }

    try {
        val installed = ThemeEngine.getInstance(context)?.getInstalledIconPacks() ?: emptyList()
        for (pkg in installed) {
            if (!seen.add(pkg)) continue
            try {
                val res = pm.getResourcesForApplication(pkg)
                if (res.getIdentifier("grayscale_icon_map", "xml", pkg) != 0) {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    themed.add(PackInfo(
                        pkg,
                        pm.getApplicationLabel(appInfo).toString(),
                        pm.getApplicationIcon(appInfo),
                    ))
                }
            } catch (_: Throwable) {
            }
        }
    } catch (_: Throwable) {
    }

    return themed
}

private fun applyIconPack(context: Context, packageName: String) {
    try {
        val resolver = context.contentResolver
        val json = Settings.Secure.getString(resolver, SETTINGS_THEME_ENGINE_DATA)
        val config = if (json.isNullOrBlank()) JSONObject() else JSONObject(json)

        val themes = config.optJSONObject("themes") ?: JSONObject()
        val iconPackConfig = JSONObject()
        if (packageName.isEmpty()) {
            iconPackConfig.put("enabled", false)
            iconPackConfig.put("packageName", JSONObject.NULL)
        } else {
            iconPackConfig.put("enabled", true)
            iconPackConfig.put("packageName", packageName)
        }
        themes.put(CATEGORY_ICON_PACK, iconPackConfig)
        config.put("themes", themes)

        if (!config.has("version")) config.put("version", 1)

        Settings.Secure.putString(resolver, SETTINGS_THEME_ENGINE_DATA, config.toString())
    } catch (_: Exception) {
    }
}
