package com.oklabs.oneui9launcher

/** Step 16c: the small rules of the Quick panel's notification list. Plain Kotlin (no Android classes), so they can be tested on a normal JVM. */
object NotifLogic {

    /** At most this many notifications are kept and shown. */
    const val MAX_ITEMS = 40

    /** "now", "5m", "3h", "2d": how long ago ([whenMs] is when it was posted, [nowMs] is now). A time in the future counts as now. */
    fun ago(nowMs: Long, whenMs: Long): String {
        val minutes = (nowMs - whenMs) / 60_000L
        return when {
            minutes < 1L -> "now"
            minutes < 60L -> "${minutes}m"
            minutes < 24L * 60L -> "${minutes / 60L}h"
            else -> "${minutes / (24L * 60L)}d"
        }
    }

    /** Newest first, and no more than [MAX_ITEMS]. */
    fun <T> newestFirst(items: List<T>, whenOf: (T) -> Long): List<T> =
        items.sortedByDescending(whenOf).take(MAX_ITEMS)

    /** The first non-blank line of [candidates] (title, then text...), or "" when all are blank. */
    fun firstText(vararg candidates: CharSequence?): String =
        candidates.firstOrNull { !it.isNullOrBlank() }?.toString()?.trim() ?: ""

    /** A swipe of [dx] pixels on a card [width] pixels wide throws it away when it went at least 35% of the width, or when it was a fast flick (>= 900 px/s, and at least 15%). */
    fun shouldDismiss(dx: Float, width: Float, velocityX: Float): Boolean {
        val far = kotlin.math.abs(dx) >= width * 0.35f
        val flick = kotlin.math.abs(velocityX) >= 900f && kotlin.math.abs(dx) >= width * 0.15f && (dx > 0) == (velocityX > 0)
        return far || flick
    }
}
