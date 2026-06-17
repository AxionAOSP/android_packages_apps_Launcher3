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
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R

@Composable
internal fun SearchScreen(activity: Activity) {
    val suggestionsVisible = rememberPackageEnabled(SUGGESTIONS_PACKAGE)
    PreferenceGroup(title = stringResource(R.string.search_app_drawer_category)) {
        item {
            BooleanPreference(
                item = LauncherPrefsExt.DRAWER_OPEN_KEYBOARD,
                titleRes = R.string.drawer_open_keyboard_title,
                summaryRes = R.string.drawer_open_keyboard_summary,
            )
        }
    }
    if (suggestionsVisible) {
        PreferenceGroup(title = stringResource(R.string.home_settings_suggestions_category)) {
            item {
                CategoryPreference(
                    titleRes = R.string.pref_suggestions_title,
                    summaryRes = R.string.pref_suggestions_summary,
                    onClick = { activity.startActivity(Intent(CONTENT_SUGGESTIONS_ACTION)) },
                )
            }
        }
    }
}
