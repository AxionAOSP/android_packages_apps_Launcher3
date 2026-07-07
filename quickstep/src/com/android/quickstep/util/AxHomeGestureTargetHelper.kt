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

package com.android.quickstep.util

import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.views.FloatingIconView
import kotlin.math.min
import kotlin.math.round

class AxHomeGestureTargetHelper(
    siblingAnimation: RectFSpringAnim?,
    targetRect: RectF?,
    private val launcher: Launcher,
    private val targetView: View?,
) {
    private val springAnimation = siblingAnimation as? AxRectFSpringAnim
    private val trackTarget = targetRect?.isEmpty == false && targetView != null
    private val targetBounds = targetRect?.let(::RectF) ?: RectF()
    private val transformedBounds = RectF()
    private val targetViewBounds = Rect()

    init {
        springAnimation?.setTargetUpdateCallback(::update)
    }

    fun update() {
        if (!trackTarget) return
        val view = targetView ?: return
        if (!view.isAttachedToWindow) return
        FloatingIconView.getLocationBoundsForView(
            launcher,
            view,
            true,
            transformedBounds,
            targetViewBounds,
        )
        if (transformedBounds.isEmpty) return
        springAnimation?.updateTargetTracking(
            round(transformedBounds.left - targetBounds.left),
            round(transformedBounds.top - targetBounds.top),
            min(1f, transformedBounds.width() / targetBounds.width()),
        )
    }
}
