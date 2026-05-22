package com.android.launcher3.allapps.compose.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.R

@Composable
fun AllAppsComposeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onMenuClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shouldAutoFocus: Boolean = false,
    focusTrigger: Int = 0,
    onSearchSubmit: (String) -> Unit = {},
    hasTopResult: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    applyBottomInsets: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    val rowInteractionSource = remember { MutableInteractionSource() }
    val legacyLayout = LocalAllAppsLegacyLayout.current
    val searchBarHeight = if (legacyLayout) LegacySearchBarHeight else TopSearchBarHeight
    val searchBarHorizontalPadding = if (legacyLayout) 12.dp else 16.dp
    val searchBarVerticalPadding = if (legacyLayout) 0.dp else 10.dp
    val searchTextSize = if (legacyLayout) 20.sp else 16.sp
    val contentColor = if (legacyLayout) {
        allAppsThemeColor(R.attr.allAppsSearchTextColor)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

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
                .heightIn(min = searchBarHeight)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = { focusRequester.requestFocus() }
                )
                .padding(
                    horizontal = searchBarHorizontalPadding,
                    vertical = searchBarVerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = searchTextSize),
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        fontSize = searchTextSize,
                        color = contentColor
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
                        contentDescription = stringResource(R.string.drawer_search_clear),
                        tint = contentColor
                    )
                }
            }

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.settings_button_text),
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
