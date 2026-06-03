package com.android.launcher3.allapps.compose.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.dot.DotInfo
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.FloatingIconViewCompanion
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import androidx.compose.runtime.snapshots.Snapshot
import com.android.launcher3.allapps.AllAppsComposeController
import com.android.launcher3.model.data.ItemInfo

class ComposeAppIconView(context: Context) : View(context), DraggableView, FloatingIconViewCompanion {

    var controller: AllAppsComposeController? = null
    var sectionId: String? = null

    var iconDrawable: Drawable? = null
        set(value) {
            if (field !== value) {
                field = value
                invalidate()
            }
        }

    private val iconBounds = Rect()
    private val iconBoundsInDragLayer = RectF()
    private val tmpBounds = Rect()
    private var dotInfo: DotInfo? = null
    private var mForceHideDot = false
    private var mDotColor: Int = 0
    private var dotScale = 0f
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        iconDrawable?.let { drawable ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }

        if (!mForceHideDot && dotInfo != null && dotScale > 0f) {
            tmpBounds.set(
                if (iconBounds.isEmpty) Rect(0, 0, width, height)
                else iconBounds
            )
            Utilities.scaleRectAboutCenter(tmpBounds, ICON_VISIBLE_AREA_FACTOR)

            val centerX = tmpBounds.right - tmpBounds.width() * 0.084f
            val centerY = tmpBounds.top + tmpBounds.height() * 0.084f
            val radius = (tmpBounds.width() * 0.228f / 2f) * dotScale

            dotPaint.color = mDotColor
            canvas.drawCircle(centerX, centerY, radius, dotPaint)
        }
    }

    override fun setIconVisible(visible: Boolean) {
        val ctrl = controller ?: return
        if (!visible) {
            (tag as? ItemInfo)?.getTargetComponent()?.let {
                ctrl.hiddenIconComponent = it
                ctrl.hiddenIconSection = sectionId
            }
            ctrl.setIconPositionTrackingFrozen(true)
        } else {
            ctrl.hiddenIconComponent = null
            ctrl.hiddenIconSection = null
            ctrl.setIconPositionTrackingFrozen(false)
        }
        Snapshot.sendApplyNotifications()
    }

    override fun setForceHideDot(hide: Boolean) {
        if (mForceHideDot != hide) {
            mForceHideDot = hide
            invalidate()
        }
    }

    fun applyDotState(info: DotInfo?, animate: Boolean) {
        val wasDotted = dotInfo != null
        dotInfo = info
        val isDotted = info != null

        dotScale = if (isDotted) 1f else 0f

        if (wasDotted || isDotted) {
            mDotColor = Themes.getAttrColor(context, R.attr.notificationDotColor)
        }
        invalidate()
    }

    override fun getViewType(): Int = DraggableView.DRAGGABLE_ICON

    override fun prepareDrawDragView(): SafeCloseable = SafeCloseable { }

    override fun getWorkspaceVisualDragBounds(bounds: Rect) {
        getIconBounds(bounds)
    }

    override fun getSourceVisualDragBounds(bounds: Rect) {
        getIconBounds(bounds)
    }

    override fun setForceHideRing(hide: Boolean) {
    }

    fun setIconBounds(bounds: Rect) {
        iconBounds.set(bounds)
    }

    fun getIconBounds(outRect: Rect) {
        if (iconBounds.isEmpty) {
            outRect.set(0, 0, width, height)
        } else {
            outRect.set(iconBounds)
        }
    }

    fun setIconBoundsInDragLayer(left: Float, top: Float, right: Float, bottom: Float) {
        iconBoundsInDragLayer.set(left, top, right, bottom)
    }

    fun clearIconBoundsInDragLayer() {
        iconBoundsInDragLayer.setEmpty()
    }

    fun getIconBoundsInDragLayer(outRect: RectF): Boolean {
        if (iconBoundsInDragLayer.isEmpty) return false
        outRect.set(iconBoundsInDragLayer)
        return true
    }

    fun getIcon(): Drawable? = iconDrawable

    private var iconSizePx: Int = 0
    fun setIconSizePx(size: Int) {
        iconSizePx = size
    }
    fun getIconSizePx(): Int = iconSizePx
}
