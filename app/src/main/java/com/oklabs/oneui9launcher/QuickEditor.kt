package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Equal cells, [COLUMNS] to a row, filled left to right. Knows which slot a finger position falls in. */
internal class TileGrid(context: Context) : ViewGroup(context) {

    private val cellH = context.dp(CELL_DP)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cellW = w / COLUMNS
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY)
            )
        }
        val rows = (childCount + COLUMNS - 1) / COLUMNS
        setMeasuredDimension(w, max(1, rows) * cellH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellW = (r - l) / COLUMNS
        for (i in 0 until childCount) {
            val x = (i % COLUMNS) * cellW
            val y = (i / COLUMNS) * cellH
            getChildAt(i).layout(x, y, x + cellW, y + cellH)
        }
    }

    /** The insertion slot (0..count) under a point given in this grid's own coordinates. */
    fun slotAt(x: Float, y: Float): Int {
        val cellW = max(1, width / COLUMNS)
        val col = (x / cellW).toInt().coerceIn(0, COLUMNS - 1)
        val row = (y / cellH).toInt().coerceAtLeast(0)
        return (row * COLUMNS + col).coerceIn(0, childCount)
    }

    /** Left edge and top of slot [slot] (it may be the empty slot after the last tile) and the cell height. */
    fun slotX(slot: Int): Int = (slot % COLUMNS) * max(1, width / COLUMNS)
    fun slotY(slot: Int): Int = (slot / COLUMNS) * cellH
    val cellHeight: Int get() = cellH

    companion object {
        const val COLUMNS = 4
        const val CELL_DP = 88
    }
}

/**
 * Step 16b: the "Edit buttons" screen of the Quick panel. The buttons on the panel (top) and the ones that are not on it
 * ("Available buttons", bottom). Touch and hold a button to move it: drop it in the top box to add it / put it in another place,
 * drop a panel button in the bottom box to take it off. Or just tap: a tap on an available button adds it, the red minus removes one.
 * The first two buttons of the panel are drawn wide. Done saves, Reset brings back the first ten; Back or Home closes without saving.
 */
internal class QuickEditor(
    context: Context,
    private val say: (String) -> Unit,
    private val onDone: (List<String>) -> Unit
) : FrameLayout(context) {

    private var active: List<String> = QuickTiles.DEFAULT_ORDER
    private val activeGrid = TileGrid(context)
    private val availGrid = TileGrid(context)
    private val activeBox = FrameLayout(context)
    private val availBox = FrameLayout(context)
    private val hint = TextView(context)
    private val emptyNote = TextView(context)
    private val content = LinearLayout(context)
    private val marker = View(context)

    // the drag in progress
    private var dragId: String? = null
    private var dragFromActive = false
    private var dragIndex = -1
    private var dragSource: View? = null
    private var ghost: View? = null

    init {
        content.orientation = LinearLayout.VERTICAL
        addView(content, LayoutParams(MATCH, WRAP))
        buildHeader()

        hint.setTextColor(Look.textA(190))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        hint.gravity = Gravity.CENTER
        val hp = LinearLayout.LayoutParams(MATCH, WRAP)
        hp.topMargin = context.dp(12)
        content.addView(hint, hp)

        box(activeBox, activeGrid)
        val ap = LinearLayout.LayoutParams(MATCH, WRAP)
        ap.topMargin = context.dp(12)
        content.addView(activeBox, ap)

        val title = TextView(context)
        title.text = "Available buttons"
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        title.gravity = Gravity.CENTER
        val tp = LinearLayout.LayoutParams(MATCH, WRAP)
        tp.topMargin = context.dp(18)
        content.addView(title, tp)

        box(availBox, availGrid)
        emptyNote.text = "Every button is on the panel"
        emptyNote.setTextColor(Look.textA(150))
        emptyNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        emptyNote.gravity = Gravity.CENTER
        availBox.addView(emptyNote, LayoutParams(MATCH, context.dp(TileGrid.CELL_DP)))
        val vp = LinearLayout.LayoutParams(MATCH, WRAP)
        vp.topMargin = context.dp(10)
        content.addView(availBox, vp)

        marker.background = roundRect(Look.text, context.dpf(2f))
        marker.visibility = View.GONE
        addView(marker, LayoutParams(context.dp(4), context.dp(52)))
    }

    private fun box(box: FrameLayout, grid: TileGrid) {
        box.background = roundRect(Look.inkA(24), context.dpf(24f))
        box.setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        box.addView(grid, LayoutParams(MATCH, WRAP))
    }

    private fun buildHeader() {
        // Step 17c, like the reference: "Panel settings" with "Touch and hold to change order" under it, and Reset / Done as plain words at the right.
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        val title = TextView(context)
        title.text = "Panel settings"
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        texts.addView(title, LinearLayout.LayoutParams(WRAP, WRAP))
        val sub = TextView(context)
        sub.text = "Touch and hold to change order"
        sub.setTextColor(Look.textA(190))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        texts.addView(sub, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(textAction("Reset", false) {
            active = QuickTiles.DEFAULT_ORDER
            render()
        }, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(textAction("Done", true) { onDone(active) }, LinearLayout.LayoutParams(WRAP, WRAP))
        content.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** A plain word as a button (no pill), like "Done" in the reference. */
    private fun textAction(text: String, strong: Boolean, onTap: () -> Unit): TextView {
        val v = TextView(context)
        v.text = text
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        v.typeface = Typeface.create(if (strong) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        v.setTextColor(if (strong) Look.text else Look.textA(200))
        v.setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        v.isClickable = true
        v.setOnClickListener { onTap() }
        return v
    }

    private fun pill(text: String, strong: Boolean, onTap: () -> Unit): TextView {
        val v = TextView(context)
        v.text = text
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        v.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        v.setPadding(context.dp(20), context.dp(9), context.dp(20), context.dp(9))
        val onFg = if (Look.dark) Color.rgb(24, 26, 32) else Color.WHITE
        v.setTextColor(if (strong) onFg else Look.text)
        v.background = roundRect(if (strong) Look.text else Look.inkA(38), context.dpf(22f))
        v.isClickable = true
        v.setOnClickListener { onTap() }
        return v
    }

    /** Shows [order] as the buttons on the panel. */
    fun load(order: List<String>) {
        active = order
        render()
    }

    // ------------------------------------------------------------------ drawing

    private fun render() {
        hint.text = "At least ${QuickTiles.MIN_BUTTONS} buttons are needed. The first two are wide."
        activeGrid.removeAllViews()
        availGrid.removeAllViews()
        for (id in active) {
            val def = QuickTiles.def(id) ?: continue
            activeGrid.addView(tile(def, true))
        }
        val rest = QuickTiles.ALL.filter { it.id !in active }
        for (def in rest) availGrid.addView(tile(def, false))
        emptyNote.visibility = if (rest.isEmpty()) View.VISIBLE else View.GONE
        availGrid.visibility = if (rest.isEmpty()) View.GONE else View.VISIBLE
        marker.visibility = View.GONE
    }

    /** One button: its circle with the icon, the name under it and (on the panel side) the red minus. */
    private fun tile(def: QuickTileDef, onPanel: Boolean): View {
        val c = context
        val cell = LinearLayout(c)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.setPadding(0, c.dp(8), 0, 0)
        cell.tag = def.id

        val holder = FrameLayout(c)
        val circle = FrameLayout(c)
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Look.inkA(44))
        }
        val glyph = QuickGlyph(c, def.glyph)
        glyph.tint = Look.text
        circle.addView(glyph, LayoutParams(c.dp(26), c.dp(26), Gravity.CENTER))
        holder.addView(circle, LayoutParams(c.dp(50), c.dp(50), Gravity.CENTER))
        if (onPanel) {
            val minus = TextView(c)
            minus.text = "−"
            minus.gravity = Gravity.CENTER
            minus.setTextColor(Color.WHITE)
            minus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minus.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minus.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(229, 57, 53))
            }
            minus.contentDescription = "Remove " + def.label
            minus.isClickable = true
            minus.setOnClickListener { remove(def.id) }
            holder.addView(minus, LayoutParams(c.dp(22), c.dp(22), Gravity.TOP or Gravity.START))
        }
        cell.addView(holder, LinearLayout.LayoutParams(c.dp(60), c.dp(56)))

        val label = TextView(c)
        label.text = def.label
        label.setTextColor(Look.text)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = c.dp(3)
        cell.addView(label, lp)

        cell.isClickable = true
        cell.isLongClickable = true
        cell.contentDescription = def.label
        if (!onPanel) cell.setOnClickListener { add(def.id) }
        cell.setOnLongClickListener {
            startDrag(cell, def, onPanel)
            true
        }
        cell.setOnTouchListener { v, ev -> if (dragSource === v) dragTouch(ev) else false }
        return cell
    }

    private fun add(id: String) {
        active = QuickTiles.added(active, id, active.size)
        render()
    }

    private fun remove(id: String) {
        val next = QuickTiles.removed(active, id)
        if (next === active || next == active) {
            say("${QuickTiles.MIN_BUTTONS} buttons are required")
            return
        }
        active = next
        render()
    }

    // ------------------------------------------------------------------ dragging

    private fun startDrag(cell: View, def: QuickTileDef, fromActive: Boolean) {
        if (dragSource != null) return
        dragId = def.id
        dragFromActive = fromActive
        dragIndex = if (fromActive) active.indexOf(def.id) else -1
        dragSource = cell
        var p: android.view.ViewParent? = cell.parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(true)
            p = p.parent
        }
        cell.alpha = 0.3f
        val g = tile(def, false)
        g.alpha = 0.92f
        g.scaleX = 1.12f
        g.scaleY = 1.12f
        g.elevation = context.dpf(8f)
        g.isClickable = false
        g.isLongClickable = false
        g.setOnLongClickListener(null)
        g.measure(
            MeasureSpec.makeMeasureSpec(cell.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(cell.height, MeasureSpec.EXACTLY)
        )
        g.layout(0, 0, cell.width, cell.height)
        addView(g, LayoutParams(cell.width, cell.height))
        ghost = g
        // start where the original is
        val here = IntArray(2)
        val mine = IntArray(2)
        cell.getLocationOnScreen(here)
        getLocationOnScreen(mine)
        g.translationX = (here[0] - mine[0]).toFloat()
        g.translationY = (here[1] - mine[1]).toFloat()
    }

    private fun dragTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> moveDrag(ev.rawX, ev.rawY)
            MotionEvent.ACTION_UP -> {
                endDrag(ev.rawX, ev.rawY, true)
            }
            MotionEvent.ACTION_CANCEL -> endDrag(ev.rawX, ev.rawY, false)
        }
        return true
    }

    /** Where in the top box a finger at ([rawX], [rawY]) would drop, or -1 when it is not over that box. */
    private fun slotUnder(rawX: Float, rawY: Float): Int {
        val box = IntArray(2)
        activeBox.getLocationOnScreen(box)
        val inside = rawX >= box[0] && rawX <= box[0] + activeBox.width && rawY >= box[1] && rawY <= box[1] + activeBox.height
        if (!inside) return -1
        val grid = IntArray(2)
        activeGrid.getLocationOnScreen(grid)
        return activeGrid.slotAt(rawX - grid[0], rawY - grid[1])
    }

    private fun moveDrag(rawX: Float, rawY: Float) {
        val g = ghost ?: return
        val mine = IntArray(2)
        getLocationOnScreen(mine)
        g.translationX = rawX - mine[0] - g.width / 2f
        g.translationY = rawY - mine[1] - g.height / 2f
        val slot = slotUnder(rawX, rawY)
        if (slot < 0) {
            marker.visibility = View.GONE
            // a panel button dragged out of the top box will be taken off: say so by fading it
            g.alpha = if (dragFromActive) 0.5f else 0.92f
            return
        }
        g.alpha = 0.92f
        val grid = IntArray(2)
        activeGrid.getLocationOnScreen(grid)
        marker.translationX = (grid[0] - mine[0] + activeGrid.slotX(slot) + context.dp(2)).toFloat()
        marker.translationY = (grid[1] - mine[1] + activeGrid.slotY(slot) + (activeGrid.cellHeight - context.dp(52)) / 2).toFloat()
        marker.visibility = View.VISIBLE
    }

    private fun endDrag(rawX: Float, rawY: Float, apply: Boolean) {
        val id = dragId
        val slot = if (apply) slotUnder(rawX, rawY) else -1
        val fromActive = dragFromActive
        val index = dragIndex
        ghost?.let { removeView(it) }
        dragSource?.alpha = 1f
        ghost = null
        dragSource = null
        dragId = null
        marker.visibility = View.GONE
        if (!apply || id == null) {
            post { render() }
            return
        }
        when {
            slot >= 0 && fromActive -> active = QuickTiles.moved(active, index, slot)
            slot >= 0 -> active = QuickTiles.added(active, id, slot)
            fromActive -> {
                val next = QuickTiles.removed(active, id)
                if (next == active) say("${QuickTiles.MIN_BUTTONS} buttons are required") else active = next
            }
        }
        post { render() } // not while this very touch is still being delivered to the tile that is about to be replaced
    }
}
