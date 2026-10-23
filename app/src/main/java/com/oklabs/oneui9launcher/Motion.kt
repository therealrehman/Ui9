package com.oklabs.oneui9launcher

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/** Step 31: one set of curves and times for every slide / fade, so the whole launcher moves the same way. */
object Motion {
    /** Starts fast, lands very softly (panels opening). */
    val OPEN: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Starts gently, leaves quickly (panels closing). */
    val CLOSE: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    const val OPEN_MS = 300L
    const val CLOSE_MS = 220L

    /** 0..1 -> 0..1 reaching full at [full] (e.g. 0.6 = fully visible at 60% of the move). */
    fun ramp(p: Float, full: Float): Float = (p / full).coerceIn(0f, 1f)
}
