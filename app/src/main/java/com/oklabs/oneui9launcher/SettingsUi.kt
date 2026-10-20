package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Step 13: the building blocks of the Settings screens ([HomeSettingsActivity]): a coloured tile with a little drawing on it,
 * cards with rows, switches, choice chips, a slider and a preview of the Apps screen grid. Everything is drawn in code.
 */

/** A rounded colour tile with a white drawing on it (the icon in front of a settings row). */
internal class SettingsTile(context: Context, private val kind: Int, private val color: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(0f, 0f, 0f, s, lighter(color), color, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, s, s), s * 0.3f, s * 0.3f, paint)
        paint.shader = null
        glyph(canvas, s)
    }

    private fun lighter(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * 0.82f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.12f).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun fill(): Paint {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        return paint
    }

    private fun stroke(width: Float): Paint {
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        return paint
    }

    private fun glyph(c: Canvas, s: Float) {
        when (kind) {
            HOME -> {
                val roof = Path()
                roof.moveTo(s * 0.5f, s * 0.2f)
                roof.lineTo(s * 0.82f, s * 0.48f)
                roof.lineTo(s * 0.74f, s * 0.48f)
                roof.lineTo(s * 0.74f, s * 0.78f)
                roof.lineTo(s * 0.26f, s * 0.78f)
                roof.lineTo(s * 0.26f, s * 0.48f)
                roof.lineTo(s * 0.18f, s * 0.48f)
                roof.close()
                c.drawPath(roof, fill())
                paint.color = color
                c.drawRoundRect(RectF(s * 0.44f, s * 0.58f, s * 0.56f, s * 0.78f), s * 0.02f, s * 0.02f, paint)
            }
            APPS -> {
                fill()
                for (i in 0..2) for (j in 0..2) c.drawCircle(s * (0.3f + 0.2f * i), s * (0.3f + 0.2f * j), s * 0.062f, paint)
            }
            DOCK -> {
                fill()
                c.drawRoundRect(RectF(s * 0.25f, s * 0.2f, s * 0.43f, s * 0.38f), s * 0.05f, s * 0.05f, paint)
                c.drawRoundRect(RectF(s * 0.57f, s * 0.2f, s * 0.75f, s * 0.38f), s * 0.05f, s * 0.05f, paint)
                c.drawRoundRect(RectF(s * 0.17f, s * 0.54f, s * 0.83f, s * 0.8f), s * 0.11f, s * 0.11f, paint)
                paint.color = color
                for (i in -1..1) c.drawCircle(s * (0.5f + i * 0.16f), s * 0.67f, s * 0.045f, paint)
            }
            ICONS -> {
                c.drawRoundRect(RectF(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f), s * 0.2f, s * 0.2f, stroke(s * 0.06f))
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.12f, fill())
            }
            LOOK -> {
                val oval = RectF(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f)
                c.drawOval(oval, stroke(s * 0.06f))
                c.drawArc(oval, 90f, 180f, true, fill())
            }
            GESTURES -> {
                c.drawLine(s * 0.5f, s * 0.68f, s * 0.5f, s * 0.28f, stroke(s * 0.07f))
                val head = Path()
                head.moveTo(s * 0.36f, s * 0.42f)
                head.lineTo(s * 0.5f, s * 0.28f)
                head.lineTo(s * 0.64f, s * 0.42f)
                c.drawPath(head, stroke(s * 0.07f))
                c.drawCircle(s * 0.5f, s * 0.79f, s * 0.05f, fill())
            }
            BADGES -> {
                fill()
                c.drawArc(RectF(s * 0.3f, s * 0.22f, s * 0.7f, s * 0.62f), 180f, 180f, true, paint)
                val skirt = Path()
                skirt.moveTo(s * 0.3f, s * 0.42f)
                skirt.lineTo(s * 0.7f, s * 0.42f)
                skirt.lineTo(s * 0.77f, s * 0.67f)
                skirt.lineTo(s * 0.23f, s * 0.67f)
                skirt.close()
                c.drawPath(skirt, paint)
                c.drawCircle(s * 0.5f, s * 0.76f, s * 0.06f, paint)
                paint.color = Color.rgb(255, 64, 80)
                c.drawCircle(s * 0.72f, s * 0.27f, s * 0.1f, paint)
            }
            EDGE -> {
                c.drawRoundRect(RectF(s * 0.28f, s * 0.16f, s * 0.72f, s * 0.84f), s * 0.1f, s * 0.1f, stroke(s * 0.05f))
                c.drawRoundRect(RectF(s * 0.6f, s * 0.34f, s * 0.7f, s * 0.66f), s * 0.04f, s * 0.04f, fill())
            }
            HIDE -> {
                c.drawOval(RectF(s * 0.16f, s * 0.33f, s * 0.84f, s * 0.67f), stroke(s * 0.055f))
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.085f, fill())
                c.drawLine(s * 0.24f, s * 0.78f, s * 0.76f, s * 0.22f, stroke(s * 0.06f))
            }
            ROTATE -> {
                c.drawArc(RectF(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f), 200f, 250f, false, stroke(s * 0.06f))
                val tip = Path()
                tip.moveTo(s * 0.36f, s * 0.78f)
                tip.lineTo(s * 0.52f, s * 0.66f)
                tip.lineTo(s * 0.52f, s * 0.9f)
                tip.close()
                c.drawPath(tip, fill())
            }
            BACKUP -> {
                c.drawLine(s * 0.5f, s * 0.6f, s * 0.5f, s * 0.26f, stroke(s * 0.065f))
                val head = Path()
                head.moveTo(s * 0.37f, s * 0.38f)
                head.lineTo(s * 0.5f, s * 0.25f)
                head.lineTo(s * 0.63f, s * 0.38f)
                c.drawPath(head, stroke(s * 0.065f))
                val tray = Path()
                tray.moveTo(s * 0.26f, s * 0.58f)
                tray.lineTo(s * 0.26f, s * 0.76f)
                tray.lineTo(s * 0.74f, s * 0.76f)
                tray.lineTo(s * 0.74f, s * 0.58f)
                c.drawPath(tray, stroke(s * 0.065f))
            }
            PRIVACY -> {
                val shield = Path()
                shield.moveTo(s * 0.5f, s * 0.17f)
                shield.lineTo(s * 0.78f, s * 0.28f)
                shield.lineTo(s * 0.78f, s * 0.5f)
                shield.cubicTo(s * 0.78f, s * 0.68f, s * 0.64f, s * 0.78f, s * 0.5f, s * 0.84f)
                shield.cubicTo(s * 0.36f, s * 0.78f, s * 0.22f, s * 0.68f, s * 0.22f, s * 0.5f)
                shield.lineTo(s * 0.22f, s * 0.28f)
                shield.close()
                c.drawPath(shield, fill())
                val tick = Path()
                tick.moveTo(s * 0.37f, s * 0.5f)
                tick.lineTo(s * 0.46f, s * 0.6f)
                tick.lineTo(s * 0.64f, s * 0.4f)
                stroke(s * 0.06f)
                paint.color = color
                c.drawPath(tick, paint)
            }
            DOC -> {
                c.drawRoundRect(RectF(s * 0.27f, s * 0.18f, s * 0.73f, s * 0.82f), s * 0.06f, s * 0.06f, fill())
                paint.color = color
                for (i in 0..2) {
                    val y = s * (0.34f + i * 0.14f)
                    c.drawRoundRect(RectF(s * 0.35f, y, s * (if (i == 2) 0.55f else 0.65f), y + s * 0.04f), s * 0.02f, s * 0.02f, paint)
                }
            }
            MAIL -> {
                c.drawRoundRect(RectF(s * 0.17f, s * 0.28f, s * 0.83f, s * 0.72f), s * 0.07f, s * 0.07f, fill())
                val flap = Path()
                flap.moveTo(s * 0.22f, s * 0.34f)
                flap.lineTo(s * 0.5f, s * 0.55f)
                flap.lineTo(s * 0.78f, s * 0.34f)
                stroke(s * 0.045f)
                paint.color = color
                c.drawPath(flap, paint)
            }
            INFO -> {
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.3f, fill())
                paint.color = color
                c.drawCircle(s * 0.5f, s * 0.36f, s * 0.04f, paint)
                c.drawRoundRect(RectF(s * 0.465f, s * 0.46f, s * 0.535f, s * 0.68f), s * 0.03f, s * 0.03f, paint)
            }
            STAR -> {
                val star = Path()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val r = if (i % 2 == 0) s * 0.31f else s * 0.135f
                    val x = s * 0.5f + (cos(a) * r).toFloat()
                    val y = s * 0.53f + (sin(a) * r).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                c.drawPath(star, fill())
            }
            SHARE -> {
                val line = stroke(s * 0.04f)
                c.drawLine(s * 0.3f, s * 0.5f, s * 0.7f, s * 0.3f, line)
                c.drawLine(s * 0.3f, s * 0.5f, s * 0.7f, s * 0.7f, line)
                fill()
                c.drawCircle(s * 0.3f, s * 0.5f, s * 0.085f, paint)
                c.drawCircle(s * 0.7f, s * 0.3f, s * 0.085f, paint)
                c.drawCircle(s * 0.7f, s * 0.7f, s * 0.085f, paint)
            }
            LOCK -> {
                c.drawArc(RectF(s * 0.34f, s * 0.2f, s * 0.66f, s * 0.6f), 180f, 180f, false, stroke(s * 0.06f))
                c.drawLine(s * 0.34f, s * 0.4f, s * 0.34f, s * 0.5f, paint)
                c.drawLine(s * 0.66f, s * 0.4f, s * 0.66f, s * 0.5f, paint)
                c.drawRoundRect(RectF(s * 0.26f, s * 0.46f, s * 0.74f, s * 0.8f), s * 0.06f, s * 0.06f, fill())
                paint.color = color
                c.drawCircle(s * 0.5f, s * 0.63f, s * 0.045f, paint)
            }
            BUG -> {
                c.drawRoundRect(RectF(s * 0.2f, s * 0.22f, s * 0.8f, s * 0.64f), s * 0.1f, s * 0.1f, fill())
                val tail = Path()
                tail.moveTo(s * 0.34f, s * 0.6f)
                tail.lineTo(s * 0.3f, s * 0.8f)
                tail.lineTo(s * 0.52f, s * 0.62f)
                tail.close()
                c.drawPath(tail, paint)
                paint.color = color
                c.drawRoundRect(RectF(s * 0.47f, s * 0.31f, s * 0.53f, s * 0.49f), s * 0.03f, s * 0.03f, paint)
                c.drawCircle(s * 0.5f, s * 0.55f, s * 0.035f, paint)
            }
            GLOBE -> {
                val r = s * 0.28f
                c.drawCircle(s * 0.5f, s * 0.5f, r, stroke(s * 0.05f))
                c.drawOval(RectF(s * 0.5f - r * 0.45f, s * 0.5f - r, s * 0.5f + r * 0.45f, s * 0.5f + r), paint)
                c.drawLine(s * 0.5f - r, s * 0.5f, s * 0.5f + r, s * 0.5f, paint)
            }
            COPY -> {
                c.drawRoundRect(RectF(s * 0.22f, s * 0.3f, s * 0.58f, s * 0.8f), s * 0.06f, s * 0.06f, stroke(s * 0.05f))
                c.drawRoundRect(RectF(s * 0.42f, s * 0.2f, s * 0.78f, s * 0.7f), s * 0.06f, s * 0.06f, fill())
            }
        }
    }

    companion object {
        const val HOME = 0
        const val APPS = 1
        const val DOCK = 2
        const val ICONS = 3
        const val LOOK = 4
        const val GESTURES = 5
        const val BADGES = 6
        const val EDGE = 7
        const val HIDE = 8
        const val ROTATE = 9
        const val BACKUP = 10
        const val PRIVACY = 11
        const val DOC = 12
        const val MAIL = 13
        const val INFO = 14
        const val STAR = 15
        const val SHARE = 16
        const val LOCK = 17
        const val BUG = 18
        const val GLOBE = 19
        const val COPY = 20

        val BLUE = Color.rgb(54, 128, 255)
        val GREEN = Color.rgb(38, 184, 108)
        val ORANGE = Color.rgb(255, 144, 38)
        val RED = Color.rgb(240, 70, 86)
        val PURPLE = Color.rgb(140, 92, 246)
        val TEAL = Color.rgb(20, 176, 176)
        val PINK = Color.rgb(236, 82, 152)
        val SLATE = Color.rgb(104, 122, 152)
        val YELLOW = Color.rgb(246, 178, 24)
    }
}

/** A small picture of the Apps screen grid: [cols] x [rows] icons (null = Auto, drawn as 4 x 5). */
internal class GridPreview(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cols = 4
    private var rows = 5

    fun setGrid(newCols: Int, newRows: Int) {
        cols = if (newCols > 0) newCols else 4
        rows = if (newRows > 0) newRows else 5
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Look.inkA(28)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), context.dpf(16f), context.dpf(16f), paint)
        val pad = context.dpf(12f)
        val cellW = (w - 2 * pad) / cols
        val cellH = (h - 2 * pad) / rows
        val side = min(cellW, cellH) * 0.62f
        paint.color = Look.textA(210)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = pad + cellW * (c + 0.5f)
                val cy = pad + cellH * (r + 0.5f)
                canvas.drawRoundRect(RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2), side * 0.32f, side * 0.32f, paint)
            }
        }
    }
}

/** Creates the views of a Settings page. All colours follow [Look] (light / dark panels). */
internal class SettingsKit(private val ctx: Context) {

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = ctx.dp(v)

    /** The top bar of a page: a back arrow and the [title]. */
    fun topBar(title: String, onBack: () -> Unit): View {
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(8), dp(6), dp(20), dp(2))
        val back = TextView(ctx)
        back.text = "\u2039"
        back.gravity = Gravity.CENTER
        back.setTextColor(Look.text)
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        back.isClickable = true
        back.contentDescription = "Back"
        back.setOnClickListener { onBack() }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val head = TextView(ctx)
        head.text = title
        head.setTextColor(Look.text)
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        head.maxLines = 1
        head.ellipsize = TextUtils.TruncateAt.END
        bar.addView(head, LinearLayout.LayoutParams(0, wrap, 1f))
        return bar
    }

    /** A whole page: a top bar (back arrow + [title]) and a scrolling column. [build] fills the column. */
    fun page(title: String, onBack: () -> Unit, build: (LinearLayout) -> Unit): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.addView(topBar(title, onBack), LinearLayout.LayoutParams(match, wrap))

        val scroll = ScrollView(ctx)
        scroll.isVerticalScrollBarEnabled = false
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(4), dp(18), dp(32))
        scroll.addView(box, FrameLayout.LayoutParams(match, wrap))
        root.addView(scroll, LinearLayout.LayoutParams(match, 0, 1f))
        build(box)
        return root
    }

    /** A small grey heading above a card. */
    fun label(box: LinearLayout, text: String) {
        val view = TextView(ctx)
        view.text = text
        view.setTextColor(Look.textA(170))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        view.setPadding(dp(6), dp(22), 0, dp(8))
        box.addView(view, LinearLayout.LayoutParams(match, wrap))
    }

    /** A grey explanation under a card. */
    fun note(box: LinearLayout, text: String) {
        val view = TextView(ctx)
        view.text = text
        view.setTextColor(Look.textA(140))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        view.setPadding(dp(6), dp(8), dp(6), 0)
        box.addView(view, LinearLayout.LayoutParams(match, wrap))
    }

    /** Puts [rows] into one rounded card with thin lines between them. */
    fun card(box: LinearLayout, vararg rows: View) {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.background = roundRect(Look.inkA(24), ctx.dpf(22f))
        for ((i, row) in rows.withIndex()) {
            if (i > 0) {
                val line = View(ctx)
                line.setBackgroundColor(Look.inkA(22))
                val lp = LinearLayout.LayoutParams(match, 1)
                lp.leftMargin = dp(16)
                lp.rightMargin = dp(16)
                card.addView(line, lp)
            }
            card.addView(row, LinearLayout.LayoutParams(match, wrap))
        }
        box.addView(card, LinearLayout.LayoutParams(match, wrap))
    }

    private fun texts(title: String, subtitle: String?): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val main = TextView(ctx)
        main.text = title
        main.setTextColor(Look.text)
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        col.addView(main, LinearLayout.LayoutParams(wrap, wrap))
        if (!subtitle.isNullOrEmpty()) {
            val sub = TextView(ctx)
            sub.text = subtitle
            sub.setTextColor(Look.textA(150))
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            col.addView(sub, LinearLayout.LayoutParams(wrap, wrap))
        }
        return col
    }

    private fun line(tile: Int, tileColor: Int, title: String, subtitle: String?): LinearLayout {
        val line = LinearLayout(ctx)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(dp(16), dp(12), dp(16), dp(12))
        if (tile >= 0) {
            val view = SettingsTile(ctx, tile, tileColor)
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.rightMargin = dp(14)
            line.addView(view, lp)
        }
        line.addView(texts(title, subtitle), LinearLayout.LayoutParams(0, wrap, 1f))
        return line
    }

    private fun pressable(view: View, onTap: () -> Unit) {
        view.isClickable = true
        view.setOnClickListener { onTap() }
    }

    /** A tappable line: optional coloured tile ([tile] = -1 for none), title, small second line and an arrow. */
    fun row(tile: Int, tileColor: Int, title: String, subtitle: String?, onTap: () -> Unit): View {
        val line = line(tile, tileColor, title, subtitle)
        val arrow = TextView(ctx)
        arrow.text = "›"
        arrow.setTextColor(Look.textA(150))
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        line.addView(arrow, LinearLayout.LayoutParams(wrap, wrap))
        pressable(line, onTap)
        return line
    }

    /** A line with a switch. The whole line is the button. [onChange] gets the new state. */
    fun toggle(tile: Int, tileColor: Int, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val line = line(tile, tileColor, title, subtitle)
        val sw = Switch(ctx)
        sw.isChecked = checked
        sw.isClickable = false
        line.addView(sw, LinearLayout.LayoutParams(wrap, wrap))
        pressable(line) {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
        return line
    }

    /** A line that shows the current choice on the right. */
    fun value(title: String, current: String, onTap: () -> Unit): View {
        val line = line(-1, 0, title, null)
        val value = TextView(ctx)
        value.text = current
        value.setTextColor(Look.textA(160))
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        line.addView(value, LinearLayout.LayoutParams(wrap, wrap))
        val arrow = TextView(ctx)
        arrow.text = "›"
        arrow.setTextColor(Look.textA(150))
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        val lp = LinearLayout.LayoutParams(wrap, wrap)
        lp.leftMargin = dp(8)
        line.addView(arrow, lp)
        pressable(line, onTap)
        return line
    }

    /** A title with a line of choice chips under it; the picked one is filled. */
    fun chips(title: String, names: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val block = LinearLayout(ctx)
        block.orientation = LinearLayout.VERTICAL
        block.setPadding(dp(16), dp(14), dp(16), dp(14))
        val head = TextView(ctx)
        head.text = title
        head.setTextColor(Look.text)
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        block.addView(head, LinearLayout.LayoutParams(wrap, wrap))

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val views = ArrayList<TextView>()
        fun paintAll(current: Int) {
            for ((i, v) in views.withIndex()) {
                val on = i == current
                v.setTextColor(if (on) Look.panel(255) else Look.text)
                v.background = roundRect(if (on) Look.text else Look.inkA(32), ctx.dpf(20f))
            }
        }
        for ((i, name) in names.withIndex()) {
            val v = TextView(ctx)
            v.text = name
            v.gravity = Gravity.CENTER
            v.maxLines = 1
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            v.setPadding(dp(4), dp(10), dp(4), dp(10))
            v.isClickable = true
            v.setOnClickListener {
                paintAll(i)
                onPick(i)
            }
            val lp = LinearLayout.LayoutParams(0, wrap, 1f)
            if (i > 0) lp.leftMargin = dp(6)
            row.addView(v, lp)
            views.add(v)
        }
        paintAll(selected)
        val rowLp = LinearLayout.LayoutParams(match, wrap)
        rowLp.topMargin = dp(10)
        block.addView(row, rowLp)
        return block
    }

    /** A title, a coloured-dots row and nothing else: [colors] are the choices, [selected] the picked colour. */
    fun colorDots(title: String, colors: IntArray, selected: Int, onPick: (Int) -> Unit): View {
        val block = LinearLayout(ctx)
        block.orientation = LinearLayout.VERTICAL
        block.setPadding(dp(16), dp(14), dp(16), dp(14))
        val head = TextView(ctx)
        head.text = title
        head.setTextColor(Look.text)
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        block.addView(head, LinearLayout.LayoutParams(wrap, wrap))
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val dots = ArrayList<Pair<Int, View>>()
        fun paintAll(current: Int) {
            for ((color, view) in dots) {
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.OVAL
                shape.setColor(color)
                shape.setStroke(dp(if (color == current) 3 else 1), if (color == current) Look.text else Look.inkA(80))
                view.background = shape
            }
        }
        for (color in colors) {
            val dot = View(ctx)
            dot.isClickable = true
            dot.setOnClickListener {
                paintAll(color)
                onPick(color)
            }
            val lp = LinearLayout.LayoutParams(dp(36), dp(36))
            lp.rightMargin = dp(12)
            row.addView(dot, lp)
            dots.add(Pair(color, dot))
        }
        paintAll(selected)
        val rowLp = LinearLayout.LayoutParams(wrap, wrap)
        rowLp.topMargin = dp(12)
        block.addView(row, rowLp)
        return block
    }

    /** A title with a slider (0..[max]). [format] turns the value into the text shown on the right. */
    fun slider(title: String, max: Int, progress: Int, format: (Int) -> String, onChange: (Int) -> Unit): View {
        val block = LinearLayout(ctx)
        block.orientation = LinearLayout.VERTICAL
        block.setPadding(dp(16), dp(14), dp(16), dp(10))
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        val name = TextView(ctx)
        name.text = title
        name.setTextColor(Look.text)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        head.addView(name, LinearLayout.LayoutParams(0, wrap, 1f))
        val shown = TextView(ctx)
        shown.text = format(progress)
        shown.setTextColor(Look.textA(160))
        shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        head.addView(shown, LinearLayout.LayoutParams(wrap, wrap))
        block.addView(head, LinearLayout.LayoutParams(match, wrap))
        val seek = SeekBar(ctx)
        seek.max = max
        seek.progress = progress
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                shown.text = format(value)
                if (fromUser) onChange(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        val lp = LinearLayout.LayoutParams(match, wrap)
        lp.topMargin = dp(6)
        block.addView(seek, lp)
        return block
    }

    /** Free text inside a card (policy paragraphs, a short explanation). */
    fun paragraph(text: String, bold: Boolean = false, sizeSp: Float = 14.5f): View {
        val view = TextView(ctx)
        view.text = text
        view.setTextColor(if (bold) Look.text else Look.textA(215))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (bold) 16f else sizeSp)
        if (bold) view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
        view.setLineSpacing(0f, 1.12f)
        view.setPadding(dp(16), dp(if (bold) 14 else 8), dp(16), dp(if (bold) 2 else 8))
        return view
    }

    /** A big, plain button (used on the Contact page). */
    fun button(text: String, onTap: () -> Unit): View {
        val view = TextView(ctx)
        view.text = text
        view.gravity = Gravity.CENTER
        view.setTextColor(Look.panel(255))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        view.setPadding(dp(16), dp(14), dp(16), dp(14))
        view.background = roundRect(Look.text, ctx.dpf(26f))
        pressable(view, onTap)
        return view
    }

    fun space(box: LinearLayout, heightDp: Int) {
        box.addView(View(ctx), LinearLayout.LayoutParams(match, dp(heightDp)))
    }
}
