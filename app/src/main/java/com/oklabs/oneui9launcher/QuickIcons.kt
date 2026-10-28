package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Step 30b: the icon a person picked for a Quick panel button. Stored in the "launcher" prefs as lines "id=kind".
 * Nothing here is picked for a button -> the automatic icon (QuickGlyphs.forLabel) is used as before.
 */
object QuickIcons {
    private const val PREFS = "launcher"
    private const val KEY = "qp_icons"

    private var cache: Map<String, Int> = emptyMap()

    /** "id=kind" lines -> map. The id may itself contain '=', so the LAST '=' splits. Bad lines are skipped. */
    fun parse(raw: String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (line in raw.split('\n')) {
            val i = line.lastIndexOf('=')
            if (i <= 0) continue
            val kind = line.substring(i + 1).trim().toIntOrNull() ?: continue
            if (kind < 0) continue
            out[line.substring(0, i)] = kind
        }
        return out
    }

    fun encode(map: Map<String, Int>): String =
        map.entries.filter { it.key.isNotEmpty() && '\n' !in it.key }.joinToString("\n") { it.key + "=" + it.value }

    fun load(context: Context) {
        cache = parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
    }

    /** The picked icon kind for button [id], or null for "automatic". */
    fun of(id: String): Int? = cache[id]

    /** [kind] null = back to automatic. */
    fun set(context: Context, id: String, kind: Int?) {
        val m = LinkedHashMap(cache)
        if (kind == null) m.remove(id) else m[id] = kind
        cache = m
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(m)).apply()
    }

    /** Every icon the gallery offers, in the order shown. */
    val GALLERY: List<Int> = (0..12).toList() + listOf(13, 15, 16, 17, 18, 27, 28, 29, 30, 31, QuickGlyph.GRID) + (33..QuickGlyphArt.LAST).toList()
}

/**
 * The "Change icons" screen shown inside the Quick panel (long-press the gear): first the list of buttons, then a grid of icons for the one tapped.
 */
class IconGallery(context: Context, private val buttons: List<QuickTileDef>, private val onDone: () -> Unit) : LinearLayout(context) {

    private var picking: QuickTileDef? = null

    init {
        orientation = VERTICAL
        showList()
    }

    /** Back button: from the grid to the list; false when already on the list (the caller leaves). */
    fun back(): Boolean {
        if (picking == null) return false
        showList()
        return true
    }

    private fun title(text: String, hint: String? = null) {
        val c = context
        val t = TextView(c)
        t.text = text
        t.setTextColor(PanelLook.text)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setPadding(c.dp(6), c.dp(4), 0, c.dp(2))
        addView(t, LayoutParams(-1, -2))
        if (hint != null) {
            val h = TextView(c)
            h.text = hint
            h.setTextColor(PanelLook.textA(170))
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            h.setPadding(c.dp(6), 0, 0, c.dp(8))
            addView(h, LayoutParams(-1, -2))
        }
    }

    private fun pill(text: String, click: () -> Unit): TextView {
        val c = context
        val b = TextView(c)
        b.text = text
        b.gravity = Gravity.CENTER
        b.setTextColor(PanelLook.text)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        b.background = roundRect(PanelLook.glass(110), c.dpf(22f))
        b.isClickable = true
        b.setOnClickListener { click() }
        return b
    }

    private fun showList() {
        picking = null
        removeAllViews()
        val c = context
        title("Change icons", "Tap a button, then pick its icon.")
        val col = LinearLayout(c)
        col.orientation = VERTICAL
        for (def in buttons) {
            val row = LinearLayout(c)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(c.dp(10), 0, c.dp(10), 0)
            row.background = roundRect(PanelLook.glass(80), c.dpf(18f))
            row.isClickable = true
            row.setOnClickListener { showGrid(def) }
            val g = QuickGlyph(c, QuickIcons.of(def.id) ?: def.glyph)
            g.tint = PanelLook.text
            if (QuickIcons.of(def.id) == null && QuickTiles.isLetterButton(def.id) && def.glyph == QuickGlyph.GRID) g.letter = def.label.trim().take(1).uppercase()
            row.addView(g, LayoutParams(c.dp(30), c.dp(30)))
            val name = TextView(c)
            name.text = def.label
            name.setTextColor(PanelLook.text)
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            name.maxLines = 1
            val np = LayoutParams(0, -2, 1f)
            np.leftMargin = c.dp(12)
            row.addView(name, np)
            val state = TextView(c)
            state.text = if (QuickIcons.of(def.id) == null) "Automatic" else "Your icon"
            state.setTextColor(PanelLook.textA(160))
            state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            row.addView(state, LayoutParams(-2, -2))
            val lp = LayoutParams(-1, c.dp(48))
            lp.bottomMargin = c.dp(6)
            col.addView(row, lp)
        }
        val sv = ScrollView(c)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        addView(sv, LayoutParams(-1, c.dp(330)))
        val done = pill("Done") { onDone() }
        val dp = LayoutParams(-1, c.dp(44))
        dp.topMargin = c.dp(8)
        addView(done, dp)
    }

    private fun showGrid(def: QuickTileDef) {
        picking = def
        removeAllViews()
        val c = context
        title(def.label, "Pick an icon for this button.")
        val grid = GridLayout(c)
        grid.columnCount = 5
        val current = QuickIcons.of(def.id)
        for (kind in QuickIcons.GALLERY) {
            val cell = LinearLayout(c)
            cell.gravity = Gravity.CENTER
            val selected = current == kind
            cell.background = roundRect(if (selected) PanelLook.textA(70) else PanelLook.glass(80), c.dpf(16f))
            cell.isClickable = true
            val g = QuickGlyph(c, kind)
            g.tint = PanelLook.text
            if (kind == QuickGlyph.GRID) g.letter = def.label.trim().take(1).uppercase()
            cell.addView(g, LayoutParams(c.dp(28), c.dp(28)))
            cell.setOnClickListener {
                QuickIcons.set(c, def.id, kind)
                showList()
            }
            val lp = GridLayout.LayoutParams()
            lp.width = c.dp(56)
            lp.height = c.dp(56)
            lp.setMargins(c.dp(3), c.dp(3), c.dp(3), c.dp(3))
            grid.addView(cell, lp)
        }
        val sv = ScrollView(c)
        sv.isVerticalScrollBarEnabled = false
        val holder = LinearLayout(c)
        holder.gravity = Gravity.CENTER_HORIZONTAL
        holder.addView(grid)
        sv.addView(holder)
        addView(sv, LayoutParams(-1, c.dp(300)))
        val row = LinearLayout(c)
        row.orientation = HORIZONTAL
        val auto = pill("Automatic") {
            QuickIcons.set(c, def.id, null)
            showList()
        }
        val cancel = pill("Cancel") { showList() }
        val l1 = LayoutParams(0, c.dp(44), 1f)
        l1.rightMargin = c.dp(5)
        val l2 = LayoutParams(0, c.dp(44), 1f)
        l2.leftMargin = c.dp(5)
        row.addView(auto, l1)
        row.addView(cancel, l2)
        val rp = LayoutParams(-1, -2)
        rp.topMargin = c.dp(8)
        addView(row, rp)
    }
}
