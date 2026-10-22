package com.oklabs.oneui9launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * Step 22: the Quick panel in every app (optional). Android does not let an app replace the phone's own status-bar panel, so this does
 * the next best thing: an Accessibility service notices that the phone's panel opened, closes it at once, and shows the launcher's
 * Quick panel in an overlay window instead. Only "a window changed" events are listened to; no screen content is read.
 */
object SystemPanelLogic {

    /** The phone's status-bar app (System UI). Makers keep this name. */
    fun isSystemUi(pkg: String?): Boolean = pkg != null && pkg.lowercase().contains("systemui")

    private val skip = listOf("keyguard", "lockscreen", "volume", "power", "screenshot", "recents", "toast", "navigation", "bouncer", "assist")
    private val shade = listOf("shade", "quick settings", "quicksettings", "notificationpanel", "notification panel", "statusbarwindow", "panelview", "controlcenter", "control center")

    /**
     * True when a "window changed" event looks like the phone's notification / quick panel opening. [described] is the event's class name and
     * its short text (the panel's title, "Notification shade." on most phones). [loose]: accept any System UI window that is not obviously
     * the lock screen, volume or power menu (for phones that name their panel differently).
     */
    fun looksLikePanel(pkg: String?, described: String, loose: Boolean): Boolean {
        if (!isSystemUi(pkg)) return false
        val d = described.lowercase()
        if (skip.any { d.contains(it) }) return false
        return loose || shade.any { d.contains(it) }
    }
}

object SystemPanel {

    const val PREF_ON = "sp_on"
    const val PREF_LOOSE = "sp_loose"
    const val PREF_LAST = "sp_last"

    /** Step 24: catch the pull-down at the top edge of the screen (no flicker of the phone's own panel). On by default. */
    const val PREF_STRIP = "sp_strip"

    private fun prefs(context: Context) = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(context: Context): Boolean = prefs(context).getBoolean(PREF_ON, false)

    fun loose(context: Context): Boolean = prefs(context).getBoolean(PREF_LOOSE, false)

    fun strip(context: Context): Boolean = prefs(context).getBoolean(PREF_STRIP, true)

    fun lastSeen(context: Context): String = prefs(context).getString(PREF_LAST, "") ?: ""

    /** True when the person switched the Accessibility service on in Android settings. */
    fun serviceEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.className == PanelAccessibilityService::class.java.name }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // a phone without that screen
        }
    }

    /** Puts the top-edge strip on or takes it away after a switch in Settings changed (does nothing while the service is off). */
    fun refreshStrip() {
        PanelAccessibilityService.instance?.syncStrip()
    }

    /** Shows the panel now (the "Show panel now" test button). False when the service is not running. */
    fun showNow(): Boolean {
        val s = PanelAccessibilityService.instance ?: return false
        s.showPanel()
        return true
    }
}

/** Starts activities from the overlay: a service has no task, so every start needs NEW_TASK. */
private class OverlayContext(base: Context) : ContextWrapper(base) {
    override fun startActivity(intent: Intent) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun startActivity(intent: Intent, options: android.os.Bundle?) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
    }
}

/**
 * Step 24: a soft "frosted" copy of the screen behind the panel. The screenshot is shrunk to a few dozen pixels and blurred,
 * then stretched back by the view, so it costs next to nothing. It is never saved or sent anywhere.
 */
internal object BackdropBlur {

    private const val TARGET_DIVIDER = 14

    fun make(src: Bitmap): Bitmap {
        val w = max(1, src.width / TARGET_DIVIDER)
        val h = max(1, src.height / TARGET_DIVIDER)
        // shrink in halves first: one big step would skip pixels and look noisy
        var cur = src
        while (cur.width / 2 >= w * 2 && cur.height / 2 >= h * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val small = Bitmap.createScaledBitmap(cur, w, h, true)
        if (cur !== src && cur !== small) cur.recycle()
        val a = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        pass(a, b, w, h, 3, true)
        pass(b, a, w, h, 3, false)
        pass(a, b, w, h, 3, true)
        pass(b, a, w, h, 3, false)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        return out
    }

    /** One box-blur pass along a row (horizontal) or a column. Colours only, the result is opaque. */
    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rs = 0
                var gs = 0
                var bs = 0
                var n = 0
                for (k in -r..r) {
                    val xx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                    val yy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                    val c = src[yy * w + xx]
                    rs += (c shr 16) and 0xFF
                    gs += (c shr 8) and 0xFF
                    bs += c and 0xFF
                    n++
                }
                dst[y * w + x] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
            }
        }
    }
}

class PanelAccessibilityService : AccessibilityService() {

    /** A panel overlay that is built but maybe not on screen yet. */
    private inner class Built(val host: HostFrame, val quick: QuickPanel, val notifs: NotificationPage)

    private var frame: HostFrame? = null
    private var lastShown = 0L

    // Step 24: the invisible strip along the top edge, the panel built while the finger is still on its way down, the blurred screen behind it
    private var strip: View? = null
    private var prepared: Built? = null
    private var backdropBitmap: Bitmap? = null
    private var shotAt = 0L
    private var shotState = SHOT_NONE
    private val ui = Handler(Looper.getMainLooper())
    private val worker: Executor by lazy { Executors.newSingleThreadExecutor() }
    private var receiverOn = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) hidePanel()
            syncStrip()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Look.load(this)
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
            receiverOn = true
        } catch (e: Exception) {
            receiverOn = false
        }
        syncStrip()
    }

    override fun onInterrupt() {
        hidePanel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hidePanel()
        removeStrip()
        if (receiverOn) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                // already gone
            }
            receiverOn = false
        }
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (forcing) return // Step 26: the phone's Quick settings is open on purpose, do not swap it for our panel
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!SystemPanel.wanted(this)) {
            removeStrip()
            return
        }
        val pkg = e.packageName?.toString() ?: return
        if (frame != null) {
            // another app came to the front (a button of the panel opened Settings, say): the panel goes
            if (pkg != packageName && !SystemPanelLogic.isSystemUi(pkg)) hidePanel()
            return
        }
        if (SystemPanel.strip(this)) {
            // Step 24: the pull-down is caught at the top edge, so there is no need to watch for the phone's own panel
            // (that watching is what made the panel pop up by itself now and then). Just keep the strip in step with the lock screen.
            syncStrip()
            return
        }
        removeStrip()
        if (!SystemPanelLogic.isSystemUi(pkg)) return
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) return

        val text = e.text?.joinToString(" ") { it?.toString() ?: "" }?.take(40) ?: ""
        val described = (e.className?.toString() ?: "") + " " + text
        // what the last System UI window was called: shown in Settings, so a phone that names its panel differently can be fixed
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putString(SystemPanel.PREF_LAST, described.trim().take(80)).apply()

        if (!SystemPanelLogic.looksLikePanel(pkg, described, SystemPanel.loose(this))) return
        val now = System.currentTimeMillis()
        if (now - lastShown < 700L) return
        lastShown = now
        showPanel()
        // the phone's own panel goes away (Android 12+); older phones get a Back, which closes it on most
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) else performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // ------------------------------------------------------------------ one-tap switching (Step 26)

    @Volatile
    private var forcing = false

    private fun norm(t: CharSequence?): String {
        if (t == null) return ""
        val sb = StringBuilder()
        var space = true
        for (ch in t.toString().lowercase()) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch)
                space = false
            } else if (!space) {
                sb.append(' ')
                space = true
            }
        }
        return sb.toString().trim()
    }

    private fun startsWithWord(text: String, v: String): Boolean = text == v || text.startsWith("$v ")

    /** The windows of the phone's System UI (its Quick settings); other apps' windows only when no System UI window can be read. */
    private fun shadeRoots(): List<AccessibilityNodeInfo> {
        val all = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == packageName) continue
                all.add(r)
            }
        } catch (e: Exception) {
            // no window list on this phone
        }
        if (all.isEmpty()) rootInActiveWindow?.let { if (it.packageName?.toString() != packageName) all.add(it) }
        val sys = all.filter { SystemPanelLogic.isSystemUi(it.packageName?.toString()) }
        return if (sys.isNotEmpty()) sys else all
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (node == null || depth > 28) return
        out.add(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    private fun clickTarget(node: AccessibilityNodeInfo, minTop: Int): AccessibilityNodeInfo? {
        val screenH = resources.displayMetrics.heightPixels
        var cur: AccessibilityNodeInfo? = node
        var up = 0
        val r = Rect()
        while (cur != null && up <= 3) {
            cur.getBoundsInScreen(r)
            if (r.height() > screenH * 0.35f) return null // a big container, not a button
            if (cur.isClickable && cur.isEnabled && (r.centerY() > minTop)) return cur
            cur = cur.parent
            up++
        }
        return null
    }

    private fun findTile(roots: List<AccessibilityNodeInfo>, variants: List<String>): AccessibilityNodeInfo? {
        val minTop = (statusBarPx() * 1.3f).toInt()
        val nodes = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, nodes, 0)
        val named = nodes.filter { it.isVisibleToUser }
        for (v in variants) {
            val key = norm(v)
            for (n in named) {
                val t = norm(n.text)
                val d = norm(n.contentDescription)
                if (!(startsWithWord(t, key) || startsWithWord(d, key))) continue
                val target = clickTarget(n, minTop) ?: continue
                return target
            }
        }
        return null
    }

    private fun scrollTiles(roots: List<AccessibilityNodeInfo>): Boolean {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, nodes, 0)
        val minTop = statusBarPx()
        val r = Rect()
        for (n in nodes) {
            if (!n.isScrollable) continue
            n.getBoundsInScreen(r)
            if (r.centerY() < minTop) continue
            if (n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        }
        return false
    }

    /** After a tap that opened a small dialog instead (the "Internet" tile on stock Android): press the switch inside it. */
    private fun pressDialogSwitch(roots: List<AccessibilityNodeInfo>, variants: List<String>): Boolean {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, nodes, 0)
        for (v in variants) {
            val key = norm(v)
            if (key == "internet") continue
            for (n in nodes) {
                val cls = n.className?.toString() ?: ""
                if (!(n.isCheckable || cls.contains("Switch"))) continue
                if (!n.isVisibleToUser) continue
                val t = norm(n.text)
                val d = norm(n.contentDescription)
                if (startsWithWord(t, key) || startsWithWord(d, key)) {
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        return false
    }

    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 160L)
        }
    }

    /**
     * Opens the phone's own Quick settings, presses the button whose name is one of [variants], and closes it again. [done] gets true when the
     * button was pressed. The panel of the launcher stays where it is the whole time.
     */
    fun forceToggle(variants: List<String>, done: (Boolean) -> Unit) {
        if (forcing || variants.isEmpty()) {
            done(false)
            return
        }
        forcing = true
        val finish: (Boolean) -> Unit = { ok ->
            ui.postDelayed({
                forcing = false
                done(ok)
            }, 450L)
        }
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        var tries = 0
        var scrolls = 0
        val step = object : Runnable {
            override fun run() {
                tries++
                val roots = try {
                    shadeRoots()
                } catch (e: Exception) {
                    emptyList()
                }
                val tile = try {
                    findTile(roots, variants)
                } catch (e: Exception) {
                    null
                }
                if (tile != null) {
                    val label = norm(tile.text) + " " + norm(tile.contentDescription)
                    val ok = tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ui.postDelayed({
                        var dialog = false
                        if (label.contains("internet")) {
                            dialog = try {
                                pressDialogSwitch(shadeRoots(), variants)
                            } catch (e: Exception) {
                                false
                            }
                        }
                        ui.postDelayed({
                            if (dialog) performGlobalAction(GLOBAL_ACTION_BACK)
                            ui.postDelayed({ closeShade() }, if (dialog) 200L else 0L)
                            finish(ok)
                        }, if (dialog) 250L else 120L)
                    }, 380L)
                    return
                }
                if (tries >= 6 && tries % 4 == 2 && scrolls < 4) {
                    scrolls++
                    try {
                        scrollTiles(roots)
                    } catch (e: Exception) {
                        // ignore, keep looking
                    }
                }
                if (tries >= 30) {
                    closeShade()
                    finish(false)
                    return
                }
                ui.postDelayed(this, 130L)
            }
        }
        ui.postDelayed(step, 500L)
    }

    // ------------------------------------------------------------------ the top-edge strip (Step 24)

    private fun statusBarPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
    }

    /** The strip is there while the feature is on, the screen is on and the phone is unlocked. */
    fun syncStrip() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val usable = SystemPanel.wanted(this) && SystemPanel.strip(this) &&
            keyguard?.isKeyguardLocked != true && power?.isInteractive != false
        if (usable) addStrip() else removeStrip()
    }

    private fun addStrip() {
        if (strip != null) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = EdgeStrip(this)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, max(statusBarPx(), dp(24)),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.setTitle("OneUI9 top edge")
            strip = view
            wm.addView(view, params)
        } catch (e: Exception) {
            strip = null
        }
    }

    private fun removeStrip() {
        val view = strip ?: return
        strip = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        } catch (e: Exception) {
            // already removed
        }
        prepared = null
    }

    /**
     * An invisible, see-through bar exactly over the status bar. Because it sits above it, a pull-down that starts here never reaches the
     * phone's own panel: nothing of it ever shows. When the finger has moved down a little, our panel opens at once.
     */
    private inner class EdgeStrip(context: Context) : View(context) {
        private var downX = 0f
        private var downY = 0f
        private var fired = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    fired = false
                    if (frame == null) prepare()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!fired && frame == null) {
                        val dy = ev.rawY - downY
                        if (dy > context.dp(10) && dy > abs(ev.rawX - downX)) {
                            fired = true
                            openFromEdge(downX)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!fired) prepared = null // it was a tap: the panel built for nothing is dropped
                }
            }
            return true
        }
    }

    /** The finger just touched the edge: build the panel and take the picture of the screen now, so both are ready when the swipe is. */
    private fun prepare() {
        if (prepared == null) prepared = buildHost()
        requestBackdrop()
    }

    /** Opens the right page for the swipe (same rule as on the Home screen: Notifications / Quick panel / Split). */
    private fun openFromEdge(downX: Float) {
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        val mode = prefs.getInt("swipe_down_mode", 2).coerceIn(0, 2)
        val width = max(1, resources.displayMetrics.widthPixels)
        val toQuick = when (mode) {
            1 -> true
            0 -> false
            else -> CardLogic.splitOpensQuick(downX / width, prefs.getBoolean("qp_left", false))
        }
        if (!toQuick && !prefs.getBoolean(NotificationPage.PREF_KEY, true)) {
            // "Notification panel: Phone's panel": hand over to the phone's own notifications
            prepared = null
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            return
        }
        showPanel(startOnNotifs = !toQuick)
    }

    /** A picture of whatever is on the screen, blurred, for behind the panel (Android 11+; the window blur of the overlay is the fallback). */
    private fun canShoot(): Boolean =
        BLUR_BEHIND_PANEL && Build.VERSION.SDK_INT >= 30 &&
            ((serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0

    private fun requestBackdrop() {
        backdropBitmap = null // never show the picture of an earlier screen
        if (!canShoot()) {
            shotState = SHOT_NONE
            return
        }
        val now = System.currentTimeMillis()
        if (now - shotAt < 400L) { // the system limits how often a picture can be taken
            shotState = SHOT_NONE
            return
        }
        shotAt = now
        shotState = SHOT_PENDING
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var blurred: Bitmap? = null
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val soft = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        if (soft != null) {
                            blurred = BackdropBlur.make(soft)
                            soft.recycle()
                        }
                    } catch (e: Exception) {
                        blurred = null
                    } finally {
                        try {
                            screenshot.hardwareBuffer.close()
                        } catch (e: Exception) {
                            // already closed
                        }
                    }
                    val result = blurred
                    ui.post {
                        if (result != null) {
                            backdropBitmap = result
                            shotState = SHOT_READY
                            frame?.showBackdrop(result)
                        } else {
                            shotState = SHOT_NONE
                            frame?.let { setWindowBlur(it, true) }
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    // the phone said no (too soon, secure screen...): the overlay's own blur is used
                    ui.post {
                        shotState = SHOT_NONE
                        frame?.let { setWindowBlur(it, true) }
                    }
                }
            })
        } catch (e: Exception) {
            // the service may not take pictures (the switch was not re-allowed after an update): the overlay's own blur is used
            shotState = SHOT_NONE
        }
    }

    // ------------------------------------------------------------------ the phone's window blur (only when no picture is available)

    private fun windowBlurAllowed(host: View): Boolean =
        BLUR_BEHIND_PANEL && Build.VERSION.SDK_INT >= 31 && host.context.windowBlurWorks() &&
            getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("blur", true)

    /**
     * Blur of the apps behind the overlay by the phone itself. It is slow on many phones, so it is only the fallback for when the picture of the
     * screen is not available, and it is switched off the moment the panel starts to close (the apps clear up at once).
     */
    private fun setWindowBlur(host: HostFrame, on: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        val lp = host.layoutParams as? WindowManager.LayoutParams ?: return
        val want = on && windowBlurAllowed(host)
        if (want == (lp.blurBehindRadius > 0)) return
        if (want) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 40
        } else {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            lp.blurBehindRadius = 0
        }
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(host, lp)
        } catch (e: Exception) {
            // the window is already gone
        }
    }

    // ------------------------------------------------------------------ the overlay

    /** The panel pages inside one overlay frame; nothing is on screen until [showPanel] adds it. */
    private fun buildHost(): Built? {
        return try {
            val ctx = OverlayContext(this)
            Look.load(ctx)
            val system = QuickSystem(ctx) { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
            val quick = QuickPanel(ctx, system) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: Exception) {
                    // no Settings app
                }
            }
            val notifs = NotificationPage(ctx) {
                try {
                    ctx.startActivity(Intent("android.settings.NOTIFICATION_SETTINGS"))
                } catch (e: Exception) {
                    // no such screen
                }
            }
            val host = HostFrame(ctx, quick, notifs)
            host.addView(notifs, FrameLayout.LayoutParams(-1, -1))
            host.addView(quick, FrameLayout.LayoutParams(-1, -1))

            val own = getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(NotificationPage.PREF_KEY, true)
            quick.onSwitchPage = {
                if (own && quick.isOpen) {
                    quick.close(animated = false)
                    notifs.open()
                }
            }
            notifs.onSwitchPage = {
                if (notifs.isOpen) {
                    notifs.close(animated = false)
                    quick.open()
                }
            }
            val gone: () -> Unit = {
                // a page switch closes one and opens the other in the same moment: look again after that
                host.post { if (!quick.isOpen && !notifs.isOpen) hidePanel() }
            }
            quick.onGone = gone
            notifs.onGone = gone
            // Step 24c: the moment a page starts to close, the blurred picture fades out fast and the phone's own blur is switched off, so the apps
            // behind clear up at once (not after the whole slide-away). Looked at one moment later, so a switch between the two pages keeps the blur.
            val sync: () -> Unit = {
                host.post {
                    val on = quick.wantsBlur || notifs.wantsBlur
                    host.setBackdropVisible(on)
                    if (!on) setWindowBlur(host, false)
                }
            }
            quick.onOpenChanged = sync
            notifs.onOpenChanged = sync
            Built(host, quick, notifs)
        } catch (e: Exception) {
            null
        }
    }

    fun showPanel(startOnNotifs: Boolean = false) {
        if (frame != null) return
        val built = prepared ?: buildHost() ?: return
        prepared = null
        try {
            val ctx = built.host.context
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            frame = built.host
            wm.addView(built.host, params)
            built.host.applyFallbackInsets()
            val picture = backdropBitmap
            if (picture != null) {
                built.host.showBackdrop(picture)
            } else if (shotState == SHOT_PENDING) {
                // the picture is on its way: only if it is late does the phone's own blur step in
                ui.postDelayed({
                    if (frame === built.host && backdropBitmap == null) setWindowBlur(built.host, true)
                }, 300L)
            } else {
                setWindowBlur(built.host, true)
            }
            if (startOnNotifs) built.notifs.open() else built.quick.open()
            built.host.requestFocus()
        } catch (e: Exception) {
            frame = null
        }
    }

    fun hidePanel() {
        val host = frame ?: return
        frame = null
        backdropBitmap = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(host)
        } catch (e: Exception) {
            // already removed
        }
    }

    /** The overlay's root: Back closes the panel, and it hands the status / navigation bar sizes to the panels. */
    private inner class HostFrame(context: Context, private val quick: QuickPanel, private val notifs: NotificationPage) : FrameLayout(context) {

        /** The blurred picture of the screen behind the panel (Step 24); it fades in when it is ready. */
        private val backdrop = ImageView(context)
        private var picture: Bitmap? = null
        private var backdropWanted = true

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            backdrop.scaleType = ImageView.ScaleType.FIT_XY
            backdrop.alpha = 0f
            addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        }

        fun showBackdrop(bitmap: Bitmap) {
            picture = bitmap
            backdrop.setImageBitmap(bitmap)
            if (backdropWanted) setBackdropVisible(true)
        }

        /** Fades the blurred picture in (a little slower) or out (quickly, so the apps behind clear up at once). */
        fun setBackdropVisible(visible: Boolean) {
            backdropWanted = visible
            backdrop.animate().cancel()
            if (visible && picture == null) return
            backdrop.animate().alpha(if (visible) 1f else 0f).setDuration(if (visible) 140L else 80L).start()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    if (quick.isOpen) quick.back(animated = true) else if (notifs.isOpen) notifs.close(animated = true) else hidePanel()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        @Suppress("DEPRECATION")
        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            val top = maxOf(insets.systemWindowInsetTop, statusBarHeight())
            setBars(top, insets.systemWindowInsetBottom)
            return insets
        }

        fun applyFallbackInsets() {
            setBars(statusBarHeight(), 0)
        }

        private fun setBars(top: Int, bottom: Int) {
            quick.setInsets(top, bottom)
            notifs.setInsets(top, bottom)
        }

        private fun statusBarHeight(): Int {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) resources.getDimensionPixelSize(id) else context.dp(28)
        }
    }

    companion object {
        private const val SHOT_NONE = 0
        private const val SHOT_PENDING = 1
        private const val SHOT_READY = 2

        /**
         * Step 25: false = nothing is blurred behind the Quick panel in other apps (no picture of the screen, no window blur), only the
         * panel's own veil. The blurred picture and the live app behind it did not line up while the panel closed, so the apps showed twice.
         * Set to true to bring the old frosted backdrop back.
         */
        private const val BLUR_BEHIND_PANEL = false

        /** The running service (null when it is off); the Settings test button uses it. */
        @Volatile
        var instance: PanelAccessibilityService? = null
    }
}
