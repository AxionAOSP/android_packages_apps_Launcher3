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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AllAppsComposeFastScroller(
    sections: List<Pair<String, Int>>,
    onSectionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    

    if (sections.isEmpty()) return

    val letters = sections.map { it.first }

    Box(
        modifier = modifier
            .width(28.dp)
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val index = (offset.y / size.height * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        selectedLetter = letter
                        onSectionSelected(sections[index].second)
                    },
                    onDragEnd = {
                        selectedLetter = null
                    },
                    onDragCancel = {
                        selectedLetter = null
                    },
                    onVerticalDrag = { change, _ ->
                        val index = (change.position.y / size.height * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        if (letter != selectedLetter) {
                            selectedLetter = letter
                            onSectionSelected(sections[index].second)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 10.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }


        if (selectedLetter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-60).dp)
                    .requiredSize(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedLetter!!,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
