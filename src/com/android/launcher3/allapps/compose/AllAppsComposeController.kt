package com.android.launcher3.allapps.compose

import com.android.launcher3.model.data.ItemInfo

class AllAppsComposeController {
    private var numColumns: Int = 4
    private var transitionProgress: Float = 0f
    private var predictedApps: List<ItemInfo> = emptyList()
    private var isPrivateSpaceHidden: Boolean = false
    private var iconSizePx: Int = 0
    private var cellWidthPx: Int = 0
    private var cellHeightPx: Int = 0

    private var updateNumColumnsAction: ((Int) -> Unit)? = null
    private var updateTransitionProgressAction: ((Float) -> Unit)? = null
    private var updatePredictedAppsAction: ((List<ItemInfo>) -> Unit)? = null
    private var updatePrivateSpaceHiddenAction: ((Boolean) -> Unit)? = null
    private var updateIconSizingAction: ((Int, Int, Int) -> Unit)? = null

    fun setUpdateNumColumnsAction(action: (Int) -> Unit) {
        updateNumColumnsAction = action
        action(numColumns)
    }

    fun setNumColumns(columns: Int) {
        numColumns = columns
        updateNumColumnsAction?.invoke(columns)
    }

    fun setUpdateTransitionProgressAction(action: (Float) -> Unit) {
        updateTransitionProgressAction = action
        action(transitionProgress)
    }

    fun setTransitionProgress(progress: Float) {
        transitionProgress = progress
        updateTransitionProgressAction?.invoke(progress)
    }

    fun setUpdatePredictedAppsAction(action: (List<ItemInfo>) -> Unit) {
        updatePredictedAppsAction = action
        action(predictedApps)
    }

    fun updatePredictedApps(apps: List<ItemInfo>) {
        predictedApps = apps
        updatePredictedAppsAction?.invoke(apps)
    }

    fun setUpdatePrivateSpaceHiddenAction(action: (Boolean) -> Unit) {
        updatePrivateSpaceHiddenAction = action
        action(isPrivateSpaceHidden)
    }

    fun setPrivateSpaceHidden(hidden: Boolean) {
        isPrivateSpaceHidden = hidden
        updatePrivateSpaceHiddenAction?.invoke(hidden)
    }

    fun setUpdateIconSizingAction(action: (Int, Int, Int) -> Unit) {
        updateIconSizingAction = action
        action(iconSizePx, cellWidthPx, cellHeightPx)
    }

    fun setIconSizing(iconSizePx: Int, cellWidthPx: Int, cellHeightPx: Int) {
        this.iconSizePx = iconSizePx
        this.cellWidthPx = cellWidthPx
        this.cellHeightPx = cellHeightPx
        updateIconSizingAction?.invoke(iconSizePx, cellWidthPx, cellHeightPx)
    }
}
