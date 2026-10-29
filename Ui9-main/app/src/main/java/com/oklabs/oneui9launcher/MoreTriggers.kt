package com.oklabs.oneui9launcher

import android.content.Context
import kotlin.math.sqrt

/** Huawei plan, step 6: more ways to open the Quick panel: double-tap / long-press on the top handle, side swipes, shake. */
object MoreTriggers {
    const val PREF_DOUBLE = "mt_double" // action of a double-tap on the handle
    const val PREF_LONG = "mt_long" // action of a long-press on the handle
    const val PREF_SPLIT = "mt_split" // side swipes: swipe down on the left half = Notifications, right half = Quick panel
    const val PREF_SHAKE = "mt_shake"
    const val PREF_SHAKE_LEVEL = "mt_shake_level" // 0 gentle, 1 normal, 2 firm

    /** What a gesture does. */
    const val ACT_NONE = 0
    const val ACT_PANEL = 1
    const val ACT_NOTIFS = 2

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun doubleTap(c: Context): Int = prefs(c).getInt(PREF_DOUBLE, ACT_NONE).coerceIn(0, 2)
    fun longPress(c: Context): Int = prefs(c).getInt(PREF_LONG, ACT_NONE).coerceIn(0, 2)
    fun split(c: Context): Boolean = prefs(c).getBoolean(PREF_SPLIT, false)
    fun shake(c: Context): Boolean = prefs(c).getBoolean(PREF_SHAKE, false)
    fun shakeLevel(c: Context): Int = prefs(c).getInt(PREF_SHAKE_LEVEL, 1).coerceIn(0, 2)

    /** Which page a swipe down at [x] (of [width]) opens: false = Quick panel, true = Notifications page. Pure, so it can be tested. */
    fun swipeToNotifs(x: Float, width: Int, split: Boolean): Boolean = split && x < width / 2f

    /** Strength (in g) a jolt must reach for each sensitivity level. */
    fun threshold(level: Int): Float = when (level) {
        0 -> 2.2f
        2 -> 3.4f
        else -> 2.7f
    }
}

/**
 * Finds "a shake": two strong jolts within 0.9 s, then 1.5 s of quiet. Feed it every accelerometer reading.
 * Pure (time is passed in), so it can be tested.
 */
class ShakeDetector(private val threshold: Float) {
    private var firstJolt = 0L
    private var lastJolt = 0L
    private var count = 0
    private var blockedUntil = 0L

    /** Returns true once, when a shake is recognised. [x], [y], [z] are in m/s^2, [nowMs] is any steady clock. */
    fun onReading(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (nowMs < blockedUntil) return false
        val g = sqrt(x * x + y * y + z * z) / 9.80665f
        if (g < threshold) return false
        if (nowMs - lastJolt < 120) return false // one jolt gives many readings
        if (count == 0 || nowMs - firstJolt > 900) {
            count = 1
            firstJolt = nowMs
        } else {
            count++
        }
        lastJolt = nowMs
        if (count >= 2) {
            count = 0
            blockedUntil = nowMs + 1500
            return true
        }
        return false
    }
}
