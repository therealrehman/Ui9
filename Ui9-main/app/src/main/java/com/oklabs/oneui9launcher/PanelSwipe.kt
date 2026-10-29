package com.oklabs.oneui9launcher

import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import kotlin.math.abs

/**
 * Step 59: the swipe up that closes the Quick panel and the Notifications page, now like the new phones: the panel FOLLOWS the finger,
 * and when the finger lifts the speed and the distance decide (close, or glide back). Before, the panel was closed the moment the finger
 * had moved a fixed distance, in 80 ms: it felt like a switch, and a quick flick that ended before that distance did nothing at all.
 * All numbers are here so both screens behave the same. Pure functions (time and positions are passed in), easy to test.
 */
object PanelSwipe {
    /** The finger must move this far up (dp) and clearly more up than sideways before the panel starts to follow it. */
    const val START_DP = 18

    /** Lifting the finger after this much upward movement (dp) closes the panel. */
    const val CLOSE_DP = 80

    /** A flick faster than this (dp per second) closes the panel after a shorter move... */
    const val FLING_DP_PER_S = 700

    /** ...but only if the finger moved at least this far (dp), so a nervous twitch does nothing. */
    const val FLING_MIN_DP = 28

    /** The rest of the closing glide and the glide back (milliseconds). Normal speed, not the 80 ms of a tap on Back. */
    const val CLOSE_MS = 220L
    const val BACK_MS = 200L

    val GLIDE: Interpolator = DecelerateInterpolator(1.4f)

    /** True when a movement of ([dx], [dy]) pixels (dy > 0 = up) is "a swipe up" that should start dragging the panel. */
    fun startsDrag(dx: Float, dy: Float, density: Float): Boolean = dy > START_DP * density && dy > abs(dx) * 1.5f

    /**
     * What to do when the finger lifts. [dyPx]: how far up the finger went in total, [vyPxPerS]: vertical speed (negative = up).
     * True = close the panel, false = glide back.
     */
    fun shouldClose(dyPx: Float, vyPxPerS: Float, density: Float): Boolean {
        if (dyPx >= CLOSE_DP * density) return true
        return -vyPxPerS >= FLING_DP_PER_S * density && dyPx >= FLING_MIN_DP * density
    }

    /** How long the rest of the close takes: the less is left, the shorter (never below 110 ms). [q] = how far it is already, 0..1. */
    fun closeMs(q: Float): Long = (CLOSE_MS * (1f - q.coerceIn(0f, 1f))).toLong().coerceIn(110L, CLOSE_MS)
}

/** Remembers the last few finger positions (raw screen Y) so the speed at lift-off can be read. Not tied to a view, so the panel moving under the finger does not matter. */
class FlingTracker {
    private val times = LongArray(8)
    private val ys = FloatArray(8)
    private var count = 0
    private var next = 0

    fun clear() {
        count = 0
        next = 0
    }

    fun add(timeMs: Long, y: Float) {
        times[next] = timeMs
        ys[next] = y
        next = (next + 1) % times.size
        if (count < times.size) count++
    }

    /** Speed in pixels per second over roughly the last 120 ms (positive = moving down, negative = up). 0 when there is not enough to tell. */
    fun velocity(): Float {
        if (count < 2) return 0f
        val newest = (next - 1 + times.size) % times.size
        val tNew = times[newest]
        val yNew = ys[newest]
        var tOld = tNew
        var yOld = yNew
        for (i in 1 until count) {
            val idx = (newest - i + times.size) % times.size
            if (tNew - times[idx] > 120L) break
            tOld = times[idx]
            yOld = ys[idx]
        }
        val dt = tNew - tOld
        return if (dt <= 0L) 0f else (yNew - yOld) * 1000f / dt
    }
}
