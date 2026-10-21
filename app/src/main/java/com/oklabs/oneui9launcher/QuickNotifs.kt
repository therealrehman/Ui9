package com.oklabs.oneui9launcher

import android.app.PendingIntent
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * Step 16c: the notification list inside the Quick panel (under the brightness slider).
 * A "Notifications" heading with **Clear all**, then one card per notification (newest first): app icon, app name and age,
 * title, text. Swipe a card sideways to remove it, tap it to open the app (the panel closes). The list scrolls when it is long.
 * Without notification access it shows one card that opens the phone's access screen.
 * [onLeave] is called when the panel should close (after a tap that opened an app or the access screen).
 */
class QuickNotifs(
    context: Context,
    /** Step 17a: true on the full-screen Notifications page (no heading, the list fills the space, a big centred "No notifications"). */
    private val fullPage: Boolean = false,
    /** Step 20: in the Quick panel's lower area the list takes all the space that is left (down to the bottom of the screen). */
    private val fillSpace: Boolean = false,
    private val onLeave: () -> Unit
) : LinearLayout(context) {

    private val clearAll = TextView(context)
    private val list = LinearLayout(context)
    private val scroller: ScrollView =
        if (fullPage) ScrollView(context) else CappedScroll(context, (context.resources.displayMetrics.heightPixels * 0.34f).toInt())
    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, Drawable?>()

    init {
        orientation = VERTICAL

        val head = LinearLayout(context)
        head.orientation = HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(context)
        title.text = "Notifications"
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        head.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        clearAll.text = "Clear all"
        clearAll.setTextColor(Look.text)
        clearAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        clearAll.setPadding(context.dp(14), context.dp(7), context.dp(14), context.dp(7))
        clearAll.background = roundRect(Look.glass(110), context.dpf(18f))
        clearAll.isClickable = true
        clearAll.setOnClickListener { Notifs.clearAll() }
        head.addView(clearAll, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (fullPage) head.visibility = GONE // the page has its own header and its own Clear all
        addView(head, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list.orientation = VERTICAL
        scroller.isVerticalScrollBarEnabled = false
        scroller.overScrollMode = OVER_SCROLL_NEVER
        scroller.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (fullPage) {
            scroller.isFillViewport = true
            addView(scroller, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val sp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            sp.topMargin = context.dp(8)
            if (fillSpace) {
                sp.weight = 1f
                scroller.isFillViewport = true
            }
            addView(scroller, sp)
        }
    }

    /** Step 20: same as [scrollsAt], for a touch given in screen coordinates (the panel's lower area sits deeper in the layout). */
    fun scrollsAtRaw(rawX: Float, rawY: Float): Boolean {
        val r = Rect()
        if (!scroller.getGlobalVisibleRect(r)) return false
        return r.contains(rawX.toInt(), rawY.toInt()) && (scroller.canScrollVertically(1) || scroller.canScrollVertically(-1))
    }

    /** Step 20: same as [cardsAt], in screen coordinates. */
    fun cardsAtRaw(rawX: Float, rawY: Float): Boolean {
        if (Notifs.current().isEmpty() || !Badges.accessGranted(context)) return false
        val r = Rect()
        if (!scroller.getGlobalVisibleRect(r)) return false
        return r.contains(rawX.toInt(), rawY.toInt())
    }

    /** True when a vertical touch at ([x], [y]) (in this view's parent's coordinates) is on a list that can scroll: the panel must not take it as "swipe up to close". */
    fun scrollsAt(x: Float, y: Float): Boolean {
        val r = Rect()
        scroller.getHitRect(r)
        r.offset(left, top)
        return r.contains(x.toInt(), y.toInt()) && (scroller.canScrollVertically(1) || scroller.canScrollVertically(-1))
    }

    /** True when ([x], [y]) is on the list while it holds cards: a sideways swipe there removes a card, so it must not switch panels. */
    fun cardsAt(x: Float, y: Float): Boolean {
        if (Notifs.current().isEmpty() || !Badges.accessGranted(context)) return false
        val r = Rect()
        scroller.getHitRect(r)
        r.offset(left, top)
        return r.contains(x.toInt(), y.toInt())
    }

    /** Draws the list again from [Notifs] (and the access state). */
    fun bind() {
        list.removeAllViews()
        list.gravity = Gravity.TOP
        val c = context
        if (!Badges.accessGranted(c)) {
            clearAll.visibility = GONE
            if (fullPage) list.gravity = Gravity.CENTER
            list.addView(
                note("Allow notification access to see your notifications here.", "Allow") {
                    Badges.openAccessSettings(c)
                    onLeave()
                }
            )
            return
        }
        val items = Notifs.current()
        clearAll.visibility = if (items.any { it.clearable }) VISIBLE else GONE
        if (items.isEmpty()) {
            if (fullPage) list.gravity = Gravity.CENTER
            list.addView(note("No notifications", null) {})
            return
        }
        val now = System.currentTimeMillis()
        for (item in items) {
            val row = CardView(c, item, now)
            val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = c.dp(8)
            list.addView(row, lp)
        }
    }

    private fun note(text: String, action: String?, onAction: () -> Unit): View {
        if (fullPage) return pageNote(text, action, onAction)
        val c = context
        val row = LinearLayout(c)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(c.dp(16), c.dp(14), c.dp(16), c.dp(14))
        row.background = roundRect(Look.glass(115), c.dpf(22f))
        val label = TextView(c)
        label.text = text
        label.setTextColor(Look.textA(200))
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        row.addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) {
            val button = TextView(c)
            button.text = action
            button.setTextColor(if (Look.dark) android.graphics.Color.rgb(24, 26, 32) else android.graphics.Color.WHITE)
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            button.setPadding(c.dp(14), c.dp(7), c.dp(14), c.dp(7))
            button.background = roundRect(Look.text, c.dpf(18f))
            row.addView(button, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            row.isClickable = true
            row.setOnClickListener { onAction() }
        }
        return row
    }

    /** The full-page version of [note]: plain big centred text (and a button), no card. */
    private fun pageNote(text: String, action: String?, onAction: () -> Unit): View {
        val c = context
        val box = LinearLayout(c)
        box.orientation = VERTICAL
        box.gravity = Gravity.CENTER
        val label = TextView(c)
        label.text = text
        label.setTextColor(Look.textA(215))
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        label.gravity = Gravity.CENTER
        box.addView(label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (action != null) {
            val button = TextView(c)
            button.text = action
            button.setTextColor(if (Look.dark) android.graphics.Color.rgb(24, 26, 32) else android.graphics.Color.WHITE)
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            button.setPadding(c.dp(20), c.dp(10), c.dp(20), c.dp(10))
            button.background = roundRect(Look.text, c.dpf(22f))
            button.isClickable = true
            button.setOnClickListener { onAction() }
            val bp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            bp.topMargin = c.dp(14)
            box.addView(button, bp)
        }
        return box
    }

    private fun labelOf(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun iconOf(pkg: String): Drawable? = icons.getOrPut(pkg) {
        try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
    }

    /** What a tap does: the app's own intent, or else just open the app. Then the card goes if the app asked for that. */
    private fun open(item: NotifItem) {
        var started = false
        val intent: PendingIntent? = item.intent
        if (intent != null) {
            try {
                intent.send()
                started = true
            } catch (e: Exception) {
                // cancelled or refused: fall back to opening the app
            }
        }
        if (!started) {
            try {
                context.packageManager.getLaunchIntentForPackage(item.pkg)?.let {
                    it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            } catch (e: Exception) {
                // nothing to open
            }
        }
        if (item.autoCancel && item.clearable) Notifs.dismiss(item.key)
        onLeave()
    }

    /** One card. Sideways swipe = remove (when the app allows it), tap = open. */
    private inner class CardView(context: Context, private val item: NotifItem, now: Long) : LinearLayout(context) {

        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var swiping = false
        private var tracker: VelocityTracker? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), context.dp(11), context.dp(14), context.dp(11))
            background = roundRect(Look.glass(115), context.dpf(22f))
            isClickable = true

            val icon = ImageView(context)
            icon.setImageDrawable(iconOf(item.pkg))
            addView(icon, LayoutParams(context.dp(32), context.dp(32)).also { it.gravity = Gravity.TOP })

            val texts = LinearLayout(context)
            texts.orientation = VERTICAL
            val meta = TextView(context)
            meta.text = labelOf(item.pkg) + " · " + NotifLogic.ago(now, item.whenMs)
            meta.setTextColor(Look.textA(160))
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            meta.maxLines = 1
            meta.ellipsize = TextUtils.TruncateAt.END
            texts.addView(meta, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (item.title.isNotEmpty()) {
                val title = TextView(context)
                title.text = item.title
                title.setTextColor(Look.text)
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                title.maxLines = 1
                title.ellipsize = TextUtils.TruncateAt.END
                texts.addView(title, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            if (item.text.isNotEmpty()) {
                val body = TextView(context)
                body.text = item.text
                body.setTextColor(Look.textA(205))
                body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                body.maxLines = 2
                body.ellipsize = TextUtils.TruncateAt.END
                texts.addView(body, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val tp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.leftMargin = context.dp(12)
            addView(texts, tp)
            setOnClickListener { open(item) }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    swiping = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    tracker?.addMovement(ev)
                    animate().cancel()
                    return super.onTouchEvent(ev)
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(ev)
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!swiping && abs(dx) > slop && abs(dx) > abs(dy) * 1.3f) {
                        swiping = true
                        isPressed = false
                        parent?.requestDisallowInterceptTouchEvent(true) // the list and the panel must not take over
                    }
                    if (swiping) {
                        val pull = if (item.clearable) dx else dx * 0.25f // a card that cannot be removed only wobbles
                        translationX = pull
                        alpha = (1f - abs(pull) / (width * 1.1f)).coerceIn(0.2f, 1f)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(ev)
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    if (swiping) {
                        swiping = false
                        val dx = translationX
                        if (ev.actionMasked == MotionEvent.ACTION_UP && item.clearable && NotifLogic.shouldDismiss(dx, width.toFloat(), vx)) {
                            val away = if (dx > 0) width.toFloat() else -width.toFloat()
                            animate().translationX(away).alpha(0f).setDuration(140).withEndAction { Notifs.dismiss(item.key) }.start()
                        } else {
                            animate().translationX(0f).alpha(1f).setDuration(160).start()
                        }
                        return true
                    }
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    /** A scroll view that never grows taller than [maxPx]. */
    private class CappedScroll(context: Context, private val maxPx: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Step 20: when the parent gives an exact height (the list fills the space), keep it.
            val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) heightMeasureSpec else MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, h)
        }

        // Inside the panel's own scrolling: a long list scrolls itself first.
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && (canScrollVertically(1) || canScrollVertically(-1))) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.onTouchEvent(ev)
        }
    }
}
