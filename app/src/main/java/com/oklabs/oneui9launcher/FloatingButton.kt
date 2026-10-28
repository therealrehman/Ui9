package com.oklabs.oneui9launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/** Huawei plan, step 4: the settings of the floating button (a small round button over every app; tap opens the Quick panel). */
object FloatButton {
    const val PREF_ON = "fb_on"
    const val PREF_SIZE = "fb_size" // dp
    const val PREF_ALPHA = "fb_alpha" // percent
    const val PREF_SNAP = "fb_snap" // true = glues itself to the left / right edge after a drag
    const val PREF_FX = "fb_fx" // position as 0..1000 of the free width
    const val PREF_FY = "fb_fy" // position as 0..1000 of the free height
    const val PREF_HAPTIC = "fb_haptic"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(c: Context): Boolean = prefs(c).getBoolean(PREF_ON, false)
    fun sizeDp(c: Context): Int = clampSize(prefs(c).getInt(PREF_SIZE, 44))
    fun alphaPct(c: Context): Int = clampAlpha(prefs(c).getInt(PREF_ALPHA, 70))
    fun snap(c: Context): Boolean = prefs(c).getBoolean(PREF_SNAP, true)
    fun fx(c: Context): Int = prefs(c).getInt(PREF_FX, 1000).coerceIn(0, 1000)
    fun fy(c: Context): Int = prefs(c).getInt(PREF_FY, 350).coerceIn(0, 1000)
    fun haptic(c: Context): Boolean = prefs(c).getBoolean(PREF_HAPTIC, true)

    fun clampSize(v: Int) = v.coerceIn(28, 72)
    fun clampAlpha(v: Int) = v.coerceIn(20, 100)

    /** Saves where the button was left (pixels -> 0..1000 of the free space). */
    fun savePosition(c: Context, xPx: Int, yPx: Int, freeW: Int, freeH: Int) {
        val fx = if (freeW > 0) (xPx * 1000f / freeW).toInt().coerceIn(0, 1000) else 1000
        val fy = if (freeH > 0) (yPx * 1000f / freeH).toInt().coerceIn(0, 1000) else 350
        prefs(c).edit().putInt(PREF_FX, fx).putInt(PREF_FY, fy).apply()
    }

    fun resetPosition(c: Context) {
        prefs(c).edit().putInt(PREF_FX, 1000).putInt(PREF_FY, 350).apply()
    }

    /** True when a touch that moved ([dx], [dy]) pixels is a drag and not a tap. Pure, so it can be tested. */
    fun isDrag(dx: Float, dy: Float, slop: Float): Boolean = abs(dx) > slop || abs(dy) > slop
}

/**
 * The button itself. [onTap] runs for a tap; while dragging, [onMove] gets the wanted top-left corner in pixels
 * and [onRelease] the final one (the service moves the window and saves / snaps).
 */
class FloatingButtonView(
    context: Context,
    var buzz: Boolean,
    private val onTap: () -> Unit,
    private val onMove: (Int, Int) -> Unit,
    private val onRelease: (Int, Int) -> Unit
) : View(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    /** Where the window is now (the service keeps these up to date). */
    var winX = 0
    var winY = 0

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(235, 30, 34, 44)
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpf(1f)
        paint.color = Color.argb(90, 255, 255, 255)
        canvas.drawCircle(width / 2f, height / 2f, r - paint.strokeWidth / 2f, paint)
        // four small rounded tiles (the panel's symbol)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val tile = r * 0.46f
        val gap = r * 0.12f
        val cx = width / 2f
        val cy = height / 2f
        val rad = tile * 0.22f
        for (i in 0..1) for (j in 0..1) {
            val left = if (i == 0) cx - gap / 2f - tile else cx + gap / 2f
            val top = if (j == 0) cy - gap / 2f - tile else cy + gap / 2f
            rect.set(left, top, left + tile, top + tile)
            canvas.drawRoundRect(rect, rad, rad, paint)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                startX = winX
                startY = winY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!dragging && FloatButton.isDrag(dx, dy, slop)) {
                    dragging = true
                    if (buzz) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                if (dragging) onMove(startX + dx.toInt(), startY + dy.toInt())
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onRelease(winX, winY)
                } else {
                    if (buzz) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap()
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onRelease(winX, winY)
                dragging = false
            }
        }
        return true
    }

    /** Smoothly moves the button from its place to ([toX], [toY]) (used for the edge snap). */
    fun animateTo(toX: Int, toY: Int, step: (Int, Int) -> Unit, done: () -> Unit) {
        val fromX = winX
        val fromY = winY
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 180
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            val t = it.animatedValue as Float
            step((fromX + (toX - fromX) * t).toInt(), (fromY + (toY - fromY) * t).toInt())
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                done()
            }
        })
        a.start()
    }
}
