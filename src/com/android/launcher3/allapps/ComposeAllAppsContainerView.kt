/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.os.Process
import android.util.Log
import android.os.UserHandle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.compose.ui.platform.ComposeView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.appprediction.AppsDividerView
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Themes

class ComposeAllAppsContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ActivityAllAppsContainerView<Launcher>(context, attrs, defStyleAttr) {

    private var stubHeader: FloatingHeaderView? = null

    private val controller: AllAppsComposeController
        get() = mActivityContext.activityComponent.allAppsComposeController

    val currentTransitionProgress: Float get() = mTransitionProgress

    override fun initContent() {
        mMainAdapterProvider = mSearchUiDelegate.createMainAdapterProvider()

        mAH.set(
            AdapterHolder.MAIN, AdapterHolder(
                AdapterHolder.MAIN,
                AlphabeticalAppsList(mActivityContext, mAllAppsStore, null, mPrivateProfileManager)
            )
        )
        mAH.set(
            AdapterHolder.WORK, AdapterHolder(
                AdapterHolder.WORK,
                AlphabeticalAppsList(mActivityContext, mAllAppsStore, mWorkManager, null)
            )
        )
        mAH.set(
            AdapterHolder.SEARCH, AdapterHolder(
                AdapterHolder.SEARCH,
                AlphabeticalAppsList(mActivityContext, null, null, null)
            )
        )

        layoutInflater.inflate(R.layout.all_apps_compose_content, this)
        mBottomSheetBackground = findViewById(R.id.bottom_sheet_background)
        clipChildren = false

        controller.attachContainer(this, mPrivateProfileManager, mWorkManager)
        controller.updateConfig(mActivityContext.deviceProfile)

        val host = findViewById<ViewGroup>(R.id.all_apps_compose_view)
        val cv = ComposeView(context)
        cv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        controller.setupComposeView(cv)
        // TODO: remove this hack
        try {
            host.addView(cv)
        } catch (e: Exception) {
            Log.e(TAG, "ComposeView attach failed, restarting", e)
            Process.killProcess(Process.myPid())
        }

        mSearchContainer = inflateSearchBar()
        mSearchContainer.visibility = GONE
        mSearchUiManager = mSearchContainer as SearchUiManager
    }

    override fun onFinishInflate() {
        val cornerRadius = Themes.getDialogCornerRadius(context)
        mBottomSheetCornerRadii = floatArrayOf(
            cornerRadius, cornerRadius,
            cornerRadius, cornerRadius,
            0f, 0f, 0f, 0f
        )
        updateBackgroundVisibility(mActivityContext.deviceProfile)

        findViewById<View>(R.id.bottom_sheet_handle)?.visibility = GONE
        findViewById<View>(R.id.bottom_sheet_handle_area)?.visibility = GONE

        mSearchUiManager.initializeSearch(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.detachContainer()
    }

    fun onProfileChanged(dp: DeviceProfile) {
        updateBackgroundVisibility(dp)
        val navBarScrimColor = Themes.getNavBarScrimColor(mActivityContext)
        if (mNavBarScrimPaint.color != navBarScrimColor) {
            mNavBarScrimPaint.color = navBarScrimColor
            invalidate()
        }
    }

    private var lastUiMode = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newUiMode != lastUiMode) {
            lastUiMode = newUiMode
            controller.onUiModeChanged()
        }
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        setInsets(mInsets)
        requestLayout()
    }

    override fun isUsingCompose(): Boolean = true

    override fun shouldContainerScroll(ev: MotionEvent): Boolean {
        return !controller.canScrollUp
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && !controller.canScrollUp) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onAllAppsTransitionProgress(progress: Float) {
        mTransitionProgress = progress
        controller.setTransitionProgressWithRefresh(progress)
        updateViewAlpha(mBottomSheetBackground, (progress - 0.7f) / 0.3f)
    }

    override fun getActiveRecyclerView(): AllAppsRecyclerView? = null

    override fun getAppsRecyclerViewContainer(): ViewGroup? = controller.getComposeView()

    override fun getSearchRecyclerView(): SearchRecyclerView? = null

    override fun getVisibleContainerView(): View? = controller.getComposeView()

    override fun getContentView(): View? = controller.getComposeView()

    override fun getHeaderBottom(): Int = 0

    override fun getHeaderProtectionHeight(): Float = 0f

    override fun setupHeader() {}

    override fun forceUpdateHeaderHeight(offset: Int) {}

    override fun getFloatingHeaderView(): FloatingHeaderView {
        return stubHeader ?: StubFloatingHeaderView(context).also { stubHeader = it }
    }

    override fun forAllRecyclerViews(consumer: androidx.core.util.Consumer<AllAppsRecyclerView>) {}

    override fun rebindAdapters(force: Boolean) {
        if (force) controller.updateConfig(mActivityContext.deviceProfile)
    }

    override fun updateSearchResultsVisibility() {}

    override fun updateHeaderScroll(scrolledOffset: Int) {}

    override fun animateToSearchState(goingToSearch: Boolean, durationMs: Long) {}

    override fun onActivePageChanged(currentActivePage: Int) {}

    override fun setScrollbarVisibility(visible: Boolean) {}

    override fun reset(animate: Boolean, exitSearch: Boolean, clearScrim: Boolean) {
        controller.resetState()
    }

    override fun addSpringFromFlingUpdateListener(
        animator: ValueAnimator, velocity: Float, progress: Float
    ) {}

    override fun getBottomSheetBackgroundColor(): Int = Color.TRANSPARENT

    override fun setInsets(insets: Rect) {
        mInsets.set(insets)
        InsettableFrameLayout.dispatchInsets(this, insets)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controller.handleBackKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun updateWorkUI() {}

    override fun onPredictionsUpdated(items: List<ItemInfo>) {
        controller.predictedApps = items
    }

    override fun drawOnScrimWithScaleAndBottomOffset(
        canvas: Canvas, scale: Float, bottomOffsetPx: Int
    ) {
        val panel = mBottomSheetBackground ?: return
        if (panel.visibility != VISIBLE) return
        val translationY = (panel.parent as View).translationY
        val topNoScale = panel.top + translationY
        val verticalScaleOffset = (1 - scale) * (panel.height - height / 2f)
        val horizontalScaleOffset = (1 - scale) * panel.width / 2f
        val left = getLeft() + panel.left.toFloat()
        val right = left + panel.width

        mHeaderPaint.color = bottomSheetBackgroundColor
        mHeaderPaint.alpha = (mHeaderPaint.alpha * mTransitionProgress).toInt()

        mTmpRectF.set(
            left + horizontalScaleOffset,
            topNoScale + verticalScaleOffset,
            right - horizontalScaleOffset,
            (panel.bottom + bottomOffsetPx).toFloat()
        )
        mTmpPath.reset()
        mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Path.Direction.CW)
        canvas.drawPath(mTmpPath, mHeaderPaint)
    }

    override fun getComposeIconForClose(packageName: String, user: UserHandle): View? =
        controller.getComposeIconForClose(packageName, user)

    override fun showFolderPickerForApp(componentName: String) {
        controller.showFolderPickerForApp(componentName)
    }

    private fun updateViewAlpha(view: View?, alpha: Float) {
        view ?: return
        val clamped = alpha.coerceIn(0f, 1f)
        view.alpha = clamped
        val targetLayer = if (clamped > 0f && clamped < 1f) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE
        if (view.layerType != targetLayer) {
            view.setLayerType(targetLayer, null)
        }
    }

    override fun computeNavBarScrimHeight(insets: WindowInsets): Int =
        insets.tappableElementInsets.bottom

    override fun isInAllApps(): Boolean =
        mActivityContext.stateManager.isInStableState(LauncherState.ALL_APPS)

    override fun shouldFloatingSearchBarBePillWhenUnfocused(): Boolean = false

    override fun getFloatingSearchBarRestingMarginBottom(): Int = -1

    override fun getFloatingSearchBarRestingMarginStart(): Int = 0

    override fun getFloatingSearchBarRestingMarginEnd(): Int = 0

    private class StubFloatingHeaderView(context: Context) : FloatingHeaderView(context) {
        private var stubPredictionRow: PredictionRowView<*>? = null
        private var stubDividerRow: AppsDividerView? = null

        init { visibility = GONE }

        @Suppress("UNCHECKED_CAST")
        override fun <T : FloatingHeaderRow> findFixedRowByType(type: Class<T>): T? = when (type) {
            PredictionRowView::class.java -> {
                val row = stubPredictionRow ?: PredictionRowView<Launcher>(context).also {
                    it.setup(this, FloatingHeaderRow.NO_ROWS, true)
                    it.visibility = GONE
                    stubPredictionRow = it
                }
                row as T
            }
            AppsDividerView::class.java -> {
                val row = stubDividerRow ?: AppsDividerView(context).also {
                    it.setup(this, FloatingHeaderRow.NO_ROWS, true)
                    it.visibility = GONE
                    stubDividerRow = it
                }
                row as T
            }
            else -> null
        }

        override fun setFloatingRowsCollapsed(collapsed: Boolean) {}
        override fun reset(animate: Boolean) {}
        override fun usingTabs(): Boolean = false
        override fun maybeSetTabVisibility(visibility: Int) {}
        override fun getFloatingRowsHeight(): Int = 0
        override fun onAttachedToWindow() {}
        override fun onDetachedFromWindow() {}
    }

    private companion object {
        const val TAG = "ComposeAllApps"
    }
}
