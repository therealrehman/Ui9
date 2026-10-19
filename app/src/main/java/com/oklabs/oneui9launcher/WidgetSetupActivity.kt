package com.oklabs.oneui9launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Step 6c2: the setup screen of the launcher's own widgets (Clock, Mini clock, Analog clock, Date).
 * Android opens it when the widget is added and the launcher opens it again from the widget's menu ("Widget settings").
 * The top half shows the real widget, live, over the wallpaper; the bottom sheet has the choices:
 * style (digital / analog), colour, time format (phone / 12 h / 24 h), date line on / off, background (none / glass / dark).
 * Done saves and updates the widget; Back / Cancel changes nothing.
 */
class WidgetSetupActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind = BuiltInKind.CLOCK
    private lateinit var config: WidgetConfig

    private lateinit var previewHolder: FrameLayout
    private lateinit var optionsBox: LinearLayout
    private lateinit var sheet: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Until "Done" is tapped the answer is "cancelled": the launcher then drops a widget that was just being added.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        val info = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        val found = BuiltInKind.fromProvider(info?.provider?.className)
        if (found == null) {
            finish()
            return
        }
        kind = found
        config = WidgetConfigStore.load(this, widgetId, kind)

        buildScreen()
        render()
    }

    // ------------------------------------------------------------------ screen

    private fun buildScreen() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.argb(70, 0, 0, 0)) // the wallpaper stays visible behind the preview

        // Top: title and the live widget, over the wallpaper.
        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER_HORIZONTAL
        top.setPadding(dp(20), dp(20), dp(20), dp(12))

        val title = TextView(this)
        title.text = kind.title
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.setShadowLayer(dpf(4f), 0f, dpf(1f), Color.argb(140, 0, 0, 0))
        top.addView(title, LinearLayout.LayoutParams(WRAP, WRAP))

        val hint = TextView(this)
        hint.text = "Choose how it looks"
        hint.setTextColor(Color.argb(220, 255, 255, 255))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        hint.setShadowLayer(dpf(4f), 0f, dpf(1f), Color.argb(140, 0, 0, 0))
        top.addView(hint, LinearLayout.LayoutParams(WRAP, WRAP))

        val middle = FrameLayout(this)
        previewHolder = FrameLayout(this)
        previewHolder.clipToOutline = true
        previewHolder.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpf(28f))
            }
        }
        val size = previewSize()
        middle.addView(previewHolder, FrameLayout.LayoutParams(size.first, size.second, Gravity.CENTER))
        top.addView(middle, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(top, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Bottom: the sheet with the choices and the two buttons.
        sheet = LinearLayout(this)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = GradientDrawable().apply {
            setColor(Color.argb(240, 28, 30, 38))
            val r = dpf(28f)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        sheet.setPadding(dp(20), dp(8), dp(20), dp(16))

        optionsBox = LinearLayout(this)
        optionsBox.orientation = LinearLayout.VERTICAL
        val scroll = MaxHeightScroll(this, (resources.displayMetrics.heightPixels * 0.56f).toInt())
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(optionsBox, FrameLayout.LayoutParams(MATCH, WRAP))
        sheet.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))
        buildOptions()

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.CENTER_VERTICAL
        val cancel = pill("Cancel", primary = false)
        cancel.setOnClickListener { finish() }
        val done = pill("Done", primary = true)
        done.setOnClickListener { save() }
        val gap = LinearLayout.LayoutParams(0, WRAP, 1f)
        gap.rightMargin = dp(10)
        buttons.addView(cancel, gap)
        buttons.addView(done, LinearLayout.LayoutParams(0, WRAP, 1f))
        val buttonsParams = LinearLayout.LayoutParams(MATCH, WRAP)
        buttonsParams.topMargin = dp(14)
        sheet.addView(buttons, buttonsParams)
        root.addView(sheet, LinearLayout.LayoutParams(MATCH, WRAP))

        // Edge-to-edge: keep the title below the status bar and the buttons above the navigation bar.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = systemBars(insets)
            view.setPadding(0, bars.first, 0, 0)
            sheet.setPadding(dp(20), dp(8), dp(20), dp(16) + bars.second)
            insets
        }
        setContentView(root)
    }

    /** (top, bottom) of the system bars in pixels. */
    @Suppress("DEPRECATION")
    private fun systemBars(insets: WindowInsets): Pair<Int, Int> =
        Pair(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)

    /** The choices; rebuilt whenever one changes, because some rows only apply to some choices. */
    private fun buildOptions() {
        optionsBox.removeAllViews()
        if (kind.isClock) {
            addSection("Style")
            addRow(
                chipRow(listOf("Digital", "Analog"), config.style) { config = config.copy(style = it); changed() }
            )
        }

        addSection("Colour")
        addRow(swatchRow())

        if (kind.isClock && config.style == WidgetConfig.STYLE_DIGITAL) {
            addSection("Time format")
            addRow(
                chipRow(listOf("Phone", "12 hour", "24 hour"), config.hours) { config = config.copy(hours = it); changed() }
            )
        }

        if (kind.isClock) {
            addSection("Date")
            addRow(
                chipRow(listOf("Show", "Hide"), if (config.showDate) 0 else 1) {
                    config = config.copy(showDate = it == 0)
                    changed()
                }
            )
        }

        addSection("Background")
        addRow(
            chipRow(listOf("None", "Glass", "Dark"), config.background) { config = config.copy(background = it); changed() }
        )
    }

    private fun addSection(text: String) {
        val label = TextView(this)
        label.text = text
        label.setTextColor(Color.argb(170, 255, 255, 255))
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = dp(14)
        lp.bottomMargin = dp(8)
        optionsBox.addView(label, lp)
    }

    private fun addRow(row: View) {
        optionsBox.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** A row of equal pills, one of them selected. */
    private fun chipRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for ((index, text) in labels.withIndex()) {
            val on = index == selected
            val chip = TextView(this)
            chip.text = text
            chip.gravity = Gravity.CENTER
            chip.maxLines = 1
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            chip.setTextColor(if (on) Color.BLACK else Color.WHITE)
            chip.setPadding(dp(8), dp(11), dp(8), dp(11))
            chip.background = roundRect(if (on) Color.WHITE else Color.argb(46, 255, 255, 255), dpf(22f))
            chip.isClickable = true
            chip.contentDescription = text
            chip.setOnClickListener { onPick(index) }
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (index < labels.size - 1) lp.rightMargin = dp(8)
            row.addView(chip, lp)
        }
        return row
    }

    /** The colour choices as round swatches; the chosen one gets a ring. */
    private fun swatchRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for ((index, color) in WidgetConfig.COLORS.withIndex()) {
            val swatch = View(this)
            swatch.background = swatchDrawable(color, index == config.color)
            swatch.isClickable = true
            swatch.contentDescription = WidgetConfig.COLOR_NAMES[index]
            swatch.setOnClickListener {
                config = config.copy(color = index)
                changed()
            }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
            lp.leftMargin = dp(3)
            lp.rightMargin = dp(3)
            row.addView(swatch, lp)
        }
        return row
    }

    private fun swatchDrawable(color: Int, selected: Boolean) =
        if (!selected) {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(1), Color.argb(110, 255, 255, 255))
            }
        } else {
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), Color.WHITE)
            }
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            LayerDrawable(arrayOf(ring, dot)).apply { setLayerInset(1, dp(6), dp(6), dp(6), dp(6)) }
        }

    private fun pill(text: String, primary: Boolean): TextView {
        val view = TextView(this)
        view.text = text
        view.gravity = Gravity.CENTER
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        view.setTextColor(if (primary) Color.BLACK else Color.WHITE)
        view.setPadding(dp(12), dp(13), dp(12), dp(13))
        view.background = roundRect(if (primary) Color.WHITE else Color.argb(46, 255, 255, 255), dpf(26f))
        view.isClickable = true
        return view
    }

    // ------------------------------------------------------------------ preview + result

    /** A choice changed: some rows only apply to some choices (time format needs a digital clock), and the swatch ring moves. */
    private fun changed() {
        buildOptions()
        render()
    }

    /** The real widget (the same views the launcher shows) at the size it has on the home screen. */
    private fun render() {
        previewHolder.removeAllViews()
        val views = BuiltInWidgets.build(this, kind, config)
        val view = try {
            views.apply(this, previewHolder)
        } catch (e: Exception) {
            null
        }
        if (view != null) previewHolder.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /** Pixel size of the preview: what the widget measures right now on the home screen, kept inside the screen. */
    private fun previewSize(): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        var wDp = 0
        var hDp = 0
        try {
            val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
            wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        } catch (e: Exception) {
            // use the default size below
        }
        if (wDp <= 0 || hDp <= 0) {
            wDp = if (kind == BuiltInKind.CLOCK) 250 else 110
            hDp = if (kind == BuiltInKind.CLOCK) 110 else 110
        }
        var w = wDp * density
        var h = hDp * density
        val maxW = resources.displayMetrics.widthPixels - dp(40).toFloat()
        val maxH = resources.displayMetrics.heightPixels * 0.30f
        val shrink = min(1f, min(maxW / w, maxH / h))
        w *= shrink
        h *= shrink
        // Never smaller than something you can see.
        return Pair(w.toInt().coerceAtLeast(dp(110)), h.toInt().coerceAtLeast(dp(80)))
    }

    private fun save() {
        WidgetConfigStore.save(this, widgetId, config)
        BuiltInWidgets.update(this, widgetId, kind, config)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /** A scroll view that never gets taller than [maxPx], so the preview above it keeps its room on small phones. */
    private class MaxHeightScroll(context: Context, private val maxPx: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val capped = if (size > 0) min(size, maxPx) else maxPx
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST))
        }
    }
}
