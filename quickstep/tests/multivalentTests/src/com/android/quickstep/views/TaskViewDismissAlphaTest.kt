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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskViewDismissAlphaTest {
    @Test
    fun calculateDismissAlpha_fadesFromTheStartOfUpwardDismissal() {
        assertThat(TaskView.calculateDismissAlpha(250f, 1000f, true))
            .isWithin(0.001f)
            .of(0.75f)
    }

    @Test
    fun calculateDismissAlpha_fadesWithDismissDistance() {
        assertThat(TaskView.calculateDismissAlpha(750f, 1000f, true))
            .isWithin(0.001f)
            .of(0.25f)
    }

    @Test
    fun calculateDismissAlpha_reachesZeroAtDismissLength() {
        assertThat(TaskView.calculateDismissAlpha(1000f, 1000f, true)).isEqualTo(0f)
    }

    @Test
    fun calculateDismissAlpha_staysOpaqueWhenDraggingDown() {
        assertThat(TaskView.calculateDismissAlpha(750f, 1000f, false)).isEqualTo(1f)
    }

    @Test
    fun calculateDismissAlpha_staysOpaqueWithZeroDismissLength() {
        assertThat(TaskView.calculateDismissAlpha(100f, 0f, true)).isEqualTo(1f)
    }
}
