package com.oklabs.oneui9launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.graphics.PointF
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

    /** Step 33: the strip is added / removed a moment after the last change, never in the middle of the unlock animation. */
    private val stripSync = Runnable { syncStrip() }

    private fun syncStripSoon(delayMs: Long) {
        ui.removeCallbacks(stripSync)
        ui.postDelayed(stripSync, delayMs)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    hidePanel()
                    ui.removeCallbacks(stripSync)
                    removeStrip()
                } else {
                    syncStripSoon(700L) // screen on / unlocked: wait until the lock screen has gone
                }
            } catch (e: Exception) {
                // Step 35: an error here would end the whole app process, and the phone then stops this service for good
            }
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
        coverOff()
        forcing = false
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
        // Step 35: nothing may throw out of here: a crash ends the app's process and the phone then leaves this service stopped.
        try {
            handleEvent(event)
        } catch (e: Exception) {
            // ignored on purpose
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
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
            syncStripSoon(350L)
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
    private var forcingSince = 0L

    /** A press that never finished (an error halfway) must not block every later tap: after 9 seconds it counts as over. */
    private fun stillForcing(): Boolean {
        if (!forcing) return false
        if (android.os.SystemClock.uptimeMillis() - forcingSince < 9000L) return true
        coverOff()
        forcing = false
        return false
    }

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

    /**
     * The windows of the phone's panel: every system window (type "system": status bar, notification shade, control center), whatever its
     * app name is (some phones keep the control center outside "systemui"). Other apps' windows only when no system window can be read.
     */
    private fun shadeRoots(): List<AccessibilityNodeInfo> {
        val system = ArrayList<AccessibilityNodeInfo>()
        val other = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == packageName) continue
                if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM || SystemPanelLogic.isSystemUi(r.packageName?.toString())) {
                    system.add(r)
                } else {
                    other.add(r)
                }
            }
        } catch (e: Exception) {
            // no window list on this phone
        }
        if (system.isNotEmpty()) return system
        if (other.isNotEmpty()) return other
        val active = rootInActiveWindow
        return if (active != null && active.packageName?.toString() != packageName) listOf(active) else emptyList()
    }

    /** A real swipe down from the top-right corner: opens the control center / quick settings on phones where the global action does not. */
    private fun swipeDownFromTop(xFraction: Float) {
        if (Build.VERSION.SDK_INT < 24) return
        try {
            val m = resources.displayMetrics
            val path = Path()
            path.moveTo(m.widthPixels * xFraction, 6f)
            path.lineTo(m.widthPixels * xFraction, m.heightPixels * 0.55f)
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 240L)).build(), null, null)
        } catch (e: Exception) {
            // not possible on this phone
        }
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

    /**
     * A found button. [clickable]: a view that takes a click. Otherwise only its name (or a tile that is not marked clickable) was found and a real
     * finger-tap is sent at ([x], [y]).
     */
    private class Hit(val node: AccessibilityNodeInfo, val clickable: Boolean, val x: Float = 0f, val y: Float = 0f)

    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels

    /** Where to press for a name: the middle of a view as big as a whole button, or just above a line of text (the icon sits there). */
    private fun tapPointFor(n: AccessibilityNodeInfo): PointF {
        val r = Rect()
        n.getBoundsInScreen(r)
        return if (r.height() >= screenW() * 0.1f) PointF(r.exactCenterX(), r.exactCenterY())
        else PointF(r.exactCenterX(), r.top - screenW() * 0.08f)
    }

    /** A clickable view next to a label: the label's own cell, or a clickable child of the same small cell (icon and name as siblings). */
    private fun clickableNear(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val r = Rect()
        var cell: AccessibilityNodeInfo? = n
        var up = 0
        while (cell != null && up <= 3) {
            cell.getBoundsInScreen(r)
            if (r.height() > screenH() * 0.2f || r.width() > screenW() * 0.5f) return null
            if (cell.isClickable && cell.isEnabled) return cell
            val list = ArrayList<AccessibilityNodeInfo>()
            collect(cell, list, 0)
            var best: AccessibilityNodeInfo? = null
            var bestArea = Int.MAX_VALUE
            for (c in list) {
                if (c === cell || !c.isClickable || !c.isEnabled) continue
                c.getBoundsInScreen(r)
                val area = r.width() * r.height()
                if (area > 0 && area < bestArea) {
                    best = c
                    bestArea = area
                }
            }
            if (best != null) return best
            cell = cell.parent
            up++
        }
        return null
    }

    /**
     * level 0: strict (visible, clickable cell). level 1: also views the phone marks as hidden / not important, siblings of the label.
     * level 2: the name alone (a finger-tap is sent at its place), for phones whose buttons are not marked clickable at all.
     */
    private fun findTile(roots: List<AccessibilityNodeInfo>, variants: List<String>, level: Int): Hit? {
        val minTop = (statusBarPx() * 1.3f).toInt()
        val nodes = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, nodes, 0)
        val named = if (level == 0) nodes.filter { it.isVisibleToUser } else nodes
        val b = Rect()
        for (v in variants) {
            if (v.startsWith("@wide")) {
                val idx = v.removePrefix("@wide").toIntOrNull() ?: continue
                val w = wideTiles(named, minTop).getOrNull(idx) ?: continue
                val icon = iconOf(w)
                if (icon != null) return Hit(icon, true)
                w.getBoundsInScreen(b)
                return Hit(w, false, b.left + b.width() * 0.2f, b.exactCenterY()) // the round icon of a wide button sits at its left
            }
            val key = norm(v)
            for (n in named) {
                val t = norm(n.text)
                val d = norm(n.contentDescription)
                if (!(startsWithWord(t, key) || startsWithWord(d, key))) continue
                n.getBoundsInScreen(b)
                if (b.width() <= 0 || b.height() <= 0) continue
                if (b.centerY() < minTop) continue // an icon in the status bar, not a button
                if (level == 0) {
                    val target = clickTarget(n, minTop)
                    if (target != null) return Hit(target, true)
                    continue
                }
                val near = clickableNear(n)
                if (near != null) return Hit(near, true)
                if (level >= 2) {
                    val p = tapPointFor(n)
                    return Hit(n, false, p.x, p.y)
                }
            }
        }
        return null
    }

    /** A real finger-tap at a place on the screen. */
    private fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        return try {
            val path = Path()
            path.moveTo(x, y)
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 60L)).build()
            dispatchGesture(g, null, null)
        } catch (e: Exception) {
            false
        }
    }

    /** Presses a found button: the click action first, a real tap when the phone ignores the action (or the view takes no click at all). */
    private fun press(hit: Hit): Boolean {
        if (hit.clickable) {
            if (hit.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val r = Rect()
            hit.node.getBoundsInScreen(r)
            return tapAt(r.exactCenterX(), r.exactCenterY())
        }
        return tapAt(hit.x, hit.y)
    }

    // ------------------------------------------------------------------ report (what the phone's Quick settings looks like to the launcher)

    private var report: String = ""

    private fun short(c: CharSequence?): String = (c?.toString() ?: "").replace('\n', ' ').take(60)

    private fun dumpNode(sb: StringBuilder, n: AccessibilityNodeInfo?, depth: Int, count: IntArray) {
        if (n == null || depth > 24 || count[0] > 450) return
        count[0]++
        val r = Rect()
        n.getBoundsInScreen(r)
        sb.append("  ".repeat(depth)).append((n.className?.toString() ?: "?").substringAfterLast('.'))
        if (!n.text.isNullOrEmpty()) sb.append(" t=\"").append(short(n.text)).append('"')
        if (!n.contentDescription.isNullOrEmpty()) sb.append(" d=\"").append(short(n.contentDescription)).append('"')
        if (Build.VERSION.SDK_INT >= 30 && !n.stateDescription.isNullOrEmpty()) sb.append(" s=\"").append(short(n.stateDescription)).append('"')
        sb.append(' ')
        if (n.isClickable) sb.append('C')
        if (n.isLongClickable) sb.append('L')
        if (n.isCheckable) sb.append(if (n.isChecked) "K" else "k")
        if (n.isScrollable) sb.append('S')
        if (!n.isVisibleToUser) sb.append('h')
        if (!n.isEnabled) sb.append('x')
        sb.append(" [").append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append("]\n")
        for (i in 0 until n.childCount) dumpNode(sb, n.getChild(i), depth + 1, count)
    }

    /** Everything the launcher can see on the screen right now (all windows), as plain text. */
    private fun dumpShade(): String {
        val sb = StringBuilder()
        sb.append("QS report v28 android=").append(Build.VERSION.SDK_INT).append(' ').append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        val count = intArrayOf(0)
        try {
            val list = windows
            sb.append("windows=").append(list.size).append('\n')
            for (w in list) {
                val root = w.root
                sb.append("== window type=").append(w.type).append(" layer=").append(w.layer).append(" title=").append(short(w.title))
                    .append(" pkg=").append(root?.packageName).append(if (w.isActive) " active" else "").append('\n')
                if (root != null && root.packageName?.toString() != packageName) dumpNode(sb, root, 0, count)
            }
        } catch (e: Exception) {
            sb.append("windows failed: ").append(e).append('\n')
        }
        if (count[0] == 0) {
            try {
                sb.append("== active window only\n")
                dumpNode(sb, rootInActiveWindow, 0, count)
            } catch (e: Exception) {
                sb.append("active failed: ").append(e).append('\n')
            }
        }
        return sb.toString().take(60000)
    }

    /** Puts the last report on the clipboard so it can be pasted into a chat. */
    fun copyReport(): Boolean {
        if (report.isEmpty()) return false
        return try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Quick panel report", report))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scrollTiles(roots: List<AccessibilityNodeInfo>): Boolean {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, nodes, 0)
        val minTop = statusBarPx()
        val r = Rect()
        val scrollables = nodes.filter {
            it.isScrollable && run {
                it.getBoundsInScreen(r)
                r.centerY() >= minTop
            }
        }
        // the sideways pager of the buttons first, then anything else that scrolls
        val ordered = scrollables.sortedBy { if ((it.className?.toString() ?: "").contains("Pager", true)) 0 else 1 }
        for (n in ordered) if (n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        return false
    }

    /** The big buttons at the top of the phone's panel (Wi-Fi, mobile data on most phones), left to right. Marked clickable or not. */
    private fun wideTiles(nodes: List<AccessibilityNodeInfo>, minTop: Int): List<AccessibilityNodeInfo> {
        val w = screenW()
        val h = screenH()
        val r = Rect()
        val seen = HashSet<String>()
        return nodes.filter {
            if (!it.isEnabled) return@filter false
            it.getBoundsInScreen(r)
            val ok = r.width() > w * 0.38f && r.width() < w * 0.7f && r.height() > h * 0.03f && r.height() < h * 0.2f &&
                r.centerY() > minTop && r.centerY() < h * 0.4f && (it.isClickable || it.childCount > 0)
            ok && seen.add((r.left / 12).toString() + "," + (r.top / 12))
        }.sortedBy {
            it.getBoundsInScreen(r)
            r.left
        }
    }

    /** The small round clickable part inside a wide button (the icon), which switches it; null when there is none. */
    private fun iconOf(wide: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inner = ArrayList<AccessibilityNodeInfo>()
        collect(wide, inner, 0)
        val r = Rect()
        wide.getBoundsInScreen(r)
        val wideArea = r.width() * r.height()
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        for (n in inner) {
            if (n === wide || !n.isClickable || !n.isEnabled) continue
            n.getBoundsInScreen(r)
            val a = r.width() * r.height()
            if (a > 0 && a < wideArea / 2 && a < bestArea) {
                best = n
                bestArea = a
            }
        }
        return best
    }

    /** On / off of a button as the phone describes it (state text, switch or the words in its description); null when it does not say. */
    private fun readState(n: AccessibilityNodeInfo?): Boolean? {
        if (n == null) return null
        val list = ArrayList<AccessibilityNodeInfo>()
        collect(n, list, 0)
        for (x in list) {
            if (Build.VERSION.SDK_INT >= 30) {
                val sd = norm(x.stateDescription)
                if (sd == "on" || sd.startsWith("on ") || sd == "enabled" || sd == "active") return true
                if (sd == "off" || sd.startsWith("off ") || sd == "disabled" || sd == "inactive") return false
            }
            if (x.isCheckable) return x.isChecked
        }
        for (x in list) {
            val words = (norm(x.contentDescription) + " " + norm(x.text)).split(' ')
            if ("off" in words) return false
            if ("on" in words) return true
        }
        return null
    }

    private val noLabels = listOf(
        "edit", "settings", "more", "power", "brightness", "volume", "slider", "search", "media", "page", "close", "back", "menu", "user",
        "devices", "auto", "notification", "notifications", "clear", "manage"
    )

    private fun labelOf(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim() ?: ""
        if (t.isNotEmpty()) return t
        val d = n.contentDescription?.toString()?.trim() ?: ""
        return d.substringBefore(',').trim()
    }

    private var scanStats = ""
    private var gridMidY = 0f

    /** The sideways pager of the buttons (or, when the phone does not mark one, the middle of the screen): [top, bottom] of the buttons area. */
    private fun gridRegion(vis: List<AccessibilityNodeInfo>, minTop: Int): Triple<Int, Int, String> {
        val w = screenW()
        val h = screenH()
        val r = Rect()
        var bestCount = 0
        var best: Rect? = null
        for (n in vis) {
            if (!n.isScrollable) continue
            n.getBoundsInScreen(r)
            if (r.width() < w * 0.55f || r.height() > h * 0.6f || r.centerY() <= minTop) continue
            val inner = ArrayList<AccessibilityNodeInfo>()
            collect(n, inner, 0)
            val count = inner.count { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }
            if (count > bestCount) {
                bestCount = count
                best = Rect(r)
            }
        }
        if (best != null && bestCount >= 3) return Triple(best.top, best.bottom, "pager")
        var top = (h * 0.2f).toInt()
        for (wd in wideTiles(vis, minTop)) {
            wd.getBoundsInScreen(r)
            top = maxOf(top, r.bottom)
        }
        var bottom = (h * 0.8f).toInt()
        for (n in vis) {
            val cls = n.className?.toString() ?: ""
            if (!cls.contains("SeekBar") && !cls.contains("Slider")) continue
            n.getBoundsInScreen(r)
            if (r.top > top + h * 0.1f) bottom = minOf(bottom, r.top)
        }
        return Triple(top, bottom, "guess")
    }

    private class Cand(val label: String, val node: AccessibilityNodeInfo, val x: Int, val y: Int)

    /**
     * Every small button of the phone's panel that is on screen right now: name and on / off. Works with buttons that are marked clickable
     * and with those that are not: the names inside the buttons area are what counts.
     */
    private fun readTiles(roots: List<AccessibilityNodeInfo>): List<Pair<String, Boolean?>> {
        val w = screenW()
        val h = screenH()
        val minTop = (statusBarPx() * 1.3f).toInt()
        val all = ArrayList<AccessibilityNodeInfo>()
        for (r in roots) collect(r, all, 0)
        var vis = all.filter { it.isVisibleToUser }
        if (vis.isEmpty()) vis = all
        val (top, bottom, how) = gridRegion(vis, minTop)
        gridMidY = (top + bottom) / 2f
        val r = Rect()
        fun gather(source: List<AccessibilityNodeInfo>): ArrayList<Cand> {
            val seen = HashSet<String>()
            val list = ArrayList<Cand>()
            for (n in source) {
                val label = labelOf(n)
                if (label.isEmpty() || label.length > 28) continue
                val cls = n.className?.toString() ?: ""
                if (cls.contains("SeekBar") || cls.contains("Slider") || cls.contains("ProgressBar") || cls.contains("Switch")) continue
                n.getBoundsInScreen(r)
                if (r.width() <= 0 || r.height() <= 0 || r.width() > w * 0.4f || r.height() > h * 0.2f) continue
                if (r.centerY() < top || r.centerY() > bottom || r.centerX() < 0 || r.centerX() > w) continue
                val l = norm(label)
                if (l.isEmpty() || l.all { it.isDigit() || it == ' ' } || noLabels.any { l == it || l.startsWith("$it ") }) continue
                if (!seen.add(l + "@" + (r.centerX() / (w / 5)))) continue
                list.add(Cand(label, n, r.centerX(), r.centerY()))
            }
            return list
        }
        var found = gather(vis)
        if (found.size < 3) found = gather(all) // the phone marks its buttons "not visible": look at everything on screen
        val row = h * 0.05f
        found.sortWith(compareBy<Cand>({ (it.y / row).toInt() }, { it.x }))
        val out = ArrayList<Pair<String, Boolean?>>()
        for (c in found) {
            var cell: AccessibilityNodeInfo? = c.node
            var up = 0
            while (cell != null && up < 3) {
                cell.getBoundsInScreen(r)
                if (r.height() > h * 0.2f || r.width() > w * 0.4f) break
                if (cell.isClickable || cell.isLongClickable) break
                cell = cell.parent
                up++
            }
            out.add(Pair(c.label, readState(cell ?: c.node.parent ?: c.node)))
        }
        scanStats = "windows ${roots.size}, views ${all.size}, area $how, names ${out.size}"
        return out
    }

    private fun swipeGridLeft() {
        if (Build.VERSION.SDK_INT < 24) return
        try {
            val w = screenW()
            val y = if (gridMidY > 0f) gridMidY else screenH() * 0.45f
            val path = Path()
            path.moveTo(w * 0.82f, y)
            path.lineTo(w * 0.18f, y)
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 230L)).build(), null, null)
        } catch (e: Exception) {
            // not possible on this phone
        }
    }

    /** True when the service was given the right to read the phone's panel (it needs switching off and on once after an update that adds it). */
    fun canReadPanel(): Boolean = ((serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0

    /**
     * Opens the phone's Quick settings behind a cover, pages through all of it and hands back every button found (null: could not read it),
     * with one line saying what was seen (shown to the person, so a failure is never silent).
     */
    fun scanTiles(done: (List<Pair<String, Boolean?>>?, String) -> Unit) {
        if (stillForcing()) {
            done(null, "busy")
            return
        }
        forcing = true
        forcingSince = android.os.SystemClock.uptimeMillis()
        scanStats = ""
        val all = LinkedHashMap<String, Boolean?>()
        val finish: (Boolean) -> Unit = { ok ->
            closeShade()
            ui.postDelayed({
                coverOff()
                forcing = false
                val note = if (canReadPanel()) scanStats else "the service may not read the panel: switch Quick panel off and on in Accessibility"
                done(if (ok) all.map { Pair(it.key, it.value) } else null, note)
            }, 330L)
        }
        coverOn {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            var pages = 0
            var empty = 0
            var swiped = false
            val step = object : Runnable {
                override fun run() {
                    val roots = try {
                        shadeRoots()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    var added = 0
                    if (pages == 0) {
                        try {
                            report = dumpShade()
                        } catch (e: Exception) {
                            // no report
                        }
                    }
                    try {
                        for ((label, on) in readTiles(roots)) if (!all.containsKey(label)) {
                            all[label] = on
                            added++
                        }
                    } catch (e: Exception) {
                        // look again
                    }
                    if (added == 0 && all.isEmpty()) {
                        empty++
                        if (empty == 6 && roots.isEmpty()) swipeDownFromTop(0.85f) // the phone did not show its panel: pull it down by hand
                        if (empty > 12) {
                            finish(false)
                            return
                        }
                        ui.postDelayed(this, 170L)
                        return
                    }
                    if (added == 0) {
                        if (!swiped && pages > 0) { // the page did not change by the action: one real swipe before giving up
                            swiped = true
                            swipeGridLeft()
                            ui.postDelayed(this, 520L)
                            return
                        }
                        finish(true)
                        return
                    }
                    swiped = false
                    if (pages >= 7) {
                        finish(true)
                        return
                    }
                    pages++
                    val moved = try {
                        scrollTiles(roots)
                    } catch (e: Exception) {
                        false
                    }
                    if (!moved) {
                        swiped = true
                        swipeGridLeft()
                    }
                    ui.postDelayed(this, 480L)
                }
            }
            ui.postDelayed(step, 400L)
        }
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

    // A frozen picture of the screen laid over everything while the phone's own Quick settings flashes by underneath (activity mode only:
    // the service's own panel overlay already sits above the phone's panel).
    private var cover: View? = null

    private fun coverOn(then: () -> Unit) {
        if (frame != null || cover != null) {
            then()
            return
        }
        var added = false
        fun add(bmp: Bitmap?) {
            if (added) return
            added = true
            try {
                val v = ImageView(this)
                v.scaleType = ImageView.ScaleType.FIT_XY
                if (bmp != null) v.setImageBitmap(bmp) else v.setBackgroundColor(android.graphics.Color.rgb(12, 13, 17))
                v.isClickable = true
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                )
                if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(v, lp)
                cover = v
            } catch (e: Exception) {
                cover = null
            }
            then()
        }
        val shoot = Build.VERSION.SDK_INT >= 30 &&
            ((serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
        if (!shoot) {
            add(null)
            return
        }
        ui.postDelayed({ add(null) }, 350L) // the picture is late: a plain dark cover is better than the phone's panel showing
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var soft: Bitmap? = null
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                    } catch (e: Exception) {
                        soft = null
                    } finally {
                        try {
                            screenshot.hardwareBuffer.close()
                        } catch (e: Exception) {
                            // already closed
                        }
                    }
                    val result = soft
                    ui.post { add(result) }
                }

                override fun onFailure(errorCode: Int) {
                    ui.post { add(null) }
                }
            })
        } catch (e: Exception) {
            add(null)
        }
    }

    private fun coverOff() {
        val v = cover ?: return
        cover = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
        } catch (e: Exception) {
            // already removed
        }
    }

    /**
     * Opens the phone's own Quick settings, presses the button whose name is one of [variants], and closes it again. [done] gets true when the
     * button was pressed. A frozen picture of the screen covers the phone's panel the whole time, so nothing of it is ever seen.
     */
    fun forceToggle(variants: List<String>, done: (Boolean, Boolean?) -> Unit) {
        if (stillForcing()) {
            done(true, null) // a second tap while one is running: ignored quietly
            return
        }
        if (variants.isEmpty()) {
            done(false, null)
            return
        }
        forcing = true
        forcingSince = android.os.SystemClock.uptimeMillis()
        var stateAfter: Boolean? = null
        val finish: (Boolean) -> Unit = { ok ->
            done(ok, stateAfter)
            ui.postDelayed({
                coverOff()
                forcing = false
            }, 330L)
        }
        coverOn {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            var tries = 0
            var scrolls = 0
            var stableAt = ""
            val step = object : Runnable {
                override fun run() {
                    tries++
                    val roots = try {
                        shadeRoots()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val level = if (tries < 4) 0 else if (tries < 8) 1 else 2
                    val hit = try {
                        findTile(roots, variants, level)
                    } catch (e: Exception) {
                        null
                    }
                    if (hit == null && tries == 14 && roots.none { SystemPanelLogic.isSystemUi(it.packageName?.toString()) }) {
                        // the phone did not show its Quick settings: pull down the notification shade, then its Quick settings
                        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                        ui.postDelayed({ performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) }, 400L)
                    }
                    if (hit == null && tries == 30) swipeDownFromTop(0.85f) // control center on phones that keep it on the right side
                    if (hit != null && !hit.clickable) {
                        // a finger-tap goes to a place: wait until the button stopped moving (the panel is still sliding open at first)
                        val where = (hit.x / 6).toInt().toString() + "," + (hit.y / 6).toInt()
                        if (where != stableAt) {
                            stableAt = where
                            ui.postDelayed(this, 80L)
                            return
                        }
                    }
                    if (hit != null) {
                        val tile = hit.node
                        val label = norm(tile.text) + " " + norm(tile.contentDescription)
                        val ok = press(hit)
                        if (label.contains("internet")) {
                            ui.postDelayed({
                                val dialog = try {
                                    pressDialogSwitch(shadeRoots(), variants)
                                } catch (e: Exception) {
                                    false
                                }
                                if (dialog) performGlobalAction(GLOBAL_ACTION_BACK)
                                ui.postDelayed({ closeShade() }, if (dialog) 120L else 0L)
                                finish(ok)
                            }, 220L)
                        } else {
                            ui.postDelayed({
                                try {
                                    tile.refresh()
                                    stateAfter = readState(tile) ?: readState(tile.parent)
                                } catch (e: Exception) {
                                    stateAfter = null
                                }
                                closeShade()
                                finish(ok)
                            }, 110L)
                        }
                        return
                    }
                    if (tries >= 6 && tries % 7 == 6 && scrolls < 6) {
                        // next page of buttons: the scroll action first, a real swipe the next time (some pagers ignore the action)
                        scrolls++
                        try {
                            if (scrolls % 2 == 1) {
                                if (!scrollTiles(roots)) swipeGridLeft()
                            } else {
                                swipeGridLeft()
                            }
                        } catch (e: Exception) {
                            // ignore, keep looking
                        }
                    }
                    if (tries >= 70) {
                        report = dumpShade()
                        copyReport()
                        closeShade()
                        finish(false)
                        return
                    }
                    ui.postDelayed(this, 50L)
                }
            }
            ui.postDelayed(step, 90L)
        }
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
