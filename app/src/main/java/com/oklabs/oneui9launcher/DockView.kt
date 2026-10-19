package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * The Favorites tray: a soft translucent pill with [HomeLayout.DOCK_SLOTS] equal slots, no labels (like One UI).
 * A slot holds an app icon or a folder tile (or nothing). The tray only draws what [LauncherActivity] gives it
 * ([setViews]); dragging, folders and saving are the same as on the home pages.
 */
class DockView(context: Context) : FrameLayout(context) {

    private val slots = HomeLayout.DOCK_SLOTS

    init {
        background = roundRect(Color.argb(56, 255, 255, 255), context.dpf(32f))
        setPadding(context.dp(6), 0, context.dp(6), 0)
    }

    private fun slotWidth(total: Int): Int = ((total - paddingLeft - paddingRight) / slots).coerceAtLeast(0)

    /** Step 8a: the tray's look. [color] is any colour (its alpha is ignored), [opacity] 0 (invisible) to 100 (solid). */
    fun setStyle(color: Int, opacity: Int) {
        val alpha = (opacity.coerceIn(0, 100) * 255 / 100)
        background = roundRect(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), context.dpf(32f))
    }

    /** Shows [views] (slot number -> icon view); every other slot is empty. */
    fun setViews(views: Map<Int, View>) {
        removeAllViews()
        for ((slot, view) in views) {
            if (slot !in 0 until slots) continue
            addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            view.tag = slot
        }
        requestLayout()
    }

    /** Slot under ([x], [y]) in this view's own coordinates (a little slack above and below), or null. */
    fun slotAt(x: Float, y: Float): Int? {
        val sw = slotWidth(width)
        if (sw <= 0) return null
        val slack = context.dp(10)
        if (y < -slack || y > height + slack) return null
        val px = x - paddingLeft
        if (px < 0f) return null
        val slot = (px / sw).toInt()
        return if (slot < slots) slot else null
    }

    /** Where a slot is, in this view's own coordinates. */
    fun slotBounds(slot: Int): Rect {
        val sw = slotWidth(width)
        val left = paddingLeft + slot * sw
        return Rect(left, 0, left + sw, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val sw = slotWidth(measuredWidth)
        val childWidth = View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.EXACTLY)
        val childHeight = View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.AT_MOST)
        for (i in 0 until childCount) getChildAt(i).measure(childWidth, childHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val sw = slotWidth(right - left)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val slot = (child.tag as? Int) ?: continue
            val l = paddingLeft + slot * sw
            val t = ((bottom - top) - child.measuredHeight) / 2
            child.layout(l, t, l + sw, t + child.measuredHeight)
        }
    }

    companion object {
        /** Same size as the Apps screen icons, so they are never stretched. */
        const val ICON_DP = 54
        const val HEIGHT_DP = 84
    }
}
