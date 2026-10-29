package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Huawei plan, step 2: the settings of the top handle (a thin strip right under the status bar; a swipe down on it opens the Quick panel). */
object TopHandle {
    const val PREF_ON = "th_on"
    const val PREF_WIDTH = "th_width" // percent of the screen width
    const val PREF_HEIGHT = "th_height" // dp, the touch height
    const val PREF_OFFSET = "th_offset" // dp below the status bar
    const val PREF_EDGE = "th_edge" // Step 63: 0 top (under the status bar), 1 bottom (above the navigation bar)
    const val PREF_SIDE = "th_side" // 0 centre, 1 left, 2 right
    const val PREF_VISIBLE = "th_visible" // true = a pill is drawn, false = invisible
    const val PREF_HAPTIC = "th_haptic"
    const val PREF_SHAPE = "th_shape" // Step 58: 0 = small pill, 1 = bar across the whole strip
    const val PREF_THICK = "th_thick" // Step 58: dp, how thick the drawn pill / bar is (the touch height is separate)
    const val PREF_COLOR = "th_color" // Step 58: 0 = same as the Quick panel, 1 = white, 2 = a gradient
    const val PREF_GRAD = "th_grad" // Step 58: which gradient (index in Gradients.all) when PREF_COLOR = 2

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(c: Context): Boolean = prefs(c).getBoolean(PREF_ON, true)
    fun widthPct(c: Context): Int = clampWidth(prefs(c).getInt(PREF_WIDTH, 60))
    fun heightDp(c: Context): Int = clampHeight(prefs(c).getInt(PREF_HEIGHT, 22))
    fun offsetDp(c: Context): Int = clampOffset(prefs(c).getInt(PREF_OFFSET, 0))
    fun atBottom(c: Context): Boolean = prefs(c).getInt(PREF_EDGE, 1) == 1
    fun side(c: Context): Int = prefs(c).getInt(PREF_SIDE, 0).coerceIn(0, 2)
    fun visible(c: Context): Boolean = prefs(c).getBoolean(PREF_VISIBLE, true)
    fun haptic(c: Context): Boolean = prefs(c).getBoolean(PREF_HAPTIC, true)

    fun shape(c: Context): Int = prefs(c).getInt(PREF_SHAPE, 0).coerceIn(0, 1)
    fun thickDp(c: Context): Int = clampThick(prefs(c).getInt(PREF_THICK, 4))
    fun colorMode(c: Context): Int = prefs(c).getInt(PREF_COLOR, 0).coerceIn(0, 2)
    fun gradIndex(c: Context): Int = prefs(c).getInt(PREF_GRAD, 0).coerceIn(0, Gradients.all.size - 1)

    /** The colours the strip is drawn with: the Quick panel's own colour, white, or the chosen gradient (one colour = plain). */
    fun colors(c: Context): IntArray = when (colorMode(c)) {
        1 -> intArrayOf(Color.WHITE)
        2 -> Gradients.all[gradIndex(c)].colors.copyOf()
        else -> PanelTheme.stripColors(c, PanelLook.dark)
    }

    fun clampThick(v: Int) = v.coerceIn(2, 24)
    fun clampWidth(v: Int) = v.coerceIn(20, 100)
    fun clampHeight(v: Int) = v.coerceIn(8, 48)
    fun clampOffset(v: Int) = v.coerceIn(0, 60)

    /** True when a drag of ([dx], [dy]) pixels is "a swipe down": far enough down and mostly down. Pure, so it can be tested. */
    fun isSwipeUp(dx: Float, dy: Float, slop: Float): Boolean = -dy > slop * 1.5f && -dy > abs(dx) * 0.9f

    fun isSwipeDown(dx: Float, dy: Float, slop: Float): Boolean = dy > slop * 1.5f && dy > abs(dx) * 0.9f
}

/** The strip itself. [onSwipe] runs once per touch, when a swipe down is recognised. */
class TopHandleView(context: Context, var showPill: Boolean, var buzz: Boolean, private val onOpen: (Boolean) -> Unit) : View(context) {
    /** Step 6: [onOpen] gets false = open the Quick panel, true = open the Notifications page. These say what the extra gestures do. */
    var splitSides = false
    var doubleAction = MoreTriggers.ACT_NONE
    var longAction = MoreTriggers.ACT_NONE
    private var lastTapAt = 0L
    private var moved = false
    private val longRun = Runnable {
        if (!fired && !moved && longAction != MoreTriggers.ACT_NONE) {
            fired = true
            if (buzz) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onOpen(longAction == MoreTriggers.ACT_NOTIFS)
        }
    }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var downX = 0f
    private var downY = 0f
    private var downLocalX = 0f
    var atBottom = false // Step 63: the strip sits above the navigation bar, so the opening swipe goes UP
    private var fired = false

    /** Step 58: how the strip is drawn. [shape] 0 = small pill, 1 = a bar over the whole width of the strip; [thickPx] = drawn thickness; [colors] = 1 colour or a gradient. */
    var shape = 0
    var thickPx = 0f
    var colors: IntArray = intArrayOf(Color.argb(150, 255, 255, 255))

    override fun onDraw(canvas: Canvas) {
        if (!showPill) return
        val h = if (thickPx > 0f) minOf(thickPx, height.toFloat()) else context.dpf(4f)
        val w = if (shape == 1) width.toFloat() else minOf(width * 0.5f, context.dpf(120f))
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        rect.set(left, top, left + w, top + h)
        val shown = Gradients.withAlpha(colors, 235)
        paint.style = Paint.Style.FILL
        if (shown.size == 1) {
            paint.shader = null
            paint.color = shown[0]
        } else {
            paint.shader = LinearGradient(left, 0f, left + w, 0f, shown, null, Shader.TileMode.CLAMP)
        }
        val r = h / 2f
        canvas.drawRoundRect(rect, r, r, paint)
        paint.shader = null
        // a thin light edge so a dark colour is still seen over a dark app
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpf(0.8f)
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                downLocalX = ev.x
                fired = false
                moved = false
                removeCallbacks(longRun)
                if (longAction != MoreTriggers.ACT_NONE) postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop) {
                    moved = true
                    removeCallbacks(longRun)
                }
                if (!fired && (if (atBottom) TopHandle.isSwipeUp(ev.rawX - downX, ev.rawY - downY, slop) else TopHandle.isSwipeDown(ev.rawX - downX, ev.rawY - downY, slop))) {
                    fired = true
                    if (buzz) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onOpen(MoreTriggers.swipeToNotifs(downLocalX, width, splitSides))
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRun)
                if (!fired && !moved && doubleAction != MoreTriggers.ACT_NONE) {
                    val now = ev.eventTime
                    if (lastTapAt != 0L && now - lastTapAt <= ViewConfiguration.getDoubleTapTimeout()) {
                        lastTapAt = 0L
                        if (buzz) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onOpen(doubleAction == MoreTriggers.ACT_NOTIFS)
                    } else {
                        lastTapAt = now
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longRun)
        }
        return true
    }
}
