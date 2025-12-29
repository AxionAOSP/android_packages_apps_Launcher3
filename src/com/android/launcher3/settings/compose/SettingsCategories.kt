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

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.*
import com.android.launcher3.R
import com.android.launcher3.states.RotationHelper

@Composable
fun HomeScreenSettings(viewModel: SettingsState, context: Context) {
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
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        context.startActivity(intent)
                    } else {
                        viewModel.setBoolean("pref_icon_badging", false)
                    }
                }
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
    }
}

@Composable
fun AppDrawerSettings(viewModel: SettingsState) {
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
}

@Composable
fun BehaviorSettings(viewModel: SettingsState) {
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
        item {
            val allowRotation by viewModel.allowRotation.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.allow_rotation_title),
                summary = stringResource(R.string.allow_rotation_desc),
                checked = allowRotation,
                onCheckedChange = { viewModel.setBoolean(RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY, it) }
            )
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
