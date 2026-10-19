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
 * Opacity 0 = no background at all, 100 = solid. [onChange] gets (colour without alpha, opacity 0..100) while you drag.
 */
class DockStyleSheet(
    context: Context,
    private val onChange: (Int, Int) -> Unit
) : LinearLayout(context) {

    private val label = TextView(context)
    private val seek = SeekBar(context)
    private val chips = ArrayList<Pair<Int, View>>()
    private var color = Color.WHITE
    private var opacity = DEFAULT_OPACITY

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
                refresh()
                onChange(color, opacity)
            }
            val lp = LinearLayout.LayoutParams(context.dp(34), context.dp(34))
            lp.setMargins(0, 0, context.dp(10), 0)
            row.addView(chip, lp)
            chips.add(Pair(c, chip))
        }
        val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rowLp.topMargin = context.dp(12)
        addView(row, rowLp)

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
                onChange(color, opacity)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refresh()
    }

    /** Shows the saved look. */
    fun show(newColor: Int, newOpacity: Int) {
        color = newColor or (0xFF shl 24)
        opacity = newOpacity.coerceIn(0, 100)
        seek.progress = opacity
        refresh()
    }

    private fun refresh() {
        label.text = "Opacity  $opacity%"
        for ((c, chip) in chips) {
            val dot = GradientDrawable()
            dot.shape = GradientDrawable.OVAL
            dot.setColor(c)
            val selected = c == color
            dot.setStroke(context.dp(if (selected) 3 else 1), if (selected) Look.text else Look.inkA(90))
            chip.background = dot
        }
    }

    companion object {
        /** The default look since Step 1: white at about 22%. */
        const val DEFAULT_OPACITY = 22
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
