package com.android.launcher3.allapps.compose

import com.android.launcher3.model.data.ItemInfo

class AllAppsComposeController {
    private var updateNumColumnsAction: ((Int) -> Unit)? = null
    private var updateTransitionProgressAction: ((Float) -> Unit)? = null
    private var updatePredictedAppsAction: ((List<ItemInfo>) -> Unit)? = null
    private var updatePrivateSpaceHiddenAction: ((Boolean) -> Unit)? = null
    private var updateIconSizingAction: ((Int, Int, Int) -> Unit)? = null

    fun setUpdateNumColumnsAction(action: (Int) -> Unit) {
        updateNumColumnsAction = action
    }

    fun setNumColumns(columns: Int) {
        updateNumColumnsAction?.invoke(columns)
    }

    fun setUpdateTransitionProgressAction(action: (Float) -> Unit) {
        updateTransitionProgressAction = action
    }

    fun setTransitionProgress(progress: Float) {
        updateTransitionProgressAction?.invoke(progress)
    }

    fun setUpdatePredictedAppsAction(action: (List<ItemInfo>) -> Unit) {
        updatePredictedAppsAction = action
    }

    fun updatePredictedApps(apps: List<ItemInfo>) {
        updatePredictedAppsAction?.invoke(apps)
    }

    fun setUpdatePrivateSpaceHiddenAction(action: (Boolean) -> Unit) {
        updatePrivateSpaceHiddenAction = action
    }

    fun setPrivateSpaceHidden(hidden: Boolean) {
        updatePrivateSpaceHiddenAction?.invoke(hidden)
    }

    fun setUpdateIconSizingAction(action: (Int, Int, Int) -> Unit) {
        updateIconSizingAction = action
    }

    fun setIconSizing(iconSizePx: Int, cellWidthPx: Int, cellHeightPx: Int) {
        updateIconSizingAction?.invoke(iconSizePx, cellWidthPx, cellHeightPx)
    }
}
