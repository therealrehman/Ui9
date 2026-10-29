package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Tiny vector-style icons drawn in code: magnifier, clear (x) and the three-dot menu. */
class GlyphView(context: Context, private val kind: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Colour of the drawing: white on the wallpaper, [Look.text] on a panel. */
    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)
        val cx = w / 2f
        val cy = h / 2f

        when (kind) {
            SEARCH -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = tint
                paint.strokeWidth = s * 0.085f
                val r = s * 0.27f
                val ox = cx - s * 0.05f
                val oy = cy - s * 0.05f
                canvas.drawCircle(ox, oy, r, paint)
                val d = r * 0.7071f
                canvas.drawLine(ox + d, oy + d, cx + s * 0.34f, cy + s * 0.34f, paint)
            }
            CLEAR -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(80, Color.red(tint), Color.green(tint), Color.blue(tint))
                canvas.drawCircle(cx, cy, s * 0.27f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = tint
                paint.strokeWidth = s * 0.06f
                val d = s * 0.1f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
            }
            MORE -> {
                paint.style = Paint.Style.FILL
                paint.color = tint
                val r = s * 0.055f
                for (i in -1..1) {
                    canvas.drawCircle(cx, cy + i * s * 0.17f, r, paint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isClickable) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> alpha = 0.5f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> alpha = 1f
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val SEARCH = 0
        const val CLEAR = 1
        const val MORE = 2
    }
}
