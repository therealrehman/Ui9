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

    /** Step 25: ten more panel gradients (own table, so the dock and the wallpapers do not change). Colours run from the first corner to the last. */
    private val extra: List<GradientDef> = listOf(
        GradientDef("twilight", "Twilight", intArrayOf(Color.rgb(0xFF, 0x9A, 0x8B), Color.rgb(0x8E, 0x54, 0xE9), Color.rgb(0x2B, 0x1B, 0x6B)), 120f),
        GradientDef("cherry", "Cherry", intArrayOf(Color.rgb(0xFF, 0x5E, 0x7E), Color.rgb(0xC2, 0x18, 0x5B), Color.rgb(0x4A, 0x0A, 0x2E)), 120f),
        GradientDef("arctic", "Arctic", intArrayOf(Color.rgb(0xB8, 0xF1, 0xFF), Color.rgb(0x5C, 0xB8, 0xF2), Color.rgb(0x2A, 0x5B, 0xB8)), 115f),
        GradientDef("neon", "Neon", intArrayOf(Color.rgb(0x39, 0xFF, 0xA8), Color.rgb(0x1E, 0x9B, 0xFF), Color.rgb(0x8A, 0x2B, 0xFF)), 125f),
        GradientDef("sand", "Sand", intArrayOf(Color.rgb(0xF6, 0xD3, 0x65), Color.rgb(0xE0, 0x8F, 0x5A), Color.rgb(0x8C, 0x4A, 0x3A)), 110f),
        GradientDef("lagoon", "Lagoon", intArrayOf(Color.rgb(0x2D, 0xE2, 0xC9), Color.rgb(0x12, 0x8F, 0xB8), Color.rgb(0x13, 0x3E, 0x7C)), 115f),
        GradientDef("plum", "Plum", intArrayOf(Color.rgb(0xE0, 0x8B, 0xFF), Color.rgb(0x8B, 0x3F, 0xC9), Color.rgb(0x35, 0x14, 0x5E)), 120f),
        GradientDef("citrus", "Citrus", intArrayOf(Color.rgb(0xD4, 0xFF, 0x6B), Color.rgb(0xFF, 0xC1, 0x3D), Color.rgb(0xFF, 0x6B, 0x2C)), 110f),
        GradientDef("steel", "Steel", intArrayOf(Color.rgb(0xA8, 0xB8, 0xD0), Color.rgb(0x5E, 0x74, 0x95), Color.rgb(0x25, 0x31, 0x4A)), 105f),
        GradientDef("dawn", "Dawn", intArrayOf(Color.rgb(0xFF, 0xD8, 0xA8), Color.rgb(0xFF, 0x8E, 0x9E), Color.rgb(0x7A, 0x5C, 0xE6)), 130f)
    )

    /** All panel gradients, in the order of their saved numbers: the first five of [Gradients], then [extra]. */
    private val grads: List<GradientDef> = Gradients.all.take(5) + extra

    private var index = 0

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

    /** The veil behind the panel; [strength] is the normal veil transparency (0..255). */
    fun scrim(strength: Int, dark: Boolean): Drawable {
        val base = baseRgb() ?: return ColorDrawable(PanelLook.veil(strength))
        val a = (strength + 40).coerceAtMost(235)
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
