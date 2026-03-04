package com.android.launcher3.allapps.compose.shared.model

import android.content.Intent
import android.os.UserHandle
import androidx.compose.runtime.Stable
import com.android.launcher3.model.data.AppInfo

@Stable
interface AllAppsComposeCallbacks {
    fun onAppClicked(iconInfo: ComposeIconInfo)
    fun onAppClickedFromFolder(appInfo: AppInfo)
    fun onAppLongClicked(iconInfo: ComposeIconInfo)
    fun onAppDragStart(iconInfo: ComposeIconInfo)
    fun onAppDragMove(screenX: Float, screenY: Float)
    fun onAppDragEnd(screenX: Float, screenY: Float)
    fun onSearchQueryChanged(query: String)
    fun onTabSelected(tab: Int)
    fun onFolderExpandedChanged(expanded: Boolean)
    fun setDismissFolderHandler(handler: (() -> Unit)?)
    fun startActivity(intent: Intent)
    fun startShortcut(packageName: String, shortcutId: String, user: UserHandle)
    fun requestContactsPermission()
    fun requestSmsPermission()
    fun requestFilePermission()
    fun requestCalendarPermission()
    fun onSearchExpandedChanged(expanded: Boolean)
    fun onPrivateSpaceClicked(isLocked: Boolean)
    fun onPrivateSpaceSettingsClicked()
    fun onPrivateSpaceInstallAppClicked()
    fun onWorkProfileToggle(enable: Boolean)
    fun setShowFolderPickerHandler(handler: ((String) -> Unit)?)
}

