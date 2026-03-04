package com.android.launcher3.allapps.compose.shared.model

import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.model.data.AppInfo

data class ComposeIconInfo(
    val appInfo: AppInfo,
    val iconBoundsOnScreen: RectF,
    val iconDrawable: Drawable?,
    val iconSizePx: Int,
    val hostView: View? = null
)

