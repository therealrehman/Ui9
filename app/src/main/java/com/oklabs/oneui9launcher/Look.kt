package com.oklabs.oneui9launcher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Dark or light look of the launcher's panels (Apps screen, folder card, quick menu, widget picker, dock card).
 * The home screen itself keeps white labels, because they sit on your wallpaper.
 * Mode: follow the phone (default), always light, or always dark. Changing it restarts the screen once.
 */
object Look {

    const val AUTO = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val PREFS = "launcher"
    private const val KEY = "theme_mode"

    private var mode = AUTO
    private var systemDark = true

    /** True while the panels are dark. */
    val dark: Boolean
        get() = when (mode) {
            LIGHT -> false
            DARK -> true
            else -> systemDark
        }

    /** Reads the saved mode and the phone's current dark / light setting. Call before any view is built. */
    fun load(context: Context) {
        mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, AUTO).coerceIn(AUTO, DARK)
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        systemDark = night != Configuration.UI_MODE_NIGHT_NO
    }

    /** Auto -> Light -> Dark -> Auto. Saved; the caller restarts the screen so every panel is built again. */
    fun cycle(context: Context) {
        mode = (mode + 1) % 3
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
    }

    /** Step 13: Settings > Look and feel sets the mode directly ([AUTO], [LIGHT] or [DARK]). */
    fun set(context: Context, newMode: Int) {
        mode = newMode.coerceIn(AUTO, DARK)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
    }

    /** The saved mode (for the Settings chips). */
    fun savedMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, AUTO).coerceIn(AUTO, DARK)

    fun modeName(): String = when (mode) {
        LIGHT -> "Light"
        DARK -> "Dark"
        else -> "Auto"
    }

    /** Main text colour of a panel. */
    val text: Int get() = if (dark) Color.WHITE else Color.rgb(22, 24, 30)

    /** [text] with transparency [alpha] (0..255). */
    fun textA(alpha: Int): Int = withAlpha(text, alpha)

    /** Soft highlights, rings and field backgrounds: white in dark mode, black in light mode, with transparency [alpha]. */
    fun inkA(alpha: Int): Int = if (dark) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 0, 0, 0)

    /**
     * Step 18: the dark "frosted glass" behind the panel buttons and cards (like the reference phone): a deep dark tint in dark mode,
     * milky white in light mode, with transparency [alpha] so the blurred wallpaper shines through.
     */
    fun glass(alpha: Int): Int =
        PanelTheme.glass(alpha, dark, if (dark) Color.argb(alpha, 14, 10, 30) else Color.argb(alpha, 255, 255, 255))

    /** Colour of a floating panel (card, sheet, menu) with transparency [alpha]. */
    fun panel(alpha: Int): Int =
        if (dark) Color.argb(alpha, 34, 36, 44) else Color.argb(alpha, 250, 251, 254)

    /** Colour of the veil over the home screen behind the Apps screen. */
    fun veil(alpha: Int): Int =
        if (dark) Color.argb(alpha, 8, 10, 16) else Color.argb(alpha, 246, 247, 251)

    /** How strong the Apps screen veil gets (a light veil needs more to keep the dark text readable). */
    val veilStrength: Int get() = if (dark) 150 else 215

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
