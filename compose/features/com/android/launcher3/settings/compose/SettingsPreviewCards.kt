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
package com.android.launcher3.settings.compose

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Outline
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.preview.LauncherPreviewRenderer
import com.android.launcher3.util.Themes
import kotlin.math.min

@Composable
internal fun HomeSettingsPreview(
    rows: Int,
    columns: Int,
    iconPercent: Int,
    labelPercent: Int,
    showLabels: Boolean,
    hotseatIcons: Int,
    showSearchBar: Boolean,
    extraKey: String = "",
    heightFraction: Float = 1f,
) {
    val context = LocalContext.current
    val previewSpec = rememberLauncherPreviewSpec(context)
    val colorScheme = MaterialTheme.colorScheme
    val previewBrush = Brush.linearGradient(
        listOf(
            colorScheme.primaryContainer,
            colorScheme.tertiaryContainer,
            colorScheme.surfaceContainerHighest,
        ),
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val safeHeightFraction = heightFraction.coerceIn(0.5f, 1f)
        val availableWidth = maxWidth * previewSpec.widthFraction
        val heightFromWidth = availableWidth / previewSpec.aspectRatio
        val minHeight = (previewSpec.minHeight * safeHeightFraction).coerceAtMost(heightFromWidth)
        val maxHeight = previewSpec.maxHeight * safeHeightFraction
        val previewHeight = heightFromWidth.coerceIn(minHeight, maxHeight)
        val previewWidth = (previewHeight * previewSpec.aspectRatio).coerceAtMost(maxWidth)
        LauncherRenderPreview(
            key = "$rows:$columns:$iconPercent:$labelPercent:$showLabels:"
                + "$hotseatIcons:$showSearchBar:$extraKey",
            cornerRadius = previewSpec.cornerRadius,
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .clip(RoundedCornerShape(previewSpec.cornerRadius))
                .background(previewBrush),
        )
    }
}

@Composable
private fun LauncherRenderPreview(
    key: String,
    cornerRadius: Dp,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    AndroidView(
        factory = { context -> LauncherPreviewFrame(context) },
        modifier = modifier,
        update = { it.render(key, cornerRadiusPx) },
        onRelease = { it.release() },
    )
}

private class LauncherPreviewFrame(context: Context) : FrameLayout(context) {

    private var renderer: LauncherPreviewRenderer? = null
    private var renderKey: String? = null
    private var previewView: View? = null
    private var cornerRadiusPx = 0f
    private val wallpaperDrawable = runCatching {
        context.getSystemService(WallpaperManager::class.java)?.peekDrawable()
    }.getOrNull()

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        clipChildren = true
        clipToPadding = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = true
    }

    fun render(key: String, cornerRadiusPx: Float) {
        updateCornerRadius(cornerRadiusPx)
        if (key == renderKey && (childCount > 0 || renderer != null)) {
            return
        }
        renderKey = key
        release()
        val nextRenderer = LauncherPreviewRenderer(
            context,
            WorkspaceLayoutManager.FIRST_SCREEN_ID,
            null,
            LauncherAppState.getInstance(context).model,
            Themes.getActivityThemeRes(context),
        )
        renderer = nextRenderer
        nextRenderer.initialRender.thenAccept { view ->
            post {
                if (renderer != nextRenderer) {
                    return@post
                }
                val parent = view.parent
                if (parent is ViewGroup) {
                    parent.removeView(view)
                }
                removeAllViews()
                addWallpaperView()
                view.setBackgroundColor(AndroidColor.TRANSPARENT)
                disableInteraction(view)
                previewView = view
                addView(
                    view,
                    FrameLayout.LayoutParams(
                        view.measuredWidth,
                        view.measuredHeight,
                        Gravity.CENTER,
                    ),
                )
                fitChild(view)
            }
        }
    }

    fun release() {
        removeAllViews()
        previewView = null
        renderer?.onViewDestroyed()
        renderer = null
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        invalidateOutline()
        previewView?.let(::fitChild)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return false
    }

    private fun updateCornerRadius(radius: Float) {
        if (cornerRadiusPx == radius) {
            return
        }
        cornerRadiusPx = radius
        invalidateOutline()
    }

    private fun disableInteraction(view: View) {
        view.isClickable = false
        view.isLongClickable = false
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                disableInteraction(view.getChildAt(i))
            }
        }
    }

    private fun addWallpaperView() {
        val wallpaper = wallpaperDrawable?.constantState?.newDrawable(resources)
            ?: wallpaperDrawable
            ?: return
        val wallpaperView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(wallpaper)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(
            wallpaperView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun fitChild(view: View) {
        if (width == 0 || height == 0 || view.measuredWidth == 0 || view.measuredHeight == 0) {
            return
        }
        val scale = min(
            width / view.measuredWidth.toFloat(),
            height / view.measuredHeight.toFloat(),
        )
        view.pivotX = view.measuredWidth / 2f
        view.pivotY = view.measuredHeight / 2f
        view.scaleX = scale
        view.scaleY = scale
    }
}

private data class LauncherPreviewSpec(
    val aspectRatio: Float,
    val widthFraction: Float,
    val minHeight: Dp,
    val maxHeight: Dp,
    val cornerRadius: Dp,
)

@Composable
private fun rememberLauncherPreviewSpec(context: Context): LauncherPreviewSpec {
    val configuration = LocalConfiguration.current
    return remember(
        context,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.orientation,
    ) {
        val properties = LauncherAppState.getIDP(context).getDeviceProfile(context)
            .getDeviceProperties()
        if (properties.isTablet) {
            LauncherPreviewSpec(
                aspectRatio = properties.aspectRatio.coerceIn(1.2f, 1.8f),
                widthFraction = 1f,
                minHeight = 220.dp,
                maxHeight = 360.dp,
                cornerRadius = 28.dp,
            )
        } else {
            val shortSide = minOf(properties.widthPx, properties.heightPx).coerceAtLeast(1)
            val longSide = maxOf(properties.widthPx, properties.heightPx).coerceAtLeast(shortSide)
            LauncherPreviewSpec(
                aspectRatio = (shortSide.toFloat() / longSide).coerceIn(0.42f, 0.58f),
                widthFraction = 0.78f,
                minHeight = 420.dp,
                maxHeight = 560.dp,
                cornerRadius = 32.dp,
            )
        }
    }
}
