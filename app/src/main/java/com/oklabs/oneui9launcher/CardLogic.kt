package com.oklabs.oneui9launcher

/** Step 16d: the small rules of the Quick panel's volume and media cards. Plain Kotlin, so they can be tested on a normal JVM. */
object CardLogic {

    /** The volume step (0..[max]) for a slider position [progress] (0..1). */
    fun levelFor(progress: Float, max: Int): Int =
        Math.round(progress.coerceIn(0f, 1f) * max.coerceAtLeast(0)).coerceIn(0, max.coerceAtLeast(0))

    /** The slider position (0..1) for the volume step [level] out of [max]. A phone with no steps (max 0) gives 0. */
    fun progressFor(level: Int, max: Int): Float =
        if (max <= 0) 0f else (level.coerceIn(0, max).toFloat() / max)

    /** Playback states as Android numbers them (PlaybackState.STATE_*). */
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3
    const val STATE_BUFFERING = 6

    /** True when the state means "sound is coming" (the play button then shows pause). */
    fun isPlaying(state: Int): Boolean = state == STATE_PLAYING || state == STATE_BUFFERING

    /**
     * Which of the phone's media sessions the panel shows: the first one that is playing, else the first one that has something
     * to show ([hasTitle]). -1 when there is none. [states] and [hasTitle] have one entry per session, in the phone's order.
     */
    fun pickSession(states: List<Int>, hasTitle: List<Boolean>): Int {
        val playing = states.indexOfFirst { isPlaying(it) }
        if (playing >= 0) return playing
        return hasTitle.indexOfFirst { it }
    }

    /**
     * Step 16e: in "Split" swipe-down mode, which half opens the Quick panel. [downFraction] is where the swipe started across the screen
     * (0 = left edge, 1 = right edge); [quickOnLeft] is the setting. The other half opens the notifications.
     */
    fun splitOpensQuick(downFraction: Float, quickOnLeft: Boolean): Boolean =
        if (quickOnLeft) downFraction < 0.5f else downFraction >= 0.5f
}
