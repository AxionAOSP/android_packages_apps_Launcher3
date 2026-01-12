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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.axion.compose.preferences.*
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.search.UniversalSearchManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PulseSettingsScreen(
    onBack: () -> Unit,
    initialPage: Int = 0,
    viewModel: SettingsState = viewModel(factory = SettingsViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    var selectedPage by remember { mutableIntStateOf(initialPage) }
    
    val categories = listOf(
        stringResource(R.string.settings_category_home),
        stringResource(R.string.settings_category_drawer),
        stringResource(R.string.settings_category_behavior),
        "Search"
    )

    LaunchedEffect(pagerState.currentPage) {
        if (!pagerState.isScrollInProgress) {
            selectedPage = pagerState.currentPage
        }
    }

    LaunchedEffect(selectedPage) {
        if (selectedPage != pagerState.currentPage) {
             pagerState.animateScrollToPage(selectedPage)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                if (!isWideScreen) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.pulse_settings_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_close)
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            PulseHeader()
                            
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEachIndexed { index, title ->
                                    CategoryPill(
                                        text = title,
                                        isSelected = selectedPage == index,
                                        onClick = { selectedPage = index },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsPageContent(selectedPage, viewModel, context, PaddingValues(bottom = 24.dp))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                         Column(modifier = Modifier.fillMaxSize()) {
                            PulseHeader()
                            
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                SettingsPageContent(
                                    page = page,
                                    viewModel = viewModel,
                                    context = context,
                                    contentPadding = PaddingValues(bottom = 124.dp)
                                )
                            }
                        }
                        
                        PulseBottomBar(
                            categories = categories,
                            selectedIndex = selectedPage,
                            onCategorySelected = { index ->
                                selectedPage = index
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PulseBottomBar(
    categories: List<String>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val icon = when (index) {
                    0 -> Icons.Default.Home
                    1 -> Icons.Default.Apps
                    2 -> Icons.Default.TouchApp
                    3 -> Icons.Default.Search
                    else -> Icons.Default.Settings
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                        .clickable { onCategorySelected(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSelected) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val accentColorLight = MaterialTheme.colorScheme.primaryContainer
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(accentColor, accentColorLight)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceBright,
                            MaterialTheme.colorScheme.surfaceBright
                        )
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsPageContent(
    page: Int,
    viewModel: SettingsState,
    context: Context,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            when (page) {
                0 -> HomeScreenSettings(viewModel, context)
                1 -> AppDrawerSettings(viewModel)
                2 -> BehaviorSettings(viewModel)
                3 -> SearchSettingsPage(context)
            }
        }
    }
}

@Composable
fun SearchSettingsPage(context: Context) {
    val searchManager = remember { UniversalSearchManager(context) }
    val preferences by searchManager.preferences.collectAsState()
    
    DisposableEffect(Unit) {
        onDispose { searchManager.cleanup() }
    }

    PreferenceGroup(
        title = "Search Providers",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            SwitchPreference(
                title = "Contacts",
                summary = "Search your contacts",
                checked = preferences.searchContacts,
                onCheckedChange = { searchManager.setSearchContacts(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Messages",
                summary = "Search your SMS messages",
                checked = preferences.searchMessages,
                onCheckedChange = { searchManager.setSearchMessages(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Files",
                summary = "Search local files",
                checked = preferences.searchFiles,
                onCheckedChange = { searchManager.setSearchFiles(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Photos",
                summary = "Search device photos",
                checked = preferences.searchPhotos,
                onCheckedChange = { searchManager.setSearchPhotos(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Calendar",
                summary = "Search calendar events",
                checked = preferences.searchCalendar,
                onCheckedChange = { searchManager.setSearchCalendar(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Settings",
                summary = "Search system settings",
                checked = preferences.searchSettings,
                onCheckedChange = { searchManager.setSearchSettings(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Web Search",
                summary = "Allow web search actions",
                checked = preferences.searchWeb,
                onCheckedChange = { searchManager.setSearchWeb(it) }
            )
        }
    }
}

@Composable
fun PulseHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_gradient")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_offset"
    )
    
    val accentColor = MaterialTheme.colorScheme.primary
    val accentColorLight = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(32.dp))
    ) {
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentColor,
                            accentColorLight,
                            accentColor
                        ),
                        start = Offset(0f, gradientOffset * 1000),
                        end = Offset(1000f, (1 - gradientOffset) * 1000)
                    )
                )
        )

        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val maxRadius = size.minDimension / 2

            
            for (i in 0..2) {
                val progress = (gradientOffset + i * 0.33f) % 1f
                val radius = maxRadius * progress
                val alpha = (1f - progress) * 0.3f

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 32.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            
            drawCircle(
                color = accentColor,
                radius = 24.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }

        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Text(
                text = "Pulse",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White
            )
        }
    }
}
