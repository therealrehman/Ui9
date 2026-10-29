package com.oklabs.oneui9launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * Themed icons (Step 8d): every app icon gets one colour family. The icon is drawn as a soft plate with the app's glyph on it,
 * both taken from ONE tint, like Android's own themed icons. The tint is the wallpaper's main colour (Android 8.1+) or one
 * of a few chosen colours. In dark panel mode (see [Look]) the plate is dark and the glyph light; in light mode the other way round.
 *
 * [IconShaper] reads [enabled] / [plate] / [glyph] every time it draws an icon, so after a change the caller only has to load the apps again.
 */
object IconTheme {

    private const val PREFS = "launcher"
    private const val KEY = "icon_theme"

    private const val OFF = 0
    private const val WALLPAPER = 1 // 2.. = a colour of [PALETTE]

    private val PALETTE = intArrayOf(
        Color.rgb(59, 123, 255),
        Color.rgb(138, 92, 255),
        Color.rgb(47, 191, 113),
        Color.rgb(255, 111, 165),
        Color.rgb(255, 150, 60)
    )
    private val NAMES = arrayOf("Blue", "Purple", "Green", "Pink", "Orange")

    private var mode = OFF

    @Volatile
    var enabled = false
        private set

    /** Colour of the icon background. */
    @Volatile
    var plate = 0
        private set

    /** Colour of the app's glyph on the plate. */
    @Volatile
    var glyph = 0
        private set

    /** Reads the saved choice. Call after [Look.load] and before any icon is drawn. */
    fun load(context: Context) {
        mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, OFF).coerceIn(OFF, WALLPAPER + PALETTE.size)
        compute(context)
    }

    /** Off -> Wallpaper -> Blue -> Purple -> Green -> Pink -> Orange -> Off. Saved; the caller loads the apps again. */
    fun cycle(context: Context) {
        mode = (mode + 1) % (WALLPAPER + PALETTE.size + 1)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
        compute(context)
    }

    /** Step 13: Settings > Icons picks the mode directly: 0 = Off, 1 = Wallpaper, 2.. = a colour of [choiceNames]. */
    fun setMode(context: Context, newMode: Int) {
        mode = newMode.coerceIn(OFF, WALLPAPER + PALETTE.size)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
        compute(context)
    }

    /** The saved mode (index into [choiceNames]). */
    fun savedMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, OFF).coerceIn(OFF, WALLPAPER + PALETTE.size)

    /** Names of every mode in order: Off, Wallpaper, then the colours. */
    val choiceNames: List<String> get() = listOf("Off", "Wallpaper") + NAMES.toList()

    /** Looks at the wallpaper again (it may have changed). True when the colours are different now: the icons must be drawn again. */
    fun refresh(context: Context): Boolean {
        if (mode != WALLPAPER) return false
        val oldPlate = plate
        val oldGlyph = glyph
        compute(context)
        return plate != oldPlate || glyph != oldGlyph
    }

    /** Changes whenever the themed look changes (used to know a cached icon is out of date). */
    val signature: String get() = if (enabled) "$plate/$glyph" else "off"

    fun modeName(): String = when {
        mode == OFF -> "Off"
        mode == WALLPAPER -> "Wallpaper"
        else -> NAMES[mode - WALLPAPER - 1]
    }

    private fun compute(context: Context) {
        if (mode == OFF) {
            enabled = false
            return
        }
        val base = if (mode == WALLPAPER) wallpaperColor(context) else PALETTE[mode - WALLPAPER - 1]
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        val hue = hsv[0]
        val s = hsv[1].coerceIn(0.25f, 1f) // a grey wallpaper still gets a soft tint
        if (Look.dark) {
            plate = Color.HSVToColor(floatArrayOf(hue, 0.55f * s, 0.26f))
            glyph = Color.HSVToColor(floatArrayOf(hue, 0.25f * s, 0.97f))
        } else {
            plate = Color.HSVToColor(floatArrayOf(hue, 0.18f * s, 0.97f))
            glyph = Color.HSVToColor(floatArrayOf(hue, 0.85f * s, 0.30f))
        }
        enabled = true
    }

    /** Main colour of the wallpaper; a calm blue when the phone can not say. */
    private fun wallpaperColor(context: Context): Int {
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                val main = colors?.primaryColor
                if (main != null) return main.toArgb()
            } catch (e: Exception) {
                // some phones refuse; use the default colour
            }
        }
        return PALETTE[0]
    }
}
