package com.android.launcher3.allapps.compose.shared.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.model.data.AppInfo

@Stable
data class AllAppsComposeState(
    val apps: List<AppInfo> = emptyList(),
    val workApps: List<AppInfo> = emptyList(),
    val privateApps: List<AppInfo> = emptyList(),
    val predictedApps: List<AppInfo> = emptyList(),
    val pinnedApps: List<AppInfo> = emptyList(),
    val adapterItems: List<AdapterItem> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = true,
    val currentTab: Int = TAB_PERSONAL,
    val hasWorkApps: Boolean = false,
    val hasPrivateApps: Boolean = false,
    val isPrivateSpaceLocked: Boolean = true,
    val isPrivateSpaceHidden: Boolean = false,
    val isWorkProfilePaused: Boolean = false,
    val numColumns: Int = 4,
    val iconSizePx: Int = 0,
    val cellWidthPx: Int = 0,
    val cellHeightPx: Int = 0,
    val isTablet: Boolean = false,
    val showLabels: Boolean = true,
    val showPredictions: Boolean = true,
    val sectionIndices: Map<String, Int> = emptyMap(),
    val iconRenderState: AllAppsIconRenderState = AllAppsIconRenderState(),
) {
    companion object {
        const val TAB_PERSONAL = 0
        const val TAB_WORK = 1
        const val TAB_PRIVATE = 2
        const val TAB_SEARCH = 3
    }
}

@Immutable
sealed class AllAppsComposeItem {
    data class AppItem(val appInfo: AppInfo, val section: String = "main") : AllAppsComposeItem()
    data class SectionHeader(val letter: String) : AllAppsComposeItem()
    object PredictionsHeader : AllAppsComposeItem()
    object PinnedAppsHeader : AllAppsComposeItem()
    object AllAppsHeader : AllAppsComposeItem()
    data class PrivateSpaceHeader(val isExpanded: Boolean) : AllAppsComposeItem()
    data class FolderItem(val category: AppCategory) : AllAppsComposeItem()
    object EmptySearchResult : AllAppsComposeItem()
}
