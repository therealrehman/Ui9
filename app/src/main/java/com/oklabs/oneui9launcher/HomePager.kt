package com.oklabs.oneui9launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The row of home-screen pages. Drag sideways to move between pages; a quick flick turns one page.
 * Before the first and after the last page the row resists (rubber band) and springs back.
 *
 * Only horizontal drags are taken here. Vertical swipes stay with [LauncherRoot], which opens the Apps screen.
 */
class HomePager(context: Context) : ViewGroup(context) {

    /** Called with (page, pageCount) whenever the page nearest the middle of the screen changes. */
    var onPageChanged: ((Int, Int) -> Unit)? = null

    /** Called with the finger's screen position when an empty part of a page is long-pressed (nothing under the finger took the touch). */
    var onEmptyLongPress: ((Float, Float) -> Unit)? = null

    /** Step 8a: called whenever the pages move, with how far along the row they are (0 = first page, 1 = last page). */
    var onScrolled: ((Float) -> Unit)? = null

    /** How far along the row of pages the view is (0..1); 0.5 while there is only one page. */
    fun scrollFraction(): Float {
        if (width <= 0 || childCount <= 1) return 0.5f
        return (scrollX.toFloat() / (width * (childCount - 1))).coerceIn(0f, 1f)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(scrollFraction())
    }

    /** Called when an empty part of a page is tapped twice quickly (Step 7b: double-tap to lock). */
    var onDoubleTap: (() -> Unit)? = null
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var downEventTime = 0L

    /** The page the dots show: the one nearest the middle while dragging, the target once settled. */
    var page = 0
        private set

    val pageCount: Int get() = childCount

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private var animator: ValueAnimator? = null
    private var tracker: VelocityTracker? = null
    private var lastTrackedTime = -1L
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var dragStartPage = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var longPressFired = false

    private val longPress = Runnable {
        if (!dragging) {
            longPressFired = true
            parent?.requestDisallowInterceptTouchEvent(true) // the menu is open: this touch must not turn into a swipe
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onEmptyLongPress?.invoke(downRawX, downRawY)
        }
    }

    // ------------------------------------------------------------------ pages

    /** Replaces all pages. [keepPage] stays on the current page (clamped) instead of jumping to the first. */
    fun setPages(views: List<View>, keepPage: Boolean) {
        animator?.cancel()
        removeAllViews()
        for (view in views) {
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        page = if (keepPage) page.coerceIn(0, max(0, childCount - 1)) else 0
        scrollTo(page * width, 0)
        onPageChanged?.invoke(page, pageCount)
    }

    /** Adds an empty page at the end (an icon is being dragged past the last page). The next [setPages] drops it again. */
    fun addPage(view: View) {
        addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onPageChanged?.invoke(page, pageCount)
    }

    /** Goes to page [index] (clamped), with the slide animation or instantly. */
    fun setPage(index: Int, animated: Boolean) {
        val target = index.coerceIn(0, max(0, childCount - 1))
        if (animated && width > 0) {
            animateTo(target)
        } else {
            animator?.cancel()
            finishAt(target)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = View.MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(widthSpec, heightSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, h)
        if (!dragging && animator?.isRunning != true) scrollTo(page * w, 0)
    }

    // ------------------------------------------------------------------ touch

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                lastTrackedTime = -1L
                track(ev)
                // A page is still sliding: catch it with the finger instead of tapping an icon.
                if (animator?.isRunning == true) startDrag()
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging) maybeStartDrag(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) recycleTracker()
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (tracker == null) tracker = VelocityTracker.obtain()
        track(ev)
        when (ev.actionMasked) {
            // Empty part of a page: keep the stream so a swipe sideways from there still works.
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                downRawX = ev.rawX
                downRawY = ev.rawY
                downEventTime = ev.eventTime
                longPressFired = false
                removeCallbacks(longPress)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPress)
                if (!dragging && !longPressFired) maybeStartDrag(ev)
                if (dragging) drag(ev.x - lastX)
                lastX = ev.x
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Another finger came or went: carry on from the finger that is still down, without a jump.
                val leaving = if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP) ev.actionIndex else -1
                val keep = (0 until ev.pointerCount).firstOrNull { it != leaving } ?: 0
                lastX = ev.getX(keep)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (ev.actionMasked == MotionEvent.ACTION_UP) noteTap(ev)
                if (dragging) release(ev.actionMasked == MotionEvent.ACTION_CANCEL)
                dragging = false
                recycleTracker()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    /** A quick tap that stayed put counts; two of them close together are a double-tap. */
    private fun noteTap(ev: MotionEvent) {
        val quick = ev.eventTime - downEventTime < ViewConfiguration.getLongPressTimeout()
        val still = abs(ev.x - downX) <= slop && abs(ev.y - downY) <= slop
        if (dragging || longPressFired || !quick || !still) {
            lastTapTime = 0L
            return
        }
        val near = hypot(ev.x - lastTapX, ev.y - lastTapY) < context.dp(100)
        if (lastTapTime > 0L && ev.eventTime - lastTapTime < ViewConfiguration.getDoubleTapTimeout() && near) {
            lastTapTime = 0L
            onDoubleTap?.invoke()
        } else {
            lastTapTime = ev.eventTime
            lastTapX = ev.x
            lastTapY = ev.y
        }
    }

    private fun maybeStartDrag(ev: MotionEvent) {
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
            startDrag()
            lastX = ev.x
        }
    }

    private fun startDrag() {
        dragging = true
        removeCallbacks(longPress)
        animator?.cancel()
        dragStartPage = nearestPage()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** Moves the pages with the finger; past the first / last page only part of the movement counts. */
    private fun drag(dx: Float) {
        if (width == 0) return
        val maxScroll = ((childCount - 1) * width).toFloat().coerceAtLeast(0f)
        val current = scrollX.toFloat()
        var delta = -dx
        val next = current + delta
        if (next < 0f || next > maxScroll) delta *= EDGE_RESISTANCE
        scrollTo((current + delta).roundToInt(), 0)
        trackNearest()
    }

    /** Finger lifted: a flick counts like extra dragging, but never skips more than one page. */
    private fun release(cancelled: Boolean) {
        var velocity = 0f
        tracker?.let {
            it.computeCurrentVelocity(1000, maxFling)
            velocity = it.xVelocity
        }
        val position = if (width == 0) 0f else scrollX.toFloat() / width
        val bias = (-velocity / context.dpf(FLING_DP_PER_PAGE)).coerceIn(-0.6f, 0.6f)
        var target = if (cancelled) nearestPage() else floor(position + bias + 0.5f).toInt()
        target = target.coerceIn(dragStartPage - 1, dragStartPage + 1).coerceIn(0, max(0, childCount - 1))
        animateTo(target)
    }

    // ------------------------------------------------------------------ settling

    private fun animateTo(target: Int) {
        animator?.cancel()
        val from = scrollX
        val to = target * width
        if (from == to || width == 0) {
            finishAt(target)
            return
        }
        val distance = abs(to - from).toFloat() / width
        val anim = ValueAnimator.ofInt(from, to)
        anim.duration = (170 + 150 * min(1f, distance)).toLong()
        anim.interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        anim.addUpdateListener {
            scrollTo(it.animatedValue as Int, 0)
            trackNearest()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) finishAt(target)
            }
        })
        animator = anim
        anim.start()
    }

    private fun finishAt(target: Int) {
        scrollTo(target * width, 0)
        if (target != page) {
            page = target
            onPageChanged?.invoke(page, pageCount)
        }
    }

    private fun nearestPage(): Int {
        if (width == 0 || childCount == 0) return 0
        return floor(scrollX.toFloat() / width + 0.5f).toInt().coerceIn(0, childCount - 1)
    }

    /** Keeps the page dots in step with the finger: they switch as soon as the next page is more than half in. */
    private fun trackNearest() {
        val nearest = nearestPage()
        if (nearest != page) {
            page = nearest
            onPageChanged?.invoke(page, pageCount)
        }
    }

    /** Feeds the velocity tracker once per event (the same event can reach both handlers). */
    private fun track(ev: MotionEvent) {
        if (ev.eventTime == lastTrackedTime) return
        lastTrackedTime = ev.eventTime
        tracker?.addMovement(ev)
    }

    private fun recycleTracker() {
        tracker?.recycle()
        tracker = null
    }

    private companion object {
        /** Share of a drag that still moves the pages beyond the first / last page. */
        const val EDGE_RESISTANCE = 0.4f

        /** A flick of this many dp per second counts as one whole page of extra dragging. */
        const val FLING_DP_PER_PAGE = 2400f
    }
}
