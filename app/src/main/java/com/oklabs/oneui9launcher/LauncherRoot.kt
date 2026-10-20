package com.oklabs.oneui9launcher

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Root of the launcher. It owns the vertical swipe that opens and closes the Apps screen:
 *  - swipe up anywhere on the home screen (dock icons included) opens it,
 *  - swipe down on the Apps screen closes it, but only when the list is already at the top.
 * The finger drives [AppsScreen.progress] directly; on release the drawer settles open or closed.
 */
class LauncherRoot(context: Context) : FrameLayout(context) {

    /** Set by the activity right after it creates the Apps screen. */
    lateinit var apps: AppsScreen

    /** Set by the activity right after it creates the drag layer (icons carried over the home screen). */
    lateinit var drag: DragLayer

    /** Set by the activity right after it creates the folder layer (the open-folder card). */
    lateinit var folders: FolderLayer

    /** Set by the activity right after it creates the widget picker (the sheet that lists all widgets). */
    lateinit var picker: WidgetPicker

    /** Set by the activity right after it creates the Edge panel (Step 9b). While it is open it owns the screen. */
    lateinit var edge: EdgePanel

    /** Set by the activity right after it creates the resize frame (resize mode for a widget). */
    lateinit var resize: ResizeLayer

    /** Screen position of the latest touch. A long-press does not say where the finger is, so the activity asks here. */
    var lastRawX = 0f
        private set
    var lastRawY = 0f
        private set

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startY = 0f
    private var startProgress = 0f
    private var dragging = false

    /** Set by the activity: true when a swipe down on the Home screen should open the notification panel. */
    var swipeDownEnabled: () -> Boolean = { false }

    /** Set by the activity: the swipe down happened (once per touch). */
    var onSwipeDown: (() -> Unit)? = null
    private var swipeDown = false

    /** Set by the activity: true while the edit screen is showing (no swipe to the Apps screen then). */
    var editMode: () -> Boolean = { false }

    /** Set by the activity: true when two fingers moving together may open the edit screen. */
    var pinchEnabled: () -> Boolean = { false }

    /** Set by the activity: two fingers pinched in (once per touch). */
    var onPinchIn: (() -> Unit)? = null

    private var pinchFired = false
    private var pinchScale = 1f
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinchScale = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pinchScale *= detector.scaleFactor
            if (pinchScale < 0.82f && !pinchFired) {
                pinchFired = true
                onPinchIn?.invoke()
            }
            return true
        }
    })

    private var tracker: VelocityTracker? = null
    private var lastTrackedTime = -1L

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastRawX = ev.rawX
        lastRawY = ev.rawY
        // Everything after a pinch is swallowed until the fingers are up.
        if (pinchFired) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) pinchFired = false
            return true
        }
        if (pinchEnabled()) {
            pinch.onTouchEvent(ev)
            if (pinchFired) {
                // The icon / widget under the fingers must not think it was touched.
                val cancel = MotionEvent.obtain(ev)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // A lifted icon owns the touch: the view that was long-pressed gets a cancel, the drag layer takes over.
        if (drag.isTouchActive) {
            drag.onTouch(ev)
            return true
        }
        // Resize mode owns the screen too: the frame takes the touches, no page swipe or Apps screen behind it.
        if (::resize.isInitialized && resize.isActive) return false
        // An open folder or the widget picker owns the screen: no swipe to the Apps screen behind it.
        if (folders.isOpen || editMode()) return false
        if (::picker.isInitialized && picker.isOpen) return false
        if (::edge.isInitialized && edge.isOpen) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                swipeDown = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                lastTrackedTime = -1L
                track(ev)
                // Grabbing the drawer while it is still moving: take over at once, no taps.
                if (apps.isAnimating) startDrag(ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging) maybeStartDrag(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) recycleTracker()
            }
        }
        return dragging || swipeDown
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (drag.isTouchActive) {
            drag.onTouch(ev)
            return true
        }
        track(ev)
        when (ev.actionMasked) {
            // Nobody underneath wanted this touch (empty part of the home screen).
            // Keep the stream so a swipe up from there still works.
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) maybeStartDrag(ev)
                if (dragging) drag(ev)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) release(ev.actionMasked == MotionEvent.ACTION_CANCEL)
                dragging = false
                swipeDown = false
                recycleTracker()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun maybeStartDrag(ev: MotionEvent) {
        val dy = ev.y - downY
        val dx = ev.x - downX
        // Step 13: with the Apps screen open, a slanted swipe belongs to the page swipe, so closing needs a clearly vertical move.
        val ratio = if (apps.progress > 0f) 1.8f else 1.2f
        if (abs(dy) <= slop || abs(dy) <= abs(dx) * ratio) return
        if (dy > 0f && apps.progress == 0f) {
            // Swipe down on the Home screen itself: the notification panel (once per touch).
            if (!swipeDown && dy > context.dp(40) && swipeDownEnabled()) {
                swipeDown = true
                onSwipeDown?.invoke()
            }
            return
        }
        val opening = dy < 0f && apps.progress < 1f
        val closing = dy > 0f && apps.progress > 0f && apps.canStartCloseDrag()
        if (opening || closing) startDrag(ev.y)
    }

    private fun startDrag(y: Float) {
        dragging = true
        startY = y
        startProgress = apps.progress
        apps.beginDrag()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun drag(ev: MotionEvent) {
        apps.setProgress(startProgress - (ev.y - startY) / apps.travelPx)
    }

    /** Finger lifted: a fast fling decides, otherwise how far the drawer got. */
    private fun release(cancelled: Boolean) {
        var velocityY = 0f
        tracker?.let {
            it.computeCurrentVelocity(1000)
            velocityY = it.yVelocity
        }
        val fling = context.dpf(700f)
        val p = apps.progress
        val target = when {
            cancelled -> if (startProgress > 0.5f) 1f else 0f
            velocityY < -fling -> 1f
            velocityY > fling -> 0f
            startProgress < 0.5f -> if (p > 0.3f) 1f else 0f
            else -> if (p > 0.7f) 1f else 0f
        }
        apps.animateTo(target)
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
}
