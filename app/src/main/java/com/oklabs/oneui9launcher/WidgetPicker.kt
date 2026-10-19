package com.oklabs.oneui9launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The widget picker: a dark sheet that slides up from the bottom. Every app that has widgets is one row;
 * tap a row and its widgets open as a strip of cards (picture, name, size in cells). Tap a card to add that widget.
 * Tap outside the sheet, Back or Home closes it.
 */
class WidgetPicker(
    context: Context,
    private val onPick: (WidgetEntry) -> Unit
) : FrameLayout(context) {

    /** True from the moment the sheet starts opening until it is completely gone. */
    val isOpen: Boolean get() = visibility == View.VISIBLE

    /** Step 8b: called when the sheet appears (true) or is completely gone (false). */
    var onOpenChanged: ((Boolean) -> Unit)? = null

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) onOpenChanged?.invoke(visibility == View.VISIBLE)
    }

    private val scrim = View(context)
    private val sheet = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val list = LinearLayout(context)

    private var insetTop = 0
    private var insetBottom = 0
    private var closing = false
    private var openBody: LinearLayout? = null
    private var openArrow: TextView? = null
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    init {
        visibility = View.GONE

        scrim.setBackgroundColor(Color.argb(140, 0, 0, 0))
        scrim.isClickable = true
        scrim.setOnClickListener { close(true) }
        addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))

        val radius = context.dpf(28f)
        val sheetBackground = GradientDrawable()
        sheetBackground.setColor(Look.panel(247))
        sheetBackground.cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = sheetBackground
        sheet.isClickable = true // taps on the sheet itself must not reach the scrim

        val handle = View(context)
        handle.background = roundRect(Look.inkA(90), context.dpf(3f))
        val handleLp = LinearLayout.LayoutParams(context.dp(36), context.dp(5))
        handleLp.gravity = Gravity.CENTER_HORIZONTAL
        handleLp.topMargin = context.dp(10)
        sheet.addView(handle, handleLp)

        val title = TextView(context)
        title.text = "Widgets"
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.typeface = medium
        title.setPadding(context.dp(22), context.dp(14), context.dp(22), context.dp(2))
        sheet.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))

        val hint = TextView(context)
        hint.text = "Tap an app, then tap a widget to add it"
        hint.setTextColor(Look.textA(150))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        hint.setPadding(context.dp(22), 0, context.dp(22), context.dp(10))
        sheet.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))

        list.orientation = LinearLayout.VERTICAL
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        sheet.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        addView(sheet, FrameLayout.LayoutParams(MATCH, context.dp(400), Gravity.BOTTOM))
    }

    /** Keeps the sheet clear of the status bar at the top and the navigation bar at the bottom. */
    fun setInsets(top: Int, bottom: Int) {
        insetTop = top
        insetBottom = bottom
        list.setPadding(0, 0, 0, max(bottom, context.dp(12)) + context.dp(12))
        if (isOpen && !closing) applySheetSize()
    }

    private fun layerHeight(): Int = if (height > 0) height else resources.displayMetrics.heightPixels

    private fun applySheetSize() {
        val layerH = layerHeight()
        val room = layerH - max(insetTop, context.dp(24)) - context.dp(24)
        val sheetH = min(room, (layerH * 0.8f).toInt())
        val lp = sheet.layoutParams as FrameLayout.LayoutParams
        if (lp.height != sheetH) {
            lp.height = sheetH
            sheet.layoutParams = lp
        }
    }

    // ------------------------------------------------------------------ open / close

    /** Shows the sheet with [groups] (one row per app, already sorted). */
    fun open(groups: List<WidgetGroup>) {
        scrim.animate().cancel()
        sheet.animate().cancel()
        closing = false
        list.removeAllViews()
        openBody = null
        openArrow = null

        if (groups.isEmpty()) {
            val note = TextView(context)
            note.text = "No widgets found on this phone"
            note.setTextColor(Look.textA(170))
            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            note.gravity = Gravity.CENTER
            note.setPadding(context.dp(22), context.dp(48), context.dp(22), context.dp(48))
            list.addView(note, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            for (group in groups) addGroup(group)
        }
        scroll.scrollTo(0, 0)

        visibility = View.VISIBLE
        applySheetSize()
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(200).start()
        sheet.translationY = layerHeight().toFloat()
        sheet.animate().translationY(0f).setDuration(280).setInterpolator(ease).start()
    }

    /** Slides the sheet away, or hides it at once when [animated] is false. */
    fun close(animated: Boolean) {
        if (!isOpen) return
        if (!animated) {
            finishClose()
            return
        }
        if (closing) return
        closing = true
        scrim.animate().alpha(0f).setDuration(180).start()
        sheet.animate()
            .translationY(layerHeight().toFloat())
            .setDuration(210)
            .setInterpolator(ease)
            .withEndAction { finishClose() }
            .start()
    }

    private fun finishClose() {
        scrim.animate().cancel()
        sheet.animate().cancel()
        closing = false
        visibility = View.GONE
        list.removeAllViews()
        openBody = null
        openArrow = null
    }

    // ------------------------------------------------------------------ rows and cards

    private fun addGroup(group: WidgetGroup) {
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.minimumHeight = context.dp(66)
        header.setPadding(context.dp(22), context.dp(8), context.dp(18), context.dp(8))
        header.isClickable = true
        header.background = RippleDrawable(
            ColorStateList.valueOf(Look.inkA(40)),
            null,
            roundRect(Color.WHITE, 0f)
        )

        val icon = ImageView(context)
        val bitmap = group.icon
        if (bitmap != null) icon.setImageBitmap(bitmap)
        header.addView(icon, LinearLayout.LayoutParams(context.dp(42), context.dp(42)))

        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        val name = TextView(context)
        name.text = group.label
        name.setTextColor(Look.text)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        name.typeface = medium
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        texts.addView(name, LinearLayout.LayoutParams(WRAP, WRAP))
        val count = TextView(context)
        count.text = if (group.entries.size == 1) "1 widget" else "${group.entries.size} widgets"
        count.setTextColor(Look.textA(150))
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        texts.addView(count, LinearLayout.LayoutParams(WRAP, WRAP))
        val textsLp = LinearLayout.LayoutParams(0, WRAP, 1f)
        textsLp.leftMargin = context.dp(14)
        header.addView(texts, textsLp)

        val arrow = TextView(context)
        arrow.text = "\u203A"
        arrow.setTextColor(Look.textA(200))
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        header.addView(arrow, LinearLayout.LayoutParams(WRAP, WRAP))

        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.visibility = View.GONE

        header.setOnClickListener {
            val wasOpen = body.visibility == View.VISIBLE
            collapseOpen()
            if (!wasOpen) {
                if (body.childCount == 0) fillBody(body, group)
                body.visibility = View.VISIBLE
                arrow.animate().rotation(90f).setDuration(160).start()
                openBody = body
                openArrow = arrow
                scroll.post { scroll.smoothScrollTo(0, header.top) }
            }
        }

        list.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        list.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun collapseOpen() {
        openBody?.visibility = View.GONE
        openArrow?.animate()?.rotation(0f)?.setDuration(160)?.start()
        openBody = null
        openArrow = null
    }

    /** The strip of widget cards of one app. Built the first time the row is opened. */
    private fun fillBody(body: LinearLayout, group: WidgetGroup) {
        val strip = HorizontalScrollView(context)
        strip.isHorizontalScrollBarEnabled = false
        strip.overScrollMode = View.OVER_SCROLL_NEVER
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(context.dp(16), context.dp(2), context.dp(16), context.dp(16))
        for (entry in group.entries) {
            val lp = LinearLayout.LayoutParams(context.dp(158), WRAP)
            lp.setMargins(context.dp(6), 0, context.dp(6), 0)
            row.addView(card(entry), lp)
        }
        strip.addView(row, FrameLayout.LayoutParams(WRAP, WRAP))
        body.addView(strip, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun card(entry: WidgetEntry): View {
        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(12))
        card.background = RippleDrawable(
            ColorStateList.valueOf(Look.inkA(60)),
            roundRect(Look.inkA(38), context.dpf(20f)),
            roundRect(Color.WHITE, context.dpf(20f))
        )

        val frame = FrameLayout(context)
        frame.background = roundRect(Color.argb(60, 0, 0, 0), context.dpf(14f))
        val preview = ImageView(context)
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        val picture = previewOf(entry)
        if (picture != null) preview.setImageDrawable(picture)
        frame.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))
        card.addView(frame, LinearLayout.LayoutParams(MATCH, context.dp(104)))

        val label = TextView(context)
        label.text = entry.label
        label.setTextColor(Look.text)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        label.typeface = medium
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        val labelLp = LinearLayout.LayoutParams(MATCH, WRAP)
        labelLp.topMargin = context.dp(8)
        card.addView(label, labelLp)

        val size = TextView(context)
        size.text = "${entry.spanX} \u00d7 ${entry.spanY}"
        size.setTextColor(Look.textA(150))
        size.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        card.addView(size, LinearLayout.LayoutParams(MATCH, WRAP))

        card.contentDescription = "${entry.label}, ${entry.spanX} by ${entry.spanY}"
        card.setOnClickListener {
            close(true)
            onPick(entry)
        }
        return card
    }

    /** The widget's own preview picture; the app's icon when it did not ship one. */
    private fun previewOf(entry: WidgetEntry): Drawable? = try {
        entry.info.loadPreviewImage(context, 0) ?: entry.info.loadIcon(context, 0)
    } catch (e: Exception) {
        null
    }
}
