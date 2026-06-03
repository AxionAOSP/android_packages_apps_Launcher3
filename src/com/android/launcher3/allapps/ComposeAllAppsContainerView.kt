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
import android.graphics.Rect
import android.os.UserHandle
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import com.android.axion.compose.host.AxComposeView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.appprediction.AppsDividerView
import com.android.launcher3.appprediction.PredictionRowView
import com.android.launcher3.allapps.compose.ui.drawerScrimAlpha
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Themes

class ComposeAllAppsContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ActivityAllAppsContainerView<Launcher>(context, attrs, defStyleAttr) {

    private var stubHeader: FloatingHeaderView? = null

    private var controllerCache: AllAppsComposeController? = null
    private val controller: AllAppsComposeController
        get() {
            val cached = controllerCache
            if (cached != null) return cached
            return mActivityContext.activityComponent.allAppsComposeController.also {
                controllerCache = it
            }
        }

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

        val cv = findViewById<AxComposeView>(R.id.all_apps_compose_view)
        Log.d(TAG, "initContent: inflated AxComposeView=$cv, attaching to controller")
        controller.attachContainer(this, cv, mPrivateProfileManager, mWorkManager)
        controller.updateConfig(mActivityContext.deviceProfile)
        cv.setContent { controller.Content() }

        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        cv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        cv.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS

        setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
        cv.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)

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
        Log.d(TAG, "onDetachedFromWindow")
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
            Log.d(TAG, "onConfigurationChanged: uiMode $lastUiMode -> $newUiMode")
            lastUiMode = newUiMode
            controller.onUiModeChanged(newConfig)
        }
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        setInsets(mInsets)
        requestLayout()
    }

    override fun updateBackgroundVisibility(deviceProfile: DeviceProfile) {
        mBottomSheetBackground.visibility = GONE
    }

    override fun isUsingCompose(): Boolean = true

    override fun shouldContainerScroll(ev: MotionEvent): Boolean {
        return canContainerHandleSwipe()
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && canContainerHandleSwipe() && !controller.isLongPressing) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun canContainerHandleSwipe(): Boolean =
        mTransitionProgress < 1f || !controller.canScrollUp

    override fun onAllAppsTransitionProgress(progress: Float) {
        updateAllAppsTransitionProgress(progress)
        controller.setTransitionProgressWithRefresh(progress)
        updateViewAlpha(mBottomSheetBackground, drawerAlpha(progress))
    }

    override fun getActiveRecyclerView(): AllAppsRecyclerView? = null

    override fun getAppsRecyclerViewContainer(): ViewGroup? = controller.getComposeView()

    override fun getSearchRecyclerView(): SearchRecyclerView? = null

    override fun getVisibleContainerView(): View? = controller.getComposeView()

    override fun getContentView(): View? = controller.getComposeView()

    override fun getHeaderBottom(): Int = 0

    override fun invalidateHeader() {}

    protected override fun shouldInvalidateHeaderOnTranslation(): Boolean = false

    override fun updateAllAppsColors() {
        controller.onAllAppsColorsChanged()
    }

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

    override fun getBottomSheetBackgroundColor(): Int = allAppsBottomSheetBackgroundColor(context)

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

    override fun getComposeIconForClose(packageName: String, user: UserHandle): View? =
        controller.getComposeIconForClose(packageName, user)

    override fun showFolderPickerForApp(componentName: String) {
        controller.showFolderPickerForApp(componentName)
    }

    private fun updateViewAlpha(view: View?, alpha: Float) {
        view ?: return
        if (view.visibility == GONE) return
        val clamped = alpha.coerceIn(0f, 1f)
        view.alpha = clamped
        val targetLayer = if (clamped > 0f && clamped < 1f) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE
        if (view.layerType != targetLayer) {
            view.setLayerType(targetLayer, null)
        }
    }

    private fun drawerAlpha(progress: Float): Float =
        drawerScrimAlpha(progress, controller.isTransitionCollapsing)

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
