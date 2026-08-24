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
package com.android.launcher3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.android.axion.blur.BlurEngine
import com.android.axion.blur.AxWindowBlurController
import com.android.axion.blur.settings.AxBackdropBlurSettingsSpec
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.views.ActivityContext

class AxBackdropBlurSurface(private val host: View, activityContext: ActivityContext?) {

    private val activityContext: Context? = activityContext?.asContext()

    private var blurEngine: BlurEngine? = null

    private var everCapable = false

    fun isActive(): Boolean {
        val capable = markCapability()
        return capableOrPreserved(capable)
            && LauncherPrefsExt.LAUNCHER_BLUR_ENABLED.get(host.context)
    }

    fun drawRect(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
    ): Boolean {
        if (right <= left || bottom <= top) return false
        val engine = prepareEngine() ?: return false
        return engine.draw(canvas, left, top, right, bottom, cornerRadius.coerceAtLeast(0f))
    }

    fun drawRegion(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        alphaFraction: Float,
    ): Boolean {
        if (right <= left || bottom <= top) return false
        val alpha = (alphaFraction.coerceIn(0f, 1f) * 255).toInt()
        if (alpha <= 0) return false
        val engine = prepareEngine() ?: return false
        return if (cornerRadii != null && cornerRadii.size >= 8) {
            engine.draw(canvas, key, left, top, right, bottom, cornerRadii, alpha)
        } else {
            engine.draw(canvas, key, left, top, right, bottom, 0f, alpha)
        }
    }

    fun drawPath(canvas: Canvas, clipPath: Path?, cornerRadius: Float): Boolean {
        if (clipPath == null) return false
        val bounds = RectF()
        clipPath.computeBounds(bounds, true)
        if (bounds.isEmpty || bounds.width() <= 0f || bounds.height() <= 0f) return false
        val engine = prepareEngine() ?: return false
        return engine.draw(canvas, bounds, clipPath, cornerRadius.coerceAtLeast(0f))
    }

    fun release() {
        blurEngine?.dispose()
        blurEngine = null
    }

    private fun markCapability(): Boolean {
        val capable = AxWindowBlurController.supportsBlur()
        if (capable) {
            everCapable = true
        }
        return capable
    }

    private fun capableOrPreserved(currentlyCapable: Boolean): Boolean {
        return currentlyCapable || everCapable
    }

    private fun prepareEngine(): BlurEngine? {
        val capable = markCapability()
        if (!capableOrPreserved(capable)) {
            blurEngine?.setEnabled(false)
            return null
        }
        if (!LauncherPrefsExt.LAUNCHER_BLUR_ENABLED.get(host.context)) {
            blurEngine?.setEnabled(false)
            return null
        }
        val engine = blurEngine ?: BlurEngine(host).also { created ->
            created.useSettings(launcherBlurSpec())
            blurEngine = created
        }
        engine.setEnabled(true)
        return engine
    }

    private fun launcherBlurSpec(): AxBackdropBlurSettingsSpec {
        val maxRadiusPx = LauncherPrefsExt.LAUNCHER_BLUR_MAX_RADIUS_PX.toFloat()
        return AxBackdropBlurSettingsSpec.launcher(
            defaultRadiusPx = maxRadiusPx,
            maxRadiusPx = maxRadiusPx,
        )
    }
}
