package com.oklabs.oneui9launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.location.LocationManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val HIDE_BRIGHT = "qp_hide_bright" // Step 38: parts of the panel taken off with the red minus
private const val HIDE_VOLUME = "qp_hide_volume"
private const val HIDE_MEDIA = "qp_hide_media"
private const val KEY_ROWS = "qp_rows" // Step 38: rows of round buttons on a page (1-4)

/**
 * Step 16a: a wide, rounded slider (brightness). A light bar grows from the left with the finger, the icon sits at the left end.
 * [progress] is 0..1; [onChange] gets the value while dragging and `final = true` when the finger lifts.
 */
class PillSlider(context: Context, glyphKind: Int) : FrameLayout(context) {

    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            layoutFill()
        }

    var onChange: ((Float, Boolean) -> Unit)? = null

    /** Called when the finger goes down (the panel can ask for a missing permission); true = go on with the drag. */
    var onTouchStart: (() -> Boolean)? = null

    var isDragging = false
        private set

    private val track = View(context)
    private val fill = View(context)
    private val glyph = QuickGlyph(context, glyphKind)
    private var accepted = false
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init {
        track.background = roundRect(PanelLook.glass(110), context.dpf(HEIGHT_DP / 2f))
        fill.background = roundRect(PanelLook.text, context.dpf(HEIGHT_DP / 2f))
        addView(track, LayoutParams(MATCH, MATCH))
        addView(fill, LayoutParams(0, MATCH))
        val g = LayoutParams(context.dp(28), context.dp(28), Gravity.START or Gravity.CENTER_VERTICAL)
        g.leftMargin = context.dp(14)
        addView(glyph, g)
        layoutFill()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutFill()
    }

    /**
     * Step 19: the bar is placed in [onLayout] (and whenever [progress] changes), not through layout parameters. A panel that is
     * built while it is still hidden is measured later, and the level used to stay invisible after the second opening.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutFill()
    }

    private fun layoutFill() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val fillWidth = (h + progress * (w - h)).toInt()
        fill.layout(0, 0, fillWidth, h)
        // The icon is dark on the light bar and light on the empty track.
        val over = fillWidth > context.dp(46)
        glyph.tint = if (over) (if (PanelLook.dark) Color.rgb(24, 26, 32) else Color.WHITE) else PanelLook.text
        invalidate()
    }

    private fun valueAt(x: Float): Float {
        val h = height.toFloat()
        val span = max(1f, width - h)
        return ((x - h / 2f) / span).coerceIn(0f, 1f)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Step 22a: a touch only counts when it turns into a sideways drag or a clean tap, so a swipe up that
                // happens to start on a slider no longer changes the volume / brightness.
                downX = ev.x
                downY = ev.y
                moved = false
                accepted = false
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!accepted && !moved) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dy) > slop && abs(dy) > abs(dx)) {
                        moved = true // a vertical swipe: the panel takes it
                        return true
                    }
                    if (abs(dx) > slop) {
                        if (onTouchStart?.invoke() == false) {
                            moved = true
                            return true
                        }
                        accepted = true
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (accepted) {
                    progress = valueAt(ev.x)
                    onChange?.invoke(progress, false)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!accepted && !moved && onTouchStart?.invoke() != false) {
                    progress = valueAt(ev.x) // a clean tap sets the level
                    onChange?.invoke(progress, false)
                    onChange?.invoke(progress, true)
                } else if (accepted) {
                    onChange?.invoke(progress, true)
                }
                accepted = false
                isDragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (accepted) onChange?.invoke(progress, true)
                accepted = false
                isDragging = false
            }
        }
        return true
    }

    companion object {
        const val HEIGHT_DP = 52
    }
}

/**
 * Step 26b: the round buttons live on pages inside one frosted box. Drag left / right and the pages follow the finger; after the last page comes the
 * first again (a loop). Dots under the pages show where you are. With one page it behaves like a plain box.
 */
class TilePager(context: Context, private val pages: List<View>) : FrameLayout(context) {

    private var index = 0
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var neighbor: View? = null
    private var neighborIndex = 0
    private var dir = 0
    private var offset = 0f
    private var velocity: android.view.VelocityTracker? = null
    private var settle: android.animation.ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        for ((i, p) in pages.withIndex()) {
            addView(p, LayoutParams(MATCH, WRAP))
            p.visibility = if (i == 0) View.VISIBLE else View.INVISIBLE
        }
    }

    val multi: Boolean get() = pages.size > 1

    fun containsRaw(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }

    private fun pageWidth(): Float = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(1f)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!multi) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                settle?.cancel()
                downX = ev.x
                downY = ev.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                    beginDrag(ev)
                    return true
                }
            }
        }
        return false
    }

    private fun beginDrag(ev: MotionEvent) {
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        velocity?.recycle()
        velocity = android.view.VelocityTracker.obtain()
        velocity?.addMovement(ev)
        downX = ev.x
        offset = 0f
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!multi) return false
        if (!dragging) {
            // Step 40: a touch that starts on an EMPTY space of the box has no child to take it, so it comes straight here and onInterceptTouchEvent
            // is never asked again: the sideways drag has to be recognised here too (before, swiping the pages from an empty space did nothing).
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    settle?.cancel()
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                        beginDrag(ev)
                        return true
                    }
                }
            }
            return ev.actionMasked == MotionEvent.ACTION_DOWN // tap on empty space: keep the gesture
        }
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> place(ev.x - downX)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val vx = velocity?.xVelocity ?: 0f
                velocity?.recycle()
                velocity = null
                dragging = false
                val w = pageWidth()
                val go = ev.actionMasked == MotionEvent.ACTION_UP && (abs(offset) > w * 0.25f || abs(vx) > 900f && vx * offset > 0f)
                settleTo(if (go) (if (offset < 0) -w else w) else 0f)
            }
        }
        return true
    }

    /** Puts the current page at [dx] and the page that comes next (or before) beside it. */
    private fun place(dx: Float) {
        offset = dx
        val w = pageWidth()
        val newDir = if (dx < 0) 1 else -1
        if (newDir != dir || neighbor == null) {
            neighbor?.visibility = View.INVISIBLE
            dir = newDir
            neighborIndex = (index + dir + pages.size) % pages.size
            neighbor = pages[neighborIndex]
            neighbor?.visibility = View.VISIBLE
        }
        pages[index].translationX = dx
        neighbor?.translationX = if (dx < 0) w + dx else -w + dx
    }

    private fun settleTo(target: Float) {
        val from = offset
        val a = android.animation.ValueAnimator.ofFloat(from, target)
        a.duration = 170
        a.interpolator = DecelerateInterpolator(1.4f)
        a.addUpdateListener { place(it.animatedValue as Float) }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (cancelled) return
                val moved = target != 0f && neighbor != null
                if (moved) {
                    pages[index].visibility = View.INVISIBLE
                    index = neighborIndex
                }
                for (p in pages) p.translationX = 0f
                neighbor?.visibility = if (moved) View.VISIBLE else View.INVISIBLE
                pages[index].visibility = View.VISIBLE
                neighbor = null
                dir = 0
                offset = 0f
                invalidate()
            }
        })
        settle = a
        a.start()
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        if (!multi) return
        val r = context.dpf(2.5f)
        val gap = context.dpf(11f)
        val total = (pages.size - 1) * gap
        val y = height - context.dpf(9f)
        var x = width / 2f - total / 2f
        val shown = if (neighbor != null && abs(offset) > pageWidth() / 2f) neighborIndex else index
        for (i in pages.indices) {
            dotPaint.color = PanelLook.text
            dotPaint.alpha = if (i == shown) 235 else 85
            canvas.drawCircle(x, y, r, dotPaint)
            x += gap
        }
    }
}

/**
 * Step 16a: the Quick panel (Samsung One UI style). Swipe down on the Home screen and it slides down from the top:
 * time and date, two wide buttons (Wi-Fi, Bluetooth), a grid of round buttons and the brightness slider.
 * Tap outside, swipe up, Back or Home closes it. Which buttons it holds is [QuickTiles.order] (Step 16b makes that editable).
 */
class QuickPanel(
    context: Context,
    private val system: QuickSystem,
    private val onOpenSettings: () -> Unit
) : FrameLayout(context) {

    /** True from the moment the panel starts opening until it is completely gone. */
    val isOpen: Boolean get() = visibility == View.VISIBLE

    /** Told when the panel opens or closes (the activity blurs the wallpaper behind it). */
    var onOpenChanged: (() -> Unit)? = null

    /** Step 17c: a swipe to the left on the panel asks for the Notifications page. */
    var onSwitchPage: (() -> Unit)? = null

    /** Step 22: told when the panel is completely gone (after the closing slide); the system-wide overlay removes its window then. */
    var onGone: (() -> Unit)? = null

    private val scrim = View(context)
    private val card = SwipeCard(context)

    /** Step 16e: everything of the panel sits in [content], which scrolls inside [body] when it is taller than the screen. */
    private val content = LinearLayout(context)
    private val body = CappedScrollView(context)
    private val tiles = ArrayList<Pair<String, TileView>>()
    private var slider: PillSlider? = null
    private var tilePager: TilePager? = null
    private var colourStrip: View? = null // Step 37: the row of panel colours under the header
    private var colourOpen = false
    private var layoutEdit = false // Step 38: edit look: red minus on the parts, Edit over the buttons, resize bar, Add a control
    private var addOpen = false
    private var editor: QuickEditor? = null
    private var gallery: IconGallery? = null // Step 30b: the "Change icons" screen
    private var notifs: QuickNotifs? = null
    private var cards: QuickCards? = null
    private var player: QuickPlayer? = null
    private var notifsWatching = false
    private val notifsListener: () -> Unit = { notifs?.bind() }
    private var insetTop = 0
    private var closing = false
    private var receiverOn = false
    private val handler = Handler(Looper.getMainLooper())

    private val swipeSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var sDownX = 0f
    private var sDownY = 0f
    private var sDownScroll = 0
    private var swiped = false

    /**
     * Step 32: the swipe up (close) and swipe left (Notifications) are read from raw positions and from ANY touch on the panel, also on
     * empty spaces and beside the buttons (before, only touches that started on a button or a card were read). True = the swipe was taken.
     */
    private fun swipeEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sDownX = ev.rawX
                sDownY = ev.rawY
                sDownScroll = body.scrollY // Step 39: where the panel was scrolled when the finger came down
                swiped = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (swiped) return true
                val slop = swipeSlop
                val dy = sDownY - ev.rawY
                if (editor == null && gallery == null && !layoutEdit && slider?.isDragging != true && !(notifs?.scrollsAtRaw(sDownX, sDownY) ?: false) && dy > slop * 2 && dy > abs(ev.rawX - sDownX) * 1.2f && (!body.canScrollVertically(1) || sDownScroll == 0 && dy > slop * 6)) {
                    swiped = true
                    close(true)
                    return true
                }
                val dx = ev.rawX - sDownX
                if (editor == null && gallery == null && !layoutEdit && slider?.isDragging != true && dx < -slop * 3f && -dx > abs(ev.rawY - sDownY) * 2f &&
                    !(notifs?.cardsAtRaw(sDownX, sDownY) ?: false) &&
                    !(tilePager?.let { it.multi && it.containsRaw(sDownX, sDownY) } ?: false) &&
                    !(colourStrip?.let { insideRaw(it, sDownX, sDownY) } ?: false)
                ) {
                    swiped = true
                    onSwitchPage?.invoke()
                    return true
                }
            }
        }
        return false
    }

    /** Closing after a swipe up. */
    private inner class SwipeCard(context: Context) : LinearLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = swipeEvent(ev)

        /**
         * Step 39: the swipe is read from EVERY touch before any child sees it. A child that takes the touch for itself (the scrolling panel body,
         * a slider, a button) used to switch this card's own look-out off, so a swipe up that began there did nothing or got stuck.
         */
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (closing) return true // the panel is on its way out: touches must not hit a button under the finger
            val was = swiped
            val taken = swipeEvent(ev)
            if (taken) {
                if (!was) {
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        // a touch that starts on an empty space ends up here (no child took it), so the swipe is read here too
        override fun onTouchEvent(event: MotionEvent): Boolean {
            swipeEvent(event)
            return true
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    /** Brightness changes come in fast while the slider is dragged: only the slider is looked at then. */
    private val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val s = slider
            if (isOpen && s != null && !s.isDragging) s.progress = (system.brightness() - 1) / 254f
        }
    }

    init {
        visibility = View.GONE

        scrim.background = PanelTheme.scrim(150, PanelLook.dark)
        scrim.isClickable = true
        scrim.setOnClickListener { close(true) }
        scrim.setOnTouchListener { _, ev -> closing || swipeEvent(ev) || (swiped && ev.actionMasked != MotionEvent.ACTION_DOWN) } // Step 32: swipe beside the buttons works too
        addView(scrim, LayoutParams(MATCH, MATCH))

        // Step 17b: no card any more - the buttons float over the blurred wallpaper, like the reference.
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true // taps on the card must not close it
        val width = min(context.resources.displayMetrics.widthPixels, context.dp(MAX_WIDTH_DP))
        content.orientation = LinearLayout.VERTICAL
        body.isVerticalScrollBarEnabled = false
        body.overScrollMode = View.OVER_SCROLL_NEVER
        body.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        // Step 20: the panel is as tall as the screen; its lower area (player + notifications) takes the space that is left.
        body.isFillViewport = true
        card.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        addView(card, LayoutParams(width, MATCH, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        setInsets(0)
    }

    /** The status bar height, so the clock sits below it. */
    fun setInsets(top: Int, bottom: Int = 0) {
        insetTop = top
        // Step 22a: the panel ends right above the navigation bar (no empty strip under it).
        card.setPadding(context.dp(18), top + context.dp(12), context.dp(18), bottom + context.dp(10))
        // the panel never grows past the screen: below this it scrolls
        body.maxPx = (context.resources.displayMetrics.heightPixels - top - bottom - context.dp(24)).coerceAtLeast(context.dp(200))
    }

    // ------------------------------------------------------------------ building

    private fun build() {
        content.removeAllViews()
        tiles.clear()
        val c = context
        val order = QuickTiles.order(c)

        content.addView(header(), LinearLayout.LayoutParams(MATCH, WRAP))
        colourStrip = null
        if (colourOpen) {
            val strip = colourStripView()
            colourStrip = strip
            val slp = LinearLayout.LayoutParams(MATCH, WRAP)
            slp.topMargin = c.dp(8)
            content.addView(strip, slp)
        }
        if (brightnessOnTop()) addBrightness(c, 12)

        // The first two buttons are wide (name + state).
        val wideRow = LinearLayout(c)
        wideRow.orientation = LinearLayout.HORIZONTAL
        for ((i, def) in order.take(2).withIndex()) {
            val tile = TileView(c, def, wide = true)
            val lp = LinearLayout.LayoutParams(0, c.dp(62), 1f)
            if (i == 0) lp.rightMargin = c.dp(5) else lp.leftMargin = c.dp(5)
            wideRow.addView(if (layoutEdit) minus(tile, true) { removeTile(def.id) } else tile, lp)
            tiles.add(Pair(def.id, tile))
            if (!layoutEdit) wire(def.id, tile)
        }
        val wideParams = LinearLayout.LayoutParams(MATCH, WRAP)
        wideParams.topMargin = c.dp(14)
        content.addView(wideRow, wideParams)

        // The rest: round buttons, four to a row, eight to a page, all inside ONE dark frosted rectangle (like the reference phone).
        // Step 26b: more than one page = drag left / right, the pages loop.
        val rest = order.drop(2)
        val rowsPerPage = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getInt(KEY_ROWS, 2).coerceIn(1, 4)
        val perPage = COLUMNS * rowsPerPage
        val pageViews = ArrayList<View>()
        var start = 0
        while (start < rest.size) {
            val chunk = rest.subList(start, minOf(rest.size, start + perPage))
            val page = LinearLayout(c)
            page.orientation = LinearLayout.VERTICAL
            var index = 0
            while (index < chunk.size) {
                val row = LinearLayout(c)
                row.orientation = LinearLayout.HORIZONTAL
                for (col in 0 until COLUMNS) {
                    val def = chunk.getOrNull(index + col)
                    if (def == null) {
                        row.addView(View(c), LinearLayout.LayoutParams(0, c.dp(1), 1f))
                        continue
                    }
                    val tile = TileView(c, def, wide = false)
                    row.addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f))
                    tiles.add(Pair(def.id, tile))
                    if (!layoutEdit) wire(def.id, tile)
                }
                val rp = LinearLayout.LayoutParams(MATCH, WRAP)
                rp.topMargin = c.dp(if (index == 0) 6 else 12)
                page.addView(row, rp)
                index += COLUMNS
            }
            pageViews.add(page)
            start += perPage
        }
        tilePager = null
        if (pageViews.isNotEmpty()) {
            val pager = TilePager(c, pageViews)
            pager.setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(if (pageViews.size > 1) 22 else 12))
            pager.background = GradientDrawable().apply {
                setColor(PanelLook.glass(80))
                cornerRadius = c.dpf(30f)
                setStroke(c.dp(1), PanelLook.inkA(22))
            }
            tilePager = pager
            val gp = LinearLayout.LayoutParams(MATCH, WRAP)
            gp.topMargin = c.dp(14)
            if (layoutEdit) {
                // Step 38: the buttons are dimmed with an Edit pill (opens the button editor) and a bar under them to change the height
                pager.alpha = 0.5f
                val frame = FrameLayout(c)
                frame.addView(pager, FrameLayout.LayoutParams(MATCH, WRAP))
                val edit = pill("Edit", 18f)
                edit.setOnClickListener { startEdit() }
                frame.addView(edit, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                content.addView(frame, gp)
                content.addView(resizeBar(rowsPerPage), LinearLayout.LayoutParams(MATCH, c.dp(34)))
            } else {
                content.addView(pager, gp)
            }
        }

        if (!brightnessOnTop()) addBrightness(c, 18)

        // Volume card (Step 16d), then the lower area (Step 20): the music player and the notifications in one frosted box
        // that stretches down to the bottom of the screen.
        cards?.stop()
        cards = null
        player?.stop()
        player = null
        notifs = null
        val cardsOn = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(QuickCards.PREF_KEY, true)
        if (cardsOn && !isHidden(HIDE_VOLUME)) {
            val k = QuickCards(c, { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() }) { close(false) }
            cards = k
            val kp = LinearLayout.LayoutParams(MATCH, WRAP)
            kp.topMargin = c.dp(14)
            content.addView(if (layoutEdit) minus(k, true) { setHidden(HIDE_VOLUME, true) } else k, kp)
            if (isOpen) k.start() // the panel is already showing (back from the editor)
        }
        val lower = LinearLayout(c)
        lower.orientation = LinearLayout.VERTICAL
        lower.setPadding(c.dp(10), c.dp(10), c.dp(10), c.dp(10))
        lower.clipChildren = true
        lower.background = GradientDrawable().apply {
            setColor(PanelLook.glass(70))
            cornerRadius = c.dpf(30f)
            setStroke(c.dp(1), PanelLook.inkA(22))
        }
        val playerOn = cardsOn && !isHidden(HIDE_MEDIA)
        if (playerOn) {
            val p = QuickPlayer(c) { close(false) }
            player = p
            lower.addView(if (layoutEdit) minus(p, false) { setHidden(HIDE_MEDIA, true) } else p, LinearLayout.LayoutParams(MATCH, WRAP))
            if (isOpen) p.start()
        }
        if (prefsOn()) {
            val n = QuickNotifs(c, false, true) { close(false) }
            n.bind()
            notifs = n
            val np = LinearLayout.LayoutParams(MATCH, 0, 1f)
            np.topMargin = c.dp(if (playerOn) 14 else 0)
            lower.addView(n, np)
        }
        if (lower.childCount > 0) {
            val lp = LinearLayout.LayoutParams(MATCH, 0, 1f) // Step 22a: only the space that is left; what does not fit is cropped
            lp.topMargin = c.dp(14)
            content.addView(lower, lp)
        }

        if (layoutEdit) {
            if (addOpen) {
                val alp = LinearLayout.LayoutParams(MATCH, WRAP)
                alp.topMargin = c.dp(10)
                content.addView(addControlView(), alp)
            }
            val add = pill("+  Add a control", 16f)
            add.setOnClickListener {
                addOpen = !addOpen
                build()
            }
            val addLp = LinearLayout.LayoutParams(WRAP, WRAP)
            addLp.gravity = Gravity.CENTER_HORIZONTAL
            addLp.topMargin = c.dp(10)
            content.addView(add, addLp)
        }

        // A little grab bar: it says "swipe up to close".
        val bar = View(c)
        bar.background = roundRect(PanelLook.inkA(70), c.dpf(2f))
        val barParams = LinearLayout.LayoutParams(c.dp(44), c.dp(4))
        barParams.gravity = Gravity.CENTER_HORIZONTAL
        barParams.topMargin = c.dp(8)
        barParams.bottomMargin = c.dp(0)
        content.addView(bar, barParams)

        refresh()
    }

    /** "Notifications in Quick panel" (Settings > Gestures), on by default. */
    private fun prefsOn(): Boolean = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(Notifs.PREF_KEY, true)

    /** Settings > Quick panel > Brightness slider: on top (under the clock) or at the bottom of the buttons (default). */
    private fun brightnessOnTop(): Boolean = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(KEY_BRIGHT_TOP, false)

    /** The brightness slider, [topMarginDp] below what is above it. */
    private fun addBrightness(c: Context, topMarginDp: Int) {
        if (isHidden(HIDE_BRIGHT)) return
        val bright = PillSlider(c, QuickGlyph.SUN)
        bright.contentDescription = "Brightness"
        bright.progress = (system.brightness() - 1) / 254f
        bright.onTouchStart = {
            if (system.canSetBrightness()) {
                true
            } else {
                system.askWriteSettings()
                close(false)
                false
            }
        }
        bright.onChange = { p, _ -> system.setBrightness(1 + (p * 254f).toInt()) }
        slider = bright
        // Step 17b: a round moon button sits at the right end of the slider (opens the phone's display / dark mode settings).
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(bright, LinearLayout.LayoutParams(0, c.dp(PillSlider.HEIGHT_DP), 1f))
        val moon = roundGlyphButton(c, QuickGlyph(c, QuickGlyph.DARK), "Display and dark mode settings") { openDisplaySettings() }
        val moonLp = LinearLayout.LayoutParams(c.dp(PillSlider.HEIGHT_DP), c.dp(PillSlider.HEIGHT_DP))
        moonLp.leftMargin = c.dp(10)
        row.addView(moon, moonLp)
        val bp = LinearLayout.LayoutParams(MATCH, WRAP)
        bp.topMargin = c.dp(topMarginDp)
        content.addView(if (layoutEdit) minus(row, true) { setHidden(HIDE_BRIGHT, true) } else row, bp)
    }

    /** The power icon in the header: locks the screen (the phone's power menu is not open to launchers). Needs "Double-tap to lock" to be allowed once. */
    private fun powerTap() {
        close(false)
        if (!LockAdmin.lock(context)) {
            Toast.makeText(context, "To lock from here, turn on Double-tap to lock (long-press an empty spot on Home)", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDisplaySettings() {
        close(false)
        try {
            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Can't open Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun header(): View {
        if (layoutEdit) return editHeader()
        val c = context
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val texts = LinearLayout(c)
        texts.orientation = LinearLayout.HORIZONTAL // Step 17b: time and date on one line
        texts.gravity = Gravity.BOTTOM
        val time = TextClock(c)
        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        time.setTextColor(PanelLook.text)
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        time.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        time.includeFontPadding = false
        texts.addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
        val date = TextClock(c)
        val pattern = try {
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd").ifEmpty { "EEE, MMM d" }
        } catch (e: Exception) {
            "EEE, MMM d"
        }
        date.format12Hour = pattern
        date.format24Hour = pattern
        date.setTextColor(PanelLook.textA(200))
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        date.includeFontPadding = false
        val dateLp = LinearLayout.LayoutParams(WRAP, WRAP)
        dateLp.leftMargin = c.dp(10)
        dateLp.bottomMargin = c.dp(4)
        texts.addView(date, dateLp)
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        // Step 37: the round colour button: it shows the colour the panel has now; a tap opens / closes the row of colours right here
        val palette = colourDot(PanelTheme.saved(c), false)
        palette.contentDescription = "Panel colour"
        palette.isClickable = true
        palette.setOnClickListener {
            colourOpen = !colourOpen
            build()
        }
        row.addView(palette, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))

        val pencil = QuickGlyph(c, QuickGlyph.PENCIL)
        pencil.tint = PanelLook.text
        pencil.contentDescription = "Edit buttons"
        pencil.isClickable = true
        pencil.setOnClickListener {
            layoutEdit = true
            colourOpen = false
            build()
        }
        pencil.setOnLongClickListener {
            maybeScan(true)
            true
        }
        row.addView(pencil, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))

        val power = QuickGlyph(c, QuickGlyph.POWER)
        power.tint = PanelLook.text
        power.contentDescription = "Lock screen"
        power.isClickable = true
        power.setOnClickListener { powerTap() }
        row.addView(power, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))

        val gear = QuickGlyph(c, QuickGlyph.GEAR)
        gear.tint = PanelLook.text
        gear.contentDescription = "Settings"
        gear.isClickable = true
        gear.setOnClickListener {
            onOpenSettings()
            close(false)
        }
        gear.setOnLongClickListener {
            startGallery()
            true
        }
        row.addView(gear, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))
        return row
    }

    /**
     * Step 27: reads the phone's own Quick settings once (hidden behind a picture) and adds every button the launcher has no button for
     * (Speed up, Record screen, vivoshare...). [again]: long-press on the pencil reads it again.
     */
    private fun maybeScan(again: Boolean = false) {
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (!again) {
            if (!SystemPanel.wanted(context)) return // Step 50: Accessibility is not used, so there is nothing to read and nothing to complain about
            if (prefs.getBoolean("qp_scanned", false)) return
            if (prefs.getInt("qp_scan_tries", 0) >= 4) return // after four empty tries only the long-press on the pencil reads again
        }
        val svc = PanelAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(context, "The Quick panel service is OFF. Turn on \"Quick panel\" in Accessibility so the phone's own buttons can be found", Toast.LENGTH_LONG).show()
            if (again) {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // no Accessibility screen
                }
            }
            return
        }
        if (!svc.canReadPanel()) {
            Toast.makeText(context, "Switch the \"Quick panel\" service OFF and ON once in Accessibility (the update asked for a new right)", Toast.LENGTH_LONG).show()
            return
        }
        handler.postDelayed({
            if (!isOpen || editor != null || gallery != null) return@postDelayed
            Toast.makeText(context, "Reading your phone's buttons...", Toast.LENGTH_SHORT).show()
            system.scan { ok, note, count ->
                prefs.edit().putInt("qp_scan_tries", prefs.getInt("qp_scan_tries", 0) + 1).apply()
                if (ok && count > 0) prefs.edit().putBoolean("qp_scanned", true).apply()
                val names = QuickTiles.ALL.filter { it.id.startsWith(QuickTiles.CUSTOM_PREFIX) }.take(5).joinToString(", ") { it.label }
                val copied = if (!ok || count == 0 || again) PanelAccessibilityService.instance?.copyReport() == true else false
                val msg = when {
                    ok && count > 0 -> "Found $count buttons of your phone: $names" + (if (count > 5) "..." else "")
                    ok -> "No extra buttons found ($note)" + (if (copied) ". Report copied: paste it in the chat" else "")
                    else -> "Could not read the phone's panel ($note)" + (if (copied) ". Report copied: paste it in the chat" else "")
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (isOpen && editor == null && gallery == null) build()
            }
        }, 450L)
    }

    private fun insideRaw(v: View, x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
    }

    /** A round swatch of panel colour number [i] with a ring (a bright ring = the colour in use). */
    private fun colourDot(i: Int, selected: Boolean): View {
        val c = context
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(c.dp(2), if (selected) PanelLook.text else PanelLook.inkA(70))
        }
        val layers = LayerDrawable(arrayOf(PanelTheme.swatch(i), ring))
        layers.setLayerInset(0, c.dp(7), c.dp(7), c.dp(7), c.dp(7))
        layers.setLayerInset(1, c.dp(3), c.dp(3), c.dp(3), c.dp(3))
        val v = View(c)
        v.background = layers
        return v
    }

    /** The row of all panel colours (the same ones as Settings > Look and feel > Panels > Panel colour). A tap changes the panel at once. */
    private fun colourStripView(): View {
        val c = context
        val cur = PanelTheme.saved(c)
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(c.dp(14), c.dp(10), c.dp(6), c.dp(6))
        box.background = GradientDrawable().apply {
            setColor(PanelLook.glass(70))
            cornerRadius = c.dpf(24f)
            setStroke(c.dp(1), PanelLook.inkA(22))
        }
        val title = TextView(c)
        title.text = "Panel colour: " + PanelTheme.name(cur)
        title.setTextColor(PanelLook.textA(220))
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        box.addView(title, LinearLayout.LayoutParams(WRAP, WRAP))
        val scroll = HorizontalScrollView(c)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until PanelTheme.COUNT) {
            val dot = colourDot(i, i == cur)
            dot.contentDescription = PanelTheme.name(i)
            dot.isClickable = true
            dot.setOnClickListener { chooseColour(i) }
            row.addView(dot, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))
        }
        scroll.addView(row, FrameLayout.LayoutParams(WRAP, WRAP))
        scroll.post { scroll.scrollTo(max(0, cur * c.dp(44) - scroll.width / 2 + c.dp(22)), 0) }
        val slp = LinearLayout.LayoutParams(MATCH, WRAP)
        slp.topMargin = c.dp(2)
        box.addView(scroll, slp)
        return box
    }

    private fun chooseColour(i: Int) {
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putInt(PanelTheme.KEY, i).apply()
        PanelTheme.load(context)
        scrim.background = PanelTheme.scrim(205, PanelLook.dark)
        build() // every glass of the panel is drawn again in the new colour
    }

    // ------------------------------------------------------------------ Step 38: edit look (like the reference phone)

    private fun uiPrefs() = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private fun isHidden(key: String): Boolean = uiPrefs().getBoolean(key, false)

    private fun setHidden(key: String, hidden: Boolean) {
        uiPrefs().edit().putBoolean(key, hidden).apply()
        build()
    }

    private fun removeTile(id: String) {
        val ids = QuickTiles.order(context).map { it.id }.filter { it != id }
        QuickTiles.save(context, ids)
        build()
    }

    private fun addTile(id: String) {
        QuickTiles.save(context, QuickTiles.order(context).map { it.id } + id)
        build()
    }

    /** A rounded frosted word-button ("Edit", "Done", "Add a control"). */
    private fun pill(text: String, sp: Float): TextView {
        val c = context
        val t = TextView(c)
        t.text = text
        t.setTextColor(PanelLook.text)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.typeface = android.graphics.Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(c.dp(18), c.dp(10), c.dp(18), c.dp(10))
        t.background = GradientDrawable().apply {
            setColor(PanelLook.glass(150))
            cornerRadius = c.dpf(26f)
            setStroke(c.dp(1), PanelLook.inkA(30))
        }
        t.isClickable = true
        return t
    }

    /** [view] with a red round minus at its top-left corner; the minus calls [onRemove]. [outside]: the minus sticks out over the corner. */
    private fun minus(view: View, outside: Boolean, onRemove: () -> Unit): View {
        val c = context
        val box = FrameLayout(c)
        box.clipChildren = false
        box.clipToPadding = false
        box.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        val badge = TextView(c)
        badge.text = "\u2212"
        badge.setTextColor(Color.WHITE)
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        badge.typeface = android.graphics.Typeface.DEFAULT_BOLD
        badge.gravity = Gravity.CENTER
        badge.includeFontPadding = false
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(229, 72, 77))
            setStroke(c.dp(2), Color.WHITE)
        }
        badge.contentDescription = "Remove"
        badge.isClickable = true
        badge.setOnClickListener { onRemove() }
        val lp = FrameLayout.LayoutParams(c.dp(26), c.dp(26), Gravity.TOP or Gravity.START)
        val m = if (outside) -c.dp(6) else c.dp(4)
        lp.setMargins(m, m, 0, 0)
        box.addView(badge, lp)
        return box
    }

    /** The header of the edit look: Panel settings at the left, Done at the right and a line of help under them. */
    private fun editHeader(): View {
        val c = context
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val settings = pill("Panel settings", 16f)
        settings.setOnClickListener {
            onOpenSettings()
            close(false)
        }
        row.addView(settings, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        val done = pill("Done", 16f)
        done.setOnClickListener {
            layoutEdit = false
            addOpen = false
            build()
        }
        row.addView(done, LinearLayout.LayoutParams(WRAP, WRAP))
        col.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        val hint = TextView(c)
        hint.text = "Tap  \u2212  to remove a part. Drag the bar under the buttons to change their height."
        hint.setTextColor(PanelLook.textA(215))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        hint.gravity = Gravity.CENTER
        val hp = LinearLayout.LayoutParams(MATCH, WRAP)
        hp.topMargin = c.dp(14)
        col.addView(hint, hp)
        return col
    }

    /** The white bar under the buttons: drag it down for more rows on a page (up to 4), up for fewer (down to 1). */
    private fun resizeBar(rows: Int): View {
        val c = context
        val holder = FrameLayout(c)
        val bar = View(c)
        bar.background = GradientDrawable().apply {
            setColor(PanelLook.text)
            cornerRadius = c.dpf(3f)
        }
        holder.addView(bar, FrameLayout.LayoutParams(c.dp(48), c.dp(5), Gravity.CENTER))
        val label = TextView(c)
        label.text = if (rows == 1) "1 row" else "$rows rows"
        label.setTextColor(PanelLook.textA(200))
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        holder.addView(label, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.END).also { it.rightMargin = c.dp(14) })
        holder.contentDescription = "Resize the buttons: drag up or down"
        val step = c.dp(70).toFloat()
        var downY = 0f
        holder.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.rawY
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val d = (ev.rawY - downY).coerceIn(-step * 2, step * 2)
                    bar.translationY = d / 3f
                    val n = (rows + Math.round(d / step)).coerceIn(1, 4)
                    label.text = if (n == 1) "1 row" else "$n rows"
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val n = (rows + Math.round((ev.rawY - downY) / step)).coerceIn(1, 4)
                    bar.translationY = 0f
                    if (ev.actionMasked == MotionEvent.ACTION_UP && n != rows) {
                        uiPrefs().edit().putInt(KEY_ROWS, n).apply()
                        build()
                    }
                    true
                }
                else -> false
            }
        }
        return holder
    }

    /** What can be put back: the parts taken off with a minus and every button that is not on the panel. Tap one to add it. */
    private fun addControlView(): View {
        val c = context
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(c.dp(14), c.dp(10), c.dp(6), c.dp(8))
        box.background = GradientDrawable().apply {
            setColor(PanelLook.glass(70))
            cornerRadius = c.dpf(24f)
            setStroke(c.dp(1), PanelLook.inkA(22))
        }
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (isHidden(HIDE_BRIGHT)) items.add(Pair("Brightness") { setHidden(HIDE_BRIGHT, false) })
        if (isHidden(HIDE_VOLUME)) items.add(Pair("Volume") { setHidden(HIDE_VOLUME, false) })
        if (isHidden(HIDE_MEDIA)) items.add(Pair("Media player") { setHidden(HIDE_MEDIA, false) })
        val active = QuickTiles.order(c).map { it.id }.toSet()
        for (d in QuickTiles.ALL) if (d.id !in active) items.add(Pair(d.label) { addTile(d.id) })
        val title = TextView(c)
        title.text = if (items.isEmpty()) "Everything is already on the panel" else "Tap to add"
        title.setTextColor(PanelLook.textA(220))
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        box.addView(title, LinearLayout.LayoutParams(WRAP, WRAP))
        if (items.isNotEmpty()) {
            val scroll = HorizontalScrollView(c)
            scroll.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            for ((label, action) in items) {
                val chip = pill(label, 14f)
                chip.setOnClickListener { action() }
                val clp = LinearLayout.LayoutParams(WRAP, WRAP)
                clp.rightMargin = c.dp(8)
                clp.topMargin = c.dp(8)
                row.addView(chip, clp)
            }
            scroll.addView(row, FrameLayout.LayoutParams(WRAP, WRAP))
            box.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        return box
    }

    /** Tap and long-press of one button. */
    private fun wire(id: String, tile: TileView) {
        tile.setOnClickListener {
            tile.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            system.tap(id)
            // Step 26: every button switches right here; the panel only closes when the tap had to open a phone screen (system.onLeave)
            handler.postDelayed({ refresh() }, 200)
            handler.postDelayed({ refresh() }, 800)
            handler.postDelayed({ refresh() }, 1800)
        }
        tile.setOnLongClickListener {
            system.longPress(id)
            close(false)
            true
        }
    }

    // ------------------------------------------------------------------ edit buttons (Step 16b)

    /** True while the "Edit buttons" screen is showing instead of the panel. */
    val isEditing: Boolean get() = editor != null || gallery != null

    private fun startEdit() {
        if (editor != null) return
        content.removeAllViews()
        tiles.clear()
        slider = null
        notifs = null
        cards?.stop()
        cards = null
        player?.stop()
        player = null
        val e = QuickEditor(context, { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }) { ids ->
            QuickTiles.save(context, ids)
            finishEdit()
        }
        e.load(QuickTiles.order(context).map { it.id })
        editor = e
        content.addView(e, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** Step 30b: long-press on the gear = pick an icon for any button. */
    private fun startGallery() {
        if (editor != null || gallery != null) return
        content.removeAllViews()
        tiles.clear()
        slider = null
        notifs = null
        cards?.stop()
        cards = null
        player?.stop()
        player = null
        val g = IconGallery(context, QuickTiles.order(context)) { finishGallery() }
        gallery = g
        content.addView(g, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun finishGallery() {
        gallery = null
        build()
    }

    /** Back to the panel (the buttons are read again, so a saved change shows at once). */
    private fun finishEdit() {
        editor = null
        build()
    }

    /** Back / Home: leaves the editor without saving, or else closes the panel. */
    fun back(animated: Boolean) {
        if (gallery != null) {
            if (gallery?.back() != true) finishGallery()
        } else if (editor != null) finishEdit() else if (layoutEdit) {
            layoutEdit = false
            addOpen = false
            build()
        } else close(animated)
    }

    /** Re-reads the phone's state for every button and the brightness. */
    fun refresh() {
        if (!isOpen) return
        for ((id, tile) in tiles) tile.show(system.state(id))
        val s = slider
        if (s != null && !s.isDragging) s.progress = (system.brightness() - 1) / 254f
    }

    // ------------------------------------------------------------------ open / close

    fun open() {
        if (isOpen && !closing) return
        if (closing) finishClose() // Step 39: a close that never finished must not block the next open
        card.animate().setUpdateListener(null).cancel()
        card.setLayerType(View.LAYER_TYPE_NONE, null)
        scrim.animate().cancel()
        card.alpha = 1f
        Motion.setBlur(card, 0f)
        closing = false
        editor = null
        gallery = null
        colourOpen = false
        layoutEdit = false
        addOpen = false
        PanelTheme.load(context)
        QuickIcons.load(context)
        build()
        system.start()
        system.onChanged = { refresh() }
        system.onLeave = { close(false) }
        watchPhone()
        cards?.start()
        player?.start()
        if (!notifsWatching) {
            notifsWatching = true
            Notifs.addListener(notifsListener)
        }
        maybeScan()
        body.scrollTo(0, 0)
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 1200L)
        visibility = View.VISIBLE
        // Step 25: nothing is blurred behind the panel any more, so the veil alone must hide the Home screen: stronger than before.
        scrim.background = PanelTheme.scrim(205, PanelLook.dark)
        // Step 31: slide down AND fade 0 -> 100 together (panel first, the veil a little later), one smooth curve.
        val h0 = -context.resources.displayMetrics.heightPixels.toFloat()
        scrim.alpha = 0f
        scrim.translationY = h0 * 0.35f
        card.alpha = 0f
        card.translationY = h0 * 0.35f
        Motion.setBlur(card, Motion.BLUR_PX)
        card.animate().translationY(0f).setDuration(Motion.OPEN_MS).setInterpolator(Motion.OPEN)
            .setUpdateListener {
                val p = 1f - (card.translationY / (h0 * 0.35f)).coerceIn(0f, 1f)
                // Step 33: top to bottom, opacity 0 -> 100 on a soft curve, the panel sharpening from a blur while it lands
                // Step 35: fast, 0 -> 100 within the first 60% of the move
                card.alpha = Motion.smooth(Motion.ramp(p, 0.5f))
                scrim.alpha = Motion.smooth(Motion.ramp(p, 0.55f))
                scrim.translationY = card.translationY
                Motion.setBlur(card, Motion.BLUR_PX * (1f - Motion.ramp(p, 0.9f)))
            }
            .withEndAction {
                card.alpha = 1f
                scrim.alpha = 1f
                scrim.translationY = 0f
                Motion.setBlur(card, 0f)
            }
            .start()
        onOpenChanged?.invoke()
    }

    fun close(animated: Boolean) {
        if (!isOpen || closing) return
        card.animate().cancel()
        scrim.animate().cancel()
        unwatchPhone()
        cards?.stop()
        player?.stop()
        if (notifsWatching) {
            notifsWatching = false
            Notifs.removeListener(notifsListener)
        }
        system.stop()
        system.onChanged = null
        handler.removeCallbacksAndMessages(null)
        if (!animated) {
            Motion.setBlur(card, 0f)
            visibility = View.GONE
            onOpenChanged?.invoke()
            onGone?.invoke()
            return
        }
        closing = true
        // Step 35: the moment the swipe up is accepted the opacity falls straight from 100% to 20%, then runs down to 0 while the panel
        // slides up fast. No blur while closing (changing a blur on every frame was a source of glitches); the card is one picture while it moves.
        card.alpha = Motion.CLOSE_START_ALPHA
        scrim.alpha = Motion.CLOSE_START_ALPHA
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val travel = -(card.height * 0.35f + context.dp(24))
        card.animate().translationY(travel).setDuration(Motion.CLOSE_MS)
            .setInterpolator(Motion.CLOSE)
            .setUpdateListener {
                val q = (card.translationY / travel).coerceIn(0f, 1f)
                card.alpha = Motion.fadeOut(q)
                scrim.alpha = Motion.fadeOut(q)
                scrim.translationY = card.translationY
            }
            .withEndAction { finishClose() }.start()
        // Step 39: if the end of the slide is never reported (the animation was cancelled, the window was busy) the panel would stay on the screen,
        // invisible, and take every touch. This makes sure it is gone a moment after the slide should have ended.
        handler.postDelayed({ finishClose() }, Motion.CLOSE_MS + 250L)
        // The blur fades while the panel slides away.
        post { onOpenChanged?.invoke() }
    }

    /** The end of the closing slide (also run by the safety timer of [close]): the panel is gone and every leftover of the slide is reset. */
    private fun finishClose() {
        if (!closing) return
        card.animate().cancel()
        card.animate().setUpdateListener(null)
        card.setLayerType(View.LAYER_TYPE_NONE, null)
        Motion.setBlur(card, 0f)
        card.alpha = 1f
        card.translationY = 0f
        scrim.alpha = 1f
        scrim.translationY = 0f
        closing = false
        visibility = View.GONE
        onGone?.invoke()
    }

    /** Step 39: while the panel is open the buttons are read again every 1.2 s, so one never stays lit (or dark) after the phone changed on its own. */
    private val poll = object : Runnable {
        override fun run() {
            if (!isOpen || closing) return
            try {
                refresh()
            } catch (e: Exception) {
                // a failed read must not stop the next one
            }
            handler.postDelayed(this, 1200L)
        }
    }

    /** `isOpen` stays true while the closing animation runs; the blur must not wait for it, so the activity asks this. */
    val wantsBlur: Boolean get() = isOpen && !closing

    private fun watchPhone() {
        if (receiverOn) return
        val filter = IntentFilter()
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        filter.addAction(LocationManager.MODE_CHANGED_ACTION)
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        filter.addAction("android.nfc.action.ADAPTER_STATE_CHANGED")
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
            receiverOn = true
        } catch (e: Exception) {
            // the buttons then refresh only after a tap
        }
    }

    private fun unwatchPhone() {
        if (!receiverOn) return
        receiverOn = false
        try {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
            context.contentResolver.unregisterContentObserver(brightnessObserver)
        } catch (e: Exception) {
            // already gone
        }
    }

    override fun onDetachedFromWindow() {
        unwatchPhone()
        cards?.stop()
        player?.stop()
        if (notifsWatching) {
            notifsWatching = false
            Notifs.removeListener(notifsListener)
        }
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ one button

    /** A round button (icon in a circle, name underneath) or a wide one (icon, name and state side by side). */
    private class TileView(context: Context, private val def: QuickTileDef, private val wide: Boolean) : LinearLayout(context) {

        private val circle = FrameLayout(context)
        private val glyph = QuickGlyph(context, def.glyph).also {
            if (def.id.startsWith(QuickTiles.CUSTOM_PREFIX) && def.glyph == QuickGlyph.GRID) it.letter = def.label.trim().take(1).uppercase()
        }
        private val label = TextView(context)
        private val sub = TextView(context)
        private var lastOn: Boolean? = null

        init {
            val c = context
            isClickable = true
            isLongClickable = true
            label.text = def.label
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (wide) 14f else 11.5f)
            label.maxLines = if (wide) 1 else 2
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            sub.maxLines = 1

            if (wide) {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(c.dp(10), 0, c.dp(10), 0)
                circle.addView(glyph, FrameLayout.LayoutParams(c.dp(26), c.dp(26), Gravity.CENTER))
                addView(circle, LayoutParams(c.dp(42), c.dp(42)))
                val texts = LinearLayout(c)
                texts.orientation = VERTICAL
                texts.addView(label, LayoutParams(WRAP, WRAP))
                texts.addView(sub, LayoutParams(WRAP, WRAP))
                val tp = LayoutParams(0, WRAP, 1f)
                tp.leftMargin = c.dp(10)
                addView(texts, tp)
            } else {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                circle.addView(glyph, FrameLayout.LayoutParams(c.dp(28), c.dp(28), Gravity.CENTER))
                addView(circle, LayoutParams(c.dp(58), c.dp(58)))
                label.gravity = Gravity.CENTER
                val lp = LayoutParams(MATCH, WRAP)
                lp.topMargin = c.dp(5)
                addView(label, lp)
            }
        }

        fun show(state: TileState) {
            val c = context
            val on = state.on
            val picked = QuickIcons.of(def.id)
            glyph.letter = if (picked == null && def.id.startsWith(QuickTiles.CUSTOM_PREFIX) && state.glyph == QuickGlyph.GRID) def.label.trim().take(1).uppercase() else if (picked == QuickGlyph.GRID) def.label.trim().take(1).uppercase() else null
            glyph.show(picked ?: state.glyph)
            sub.text = state.sub
            if (lastOn != on) {
                lastOn = on
                val onBg = PanelLook.text
                val onFg = if (PanelLook.dark) Color.rgb(24, 26, 32) else Color.WHITE
                if (wide) {
                    background = roundRect(if (on) onBg else PanelLook.glass(110), c.dpf(31f))
                    circle.background = oval(if (on) Color.argb(34, Color.red(onFg), Color.green(onFg), Color.blue(onFg)) else PanelLook.glass(90))
                } else {
                    circle.background = oval(if (on) onBg else PanelLook.glass(115))
                }
                glyph.tint = if (on) onFg else PanelLook.textA(225)
                if (wide) {
                    label.setTextColor(if (on) onFg else PanelLook.text)
                    sub.setTextColor(if (on) Color.argb(190, Color.red(onFg), Color.green(onFg), Color.blue(onFg)) else PanelLook.textA(170))
                } else {
                    label.setTextColor(PanelLook.text)
                }
            }
            contentDescription = def.label + (if (state.sub.isNotEmpty()) ", " + state.sub else "")
        }

        private fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            return super.onTouchEvent(event)
        }
    }

    private companion object {
        const val COLUMNS = 4

        /** "Brightness slider on top" (Settings > Quick panel). */
        const val KEY_BRIGHT_TOP = "qp_bright_top"
        const val MAX_WIDTH_DP = 600

    }
}

/** Step 16e: a scroll view that never grows taller than [maxPx] (the Quick panel scrolls inside it when it is taller than the screen). */
class CappedScrollView(context: Context) : android.widget.ScrollView(context) {
    var maxPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Step 20: an exact height from the parent (the panel fills the screen) is kept.
        val h = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) heightMeasureSpec else View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, h)
    }
}
