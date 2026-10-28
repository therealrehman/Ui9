package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
    const val PREF_SIDE = "th_side" // 0 centre, 1 left, 2 right
    const val PREF_VISIBLE = "th_visible" // true = a pill is drawn, false = invisible
    const val PREF_HAPTIC = "th_haptic"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(c: Context): Boolean = prefs(c).getBoolean(PREF_ON, true)
    fun widthPct(c: Context): Int = clampWidth(prefs(c).getInt(PREF_WIDTH, 60))
    fun heightDp(c: Context): Int = clampHeight(prefs(c).getInt(PREF_HEIGHT, 22))
    fun offsetDp(c: Context): Int = clampOffset(prefs(c).getInt(PREF_OFFSET, 0))
    fun side(c: Context): Int = prefs(c).getInt(PREF_SIDE, 0).coerceIn(0, 2)
    fun visible(c: Context): Boolean = prefs(c).getBoolean(PREF_VISIBLE, true)
    fun haptic(c: Context): Boolean = prefs(c).getBoolean(PREF_HAPTIC, true)

    fun clampWidth(v: Int) = v.coerceIn(20, 100)
    fun clampHeight(v: Int) = v.coerceIn(8, 48)
    fun clampOffset(v: Int) = v.coerceIn(0, 60)

    /** True when a drag of ([dx], [dy]) pixels is "a swipe down": far enough down and mostly down. Pure, so it can be tested. */
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
    private var fired = false

    override fun onDraw(canvas: Canvas) {
        if (!showPill) return
        val h = context.dpf(4f)
        val w = minOf(width * 0.5f, context.dpf(120f))
        rect.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        paint.color = Color.argb(150, 255, 255, 255)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
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
                if (!fired && TopHandle.isSwipeDown(ev.rawX - downX, ev.rawY - downY, slop)) {
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
