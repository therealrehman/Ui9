package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Topmost layer of the launcher. It carries everything that floats above the home screen:
 *  - the lifted copy of a long-pressed icon (or widget: then the highlight is the block of cells it would cover,
 *    red when it does not fit there),
 *  - the quick menu (shortcuts / App info / Uninstall / Remove),
 *  - the drop zones at the top and the highlight of the cell you are about to drop on
 *    (it turns into a rounder, brighter "folder" highlight when you rest the icon on another icon).
 *
 * Flow: long-press -> [begin] (icon lifts, menu opens). Lift the finger: the menu stays and can be tapped.
 * Keep the finger down and move: the menu goes away and the icon follows the finger ([State.DRAGGING]).
 *
 * While a finger is down on a lifted icon, [LauncherRoot] sends every touch to [onTouch].
 */
class DragLayer(context: Context, private val host: DragHost) : FrameLayout(context) {

    private enum class State { IDLE, PRESSED, DRAGGING, SETTLING, MENU }

    private var state = State.IDLE

    /** True while a finger is down on a lifted icon: the root then routes every touch here. */
    val isTouchActive: Boolean
        get() = state == State.PRESSED || state == State.DRAGGING

    private var item: DragItem? = null
    private var source: View? = null
    private var proxy: ImageView? = null
    private var proxyWidth = 0
    private var proxyHeight = 0
    private var menu: QuickMenu? = null
    private var dragStarted = false

    private val highlight = View(context)
    private val zoneBar = LinearLayout(context)
    private val zones = ArrayList<Pair<DragAction, TextView>>()

    private var insetTop = 0
    private var insetBottom = 0
    private var pressX = 0f
    private var pressY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var hoverZone: DragAction? = null
    private var hoverCell: IntArray? = null
    private var edgeDir = 0

    /** The icon has been held over another icon long enough: dropping now makes (or fills) a folder. */
    private var mergeArmed = false

    private val handler = Handler(Looper.getMainLooper())
    private val ownPos = IntArray(2)
    private val tmpPos = IntArray(2)

    private val armMerge = Runnable {
        if (state == State.DRAGGING && hoverCell != null) {
            mergeArmed = true
            styleHighlight()
            tick()
        }
    }

    private val edgeSlide = object : Runnable {
        override fun run() {
            if (state != State.DRAGGING || edgeDir == 0) return
            if (host.slidePage(edgeDir)) {
                // The page needs a moment to slide in; re-check which cell is under the finger afterwards.
                handler.postDelayed({ if (state == State.DRAGGING) updateDrag(lastX, lastY) }, 380)
            }
            handler.postDelayed(this, 900)
        }
    }

    init {
        highlight.background = roundRect(Color.argb(70, 255, 255, 255), context.dpf(18f))
        highlight.visibility = View.GONE
        addView(highlight, FrameLayout.LayoutParams(0, 0))

        zoneBar.orientation = LinearLayout.HORIZONTAL
        zoneBar.gravity = Gravity.CENTER
        zoneBar.visibility = View.GONE
        addView(zoneBar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    private fun dp(value: Int): Int = context.dp(value)
    private fun dpf(value: Float): Float = context.dpf(value)

    /** Keeps the drop zones below the status bar and the menu clear of the navigation bar. */
    fun setInsets(top: Int, bottom: Int) {
        insetTop = top
        insetBottom = bottom
        (zoneBar.layoutParams as FrameLayout.LayoutParams).topMargin = max(top, dp(24)) + dp(8)
        zoneBar.requestLayout()
    }

    // ------------------------------------------------------------------ long-press

    /**
     * A long-press just fired on [from]: lift a copy of the icon under the finger and open the quick menu.
     * [rawX] / [rawY] are the finger's screen position.
     */
    fun begin(newItem: DragItem, from: View, rawX: Float, rawY: Float, entries: List<MenuEntry>) {
        if (state != State.IDLE) finish(null)

        item = newItem
        source = from
        pressX = rawX
        pressY = rawY
        lastX = rawX
        lastY = rawY
        state = State.PRESSED

        refreshOwnPos()
        from.getLocationOnScreen(tmpPos)
        val w = from.width
        val h = from.height
        val left = tmpPos[0] - ownPos[0]
        val top = tmpPos[1] - ownPos[1]
        grabDx = rawX - (tmpPos[0] + w / 2f)
        grabDy = rawY - (tmpPos[1] + h / 2f)

        val bitmap = snapshot(from)
        if (bitmap != null) {
            val view = ImageView(context)
            view.setImageBitmap(bitmap)
            addView(view, FrameLayout.LayoutParams(w, h))
            view.translationX = left.toFloat()
            view.translationY = top.toFloat()
            val lift = if (newItem.isWidget) WIDGET_LIFT else LIFT
            view.animate().scaleX(lift).scaleY(lift).setDuration(120).start()
            proxy = view
            proxyWidth = w
            proxyHeight = h
            from.alpha = 0f
        }

        if (entries.isNotEmpty()) showMenu(entries, Rect(left, top, left + w, top + h))
    }

    /**
     * Opens the quick menu next to [screenBounds] (screen coordinates) without lifting an icon: used for a long-press on
     * a widget or on an empty part of the home screen. A tap outside, Back or Home closes it.
     */
    fun openMenu(screenBounds: Rect, entries: List<MenuEntry>) {
        if (entries.isEmpty()) return
        if (state != State.IDLE) finish(null)
        refreshOwnPos()
        val anchor = Rect(screenBounds)
        anchor.offset(-ownPos[0], -ownPos[1])
        showMenu(entries, anchor)
        state = State.MENU
    }

    /** The finger lifted without moving: the icon drops back, the menu stays for a tap. */
    private fun releasePressed() {
        removeProxy()
        source?.alpha = 1f
        source = null
        if (menu != null) {
            state = State.MENU
        } else {
            finish(null)
        }
    }

    /** Closes whatever is showing (menu or drag). Returns true when something was. */
    fun dismiss(): Boolean {
        if (state == State.IDLE) return false
        finish(null)
        return true
    }

    // ------------------------------------------------------------------ touches

    /** Every touch of a lifted icon: wait for movement, then drag; on lift, drop. */
    fun onTouch(ev: MotionEvent) {
        when (state) {
            State.PRESSED -> when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    lastX = ev.rawX
                    lastY = ev.rawY
                    val moved = hypot(ev.rawX - pressX, ev.rawY - pressY)
                    if (moved > dp(10)) startDragging()
                }
                MotionEvent.ACTION_UP -> releasePressed()
                MotionEvent.ACTION_CANCEL -> finish(null)
            }
            State.DRAGGING -> when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> updateDrag(ev.rawX, ev.rawY)
                MotionEvent.ACTION_UP -> drop(ev.rawX, ev.rawY)
                MotionEvent.ACTION_CANCEL -> finish(null)
            }
            else -> Unit
        }
    }

    /** Menu open (finger up): a tap outside closes it and is not passed on. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (state) {
            State.MENU -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) finish(null)
                true
            }
            State.SETTLING -> true
            else -> false
        }
    }

    // ------------------------------------------------------------------ dragging

    private fun startDragging() {
        val current = item ?: return
        state = State.DRAGGING
        dragStarted = true
        removeMenu()
        host.onDragStarted(current)
        buildZones(current)
        updateDrag(lastX, lastY)
    }

    private fun updateDrag(rawX: Float, rawY: Float) {
        lastX = rawX
        lastY = rawY
        refreshOwnPos()

        proxy?.let {
            it.translationX = rawX - grabDx - proxyWidth / 2f - ownPos[0]
            it.translationY = rawY - grabDy - proxyHeight / 2f - ownPos[1]
        }

        // Drop zones at the top.
        var zone: DragAction? = null
        for ((action, view) in zones) {
            view.getLocationOnScreen(tmpPos)
            val inside = rawX >= tmpPos[0] - dp(6) && rawX <= tmpPos[0] + view.width + dp(6) &&
                rawY >= tmpPos[1] - dp(12) && rawY <= tmpPos[1] + view.height + dp(18)
            if (inside && view.width > 0) zone = action
        }
        if (zone != hoverZone) {
            hoverZone = zone
            styleZones()
            tick()
        }

        // Cell of the home grid under the finger.
        if (zone == null && host.homeReady()) {
            showCellUnder(rawX, rawY)
        } else {
            clearCell()
        }

        // Finger parked at a screen edge: after a short wait the pages slide.
        val screenWidth = resources.displayMetrics.widthPixels
        val edge = dp(34)
        val dir = when {
            zone != null || !host.homeReady() -> 0
            rawX < edge -> -1
            rawX > screenWidth - edge -> 1
            else -> 0
        }
        if (dir != edgeDir) {
            edgeDir = dir
            handler.removeCallbacks(edgeSlide)
            if (dir != 0) handler.postDelayed(edgeSlide, 500)
        }
    }

    /**
     * A widget is carried: the block of cells under the lifted picture (its top-left corner snapped to the nearest cell,
     * kept inside the page) lights up; red when the widget can not go there.
     */
    private fun showBlockUnder(carried: DragItem, rawX: Float, rawY: Float) {
        val page = host.currentPage()
        if (page == null) {
            clearCell()
            return
        }
        page.getLocationOnScreen(tmpPos)
        val origin = page.cellBounds(0, 0)
        val cw = origin.width()
        val ch = origin.height()
        if (cw <= 0 || ch <= 0) {
            clearCell()
            return
        }
        val left = rawX - grabDx - proxyWidth / 2f
        val top = rawY - grabDy - proxyHeight / 2f
        val col = ((left - tmpPos[0] - origin.left) / cw).roundToInt().coerceIn(0, max(0, page.cols - carried.spanX))
        val row = ((top - tmpPos[1] - origin.top) / ch).roundToInt().coerceIn(0, max(0, page.rows - carried.spanY))
        val target = intArrayOf(host.currentPageIndex(), col, row)
        val bounds = page.blockBounds(col, row, carried.spanX, carried.spanY)
        bounds.offset(tmpPos[0], tmpPos[1])
        if (hoverCell?.contentEquals(target) != true) {
            hoverCell = target
            val fits = host.canPlaceWidget(carried, target[0], col, row)
            highlight.background = roundRect(if (fits) HIGHLIGHT_PLAIN else HIGHLIGHT_BLOCKED, dpf(18f))
            tick()
        }
        val pad = dp(3)
        val lp = highlight.layoutParams as FrameLayout.LayoutParams
        lp.width = bounds.width() - 2 * pad
        lp.height = bounds.height() - 2 * pad
        highlight.layoutParams = lp
        highlight.translationX = (bounds.left + pad - ownPos[0]).toFloat()
        highlight.translationY = (bounds.top + pad - ownPos[1]).toFloat()
        highlight.visibility = View.VISIBLE
    }

    private fun showCellUnder(rawX: Float, rawY: Float) {
        val dragged = item
        if (dragged != null && dragged.isWidget) {
            showBlockUnder(dragged, rawX, rawY)
            return
        }
        // The dock first (its slots are cells of the page DOCK_PAGE), then the home page under the finger.
        var now: IntArray? = null
        var rect: Rect? = null
        val dock = host.dock()
        if (dock != null && dock.width > 0) {
            dock.getLocationOnScreen(tmpPos)
            val slot = dock.slotAt(rawX - tmpPos[0], rawY - tmpPos[1])
            if (slot != null) {
                now = intArrayOf(HomeLayout.DOCK_PAGE, slot, 0)
                rect = dock.slotBounds(slot).also { it.offset(tmpPos[0], tmpPos[1]) }
            }
        }
        if (now == null) {
            val page = host.currentPage()
            if (page == null) {
                clearCell()
                return
            }
            page.getLocationOnScreen(tmpPos)
            val cell = page.cellAt(rawX - tmpPos[0], rawY - tmpPos[1])
            if (cell == null) {
                clearCell()
                return
            }
            now = intArrayOf(host.currentPageIndex(), cell.first, cell.second)
            rect = page.cellBounds(cell.first, cell.second).also { it.offset(tmpPos[0], tmpPos[1]) }
        }
        val target: IntArray = now ?: return
        val bounds: Rect = rect ?: return
        if (hoverCell?.contentEquals(target) != true) {
            hoverCell = target
            handler.removeCallbacks(armMerge)
            if (mergeArmed) {
                mergeArmed = false
                styleHighlight()
            }
            val carried = item
            if (carried != null && host.canMerge(carried, target[0], target[1], target[2])) {
                handler.postDelayed(armMerge, MERGE_DWELL_MS)
            }
            tick()
        }
        val pad = dp(3)
        val lp = highlight.layoutParams as FrameLayout.LayoutParams
        lp.width = bounds.width() - 2 * pad
        lp.height = bounds.height() - 2 * pad
        highlight.layoutParams = lp
        highlight.translationX = (bounds.left + pad - ownPos[0]).toFloat()
        highlight.translationY = (bounds.top + pad - ownPos[1]).toFloat()
        highlight.visibility = View.VISIBLE
    }

    /** Where cell ([col], [row]) of [page] (or a dock slot) is on the screen, or null when it is not built. */
    private fun cellOnScreen(page: Int, col: Int, row: Int): Rect? {
        if (page == HomeLayout.DOCK_PAGE) {
            val dock = host.dock() ?: return null
            dock.getLocationOnScreen(tmpPos)
            return dock.slotBounds(col).also { it.offset(tmpPos[0], tmpPos[1]) }
        }
        val view = host.currentPage() ?: return null
        view.getLocationOnScreen(tmpPos)
        val carried = item
        val rect = if (carried != null && carried.isWidget) {
            view.blockBounds(col, row, carried.spanX, carried.spanY) // the whole block a widget covers
        } else {
            view.cellBounds(col, row)
        }
        return rect.also { it.offset(tmpPos[0], tmpPos[1]) }
    }

    private fun clearCell() {
        hoverCell = null
        handler.removeCallbacks(armMerge)
        if (mergeArmed) {
            mergeArmed = false
            styleHighlight()
        }
        highlight.visibility = View.GONE
    }

    /** Plain soft square for a normal drop; a rounder, brighter one once a folder drop is armed. */
    private fun styleHighlight() {
        highlight.background = if (mergeArmed) {
            roundRect(Color.argb(125, 255, 255, 255), dpf(30f))
        } else {
            roundRect(Color.argb(70, 255, 255, 255), dpf(18f))
        }
    }

    /** Finger lifted while dragging. */
    private fun drop(rawX: Float, rawY: Float) {
        val current = item
        if (current == null) {
            finish(null)
            return
        }
        updateDrag(rawX, rawY)
        handler.removeCallbacks(edgeSlide)

        val zone = hoverZone
        if (zone != null) {
            finish { host.onDropOnAction(current, zone) }
            return
        }

        // Released while the Apps screen was still closing: finish that now so the cells are where they look.
        if (!host.homeReady()) {
            host.settleHome()
            updateDrag(rawX, rawY)
        }

        val cell = hoverCell
        val merge = mergeArmed
        val icon = proxy
        val bounds = if (cell != null) cellOnScreen(cell[0], cell[1], cell[2]) else null
        if (cell == null || bounds == null || icon == null) {
            finish(null)
            return
        }

        // The icon glides into the middle of its cell, then the layout changes.
        refreshOwnPos()
        val targetX = bounds.centerX() - ownPos[0] - proxyWidth / 2f
        val targetY = bounds.centerY() - ownPos[1] - proxyHeight / 2f
        state = State.SETTLING
        icon.animate()
            .translationX(targetX)
            .translationY(targetY)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(130)
            .withEndAction { finish { host.onDropOnCell(current, cell[0], cell[1], cell[2], merge) } }
            .start()
    }

    // ------------------------------------------------------------------ drop zones

    private fun buildZones(current: DragItem) {
        zoneBar.removeAllViews()
        zones.clear()
        val wanted = ArrayList<Pair<DragAction, String>>()
        if (current.origin != DragOrigin.DRAWER) {
            wanted.add(Pair(DragAction.REMOVE, "Remove"))
        }
        if (current.app != null) { // a folder or a widget has no app info and nothing to uninstall
            wanted.add(Pair(DragAction.APP_INFO, "App info"))
            if (current.canUninstall) wanted.add(Pair(DragAction.UNINSTALL, "Uninstall"))
        }

        for ((action, label) in wanted) {
            val view = TextView(context)
            view.text = label
            view.setTextColor(Color.WHITE)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            view.gravity = Gravity.CENTER
            view.setPadding(dp(18), dp(11), dp(18), dp(11))
            view.background = roundRect(ZONE_IDLE, dpf(24f))
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.setMargins(dp(5), 0, dp(5), 0)
            zoneBar.addView(view, lp)
            zones.add(Pair(action, view))
        }
        zoneBar.alpha = 0f
        zoneBar.visibility = View.VISIBLE
        zoneBar.animate().alpha(1f).setDuration(140).start()
    }

    private fun styleZones() {
        for ((action, view) in zones) {
            val hot = action == hoverZone
            view.background = roundRect(if (hot) ZONE_HOT else ZONE_IDLE, dpf(24f))
            view.setTextColor(if (hot) Color.BLACK else Color.WHITE)
        }
    }

    // ------------------------------------------------------------------ menu + cleanup

    private fun showMenu(entries: List<MenuEntry>, anchor: Rect) {
        val card = QuickMenu(context, entries) { entry ->
            finish(null)
            entry.action()
        }
        card.measure(
            View.MeasureSpec.makeMeasureSpec(max(dp(210), width - dp(24)), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(max(dp(100), height), View.MeasureSpec.AT_MOST)
        )
        val menuW = card.measuredWidth
        val menuH = card.measuredHeight
        val gap = dp(10)
        val roomBelow = height - max(insetBottom, dp(12)) - anchor.bottom - gap
        val top = if (menuH <= roomBelow) {
            anchor.bottom + gap
        } else {
            max(max(insetTop, dp(24)) + dp(8), anchor.top - gap - menuH)
        }
        val left = (anchor.centerX() - menuW / 2).coerceIn(dp(12), max(dp(12), width - menuW - dp(12)))

        val lp = FrameLayout.LayoutParams(menuW, menuH)
        lp.leftMargin = left
        lp.topMargin = top
        addView(card, lp)
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
        menu = card
    }

    private fun removeMenu() {
        menu?.let {
            it.animate().cancel()
            removeView(it)
        }
        menu = null
    }

    private fun removeProxy() {
        proxy?.let {
            it.animate().cancel()
            removeView(it)
        }
        proxy = null
    }

    /**
     * Puts everything away. [afterwards] runs once the layer is clean and before the pages are rebuilt,
     * so a drop can change the layout first. If a real drag happened the home pages are rebuilt.
     */
    private fun finish(afterwards: (() -> Unit)?) {
        handler.removeCallbacksAndMessages(null)
        edgeDir = 0
        removeProxy()
        removeMenu()
        highlight.visibility = View.GONE
        zoneBar.animate().cancel()
        zoneBar.visibility = View.GONE
        zoneBar.removeAllViews()
        zones.clear()
        source?.alpha = 1f
        source = null
        hoverZone = null
        hoverCell = null
        mergeArmed = false
        styleHighlight() // back to the plain look (a widget drag may have left it red)
        val rebuild = dragStarted
        dragStarted = false
        state = State.IDLE
        item = null
        afterwards?.invoke()
        if (rebuild) host.onDragEnded()
    }

    // ------------------------------------------------------------------ helpers

    private fun refreshOwnPos() {
        getLocationOnScreen(ownPos)
    }

    private fun tick() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A picture of [view] as it looks right now (icon + label). */
    private fun snapshot(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val LIFT = 1.12f

        /** A widget is big: it lifts only a little. */
        const val WIDGET_LIFT = 1.04f
        val HIGHLIGHT_PLAIN = Color.argb(70, 255, 255, 255)
        val HIGHLIGHT_BLOCKED = Color.argb(105, 255, 80, 80)

        /** How long an icon must rest on another icon before the drop makes a folder. */
        const val MERGE_DWELL_MS = 380L
        val ZONE_IDLE = Color.argb(150, 18, 20, 28)
        val ZONE_HOT = Color.argb(235, 255, 255, 255)
    }
}
