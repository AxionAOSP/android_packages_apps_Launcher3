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

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import com.android.launcher3.dot.DotInfo
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.icons.DotRenderer
import com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.FloatingIconViewCompanion
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import android.util.Log

class ComposeAppIconView(context: Context) : AbstractComposeView(context), DraggableView, FloatingIconViewCompanion {

    var iconDrawable by mutableStateOf<Drawable?>(null)
    private val iconBounds = Rect()

    private var dotInfo by mutableStateOf<DotInfo?>(null)
    private var mForceHideDot by mutableStateOf(false)
    private val dotParams = DotRenderer.DrawParams()
    private val dotRenderer: DotRenderer?
        get() = ActivityContext.lookupContext<Launcher>(context).deviceProfile.mDotRendererAllApps

    private var targetDotScale by mutableStateOf(0f)

    init {
        setWillNotDraw(false)
    }

    @Composable
    override fun Content() {
        val animatedScale by animateFloatAsState(targetValue = targetDotScale, label = "dotScale")
        dotParams.scale = animatedScale

        Canvas(modifier = Modifier.fillMaxSize()) {
            iconDrawable?.let { drawable ->
                drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                drawable.draw(drawContext.canvas.nativeCanvas)
            }

            if (!mForceHideDot && (dotInfo != null || dotParams.scale > 0)) {
                dotRenderer?.let { renderer ->
                    getIconBounds(dotParams.iconBounds)
                    val iconRect = dotParams.iconBounds
                    Utilities.scaleRectAboutCenter(iconRect, ICON_VISIBLE_AREA_FACTOR)
                    
                    val dotPosition = if (dotParams.leftAlign) renderer.leftDotPosition else renderer.rightDotPosition
                    val centerX = iconRect.left + iconRect.width() * dotPosition[0]
                    val centerY = iconRect.top + iconRect.height() * dotPosition[1]
                    val radius = (iconRect.width() * 0.228f) / 2f
                    
                    val finalScale = dotParams.scale

                    if (finalScale > 0) {
                        drawCircle(
                            color = Color(dotParams.dotColor),
                            radius = radius * finalScale,
                            center = Offset(centerX, centerY)
                        )
                    }
                }
            }
        }
    }

    override fun setIconVisible(visible: Boolean) {
        visibility = if (visible) VISIBLE else INVISIBLE
    }

    override fun setForceHideDot(hide: Boolean) {
        mForceHideDot = hide
    }

    fun applyDotState(info: DotInfo?, animate: Boolean) {
        val wasDotted = dotInfo != null
        dotInfo = info
        val isDotted = dotInfo != null
        
        val newDotScale = if (isDotted) 1f else 0f
        
        if (wasDotted || isDotted) {
            dotParams.dotColor = Themes.getAttrColor(context, R.attr.notificationDotColor)

            if (animate && (wasDotted != isDotted) && isShown) {
                targetDotScale = newDotScale
            } else {
                targetDotScale = newDotScale
                dotParams.scale = newDotScale
            }
        }
    }

    override fun getViewType(): Int = DraggableView.DRAGGABLE_ICON

    override fun prepareDrawDragView(): SafeCloseable = SafeCloseable { }

    override fun getWorkspaceVisualDragBounds(bounds: Rect) {
        getIconBounds(bounds)
    }

    override fun getSourceVisualDragBounds(bounds: Rect) {
        getIconBounds(bounds)
    }

    override fun setForceHideRing(hide: Boolean) {
    }

    fun setIconBounds(bounds: Rect) {
        iconBounds.set(bounds)
    }

    fun getIconBounds(outRect: Rect) {
        if (iconBounds.isEmpty) {
            outRect.set(0, 0, width, height)
        } else {
            outRect.set(iconBounds)
        }
    }

    fun getIcon(): Drawable? = iconDrawable

    private var iconSizePx: Int = 0
    fun setIconSizePx(size: Int) {
        iconSizePx = size
    }
    fun getIconSizePx(): Int = iconSizePx
}
