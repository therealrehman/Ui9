package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Locale

/**
 * Steps 21b + 21c: the launcher's own lock screen: wallpaper, big clock, date, the music card and notification cards (the Quick
 * panel's own), the phone's Phone and Camera apps, and swipe up to unlock. Drawn above the phone's lock screen; swiping up
 * asks Android to unlock (PIN / fingerprint if the phone has one).
 */
class LockScreenActivity : Activity() {

    private lateinit var content: View
    private var unlocking = false
    private var player: QuickPlayer? = null
    private var notifs: QuickNotifs? = null
    private var watching = false
    private val notifsListener: () -> Unit = { notifs?.bind() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        Look.load(this)
        Look.forceDark = true
        blurWallpaper()
        setContentView(build())
    }

    override fun onStart() {
        super.onStart()
        player?.start()
        notifs?.bind()
        if (!watching) {
            watching = true
            Notifs.addListener(notifsListener)
        }
    }

    override fun onStop() {
        player?.stop()
        if (watching) {
            watching = false
            Notifs.removeListener(notifsListener)
        }
        super.onStop()
    }

    override fun onDestroy() {
        Look.forceDark = false
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        unlocking = false
        content.translationY = 0f
        content.alpha = 1f
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // the lock screen has no back
    }

    private fun blurWallpaper() {
        val wanted = getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("blur", true)
        if (Build.VERSION.SDK_INT >= 31 && wanted && windowBlurWorks()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = window.attributes
            params.blurBehindRadius = 50
            window.attributes = params
        }
    }

    // ------------------------------------------------------------------ screen

    private fun build(): View {
        val root = SwipeFrame(this) { unlock(null) }
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(95, 60, 30, 130), Color.argb(70, 30, 20, 80), Color.argb(150, 20, 10, 60))
        )

        // one column: top (padlock, clock, date), middle (music + notifications), bottom (hint, Phone, Camera)
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        content = column
        root.slide = column
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER_HORIZONTAL
        val padlock = PadlockView(this)
        top.addView(padlock, LinearLayout.LayoutParams(dp(18), dp(22)))

        val clock = TextClock(this)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 80f)
        clock.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        clock.setTextColor(Color.argb(205, 255, 255, 255))
        clock.includeFontPadding = false
        clock.gravity = Gravity.CENTER
        clock.setShadowLayer(dpf(10f), 0f, dpf(2f), Color.argb(70, 0, 0, 0))
        top.addView(clock, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(30) })

        val date = TextClock(this)
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMMd")
        date.format12Hour = pattern
        date.format24Hour = pattern
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        date.setTextColor(Color.argb(190, 255, 255, 255))
        date.gravity = Gravity.CENTER
        top.addView(date, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(6) })
        column.addView(top, LinearLayout.LayoutParams(-1, -2))

        // middle: the music card and the notification cards (the same ones as in the Quick panel)
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        val middle = LinearLayout(this)
        middle.orientation = LinearLayout.VERTICAL
        middle.setPadding(dp(16), dp(18), dp(16), dp(8))
        if (prefs.getBoolean(QuickCards.PREF_KEY, true)) {
            val p = QuickPlayer(this) { unlock(null) }
            player = p
            middle.addView(p, LinearLayout.LayoutParams(-1, -2))
        }
        if (prefs.getBoolean(Notifs.PREF_KEY, true)) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(10), dp(10), dp(10), dp(10))
            box.background = GradientDrawable().apply {
                setColor(Look.glass(70))
                cornerRadius = dpf(26f)
                setStroke(dp(1), Look.inkA(22))
            }
            val n = QuickNotifs(this, false, true) { unlock(null) }
            n.bind()
            notifs = n
            box.addView(n, LinearLayout.LayoutParams(-1, -1))
            middle.addView(box, LinearLayout.LayoutParams(-1, 0, 1f).also { it.topMargin = dp(if (player != null) 12 else 0) })
        }
        root.keepTouch = { x, y -> notifs?.scrollsAtRaw(x, y) == true }
        column.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))

        // bottom: hint, Phone and Camera (the phone's own apps, with their own icons)
        val bottom = FrameLayout(this)
        val hint = TextView(this)
        hint.text = "Swipe up to unlock"
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        hint.setTextColor(Color.argb(210, 255, 255, 255))
        hint.gravity = Gravity.CENTER
        bottom.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val dial = Intent(Intent.ACTION_DIAL)
        val cam = Intent("android.media.action.STILL_IMAGE_CAMERA")
        val phone = shortcut(ownIcon(dial), android.R.drawable.ic_menu_call) { unlock { startOther(dial) } }
        val camera = shortcut(ownIcon(cam), android.R.drawable.ic_menu_camera) { unlock { startOther(cam) } }
        bottom.addView(phone, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.START or Gravity.BOTTOM).also { it.marginStart = dp(34) })
        bottom.addView(camera, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END or Gravity.BOTTOM).also { it.marginEnd = dp(34) })
        column.addView(bottom, LinearLayout.LayoutParams(-1, dp(120)))

        // keep everything clear of the status and navigation bars
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val topInset = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottomInset = insets.systemWindowInsetBottom
            top.setPadding(0, topInset + dp(40), 0, 0)
            bottom.setPadding(0, 0, 0, bottomInset + dp(22))
            (bottom.layoutParams as LinearLayout.LayoutParams).height = dp(120) + bottomInset
            bottom.requestLayout()
            insets
        }
        return root
    }

    /** The icon of the phone's own app that handles [intent] (null when there is none). */
    private fun ownIcon(intent: Intent): Drawable? = try {
        packageManager.resolveActivity(intent, 0)?.loadIcon(packageManager)
    } catch (e: Exception) {
        null
    }

    private fun shortcut(own: Drawable?, fallback: Int, onTap: () -> Unit): View {
        val button = ImageView(this)
        if (own != null) {
            button.setImageDrawable(own)
            button.setPadding(dp(8), dp(8), dp(8), dp(8))
            button.background = roundRect(Color.argb(60, 255, 255, 255), dpf(27f))
        } else {
            button.setImageResource(fallback)
            button.setColorFilter(Color.WHITE)
            button.setPadding(dp(15), dp(15), dp(15), dp(15))
            button.background = roundRect(Color.rgb(84, 52, 196), dpf(27f))
        }
        button.scaleType = ImageView.ScaleType.CENTER_INSIDE
        button.setOnClickListener { onTap() }
        return button
    }

    // ------------------------------------------------------------------ unlock

    /** Asks Android to unlock; when that works runs [then] (opens Phone / Camera) and closes this screen. */
    private fun unlock(then: (() -> Unit)?) {
        if (unlocking) return
        unlocking = true
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isKeyguardLocked) {
            done(then)
            return
        }
        try {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    done(then)
                }

                override fun onDismissCancelled() {
                    snapBack()
                }

                override fun onDismissError() {
                    snapBack()
                }
            })
        } catch (e: Exception) {
            snapBack()
        }
    }

    private fun done(then: (() -> Unit)?) {
        then?.invoke()
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    private fun snapBack() {
        unlocking = false
        content.animate().translationY(0f).alpha(1f).setDuration(200).start()
    }

    private fun startOther(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // no such app on this phone
        }
    }
}

/** A frame that follows an upward drag with [slide] and calls [onUnlock] when it was pulled far enough (or flicked). */
private class SwipeFrame(context: Context, private val onUnlock: () -> Unit) : FrameLayout(context) {

    var slide: View? = null

    /** True where a vertical drag belongs to a scrolling list (the notifications), not to swipe-up-to-unlock. */
    var keepTouch: ((Float, Float) -> Boolean)? = null
    private var ignore = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var downX = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.rawY
                downX = e.rawX
                dragging = false
                ignore = keepTouch?.invoke(e.rawX, e.rawY) == true
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dy = downY - e.rawY
                if (!ignore && dy > slop && dy > Math.abs(downX - e.rawX)) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = (downY - e.rawY).coerceAtLeast(0f)
                slide?.translationY = -dy * 0.85f
                slide?.alpha = (1f - dy / (height * 0.7f)).coerceIn(0.2f, 1f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = downY - e.rawY
                tracker?.computeCurrentVelocity(1000)
                val flick = (tracker?.yVelocity ?: 0f) < -1400f
                tracker?.recycle()
                tracker = null
                if (e.actionMasked == MotionEvent.ACTION_UP && dragging && (dy > height * 0.18f || flick)) {
                    onUnlock()
                } else {
                    slide?.animate()?.translationY(0f)?.alpha(1f)?.setDuration(200)?.start()
                }
                dragging = false
                return true
            }
        }
        return dragging
    }
}

/** A small padlock drawn from shapes. */
private class PadlockView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = Color.argb(225, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.13f
        canvas.drawArc(RectF(w * 0.22f, h * 0.04f, w * 0.78f, h * 0.7f), 180f, 180f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(w * 0.06f, h * 0.42f, w * 0.94f, h * 0.98f), w * 0.16f, w * 0.16f, paint)
    }
}
