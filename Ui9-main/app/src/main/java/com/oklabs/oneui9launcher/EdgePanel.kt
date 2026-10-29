package com.oklabs.oneui9launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Step 9b: the Edge panel. A column of favourite apps that slides in from the right edge of the screen (opened from the
 * handle on the Home screen). Tap an app to start it; tap outside, Back or Home closes it. The apps are chosen in Home settings.
 */
class EdgePanel(
    context: Context,
    private val onLaunch: (View, Intent) -> Unit
) : FrameLayout(context) {

    /** True from the moment the panel starts opening until it is completely gone. */
    val isOpen: Boolean get() = visibility == View.VISIBLE

    private val scrim = View(context)
    private val card = LinearLayout(context)
    private val column = LinearLayout(context)
    private val scroll = CappedScroll(context, (context.resources.displayMetrics.heightPixels * 0.72f).toInt())
    private var closing = false

    init {
        visibility = View.GONE

        scrim.setBackgroundColor(Color.argb(110, 0, 0, 0))
        scrim.isClickable = true
        scrim.setOnClickListener { close(true) }
        addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))

        val r = context.dpf(28f)
        card.background = GradientDrawable().apply {
            setColor(Look.panel(244))
            cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r) // round on the left side only
        }
        card.orientation = LinearLayout.VERTICAL
        card.elevation = context.dpf(14f)
        card.isClickable = true // taps on the card must not close it
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(context.dp(8), context.dp(14), context.dp(8), context.dp(14))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(column, FrameLayout.LayoutParams(WRAP, WRAP))
        card.addView(scroll, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(card, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
    }

    /** Fills the column with [entries] (one app per row, with its label) and slides the panel in. */
    fun open(entries: List<AppEntry>) {
        card.animate().cancel()
        scrim.animate().cancel()
        closing = false
        column.removeAllViews()
        if (entries.isEmpty()) {
            val empty = TextView(context)
            empty.text = "No apps yet.\nAdd some in Home settings > Edge panel."
            empty.setTextColor(Look.textA(190))
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            empty.maxWidth = context.dp(210)
            empty.setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            column.addView(empty, LinearLayout.LayoutParams(WRAP, WRAP))
        } else {
            for (app in entries) {
                val icon = AppIconView(context, app.icon, app.label, ICON_DP, Look.text)
                icon.setPadding(context.dp(4), context.dp(6), context.dp(4), context.dp(6))
                icon.contentDescription = app.label
                icon.badgePackage = app.packageName
                icon.setOnClickListener { view ->
                    onLaunch(view, app.launchIntent)
                    close(false)
                }
                column.addView(icon, LinearLayout.LayoutParams(context.dp(84), WRAP))
            }
        }
        scroll.scrollTo(0, 0)
        visibility = View.VISIBLE
        scrim.alpha = 0f
        card.translationX = context.dp(PANEL_SHIFT_DP).toFloat()
        scrim.animate().alpha(1f).setDuration(200).start()
        card.animate().translationX(0f).setDuration(230).start()
    }

    fun close(animated: Boolean) {
        if (!isOpen || closing) return
        card.animate().cancel()
        scrim.animate().cancel()
        if (!animated) {
            visibility = View.GONE
            return
        }
        closing = true
        scrim.animate().alpha(0f).setDuration(170).start()
        card.animate().translationX(context.dp(PANEL_SHIFT_DP).toFloat()).setDuration(190)
            .withEndAction {
                closing = false
                visibility = View.GONE
            }.start()
    }

    /** A scroll view that never gets taller than [maxPx]. */
    private class CappedScroll(context: Context, private val maxPx: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val capped = if (size > 0) min(size, maxPx) else maxPx
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST))
        }
    }

    private companion object {
        const val ICON_DP = 48

        /** How far the panel waits off-screen to the right (wider than the panel can ever be). */
        const val PANEL_SHIFT_DP = 260
    }
}
