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

package com.android.launcher3.settings.compose

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.*
import com.android.launcher3.R
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.qsb.SearchWidgetHelper
import com.android.launcher3.LauncherFiles
import com.android.launcher3.util.DisplayController
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.Flags
import kotlin.math.roundToInt

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    val myListener = ComponentName(context, NotificationListener::class.java)
    return enabledListeners != null && (
        enabledListeners.contains(myListener.flattenToString()) ||
        enabledListeners.contains(myListener.flattenToShortString())
    )
}

@Composable
fun HomeScreenSettings(viewModel: SettingsState, context: Context) {
    IconSizeSettings(viewModel, context)

    PreferenceGroup(
        title = stringResource(R.string.settings_category_home),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            val workspaceLock by viewModel.workspaceLock.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.settings_lock_layout_title),
                summary = if (workspaceLock) stringResource(R.string.settings_lock_layout_summary_on) else stringResource(R.string.settings_lock_layout_summary_off),
                checked = workspaceLock,
                onCheckedChange = { viewModel.setBoolean("pref_workspace_lock", it) }
            )
        }
        item {
            val desktopShowLabels by viewModel.desktopShowLabels.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.desktop_show_labels),
                checked = desktopShowLabels,
                onCheckedChange = { viewModel.setBoolean("pref_desktop_show_labels", it) }
            )
        }
        item {
            val notificationDots by viewModel.notificationDots.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.notification_dots_title),
                summary = stringResource(R.string.notification_dots_service_title),
                checked = notificationDots,
                onCheckedChange = {
                    if (it) {
                        if (isNotificationServiceEnabled(context)) {
                            Settings.Secure.putInt(
                                context.contentResolver,
                                "notification_badging",
                                1
                            )
                        } else {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            context.startActivity(intent)
                        }
                    } else {
                        Settings.Secure.putInt(
                            context.contentResolver,
                            "notification_badging",
                            0
                        )
                    }
                }
            )
        }
        item {
            val disableWallpaperZoom by viewModel.disableWallpaperZoom.collectAsState()
            SwitchPreference(
                title = "Disable Wallpaper Zoom",
                summary = "Prevent wallpaper from zooming when opening apps or switching states",
                checked = disableWallpaperZoom,
                onCheckedChange = { viewModel.setBoolean("pref_disable_wallpaper_zoom", it) }
            )
        }
        item {
            val autoAddIcons by viewModel.autoAddIcons.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.auto_add_shortcuts_label),
                summary = stringResource(R.string.auto_add_shortcuts_description),
                checked = autoAddIcons,
                onCheckedChange = { viewModel.setBoolean("pref_add_icon_to_home", it) }
            )
        }
        item {
            val searchWidgetHelper = remember { SearchWidgetHelper.getAvailableSearchWidgets(context) }
            val searchWidgetOptions = remember(searchWidgetHelper) {
                searchWidgetHelper
                    .filter { info -> !info.label.equals("Search", ignoreCase = true) }
                    .map { info -> info.provider.flattenToString() to info.label }
            }
            
            val searchWidgetPrefs = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            var selectedSearchWidget by remember {
                mutableStateOf(searchWidgetPrefs.getString("pref_qsb_search_provider", "") ?: "")
            }

            if (searchWidgetOptions.isNotEmpty()) {
                ListPreference(
                    title = "Search Provider",
                    summary = if (selectedSearchWidget == "none") "None" else searchWidgetOptions.find { it.first == selectedSearchWidget }?.second ?: searchWidgetOptions.firstOrNull()?.second ?: "Select a provider",
                    options = listOf("none" to "None") + searchWidgetOptions,
                    value = selectedSearchWidget,
                    onValueChange = { newValue ->
                        selectedSearchWidget = newValue
                        searchWidgetPrefs.edit().putString("pref_qsb_search_provider", newValue).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun AppDrawerSettings(viewModel: SettingsState) {
    PreferenceGroup(
        title = "Drawer Layout",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            val drawerLayoutMode by viewModel.drawerLayoutMode.collectAsState()
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LayoutModeCard(
                        title = "Default",
                        description = "Normal grid layout",
                        isSelected = drawerLayoutMode == "dynamic",
                        onClick = { viewModel.setDrawerLayoutMode("default") },
                        modifier = Modifier.weight(1f)
                    )
                    
                    LayoutModeCard(
                        title = "Smart",
                        description = "Category folders",
                        badge = "BETA",
                        isSelected = drawerLayoutMode == "smart",
                        onClick = { viewModel.setDrawerLayoutMode("smart") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    
    PreferenceGroup(
        title = stringResource(R.string.settings_category_drawer),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            val themedIcons by viewModel.themedIcons.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.pref_themed_icons_drawer_title),
                summary = stringResource(R.string.pref_themed_icons_drawer_summary),
                checked = themedIcons,
                onCheckedChange = { viewModel.setBoolean("pref_allapps_themed_icons", it) }
            )
        }
        item {
            val drawerShowLabels by viewModel.drawerShowLabels.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.drawer_show_labels),
                checked = drawerShowLabels,
                onCheckedChange = { viewModel.setBoolean("pref_drawer_show_labels", it) }
            )
        }
        item {
            val swipeToSearch by viewModel.swipeToSearch.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.drawer_open_keyboard_title),
                summary = stringResource(R.string.drawer_open_keyboard_summary),
                checked = swipeToSearch,
                onCheckedChange = { viewModel.setBoolean("pref_drawer_open_keyboard", it) }
            )
        }
    }
    
    PreferenceGroup(
        title = "Appearance",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val alpha by viewModel.allAppsBgOpacity.collectAsState()
                    val alphaFloat = alpha / 255f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "All Apps Opacity",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val isModified = alphaFloat != 0.5f
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (isModified) {
                                            Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    viewModel.setAllAppsBgOpacity(128)
                                                }
                                            )
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = if (isModified) "Long press to reset" else null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (isModified) 1f else 0.3f
                                    ),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "${(alphaFloat * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = alphaFloat,
                        onValueChange = { viewModel.setAllAppsBgOpacity((it * 255).toInt()) },
                        valueRange = 0f..1f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BehaviorSettings(viewModel: SettingsState) {
    val context = LocalContext.current
    val info = remember { DisplayController.INSTANCE.get(context).info }
    
    val showAllowRotation = remember(info) {
        val isTablet = info.isTablet(info.realBounds)
        val rotationAllowed = info.isRotationAllowed()
        val oneGridSpecs = Flags.oneGridSpecs()
        
        !(oneGridSpecs && !rotationAllowed) && !isTablet
    }

    val showFixedLandscape = remember(info, context) {
        val oneGridSpecs = Flags.oneGridSpecs()
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val isMultiDisplay = idp.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
        val isTablet = idp.deviceType == InvariantDeviceProfile.TYPE_TABLET
        val rotationAllowed = info.isRotationAllowed()
        
        oneGridSpecs && !isMultiDisplay && !isTablet && !rotationAllowed
    }

    PreferenceGroup(
        title = stringResource(R.string.settings_category_behavior),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            val doubleTapToSleep by viewModel.doubleTapToSleep.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.pref_sleep_gesture_title),
                summary = stringResource(R.string.pref_sleep_gesture_summary),
                checked = doubleTapToSleep,
                onCheckedChange = { viewModel.setBoolean("pref_sleep_gesture", it) }
            )
        }

        if (showAllowRotation) {
            item {
                val allowRotation by viewModel.allowRotation.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.allow_rotation_title),
                    summary = stringResource(R.string.allow_rotation_desc),
                    checked = allowRotation,
                    onCheckedChange = { viewModel.setBoolean(RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY, it) }
                )
            }
        }

        if (showFixedLandscape) {
            item {
                val fixedLandscape by viewModel.fixedLandscape.collectAsState()
                SwitchPreference(
                    title = "Landscape mode",
                    summary = "Always use landscape mode for home screen",
                    checked = fixedLandscape,
                    onCheckedChange = { viewModel.setBoolean(SettingsActivity.FIXED_LANDSCAPE_MODE, it) }
                )
            }
        }

        item {
            val showGoogleApp by viewModel.showGoogleApp.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.title_show_google_app),
                summary = stringResource(R.string.msg_minus_one_on_left),
                checked = showGoogleApp,
                onCheckedChange = { viewModel.setBoolean("pref_enable_minus_one", it) }
            )
        }
    }
}

@Composable
private fun LayoutModeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                badge?.let {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun IconSizeSettings(viewModel: SettingsState, context: Context) {
    val workspaceScale by viewModel.workspaceIconScale.collectAsState()
    val allAppsScale by viewModel.allAppsIconScale.collectAsState()
    val haptic = LocalHapticFeedback.current

    val idp = remember { InvariantDeviceProfile.INSTANCE.get(context) }
    val density = LocalDensity.current
    val workspaceBaseSize = remember(idp) {
        with(density) { idp.iconSize[0].dp }
    }
    val allAppsBaseSize = remember(idp) {
        with(density) { idp.allAppsIconSize[0].dp }
    }

    val settingsIcon = remember {
        try {
            context.packageManager.getApplicationIcon("com.android.settings")
        } catch (e: PackageManager.NameNotFoundException) {
            context.packageManager.defaultActivityIcon
        }
    }

    val iconBitmap = remember(settingsIcon) {
        val bitmap = Bitmap.createBitmap(
            settingsIcon.intrinsicWidth.coerceAtLeast(1),
            settingsIcon.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        settingsIcon.setBounds(0, 0, canvas.width, canvas.height)
        settingsIcon.draw(canvas)
        bitmap.asImageBitmap()
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Icon Sizes",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(workspaceBaseSize * workspaceScale)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(workspaceScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(allAppsBaseSize * allAppsScale)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "App Drawer",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(allAppsScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                IconSizeSlider(
                    label = "Home Screen",
                    scale = workspaceScale,
                    onScaleChange = { viewModel.setFloat("pref_workspace_icon_scale", it) },
                    onReset = { viewModel.setFloat("pref_workspace_icon_scale", 1.0f) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                IconSizeSlider(
                    label = "App Drawer",
                    scale = allAppsScale,
                    onScaleChange = { viewModel.setFloat("pref_allapps_icon_scale", it) },
                    onReset = { viewModel.setFloat("pref_allapps_icon_scale", 1.0f) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconSizeSlider(
    label: String,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
                val isModified = scale != 1.0f
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (isModified) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onReset()
                                    }
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isModified) "Long press to reset" else null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isModified) 1f else 0.3f
                        ),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = "${(scale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = scale,
            onValueChange = onScaleChange,
            valueRange = 0.75f..1.25f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}
