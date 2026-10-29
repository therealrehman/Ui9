package com.oklabs.oneui9launcher

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/** Step 31: one set of curves and times for every slide / fade, so the whole launcher moves the same way. */
object Motion {
    /** Starts fast, lands very softly (panels opening). */
    val OPEN: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** Step 34: gone at once (panels closing): the move starts at full speed and only slows at the very end. */
    val CLOSE: Interpolator = PathInterpolator(0.15f, 0.7f, 0.3f, 1f)

    /** Step 35: both are short: the panel opens in 170 ms and is gone in 110 ms. */
    const val OPEN_MS = 130L
    const val CLOSE_MS = 80L

    /** How blurred (in pixels) a panel is when it is not (yet / any more) fully there. Step 34: about a third of before (was 26). */
    const val BLUR_PX = 9f

    /** 0..1 -> 0..1 reaching full at [full] (e.g. 0.6 = fully visible at 60% of the move). */
    fun ramp(p: Float, full: Float): Float = (p / full).coerceIn(0f, 1f)

    /** Smoothstep: a 0..1 ramp with soft ends. */
    fun smooth(p: Float): Float {
        val c = p.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /**
     * Step 34: opacity while a panel goes away, 1 -> 0 as [q] goes 0 -> 1. The moment the swipe starts it drops to about 20% (within the first
     * third of the move) and then lands softly on 0: the panel is gone at once, without a hard cut.
     */
    fun fadeOut(q: Float): Float = smooth(1f - ramp(q, 0.85f))

    /** Step 35: the opacity a panel has the moment a swipe up is accepted: straight from 100% to 20%, then it runs down to 0 (see [fadeOut]). */
    const val CLOSE_START_ALPHA = 1f

    /** Blurs [view] and everything in it by [radius] pixels (0 = sharp). Android 12+; older phones simply skip the blur. */
    fun setBlur(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            view.setRenderEffect(if (radius < 0.5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        } catch (e: Exception) {
            // the phone can not blur: the panel just fades
        }
    }
}
