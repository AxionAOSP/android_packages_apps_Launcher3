package com.android.launcher3.allapps.compose

class AllAppsComposeController {
    private var updateNumColumnsAction: ((Int) -> Unit)? = null
    private var updateTransitionProgressAction: ((Float) -> Unit)? = null

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
}
