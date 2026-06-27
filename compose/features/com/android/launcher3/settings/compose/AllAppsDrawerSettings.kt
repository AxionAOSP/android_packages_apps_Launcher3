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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.FeatureCard
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.allapps.AxAllAppsDisplayPrefs
import com.android.launcher3.allapps.AxSmartDrawerManager

@Composable
private fun DrawerLayoutFeatureCards(
    selectedLayout: Int,
    onLayoutSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureCard(
            title = stringResource(R.string.drawer_layout_default_title),
            summary = stringResource(R.string.drawer_layout_default_summary),
            selected = selectedLayout == AxSmartDrawerManager.DRAWER_LAYOUT_DEFAULT,
            onClick = { onLayoutSelected(AxSmartDrawerManager.DRAWER_LAYOUT_DEFAULT) },
            modifier = Modifier.weight(1f),
        ) {
            DefaultDrawerMiniIllustration(
                selected = selectedLayout == AxSmartDrawerManager.DRAWER_LAYOUT_DEFAULT,
            )
        }
        FeatureCard(
            title = stringResource(R.string.drawer_layout_smart_title),
            summary = stringResource(R.string.drawer_layout_smart_summary),
            selected = selectedLayout == AxSmartDrawerManager.DRAWER_LAYOUT_SMART,
            onClick = { onLayoutSelected(AxSmartDrawerManager.DRAWER_LAYOUT_SMART) },
            modifier = Modifier.weight(1f),
        ) {
            SmartDrawerMiniIllustration(
                selected = selectedLayout == AxSmartDrawerManager.DRAWER_LAYOUT_SMART,
            )
        }
    }
}

@Composable
private fun DefaultDrawerMiniIllustration(selected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(miniIllustrationAccent(selected, 0.72f)),
        )
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(4) {
                    MiniIllustrationDot(selected = selected)
                }
            }
        }
    }
}

@Composable
private fun SmartDrawerMiniIllustration(selected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(miniIllustrationAccent(selected, 0.72f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(2) {
                MiniSmartDrawerCard(
                    selected = selected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniSmartDrawerCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(miniIllustrationAccent(selected, 0.22f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(4.dp)
                .clip(CircleShape)
                .background(miniIllustrationAccent(selected, 0.62f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MiniIllustrationDot(selected = selected)
            MiniIllustrationDot(selected = selected)
        }
    }
}

@Composable
private fun MiniIllustrationDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(miniIllustrationAccent(selected, 0.88f)),
    )
}

@Composable
private fun miniIllustrationAccent(selected: Boolean, alpha: Float) =
    if (selected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    }

@Composable
internal fun AllAppsDrawerScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val defaultColumns = remember(context) {
        LauncherAppState.getIDP(context).getDeviceProfile(context).numShownAllAppsColumns
    }
    val layoutPreference = rememberLauncherPreference(LauncherPrefsExt.ALL_APPS_DRAWER_LAYOUT_MODE)
    val opacityPreference = rememberLauncherPreference(
        item = LauncherPrefsExt.ALL_APPS_BG_OPACITY,
        read = { LauncherPrefsExt.allAppsOpacityPercent(context) },
        write = { prefs, value ->
            prefs.put(LauncherPrefsExt.ALL_APPS_BG_OPACITY, value.coerceIn(0, 100))
        },
    )
    DrawerLayoutFeatureCards(
        selectedLayout = layoutPreference.value,
        onLayoutSelected = layoutPreference.onChange,
    )
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_layout_category)) {
        if (layoutPreference.value == AxSmartDrawerManager.DRAWER_LAYOUT_SMART) {
            item {
                CategoryPreference(
                    titleRes = R.string.smart_drawer_folders_title,
                    summaryRes = R.string.smart_drawer_folders_summary,
                    onClick = { onNavigate(HomeSettingsRoutes.ALL_APPS_SMART_DRAWER) },
                )
            }
        } else {
            item {
                CategoryPreference(
                    titleRes = R.string.all_apps_folders_title,
                    summaryRes = R.string.all_apps_folders_summary,
                    onClick = { onNavigate(HomeSettingsRoutes.ALL_APPS_FOLDERS) },
                )
            }
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_behavior_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SHOW_ALLAPPS_PREDICTIONS,
                titleRes = R.string.drawer_predictions_title,
                summaryRes = R.string.drawer_predictions_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_HAPTIC_FEEDBACK,
                titleRes = R.string.drawer_haptic_feedback_title,
                summaryRes = R.string.drawer_haptic_feedback_summary,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_REMEMBER_POSITION,
                titleRes = R.string.drawer_remember_position_title,
                summaryRes = R.string.drawer_remember_position_summary,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_grid_category)) {
        item {
            IntSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_COLUMNS,
                titleRes = R.string.drawer_columns_title,
                min = AxAllAppsDisplayPrefs.MIN_COLUMNS_FOR_SETTINGS,
                max = AxAllAppsDisplayPrefs.MAX_COLUMNS_FOR_SETTINGS,
                defaultValue = defaultColumns,
                valueOverride = { if (it > 0) it else defaultColumns },
                resetValue = 0,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_ICON_SCALE,
                titleRes = R.string.drawer_icon_size_title,
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_ROW_SCALE,
                titleRes = R.string.drawer_row_height_title,
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_SIDE_PADDING_SCALE,
                titleRes = R.string.drawer_side_padding_title,
                min = 0,
                max = 150,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_appearance_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALLAPPS_THEMED_ICONS,
                titleRes = R.string.pref_themed_icons_title,
                summaryRes = R.string.pref_themed_icons_summary,
            )
        }
        item {
            IntSliderPreference(
                preference = opacityPreference,
                titleRes = R.string.drawer_opacity_title,
                min = 0,
                max = 100,
                defaultValue = LauncherPrefsExt.ALL_APPS_DEFAULT_BG_OPACITY,
                interval = 10,
                valueLabel = { stringResource(R.string.home_settings_percent_value, it) },
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_labels_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.SHOW_DRAWER_LABELS,
                titleRes = R.string.drawer_show_labels,
            )
        }
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ENABLE_TWOLINE_ALLAPPS_TOGGLE,
                titleRes = R.string.drawer_two_line_labels_title,
                summaryRes = R.string.drawer_two_line_labels_summary,
            )
        }
        item {
            PercentSliderPreference(
                item = LauncherPrefsExt.ALL_APPS_DRAWER_LABEL_SCALE,
                titleRes = R.string.drawer_label_size_title,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.all_apps_drawer_advanced_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.ALL_APPS_SHOW_SCROLLBAR,
                titleRes = R.string.drawer_show_scrollbar_title,
                summaryRes = R.string.drawer_show_scrollbar_summary,
            )
        }
    }
}
