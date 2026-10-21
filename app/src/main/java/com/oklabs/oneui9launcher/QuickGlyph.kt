package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Step 16a: the little icons of the Quick panel (Wi-Fi, Bluetooth, torch...), drawn in code - original line art, no image files.
 * One view draws one icon in [tint], scaled to the view's size.
 */
class QuickGlyph(context: Context, var kind: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    /** Colour of the drawing. */
    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /** Changes which icon is drawn (the sound tile switches between sound / vibrate / mute). */
    fun show(newKind: Int) {
        if (newKind == kind) return
        kind = newKind
        invalidate()
    }

    private fun stroke(s: Float, width: Float = 0.085f) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = s * width
        paint.color = tint
    }

    private fun fill() {
        paint.style = Paint.Style.FILL
        paint.color = tint
    }

    private fun arc(canvas: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) {
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, startDeg, sweepDeg, false, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        when (kind) {
            WIFI -> {
                stroke(s)
                val by = cy + s * 0.26f
                for (i in 1..3) arc(canvas, cx, by, s * 0.17f * i, 225f, 90f)
                fill()
                canvas.drawCircle(cx, by, s * 0.05f, paint)
            }
            BLUETOOTH -> {
                stroke(s)
                path.reset()
                path.moveTo(cx - s * 0.22f, cy - s * 0.2f)
                path.lineTo(cx + s * 0.2f, cy + s * 0.2f)
                path.lineTo(cx, cy + s * 0.4f)
                path.lineTo(cx, cy - s * 0.4f)
                path.lineTo(cx + s * 0.2f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.22f, cy + s * 0.2f)
                canvas.drawPath(path, paint)
            }
            DATA -> {
                stroke(s)
                val lx = cx - s * 0.14f
                val rx = cx + s * 0.14f
                canvas.drawLine(lx, cy + s * 0.32f, lx, cy - s * 0.32f, paint)
                canvas.drawLine(lx, cy - s * 0.32f, lx - s * 0.13f, cy - s * 0.18f, paint)
                canvas.drawLine(lx, cy - s * 0.32f, lx + s * 0.13f, cy - s * 0.18f, paint)
                canvas.drawLine(rx, cy - s * 0.32f, rx, cy + s * 0.32f, paint)
                canvas.drawLine(rx, cy + s * 0.32f, rx - s * 0.13f, cy + s * 0.18f, paint)
                canvas.drawLine(rx, cy + s * 0.32f, rx + s * 0.13f, cy + s * 0.18f, paint)
            }
            AIRPLANE -> {
                fill()
                canvas.save()
                canvas.rotate(45f, cx, cy)
                path.reset()
                val p = floatArrayOf(
                    0f, -0.46f, 0.07f, -0.34f, 0.07f, -0.08f, 0.44f, 0.14f, 0.44f, 0.24f, 0.07f, 0.12f,
                    0.07f, 0.3f, 0.17f, 0.38f, 0.17f, 0.44f, 0f, 0.4f
                )
                path.moveTo(cx + p[0] * s, cy + p[1] * s)
                var i = 2
                while (i < p.size) {
                    path.lineTo(cx + p[i] * s, cy + p[i + 1] * s)
                    i += 2
                }
                i = p.size - 4 // the left half: the same points mirrored, walking back to the nose
                while (i >= 2) {
                    path.lineTo(cx - p[i] * s, cy + p[i + 1] * s)
                    i -= 2
                }
                path.close()
                canvas.drawPath(path, paint)
                canvas.restore()
            }
            TORCH -> {
                fill()
                path.reset()
                path.moveTo(cx - s * 0.2f, cy - s * 0.4f)
                path.lineTo(cx + s * 0.2f, cy - s * 0.4f)
                path.lineTo(cx + s * 0.11f, cy - s * 0.1f)
                path.lineTo(cx - s * 0.11f, cy - s * 0.1f)
                path.close()
                canvas.drawPath(path, paint)
                oval.set(cx - s * 0.11f, cy - s * 0.06f, cx + s * 0.11f, cy + s * 0.4f)
                canvas.drawRoundRect(oval, s * 0.06f, s * 0.06f, paint)
            }
            ROTATE -> {
                stroke(s, 0.07f)
                val r = s * 0.3f
                arc(canvas, cx, cy, r, -170f, 110f)
                arc(canvas, cx, cy, r, 10f, 110f)
                arrowHead(canvas, cx, cy, r, -60f, s)
                arrowHead(canvas, cx, cy, r, 120f, s)
                oval.set(cx - s * 0.1f, cy - s * 0.13f, cx + s * 0.1f, cy + s * 0.13f)
                canvas.drawRoundRect(oval, s * 0.03f, s * 0.03f, paint)
            }
            DND -> {
                stroke(s)
                canvas.drawCircle(cx, cy, s * 0.32f, paint)
                canvas.drawLine(cx - s * 0.15f, cy, cx + s * 0.15f, cy, paint)
            }
            LOCATION -> {
                stroke(s)
                path.reset()
                path.moveTo(cx, cy + s * 0.42f)
                path.cubicTo(cx - s * 0.3f, cy + s * 0.1f, cx - s * 0.26f, cy - s * 0.38f, cx, cy - s * 0.38f)
                path.cubicTo(cx + s * 0.26f, cy - s * 0.38f, cx + s * 0.3f, cy + s * 0.1f, cx, cy + s * 0.42f)
                canvas.drawPath(path, paint)
                fill()
                canvas.drawCircle(cx, cy - s * 0.1f, s * 0.07f, paint)
            }
            HOTSPOT -> {
                stroke(s)
                for (i in 1..2) {
                    val r = s * 0.17f * i + s * 0.04f
                    arc(canvas, cx, cy, r, -40f, 80f)
                    arc(canvas, cx, cy, r, 140f, 80f)
                }
                fill()
                canvas.drawCircle(cx, cy, s * 0.06f, paint)
            }
            SOUND, VIBRATE, MUTE -> drawSound(canvas, s, cx, cy)
            PENCIL -> {
                stroke(s, 0.13f)
                canvas.drawLine(cx - s * 0.04f, cy + s * 0.04f, cx + s * 0.22f, cy - s * 0.22f, paint)
                fill()
                path.reset()
                path.moveTo(cx + s * 0.006f, cy + s * 0.086f)
                path.lineTo(cx - s * 0.086f, cy - s * 0.006f)
                path.lineTo(cx - s * 0.3f, cy + s * 0.3f)
                path.close()
                canvas.drawPath(path, paint)
            }
            BATTERY -> {
                stroke(s, 0.07f)
                oval.set(cx - s * 0.2f, cy - s * 0.3f, cx + s * 0.2f, cy + s * 0.4f)
                canvas.drawRoundRect(oval, s * 0.06f, s * 0.06f, paint)
                canvas.drawLine(cx - s * 0.07f, cy - s * 0.4f, cx + s * 0.07f, cy - s * 0.4f, paint)
                canvas.drawLine(cx - s * 0.09f, cy + s * 0.05f, cx + s * 0.09f, cy + s * 0.05f, paint)
                canvas.drawLine(cx, cy - s * 0.04f, cx, cy + s * 0.14f, paint)
            }
            POWER -> {
                stroke(s, 0.1f)
                arc(canvas, cx, cy + s * 0.03f, s * 0.3f, -60f, 300f)
                canvas.drawLine(cx, cy - s * 0.36f, cx, cy - s * 0.04f, paint)
            }
            DARK -> {
                fill()
                path.reset()
                path.addCircle(cx - s * 0.02f, cy, s * 0.3f, Path.Direction.CW)
                val cut = Path()
                cut.addCircle(cx + s * 0.14f, cy - s * 0.12f, s * 0.26f, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE)
                canvas.drawPath(path, paint)
            }
            CAST -> {
                stroke(s, 0.07f)
                path.reset()
                path.moveTo(cx - s * 0.38f, cy - s * 0.1f)
                path.lineTo(cx - s * 0.38f, cy - s * 0.3f)
                path.lineTo(cx + s * 0.38f, cy - s * 0.3f)
                path.lineTo(cx + s * 0.38f, cy + s * 0.3f)
                path.lineTo(cx + s * 0.12f, cy + s * 0.3f)
                canvas.drawPath(path, paint)
                val ox = cx - s * 0.38f
                val oy = cy + s * 0.3f
                arc(canvas, ox, oy, s * 0.16f, -90f, 90f)
                arc(canvas, ox, oy, s * 0.3f, -90f, 90f)
                fill()
                canvas.drawCircle(ox, oy, s * 0.04f, paint)
            }
            CAMERA -> {
                stroke(s, 0.07f)
                oval.set(cx - s * 0.37f, cy - s * 0.2f, cx + s * 0.37f, cy + s * 0.32f)
                canvas.drawRoundRect(oval, s * 0.07f, s * 0.07f, paint)
                path.reset()
                path.moveTo(cx - s * 0.14f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.08f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.08f, cy - s * 0.32f)
                path.lineTo(cx + s * 0.14f, cy - s * 0.2f)
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx, cy + s * 0.06f, s * 0.12f, paint)
            }
            SUN -> {
                stroke(s, 0.07f)
                canvas.drawCircle(cx, cy, s * 0.15f, paint)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val c = cos(a).toFloat()
                    val d = sin(a).toFloat()
                    canvas.drawLine(cx + c * s * 0.25f, cy + d * s * 0.25f, cx + c * s * 0.37f, cy + d * s * 0.37f, paint)
                }
            }
            GEAR -> {
                stroke(s, 0.075f)
                canvas.drawCircle(cx, cy, s * 0.15f, paint)
                paint.strokeWidth = s * 0.12f
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val c = cos(a).toFloat()
                    val d = sin(a).toFloat()
                    canvas.drawLine(cx + c * s * 0.28f, cy + d * s * 0.28f, cx + c * s * 0.35f, cy + d * s * 0.35f, paint)
                }
                paint.strokeWidth = s * 0.075f
                canvas.drawCircle(cx, cy, s * 0.26f, paint)
            }
            PLAY -> {
                fill()
                path.reset()
                path.moveTo(cx - s * 0.16f, cy - s * 0.26f)
                path.lineTo(cx + s * 0.26f, cy)
                path.lineTo(cx - s * 0.16f, cy + s * 0.26f)
                path.close()
                canvas.drawPath(path, paint)
            }
            PAUSE -> {
                fill()
                oval.set(cx - s * 0.2f, cy - s * 0.24f, cx - s * 0.06f, cy + s * 0.24f)
                canvas.drawRoundRect(oval, s * 0.04f, s * 0.04f, paint)
                oval.set(cx + s * 0.06f, cy - s * 0.24f, cx + s * 0.2f, cy + s * 0.24f)
                canvas.drawRoundRect(oval, s * 0.04f, s * 0.04f, paint)
            }
            PREV, NEXT -> {
                fill()
                val dir = if (kind == NEXT) 1f else -1f // +1: the triangle points right and the bar is on the right
                path.reset()
                path.moveTo(cx - dir * s * 0.2f, cy - s * 0.22f)
                path.lineTo(cx + dir * s * 0.16f, cy)
                path.lineTo(cx - dir * s * 0.2f, cy + s * 0.22f)
                path.close()
                canvas.drawPath(path, paint)
                val bx = cx + dir * s * 0.24f
                oval.set(bx - s * 0.04f, cy - s * 0.22f, bx + s * 0.04f, cy + s * 0.22f)
                canvas.drawRoundRect(oval, s * 0.03f, s * 0.03f, paint)
            }
            OUTPUT -> {
                stroke(s, 0.07f)
                oval.set(cx - s * 0.18f, cy - s * 0.32f, cx + s * 0.18f, cy + s * 0.32f)
                canvas.drawRoundRect(oval, s * 0.07f, s * 0.07f, paint)
                canvas.drawCircle(cx, cy + s * 0.14f, s * 0.085f, paint)
                fill()
                canvas.drawCircle(cx, cy - s * 0.15f, s * 0.035f, paint)
            }
            DEVICES -> {
                stroke(s, 0.07f)
                oval.set(cx - s * 0.32f, cy - s * 0.26f, cx + s * 0.32f, cy + s * 0.12f)
                canvas.drawRoundRect(oval, s * 0.06f, s * 0.06f, paint)
                canvas.drawLine(cx, cy + s * 0.12f, cx, cy + s * 0.26f, paint)
                canvas.drawLine(cx - s * 0.16f, cy + s * 0.27f, cx + s * 0.16f, cy + s * 0.27f, paint)
            }
            CHEVRON_DOWN, CHEVRON_UP -> {
                stroke(s, 0.09f)
                val d = if (kind == CHEVRON_DOWN) 1f else -1f
                path.reset()
                path.moveTo(cx - s * 0.2f, cy - d * s * 0.08f)
                path.lineTo(cx, cy + d * s * 0.12f)
                path.lineTo(cx + s * 0.2f, cy - d * s * 0.08f)
                canvas.drawPath(path, paint)
            }
            BELL -> {
                fill()
                path.reset()
                path.moveTo(cx - s * 0.26f, cy + s * 0.14f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.14f)
                path.lineTo(cx - s * 0.2f, cy - s * 0.04f)
                path.cubicTo(cx - s * 0.2f, cy - s * 0.3f, cx + s * 0.2f, cy - s * 0.3f, cx + s * 0.2f, cy - s * 0.04f)
                path.lineTo(cx + s * 0.2f, cy + s * 0.14f)
                path.lineTo(cx + s * 0.26f, cy + s * 0.14f)
                path.lineTo(cx + s * 0.26f, cy + s * 0.2f)
                path.lineTo(cx - s * 0.26f, cy + s * 0.2f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx, cy + s * 0.3f, s * 0.05f, paint)
            }
            ALARM -> {
                stroke(s, 0.07f)
                canvas.drawCircle(cx, cy + s * 0.03f, s * 0.26f, paint)
                canvas.drawLine(cx, cy + s * 0.03f, cx, cy - s * 0.1f, paint)
                canvas.drawLine(cx, cy + s * 0.03f, cx + s * 0.1f, cy + s * 0.08f, paint)
                arc(canvas, cx - s * 0.2f, cy - s * 0.2f, s * 0.1f, 180f, 110f)
                arc(canvas, cx + s * 0.2f, cy - s * 0.2f, s * 0.1f, 250f, 110f)
            }
        }
    }

    /** Small arrow head at [angleDeg] of a circle, pointing along the clockwise direction. */
    private fun arrowHead(canvas: Canvas, cx: Float, cy: Float, r: Float, angleDeg: Float, s: Float) {
        val a = Math.toRadians(angleDeg.toDouble())
        val px = cx + (cos(a) * r).toFloat()
        val py = cy + (sin(a) * r).toFloat()
        val tx = (-sin(a)).toFloat() // clockwise tangent
        val ty = cos(a).toFloat()
        val nx = cos(a).toFloat()
        val ny = sin(a).toFloat()
        val len = s * 0.11f
        path.reset()
        path.moveTo(px + tx * len * 0.9f, py + ty * len * 0.9f)
        path.lineTo(px + nx * len * 0.8f, py + ny * len * 0.8f)
        path.lineTo(px - nx * len * 0.8f, py - ny * len * 0.8f)
        path.close()
        val old = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        paint.style = old
    }

    /** Speaker with waves (sound), a phone with shake lines (vibrate) or a speaker with a cross (mute). */
    private fun drawSound(canvas: Canvas, s: Float, cx: Float, cy: Float) {
        if (kind == VIBRATE) {
            stroke(s, 0.07f)
            oval.set(cx - s * 0.11f, cy - s * 0.24f, cx + s * 0.11f, cy + s * 0.24f)
            canvas.drawRoundRect(oval, s * 0.04f, s * 0.04f, paint)
            canvas.drawLine(cx - s * 0.2f, cy - s * 0.14f, cx - s * 0.2f, cy + s * 0.14f, paint)
            canvas.drawLine(cx + s * 0.2f, cy - s * 0.14f, cx + s * 0.2f, cy + s * 0.14f, paint)
            canvas.drawLine(cx - s * 0.31f, cy - s * 0.08f, cx - s * 0.31f, cy + s * 0.08f, paint)
            canvas.drawLine(cx + s * 0.31f, cy - s * 0.08f, cx + s * 0.31f, cy + s * 0.08f, paint)
            return
        }
        fill()
        val left = cx - s * 0.3f
        path.reset()
        path.moveTo(left, cy - s * 0.1f)
        path.lineTo(left + s * 0.14f, cy - s * 0.1f)
        path.lineTo(left + s * 0.32f, cy - s * 0.26f)
        path.lineTo(left + s * 0.32f, cy + s * 0.26f)
        path.lineTo(left + s * 0.14f, cy + s * 0.1f)
        path.lineTo(left, cy + s * 0.1f)
        path.close()
        canvas.drawPath(path, paint)
        stroke(s, 0.07f)
        if (kind == SOUND) {
            arc(canvas, left + s * 0.32f, cy, s * 0.14f, -50f, 100f)
            arc(canvas, left + s * 0.32f, cy, s * 0.26f, -50f, 100f)
        } else {
            val x = left + s * 0.5f
            canvas.drawLine(x, cy - s * 0.1f, x + s * 0.2f, cy + s * 0.1f, paint)
            canvas.drawLine(x, cy + s * 0.1f, x + s * 0.2f, cy - s * 0.1f, paint)
        }
    }

    companion object {
        const val WIFI = 0
        const val BLUETOOTH = 1
        const val DATA = 2
        const val AIRPLANE = 3
        const val TORCH = 4
        const val ROTATE = 5
        const val DND = 6
        const val LOCATION = 7
        const val HOTSPOT = 8
        const val SOUND = 9
        const val VIBRATE = 10
        const val MUTE = 11
        const val SUN = 12
        const val GEAR = 13
        const val PENCIL = 14
        const val BATTERY = 15
        const val DARK = 16
        const val CAST = 17
        const val CAMERA = 18
        const val PLAY = 19
        const val PAUSE = 20
        const val PREV = 21
        const val NEXT = 22
        const val OUTPUT = 23
        const val DEVICES = 24
        const val CHEVRON_DOWN = 25
        const val CHEVRON_UP = 26
        const val BELL = 27
        const val ALARM = 28
        const val POWER = 29
    }
}

/** Step 17b: a round glass button with an icon (the moon next to brightness, the speaker next to volume, like the reference). */
fun roundGlyphButton(context: Context, glyph: QuickGlyph, description: String, onTap: () -> Unit): View {
    val box = android.widget.FrameLayout(context)
    box.background = roundRect(Look.inkA(40), context.dpf(26f))
    box.contentDescription = description
    box.isClickable = true
    glyph.tint = Look.text
    box.addView(glyph, android.widget.FrameLayout.LayoutParams(context.dp(26), context.dp(26), android.view.Gravity.CENTER))
    box.setOnClickListener { onTap() }
    return box
}
