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
package com.android.launcher3

import com.android.launcher3.LauncherPrefs.Companion.backedUpItem

object LauncherPrefsExt {
    @JvmField
    val ENABLE_TWOLINE_ALLAPPS_TOGGLE = backedUpItem("pref_enable_two_line_toggle", false)
    @JvmField val ENABLE_MINUS_ONE =
        backedUpItem("pref_enable_minus_one", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val ADD_ICON_TO_HOME =
        backedUpItem(
            SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY,
            true,
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val WORKSPACE_LOCK =
        backedUpItem("pref_workspace_lock", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val ALLAPPS_THEMED_ICONS =
        backedUpItem("pref_allapps_themed_icons", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val DRAWER_OPEN_KEYBOARD =
        backedUpItem("pref_drawer_open_keyboard", false, EncryptionType.SECURE_SETTINGS)
    @JvmField val SHOW_DESKTOP_LABELS =
        backedUpItem("pref_desktop_show_labels", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val SHOW_DRAWER_LABELS =
        backedUpItem("pref_drawer_show_labels", true, EncryptionType.SECURE_SETTINGS)
    @JvmField val PINNED_APPS =
        backedUpItem(
            "pref_all_apps_pinned_apps",
            emptySet<String>(),
            EncryptionType.SECURE_SETTINGS,
        )
    @JvmField val SLEEP_GESTURE =
        backedUpItem("pref_sleep_gesture", false, EncryptionType.SECURE_SETTINGS)
}
