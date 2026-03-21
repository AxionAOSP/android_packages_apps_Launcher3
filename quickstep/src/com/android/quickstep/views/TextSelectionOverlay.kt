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
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.android.axion.ocr.AxOcrEngine
import com.android.axion.ocr.OcrResult
import com.android.axion.ocr.TextBlock
import com.android.launcher3.R
import com.android.quickstep.util.TaskCornerRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private val highlightRects = ArrayList<RectF>()
    private val selectionPadding = (4f * resources.displayMetrics.density)

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
    }

    private val highlightPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.PLUS
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = selectionPadding * 2f
    }

    private var cornerRadius = 0f
    private val boundsRect = Rect()
    private val boundsRectF = RectF()

    private var highlightProgress = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var isLoading = true

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
        style = Paint.Style.FILL
    }

    private var loadingView: ComposeView? = null

    private val clipPath = Path()

    private var isDragging = false
    private var dragStartIndex = -1
    private var longPressTriggered = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPressConfirmed() }

    init {
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
    }

    fun startOcr(bitmap: Bitmap, snapshotView: View) {
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        isLoading = true
        startScanAnimation()

        scope = CoroutineScope(Dispatchers.Main)
        ocrJob = scope?.launch {
            val engine = AxOcrEngine(context)
            try {
                val result = engine.recognize(bitmap)
                ocrResult = result
                isLoading = false
                stopScanAnimation()

                if (result.isEmpty) {
                    showToast(context.getString(R.string.select_text_no_text))
                    dismiss()
                } else {
                    animate().alpha(RESULTS_ALPHA).setDuration(300).start()
                    buildHighlights(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                isLoading = false
                stopScanAnimation()
                showToast(context.getString(R.string.select_text_failed))
                dismiss()
            } finally {
                engine.close()
            }
        }
    }

    private fun buildHighlights(result: OcrResult) {
        val scaleX = width.toFloat() / bitmapWidth
        val scaleY = height.toFloat() / bitmapHeight

        highlightRects.clear()

        for (block in result.textBlocks) {
            val rect = RectF(
                block.boundingBox.left * scaleX,
                block.boundingBox.top * scaleY,
                block.boundingBox.right * scaleX,
                block.boundingBox.bottom * scaleY
            )
            rect.inset(-selectionPadding, -selectionPadding)
            highlightRects.add(rect)
        }

        animateHighlightsIn()
    }

    private fun animateHighlightsIn() {
        ObjectAnimator.ofFloat(this, HIGHLIGHT_PROGRESS, 0f, 1f).apply {
            duration = 667
            interpolator = SELECTION_INTERPOLATOR
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        if (cornerRadius > 0f) {
            clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (height == 0) return

        if (cornerRadius > 0f) {
            canvas.save()
            canvas.clipPath(clipPath)
        }

        if (isLoading) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            if (cornerRadius > 0f) canvas.restore()
            return
        }

        getDrawingRect(boundsRect)
        boundsRectF.set(boundsRect)
        canvas.drawRoundRect(boundsRectF, cornerRadius, cornerRadius, backgroundPaint)

        val sweepProgress = highlightProgress * 1.1f
        for ((index, rect) in highlightRects.withIndex()) {
            val normalizedTop = rect.top / height
            val fade = ((sweepProgress - normalizedTop) * 10f).coerceIn(0f, 1f)

            if (index in selectedBlocks) {
                selectedPaint.alpha = (fade * 255f).toInt()
                canvas.drawRoundRect(rect, selectionPadding, selectionPadding, selectedPaint)
            } else {
                highlightPaint.alpha = (fade * 200f).toInt()
                canvas.drawRoundRect(rect, selectionPadding, selectionPadding, highlightPaint)
            }
        }

        if (cornerRadius > 0f) canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLoading) return true

        val result = ocrResult ?: return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                longPressTriggered = false
                isDragging = false
                handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!longPressTriggered) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (dx * dx + dy * dy > TOUCH_SLOP * TOUCH_SLOP) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                    return true
                }

                if (isDragging) {
                    val hitIndex = findBlockAt(event.x, event.y)
                    if (hitIndex >= 0 && dragStartIndex >= 0) {
                        selectedBlocks.clear()
                        val from = minOf(dragStartIndex, hitIndex)
                        val to = maxOf(dragStartIndex, hitIndex)
                        for (i in from..to) selectedBlocks.add(i)
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)

                if (isDragging) {
                    isDragging = false
                    if (selectedBlocks.isNotEmpty()) copySelectedText()
                    return true
                }

                if (!longPressTriggered) {
                    val hitIndex = findBlockAt(event.x, event.y)
                    if (hitIndex >= 0) {
                        if (hitIndex in selectedBlocks) {
                            selectedBlocks.remove(hitIndex)
                        } else {
                            selectedBlocks.add(hitIndex)
                        }
                        invalidate()
                        if (selectedBlocks.isNotEmpty()) copySelectedText()
                    } else {
                        if (selectedBlocks.isEmpty()) {
                            selectAllAndCopy()
                        } else {
                            dismiss()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                isDragging = false
                return true
            }
        }
        return true
    }

    private fun onLongPressConfirmed() {
        longPressTriggered = true
        isDragging = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        dragStartIndex = findBlockAt(touchDownX, touchDownY)
        if (dragStartIndex >= 0) {
            selectedBlocks.clear()
            selectedBlocks.add(dragStartIndex)
            invalidate()
        } else {
            selectAllAndCopy()
        }
    }

    private fun findBlockAt(x: Float, y: Float): Int {
        val hitSlop = 12f * resources.displayMetrics.density
        for ((index, rect) in highlightRects.withIndex()) {
            val expanded = RectF(rect)
            expanded.inset(-hitSlop, -hitSlop)
            if (expanded.contains(x, y)) return index
        }
        return -1
    }

    private fun selectAllAndCopy() {
        val result = ocrResult ?: return
        for (i in result.textBlocks.indices) selectedBlocks.add(i)
        invalidate()
        copySelectedText()
    }

    private fun copySelectedText() {
        val result = ocrResult ?: return
        val blocks = selectedBlocks
            .sorted()
            .mapNotNull { result.textBlocks.getOrNull(it) }

        if (blocks.isEmpty()) return

        val sb = StringBuilder()
        var prevBlock: TextBlock? = null
        for (block in blocks) {
            if (prevBlock != null) {
                val prevCenterY = prevBlock.boundingBox.centerY()
                val currCenterY = block.boundingBox.centerY()
                val lineHeight = prevBlock.boundingBox.height()
                if (Math.abs(currCenterY - prevCenterY) > lineHeight * 0.6f) {
                    sb.append('\n')
                } else {
                    sb.append(' ')
                }
            }
            sb.append(block.text)
            prevBlock = block
        }
        val text = sb.toString()

        if (text.isNotBlank()) {
            val clipboard = context.applicationContext
                .getSystemService(ClipboardManager::class.java)
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
                showToast(context.getString(R.string.select_text_copied))
            }
        }
    }

    fun dismiss() {
        ocrJob?.cancel()
        scope?.cancel()
        stopScanAnimation()
        handler.removeCallbacks(longPressRunnable)

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

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun startScanAnimation() {
        val composeView = ComposeView(context)
        composeView.setContent {
            ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
        }
        val lp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.CENTER
        addView(composeView, lp)
        loadingView = composeView
    }

    private fun stopScanAnimation() {
        loadingView?.let { removeView(it) }
        loadingView = null
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ocrJob?.cancel()
        scope?.cancel()
        stopScanAnimation()
        handler.removeCallbacks(longPressRunnable)
    }

    companion object {
        private const val TAG = "TextSelectionOverlay"
        private const val LONG_PRESS_TIMEOUT = 400L
        private const val TOUCH_SLOP = 16f
        private const val RESULTS_ALPHA = 0.25f

        private val SELECTION_INTERPOLATOR = PathInterpolator(0.71f, 0f, 0.13f, 1f)

        private val HIGHLIGHT_PROGRESS = object : FloatProperty<TextSelectionOverlay>("highlightProgress") {
            override fun get(obj: TextSelectionOverlay) = obj.highlightProgress
            override fun setValue(obj: TextSelectionOverlay, value: Float) {
                obj.highlightProgress = value
            }
        }

        @JvmStatic
        fun show(taskView: TaskView, snapshotView: View, thumbnail: Bitmap) {
            val existing = taskView.findViewWithTag<TextSelectionOverlay>(TAG)
            existing?.dismiss()

            val overlay = TextSelectionOverlay(taskView.context)
            overlay.tag = TAG
            overlay.alpha = 0f
            overlay.cornerRadius = TaskCornerRadius.get(taskView.context)

            val lp = LayoutParams(snapshotView.width, snapshotView.height)
            lp.leftMargin = snapshotView.left
            lp.topMargin = snapshotView.top

            taskView.addView(overlay, lp)

            overlay.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    overlay.startOcr(thumbnail, snapshotView)
                }
                .start()
        }
    }
}
