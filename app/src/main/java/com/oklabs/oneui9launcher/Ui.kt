package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.WindowManager

/** Step 18: true when this phone really blurs the wallpaper behind the window (Android 12+, not switched off by battery saver / the maker). */
internal fun Context.windowBlurWorks(): Boolean = try {
    Build.VERSION.SDK_INT >= 31 && getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
} catch (e: Exception) {
    false
}

/** dp -> px, rounded. */
internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

/** Step 70: ONE height for the bottom sheets (Quick panel and Notifications page), so both are exactly equally tall: 78% of the screen. */
internal fun Context.sheetHeightPx(): Int = (resources.displayMetrics.heightPixels * 0.78f).toInt()

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
