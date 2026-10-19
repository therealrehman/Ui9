package com.oklabs.oneui9launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Puts every app icon into the same squircle, the way One UI does.
 * Adaptive icons: both layers are drawn and cut to the squircle.
 * Old-style icons: drawn smaller on a light plate so they match the rest.
 * Step 8d: with themed icons on ([IconTheme]) the icon is drawn in one tint: the app's monochrome layer (Android 13+) as a
 * glyph on a tinted plate, or, for apps without one, the normal icon turned into two tones of the same colour.
 */
object IconShaper {

    /** Superellipse exponent: 2.0 = circle, higher = squarer. */
    private const val EXPONENT = 4.0
    private const val POINTS = 180

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val outsideCache = HashMap<Int, Path>()

    /** Everything OUTSIDE the squircle (inverse fill) - clearing it gives soft, anti-aliased corners. */
    @Synchronized
    private fun outside(size: Int): Path = outsideCache.getOrPut(size) {
        val half = size / 2.0
        val power = 2.0 / EXPONENT
        val p = Path()
        for (i in 0..POINTS) {
            val t = 2.0 * Math.PI * i / POINTS
            val c = cos(t)
            val s = sin(t)
            val x = (half + half * sign(c) * abs(c).pow(power)).toFloat()
            val y = (half + half * sign(s) * abs(s).pow(power)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        p.fillType = Path.FillType.INVERSE_WINDING
        p
    }

    /**
     * Returns a square [size] x [size] bitmap of [icon] in the launcher's squircle shape.
     * Synchronized: the Apps screen shapes icons on a worker thread while the dock does it on the main thread.
     */
    @Synchronized
    fun shape(icon: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (IconTheme.enabled) {
            drawThemed(canvas, icon, size)
        } else {
            drawNormal(canvas, icon, size)
        }
        canvas.drawPath(outside(size), clearPaint)
        return bitmap
    }

    private fun drawNormal(canvas: Canvas, icon: Drawable, size: Int) {
        if (icon is AdaptiveIconDrawable) {
            drawAdaptive(canvas, icon, size)
        } else {
            drawLegacy(canvas, icon, size)
        }
    }

    /** One colour family: the monochrome layer on a plate, or the normal icon mapped from dark -> plate colour to bright -> glyph colour. */
    private fun drawThemed(canvas: Canvas, icon: Drawable, size: Int) {
        val plate = IconTheme.plate
        val glyph = IconTheme.glyph
        val mono: Drawable? = if (Build.VERSION.SDK_INT >= 33 && icon is AdaptiveIconDrawable) icon.monochrome else null
        if (mono != null) {
            canvas.drawColor(plate)
            val pad = (size * 0.25f).toInt()
            mono.colorFilter = PorterDuffColorFilter(glyph, PorterDuff.Mode.SRC_IN)
            mono.setBounds(-pad, -pad, size + pad, size + pad)
            mono.draw(canvas)
            return
        }
        val plain = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawNormal(Canvas(plain), icon, size)
        val duo = ColorMatrix(
            floatArrayOf(
                (Color.red(glyph) - Color.red(plate)) / 255f, 0f, 0f, 0f, Color.red(plate).toFloat(),
                0f, (Color.green(glyph) - Color.green(plate)) / 255f, 0f, 0f, Color.green(plate).toFloat(),
                0f, 0f, (Color.blue(glyph) - Color.blue(plate)) / 255f, 0f, Color.blue(plate).toFloat(),
                0f, 0f, 0f, 1f, 0f
            )
        )
        val gray = ColorMatrix()
        gray.setSaturation(0f)
        duo.preConcat(gray) // brightness first, then the two tones
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(duo)
        canvas.drawBitmap(plain, 0f, 0f, paint)
        plain.recycle()
    }

    private fun drawAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable, size: Int) {
        // Adaptive layers are 108dp; the visible window is the middle 72dp.
        // Stretch the layers so that middle window fills our icon square.
        val pad = (size * 0.25f).toInt()
        icon.background?.apply {
            setBounds(-pad, -pad, size + pad, size + pad)
            draw(canvas)
        }
        icon.foreground?.apply {
            setBounds(-pad, -pad, size + pad, size + pad)
            draw(canvas)
        }
    }

    private fun drawLegacy(canvas: Canvas, icon: Drawable, size: Int) {
        canvas.drawColor(Color.parseColor("#F4F5F7"))
        val inset = (size * 0.15f).toInt()
        icon.setBounds(inset, inset, size - inset, size - inset)
        icon.draw(canvas)
    }
}
