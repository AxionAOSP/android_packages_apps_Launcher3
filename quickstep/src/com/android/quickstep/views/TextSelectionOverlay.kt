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

package com.android.quickstep.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.android.axion.ocr.AxOcrEngine
import com.android.axion.ocr.OcrResult
import com.android.axion.ocr.TextBlock
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextSelectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var ocrResult: OcrResult? = null
    private var bitmapWidth = 0
    private var bitmapHeight = 0
    private val selectedBlocks = mutableSetOf<Int>()
    private var scope: CoroutineScope? = null
    private var ocrJob: Job? = null

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4D1A73E8.toInt()
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A73E8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private var isLoading = true
    private var loadingAlpha = 0f
    private var loadingAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
    }

    fun startOcr(bitmap: Bitmap, snapshotView: View) {
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        isLoading = true
        startLoadingAnimation()

        scope = CoroutineScope(Dispatchers.Main)
        ocrJob = scope?.launch {
            val engine = AxOcrEngine(context)
            try {
                val result = engine.recognize(bitmap)
                ocrResult = result
                isLoading = false
                stopLoadingAnimation()

                if (result.isEmpty) {
                    showToast(context.getString(R.string.select_text_no_text))
                    dismiss()
                } else {
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                isLoading = false
                stopLoadingAnimation()
                showToast(context.getString(R.string.select_text_failed))
                dismiss()
            } finally {
                engine.close()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isLoading) {
            loadingPaint.alpha = (loadingAlpha * 128).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), loadingPaint)
            return
        }

        val result = ocrResult ?: return
        val scaleX = width.toFloat() / bitmapWidth
        val scaleY = height.toFloat() / bitmapHeight

        for ((index, block) in result.textBlocks.withIndex()) {
            val rect = scaleRect(block.boundingBox, scaleX, scaleY)

            if (index in selectedBlocks) {
                canvas.drawRect(rect, highlightPaint)
                canvas.drawRect(rect, borderPaint)
            } else {
                val unselectedBorder = Paint(borderPaint)
                unselectedBorder.color = 0x401A73E8.toInt()
                canvas.drawRect(rect, unselectedBorder)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLoading) return true

        when (event.action) {
            MotionEvent.ACTION_UP -> {
                val result = ocrResult ?: return true
                val scaleX = width.toFloat() / bitmapWidth
                val scaleY = height.toFloat() / bitmapHeight

                val hitIndex = findBlockAt(event.x, event.y, result.textBlocks, scaleX, scaleY)
                if (hitIndex >= 0) {
                    if (hitIndex in selectedBlocks) {
                        selectedBlocks.remove(hitIndex)
                    } else {
                        selectedBlocks.add(hitIndex)
                    }
                    invalidate()
                    if (selectedBlocks.isNotEmpty()) {
                        copySelectedText()
                    }
                } else {
                    if (selectedBlocks.isEmpty()) {
                        selectAllAndCopy()
                    } else {
                        dismiss()
                    }
                }
            }
        }
        return true
    }

    private fun findBlockAt(
        x: Float, y: Float,
        blocks: List<TextBlock>,
        scaleX: Float, scaleY: Float
    ): Int {
        for ((index, block) in blocks.withIndex()) {
            val rect = scaleRect(block.boundingBox, scaleX, scaleY)
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    private fun scaleRect(rect: RectF, scaleX: Float, scaleY: Float): RectF {
        return RectF(
            rect.left * scaleX,
            rect.top * scaleY,
            rect.right * scaleX,
            rect.bottom * scaleY
        )
    }

    private fun selectAllAndCopy() {
        val result = ocrResult ?: return
        for (i in result.textBlocks.indices) {
            selectedBlocks.add(i)
        }
        invalidate()
        copySelectedText()
    }

    private fun copySelectedText() {
        val result = ocrResult ?: return
        val text = selectedBlocks
            .sorted()
            .mapNotNull { result.textBlocks.getOrNull(it)?.text }
            .joinToString(" ")

        if (text.isNotBlank()) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
            showToast(context.getString(R.string.select_text_copied))
        }
    }

    private fun dismiss() {
        ocrJob?.cancel()
        scope?.cancel()
        stopLoadingAnimation()

        animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (parent as? ViewGroup)?.removeView(this@TextSelectionOverlay)
                }
            })
            .start()
    }

    private fun startLoadingAnimation() {
        loadingAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                loadingAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ocrJob?.cancel()
        scope?.cancel()
        stopLoadingAnimation()
    }

    companion object {
        private const val TAG = "TextSelectionOverlay"

        @JvmStatic
        fun show(taskView: TaskView, snapshotView: View, thumbnail: Bitmap) {
            val existing = taskView.findViewWithTag<TextSelectionOverlay>(TAG)
            existing?.dismiss()

            val overlay = TextSelectionOverlay(taskView.context)
            overlay.tag = TAG
            overlay.alpha = 0f

            val lp = LayoutParams(snapshotView.width, snapshotView.height)
            lp.leftMargin = snapshotView.left
            lp.topMargin = snapshotView.top

            taskView.addView(overlay, lp)

            overlay.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            overlay.startOcr(thumbnail, snapshotView)
        }
    }
}
