package com.oklabs.oneui9launcher

import android.content.Context
import android.content.res.Configuration

/**
 * Step 64: the size of the parts of the Quick panel, one percent for each kind of part and SEPARATELY for portrait and landscape
 * (Settings > Quick panel > Sizes). 100 = the normal size. Read every time the panel is built, so a change shows the next time it opens.
 */
object QuickSize {
    const val BUTTON = "button" // the round buttons
    const val WIDE = "wide" // the wide Wi-Fi / Bluetooth style buttons (and Media output / Devices)
    const val SLIDER = "slider" // brightness and volume sliders

    const val MIN = 60
    const val MAX = 160

    fun key(part: String, landscape: Boolean): String = "qs_" + part + (if (landscape) "_l" else "_p")

    fun isLandscape(c: Context): Boolean = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun pct(c: Context, part: String, landscape: Boolean = isLandscape(c)): Int =
        c.getSharedPreferences("launcher", Context.MODE_PRIVATE).getInt(key(part, landscape), 100).coerceIn(MIN, MAX)

    /** [baseDp] scaled for [part] in the current orientation, in pixels. */
    fun dp(c: Context, baseDp: Int, part: String): Int = (baseDp * c.resources.displayMetrics.density * pct(c, part) / 100f + 0.5f).toInt()

    /** The same for a text size or other plain number. */
    fun scale(c: Context, base: Float, part: String): Float = base * pct(c, part) / 100f
}
