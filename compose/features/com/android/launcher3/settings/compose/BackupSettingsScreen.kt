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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.R
import com.android.launcher3.settings.AxLauncherBackupController

@Composable
internal fun BackupScreen() {
    val context = LocalContext.current
    val controller = remember(context) { AxLauncherBackupController.INSTANCE.get(context) }
    val exportSuccess = stringResource(R.string.home_settings_backup_export_success)
    val exportFailure = stringResource(R.string.home_settings_backup_export_failure)
    val importSuccess = stringResource(R.string.home_settings_backup_import_success)
    val importFailure = stringResource(R.string.home_settings_backup_import_failure)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml"),
    ) { uri: Uri? ->
        uri?.let {
            controller.exportTo(it) { success ->
                showToast(context, if (success) exportSuccess else exportFailure)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            controller.importFrom(it) { success ->
                showToast(context, if (success) importSuccess else importFailure)
            }
        }
    }
    PreferenceGroup {
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_backup_export_title,
                summaryRes = R.string.home_settings_backup_export_summary,
                onClick = { exportLauncher.launch(LAUNCHER_LAYOUT_FILE_NAME) },
            )
        }
        item {
            CategoryPreference(
                titleRes = R.string.home_settings_backup_import_title,
                summaryRes = R.string.home_settings_backup_import_summary,
                onClick = { importLauncher.launch(arrayOf("text/xml")) },
            )
        }
    }
}
