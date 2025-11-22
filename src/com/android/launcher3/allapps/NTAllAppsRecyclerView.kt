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
package com.android.launcher3.allapps

import android.content.Context
import android.graphics.*
import android.util.Log
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.LauncherPrefs

class NTAllAppsRecyclerView(private val ctx: Context, private val rv: AllAppsRecyclerView) {

    val themeManager: ThemeManager get() = ThemeManager.INSTANCE.get(ctx)
    val rad: Int = ctx.resources.getDimensionPixelSize(R.dimen.all_apps_bg_corner_radius)
    val topOffset: Int = ctx.resources.getDimensionPixelSize(R.dimen.all_apps_padding_bg_top_offset)
    val scrollOffset: Int get() = rv.computeVerticalScrollOffset()
    val Int.f: Float get() = this.toFloat()
    val bgColorRes: Int get() = if (themeManager.isMonoThemeEnabled && LauncherPrefs.ALLAPPS_THEMED_ICONS.get(ctx)) {
        R.color.nt_all_apps_content_background_color 
    } else {
        R.color.color_all_apps_content_background_color
    }
    val bgColor: Int get() = ctx.getColor(bgColorRes)
    val bgPaint get() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
    }
    val appDrawerSize: Int get() = rv.height + scrollOffset - (rv.paddingBottom / 2)
    val backgroundSizes: List<Rect> get() = listOf(Rect(rv.paddingLeft, rv.paddingTop - topOffset, rv.width - rv.paddingRight, appDrawerSize))

    fun onDispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, -scrollOffset.f)
        for (r in backgroundSizes) {
            canvas.drawRoundRect(
                RectF(r.left.f, r.top.f, r.right.f, r.bottom.f),
                rad.f,
                rad.f,
                bgPaint
            )
        }
        canvas.restore()
    }

    fun clipCanvas(canvas: Canvas, onDispatchDraw: Runnable) {
        canvas.save()
        clipBottomPath(canvas)
        onDispatchDraw.run()
        canvas.restore()
    }

    private fun clipBottomPath(canvas: Canvas) {
        val p = Path()
        canvas.translate(0f, -scrollOffset.f)
        for (r in backgroundSizes) {
            p.addRoundRect(
                RectF(r.left.f, 0f, r.right.f, r.bottom.f),
                floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    rad.f, rad.f,
                    rad.f, rad.f
                ),
                Path.Direction.CW
            )
        }
        canvas.clipPath(p)
        canvas.translate(0f, scrollOffset.f)
    }
}
