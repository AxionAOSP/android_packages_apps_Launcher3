package com.android.launcher3.settings.compose

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.compose.preferences.*
import com.android.axion.compose.scaffold.AxionScaffold
import com.android.launcher3.R

enum class SettingsDestination {
    ROOT, GENERAL, HOME_SCREEN, APP_DRAWER, SEARCH, GESTURES, ABOUT
}

data class SettingsCategory(
    val destination: SettingsDestination,
    val titleRes: Int,
    val summaryRes: Int,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseSettingsScreen(
    onBack: () -> Unit,
    initialDestination: SettingsDestination = SettingsDestination.ROOT,
    viewModel: SettingsState = viewModel(factory = SettingsViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    var currentDestination by rememberSaveable { mutableStateOf(initialDestination) }

    val categories = remember {
        listOf(
            SettingsCategory(SettingsDestination.GENERAL, R.string.settings_category_general, R.string.settings_category_general_summary, Icons.Outlined.Tune),
            SettingsCategory(SettingsDestination.HOME_SCREEN, R.string.settings_category_home, R.string.settings_category_home_summary, Icons.Outlined.Home),
            SettingsCategory(SettingsDestination.APP_DRAWER, R.string.settings_category_drawer, R.string.settings_category_drawer_summary, Icons.Outlined.Apps),
            SettingsCategory(SettingsDestination.SEARCH, R.string.settings_category_search, R.string.settings_category_search_summary, Icons.Outlined.Search),
            SettingsCategory(SettingsDestination.GESTURES, R.string.settings_category_gestures, R.string.settings_category_gestures_summary, Icons.Outlined.TouchApp),
            SettingsCategory(SettingsDestination.ABOUT, R.string.settings_category_about, R.string.settings_category_about_summary, Icons.Outlined.Info),
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            DualPaneSettings(
                categories = categories,
                currentDestination = currentDestination,
                onDestinationChanged = { currentDestination = it },
                onBack = onBack,
                viewModel = viewModel,
                context = context
            )
        } else {
            SinglePaneSettings(
                categories = categories,
                currentDestination = currentDestination,
                onDestinationChanged = { currentDestination = it },
                onBack = onBack,
                viewModel = viewModel,
                context = context
            )
        }
    }
}

@Composable
private fun SinglePaneSettings(
    categories: List<SettingsCategory>,
    currentDestination: SettingsDestination,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsState,
    context: Context
) {
    BackHandler(enabled = currentDestination != SettingsDestination.ROOT) {
        onDestinationChanged(SettingsDestination.ROOT)
    }

    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (targetState == SettingsDestination.ROOT) {
                (slideInHorizontally { -it } + fadeIn()) togetherWith
                    (slideOutHorizontally { it } + fadeOut())
            } else {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            }
        },
        label = "settings_nav"
    ) { destination ->
        when (destination) {
            SettingsDestination.ROOT -> SettingsRootScreen(
                categories = categories,
                onCategoryClick = onDestinationChanged,
                onBack = onBack
            )
            else -> SettingsDetailScreen(
                destination = destination,
                categories = categories,
                onBack = { onDestinationChanged(SettingsDestination.ROOT) },
                viewModel = viewModel,
                context = context
            )
        }
    }
}

@Composable
private fun DualPaneSettings(
    categories: List<SettingsCategory>,
    currentDestination: SettingsDestination,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsState,
    context: Context
) {
    val activeDestination = if (currentDestination == SettingsDestination.ROOT) {
        SettingsDestination.GENERAL
    } else {
        currentDestination
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.pulse_settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
            }
            item {
                PreferenceGroup {
                    categories.forEach { category ->
                        item {
                            ClickablePreference(
                                title = stringResource(category.titleRes),
                                summary = stringResource(category.summaryRes),
                                icon = category.icon,
                                iconBackgroundColor = if (activeDestination == category.destination)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                iconTint = if (activeDestination == category.destination)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    null,
                                onClick = { onDestinationChanged(category.destination) }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Box(modifier = Modifier.weight(0.5f)) {
            val category = categories.find { it.destination == activeDestination }
            val title = category?.let { stringResource(it.titleRes) } ?: ""

            AxionScaffold(
                title = title,
                onBackClick = onBack
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    item {
                        SettingsDetailContent(
                            destination = activeDestination,
                            viewModel = viewModel,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRootScreen(
    categories: List<SettingsCategory>,
    onCategoryClick: (SettingsDestination) -> Unit,
    onBack: () -> Unit
) {
    AxionScaffold(
        title = stringResource(R.string.pulse_settings_title),
        onBackClick = onBack,
        collapsedByDefault = false
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                PreferenceGroup {
                    categories.forEach { category ->
                        item {
                            ClickablePreference(
                                title = stringResource(category.titleRes),
                                summary = stringResource(category.summaryRes),
                                icon = category.icon,
                                iconBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                onClick = { onCategoryClick(category.destination) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailScreen(
    destination: SettingsDestination,
    categories: List<SettingsCategory>,
    onBack: () -> Unit,
    viewModel: SettingsState,
    context: Context
) {
    val category = categories.find { it.destination == destination }
    val title = category?.let { stringResource(it.titleRes) } ?: ""

    AxionScaffold(
        title = title,
        onBackClick = onBack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                SettingsDetailContent(
                    destination = destination,
                    viewModel = viewModel,
                    context = context
                )
            }
        }
    }
}

@Composable
fun SettingsDetailContent(
    destination: SettingsDestination,
    viewModel: SettingsState,
    context: Context
) {
    when (destination) {
        SettingsDestination.GENERAL -> GeneralSettings(viewModel, context)
        SettingsDestination.HOME_SCREEN -> HomeScreenSettings(viewModel, context)
        SettingsDestination.APP_DRAWER -> AppDrawerSettings(viewModel)
        SettingsDestination.SEARCH -> SearchSettingsPage(context)
        SettingsDestination.GESTURES -> GestureSettings(viewModel)
        SettingsDestination.ABOUT -> AboutSettings(context)
        else -> {}
    }
}
