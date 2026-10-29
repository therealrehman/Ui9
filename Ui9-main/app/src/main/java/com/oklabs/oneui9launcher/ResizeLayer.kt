package com.oklabs.oneui9launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the resize frame needs from the home screen. Implemented by [LauncherActivity]. */
interface ResizeHost {
    /** The home page grid the widget is on, or null while the home screen is not built yet. */
    fun currentPage(): CellLayout?

    /** Index of the home page nearest the middle of the screen. */
    fun currentPageIndex(): Int

    /** [page, col, row, spanX, spanY] of the widget [key], or null when it is gone. */
    fun widgetCells(key: String): IntArray?

    /** True when the widget [key] can cover the block of [spanX] x [spanY] cells whose top-left cell is ([col], [row]). */
    fun canResize(key: String, col: Int, row: Int, spanX: Int, spanY: Int): Boolean

    /** The frame was let go on a new size that fits: change the layout, save it and rebuild the pages. */
    fun onResized(key: String, col: Int, row: Int, spanX: Int, spanY: Int)

    /** The frame was let go on a size that does not fit (another widget, no room for the apps): tell the person. */
    fun onResizeBlocked()
}

/**
 * Resize mode for one widget: a rounded frame around the widget's block of cells with a handle on every side the
 * widget can be resized on. Drag a handle: the edge follows the finger, a soft block shows the cells the widget will
 * cover (it snaps to whole cells; red when it does not fit there). Let go and the widget takes the new size; the frame
 * stays so it can be resized again. A tap outside the frame, Back or Home ends resize mode.
 *
 * It sits above the home screen and takes every touch while it is active, so no page swipe, Apps screen or widget
 * reacts underneath.
 */
class ResizeLayer(context: Context, private val host: ResizeHost) : View(context) {

    private var key: String? = null
    private var limits = ResizeLimits(1, 1, 1, 1)

    // The widget's block as last saved.
    private var cCol = 0
    private var cRow = 0
    private var cSX = 1
    private var cSY = 1

    // The page grid in this view's coordinates.
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var gridCols = HomeStore.COLS
    private var gridRows = HomeStore.ROWS

    /** The frame that is drawn: sits on the block, and one edge follows the finger while a handle is dragged. */
    private val frame = RectF()

    /** The block the widget will take when the finger lets go (snapped to cells). */
    private val preview = RectF()
    private var previewCells = IntArray(4)
    private var previewFits = true

    private var handle = NONE
    private var downOutside = false
    private var downX = 0f
    private var downY = 0f
    private var animator: ValueAnimator? = null

    private val ownPos = IntArray(2)
    private val tmpPos = IntArray(2)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val gap = context.dpf(4f)
    private val corner = context.dpf(20f)

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2f)
        color = Color.argb(240, 255, 255, 255)
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 0, 0, 0)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    init {
        visibility = GONE
    }

    /** True from [start] until the frame is closed. */
    val isActive: Boolean get() = key != null

    // ------------------------------------------------------------------ open / close

    /** Shows the frame around the widget [widgetKey]. [newLimits] says how many cells wide / tall it may become. */
    fun start(widgetKey: String, newLimits: ResizeLimits) {
        animator?.cancel()
        key = widgetKey
        limits = newLimits
        handle = NONE
        visibility = VISIBLE
        if (!sync()) {
            close(animated = false)
            return
        }
        animate().cancel()
        alpha = 0f
        animate().alpha(1f).setDuration(140).start()
    }

    /** Ends resize mode. */
    fun close(animated: Boolean = true) {
        if (key == null && visibility != VISIBLE) return
        animator?.cancel()
        animator = null
        handle = NONE
        key = null
        animate().cancel()
        if (animated && isAttachedToWindow) {
            animate().alpha(0f).setDuration(120).withEndAction { if (key == null) visibility = GONE }.start()
        } else {
            alpha = 0f
            visibility = GONE
        }
    }

    /** The home pages were rebuilt (a resize was applied, apps were installed...): follow the widget to where it is now. */
    fun onHomeRebuilt() {
        if (key == null) return
        val tree = viewTreeObserver
        if (!tree.isAlive) {
            if (!sync()) close(animated = false)
            return
        }
        // The new pages are laid out a moment later; look again right after that, before anything is drawn.
        tree.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val current = viewTreeObserver
                if (current.isAlive) current.removeOnPreDrawListener(this)
                if (key != null && !sync()) close(animated = false)
                return true
            }
        })
        invalidate()
    }

    /** Reads where the widget and the page grid are now. False when the widget is gone or not on the page in front. */
    private fun sync(): Boolean {
        val k = key ?: return false
        val cells = host.widgetCells(k) ?: return false
        if (cells[0] != host.currentPageIndex()) return false
        val page = host.currentPage() ?: return false
        if (page.width <= 0 || page.height <= 0) return false
        getLocationOnScreen(ownPos)
        page.getLocationOnScreen(tmpPos)
        val origin = page.cellBounds(0, 0)
        gridLeft = (tmpPos[0] - ownPos[0] + origin.left).toFloat()
        gridTop = (tmpPos[1] - ownPos[1] + origin.top).toFloat()
        cellW = origin.width().toFloat()
        cellH = origin.height().toFloat()
        if (cellW <= 0f || cellH <= 0f) return false
        gridCols = page.cols
        gridRows = page.rows
        cCol = cells[1]
        cRow = cells[2]
        cSX = cells[3]
        cSY = cells[4]
        animator?.cancel()
        blockRect(cCol, cRow, cSX, cSY, frame)
        invalidate()
        return true
    }

    /** The frame of a block of cells: the cells' outline minus the small gap widgets keep around them. */
    private fun blockRect(col: Int, row: Int, spanX: Int, spanY: Int, out: RectF) {
        out.set(
            gridLeft + col * cellW + gap,
            gridTop + row * cellH + gap,
            gridLeft + (col + spanX) * cellW - gap,
            gridTop + (row + spanY) * cellH - gap
        )
    }

    // ------------------------------------------------------------------ touches

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (key == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                downX = event.x
                downY = event.y
                handle = hitHandle(event.x, event.y)
                downOutside = false
                if (handle != NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    blockRect(cCol, cRow, cSX, cSY, frame)
                    previewCells = intArrayOf(cCol, cRow, cSX, cSY)
                    previewFits = true
                    blockRect(cCol, cRow, cSX, cSY, preview)
                    invalidate()
                } else {
                    val reach = context.dpf(22f)
                    val inside = event.x >= frame.left - reach && event.x <= frame.right + reach &&
                        event.y >= frame.top - reach && event.y <= frame.bottom + reach
                    downOutside = !inside
                }
            }
            MotionEvent.ACTION_MOVE -> if (handle != NONE) dragHandle(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (handle != NONE) {
                    releaseHandle(canceled = false)
                } else if (downOutside && hypot(event.x - downX, event.y - downY) <= slop) {
                    close() // a tap outside the frame ends resize mode
                }
            }
            MotionEvent.ACTION_CANCEL -> if (handle != NONE) releaseHandle(canceled = true)
        }
        return true
    }

    /** The handle under ([x], [y]) or [NONE]. The touch area is a lot bigger than the dot you see. */
    private fun hitHandle(x: Float, y: Float): Int {
        val reach = context.dpf(32f)
        var best = NONE
        var bestDistance = reach
        for (h in HANDLES) {
            if (!allowed(h)) continue
            val distance = hypot(x - handleX(h), y - handleY(h))
            if (distance <= bestDistance) {
                bestDistance = distance
                best = h
            }
        }
        return best
    }

    private fun allowed(h: Int): Boolean =
        if (h == LEFT || h == RIGHT) limits.canX else limits.canY

    private fun handleX(h: Int): Float = when (h) {
        LEFT -> frame.left
        RIGHT -> frame.right
        else -> frame.centerX()
    }

    private fun handleY(h: Int): Float = when (h) {
        TOP -> frame.top
        BOTTOM -> frame.bottom
        else -> frame.centerY()
    }

    /** One edge follows the finger (inside what the widget allows); the block it would snap to is worked out from that edge. */
    private fun dragHandle(x: Float, y: Float) {
        val c0 = cCol
        val c1 = cCol + cSX
        val r0 = cRow
        val r1 = cRow + cSY
        var n0 = c0
        var n1 = c1
        var m0 = r0
        var m1 = r1
        blockRect(c0, r0, cSX, cSY, frame)

        when (handle) {
            LEFT -> {
                val lowest = max(0, c1 - limits.maxX)
                val highest = c1 - limits.minX
                frame.left = x.coerceIn(gridLeft + lowest * cellW + gap, gridLeft + highest * cellW + gap)
                n0 = ((frame.left - gap - gridLeft) / cellW).roundToInt().coerceIn(lowest, highest)
            }
            RIGHT -> {
                val lowest = c0 + limits.minX
                val highest = min(gridCols, c0 + limits.maxX)
                frame.right = x.coerceIn(gridLeft + lowest * cellW - gap, gridLeft + highest * cellW - gap)
                n1 = ((frame.right + gap - gridLeft) / cellW).roundToInt().coerceIn(lowest, highest)
            }
            TOP -> {
                val lowest = max(0, r1 - limits.maxY)
                val highest = r1 - limits.minY
                frame.top = y.coerceIn(gridTop + lowest * cellH + gap, gridTop + highest * cellH + gap)
                m0 = ((frame.top - gap - gridTop) / cellH).roundToInt().coerceIn(lowest, highest)
            }
            BOTTOM -> {
                val lowest = r0 + limits.minY
                val highest = min(gridRows, r0 + limits.maxY)
                frame.bottom = y.coerceIn(gridTop + lowest * cellH - gap, gridTop + highest * cellH - gap)
                m1 = ((frame.bottom + gap - gridTop) / cellH).roundToInt().coerceIn(lowest, highest)
            }
        }

        val next = intArrayOf(n0, m0, n1 - n0, m1 - m0)
        if (!next.contentEquals(previewCells)) {
            previewCells = next
            val unchanged = next[0] == cCol && next[1] == cRow && next[2] == cSX && next[3] == cSY
            previewFits = unchanged || host.canResize(key ?: return, next[0], next[1], next[2], next[3])
            blockRect(next[0], next[1], next[2], next[3], preview)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
    }

    /** Finger lifted: the frame glides into the snapped block, then the widget takes that size. */
    private fun releaseHandle(canceled: Boolean) {
        handle = NONE
        val k = key ?: return
        val changed = previewCells[0] != cCol || previewCells[1] != cRow || previewCells[2] != cSX || previewCells[3] != cSY
        if (canceled || !changed) {
            glideFrameTo(blockRectOf(cCol, cRow, cSX, cSY), null)
            return
        }
        if (!previewFits) {
            host.onResizeBlocked()
            glideFrameTo(blockRectOf(cCol, cRow, cSX, cSY), null)
            return
        }
        val target = RectF(preview)
        val cells = previewCells.copyOf()
        glideFrameTo(target) {
            if (key == k) host.onResized(k, cells[0], cells[1], cells[2], cells[3])
        }
    }

    private fun blockRectOf(col: Int, row: Int, spanX: Int, spanY: Int): RectF {
        val out = RectF()
        blockRect(col, row, spanX, spanY, out)
        return out
    }

    private fun glideFrameTo(target: RectF, then: (() -> Unit)?) {
        animator?.cancel()
        val from = RectF(frame)
        val to = RectF(target)
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 110
        anim.addUpdateListener {
            val f = it.animatedFraction
            frame.set(
                from.left + (to.left - from.left) * f,
                from.top + (to.top - from.top) * f,
                from.right + (to.right - from.right) * f,
                from.bottom + (to.bottom - from.bottom) * f
            )
            invalidate()
        }
        var cancelled = false
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                frame.set(to)
                invalidate()
                then?.invoke()
            }
        })
        animator = anim
        anim.start()
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        if (key == null) return
        if (handle != NONE) {
            previewPaint.color = if (previewFits) PREVIEW_FITS else PREVIEW_BLOCKED
            canvas.drawRoundRect(preview, corner, corner, previewPaint)
        }
        canvas.drawRoundRect(frame, corner, corner, framePaint)
        for (h in HANDLES) {
            if (!allowed(h)) continue
            val radius = context.dpf(if (h == handle) 9f else 7f)
            val x = handleX(h)
            val y = handleY(h)
            canvas.drawCircle(x, y, radius + context.dpf(2.5f), ringPaint)
            canvas.drawCircle(x, y, radius, dotPaint)
        }
    }

    private companion object {
        const val NONE = -1
        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
        val HANDLES = intArrayOf(LEFT, TOP, RIGHT, BOTTOM)
        val PREVIEW_FITS = Color.argb(60, 255, 255, 255)
        val PREVIEW_BLOCKED = Color.argb(105, 255, 80, 80)
    }
}
