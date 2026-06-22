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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.compose.navigation.AxRouteAnimatedContent
import com.android.axion.compose.navigation.rememberAxRouteNavigator
import com.android.axion.compose.scaffold.AxionScaffold
import com.android.launcher3.settings.SettingsActivity

@Composable
internal fun HomeSettingsScreen(
    activity: SettingsActivity,
    initialRoute: String,
) {
    val navigator = rememberAxRouteNavigator(
        initialRoute = initialRoute,
        parentRoute = ::parentRoute,
    )
    val route = navigator.route ?: HomeSettingsRoutes.ROOT

    fun goBack() {
        if (!navigator.goBack()) {
            activity.finish()
        }
    }

    BackHandler(onBack = ::goBack)
    val scrollState = rememberScrollState()

    LaunchedEffect(route) {
        scrollState.scrollTo(0)
    }

    AxionScaffold(
        title = stringResource(routeTitle(route)),
        onBackClick = ::goBack,
        collapsedByDefault = route != HomeSettingsRoutes.ROOT,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AxRouteAnimatedContent(
                targetRoute = route,
                isForward = navigator.isForward,
                modifier = Modifier.fillMaxWidth(),
                label = "homeSettingsRoute",
            ) { targetRoute ->
                SettingsContentColumn {
                    when (targetRoute) {
                        HomeSettingsRoutes.ROOT -> DashboardScreen(activity, navigator::navigateTo)
                        HomeSettingsRoutes.GENERAL -> GeneralScreen(activity)
                        HomeSettingsRoutes.HOME -> HomeScreen(activity, navigator::navigateTo)
                        HomeSettingsRoutes.HOME_GRID -> HomeGridScreen()
                        HomeSettingsRoutes.ALL_APPS -> AllAppsDrawerScreen(navigator::navigateTo)
                        HomeSettingsRoutes.ALL_APPS_FOLDERS -> AllAppsFoldersScreen()
                        HomeSettingsRoutes.SEARCH -> SearchScreen(activity)
                        HomeSettingsRoutes.NOTIFICATIONS -> NotificationsScreen(activity)
                        HomeSettingsRoutes.PRIVACY -> PrivacyScreen(activity)
                        HomeSettingsRoutes.BACKUP -> BackupScreen()
                        else -> DashboardScreen(activity, navigator::navigateTo)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsContentColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}
