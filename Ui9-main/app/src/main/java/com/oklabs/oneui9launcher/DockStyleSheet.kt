package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * The small card of the edit screen that styles the dock: pick a colour, drag the opacity.
 * Opacity 0 = no background at all, 100 = solid. Step 15: a row of gradients under the colours (None = the plain colour).
 * [onChange] gets (colour without alpha, opacity 0..100, gradient 0..N) while you drag or tap.
 */
class DockStyleSheet(
    context: Context,
    private val onChange: (Int, Int, Int) -> Unit
) : LinearLayout(context) {

    private val label = TextView(context)
    private val seek = SeekBar(context)
    private val chips = ArrayList<Pair<Int, View>>()
    private var color = Color.WHITE
    private var opacity = DEFAULT_OPACITY
    private var gradient = 0
    private val gradientChips = ArrayList<Pair<Int, View>>()

    init {
        orientation = VERTICAL
        isClickable = true // taps on the card must not reach the layer behind it
        background = roundRect(Look.panel(238), context.dpf(26f))
        setPadding(context.dp(18), context.dp(14), context.dp(18), context.dp(14))

        val title = TextView(context)
        title.text = "Dock background"
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        for (c in COLORS) {
            val chip = View(context)
            chip.isClickable = true
            chip.setOnClickListener {
                color = c
                gradient = 0
                refresh()
                onChange(color, opacity, gradient)
            }
            val lp = LinearLayout.LayoutParams(context.dp(34), context.dp(34))
            lp.setMargins(0, 0, context.dp(10), 0)
            row.addView(chip, lp)
            chips.add(Pair(c, chip))
        }
        val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rowLp.topMargin = context.dp(12)
        addView(row, rowLp)

        val gradientTitle = TextView(context)
        gradientTitle.text = "Gradient"
        gradientTitle.setTextColor(Look.textA(200))
        gradientTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        val gradientTitleLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        gradientTitleLp.topMargin = context.dp(12)
        addView(gradientTitle, gradientTitleLp)

        val gradientRow = LinearLayout(context)
        gradientRow.orientation = HORIZONTAL
        gradientRow.gravity = Gravity.CENTER_VERTICAL
        for (index in 1..Gradients.all.size) {
            val chip = View(context)
            chip.isClickable = true
            chip.contentDescription = Gradients.all[index - 1].name
            chip.setOnClickListener {
                gradient = index
                if (opacity < GRADIENT_MIN_OPACITY) {
                    // a gradient at 22% is hard to see: lift it to a clear glass look (the slider follows)
                    opacity = GRADIENT_OPACITY
                    seek.progress = opacity
                }
                refresh()
                onChange(color, opacity, gradient)
            }
            val lp = LinearLayout.LayoutParams(context.dp(34), context.dp(34))
            lp.setMargins(0, 0, context.dp(10), 0)
            gradientRow.addView(chip, lp)
            gradientChips.add(Pair(index, chip))
        }
        val scroller = android.widget.HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(gradientRow)
        val scrollerLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scrollerLp.topMargin = context.dp(6)
        addView(scroller, scrollerLp)

        label.setTextColor(Look.textA(200))
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        val labelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        labelLp.topMargin = context.dp(12)
        addView(label, labelLp)

        seek.max = 100
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                opacity = progress
                refresh()
                onChange(color, opacity, gradient)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refresh()
    }

    /** Shows the saved look. */
    fun show(newColor: Int, newOpacity: Int, newGradient: Int = 0) {
        color = newColor or (0xFF shl 24)
        opacity = newOpacity.coerceIn(0, 100)
        gradient = if (newGradient in 1..Gradients.all.size) newGradient else 0
        seek.progress = opacity
        refresh()
    }

    private fun refresh() {
        label.text = "Opacity  $opacity%"
        for ((c, chip) in chips) {
            val dot = GradientDrawable()
            dot.shape = GradientDrawable.OVAL
            dot.setColor(c)
            val selected = c == color && gradient == 0
            dot.setStroke(context.dp(if (selected) 3 else 1), if (selected) Look.text else Look.inkA(90))
            chip.background = dot
        }
        for ((index, chip) in gradientChips) {
            val dot = GradientDrawable(GradientDrawable.Orientation.TL_BR, Gradients.all[index - 1].colors)
            dot.shape = GradientDrawable.OVAL
            val selected = index == gradient
            dot.setStroke(context.dp(if (selected) 3 else 1), if (selected) Look.text else Look.inkA(90))
            chip.background = dot
        }
    }

    companion object {
        /** The default look since Step 1: white at about 22%. */
        const val DEFAULT_OPACITY = 22

        /** Step 15: picking a gradient while the opacity is below [GRADIENT_MIN_OPACITY] sets it to [GRADIENT_OPACITY]. */
        const val GRADIENT_MIN_OPACITY = 40
        const val GRADIENT_OPACITY = 75
        val COLORS = intArrayOf(
            Color.WHITE,
            Color.BLACK,
            Color.rgb(59, 123, 255),
            Color.rgb(138, 92, 255),
            Color.rgb(47, 191, 113),
            Color.rgb(255, 111, 165)
        )
    }
}
