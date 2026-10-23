package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Step 17a: the launcher's own Notifications page, like the reference phone: a full-screen page over the blurred wallpaper.
 * Time and date at the top left, then your notifications (swipe a card sideways to remove it, tap it to open the app),
 * or a centred "No notifications"; at the bottom **Notification settings** on the left and **Clear all** on the right.
 * A swipe down on the Home screen opens it (Settings > Quick panel > Notification panel). Tap an empty spot, swipe up,
 * Back or Home closes it.
 */
class NotificationPage(
    context: Context,
    private val onOpenSettings: () -> Unit
) : FrameLayout(context) {

    /** True from the moment the page starts opening until it is completely gone. */
    val isOpen: Boolean get() = visibility == View.VISIBLE

    /** `isOpen` stays true while the closing animation runs; the blur must not wait for it, so the activity asks this. */
    val wantsBlur: Boolean get() = isOpen && !closing

    /** Told when the page opens or closes (the activity blurs the wallpaper behind it). */
    var onOpenChanged: (() -> Unit)? = null

    /** Step 17c: a swipe to the right on the page asks for the Quick panel. */
    var onSwitchPage: (() -> Unit)? = null

    /** Step 22: told when the page is completely gone (system-wide overlay). */
    var onGone: (() -> Unit)? = null

    private val veil = View(context)
    private val column = Column(context)
    private var clearButton = TextView(context)
    private var notifs: QuickNotifs? = null
    private var watching = false
    private var closing = false
    private val listener: () -> Unit = {
        notifs?.bind()
        updateClear()
    }

    /** One column with everything. A swipe up (or a tap on an empty spot) closes the page. */
    private inner class Column(context: Context) : LinearLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f

        private fun swipedRight(x: Float, y: Float): Boolean {
            val dx = x - downX
            return dx > slop * 3f && dx > abs(y - downY) * 2f
        }

        private fun swipedUp(x: Float, y: Float): Boolean {
            val dy = downY - y
            return dy > slop * 2 && dy > abs(x - downX) * 1.5f
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    // A long list scrolls itself first; only a swipe up outside a scrolling list closes the page.
                    if (swipedUp(ev.x, ev.y) && !(notifs?.scrollsAt(downX, downY) ?: false)) {
                        close(true)
                        return true
                    }
                    if (swipedRight(ev.x, ev.y) && !(notifs?.cardsAt(downX, downY) ?: false)) {
                        onSwitchPage?.invoke()
                        return true
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (swipedUp(ev.x, ev.y)) close(true) else if (swipedRight(ev.x, ev.y)) onSwitchPage?.invoke()
                }
                MotionEvent.ACTION_UP -> if (abs(ev.x - downX) <= slop && abs(ev.y - downY) <= slop) close(true)
            }
            return true
        }
    }

    init {
        visibility = View.GONE

        veil.background = PanelTheme.scrim(135, PanelLook.dark)
        veil.isClickable = true
        veil.setOnClickListener { close(true) }
        addView(veil, LayoutParams(MATCH, MATCH))

        column.orientation = LinearLayout.VERTICAL
        addView(column, LayoutParams(MATCH, MATCH))
        setInsets(0, 0)
    }

    /** The status bar height (so the clock sits below it) and the navigation bar height (so the buttons sit above it). */
    fun setInsets(top: Int, bottom: Int) {
        column.setPadding(
            context.dp(22),
            top + context.dp(16),
            context.dp(22),
            max(bottom, context.dp(12)) + context.dp(14)
        )
    }

    // ------------------------------------------------------------------ building

    private fun build() {
        val c = context
        column.removeAllViews()

        // Time and date on one line, like the reference: the time big, the date small beside it.
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.BOTTOM
        val time = TextClock(c)
        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        time.setTextColor(PanelLook.text)
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        time.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        time.includeFontPadding = false
        head.addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
        val date = TextClock(c)
        val pattern = try {
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd").ifEmpty { "EEE, MMM d" }
        } catch (e: Exception) {
            "EEE, MMM d"
        }
        date.format12Hour = pattern
        date.format24Hour = pattern
        date.setTextColor(PanelLook.textA(210))
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        date.includeFontPadding = false
        val dateLp = LinearLayout.LayoutParams(WRAP, WRAP)
        dateLp.leftMargin = c.dp(10)
        dateLp.bottomMargin = c.dp(4)
        head.addView(date, dateLp)
        column.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        // The list (or "No notifications") fills the space between the header and the buttons.
        val n = QuickNotifs(c, true) { close(false) }
        n.bind()
        notifs = n
        val listLp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        listLp.topMargin = c.dp(14)
        column.addView(n, listLp)

        // Bottom: Notification settings (left), Clear all (right).
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(pill("Notification settings") {
            onOpenSettings()
            close(false)
        })
        bar.addView(View(c), LinearLayout.LayoutParams(0, c.dp(1), 1f))
        // A new button every time the page is built: the old one is still inside the old bar, and a view with a parent can not be added again
        // (that was the "specified child already has a parent" crash on the second opening).
        clearButton = TextView(c)
        clearButton.text = "Clear all"
        styleAsPill(clearButton)
        clearButton.setOnClickListener { Notifs.clearAll() }
        bar.addView(clearButton, LinearLayout.LayoutParams(WRAP, WRAP))
        val barLp = LinearLayout.LayoutParams(MATCH, WRAP)
        barLp.topMargin = c.dp(10)
        column.addView(bar, barLp)
        updateClear()
    }

    private fun pill(text: String, onClick: () -> Unit): TextView {
        val button = TextView(context)
        button.text = text
        styleAsPill(button)
        button.setOnClickListener { onClick() }
        return button
    }

    private fun styleAsPill(button: TextView) {
        button.setTextColor(PanelLook.text)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        button.background = roundRect(PanelLook.glass(115), context.dpf(22f))
        button.isClickable = true
    }

    /** "Clear all" only shows when there is something that can be cleared. */
    private fun updateClear() {
        val any = Badges.accessGranted(context) && Notifs.current().any { it.clearable }
        clearButton.visibility = if (any) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------ open / close

    fun open() {
        if (isOpen) return
        column.animate().setUpdateListener(null).cancel()
        veil.animate().cancel()
        Motion.setBlur(column, 0f)
        closing = false
        build()
        if (!watching) {
            watching = true
            Notifs.addListener(listener)
        }
        visibility = View.VISIBLE
        // Without a working window blur the wallpaper stays sharp, so the dark veil is stronger to hide it.
        PanelTheme.load(context)
        veil.background = PanelTheme.scrim(if (context.windowBlurWorks()) 140 else 195, PanelLook.dark)
        // Step 31: veil and list slide a little and fade 0 -> 100 on the same smooth curve
        val h = context.resources.displayMetrics.heightPixels.toFloat()
        veil.alpha = 0f
        veil.translationY = -h * 0.12f
        veil.translationX = 0f
        column.alpha = 0f
        column.translationY = -context.dp(56).toFloat()
        Motion.setBlur(column, Motion.BLUR_PX)
        veil.animate().alpha(1f).translationY(0f).setDuration(Motion.OPEN_MS).setInterpolator(Motion.OPEN).start()
        // Step 33: the page fades 0 -> 100 while it sharpens from a blur (Step 35: faster)
        column.animate().alpha(1f).translationY(0f).setDuration(Motion.OPEN_MS).setInterpolator(Motion.OPEN)
            .setUpdateListener { Motion.setBlur(column, Motion.BLUR_PX * (1f - Motion.ramp(column.alpha, 0.9f))) }
            .withEndAction {
                column.animate().setUpdateListener(null)
                Motion.setBlur(column, 0f)
            }
            .start()
        onOpenChanged?.invoke()
    }

    fun close(animated: Boolean) {
        if (!isOpen || closing) return
        column.animate().setUpdateListener(null).cancel()
        veil.animate().cancel()
        if (watching) {
            watching = false
            Notifs.removeListener(listener)
        }
        if (!animated) {
            Motion.setBlur(column, 0f)
            visibility = View.GONE
            onOpenChanged?.invoke()
            onGone?.invoke()
            return
        }
        closing = true
        // Step 35: straight from 100% to 20% the moment the swipe up is accepted, then down to 0 fast; no blur while closing.
        veil.alpha = Motion.CLOSE_START_ALPHA
        column.alpha = Motion.CLOSE_START_ALPHA
        veil.animate().alpha(0f).translationY(-context.resources.displayMetrics.heightPixels * 0.12f).setDuration(Motion.CLOSE_MS)
            .setInterpolator(Motion.CLOSE).start()
        val travel = -context.dp(56).toFloat()
        column.animate().translationY(travel).setDuration(Motion.CLOSE_MS).setInterpolator(Motion.CLOSE)
            .setUpdateListener {
                val q = (column.translationY / travel).coerceIn(0f, 1f)
                column.alpha = Motion.fadeOut(q)
            }
            .withEndAction {
                column.animate().setUpdateListener(null)
                Motion.setBlur(column, 0f)
                column.alpha = 1f
                closing = false
                veil.translationY = 0f
                veil.alpha = 1f
                visibility = View.GONE
                onGone?.invoke()
            }.start()
        // The blur fades while the page slides away.
        post { onOpenChanged?.invoke() }
    }

    companion object {
        /** Settings > Quick panel > Notification panel: true (default) = this page, false = the phone's own notification panel. */
        const val PREF_KEY = "np_own"
    }
}
