package com.android.launcher3.settings.compose

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.*
import com.android.launcher3.R
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.DisplayController
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.Flags
import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.allapps.compose.search.domain.UniversalSearchManager
import com.android.launcher3.qsb.HotseatQsbSearchProvider
import com.android.launcher3.qsb.SearchWidgetHelper
import kotlin.math.roundToInt


@Composable
fun GeneralSettings(viewModel: SettingsState, context: Context) {
    IconSizeSettings(viewModel, context)

    Spacer(modifier = Modifier.height(8.dp))

    PreferenceGroup(title = stringResource(R.string.settings_section_display)) {
        item {
            val notificationDots by viewModel.notificationDots.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.notification_dots_title),
                summary = stringResource(R.string.notification_dots_service_title),
                checked = notificationDots,
                onCheckedChange = {
                    handleNotificationDotsChange(it, context)
                }
            )
        }
        item {
            val disableWallpaperZoom by viewModel.disableWallpaperZoom.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.pref_disable_wallpaper_zoom_title),
                summary = stringResource(R.string.pref_disable_wallpaper_zoom_summary),
                checked = disableWallpaperZoom,
                onCheckedChange = { viewModel.setBoolean("pref_disable_wallpaper_zoom", it) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LauncherBlurSettings()
}

@Composable
private fun LauncherBlurSettings() {
    val (blurEnabled, setBlurEnabled) = rememberSecureSettingBooleanState(
        key = "pulse_launcher_blur_enabled",
        defaultValue = true,
    )
    val (blurRadius, setBlurRadius) = rememberSecureSettingIntState(
        key = "pulse_launcher_blur_radius",
        defaultValue = 34,
    )
    val sliderValue = blurRadius.coerceIn(0, 100).toFloat()

    PreferenceGroup(title = stringResource(R.string.pref_launcher_blur_category)) {
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_launcher_blur_title),
                summary = stringResource(R.string.pref_launcher_blur_summary),
                checked = blurEnabled,
                onCheckedChange = setBlurEnabled,
            )
        }
        item {
            SliderPreference(
                title = stringResource(R.string.pref_launcher_blur_radius_title),
                summary = stringResource(R.string.pref_launcher_blur_radius_summary),
                value = sliderValue,
                onValueChange = { setBlurRadius(it.roundToInt()) },
                onValueChangeFinished = {},
                valueRange = 0f..100f,
                steps = 0,
                displayValue = stringResource(
                    R.string.pref_launcher_blur_radius_pixels,
                    sliderValue.roundToInt(),
                ),
                enabled = blurEnabled,
                onReset = { setBlurRadius(34) },
            )
        }
    }
}

private fun handleNotificationDotsChange(enabled: Boolean, context: Context) {
    if (enabled) {
        if (isNotificationServiceEnabled(context)) {
            Settings.Secure.putInt(context.contentResolver, "notification_badging", 1)
        } else {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
        }
    } else {
        Settings.Secure.putInt(context.contentResolver, "notification_badging", 0)
    }
}

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

    PreferenceGroup(title = stringResource(R.string.settings_section_layout)) {
        item {
            val workspaceLock by viewModel.workspaceLock.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.settings_lock_layout_title),
                summary = if (workspaceLock) stringResource(R.string.settings_lock_layout_summary_on)
                          else stringResource(R.string.settings_lock_layout_summary_off),
                checked = workspaceLock,
                onCheckedChange = { viewModel.setBoolean("pref_workspace_lock", it) }
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
                    title = stringResource(R.string.pref_landscape_mode_title),
                    summary = stringResource(R.string.pref_landscape_mode_summary),
                    checked = fixedLandscape,
                    onCheckedChange = { viewModel.setBoolean(SettingsActivity.FIXED_LANDSCAPE_MODE, it) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PreferenceGroup(title = stringResource(R.string.settings_section_labels)) {
        item {
            val desktopShowLabels by viewModel.desktopShowLabels.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.desktop_show_labels),
                checked = desktopShowLabels,
                onCheckedChange = { viewModel.setBoolean("pref_desktop_show_labels", it) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PreferenceGroup {
        item {
            val searchWidgets = remember { SearchWidgetHelper.getAvailableSearchWidgets(context) }
            val searchWidgetOptions = remember(searchWidgets) {
                searchWidgets
                    .filter { info -> !info.label.equals("Search", ignoreCase = true) }
                    .map { info -> info.provider.flattenToString() to info.label }
            }

            val settingsFlow = rememberSettingsFlow(SettingsType.SECURE)
            val selectedProvider by rememberSettingString(
                HotseatQsbSearchProvider.KEY,
                default = HotseatQsbSearchProvider.DEFAULT
            )

            if (searchWidgetOptions.isNotEmpty()) {
                ListPreference(
                    title = "Search Provider",
                    summary = if (selectedProvider == "none") "None"
                              else searchWidgetOptions.find { it.first == selectedProvider }?.second
                                  ?: searchWidgetOptions.firstOrNull()?.second
                                  ?: "Select a provider",
                    options = listOf("none" to "None") + searchWidgetOptions,
                    value = selectedProvider,
                    onValueChange = { newValue ->
                        settingsFlow.putString(HotseatQsbSearchProvider.KEY, newValue)
                    }
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
fun AppDrawerSettings(viewModel: SettingsState) {
    PreferenceGroup(title = stringResource(R.string.settings_section_layout)) {
        item {
            val drawerLayoutMode by viewModel.drawerLayoutMode.collectAsState()
            DrawerLayoutSelector(
                currentMode = drawerLayoutMode,
                onModeSelected = { viewModel.setDrawerLayoutMode(it) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PreferenceGroup {
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
        item {
            val allAppsPredictions by viewModel.allAppsPredictions.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.all_apps_suggestions_title),
                summary = stringResource(R.string.all_apps_suggestions_summary),
                checked = allAppsPredictions,
                onCheckedChange = { viewModel.setBoolean("pref_all_apps_predictions", it) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PreferenceGroup(title = stringResource(R.string.settings_section_style)) {
        item {
            OpacitySliderPreference(viewModel)
        }
    }
}

@Composable
private fun DrawerLayoutSelector(
    currentMode: String,
    onModeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LayoutModeCard(
            title = "Default",
            isSelected = currentMode == "dynamic",
            onClick = { onModeSelected("default") },
            icon = Icons.Outlined.GridView,
            modifier = Modifier.weight(1f)
        )
        LayoutModeCard(
            title = "Smart",
            badge = "BETA",
            isSelected = currentMode == "smart",
            onClick = { onModeSelected("smart") },
            icon = Icons.Outlined.AutoAwesome,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LayoutModeCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpacitySliderPreference(viewModel: SettingsState) {
    val alpha by viewModel.allAppsBgOpacity.collectAsState()
    val alphaFloat = alpha / 255f

    SliderPreference(
        title = stringResource(R.string.pref_all_apps_opacity_title),
        summary = stringResource(R.string.pref_all_apps_opacity_summary),
        value = alphaFloat,
        onValueChange = { viewModel.setAllAppsBgOpacity((it * 255).toInt()) },
        onValueChangeFinished = {},
        valueRange = 0f..1f,
        steps = 0,
        displayValue = "${(alphaFloat * 100).roundToInt()}%",
        onReset = { viewModel.setAllAppsBgOpacity(255) }
    )
}


@Composable
fun SearchSettingsPage(context: Context) {
    val searchManager = remember { UniversalSearchManager(context) }
    val preferences by searchManager.preferences.collectAsState()

    DisposableEffect(Unit) {
        onDispose { searchManager.cleanup() }
    }

    PreferenceGroup(title = stringResource(R.string.settings_section_providers)) {
        item {
            SwitchPreference(
                title = "Contacts",
                summary = "Search your contacts",
                checked = preferences.searchContacts,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_CONTACTS, it) }
            )
        }
        item {
            SwitchPreference(
                title = "Messages",
                summary = "Search your SMS messages",
                checked = preferences.searchMessages,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_MESSAGES, it) }
            )
        }
        item {
            SwitchPreference(
                title = "Files",
                summary = "Search local files",
                checked = preferences.searchFiles,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_FILES, it) }
            )
        }
        item {
            SwitchPreference(
                title = "Photos",
                summary = "Search device photos",
                checked = preferences.searchPhotos,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_PHOTOS, it) }
            )
        }
        item {
            SwitchPreference(
                title = "Calendar",
                summary = "Search calendar events",
                checked = preferences.searchCalendar,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_CALENDAR, it) }
            )
        }
        item {
            SwitchPreference(
                title = "Settings",
                summary = "Search system settings",
                checked = preferences.searchSettings,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_SETTINGS, it) }
            )
        }
        item {
            SwitchPreference(
                title = "Web Search",
                summary = "Allow web search actions",
                checked = preferences.searchWeb,
                onCheckedChange = { searchManager.setSearchPreference(PreferenceKeys.SEARCH_WEB, it) }
            )
        }
    }
}


@Composable
fun GestureSettings(viewModel: SettingsState) {
    PreferenceGroup {
        item {
            val doubleTapToSleep by viewModel.doubleTapToSleep.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.pref_sleep_gesture_title),
                summary = stringResource(R.string.pref_sleep_gesture_summary),
                checked = doubleTapToSleep,
                onCheckedChange = { viewModel.setBoolean("pref_sleep_gesture", it) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    NavHandleBurnInSettings()
}

@Composable
private fun NavHandleBurnInSettings() {
    val (burnInEnabled, setBurnInEnabled) = rememberSecureSettingBooleanState(
        key = "pulse_stashed_handle_burn_in_enabled",
        defaultValue = true,
    )
    val (intervalSec, setIntervalSec) = rememberSecureSettingIntState(
        key = "pulse_stashed_handle_burn_in_interval_sec",
        defaultValue = 60,
    )
    val sliderValue = intervalSec.coerceIn(15, 300).toFloat()

    PreferenceGroup(title = stringResource(R.string.pref_nav_handle_burn_in_category)) {
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_nav_handle_burn_in_title),
                summary = stringResource(R.string.pref_nav_handle_burn_in_summary),
                checked = burnInEnabled,
                onCheckedChange = setBurnInEnabled,
            )
        }
        item {
            SliderPreference(
                title = stringResource(R.string.pref_nav_handle_burn_in_interval_title),
                summary = stringResource(R.string.pref_nav_handle_burn_in_interval_summary),
                value = sliderValue,
                onValueChange = { setIntervalSec(it.roundToInt()) },
                onValueChangeFinished = {},
                valueRange = 15f..300f,
                steps = 0,
                displayValue = stringResource(
                    R.string.pref_nav_handle_burn_in_interval_seconds,
                    sliderValue.roundToInt(),
                ),
                enabled = burnInEnabled,
                onReset = { setIntervalSec(60) },
            )
        }
    }
}


@Composable
fun RecentsSettings() {
    val (showLock, setShowLock) = rememberSecureSettingBooleanState(
        key = "pulse_recents_show_lock",
        defaultValue = true,
    )
    val (showScreenshot, setShowScreenshot) = rememberSecureSettingBooleanState(
        key = "pulse_recents_show_screenshot",
        defaultValue = true,
    )
    val (showSelectText, setShowSelectText) = rememberSecureSettingBooleanState(
        key = "pulse_recents_show_select_text",
        defaultValue = true,
    )
    val (showFreeform, setShowFreeform) = rememberSecureSettingBooleanState(
        key = "pulse_recents_show_freeform",
        defaultValue = true,
    )
    val (showClearAll, setShowClearAll) = rememberSecureSettingBooleanState(
        key = "pulse_recents_show_clear_all",
        defaultValue = true,
    )

    PreferenceGroup(title = stringResource(R.string.pref_recents_buttons_category)) {
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_recents_show_lock_title),
                summary = stringResource(R.string.pref_recents_show_lock_summary),
                checked = showLock,
                onCheckedChange = setShowLock,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_recents_show_screenshot_title),
                summary = stringResource(R.string.pref_recents_show_screenshot_summary),
                checked = showScreenshot,
                onCheckedChange = setShowScreenshot,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_recents_show_select_text_title),
                summary = stringResource(R.string.pref_recents_show_select_text_summary),
                checked = showSelectText,
                onCheckedChange = setShowSelectText,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_recents_show_freeform_title),
                summary = stringResource(R.string.pref_recents_show_freeform_summary),
                checked = showFreeform,
                onCheckedChange = setShowFreeform,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_recents_show_clear_all_title),
                summary = stringResource(R.string.pref_recents_show_clear_all_summary),
                checked = showClearAll,
                onCheckedChange = setShowClearAll,
            )
        }
    }
}

data class Contributor(
    val name: String,
    val role: String,
    val avatarRes: Int? = null
)

private val contributors = listOf(
    Contributor("rmp22", "Development", R.drawable.contributor_rmp22),
)

@Composable
fun AboutSettings(context: Context) {
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        val appIcon = remember {
            try {
                context.packageManager.getApplicationIcon(context.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                context.packageManager.defaultActivityIcon
            }
        }
        val iconBitmap = remember(appIcon) {
            val bitmap = Bitmap.createBitmap(
                appIcon.intrinsicWidth.coerceAtLeast(1),
                appIcon.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            appIcon.setBounds(0, 0, canvas.width, canvas.height)
            appIcon.draw(canvas)
            bitmap.asImageBitmap()
        }

        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pulse",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = appVersion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AboutActionButton(
                icon = Icons.Outlined.Code,
                label = stringResource(R.string.about_github),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AxionAOSP/android_packages_apps_Launcher3"))
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            AboutActionButton(
                icon = Icons.Outlined.Translate,
                label = stringResource(R.string.about_translate),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdin.com/project/axionos"))
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            AboutActionButton(
                icon = Icons.Outlined.VolunteerActivism,
                label = stringResource(R.string.about_donate),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/rmp22"))
                    context.startActivity(intent)
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    PreferenceGroup {
        item {
            ClickablePreference(
                title = stringResource(R.string.about_version),
                summary = appVersion,
                icon = Icons.Outlined.Info,
                onClick = {}
            )
        }
        item {
            ClickablePreference(
                title = stringResource(R.string.about_based_on),
                summary = "AOSP Launcher3",
                icon = Icons.Outlined.Code,
                onClick = {}
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PreferenceGroup(title = stringResource(R.string.about_contributors)) {
        contributors.forEach { contributor ->
            item {
                ContributorItem(contributor)
            }
        }
    }
}

@Composable
private fun ContributorItem(contributor: Contributor) {
    BasePreference(
        title = contributor.name,
        summary = contributor.role,
        customIcon = {
            if (contributor.avatarRes != null) {
                Image(
                    painter = painterResource(id = contributor.avatarRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contributor.name.first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}

@Composable
private fun AboutActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
fun IconSizeSettings(viewModel: SettingsState, context: Context) {
    val workspaceScale by viewModel.workspaceIconScale.collectAsState()
    val allAppsScale by viewModel.allAppsIconScale.collectAsState()

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

    PreferenceGroup(title = stringResource(R.string.settings_section_icons)) {
        item {
            IconPreviewRow(
                iconBitmap = iconBitmap,
                workspaceScale = workspaceScale,
                allAppsScale = allAppsScale,
                workspaceBaseSize = workspaceBaseSize,
                allAppsBaseSize = allAppsBaseSize
            )
        }
        item {
            IconSizeSlider(
                label = stringResource(R.string.icon_size_home),
                scale = workspaceScale,
                onScaleChange = { viewModel.setFloat("pref_workspace_icon_scale", it) },
                onReset = { viewModel.setFloat("pref_workspace_icon_scale", 1.0f) }
            )
        }
        item {
            IconSizeSlider(
                label = stringResource(R.string.icon_size_drawer),
                scale = allAppsScale,
                onScaleChange = { viewModel.setFloat("pref_allapps_icon_scale", it) },
                onReset = { viewModel.setFloat("pref_allapps_icon_scale", 1.0f) }
            )
        }
    }
}

@Composable
private fun IconPreviewRow(
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap,
    workspaceScale: Float,
    allAppsScale: Float,
    workspaceBaseSize: androidx.compose.ui.unit.Dp,
    allAppsBaseSize: androidx.compose.ui.unit.Dp
) {
    val position = LocalPreferencePosition.current
    val shape = preferenceShape(position)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        IconPreviewColumn(iconBitmap, stringResource(R.string.icon_size_home), workspaceScale, workspaceBaseSize)
        IconPreviewColumn(iconBitmap, stringResource(R.string.icon_size_drawer), allAppsScale, allAppsBaseSize)
    }
}

@Composable
private fun IconPreviewColumn(
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap,
    label: String,
    scale: Float,
    baseSize: androidx.compose.ui.unit.Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(baseSize * scale)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun IconSizeSlider(
    label: String,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    SliderPreference(
        title = label,
        summary = stringResource(R.string.icon_size_slider_summary),
        value = scale,
        onValueChange = onScaleChange,
        onValueChangeFinished = {},
        valueRange = 0.75f..1.25f,
        steps = 0,
        displayValue = "${(scale * 100).roundToInt()}%",
        onReset = onReset
    )
}
