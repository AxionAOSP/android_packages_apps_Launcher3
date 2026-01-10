/*
 * Copyright (C) 2025 AxionOS
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

package com.android.launcher3.allapps.compose

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.BitmapRenderer
import com.android.launcher3.views.ActivityContext

class ComposeDragPreviewProvider(
    view: View,
    private val iconDrawable: Drawable?,
    private val iconSizePx: Int,
    private val iconBoundsOnScreen: Rect
) : DragPreviewProvider(view) {

    override fun drawDragView(destCanvas: Canvas, scale: Float) {
        val saveCount = destCanvas.save()
        destCanvas.scale(scale, scale)
        
        iconDrawable?.let { drawable ->
            destCanvas.translate((blurSizeOutline / 2).toFloat(), (blurSizeOutline / 2).toFloat())
            drawable.setBounds(0, 0, iconSizePx, iconSizePx)
            drawable.draw(destCanvas)
        }
        
        destCanvas.restoreToCount(saveCount)
    }

    override fun createDrawable(): Drawable? {
        if (iconDrawable == null) return null
        
        val bitmap = BitmapRenderer.createHardwareBitmap(
            iconSizePx + blurSizeOutline,
            iconSizePx + blurSizeOutline
        ) { canvas -> drawDragView(canvas, 1f) }
        
        return if (bitmap != null) FastBitmapDrawable(bitmap) else null
    }

    override fun getScaleAndPosition(preview: Drawable, outPos: IntArray): Float {
        val context: ActivityContext = ActivityContext.lookupContext(mView.context)
        val dragLayer = context.dragLayer
        
        val dragLayerPos = IntArray(2)
        dragLayer.getLocationOnScreen(dragLayerPos)
        
        outPos[0] = iconBoundsOnScreen.left - dragLayerPos[0]
        outPos[1] = iconBoundsOnScreen.top - dragLayerPos[1]
        
        outPos[0] = Math.round(outPos[0].toFloat() - 
                (preview.intrinsicWidth - iconSizePx.toFloat()) / 2)
        outPos[1] = Math.round(outPos[1].toFloat() - 
                (preview.intrinsicHeight - iconSizePx.toFloat()) / 2)
        
        return 1f
    }

    override fun getScaleAndPosition(view: View, outPos: IntArray): Float {
        val context: ActivityContext = ActivityContext.lookupContext(mView.context)
        val dragLayer = context.dragLayer
        
        val dragLayerPos = IntArray(2)
        dragLayer.getLocationOnScreen(dragLayerPos)
        
        outPos[0] = iconBoundsOnScreen.left - dragLayerPos[0]
        outPos[1] = iconBoundsOnScreen.top - dragLayerPos[1]
        
        outPos[0] = Math.round(outPos[0].toFloat() - 
                (view.width - iconSizePx.toFloat()) / 2)
        outPos[1] = Math.round(outPos[1].toFloat() - 
                (view.height - iconSizePx.toFloat()) / 2)
        
        return 1f
    }

    override fun getContentView(): View? = null
}
