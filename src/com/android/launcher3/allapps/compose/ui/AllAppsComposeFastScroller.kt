package com.android.launcher3.allapps.compose.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AllAppsComposeFastScroller(
    sections: List<Pair<String, Int>>,
    gridState: LazyGridState,
    totalItems: Int,
    onSectionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    if (sections.isEmpty()) return

    val letters = sections.map { it.first }
    
    val scrollProgress by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            if (totalItemsCount == 0) return@derivedStateOf 0f

            val viewportHeight = layoutInfo.viewportSize.height
            val firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
            
            if (firstItem == null) 0f
            else {
                val firstRowY = firstItem.offset.y
                val colsInFirstRow = layoutInfo.visibleItemsInfo.count { it.offset.y == firstRowY }
                val numCols = colsInFirstRow.coerceAtLeast(1)
                
                val itemHeight = firstItem.size.height.toFloat()
                val visibleRows = (viewportHeight / itemHeight)
                
                val totalRows = (totalItemsCount + numCols - 1) / numCols
                val maxScrollRow = (totalRows - visibleRows).coerceAtLeast(1f)
                
                val currentRow = firstVisibleItemIndex / numCols
                val rowOffset = gridState.firstVisibleItemScrollOffset / itemHeight
                
                ((currentRow + rowOffset) / maxScrollRow).coerceIn(0f, 1f)
            }
        }
    }
    
    val displayProgress = if (isDragging) dragProgress else scrollProgress

    Box(
        modifier = modifier
            .width(24.dp)
            .padding(vertical = 16.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.y / size.height).coerceIn(0f, 1f)
                        val index = (dragProgress * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        selectedLetter = letters[index]
                        onSectionSelected(sections[index].second)
                    },
                    onDragEnd = {
                        isDragging = false
                        selectedLetter = null
                    },
                    onDragCancel = {
                        isDragging = false
                        selectedLetter = null
                    },
                    onVerticalDrag = { change, _ ->
                        dragProgress = (change.position.y / size.height).coerceIn(0f, 1f)
                        val index = (dragProgress * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        if (letter != selectedLetter) {
                            selectedLetter = letter
                            onSectionSelected(sections[index].second)
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val thumbHeight = 32.dp
        val animatedProgress by animateFloatAsState(
            targetValue = displayProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "thumbProgress"
        )
        
        val thumbWidth by animateDpAsState(
            targetValue = if (isDragging) 6.dp else 4.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "thumbWidth"
        )
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val trackHeight = maxHeight - thumbHeight
            val thumbOffset = trackHeight * animatedProgress
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffset)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            AnimatedVisibility(
                visible = isDragging && selectedLetter != null,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialScale = 0.5f
                ) + fadeIn(animationSpec = tween(100)),
                exit = scaleOut(
                    animationSpec = tween(150),
                    targetScale = 0.7f
                ) + fadeOut(animationSpec = tween(100)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-56).dp, y = thumbOffset - 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedLetter == "\uD83D\uDCCC") {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = selectedLetter ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

