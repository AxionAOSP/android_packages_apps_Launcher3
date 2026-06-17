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

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.R
import com.android.launcher3.lineage.LineageUtils
import com.android.launcher3.lineage.trust.TrustAppsActivity
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.settings.NotificationDotsPreference
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI

@Composable
internal fun NotificationsScreen(activity: SettingsActivity) {
    PreferenceGroup {
        item {
            NotificationDotsPreferenceItem(activity)
        }
    }
}

@Composable
internal fun NotificationDotsPreferenceItem(activity: SettingsActivity) {
    val state = rememberNotificationDotsState(activity)
    ClickablePreference(
        title = stringResource(R.string.notification_dots_title),
        summary = stringResource(state.summaryRes),
        onClick = {
            if (state.needsAccess) {
                NotificationDotsPreference.NotificationAccessConfirmation()
                    .show(activity.supportFragmentManager, NOTIFICATION_BADGING_KEY)
            } else {
                activity.startActivity(notificationSettingsIntent())
            }
        },
    )
}

@Composable
internal fun PrivacyScreen(activity: Activity) {
    val title = stringResource(R.string.trust_apps_manager_name)
    PreferenceGroup {
        item {
            CategoryPreference(
                titleRes = R.string.trust_apps_manager_name,
                onClick = {
                    LineageUtils.showLockScreen(activity, title) {
                        activity.startActivity(Intent(activity, TrustAppsActivity::class.java))
                    }
                },
            )
        }
    }
}

private data class NotificationDotsState(
    @StringRes val summaryRes: Int,
    val needsAccess: Boolean,
)

@Composable
private fun rememberNotificationDotsState(context: Context): NotificationDotsState {
    var state by remember { mutableStateOf(readNotificationDotsState(context)) }
    DisposableEffect(context) {
        val settingsCache = SettingsCache.INSTANCE.get(context)
        val update = { state = readNotificationDotsState(context) }
        val settingsListener = SettingsCache.OnChangeListener { _ -> update() }
        val listenerObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                update()
            }
        }
        settingsCache.register(NOTIFICATION_BADGING_URI, settingsListener)
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(NOTIFICATION_ENABLED_LISTENERS),
            false,
            listenerObserver,
        )
        update()
        onDispose {
            settingsCache.unregister(NOTIFICATION_BADGING_URI, settingsListener)
            context.contentResolver.unregisterContentObserver(listenerObserver)
        }
    }
    return state
}

private fun readNotificationDotsState(context: Context): NotificationDotsState {
    val enabled = SettingsCache.INSTANCE.get(context).getValue(NOTIFICATION_BADGING_URI)
    if (!enabled) {
        return NotificationDotsState(R.string.notification_dots_desc_off, false)
    }
    if (!hasNotificationAccess(context)) {
        return NotificationDotsState(R.string.title_missing_notification_access, true)
    }
    return NotificationDotsState(R.string.notification_dots_desc_on, false)
}

private fun hasNotificationAccess(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        NOTIFICATION_ENABLED_LISTENERS,
    ) ?: return false
    val listener = ComponentName(context, NotificationListener::class.java)
    return enabledListeners.contains(listener.flattenToString()) ||
            enabledListeners.contains(listener.flattenToShortString())
}

private fun notificationSettingsIntent(): Intent {
    val extras = Bundle().apply {
        putString(SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY, NOTIFICATION_BADGING_KEY)
    }
    return Intent(Settings.ACTION_NOTIFICATION_SETTINGS)
        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, extras)
}
