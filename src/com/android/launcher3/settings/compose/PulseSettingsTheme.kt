package com.android.launcher3.settings.compose

import androidx.compose.runtime.Composable
import com.android.axion.compose.theme.AxionTheme

@Composable
fun PulseTheme(
    content: @Composable () -> Unit
) {
    AxionTheme(content = content)
}
