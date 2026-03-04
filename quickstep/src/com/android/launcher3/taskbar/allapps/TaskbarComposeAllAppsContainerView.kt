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

package com.android.launcher3.taskbar.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.compose.ui.platform.ComposeView
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsComposeController
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.FloatingHeaderRow
import com.android.launcher3.allapps.FloatingHeaderView
import com.android.launcher3.allapps.SearchRecyclerView
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.appprediction.AppsDividerView
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext

class TaskbarComposeAllAppsContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TaskbarAllAppsContainerView(context, attrs, defStyleAttr) {

    private var stubHeader: FloatingHeaderView? = null

    private val controller: AllAppsComposeController
        get() = mActivityContext.activityComponent.allAppsComposeController

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

        controller.attachTaskbarContainer(this)
        controller.updateConfig(mActivityContext.deviceProfile)

        val host = findViewById<ViewGroup>(R.id.all_apps_compose_view)
        val cv = ComposeView(context)
        cv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        controller.setupComposeView(cv)
        try {
            host.addView(cv)
        } catch (e: Exception) {
            Log.e(TAG, "ComposeView attach failed", e)
        }

        mSearchContainer = inflateSearchBar()
        mSearchContainer.visibility = GONE
        mSearchUiManager = mSearchContainer as SearchUiManager
    }

    override fun onFinishInflate() {
        mSearchUiManager.initializeSearch(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.detachContainer()
    }

    override fun isUsingCompose(): Boolean = true

    override fun shouldContainerScroll(ev: MotionEvent): Boolean {
        return !controller.canScrollUp
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
    ) {}

    override fun computeNavBarScrimHeight(insets: WindowInsets): Int =
        insets.tappableElementInsets.bottom

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
                val row = stubPredictionRow ?: PredictionRowView<TaskbarOverlayContext>(context).also {
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
        const val TAG = "TaskbarComposeAllApps"
    }
}
