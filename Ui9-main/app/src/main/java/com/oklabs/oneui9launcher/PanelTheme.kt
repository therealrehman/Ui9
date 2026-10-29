package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/**
 * Step 21b: the colour of the Quick panel and the Notifications page. 0 = White glass (the default), 1-5 = five plain colours,
 * 6-10 = five gradients (the first five of [Gradients]). It tints the veil behind the panel and the frosted glass of its buttons and cards.
 * Step 25: 11-20 = ten more gradients, made for the panel ([extra]). Saved numbers 0-10 keep their old meaning.
 */
object PanelTheme {
    const val KEY_WP_FOLLOW = "qp_wp_follow" // Step 62b: default ON
    const val KEY_WP_TARGET = "qp_wp_target" // 0 Home, 1 Lock, 2 Both

    /** Step 62b: puts the wallpaper of panel colour [index] on the phone (portrait size, Home / Lock / Both as saved). Runs in the background; [done] gets the message. */
    fun setWallpaper(context: Context, index: Int, done: (String) -> Unit = {}) {
        val app = context.applicationContext
        val target = app.getSharedPreferences("launcher", Context.MODE_PRIVATE).getInt(KEY_WP_TARGET, 2)
        val manager = android.app.WallpaperManager.getInstance(app)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
            done("This phone does not let apps change the wallpaper.")
            return
        }
        val m = app.resources.displayMetrics
        val (w, h) = Gradients.sizeFor(Gradients.PORTRAIT, m.widthPixels, m.heightPixels)
        val which = when (target) {
            0 -> android.app.WallpaperManager.FLAG_SYSTEM
            1 -> android.app.WallpaperManager.FLAG_LOCK
            else -> android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
        }
        val def = wallpaperFor(index)
        Thread {
            val msg = try {
                val bmp = Gradients.render(def, w, h)
                manager.setBitmap(bmp, null, true, which)
                bmp.recycle()
                "Wallpaper set: " + def.name
            } catch (e: Throwable) {
                "Could not set the wallpaper"
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(msg) }
        }.start()
    }

    /** Step 62b: called after a panel colour pick: sets the matching wallpaper when "Wallpaper follows the panel colour" is on (it is by default). */
    fun syncWallpaper(context: Context, index: Int) {
        if (!context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(KEY_WP_FOLLOW, true)) return
        setWallpaper(context, index) { android.widget.Toast.makeText(context.applicationContext, it, android.widget.Toast.LENGTH_SHORT).show() }
    }

    const val KEY = "qp_theme"
    const val COUNT = 21

    private class Solid(val name: String, val rgb: Int)

    private val solids = listOf(
        Solid("Blue", Color.rgb(34, 62, 150)),
        Solid("Purple", Color.rgb(84, 44, 140)),
        Solid("Green", Color.rgb(20, 100, 78)),
        Solid("Red", Color.rgb(150, 38, 58)),
        Solid("Graphite", Color.rgb(40, 44, 52))
    )

    /** All panel gradients, in the order of their saved numbers: the first five of [Gradients], then [extra]. */
    private val grads: List<GradientDef> = Gradients.all.take(5) + Gradients.panelExtra

    private var index = 0

    /** Step 61: the wallpaper that matches panel colour number [i] (white glass -> a soft frost; solids -> light-to-dark shades; gradients -> the same gradient). */
    fun wallpaperFor(i: Int): GradientDef = when {
        i <= 0 -> Gradients.frost
        i <= solids.size -> Gradients.solidWallpaper(solids[i - 1].name, solids[i - 1].rgb)
        else -> grads[i - solids.size - 1]
    }

    fun load(context: Context) {
        index = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getInt(KEY, 0).coerceIn(0, COUNT - 1)
    }

    fun saved(context: Context): Int = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getInt(KEY, 0).coerceIn(0, COUNT - 1)

    fun name(i: Int): String = when {
        i == 0 -> "White glass"
        i <= solids.size -> solids[i - 1].name
        else -> grads[i - solids.size - 1].name
    }

    /** The colour all the glass of the panel is based on, or null for the default white glass. */
    private fun baseRgb(): Int? = when {
        index == 0 -> null
        index <= solids.size -> solids[index - 1].rgb
        else -> grads[index - solids.size - 1].colors.let { it[it.size / 2] }
    }

    /** Light mode needs a pale tint so the dark text stays readable. */
    private fun forMode(rgb: Int, dark: Boolean): Int {
        if (dark) return rgb
        fun mix(c: Int) = (c * 0.25f + 255 * 0.75f).toInt()
        return Color.rgb(mix(Color.red(rgb)), mix(Color.green(rgb)), mix(Color.blue(rgb)))
    }

    /** Frosted glass of buttons and cards with transparency [alpha]; [fallback] is the normal glass of the Look. */
    fun glass(alpha: Int, dark: Boolean, fallback: Int): Int {
        val base = baseRgb()
        if (base == null) {
            // White glass: milky white over the dark veil
            return if (dark) Color.argb((alpha * 0.55f).toInt().coerceIn(0, 255), 255, 255, 255) else fallback
        }
        val c = forMode(base, dark)
        val deep = if (dark) Color.rgb((Color.red(c) * 0.7f).toInt(), (Color.green(c) * 0.7f).toInt(), (Color.blue(c) * 0.7f).toInt()) else c
        return Color.argb(alpha, Color.red(deep), Color.green(deep), Color.blue(deep))
    }

    /**
     * Step 26: the layer at the very back of the panel is completely solid (100 % opaque): nothing of the Home screen or of the app
     * underneath shows through any more. [strength] is no longer used; it stays so the callers did not have to change.
     */
    const val BACK_OPACITY = 255

    /** The veil behind the panel: solid (see [BACK_OPACITY]) for any [strength] above 0, nothing for 0 (the space above a bottom sheet). */
    fun scrim(strength: Int, dark: Boolean): Drawable {
        // Step 68: strength 0 = no veil at all. Steps 64-67 asked for a transparent space above the bottom sheet (scrimA(0)), but this function ignored
        // [strength] and always returned the solid veil, so the space stayed coloured. Any positive strength still gives the solid veil as before.
        if (strength <= 0) return ColorDrawable(Color.TRANSPARENT)
        val base = baseRgb() ?: return ColorDrawable(PanelLook.veil(BACK_OPACITY))
        val a = BACK_OPACITY
        if (index > solids.size) {
            val colors = grads[index - solids.size - 1].colors
            val shown = IntArray(colors.size) {
                val c = forMode(colors[it], dark)
                Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
            }
            return GradientDrawable(GradientDrawable.Orientation.TL_BR, shown)
        }
        val c = forMode(base, dark)
        return ColorDrawable(Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)))
    }

    /**
     * Step 58: the colour(s) of the Quick panel as the top handle shows them ("Same as the Quick panel"): one colour for white glass or a plain colour,
     * three colours (first corner to last) for a gradient panel. Read from the saved setting, so it follows a change at once.
     */
    fun stripColors(context: Context, dark: Boolean): IntArray {
        val i = saved(context)
        return when {
            i == 0 -> intArrayOf(if (dark) Color.rgb(236, 238, 246) else Color.rgb(60, 64, 76))
            i <= solids.size -> intArrayOf(solids[i - 1].rgb)
            else -> grads[i - solids.size - 1].colors.copyOf()
        }
    }

    /** The round swatch shown in Settings for number [i]. */
    fun swatch(i: Int): GradientDrawable {
        val shape = when {
            i == 0 -> GradientDrawable().apply { setColor(Color.argb(235, 255, 255, 255)) }
            i <= solids.size -> GradientDrawable().apply { setColor(solids[i - 1].rgb) }
            else -> GradientDrawable(GradientDrawable.Orientation.TL_BR, grads[i - solids.size - 1].colors)
        }
        shape.shape = GradientDrawable.OVAL
        return shape
    }
}
