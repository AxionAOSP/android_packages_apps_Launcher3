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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.BasePreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.util.PackageManagerUtils
import com.android.launcher3.R
import com.android.launcher3.util.painterResource as drawablePainterResource

@Composable
internal fun AboutScreen() {
    val context = LocalContext.current
    val appVersion = remember(context) { launcherVersion(context) }
    AboutHeader(
        appVersion = appVersion,
        onGitHubClick = { openIntent(context, viewUriIntent(AXION_LAUNCHER_SOURCE_URL)) },
        onTranslateClick = { openIntent(context, viewUriIntent(AXION_TRANSLATE_URL)) },
        onDonateClick = { openIntent(context, viewUriIntent(AXION_DONATE_URL)) },
    )
    PreferenceGroup {
        item {
            AboutInfoPreference(
                titleRes = R.string.home_settings_about_version_title,
                summary = appVersion,
                iconRes = R.drawable.ic_info_no_shadow,
            )
        }
        item {
            AboutInfoPreference(
                titleRes = R.string.home_settings_about_based_on_title,
                summary = stringResource(R.string.home_settings_about_based_on_summary),
                iconRes = R.drawable.ic_home_settings_home,
            )
        }
    }
    PreferenceGroup(title = stringResource(R.string.home_settings_about_contributors_category)) {
        aboutContributors.forEach { contributor ->
            item { ContributorPreference(contributor) }
        }
    }
}

@Composable
private fun AboutHeader(
    appVersion: String,
    onGitHubClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onDonateClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LauncherAppIcon()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.lineageos_app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = appVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutActionButton(
                titleRes = R.string.home_settings_about_github,
                iconRes = R.drawable.ic_github,
                onClick = onGitHubClick,
            )
            AboutActionButton(
                titleRes = R.string.home_settings_about_translate,
                iconRes = R.drawable.ic_translate,
                onClick = onTranslateClick,
            )
            AboutActionButton(
                titleRes = R.string.home_settings_about_donate,
                iconRes = R.drawable.ic_help,
                onClick = onDonateClick,
            )
        }
    }
}

@Composable
private fun LauncherAppIcon() {
    val context = LocalContext.current
    val icon = remember(context) {
        PackageManagerUtils.getApplicationIconOrDefault(context, context.packageName)
    }
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = drawablePainterResource(icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AboutActionButton(
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    val title = stringResource(titleRes)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .sizeIn(minWidth = 72.dp, minHeight = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutInfoPreference(
    @StringRes titleRes: Int,
    summary: String,
    @DrawableRes iconRes: Int,
) {
    BasePreference(
        title = stringResource(titleRes),
        summary = summary,
        customIcon = preferenceIcon(iconRes),
    )
}

@Composable
private fun ContributorPreference(contributor: AboutContributor) {
    BasePreference(
        title = stringResource(contributor.nameRes),
        summary = stringResource(contributor.roleRes),
        customIcon = { ContributorIcon(contributor) },
    )
}

@Composable
private fun ContributorIcon(contributor: AboutContributor) {
    if (contributor.avatarRes != 0) {
        Image(
            painter = painterResource(contributor.avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(contributor.nameRes).take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class AboutContributor(
    @StringRes val nameRes: Int,
    @StringRes val roleRes: Int,
    @DrawableRes val avatarRes: Int = 0,
)

private val aboutContributors = listOf(
    AboutContributor(
        R.string.home_settings_about_contributor_rmp22,
        R.string.home_settings_about_role_development,
        R.drawable.contributor_rmp22,
    ),
)

private fun openIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        showToast(context, context.getString(R.string.activity_not_found))
    } catch (e: SecurityException) {
        showToast(context, context.getString(R.string.activity_not_found))
    }
}

private fun viewUriIntent(uri: String): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
}

private fun launcherVersion(context: Context): String {
    return PackageManagerUtils.getPackageVersionName(context, context.packageName)
        ?: context.getString(R.string.home_settings_about_unknown_version)
}
