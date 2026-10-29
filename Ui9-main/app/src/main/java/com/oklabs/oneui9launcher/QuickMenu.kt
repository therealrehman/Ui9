package com.oklabs.oneui9launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.TextUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** The long-press card: app shortcuts first, then App info / Uninstall / Remove. Rounded, dark, floats over everything. */
class QuickMenu(
    context: Context,
    entries: List<MenuEntry>,
    private val onPick: (MenuEntry) -> Unit
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        background = roundRect(Look.panel(246), context.dpf(22f))
        elevation = context.dpf(14f)
        setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        minimumWidth = context.dp(210)
        for (entry in entries) addView(row(entry), LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun row(entry: MenuEntry): View {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = context.dp(46)
        row.setPadding(context.dp(14), 0, context.dp(18), 0)
        row.isClickable = true
        row.background = RippleDrawable(
            ColorStateList.valueOf(Look.inkA(46)),
            null,
            roundRect(Color.WHITE, context.dpf(16f))
        )
        row.setOnClickListener { onPick(entry) }

        val icon = entry.icon
        if (icon != null) {
            val image = ImageView(context)
            image.setImageBitmap(icon)
            val lp = LinearLayout.LayoutParams(context.dp(24), context.dp(24))
            lp.rightMargin = context.dp(12)
            row.addView(image, lp)
        }

        val label = TextView(context)
        label.text = entry.label
        label.setTextColor(Look.text)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        row.addView(label, LinearLayout.LayoutParams(WRAP, WRAP))
        return row
    }
}
