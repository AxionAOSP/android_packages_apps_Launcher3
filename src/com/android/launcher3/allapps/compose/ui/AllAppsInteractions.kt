package com.android.launcher3.allapps.compose.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import com.android.launcher3.allapps.AllAppsComposeController
import com.android.launcher3.allapps.compose.shared.model.ComposeIconInfo
import com.android.launcher3.model.data.AppInfo

@Stable
class AllAppsInteractions(
    val controller: AllAppsComposeController? = null,
    val onAppClick: (ComposeIconInfo) -> Unit = {},
    val onAppLongClick: (ComposeIconInfo) -> Unit = {},
    val onAppDragStart: ((ComposeIconInfo) -> Unit)? = null,
    val onAppDragMove: ((screenX: Float, screenY: Float) -> Unit)? = null,
    val onAppDragEnd: ((screenX: Float, screenY: Float) -> Unit)? = null,
    val onAppClickedFromFolder: (AppInfo) -> Unit = {},
    val onPrivateSpaceClicked: (Boolean) -> Unit = {},
    val onFolderExpandedChanged: (Boolean) -> Unit = {}
) {
    fun withoutController() = AllAppsInteractions(
        controller = null,
        onAppClick = onAppClick,
        onAppLongClick = onAppLongClick,
        onAppDragStart = onAppDragStart,
        onAppDragMove = onAppDragMove,
        onAppDragEnd = onAppDragEnd,
        onAppClickedFromFolder = onAppClickedFromFolder,
        onPrivateSpaceClicked = onPrivateSpaceClicked,
        onFolderExpandedChanged = onFolderExpandedChanged
    )
}

@Immutable
data class IconConfig(val themed: Boolean = false, val version: Int = 0)

val LocalAllAppsInteractions = compositionLocalOf { AllAppsInteractions() }
val LocalPagerSwiping = compositionLocalOf { false }
val LocalIconConfig = compositionLocalOf { IconConfig() }
val LocalSectionId = compositionLocalOf { "unknown" }

