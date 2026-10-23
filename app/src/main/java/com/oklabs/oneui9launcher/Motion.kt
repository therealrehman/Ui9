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

    /** Starts gently, leaves quickly (panels closing). */
    val CLOSE: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    const val OPEN_MS = 300L
    const val CLOSE_MS = 240L

    /** How blurred (in pixels) a panel is when it is not (yet / any more) fully there. */
    const val BLUR_PX = 26f

    /** 0..1 -> 0..1 reaching full at [full] (e.g. 0.6 = fully visible at 60% of the move). */
    fun ramp(p: Float, full: Float): Float = (p / full).coerceIn(0f, 1f)

    /** Smoothstep: a 0..1 ramp with soft ends. */
    fun smooth(p: Float): Float {
        val c = p.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /**
     * Step 33: opacity while a panel goes away, 1 -> 0 as [q] goes 0 -> 1. It is about 30% at two thirds of the way and then lands softly on 0
     * (no sudden last drop).
     */
    fun fadeOut(q: Float): Float = 1f - smooth(q)

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
