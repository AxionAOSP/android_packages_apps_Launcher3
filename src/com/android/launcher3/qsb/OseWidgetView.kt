/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.android.launcher3.Hotseat
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem

class OseWidgetView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), View.OnLongClickListener {

    init {
        setOnLongClickListener(this)
    }

    private val oseWidgetManager = context.appComponent.oseWidgetManager
    private val launcherPrefs = LauncherPrefs.get(context)
    @VisibleForTesting val closeActions = RunnableList()
    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)
    private var suppressClickAfterLongPress = false
    private val hotseatSearchListener = LauncherPrefChangeListener { key ->
        if (key == LauncherPrefsExt.HOTSEAT_SEARCH_BAR.sharedPrefKey ||
            key == LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER.sharedPrefKey) {
            syncSearchVisibility()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcherPrefs.addListener(
            hotseatSearchListener,
            LauncherPrefsExt.HOTSEAT_SEARCH_BAR,
            LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER,
        )
        syncSearchVisibility()
    }

    @VisibleForTesting
    fun attachedToWindow() {
        closeActions.executeAllAndClear()
        closeActions.add(
            oseWidgetManager.providerInfo.forEach(MAIN_EXECUTOR, this::bindWidgetView)::close
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launcherPrefs.removeListener(
            hotseatSearchListener,
            LauncherPrefsExt.HOTSEAT_SEARCH_BAR,
            LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER,
        )
        detachedFromWindow()
    }

    @VisibleForTesting
    fun detachedFromWindow() {
        closeActions.executeAllAndClear()
    }

    private fun syncSearchVisibility() {
        if (OseWidgetManager.isSearchBarEnabled(launcherPrefs)) {
            visibility = View.VISIBLE
            if (isAttachedToWindow) {
                attachedToWindow()
            }
        } else {
            visibility = View.GONE
            removeAllViews()
            tag = getTagInfo(null, INVALID_APPWIDGET_ID)
            detachedFromWindow()
        }
        (parent as? Hotseat)?.refreshQsbLayout()
    }

    private fun bindWidgetView(provider: AppWidgetProviderInfo?) {
        removeAllViews()
        tag = getTagInfo(provider, oseWidgetManager.getActiveWidgetId())
        if (!OseWidgetManager.isSearchBarEnabled(launcherPrefs)) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        val widgetView = provider?.let { oseWidgetManager.createWidgetView(context) }
        val content = widgetView ?: getDefaultView()
        content.setOnLongClickListener { onLongClick(it) }
        addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL -> {
                suppressClickAfterLongPress = false
            }
            MotionEvent.ACTION_UP -> if (suppressClickAfterLongPress) {
                post { suppressClickAfterLongPress = false }
                return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun getDefaultView(): View =
        View.inflate(context, R.layout.qsb_default_view, null).apply {
            val clickListener = View.OnClickListener { openSearch(it) }
            setOnClickListener(clickListener)
            findViewById<View>(R.id.btn_qsb_search).setOnClickListener(clickListener)
        }

    private fun openSearch(view: View) {
        val oseManager = context.appComponent.getOseManager()
        val oseInfo = oseManager.oseInfo.value
        val osePkg: String? =
            when {
                oseInfo.isOseConfigured -> oseInfo.pkg
                else -> null
            }
        osePkg?.run {
            val searchIntent =
                Intent(Intent.ACTION_SEARCH)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                    .setPackage(this)
            activityContext.startActivitySafely(view, searchIntent, null)
        } ?: openDefaultBrowser(view)
    }

    fun openDefaultBrowser(view: View) {
        val browserIntent =
            Intent(Intent.ACTION_VIEW)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
        browserIntent.setData("about:blank".toUri())
        activityContext.startActivitySafely(view, browserIntent, null)
    }

    override fun onLongClick(view: View?): Boolean {
        val oseWidgetOptionsProvider =
            activityContext.activityComponent.getOseWidgetOptionsProvider()
        val optionItems = oseWidgetOptionsProvider.getOptionItems()
        if (optionItems.isEmpty()) return false

        val bounds =
            RectF(Utilities.getViewBounds(this)).apply {
                left = centerX()
                right = centerX()
            }
        showOptionsPopup(bounds, optionItems)
        suppressClickAfterLongPress = true
        return true
    }

    @VisibleForTesting
    fun showOptionsPopup(bounds: RectF, optionItems: List<OptionItem>) {
        OptionsPopupView.showNoReturn(activityContext, bounds, optionItems, true)
    }

    private class QsbItemInfo : ItemInfo() {

        override fun getStableId() = STABLE_ID
    }

    companion object {
        private val STABLE_ID = Object()

        private fun getTagInfo(provider: AppWidgetProviderInfo?, widgetId: Int): ItemInfo {
            val info =
                if (provider != null && widgetId != INVALID_APPWIDGET_ID) {
                    LauncherAppWidgetInfo(widgetId, provider.provider)
                } else {
                    QsbItemInfo()
                }
            info.id = R.id.search_container_hotseat
            info.container = Favorites.CONTAINER_HOTSEAT
            return info
        }
    }
}
