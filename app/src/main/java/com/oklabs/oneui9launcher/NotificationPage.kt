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

    /**
     * One column with everything. A swipe up (or a tap on an empty spot) closes the page.
     *
     * Step 59: why the swipe up from an empty space did not work: the list (a ScrollView) fills all the empty space, takes the touch, and
     * after a few dp tells its parent "do not intercept any more". The old code looked at the swipe in onInterceptTouchEvent only, and needed
     * 84 dp, so it never saw it. Now every touch is read in dispatchTouchEvent BEFORE any child (like the Quick panel does), and the page
     * follows the finger: after ~18 dp it moves with it, and the distance (80 dp) or the speed of a flick decides when the finger lifts.
     */
    private inner class Column(context: Context) : LinearLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val density = context.resources.displayMetrics.density
        private var downX = 0f
        private var downY = 0f
        private var downRawY = 0f
        private var taken = false // this touch belongs to the page now (a swipe up or a swipe right)
        private var dragging = false
        private var listBlocks = false // the finger came down on a list that can still scroll down: that swipe scrolls it
        private val tracker = FlingTracker()

        private fun closeDy(rawY: Float): Float = if (sheetMode) rawY - downRawY else downRawY - rawY

        private fun swipedRight(x: Float, y: Float): Boolean {
            val dx = x - downX
            return dx > context.dp(72) && dx > abs(y - downY) * 2f // Step 58: a real swipe, not a light touch
        }

        /** Reads one touch event. True = the page took the touch (children must not see it). */
        private fun read(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!closing && column.translationY != 0f) {
                        column.animate().cancel() // a new touch while the page was still gliding back
                        resetDragLook()
                    }
                    downX = ev.x
                    downY = ev.y
                    downRawY = ev.rawY
                    taken = false
                    dragging = false
                    listBlocks = (if (sheetMode) notifs?.blocksSwipeDownAt(downX, downY) else notifs?.blocksSwipeUpAt(downX, downY)) ?: false
                    tracker.clear()
                    tracker.add(ev.eventTime, trackY(ev.rawY))
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker.add(ev.eventTime, trackY(ev.rawY))
                    if (dragging) {
                        dragTo(closeDy(ev.rawY))
                        return true
                    }
                    if (taken) return true
                    val dyUp = closeDy(ev.rawY)
                    if (!listBlocks && PanelSwipe.startsDrag(ev.x - downX, dyUp, density)) {
                        taken = true
                        dragging = true
                        dragBase = dyUp
                        column.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        dragTo(dyUp)
                        return true
                    }
                    if (swipedRight(ev.x, ev.y) && !(notifs?.cardsAt(downX, downY) ?: false)) {
                        taken = true
                        onSwitchPage?.invoke()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    tracker.add(ev.eventTime, trackY(ev.rawY))
                    val dyUp = closeDy(ev.rawY)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && PanelSwipe.shouldClose(dyUp, tracker.velocity(), density)) close(true, fromDrag = true) else springBack()
                    return true
                }
            }
            return false
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (closing) return true // on its way out: a touch must not hit a card under the finger
            val was = taken
            if (read(ev)) {
                if (!was) {
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel) // the card / list that had the touch lets go of it
                    cancel.recycle()
                }
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

        // A touch that starts on an empty spot ends up here (no child took it): a tap closes the page.
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_UP -> if (abs(ev.x - downX) <= slop && abs(ev.y - downY) <= slop) close(true)
            }
            return true
        }
    }

    private var dragBase = 0f

    /** Step 65: opened from the strip (the host sets this) the page is a bottom sheet: it slides UP from the bottom and closes with a swipe DOWN. On the Home screen it stays the full page from the top. */
    var sheetAllowed = false
    private var sheetMode = false
    private var insetTop = 0
    private var insetBottom = 0
    private fun sgn(): Float = if (sheetMode) 1f else -1f
    private fun trackY(rawY: Float): Float = if (sheetMode) -rawY else rawY
    private fun scrimA(a: Int): Int = if (sheetMode) 0 else a // the space above the sheet is transparent

    private fun applyStyle() {
        sheetMode = sheetAllowed && context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(PREF_SHEET, true)
        val lp = column.layoutParams as LayoutParams
        if (sheetMode) {
            lp.height = (context.resources.displayMetrics.heightPixels * 0.78f).toInt()
            lp.gravity = Gravity.BOTTOM
            column.background = PanelTheme.scrim(240, PanelLook.dark)
            val rad = context.dpf(28f)
            column.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, (view.height + rad).toInt(), rad)
                }
            }
            column.clipToOutline = true
        } else {
            lp.height = MATCH
            lp.gravity = Gravity.TOP
            column.background = null
            column.clipToOutline = false
        }
        column.layoutParams = lp
        setInsets(insetTop, insetBottom)
    }

    private fun dragTravel(): Float = column.height * 0.35f

    /** Step 59: the page and its veil follow the finger [dy] pixels up ([dragBase] of it was used up before the drag started). */
    private fun dragTo(dy: Float) {
        val d = (dy - dragBase).coerceAtLeast(0f)
        val q = (d / dragTravel()).coerceIn(0f, 1f)
        column.translationY = sgn() * d
        column.alpha = Motion.fadeOut(q)
        veil.alpha = Motion.fadeOut(q)
        veil.translationY = column.translationY
    }

    private fun springBack() {
        column.animate().cancel()
        column.animate().translationY(0f).setDuration(PanelSwipe.BACK_MS).setInterpolator(PanelSwipe.GLIDE)
            .setUpdateListener {
                val q = (abs(column.translationY) / dragTravel()).coerceIn(0f, 1f)
                column.alpha = Motion.fadeOut(q)
                veil.alpha = Motion.fadeOut(q)
                veil.translationY = column.translationY
            }
            .withEndAction { resetDragLook() }
            .start()
    }

    private fun resetDragLook() {
        column.animate().setUpdateListener(null)
        column.setLayerType(View.LAYER_TYPE_NONE, null)
        Motion.setBlur(column, 0f)
        column.alpha = 1f
        column.translationY = 0f
        veil.alpha = 1f
        veil.translationY = 0f
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
        insetTop = top
        insetBottom = bottom
        column.setPadding(
            context.dp(22),
            (if (sheetMode) 0 else top) + context.dp(16),
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
        applyStyle()
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
        veil.background = PanelTheme.scrim(scrimA(if (context.windowBlurWorks()) 140 else 195), PanelLook.dark)
        // Step 31: veil and list slide a little and fade 0 -> 100 on the same smooth curve
        val h = context.resources.displayMetrics.heightPixels.toFloat()
        veil.alpha = 0f
        veil.translationY = sgn() * h * 0.12f
        veil.translationX = 0f
        column.alpha = 0f
        column.translationY = sgn() * (if (sheetMode) h * 0.25f else context.dp(56).toFloat())
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

    fun close(animated: Boolean, fromDrag: Boolean = false) {
        if (!isOpen || closing) return
        column.animate().setUpdateListener(null).cancel()
        veil.animate().cancel()
        if (watching) {
            watching = false
            Notifs.removeListener(listener)
        }
        if (!animated) {
            resetDragLook() // also puts back what a swipe in progress had moved
            visibility = View.GONE
            onOpenChanged?.invoke()
            onGone?.invoke()
            return
        }
        closing = true
        if (fromDrag) {
            // Step 59: after a swipe up the page is already part-way and fading: it carries on from there and glides the rest in about 0.2 s.
            val travel = sgn() * dragTravel()
            val target = if (sheetMode) maxOf(column.translationY, travel) else minOf(column.translationY, travel)
            val ms = PanelSwipe.closeMs((column.translationY / travel).coerceIn(0f, 1f))
            veil.animate().cancel()
            column.animate().translationY(target).setDuration(ms).setInterpolator(PanelSwipe.GLIDE)
                .setUpdateListener {
                    val q = (column.translationY / travel).coerceIn(0f, 1f)
                    column.alpha = Motion.fadeOut(q)
                    veil.alpha = Motion.fadeOut(q)
                    veil.translationY = column.translationY
                }
                .withEndAction { finishClose() }.start()
            postDelayed({ finishClose() }, ms + 250L) // safety: never stay on screen, invisible, taking touches
            post { onOpenChanged?.invoke() }
            return
        }
        // Step 35: a tap / Back: straight from 100% to 20% the moment the close starts, then down to 0 fast; no blur while closing.
        veil.alpha = Motion.CLOSE_START_ALPHA
        column.alpha = Motion.CLOSE_START_ALPHA
        veil.animate().alpha(0f).translationY(sgn() * context.resources.displayMetrics.heightPixels * 0.12f).setDuration(Motion.CLOSE_MS)
            .setInterpolator(Motion.CLOSE).start()
        val travel = sgn() * (if (sheetMode) context.resources.displayMetrics.heightPixels * 0.25f else context.dp(56).toFloat())
        column.animate().translationY(travel).setDuration(Motion.CLOSE_MS).setInterpolator(Motion.CLOSE)
            .setUpdateListener {
                val q = (column.translationY / travel).coerceIn(0f, 1f)
                column.alpha = Motion.fadeOut(q)
            }
            .withEndAction { finishClose() }.start()
        postDelayed({ finishClose() }, Motion.CLOSE_MS + 250L)
        // The blur fades while the page slides away.
        post { onOpenChanged?.invoke() }
    }

    /** The end of the closing slide (also run by a safety timer): everything the slide changed is put back and the page is gone. */
    private fun finishClose() {
        if (!closing) return
        column.animate().cancel()
        column.animate().setUpdateListener(null)
        column.setLayerType(View.LAYER_TYPE_NONE, null)
        Motion.setBlur(column, 0f)
        column.alpha = 1f
        column.translationY = 0f
        veil.alpha = 1f
        veil.translationY = 0f
        closing = false
        visibility = View.GONE
        onGone?.invoke()
    }

    companion object {
        /** Settings > Quick panel > Notification panel: true (default) = this page, false = the phone's own notification panel. */
        const val PREF_KEY = "np_own"
    }
}
