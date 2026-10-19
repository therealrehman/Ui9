package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "Show search on Home screen" bar: a soft glass pill with a magnifier and the word "Search".
 * Tap it and the Apps screen opens with the keyboard up, ready to type. Looks like the pill on the Apps screen.
 */
class HomeSearchBar(context: Context) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = "Search"
        background = roundRect(Color.argb(64, 255, 255, 255), context.dpf(28f))
        setPadding(context.dp(18), 0, context.dp(18), 0)

        val glyph = GlyphView(context, GlyphView.SEARCH)
        addView(glyph, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))

        val hint = TextView(context)
        hint.text = "Search"
        hint.setTextColor(Color.argb(200, 255, 255, 255))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        hint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = context.dp(10)
        addView(hint, lp)
    }

    /** Same "squish" as the icons. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(2f)).start()
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val HEIGHT_DP = 50
    }
}
