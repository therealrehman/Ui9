package com.oklabs.oneui9launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Step 30a: more Quick panel icons, drawn in code like the first ones (original line art, no image files).
 * These are the buttons other phones have (Samsung, Vivo, Xiaomi, Oppo...) that the launcher had no icon for.
 * [QuickGlyph] hands every kind it does not know to [draw]; [QuickGlyphs.forLabel] picks the icon for a phone's button name.
 */
object QuickGlyphArt {

    const val SCREENSHOT = 33
    const val RECORD = 34
    const val SPEED = 35
    const val SHARE = 36
    const val QR = 37
    const val WALLET = 38
    const val EYE = 39
    const val ECO = 40
    const val SYNC = 41
    const val MOON = 42
    const val LOCK = 43
    const val SHIELD = 44
    const val CAR = 45
    const val GAME = 46
    const val MIC = 47
    const val MAGNIFIER = 48
    const val TIMER = 49
    const val CALC = 50
    const val SPLIT = 51
    const val FLOAT = 52
    const val USB = 53
    const val HEADPHONES = 54
    const val ACCESS = 55
    const val AUTO_BRIGHT = 56
    const val COFFEE = 57
    const val SIM = 58
    const val MUSIC = 59
    const val HEART = 60
    const val STAR = 61
    const val HOME = 62
    const val MAIL = 63
    const val CLOCK = 64
    const val CHAT = 65
    const val FOLDER = 66
    const val PIN = 67
    const val BOOK = 68
    const val BULB = 69
    const val GLOBE = 70
    const val KEY = 71
    const val BOLT = 72

    /** Highest kind drawn here (the icon gallery lists 33..LAST). */
    const val LAST = 72

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private var tint = 0

    private fun st(s: Float, w: Float = 0.075f) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = s * w
        paint.color = tint
    }

    private fun fl() {
        paint.style = Paint.Style.FILL
        paint.color = tint
    }

    private fun rr(c: Canvas, s: Float, cx: Float, cy: Float, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        box.set(cx + l * s, cy + t * s, cx + r * s, cy + b * s)
        c.drawRoundRect(box, rad * s, rad * s, paint)
    }

    private fun arc(c: Canvas, s: Float, cx: Float, cy: Float, r: Float, a: Float, sweep: Float) {
        box.set(cx - r * s, cy - r * s, cx + r * s, cy + r * s)
        c.drawArc(box, a, sweep, false, paint)
    }

    private fun ln(c: Canvas, s: Float, cx: Float, cy: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        c.drawLine(cx + x1 * s, cy + y1 * s, cx + x2 * s, cy + y2 * s, paint)
    }

    private fun dot(c: Canvas, s: Float, cx: Float, cy: Float, x: Float, y: Float, r: Float) {
        c.drawCircle(cx + x * s, cy + y * s, r * s, paint)
    }

    /** Small filled arrow head at [deg] of a circle, pointing clockwise. */
    private fun head(c: Canvas, s: Float, cx: Float, cy: Float, r: Float, deg: Float) {
        val a = Math.toRadians(deg.toDouble())
        val px = cx + (cos(a) * r * s).toFloat()
        val py = cy + (sin(a) * r * s).toFloat()
        val tx = (-sin(a)).toFloat()
        val ty = cos(a).toFloat()
        val nx = cos(a).toFloat()
        val ny = sin(a).toFloat()
        val len = s * 0.12f
        path.reset()
        path.moveTo(px + tx * len, py + ty * len)
        path.lineTo(px + nx * len * 0.8f, py + ny * len * 0.8f)
        path.lineTo(px - nx * len * 0.8f, py - ny * len * 0.8f)
        path.close()
        val old = paint.style
        paint.style = Paint.Style.FILL
        c.drawPath(path, paint)
        paint.style = old
    }

    /** Draws icon [kind] centred at ([cx], [cy]) in a square of side [s]; false when [kind] is not one of these. */
    fun draw(c: Canvas, kind: Int, s: Float, cx: Float, cy: Float, color: Int): Boolean {
        tint = color
        when (kind) {
            SCREENSHOT -> {
                st(s, 0.08f)
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(cx + sx * 0.32f * s, cy + sy * 0.14f * s)
                    path.lineTo(cx + sx * 0.32f * s, cy + sy * 0.32f * s)
                    path.lineTo(cx + sx * 0.14f * s, cy + sy * 0.32f * s)
                    c.drawPath(path, paint)
                }
                st(s, 0.06f)
                rr(c, s, cx, cy, -0.13f, -0.1f, 0.13f, 0.1f, 0.03f)
            }
            RECORD -> {
                st(s)
                rr(c, s, cx, cy, -0.34f, -0.25f, 0.34f, 0.25f, 0.08f)
                fl()
                dot(c, s, cx, cy, 0f, 0f, 0.1f)
            }
            SPEED -> {
                st(s)
                arc(c, s, cx, cy + 0.1f * s, 0.32f, 160f, 220f)
                val a = Math.toRadians(-50.0)
                c.drawLine(cx, cy + 0.1f * s, cx + (cos(a) * 0.22f * s).toFloat(), cy + 0.1f * s + (sin(a) * 0.22f * s).toFloat(), paint)
                fl()
                dot(c, s, cx, cy, 0f, 0.1f, 0.05f)
            }
            SHARE -> {
                st(s, 0.065f)
                ln(c, s, cx, cy, -0.24f, 0f, 0.2f, -0.22f)
                ln(c, s, cx, cy, -0.24f, 0f, 0.2f, 0.22f)
                fl()
                dot(c, s, cx, cy, -0.24f, 0f, 0.09f)
                dot(c, s, cx, cy, 0.2f, -0.22f, 0.09f)
                dot(c, s, cx, cy, 0.2f, 0.22f, 0.09f)
            }
            QR -> {
                st(s, 0.06f)
                for (p in arrayOf(floatArrayOf(-0.34f, -0.34f), floatArrayOf(0.12f, -0.34f), floatArrayOf(-0.34f, 0.12f))) {
                    rr(c, s, cx, cy, p[0], p[1], p[0] + 0.22f, p[1] + 0.22f, 0.03f)
                }
                fl()
                for (p in arrayOf(floatArrayOf(-0.27f, -0.27f), floatArrayOf(0.19f, -0.27f), floatArrayOf(-0.27f, 0.19f))) {
                    rr(c, s, cx, cy, p[0], p[1], p[0] + 0.08f, p[1] + 0.08f, 0.01f)
                }
                rr(c, s, cx, cy, 0.12f, 0.12f, 0.21f, 0.21f, 0.01f)
                rr(c, s, cx, cy, 0.26f, 0.12f, 0.34f, 0.2f, 0.01f)
                rr(c, s, cx, cy, 0.12f, 0.26f, 0.2f, 0.34f, 0.01f)
                rr(c, s, cx, cy, 0.26f, 0.26f, 0.34f, 0.34f, 0.01f)
            }
            WALLET -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.34f, -0.18f, 0.34f, 0.28f, 0.07f)
                ln(c, s, cx, cy, -0.22f, -0.18f, 0.08f, -0.32f)
                rr(c, s, cx, cy, 0.1f, 0.0f, 0.34f, 0.15f, 0.05f)
                fl()
                dot(c, s, cx, cy, 0.2f, 0.075f, 0.027f)
            }
            EYE -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.38f * s, cy)
                path.quadTo(cx, cy - 0.42f * s, cx + 0.38f * s, cy)
                path.quadTo(cx, cy + 0.42f * s, cx - 0.38f * s, cy)
                path.close()
                c.drawPath(path, paint)
                fl()
                dot(c, s, cx, cy, 0f, 0f, 0.1f)
            }
            ECO -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.26f * s, cy + 0.28f * s)
                path.cubicTo(cx - 0.36f * s, cy - 0.1f * s, cx - 0.02f * s, cy - 0.34f * s, cx + 0.3f * s, cy - 0.3f * s)
                path.cubicTo(cx + 0.36f * s, cy + 0.04f * s, cx + 0.1f * s, cy + 0.32f * s, cx - 0.26f * s, cy + 0.28f * s)
                path.close()
                c.drawPath(path, paint)
                ln(c, s, cx, cy, -0.3f, 0.34f, 0.08f, -0.08f)
            }
            SYNC -> {
                st(s, 0.075f)
                arc(c, s, cx, cy, 0.26f, 205f, 110f)
                arc(c, s, cx, cy, 0.26f, 25f, 110f)
                head(c, s, cx, cy, 0.26f, 315f)
                head(c, s, cx, cy, 0.26f, 135f)
            }
            MOON -> {
                fl()
                val outer = Path()
                outer.addCircle(cx - 0.04f * s, cy, 0.3f * s, Path.Direction.CW)
                val cut = Path()
                cut.addCircle(cx + 0.13f * s, cy - 0.1f * s, 0.26f * s, Path.Direction.CW)
                outer.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(outer, paint)
            }
            LOCK -> {
                st(s, 0.075f)
                rr(c, s, cx, cy, -0.25f, -0.02f, 0.25f, 0.34f, 0.06f)
                arc(c, s, cx, cy - 0.1f * s, 0.15f, 180f, 180f)
                ln(c, s, cx, cy, -0.15f, -0.1f, -0.15f, -0.02f)
                ln(c, s, cx, cy, 0.15f, -0.1f, 0.15f, -0.02f)
                fl()
                dot(c, s, cx, cy, 0f, 0.15f, 0.04f)
            }
            SHIELD -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx, cy - 0.38f * s)
                path.lineTo(cx + 0.3f * s, cy - 0.26f * s)
                path.lineTo(cx + 0.3f * s, cy + 0.02f * s)
                path.cubicTo(cx + 0.3f * s, cy + 0.22f * s, cx + 0.12f * s, cy + 0.34f * s, cx, cy + 0.4f * s)
                path.cubicTo(cx - 0.12f * s, cy + 0.34f * s, cx - 0.3f * s, cy + 0.22f * s, cx - 0.3f * s, cy + 0.02f * s)
                path.lineTo(cx - 0.3f * s, cy - 0.26f * s)
                path.close()
                c.drawPath(path, paint)
                path.reset()
                path.moveTo(cx - 0.12f * s, cy + 0.02f * s)
                path.lineTo(cx - 0.03f * s, cy + 0.12f * s)
                path.lineTo(cx + 0.13f * s, cy - 0.08f * s)
                c.drawPath(path, paint)
            }
            CAR -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.4f * s, cy + 0.14f * s)
                path.lineTo(cx - 0.4f * s, cy)
                path.lineTo(cx - 0.3f * s, cy - 0.05f * s)
                path.lineTo(cx - 0.2f * s, cy - 0.22f * s)
                path.lineTo(cx + 0.2f * s, cy - 0.22f * s)
                path.lineTo(cx + 0.3f * s, cy - 0.05f * s)
                path.lineTo(cx + 0.4f * s, cy)
                path.lineTo(cx + 0.4f * s, cy + 0.14f * s)
                path.close()
                c.drawPath(path, paint)
                ln(c, s, cx, cy, -0.26f, -0.05f, 0.26f, -0.05f)
                fl()
                dot(c, s, cx, cy, -0.22f, 0.16f, 0.075f)
                dot(c, s, cx, cy, 0.22f, 0.16f, 0.075f)
            }
            GAME -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.4f, -0.2f, 0.4f, 0.2f, 0.14f)
                st(s, 0.06f)
                ln(c, s, cx, cy, -0.28f, 0f, -0.12f, 0f)
                ln(c, s, cx, cy, -0.2f, -0.08f, -0.2f, 0.08f)
                fl()
                dot(c, s, cx, cy, 0.14f, 0.04f, 0.04f)
                dot(c, s, cx, cy, 0.26f, -0.05f, 0.04f)
            }
            MIC -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.1f, -0.34f, 0.1f, 0.06f, 0.1f)
                arc(c, s, cx, cy + 0.02f * s, 0.2f, 0f, 180f)
                ln(c, s, cx, cy, 0f, 0.22f, 0f, 0.34f)
                ln(c, s, cx, cy, -0.1f, 0.34f, 0.1f, 0.34f)
            }
            MAGNIFIER -> {
                st(s, 0.075f)
                dot(c, s, cx, cy, -0.08f, -0.08f, 0.22f)
                paint.strokeWidth = s * 0.1f
                ln(c, s, cx, cy, 0.09f, 0.09f, 0.3f, 0.3f)
            }
            TIMER -> {
                st(s, 0.07f)
                dot(c, s, cx, cy, 0f, 0.05f, 0.27f)
                ln(c, s, cx, cy, -0.08f, -0.35f, 0.08f, -0.35f)
                ln(c, s, cx, cy, 0f, -0.35f, 0f, -0.22f)
                ln(c, s, cx, cy, 0f, 0.05f, 0.12f, -0.1f)
            }
            CALC -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.26f, -0.34f, 0.26f, 0.34f, 0.06f)
                st(s, 0.05f)
                rr(c, s, cx, cy, -0.16f, -0.25f, 0.16f, -0.1f, 0.02f)
                fl()
                for (i in -1..1) for (j in 0..2) dot(c, s, cx, cy, i * 0.13f, 0.02f + j * 0.12f, 0.032f)
            }
            SPLIT -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.34f, -0.3f, 0.34f, 0.3f, 0.06f)
                ln(c, s, cx, cy, -0.34f, 0f, 0.34f, 0f)
            }
            FLOAT -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.36f, -0.3f, 0.16f, 0.1f, 0.05f)
                fl()
                rr(c, s, cx, cy, -0.02f, -0.02f, 0.36f, 0.3f, 0.05f)
            }
            USB -> {
                st(s, 0.065f)
                ln(c, s, cx, cy, -0.3f, 0f, 0.2f, 0f)
                path.reset()
                path.moveTo(cx - 0.12f * s, cy)
                path.lineTo(cx - 0.04f * s, cy - 0.2f * s)
                path.lineTo(cx + 0.08f * s, cy - 0.2f * s)
                path.moveTo(cx - 0.02f * s, cy)
                path.lineTo(cx + 0.06f * s, cy + 0.2f * s)
                path.lineTo(cx + 0.16f * s, cy + 0.2f * s)
                c.drawPath(path, paint)
                fl()
                dot(c, s, cx, cy, -0.3f, 0f, 0.07f)
                dot(c, s, cx, cy, 0.21f, 0.2f, 0.06f)
                rr(c, s, cx, cy, 0.08f, -0.26f, 0.18f, -0.14f, 0.01f)
                path.reset()
                path.moveTo(cx + 0.2f * s, cy - 0.08f * s)
                path.lineTo(cx + 0.36f * s, cy)
                path.lineTo(cx + 0.2f * s, cy + 0.08f * s)
                path.close()
                c.drawPath(path, paint)
            }
            HEADPHONES -> {
                st(s, 0.075f)
                arc(c, s, cx, cy, 0.3f, 180f, 180f)
                ln(c, s, cx, cy, -0.3f, 0f, -0.3f, 0.06f)
                ln(c, s, cx, cy, 0.3f, 0f, 0.3f, 0.06f)
                fl()
                rr(c, s, cx, cy, -0.38f, 0.04f, -0.2f, 0.28f, 0.06f)
                rr(c, s, cx, cy, 0.2f, 0.04f, 0.38f, 0.28f, 0.06f)
            }
            ACCESS -> {
                st(s, 0.06f)
                dot(c, s, cx, cy, 0f, 0f, 0.37f)
                st(s, 0.065f)
                ln(c, s, cx, cy, -0.2f, -0.07f, 0.2f, -0.07f)
                ln(c, s, cx, cy, 0f, -0.07f, 0f, 0.07f)
                ln(c, s, cx, cy, 0f, 0.07f, -0.12f, 0.25f)
                ln(c, s, cx, cy, 0f, 0.07f, 0.12f, 0.25f)
                fl()
                dot(c, s, cx, cy, 0f, -0.2f, 0.05f)
            }
            AUTO_BRIGHT -> {
                st(s, 0.065f)
                val sx = cx - 0.06f * s
                val sy = cy - 0.06f * s
                c.drawCircle(sx, sy, s * 0.11f, paint)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    c.drawLine(sx + ca * s * 0.18f, sy + sa * s * 0.18f, sx + ca * s * 0.27f, sy + sa * s * 0.27f, paint)
                }
                fl()
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                paint.textSize = s * 0.3f
                c.drawText("A", cx + 0.27f * s, cy + 0.38f * s, paint)
            }
            COFFEE -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.26f * s, cy - 0.04f * s)
                path.lineTo(cx - 0.26f * s, cy + 0.14f * s)
                path.quadTo(cx - 0.26f * s, cy + 0.3f * s, cx - 0.1f * s, cy + 0.3f * s)
                path.lineTo(cx + 0.02f * s, cy + 0.3f * s)
                path.quadTo(cx + 0.18f * s, cy + 0.3f * s, cx + 0.18f * s, cy + 0.14f * s)
                path.lineTo(cx + 0.18f * s, cy - 0.04f * s)
                path.close()
                c.drawPath(path, paint)
                arc(c, s, cx + 0.19f * s, cy + 0.08f * s, 0.1f, -90f, 180f)
                for (x in floatArrayOf(-0.1f, 0.06f)) {
                    path.reset()
                    path.moveTo(cx + x * s, cy - 0.12f * s)
                    path.quadTo(cx + (x + 0.07f) * s, cy - 0.2f * s, cx + x * s, cy - 0.3f * s)
                    c.drawPath(path, paint)
                }
            }
            SIM -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.22f * s, cy - 0.34f * s)
                path.lineTo(cx + 0.08f * s, cy - 0.34f * s)
                path.lineTo(cx + 0.24f * s, cy - 0.18f * s)
                path.lineTo(cx + 0.24f * s, cy + 0.34f * s)
                path.lineTo(cx - 0.22f * s, cy + 0.34f * s)
                path.close()
                c.drawPath(path, paint)
                st(s, 0.05f)
                rr(c, s, cx, cy, -0.12f, 0.0f, 0.14f, 0.24f, 0.03f)
                ln(c, s, cx, cy, -0.12f, 0.12f, 0.14f, 0.12f)
            }
            MUSIC -> {
                st(s, 0.07f)
                ln(c, s, cx, cy, 0.12f, 0.2f, 0.12f, -0.3f)
                path.reset()
                path.moveTo(cx + 0.12f * s, cy - 0.3f * s)
                path.quadTo(cx + 0.14f * s, cy - 0.08f * s, cx + 0.32f * s, cy - 0.06f * s)
                c.drawPath(path, paint)
                fl()
                dot(c, s, cx, cy, 0.0f, 0.22f, 0.12f)
            }
            HEART -> {
                st(s, 0.075f)
                path.reset()
                path.moveTo(cx, cy + 0.3f * s)
                path.cubicTo(cx - 0.5f * s, cy - 0.05f * s, cx - 0.25f * s, cy - 0.38f * s, cx, cy - 0.1f * s)
                path.cubicTo(cx + 0.25f * s, cy - 0.38f * s, cx + 0.5f * s, cy - 0.05f * s, cx, cy + 0.3f * s)
                path.close()
                c.drawPath(path, paint)
            }
            STAR -> {
                st(s, 0.07f)
                path.reset()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.34f else 0.15f
                    val a = -PI / 2 + i * PI / 5
                    val px = cx + (cos(a) * r * s).toFloat()
                    val py = cy + 0.02f * s + (sin(a) * r * s).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                c.drawPath(path, paint)
            }
            HOME -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.34f * s, cy - 0.02f * s)
                path.lineTo(cx, cy - 0.32f * s)
                path.lineTo(cx + 0.34f * s, cy - 0.02f * s)
                c.drawPath(path, paint)
                path.reset()
                path.moveTo(cx - 0.22f * s, cy - 0.08f * s)
                path.lineTo(cx - 0.22f * s, cy + 0.3f * s)
                path.lineTo(cx + 0.22f * s, cy + 0.3f * s)
                path.lineTo(cx + 0.22f * s, cy - 0.08f * s)
                c.drawPath(path, paint)
                rr(c, s, cx, cy, -0.07f, 0.1f, 0.07f, 0.3f, 0.03f)
            }
            MAIL -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.34f, -0.23f, 0.34f, 0.23f, 0.06f)
                path.reset()
                path.moveTo(cx - 0.32f * s, cy - 0.17f * s)
                path.lineTo(cx, cy + 0.05f * s)
                path.lineTo(cx + 0.32f * s, cy - 0.17f * s)
                c.drawPath(path, paint)
            }
            CLOCK -> {
                st(s, 0.07f)
                dot(c, s, cx, cy, 0f, 0f, 0.3f)
                ln(c, s, cx, cy, 0f, 0f, 0f, -0.17f)
                ln(c, s, cx, cy, 0f, 0f, 0.12f, 0.08f)
            }
            CHAT -> {
                st(s, 0.07f)
                path.reset()
                rr(c, s, cx, cy, -0.34f, -0.28f, 0.34f, 0.14f, 0.1f)
                path.moveTo(cx - 0.14f * s, cy + 0.14f * s)
                path.lineTo(cx - 0.22f * s, cy + 0.32f * s)
                path.lineTo(cx + 0.02f * s, cy + 0.14f * s)
                c.drawPath(path, paint)
                fl()
                for (x in floatArrayOf(-0.16f, 0f, 0.16f)) dot(c, s, cx, cy, x, -0.07f, 0.034f)
            }
            FOLDER -> {
                st(s, 0.07f)
                path.reset()
                path.moveTo(cx - 0.34f * s, cy - 0.2f * s)
                path.lineTo(cx - 0.1f * s, cy - 0.2f * s)
                path.lineTo(cx - 0.02f * s, cy - 0.1f * s)
                path.lineTo(cx + 0.34f * s, cy - 0.1f * s)
                path.lineTo(cx + 0.34f * s, cy + 0.26f * s)
                path.lineTo(cx - 0.34f * s, cy + 0.26f * s)
                path.close()
                c.drawPath(path, paint)
            }
            PIN -> {
                st(s, 0.07f)
                arc(c, s, cx, cy - 0.08f * s, 0.24f, 0f, 360f)
                path.reset()
                path.moveTo(cx - 0.2f * s, cy + 0.06f * s)
                path.lineTo(cx, cy + 0.34f * s)
                path.lineTo(cx + 0.2f * s, cy + 0.06f * s)
                c.drawPath(path, paint)
                fl()
                dot(c, s, cx, cy, 0f, -0.08f, 0.07f)
            }
            BOOK -> {
                st(s, 0.07f)
                rr(c, s, cx, cy, -0.34f, -0.22f, -0.01f, 0.24f, 0.04f)
                rr(c, s, cx, cy, 0.01f, -0.22f, 0.34f, 0.24f, 0.04f)
                st(s, 0.05f)
                ln(c, s, cx, cy, -0.25f, -0.08f, -0.1f, -0.08f)
                ln(c, s, cx, cy, 0.1f, -0.08f, 0.25f, -0.08f)
                ln(c, s, cx, cy, -0.25f, 0.06f, -0.1f, 0.06f)
                ln(c, s, cx, cy, 0.1f, 0.06f, 0.25f, 0.06f)
            }
            BULB -> {
                st(s, 0.07f)
                arc(c, s, cx, cy - 0.1f * s, 0.22f, 120f, 300f)
                path.reset()
                path.moveTo(cx - 0.11f * s, cy + 0.09f * s)
                path.lineTo(cx - 0.1f * s, cy + 0.2f * s)
                path.lineTo(cx + 0.1f * s, cy + 0.2f * s)
                path.lineTo(cx + 0.11f * s, cy + 0.09f * s)
                c.drawPath(path, paint)
                ln(c, s, cx, cy, -0.07f, 0.3f, 0.07f, 0.3f)
            }
            GLOBE -> {
                st(s, 0.065f)
                dot(c, s, cx, cy, 0f, 0f, 0.3f)
                rr(c, s, cx, cy, -0.12f, -0.3f, 0.12f, 0.3f, 0.12f)
                ln(c, s, cx, cy, -0.3f, 0f, 0.3f, 0f)
            }
            KEY -> {
                st(s, 0.07f)
                dot(c, s, cx, cy, -0.18f, 0f, 0.14f)
                ln(c, s, cx, cy, -0.04f, 0f, 0.32f, 0f)
                ln(c, s, cx, cy, 0.2f, 0f, 0.2f, 0.12f)
                ln(c, s, cx, cy, 0.32f, 0f, 0.32f, 0.12f)
            }
            BOLT -> {
                fl()
                path.reset()
                path.moveTo(cx + 0.06f * s, cy - 0.34f * s)
                path.lineTo(cx - 0.18f * s, cy + 0.04f * s)
                path.lineTo(cx - 0.01f * s, cy + 0.04f * s)
                path.lineTo(cx - 0.06f * s, cy + 0.34f * s)
                path.lineTo(cx + 0.18f * s, cy - 0.06f * s)
                path.lineTo(cx + 0.01f * s, cy - 0.06f * s)
                path.close()
                c.drawPath(path, paint)
            }
            else -> return false
        }
        return true
    }
}
