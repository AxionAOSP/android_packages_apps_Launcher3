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
import android.content.pm.PackageManager
import android.content.res.ThemeEngine
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
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

private const val SETTING_THEMED_ICON_STYLE = "themed_icon_style"
private const val SETTING_THEMED_ICONS = "themed_icons"
private const val SETTING_THEMED_ICON_PACK = "themed_icon_pack"
private const val SETTINGS_THEME_ENGINE_DATA = "theme_engine_data"
private const val CATEGORY_ICON_PACK = "icon_pack"
private const val STYLE_AXION = "axion"
private const val STYLE_AOSP = "aosp"
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

@Composable
private fun IconStyleContent() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val resolver = context.contentResolver

    var iconPacks by remember { mutableStateOf<List<PackInfo>>(emptyList()) }
    var activeIconPack by remember { mutableStateOf<String?>(null) }

    var themedEnabled by remember {
        mutableStateOf(Settings.Secure.getInt(resolver, SETTING_THEMED_ICONS, 0) == 1)
    }
    var themedStyle by remember {
        mutableStateOf(Settings.Secure.getString(resolver, SETTING_THEMED_ICON_STYLE) ?: STYLE_AXION)
    }

    var themedIconPacks by remember { mutableStateOf<List<PackInfo>>(emptyList()) }
    var activeThemedIconPack by remember { mutableStateOf<String?>(null) }

    val shapes = remember { ShapesProvider.iconShapes }
    var selectedShape by remember {
        mutableStateOf(LauncherPrefs.get(context).get(ThemeManager.PREF_ICON_SHAPE))
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val engine = ThemeEngine.getInstance(context)
                val pm = context.packageManager

                val packs = mutableListOf(
                    PackInfo("", context.getString(R.string.icon_pack_picker_default))
                )
                val installed = engine?.getInstalledIconPacks() ?: emptyList()
                for (pkg in installed) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        packs.add(PackInfo(pkg, label, pm.getApplicationIcon(appInfo)))
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
                iconPacks = packs
                activeIconPack = engine?.getIconPackPackage()
            } catch (_: Exception) {}

            try {
                val pm = context.packageManager
                val themed = mutableListOf(
                    PackInfo("", context.getString(R.string.themed_icon_pack_none))
                )
                val seen = mutableSetOf<String>()

                try {
                    val results = pm.queryIntentActivities(
                        android.content.Intent(ACTION_THEMED_ICON), 0)
                    for (ri in results) {
                        val pkg = ri.activityInfo.packageName
                        if (!seen.add(pkg)) continue
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            themed.add(PackInfo(pkg, label, pm.getApplicationIcon(appInfo)))
                        } catch (_: PackageManager.NameNotFoundException) {}
                    }
                } catch (_: Exception) {}

                val installed = ThemeEngine.getInstance(context)
                    ?.getInstalledIconPacks() ?: emptyList()
                for (pkg in installed) {
                    if (!seen.add(pkg)) continue
                    try {
                        val res = pm.getResourcesForApplication(pkg)
                        if (res.getIdentifier("grayscale_icon_map", "xml", pkg) != 0) {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            themed.add(PackInfo(pkg, label, pm.getApplicationIcon(appInfo)))
                        }
                    } catch (_: Exception) {}
                }

                themedIconPacks = themed
                activeThemedIconPack = Settings.Secure.getString(
                    resolver, SETTING_THEMED_ICON_PACK)
            } catch (_: Exception) {}
        }
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
            color = colors.onSurface,
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
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
        }

        SectionLabel(stringResource(R.string.themed_icons_card_title))
        Spacer(modifier = Modifier.height(10.dp))
        ThemedIconsSection(
            enabled = themedEnabled,
            style = themedStyle,
            onEnabledChange = { enabled ->
                themedEnabled = enabled
                Settings.Secure.putInt(resolver, SETTING_THEMED_ICONS, if (enabled) 1 else 0)
            },
            onStyleChange = { style ->
                themedStyle = style
                Settings.Secure.putString(resolver, SETTING_THEMED_ICON_STYLE, style)
            },
        )

        if (themedIconPacks.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

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
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                    .width(70.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isActive) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
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
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
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
private fun ThemedIconsSection(
    enabled: Boolean,
    style: String,
    onEnabledChange: (Boolean) -> Unit,
    onStyleChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.themed_icons_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
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

        if (enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(STYLE_AXION, STYLE_AOSP)
                val labels = listOf("AxIcons", "AOSP")
                val selectedIndex = options.indexOf(style).coerceAtLeast(0)

                options.forEachIndexed { index, s ->
                    SegmentedButton(
                        selected = selectedIndex == index,
                        onClick = { onStyleChange(s) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold
                                else FontWeight.Medium,
                        )
                    }
                }
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
                    .width(70.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isActive) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
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
        val transformed = android.graphics.Path(path)
        val matrix = android.graphics.Matrix()
        matrix.setScale(scaleFactor, scaleFactor)
        transformed.transform(matrix)

        drawContext.canvas.nativeCanvas.drawPath(
            transformed,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor.toArgb()
                style = android.graphics.Paint.Style.FILL
            },
        )
    }
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
    } catch (_: Exception) {}
}

@Composable
private fun IconStyleTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colorScheme, content = content)
}
