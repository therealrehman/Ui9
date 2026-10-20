package com.oklabs.oneui9launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One gradient of the launcher's table: [colors] are opaque RGB, from the first corner to the last. [angle] is the direction in degrees (0 = left to right, 90 = top to bottom). */
class GradientDef(val key: String, val name: String, val colors: IntArray, val angle: Float)

/**
 * Step 15: the gradients used for the dock background and for the gradient wallpapers. One table, so the dock, the wallpaper
 * tiles and the preview always match. Everything is drawn in code (no pictures in the app).
 */
object Gradients {

    val all: List<GradientDef> = listOf(
        GradientDef("aurora", "Aurora", intArrayOf(rgb(0x1D, 0xD3, 0xB8), rgb(0x4F, 0x8B, 0xE8), rgb(0x7B, 0x61, 0xFF)), 125f),
        GradientDef("sunset", "Sunset", intArrayOf(rgb(0xFF, 0xB3, 0x4D), rgb(0xFF, 0x6A, 0x6A), rgb(0xE0, 0x3E, 0x8C)), 120f),
        GradientDef("ocean", "Ocean", intArrayOf(rgb(0x16, 0xD3, 0xE6), rgb(0x2F, 0x8C, 0xFF), rgb(0x1C, 0x4E, 0xC4)), 115f),
        GradientDef("forest", "Forest", intArrayOf(rgb(0x6D, 0xDD, 0x7A), rgb(0x1F, 0x9E, 0x72), rgb(0x0B, 0x4F, 0x5C)), 110f),
        GradientDef("midnight", "Midnight", intArrayOf(rgb(0x3A, 0x45, 0x9E), rgb(0x2B, 0x1F, 0x63), rgb(0x0E, 0x0B, 0x2B)), 105f),
        GradientDef("rose", "Rose", intArrayOf(rgb(0xFF, 0xA8, 0xC8), rgb(0xFF, 0x6F, 0xA5), rgb(0xA8, 0x3F, 0xC9)), 125f),
        GradientDef("lavender", "Lavender", intArrayOf(rgb(0xC9, 0xB6, 0xFF), rgb(0x9A, 0x7B, 0xF2), rgb(0x5B, 0x4B, 0xC9)), 115f),
        GradientDef("peach", "Peach", intArrayOf(rgb(0xFF, 0xE0, 0xA3), rgb(0xFF, 0xA0, 0x7A), rgb(0xF0, 0x6A, 0x8A)), 130f),
        GradientDef("mint", "Mint", intArrayOf(rgb(0xC8, 0xF7, 0xDC), rgb(0x4F, 0xD1, 0xB0), rgb(0x1F, 0x8F, 0xA8)), 120f),
        GradientDef("graphite", "Graphite", intArrayOf(rgb(0x5C, 0x64, 0x70), rgb(0x2B, 0x30, 0x3A), rgb(0x0F, 0x11, 0x16)), 100f),
        GradientDef("candy", "Candy", intArrayOf(rgb(0xFF, 0x7E, 0xB6), rgb(0xB1, 0x6C, 0xFF), rgb(0x4D, 0xB6, 0xFF)), 135f),
        GradientDef("ember", "Ember", intArrayOf(rgb(0xFF, 0xD1, 0x4D), rgb(0xFF, 0x6A, 0x1F), rgb(0x9E, 0x1B, 0x32)), 110f)
    )

    /** The gradient behind a saved number: 0 = none, 1.. = [all] in order. */
    fun forIndex(index: Int): GradientDef? = all.getOrNull(index - 1)

    /** [colors] with transparency [alpha] (0..255), for the dock. */
    fun withAlpha(colors: IntArray, alpha: Int): IntArray =
        IntArray(colors.size) { Color.argb(alpha.coerceIn(0, 255), Color.red(colors[it]), Color.green(colors[it]), Color.blue(colors[it])) }

    /**
     * Draws the wallpaper [def] at [width] x [height]: the gradient across the picture, two soft glows (a light one top left,
     * a deep one bottom right) so it is not flat, and a gentle shade at the bottom so the dock and the icons stay readable.
     * The same call makes the little preview tiles, so what you see is what the phone gets.
     */
    fun render(def: GradientDef, width: Int, height: Int): Bitmap {
        val w = max(1, width)
        val h = max(1, height)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        // 1. The gradient: a line through the middle in the direction of the angle, long enough to reach the corners.
        val rad = Math.toRadians(def.angle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val half = (abs(dx) * wf + abs(dy) * hf) / 2f
        val cx = wf / 2f
        val cy = hf / 2f
        paint.shader = LinearGradient(
            cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
            def.colors, null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)

        // 2. Two glows. The radius follows the longer side, so landscape and portrait both look full.
        val big = max(wf, hf)
        val light = def.colors.first()
        val deep = def.colors.last()
        paint.shader = RadialGradient(
            wf * 0.18f, hf * 0.2f, big * 0.62f,
            intArrayOf(Color.argb(110, Color.red(light), Color.green(light), Color.blue(light)), Color.argb(0, Color.red(light), Color.green(light), Color.blue(light))),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = RadialGradient(
            wf * 0.86f, hf * 0.82f, big * 0.55f,
            intArrayOf(Color.argb(120, Color.red(deep), Color.green(deep), Color.blue(deep)), Color.argb(0, Color.red(deep), Color.green(deep), Color.blue(deep))),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)

        // 3. A shade over the bottom third.
        paint.shader = LinearGradient(
            0f, hf * 0.62f, 0f, hf,
            Color.argb(0, 0, 0, 0), Color.argb(70, 0, 0, 0), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, hf * 0.62f, wf, hf, paint)
        return bitmap
    }

    /** How the picture is cut for a phone. */
    const val PORTRAIT = 0
    const val LANDSCAPE = 1
    const val BOTH = 2

    /**
     * The size of the picture to give the phone: the screen's own size for [PORTRAIT] / [LANDSCAPE]; for [BOTH] a square as big as
     * the long side, which the phone crops well when it is held either way (a gradient has no "wrong way up"). Never above [MAX_SIDE].
     */
    fun sizeFor(mode: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val shortSide = min(screenWidth, screenHeight)
        val longSide = max(screenWidth, screenHeight)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        val s = max(1, (shortSide * scale).toInt())
        val l = max(1, (longSide * scale).toInt())
        return when (mode) {
            LANDSCAPE -> Pair(l, s)
            BOTH -> Pair(l, l)
            else -> Pair(s, l)
        }
    }

    const val MAX_SIDE = 2560

    private fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
}
