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
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.util.AttributeSet
import android.view.View
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.views.AbstractSlideInView
import kotlin.math.cos
import kotlin.math.sin

class FolderStylePickerSheet(context: Context, attrs: AttributeSet?) :
    AbstractSlideInView<Launcher>(context, attrs, 0) {

    private var mFolderInfo: FolderInfo? = null

    private var mFolderIcon: FolderIcon? = null

    init {
        setWillNotDraw(false)
    }

    override fun handleClose(animate: Boolean) {
        handleClose(animate, 200)
    }

    override fun isOfType(type: Int): Boolean {
        return type and AbstractFloatingView.TYPE_FOLDER_STYLE_PICKER != 0
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        mContent = findViewById(R.id.folder_style_content)
        context.getDrawable(R.drawable.bg_rounded_corner_bottom_sheet)?.let { drawable ->
            setContentBackgroundWithParent(drawable, mContent)
        }
    }

    fun populateAndShow(folderIcon: FolderIcon, folderInfo: FolderInfo) {
        mFolderInfo = folderInfo
        mFolderIcon = folderIcon

        val composeView = findViewById<ComposeView>(R.id.compose_view)
        composeView.setContent {
            FolderStylePickerTheme {
                FolderStylePicker(
                    initialStyle = folderInfo.folderStyle,
                    initialCoverText = folderInfo.coverText ?: DEFAULT_COVER_EMOJI,
                    onStyleSelected = { style, coverText ->
                        updateFolderStyle(style, coverText)
                    }
                )
            }
        }

        attachToContainer()
        mIsOpen = false
        animateOpen()
    }

    private fun animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning) {
            return
        }
        mIsOpen = true
        setUpDefaultOpenAnimation().start()
    }

    private fun updateFolderStyle(style: Int, coverText: String?) {
        val info = mFolderInfo ?: return
        val icon = mFolderIcon ?: return

        info.folderStyle = style
        info.coverText = coverText
        mActivityContext.modelWriter.updateItemInDatabase(info)

        icon.folderBackground.updateFolderStyle(style)
        icon.folder?.let { folder ->
            folder.content?.alpha = 1f
            folder.mFooter?.alpha = 1f
        }
        icon.onItemsChanged(true)
    }

    companion object {
        fun show(launcher: Launcher, folderIcon: FolderIcon, folderInfo: FolderInfo) {
            AbstractFloatingView.closeOpenViews(
                launcher, true, AbstractFloatingView.TYPE_FOLDER_STYLE_PICKER
            )

            val sheet = launcher.layoutInflater.inflate(
                R.layout.folder_style_picker_sheet,
                launcher.dragLayer,
                false
            ) as FolderStylePickerSheet
            sheet.populateAndShow(folderIcon, folderInfo)
        }
    }
}

private const val DEFAULT_COVER_EMOJI = "📁"

private val COVER_PRESETS = listOf(
    "📁", "⭐", "🎮", "🎵", "📷", "💼", "🏠", "❤️", "🔧", "📚",
    "💬", "🛒", "💰", "🎬", "✈️", "🍔", "💪", "🎨", "📱", "🔒"
)

@Composable
fun FolderStylePicker(
    initialStyle: Int,
    initialCoverText: String,
    onStyleSelected: (Int, String?) -> Unit
) {
    var selectedStyle by remember { mutableIntStateOf(initialStyle) }
    var coverText by remember { mutableStateOf(initialCoverText) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val coverTextPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            color = AndroidColor.WHITE
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceBright = MaterialTheme.colorScheme.surfaceBright

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val text = coverText.ifEmpty { DEFAULT_COVER_EMOJI }

            Canvas(modifier = Modifier.size(80.dp)) {
                withTransform({ scale(1.6f, 1.6f, center) }) {
                    when (selectedStyle) {
                        LauncherSettings.Favorites.FOLDER_STYLE_QUADRANT -> {
                            drawCircle(surfaceBright)
                            drawQuadrantDots(this, primaryColor, center.x, center.y, size.width / 2)
                        }
                        LauncherSettings.Favorites.FOLDER_STYLE_GRID -> {
                            drawRoundRect(
                                color = surfaceBright,
                                cornerRadius = CornerRadius(size.width * 0.2f),
                                size = size
                            )
                            drawGridDots(this, primaryColor, center.x, center.y, size.width / 2)
                        }
                        LauncherSettings.Favorites.FOLDER_STYLE_CIRCLE -> {
                            drawCircle(surfaceBright)
                            drawCircleDots(this, primaryColor, center.x, center.y, size.width / 2)
                        }
                        LauncherSettings.Favorites.FOLDER_STYLE_COVER -> {
                            drawCircle(surfaceBright)
                            coverTextPaint.textSize = 30.sp.toPx()
                            drawContext.canvas.nativeCanvas.drawText(
                                if (coverText.isEmpty()) "?" else text,
                                center.x,
                                center.y + (30.sp.toPx() / 3),
                                coverTextPaint
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StyleOptionPreview(
                name = stringResource(R.string.folder_style_quadrant),
                selected = selectedStyle == LauncherSettings.Favorites.FOLDER_STYLE_QUADRANT,
                onClick = {
                    selectedStyle = LauncherSettings.Favorites.FOLDER_STYLE_QUADRANT
                    onStyleSelected(selectedStyle, coverText.ifEmpty { null })
                    showEmojiPicker = false
                },
                preview = { QuadrantPreview(it) }
            )
            StyleOptionPreview(
                name = stringResource(R.string.folder_style_grid),
                selected = selectedStyle == LauncherSettings.Favorites.FOLDER_STYLE_GRID,
                onClick = {
                    selectedStyle = LauncherSettings.Favorites.FOLDER_STYLE_GRID
                    onStyleSelected(selectedStyle, coverText.ifEmpty { null })
                    showEmojiPicker = false
                },
                preview = { GridPreview(it) }
            )
            StyleOptionPreview(
                name = stringResource(R.string.folder_style_circle),
                selected = selectedStyle == LauncherSettings.Favorites.FOLDER_STYLE_CIRCLE,
                onClick = {
                    selectedStyle = LauncherSettings.Favorites.FOLDER_STYLE_CIRCLE
                    onStyleSelected(selectedStyle, coverText.ifEmpty { null })
                    showEmojiPicker = false
                },
                preview = { CirclePreview(it) }
            )
            StyleOptionPreview(
                name = stringResource(R.string.folder_style_cover),
                selected = selectedStyle == LauncherSettings.Favorites.FOLDER_STYLE_COVER,
                onClick = {
                    selectedStyle = LauncherSettings.Favorites.FOLDER_STYLE_COVER
                    onStyleSelected(selectedStyle, coverText.ifEmpty { null })
                },
                preview = { ConcentricCirclesPreview(it) }
            )
        }

        if (selectedStyle == LauncherSettings.Favorites.FOLDER_STYLE_COVER) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val arrowColor = MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceBright)
                        .clickable { showEmojiPicker = !showEmojiPicker },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = coverText.ifEmpty { "☺\ufe0f" },
                        fontSize = 24.sp
                    )
                    
                    Canvas(
                        modifier = Modifier.matchParentSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        val triangleSize = 8.dp.toPx()
                        val path = Path().apply {
                            moveTo(w, h)
                            lineTo(w, h - triangleSize)
                            lineTo(w - triangleSize, h)
                            close()
                        }
                        drawPath(path, color = arrowColor)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(COVER_PRESETS.take(8)) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (coverText == emoji) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    coverText = emoji
                                    onStyleSelected(selectedStyle, emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
            
            if (showEmojiPicker) {
                Spacer(modifier = Modifier.height(16.dp))
                EmojiPickerGrid(
                    onEmojiSelected = { emoji ->
                        coverText = emoji
                        onStyleSelected(selectedStyle, emoji)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun EmojiPickerGrid(onEmojiSelected: (String) -> Unit) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        factory = { context ->
            EmojiPickerView(context).apply {
                setOnEmojiPickedListener { item ->
                    onEmojiSelected(item.emoji)
                }
            }
        }
    )
}

@Composable
fun StyleOptionPreview(
    name: String, 
    selected: Boolean, 
    onClick: () -> Unit,
    preview: @Composable (Color) -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface
    val color = if (selected) selectedColor else unselectedColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 2.dp,
                    color = if (selected) selectedColor else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(if (selected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            preview(color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun QuadrantPreview(dotColor: Color) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.width / 2
        
        drawCircle(color = dotColor.copy(alpha = 0.1f), radius = r)
        drawQuadrantDots(this, dotColor, cx, cy, r)
    }
}

@Composable
private fun GridPreview(dotColor: Color) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.width / 2
        
        drawRoundRect(
            color = dotColor.copy(alpha = 0.1f),
            cornerRadius = CornerRadius(size.width * 0.2f),
            size = size
        )
        drawGridDots(this, dotColor, cx, cy, r)
    }
}

@Composable
private fun CirclePreview(dotColor: Color) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.width / 2
        
        drawCircle(color = dotColor.copy(alpha = 0.1f), radius = r)
        drawCircleDots(this, dotColor, cx, cy, r)
    }
}

@Composable
private fun ConcentricCirclesPreview(dotColor: Color) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.width / 2
        
        drawCircle(color = dotColor.copy(alpha = 0.1f), radius = r)
        drawCircle(color = dotColor, radius = r * 0.5f, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = dotColor, radius = r * 0.2f)
    }
}

private fun drawQuadrantDots(scope: DrawScope, color: Color, cx: Float, cy: Float, radius: Float) {
    val dotRadius = radius * 0.28f
    val spacing = radius * 0.4f
    scope.drawCircle(color, dotRadius, Offset(cx - spacing, cy - spacing))
    scope.drawCircle(color, dotRadius, Offset(cx + spacing, cy - spacing))
    scope.drawCircle(color, dotRadius, Offset(cx - spacing, cy + spacing))
    drawMiniIcons(scope, color, cx + spacing, cy + spacing, dotRadius)
}

private fun drawMiniIcons(scope: DrawScope, color: Color, cx: Float, cy: Float, itemRadius: Float) {
    val miniRadius = itemRadius * 0.45f 
    val spacing = miniRadius * 1.1f
    scope.drawCircle(color, miniRadius, Offset(cx - spacing, cy - spacing))
    scope.drawCircle(color, miniRadius, Offset(cx + spacing, cy - spacing))
    scope.drawCircle(color, miniRadius, Offset(cx - spacing, cy + spacing))
    scope.drawCircle(color, miniRadius, Offset(cx + spacing, cy + spacing))
}

private fun drawGridDots(scope: DrawScope, color: Color, cx: Float, cy: Float, radius: Float) {
    val dotRadius = radius * 0.18f
    val spacing = radius * 0.6f
    
    for (row in -1..1) {
        for (col in -1..1) {
            if (row == 1 && col == 1) continue 
            scope.drawCircle(color, dotRadius, Offset(cx + col * spacing, cy + row * spacing))
        }
    }
    drawIndicatorDots(scope, color, cx + spacing, cy + spacing, dotRadius * 0.4f)
}

private fun drawCircleDots(scope: DrawScope, dotColor: Color, cx: Float, cy: Float, ringRadius: Float) {
    val dotRadius = ringRadius * 0.25f
    val radius = ringRadius * 0.7f
    
    for (i in 0 until 6) {
        val angle = (2 * Math.PI * i / 6) - Math.PI / 2
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        scope.drawCircle(dotColor, dotRadius, Offset(x, y))
    }
}
    
private fun drawIndicatorDots(scope: DrawScope, color: Color, cx: Float, cy: Float, dotRadius: Float) {
    val spacing = dotRadius * 1.8f
    scope.drawCircle(color, dotRadius, Offset(cx - spacing, cy - spacing))
    scope.drawCircle(color, dotRadius, Offset(cx + spacing, cy - spacing))
    scope.drawCircle(color, dotRadius, Offset(cx - spacing, cy + spacing))
    scope.drawCircle(color, dotRadius, Offset(cx + spacing, cy + spacing))
}

@Composable
fun FolderStylePickerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
