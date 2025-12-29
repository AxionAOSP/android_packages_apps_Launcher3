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

import android.app.ActivityThread
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher

data class ComposeFloatingIconState(
    val isAnimating: Boolean = false,
    val isOpening: Boolean = true,
    val iconDrawable: Drawable? = null,
    val iconBounds: Rect = Rect(),
    val targetBounds: Rect = Rect(),
    val progress: Float = 0f
)

class ComposeFloatingIconController {
    var state by mutableStateOf(ComposeFloatingIconState())
        private set
    
    private var animationJob: Animatable<Float, AnimationVector1D>? = null
    
    fun startOpenAnimation(
        icon: BubbleTextView,
        iconBounds: Rect,
        onComplete: () -> Unit
    ) {
        val drawable = icon.icon?.constantState?.newDrawable()
        val context = icon.context
        val dp = (Launcher.getLauncher(context) as? Launcher)?.deviceProfile
        
        val targetBounds = Rect(
            0, 0,
            dp?.deviceProperties?.availableWidthPx ?: context.resources.displayMetrics.widthPixels,
            dp?.deviceProperties?.availableHeightPx ?: context.resources.displayMetrics.heightPixels
        )
        
        state = ComposeFloatingIconState(
            isAnimating = true,
            isOpening = true,
            iconDrawable = drawable,
            iconBounds = iconBounds,
            targetBounds = targetBounds,
            progress = 0f
        )
        
        onComplete()
    }
    
    fun startCloseAnimation(
        iconBounds: Rect,
        drawable: Drawable?,
        onComplete: () -> Unit
    ) {
        val context = ActivityThread.currentApplication()
        val displayMetrics = context.resources.displayMetrics
        
        val targetBounds = Rect(
            0, 0,
            displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )
        
        state = ComposeFloatingIconState(
            isAnimating = true,
            isOpening = false,
            iconDrawable = drawable,
            iconBounds = iconBounds,
            targetBounds = targetBounds,
            progress = 1f
        )
    }
    
    fun updateProgress(progress: Float) {
        state = state.copy(progress = progress)
    }
    
    fun endAnimation() {
        state = ComposeFloatingIconState()
    }
}

@Composable
fun ComposeFloatingIcon(
    controller: ComposeFloatingIconController,
    modifier: Modifier = Modifier
) {
    val state = controller.state
    
    if (!state.isAnimating || state.iconDrawable == null) {
        return
    }
    
    val density = LocalDensity.current
    
    val progress by animateFloatAsState(
        targetValue = if (state.isOpening) 1f else 0f,
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        ),
        finishedListener = { 
            controller.endAnimation()
        },
        label = "floatingIconProgress"
    )
    
    LaunchedEffect(progress) {
        controller.updateProgress(progress)
    }
    
    val iconBounds = state.iconBounds
    val targetBounds = state.targetBounds
    
    val currentLeft = lerp(iconBounds.left.toFloat(), targetBounds.left.toFloat(), progress)
    val currentTop = lerp(iconBounds.top.toFloat(), targetBounds.top.toFloat(), progress)
    val currentWidth = lerp(iconBounds.width().toFloat(), targetBounds.width().toFloat(), progress)
    val currentHeight = lerp(iconBounds.height().toFloat(), targetBounds.height().toFloat(), progress)
    
    val iconCornerRadius = with(density) { 24.dp.toPx() }
    val windowCornerRadius = with(density) { 32.dp.toPx() }
    val currentCornerRadius = lerp(iconCornerRadius, windowCornerRadius, progress)
    
    val alpha = if (state.isOpening) {
        1f - (progress * 0.3f).coerceIn(0f, 1f)
    } else {
        progress.coerceIn(0f, 1f)
    }
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val drawable = state.iconDrawable ?: return@Canvas
        
        translate(left = currentLeft, top = currentTop) {
            scale(
                scaleX = currentWidth / iconBounds.width().toFloat().coerceAtLeast(1f),
                scaleY = currentHeight / iconBounds.height().toFloat().coerceAtLeast(1f),
                pivot = Offset.Zero
            ) {
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    drawable.alpha = (alpha * 255).toInt()
                    drawable.setBounds(0, 0, iconBounds.width(), iconBounds.height())
                    drawable.draw(this)
                    restore()
                }
            }
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
