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
package com.android.launcher3.icons.customicon

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.compose.host.AxComposeView
import com.android.axion.compose.sheet.BottomSheetDialog
import com.android.axion.compose.theme.AxionTheme
import com.android.launcher3.Launcher
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 500L

object CustomIconPickerScreen {
    @JvmStatic
    fun show(
        launcher: Launcher,
        componentName: ComponentName,
        appLabel: String,
        appIcon: Drawable,
        user: UserHandle,
    ) {
        val repository = IconOverrideRepository.INSTANCE.get(launcher)
        val host = AxComposeView(launcher).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val dismissed = booleanArrayOf(false)
        val dismiss = {
            if (!dismissed[0]) {
                dismissed[0] = true
                launcher.dragLayer.removeView(host)
            }
        }
        launcher.dragLayer.addView(host)
        host.setContent {
            AxionTheme {
                CustomIconPickerContent(
                    componentName = componentName,
                    appLabel = appLabel,
                    appIcon = appIcon,
                    user = user,
                    repository = repository,
                    onDismiss = dismiss,
                )
            }
        }
    }
}

@Composable
private fun CustomIconPickerContent(
    componentName: ComponentName,
    appLabel: String,
    appIcon: Drawable,
    user: UserHandle,
    repository: IconOverrideRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedPack by remember { mutableStateOf<IconPackInfo?>(null) }
    BottomSheetDialog(onDismiss = onDismiss, heightFraction = 0.75f) {
        if (selectedPack == null) {
            PackListStage(
                context = context,
                appLabel = appLabel,
                appIcon = appIcon,
                hasOverride = repository.hasOverride(componentName),
                onPackSelected = { selectedPack = it },
                onReset = {
                    repository.clear(componentName, user)
                    onDismiss()
                },
            )
        } else {
            DrawableGridStage(
                context = context,
                pack = selectedPack!!,
                onBack = { selectedPack = null },
                onIconSelected = { drawableName ->
                    repository.set(
                        componentName,
                        IconOverride(selectedPack!!.packageName, drawableName),
                        user,
                    )
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun PackListStage(
    context: Context,
    appLabel: String,
    appIcon: Drawable,
    hasOverride: Boolean,
    onPackSelected: (IconPackInfo) -> Unit,
    onReset: () -> Unit,
) {
    var packs by remember { mutableStateOf<List<IconPackInfo>?>(null) }
    val appIconBitmap = remember(appIcon) { appIcon.toBitmap(56, 56).asImageBitmap() }
    LaunchedEffect(Unit) {
        packs = IconPackEnumerator.listInstalledIconPacks(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.custom_icon_picker_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            packs == null -> LoadingRow(height = 104)
            packs!!.isEmpty() -> EmptyText(R.string.custom_icon_no_packs)
            else -> packs!!.forEach { pack ->
                PackRow(pack = pack, onClick = { onPackSelected(pack) })
            }
        }
        if (hasOverride) {
            TextButton(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.custom_icon_reset),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PackRow(pack: IconPackInfo, onClick: () -> Unit) {
    val iconBitmap = remember(pack.icon) { pack.icon?.toBitmap(48, 48)?.asImageBitmap() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } ?: Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = pack.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DrawableGridStage(
    context: Context,
    pack: IconPackInfo,
    onBack: () -> Unit,
    onIconSelected: (String) -> Unit,
) {
    var drawables by remember(pack.packageName) {
        mutableStateOf<List<IconPackDrawableInfo>?>(null)
    }
    var query by remember(pack.packageName) { mutableStateOf("") }
    var appliedQuery by remember(pack.packageName) { mutableStateOf("") }

    LaunchedEffect(pack.packageName) {
        drawables = IconPackEnumerator.listDrawables(context, pack.packageName)
    }
    LaunchedEffect(query) {
        if (query == appliedQuery) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        appliedQuery = query
    }

    val filtered = drawables?.let { list ->
        val trimmed = appliedQuery.trim()
        if (trimmed.isEmpty()) list else list.filter {
            it.label.contains(trimmed, ignoreCase = true) ||
                it.drawableName.contains(trimmed, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.custom_icon_back))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = pack.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (!drawables.isNullOrEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.custom_icon_search_hint)) },
                shape = RoundedCornerShape(28.dp),
            )
        }
        when {
            drawables == null -> LoadingRow(height = 320)
            drawables!!.isEmpty() -> EmptyText(R.string.custom_icon_no_drawables)
            filtered!!.isEmpty() -> LoadingOrEmptySearch(query = query, appliedQuery = appliedQuery)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(320.dp),
            ) {
                items(filtered, key = { it.drawableName }) { item ->
                    DrawableCell(
                        context = context,
                        item = item,
                        onClick = { onIconSelected(item.drawableName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingOrEmptySearch(query: String, appliedQuery: String) {
    if (query.trim() != appliedQuery.trim()) {
        LoadingRow(height = 320)
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.custom_icon_no_search_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawableCell(
    context: Context,
    item: IconPackDrawableInfo,
    onClick: () -> Unit,
) {
    var bitmap by remember(item.packPackage, item.drawableName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.packPackage, item.drawableName) {
        bitmap = withContext(Dispatchers.IO) {
            IconPackDrawableResolver.loadDrawable(
                context,
                item.packPackage,
                item.drawableName,
                context.resources.displayMetrics.densityDpi,
            )?.toBitmap(64, 64)
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item.label,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun LoadingRow(height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyText(stringRes: Int) {
    Text(
        text = stringResource(stringRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
