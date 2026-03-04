package com.android.launcher3.allapps.compose.shared.model

import com.android.launcher3.model.data.AppInfo

data class AppCategory(
    val id: Int,
    val name: String,
    val apps: List<AppInfo>,
    val isCustom: Boolean = false
)

