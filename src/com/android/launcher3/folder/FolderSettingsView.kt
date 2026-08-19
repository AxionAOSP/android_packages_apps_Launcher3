/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.launcher3.folder

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.launcher3.AbstractFloatingView.TYPE_FOLDER_SETTINGS
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderPreviewSettings
import com.android.launcher3.views.AbstractSlideInView
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class FolderSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractSlideInView<Launcher>(context, attrs, defStyleAttr), Insettable {
    private lateinit var folderIcon: FolderIcon
    private lateinit var scaleSlider: Slider
    private lateinit var scaleValue: TextView
    private lateinit var paddingSlider: Slider
    private lateinit var paddingValue: TextView
    private lateinit var minimumGapSlider: Slider
    private lateinit var minimumGapValue: TextView

    private var hasUncommittedChanges = false

    override fun setInsets(insets: Rect) {
        val contentPadding =
            resources.getDimensionPixelSize(R.dimen.folder_settings_content_padding)

        mContent.setPadding(
            contentPadding + insets.left,
            mContent.paddingTop,
            contentPadding + insets.right,
            contentPadding + insets.bottom,
        )
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        mContent = findViewById(R.id.folder_settings_content)

        scaleSlider = findViewById(R.id.folder_preview_icon_scale)
        scaleValue = findViewById(R.id.folder_preview_icon_scale_value)
        paddingSlider = findViewById(R.id.folder_preview_padding)
        paddingValue = findViewById(R.id.folder_preview_padding_value)
        minimumGapSlider = findViewById(R.id.folder_preview_min_gap)
        minimumGapValue = findViewById(R.id.folder_preview_min_gap_value)

        configureSlider(
            scaleSlider,
            FolderPreviewSettings.MIN_ITEM_SCALE_PERCENT,
            FolderPreviewSettings.MAX_ITEM_SCALE_PERCENT,
        ) { percent ->
            FolderPreviewSettings.setItemScalePercent(
                folderIcon.mInfo,
                percent,
                null,
            )
            updateValue(
                scaleValue,
                R.string.folder_settings_percentage,
                percent,
            )
        }

        configureSlider(
            paddingSlider,
            FolderPreviewSettings.MIN_PADDING_DP,
            FolderPreviewSettings.MAX_PADDING_DP,
        ) { paddingDp ->
            FolderPreviewSettings.setPaddingDp(
                folderIcon.mInfo,
                paddingDp,
                null,
            )
            updateValue(
                paddingValue,
                R.string.folder_settings_dp,
                paddingDp,
            )
        }

        configureSlider(
            minimumGapSlider,
            FolderPreviewSettings.MIN_GAP_DP,
            FolderPreviewSettings.MAX_GAP_DP,
        ) { gapDp ->
            FolderPreviewSettings.setMinimumGapDp(
                folderIcon.mInfo,
                gapDp,
                null,
            )
            updateValue(
                minimumGapValue,
                R.string.folder_settings_dp,
                gapDp,
            )
        }

        findViewById<View>(R.id.folder_settings_reset).setOnClickListener {
            FolderPreviewSettings.resetAll(
                folderIcon.mInfo,
                mActivityContext.modelWriter,
            )
            hasUncommittedChanges = false
            updateSliders()
            folderIcon.onPreviewSettingsChanged()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildWithMargins(
            mContent,
            widthMeasureSpec,
            0,
            heightMeasureSpec,
            mActivityContext.deviceProfile.bottomSheetProfile.bottomSheetTopPadding,
        )
        setMeasuredDimension(
            View.MeasureSpec.getSize(widthMeasureSpec),
            View.MeasureSpec.getSize(heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val contentLeft = (right - left - mContent.measuredWidth) / 2
        val contentTop = bottom - top - mContent.measuredHeight

        mContent.layout(
            contentLeft,
            contentTop,
            contentLeft + mContent.measuredWidth,
            contentTop + mContent.measuredHeight,
        )
        setTranslationShift(mTranslationShift)
    }

    fun show(folderIcon: FolderIcon) {
        if (mIsOpen) return

        this.folderIcon = folderIcon
        updateSliders()

        (parent as? ViewGroup)?.removeView(this)
        attachToContainer()
        mIsOpen = true
        setUpDefaultOpenAnimation().start()
    }

    override fun handleClose(animate: Boolean) {
        persistChanges()
        handleClose(animate, CLOSE_DURATION_MS)
    }

    override fun isOfType(type: Int): Boolean =
        type and TYPE_FOLDER_SETTINGS != 0

    companion object {
        private const val CLOSE_DURATION_MS = 200L
    }

    private fun configureSlider(
        slider: Slider,
        minimum: Int,
        maximum: Int,
        onValueChanged: (Int) -> Unit,
    ) {
        slider.valueFrom = minimum.toFloat()
        slider.valueTo = maximum.toFloat()
        slider.stepSize = 1f

        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener

            val oldOptions = folderIcon.mInfo.options
            onValueChanged(value.roundToInt())

            hasUncommittedChanges =
                hasUncommittedChanges ||
                    folderIcon.mInfo.options != oldOptions

            folderIcon.onPreviewSettingsChanged()
        }

        slider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                override fun onStopTrackingTouch(slider: Slider) {
                    persistChanges()
                }
            },
        )
    }

    private fun updateSliders() {
        val scale = FolderPreviewSettings.getItemScalePercent(
            resources,
            folderIcon.mInfo,
        )
        val padding = FolderPreviewSettings.getPaddingDp(
            resources,
            folderIcon.mInfo,
        )
        val minimumGap = FolderPreviewSettings.getMinimumGapDp(
            resources,
            folderIcon.mInfo,
        )

        scaleSlider.value = scale.toFloat()
        paddingSlider.value = padding.toFloat()
        minimumGapSlider.value = minimumGap.toFloat()

        updateValue(scaleValue, R.string.folder_settings_percentage, scale)
        updateValue(paddingValue, R.string.folder_settings_dp, padding)
        updateValue(minimumGapValue, R.string.folder_settings_dp, minimumGap)
    }

    private fun updateValue(view: TextView, formatResource: Int, value: Int) {
        view.text = resources.getString(formatResource, value)
    }

    private fun persistChanges() {
        if (!hasUncommittedChanges) return

        mActivityContext.modelWriter.updateItemInDatabase(folderIcon.mInfo)
        hasUncommittedChanges = false
    }
}