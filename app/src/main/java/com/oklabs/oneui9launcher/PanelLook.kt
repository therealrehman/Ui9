package com.oklabs.oneui9launcher

import android.graphics.Color

/**
 * Step 24b: the look of the Quick panel and the Notifications page, which **always overrides the phone's dark / light mode**.
 * They keep their own frosted-glass look (dark glass with white text, like the reference phone) whatever the phone is set to;
 * only an explicit "Light" in Settings > Look and feel > Panels makes them light. ("Auto" and "Dark" both mean the dark glass here.)
 * Same functions as [Look], so the panel code only had to change the name.
 */
object PanelLook {

    /** True while the panel is dark. Never follows the phone. */
    val dark: Boolean get() = !Look.lightChosen

    val text: Int get() = if (dark) Color.WHITE else Color.rgb(22, 24, 30)

    fun textA(alpha: Int): Int = Color.argb(alpha, Color.red(text), Color.green(text), Color.blue(text))

    fun inkA(alpha: Int): Int = if (dark) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 0, 0, 0)

    /** The frosted glass of the panel's buttons and cards (tinted by the chosen panel colour). */
    fun glass(alpha: Int): Int =
        PanelTheme.glass(alpha, dark, if (dark) Color.argb(alpha, 14, 10, 30) else Color.argb(alpha, 255, 255, 255))

    fun panel(alpha: Int): Int =
        if (dark) Color.argb(alpha, 34, 36, 44) else Color.argb(alpha, 250, 251, 254)

    fun veil(alpha: Int): Int =
        if (dark) Color.argb(alpha, 8, 10, 16) else Color.argb(alpha, 246, 247, 251)
}
