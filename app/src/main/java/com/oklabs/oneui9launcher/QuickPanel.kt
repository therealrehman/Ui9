package com.oklabs.oneui9launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    init {
        track.background = roundRect(Look.glass(110), context.dpf(HEIGHT_DP / 2f))
        fill.background = roundRect(Look.text, context.dpf(HEIGHT_DP / 2f))
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
        glyph.tint = if (over) (if (Look.dark) Color.rgb(24, 26, 32) else Color.WHITE) else Look.text
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
                accepted = onTouchStart?.invoke() ?: true
                if (!accepted) return true
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                progress = valueAt(ev.x)
                onChange?.invoke(progress, false)
            }
            MotionEvent.ACTION_MOVE -> if (accepted) {
                progress = valueAt(ev.x)
                onChange?.invoke(progress, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

    private val scrim = View(context)
    private val card = SwipeCard(context)

    /** Step 16e: everything of the panel sits in [content], which scrolls inside [body] when it is taller than the screen. */
    private val content = LinearLayout(context)
    private val body = CappedScrollView(context)
    private val tiles = ArrayList<Pair<String, TileView>>()
    private var slider: PillSlider? = null
    private var editor: QuickEditor? = null
    private var notifs: QuickNotifs? = null
    private var cards: QuickCards? = null
    private var notifsWatching = false
    private val notifsListener: () -> Unit = { notifs?.bind() }
    private var insetTop = 0
    private var closing = false
    private var receiverOn = false
    private val handler = Handler(Looper.getMainLooper())

    /** Closing after a swipe up. */
    private inner class SwipeCard(context: Context) : LinearLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = downY - ev.y
                    if (editor == null && slider?.isDragging != true && !body.canScrollVertically(1) && !(notifs?.scrollsAt(downX, downY) ?: false) && dy > slop * 2 && dy > abs(ev.x - downX) * 1.5f) {
                        close(true)
                        return true
                    }
                    val dx = ev.x - downX
                    if (editor == null && slider?.isDragging != true && dx < -slop * 3f && -dx > abs(ev.y - downY) * 2f &&
                        !(notifs?.cardsAt(downX, downY) ?: false)
                    ) {
                        onSwitchPage?.invoke()
                        return true
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = true
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

        scrim.setBackgroundColor(Look.veil(150))
        scrim.isClickable = true
        scrim.setOnClickListener { close(true) }
        addView(scrim, LayoutParams(MATCH, MATCH))

        // Step 17b: no card any more - the buttons float over the blurred wallpaper, like the reference.
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true // taps on the card must not close it
        val width = min(context.resources.displayMetrics.widthPixels, context.dp(MAX_WIDTH_DP))
        content.orientation = LinearLayout.VERTICAL
        body.isVerticalScrollBarEnabled = false
        body.overScrollMode = View.OVER_SCROLL_NEVER
        body.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        card.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(card, LayoutParams(width, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        setInsets(0)
    }

    /** The status bar height, so the clock sits below it. */
    fun setInsets(top: Int) {
        insetTop = top
        card.setPadding(context.dp(18), top + context.dp(12), context.dp(18), context.dp(12))
        // the panel never grows past the screen: below this it scrolls
        body.maxPx = (context.resources.displayMetrics.heightPixels - top - context.dp(64)).coerceAtLeast(context.dp(200))
    }

    // ------------------------------------------------------------------ building

    private fun build() {
        content.removeAllViews()
        tiles.clear()
        val c = context
        val order = QuickTiles.order(c)

        content.addView(header(), LinearLayout.LayoutParams(MATCH, WRAP))
        if (brightnessOnTop()) addBrightness(c, 12)

        // The first two buttons are wide (name + state).
        val wideRow = LinearLayout(c)
        wideRow.orientation = LinearLayout.HORIZONTAL
        for ((i, def) in order.take(2).withIndex()) {
            val tile = TileView(c, def, wide = true)
            val lp = LinearLayout.LayoutParams(0, c.dp(62), 1f)
            if (i == 0) lp.rightMargin = c.dp(5) else lp.leftMargin = c.dp(5)
            wideRow.addView(tile, lp)
            tiles.add(Pair(def.id, tile))
            wire(def.id, tile)
        }
        val wideParams = LinearLayout.LayoutParams(MATCH, WRAP)
        wideParams.topMargin = c.dp(14)
        content.addView(wideRow, wideParams)

        // The rest: round buttons, four to a row, all inside ONE dark frosted rectangle (like the reference phone).
        val rest = order.drop(2)
        val grid = LinearLayout(c)
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(12))
        grid.background = GradientDrawable().apply {
            setColor(Look.glass(80))
            cornerRadius = c.dpf(30f)
            setStroke(c.dp(1), Look.inkA(22))
        }
        var index = 0
        while (index < rest.size) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until COLUMNS) {
                val def = rest.getOrNull(index + col)
                if (def == null) {
                    row.addView(View(c), LinearLayout.LayoutParams(0, c.dp(1), 1f))
                    continue
                }
                val tile = TileView(c, def, wide = false)
                row.addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f))
                tiles.add(Pair(def.id, tile))
                wire(def.id, tile)
            }
            val rp = LinearLayout.LayoutParams(MATCH, WRAP)
            rp.topMargin = c.dp(if (index == 0) 6 else 12)
            grid.addView(row, rp)
            index += COLUMNS
        }
        if (rest.isNotEmpty()) {
            val gp = LinearLayout.LayoutParams(MATCH, WRAP)
            gp.topMargin = c.dp(14)
            content.addView(grid, gp)
        }

        if (!brightnessOnTop()) addBrightness(c, 18)

        // Volume and media cards (Step 16d), then the notifications (Step 16c): under the brightness slider.
        cards?.stop()
        cards = null
        if (context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(QuickCards.PREF_KEY, true)) {
            val k = QuickCards(c, { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() }) { close(false) }
            cards = k
            val kp = LinearLayout.LayoutParams(MATCH, WRAP)
            kp.topMargin = c.dp(14)
            content.addView(k, kp)
            if (isOpen) k.start() // the panel is already showing (back from the editor)
        }
        notifs = null
        if (prefsOn()) {
            val n = QuickNotifs(c) { close(false) }
            n.bind()
            notifs = n
            val np = LinearLayout.LayoutParams(MATCH, WRAP)
            np.topMargin = c.dp(18)
            content.addView(n, np)
        }

        // A little grab bar: it says "swipe up to close".
        val bar = View(c)
        bar.background = roundRect(Look.inkA(70), c.dpf(2f))
        val barParams = LinearLayout.LayoutParams(c.dp(44), c.dp(4))
        barParams.gravity = Gravity.CENTER_HORIZONTAL
        barParams.topMargin = c.dp(16)
        barParams.bottomMargin = c.dp(2)
        content.addView(bar, barParams)

        refresh()
    }

    /** "Notifications in Quick panel" (Settings > Gestures), on by default. */
    private fun prefsOn(): Boolean = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(Notifs.PREF_KEY, true)

    /** Settings > Quick panel > Brightness slider: on top (under the clock) or at the bottom of the buttons (default). */
    private fun brightnessOnTop(): Boolean = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(KEY_BRIGHT_TOP, false)

    /** The brightness slider, [topMarginDp] below what is above it. */
    private fun addBrightness(c: Context, topMarginDp: Int) {
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
        content.addView(row, bp)
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
        time.setTextColor(Look.text)
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
        date.setTextColor(Look.textA(200))
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        date.includeFontPadding = false
        val dateLp = LinearLayout.LayoutParams(WRAP, WRAP)
        dateLp.leftMargin = c.dp(10)
        dateLp.bottomMargin = c.dp(4)
        texts.addView(date, dateLp)
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val pencil = QuickGlyph(c, QuickGlyph.PENCIL)
        pencil.tint = Look.text
        pencil.contentDescription = "Edit buttons"
        pencil.isClickable = true
        pencil.setOnClickListener { startEdit() }
        row.addView(pencil, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))

        val power = QuickGlyph(c, QuickGlyph.POWER)
        power.tint = Look.text
        power.contentDescription = "Lock screen"
        power.isClickable = true
        power.setOnClickListener { powerTap() }
        row.addView(power, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))

        val gear = QuickGlyph(c, QuickGlyph.GEAR)
        gear.tint = Look.text
        gear.contentDescription = "Settings"
        gear.isClickable = true
        gear.setOnClickListener {
            onOpenSettings()
            close(false)
        }
        row.addView(gear, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))
        return row
    }

    /** Tap and long-press of one button. */
    private fun wire(id: String, tile: TileView) {
        tile.setOnClickListener {
            tile.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            system.tap(id)
            if (id in IN_PLACE) {
                handler.postDelayed({ refresh() }, 200)
                handler.postDelayed({ refresh() }, 800)
            } else {
                close(false) // it opened a phone settings screen: Home is waiting when the person comes back
            }
        }
        tile.setOnLongClickListener {
            system.longPress(id)
            close(false)
            true
        }
    }

    // ------------------------------------------------------------------ edit buttons (Step 16b)

    /** True while the "Edit buttons" screen is showing instead of the panel. */
    val isEditing: Boolean get() = editor != null

    private fun startEdit() {
        if (editor != null) return
        content.removeAllViews()
        tiles.clear()
        slider = null
        notifs = null
        cards?.stop()
        cards = null
        val e = QuickEditor(context, { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }) { ids ->
            QuickTiles.save(context, ids)
            finishEdit()
        }
        e.load(QuickTiles.order(context).map { it.id })
        editor = e
        content.addView(e, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** Back to the panel (the buttons are read again, so a saved change shows at once). */
    private fun finishEdit() {
        editor = null
        build()
    }

    /** Back / Home: leaves the editor without saving, or else closes the panel. */
    fun back(animated: Boolean) {
        if (editor != null) finishEdit() else close(animated)
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
        if (isOpen) return
        card.animate().cancel()
        scrim.animate().cancel()
        closing = false
        editor = null
        build()
        system.start()
        system.onChanged = { refresh() }
        watchPhone()
        cards?.start()
        if (!notifsWatching) {
            notifsWatching = true
            Notifs.addListener(notifsListener)
        }
        visibility = View.VISIBLE
        // Without a working window blur the wallpaper stays sharp, so the dark veil is stronger to hide it.
        scrim.setBackgroundColor(Look.veil(if (context.windowBlurWorks()) 150 else 200))
        scrim.alpha = 0f
        card.translationY = -context.resources.displayMetrics.heightPixels.toFloat()
        scrim.animate().alpha(1f).setDuration(220).start()
        card.animate().translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
        onOpenChanged?.invoke()
    }

    fun close(animated: Boolean) {
        if (!isOpen || closing) return
        card.animate().cancel()
        scrim.animate().cancel()
        unwatchPhone()
        cards?.stop()
        if (notifsWatching) {
            notifsWatching = false
            Notifs.removeListener(notifsListener)
        }
        system.stop()
        system.onChanged = null
        handler.removeCallbacksAndMessages(null)
        if (!animated) {
            visibility = View.GONE
            onOpenChanged?.invoke()
            return
        }
        closing = true
        scrim.animate().alpha(0f).setDuration(180).start()
        card.animate().translationY(-(card.height + context.dp(40)).toFloat()).setDuration(210)
            .withEndAction {
                closing = false
                visibility = View.GONE
            }.start()
        // The blur fades while the panel slides away.
        post { onOpenChanged?.invoke() }
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
        private val glyph = QuickGlyph(context, def.glyph)
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
            glyph.show(state.glyph)
            sub.text = state.sub
            if (lastOn != on) {
                lastOn = on
                val onBg = Look.text
                val onFg = if (Look.dark) Color.rgb(24, 26, 32) else Color.WHITE
                if (wide) {
                    background = roundRect(if (on) onBg else Look.glass(110), c.dpf(31f))
                    circle.background = oval(if (on) Color.argb(34, Color.red(onFg), Color.green(onFg), Color.blue(onFg)) else Look.glass(90))
                } else {
                    circle.background = oval(if (on) onBg else Look.glass(115))
                }
                glyph.tint = if (on) onFg else Look.textA(225)
                if (wide) {
                    label.setTextColor(if (on) onFg else Look.text)
                    sub.setTextColor(if (on) Color.argb(190, Color.red(onFg), Color.green(onFg), Color.blue(onFg)) else Look.textA(170))
                } else {
                    label.setTextColor(Look.text)
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

        /** Buttons that change right there (the panel stays open); the others open a phone settings screen. */
        val IN_PLACE = setOf("sound", "torch", "rotate", "dnd")
    }
}

/** Step 16e: a scroll view that never grows taller than [maxPx] (the Quick panel scrolls inside it when it is taller than the screen). */
class CappedScrollView(context: Context) : android.widget.ScrollView(context) {
    var maxPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST))
    }
}
