package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** dp -> px, rounded. */
internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

/** dp -> px as float (corner radii, shadows). */
internal fun Context.dpf(value: Float): Float =
    value * resources.displayMetrics.density

/** Flat rounded-rectangle background. */
internal fun roundRect(color: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(color)
    }
