package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One app icon with an optional label underneath and the One UI "squish" when pressed.
 * Used by the dock and by the Apps screen grid (views get recycled, so use [bind] to change the app).
 * Step 9b: with a [badgePackage] the icon shows a small red dot or number for that app's notifications.
 */
class AppIconView(
    context: Context,
    icon: Bitmap,
    label: String?,
    iconSizeDp: Int,
    labelColor: Int = Color.WHITE
) : LinearLayout(context) {

    private val iconView = ImageView(context)
    private var labelView: TextView? = null

    /** The app whose notifications this icon counts (Step 9b). Null = no badge. */
    var badgePackage: String? = null
        set(value) {
            field = value
            refreshBadge()
        }

    private var badgeCount = 0
    private val badgeListener: () -> Unit = { refreshBadge() }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeRect = RectF()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        isFocusable = true

        val iconPx = context.dp(iconSizeDp)
        iconView.setImageBitmap(icon)
        addView(iconView, LinearLayout.LayoutParams(iconPx, iconPx))

        if (label != null) {
            val text = TextView(context).apply {
                this.text = label
                setTextColor(labelColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(context.dpf(2f), 0f, context.dpf(1f), Color.argb(100, 0, 0, 0))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = context.dp(4)
            addView(text, lp)
            labelView = text
        }
    }

    private fun refreshBadge() {
        val pkg = badgePackage
        val count = if (pkg == null) 0 else Badges.count(pkg)
        if (count != badgeCount) {
            badgeCount = count
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Badges.addListener(badgeListener)
        refreshBadge()
    }

    override fun onDetachedFromWindow() {
        Badges.removeListener(badgeListener)
        super.onDetachedFromWindow()
    }

    /** The badge sits on the top-right corner of the icon: a plain dot, or a red pill with the number. */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (badgeCount <= 0 || iconView.width <= 0) return
        val density = resources.displayMetrics.density
        val red = Color.rgb(255, 59, 48)
        if (HomeStore.badgeMode == HomeStore.BADGE_NUMBER) {
            val text = if (badgeCount > 99) "99+" else badgeCount.toString()
            badgePaint.textSize = 11f * density
            badgePaint.typeface = Typeface.DEFAULT_BOLD
            val textWidth = badgePaint.measureText(text)
            val height = 18f * density
            val width = maxOf(height, textWidth + 10f * density)
            val right = iconView.right + 4f * density
            val top = iconView.top - 4f * density
            badgeRect.set(right - width, top, right, top + height)
            badgePaint.style = Paint.Style.FILL
            badgePaint.color = red
            canvas.drawRoundRect(badgeRect, height / 2f, height / 2f, badgePaint)
            badgePaint.color = Color.WHITE
            badgePaint.textAlign = Paint.Align.CENTER
            val baseline = badgeRect.centerY() - (badgePaint.descent() + badgePaint.ascent()) / 2f
            canvas.drawText(text, badgeRect.centerX(), baseline, badgePaint)
        } else {
            val radius = 6f * density
            val cx = iconView.right - 2f * density
            val cy = iconView.top + 2f * density
            badgePaint.style = Paint.Style.FILL
            badgePaint.color = red
            canvas.drawCircle(cx, cy, radius, badgePaint)
            badgePaint.style = Paint.Style.STROKE
            badgePaint.strokeWidth = 1.5f * density
            badgePaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius, badgePaint)
        }
    }

    /** Shows a different app in this (recycled) view and resets any half-finished press animation. */
    fun bind(icon: Bitmap, label: String?) {
        iconView.setImageBitmap(icon)
        labelView?.text = label
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                animate().scaleX(0.88f).scaleY(0.88f).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(240)
                    .setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        return super.onTouchEvent(event)
    }
}
