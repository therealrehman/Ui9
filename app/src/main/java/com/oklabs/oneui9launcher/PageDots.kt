package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** The little page-indicator dots above the dock. */
class PageDots(context: Context) : View(context) {

    private var count = 1
    private var active = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Colour of the dots: white on the wallpaper (default), the panel's text colour on the Apps screen. */
    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    fun setPages(total: Int, current: Int) {
        count = total.coerceAtLeast(1)
        active = current.coerceIn(0, count - 1)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dot = context.dpf(DOT_DP)
        val gap = context.dpf(GAP_DP)
        val width = (count * dot + (count - 1) * gap).toInt() + context.dp(2)
        setMeasuredDimension(width, context.dp(10))
    }

    override fun onDraw(canvas: Canvas) {
        val dot = context.dpf(DOT_DP)
        val gap = context.dpf(GAP_DP)
        val cy = height / 2f
        for (i in 0 until count) {
            val cx = context.dpf(1f) + dot / 2f + i * (dot + gap)
            val isActive = i == active
            paint.color = if (isActive) tint else Color.argb(115, Color.red(tint), Color.green(tint), Color.blue(tint))
            canvas.drawCircle(cx, cy, if (isActive) dot / 2f + context.dpf(0.5f) else dot / 2f, paint)
        }
    }

    private companion object {
        const val DOT_DP = 6f
        const val GAP_DP = 9f
    }
}
