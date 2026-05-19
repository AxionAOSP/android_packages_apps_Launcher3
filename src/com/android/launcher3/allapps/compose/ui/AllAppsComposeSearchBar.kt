package com.android.launcher3.allapps.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.axion.blur.AxBlurSurfaceDefaults
import com.android.axion.blur.axBlurBackground
import com.android.axion.blur.rememberAxBlurEnabled
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSpec

private const val MAX_DRAWER_OPACITY = 255

@Composable
fun AllAppsComposeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    shouldAutoFocus: Boolean = false,
    focusTrigger: Int = 0,
    onSearchSubmit: (String) -> Unit = {},
    hasTopResult: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    applyBottomInsets: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    val rowInteractionSource = remember { MutableInteractionSource() }
    val drawerOpacity = rememberDrawerOpacity().coerceIn(0, MAX_DRAWER_OPACITY)
    val blurSettingsSpec = remember { AxBackdropBlurSettingsSpec.launcher() }
    val blurEnabled = rememberAxBlurEnabled(blurSettingsSpec) && drawerOpacity < MAX_DRAWER_OPACITY
    val drawerAlpha = drawerOpacity / MAX_DRAWER_OPACITY.toFloat()
    val blurFallbackColor = AxBlurSurfaceDefaults.surfaceColor(drawerAlpha)
    val backgroundColor = if (blurEnabled) blurFallbackColor else containerColor
    val tintColor = AxBlurSurfaceDefaults.tintColor(drawerAlpha)

    LaunchedEffect(focusTrigger) {
        if (shouldAutoFocus && focusTrigger > 0) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
            .then(if (applyBottomInsets) Modifier.navigationBarsPadding().imePadding() else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TopSearchBarHeight)
                .clip(CircleShape)
                .axBlurBackground(
                    enabled = blurEnabled,
                    fallbackColor = backgroundColor,
                    tintColor = tintColor,
                    settingsSpec = blurSettingsSpec,
                )
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = { focusRequester.requestFocus() }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (hasTopResult) ImeAction.Go else ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchSubmit(query) },
                        onGo = { onSearchSubmit(query) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
