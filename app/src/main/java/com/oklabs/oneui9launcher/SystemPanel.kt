package com.oklabs.oneui9launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.Toast

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

    private fun prefs(context: Context) = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(context: Context): Boolean = prefs(context).getBoolean(PREF_ON, false)

    fun loose(context: Context): Boolean = prefs(context).getBoolean(PREF_LOOSE, false)

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

class PanelAccessibilityService : AccessibilityService() {

    private var frame: HostFrame? = null
    private var lastShown = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Look.load(this)
    }

    override fun onInterrupt() {
        hidePanel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hidePanel()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!SystemPanel.wanted(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (frame != null) {
            // another app came to the front (a button of the panel opened Settings, say): the panel goes
            if (pkg != packageName && !SystemPanelLogic.isSystemUi(pkg)) hidePanel()
            return
        }
        if (!SystemPanelLogic.isSystemUi(pkg)) return
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
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

    // ------------------------------------------------------------------ the overlay

    fun showPanel() {
        if (frame != null) return
        try {
            val ctx = OverlayContext(this)
            Look.load(ctx)
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (Build.VERSION.SDK_INT >= 31 && ctx.windowBlurWorks() && getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("blur", true)) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 40
            }
            frame = host
            wm.addView(host, params)
            host.applyFallbackInsets()
            quick.open()
            host.requestFocus()
        } catch (e: Exception) {
            frame = null
        }
    }

    fun hidePanel() {
        val host = frame ?: return
        frame = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(host)
        } catch (e: Exception) {
            // already removed
        }
    }

    /** The overlay's root: Back closes the panel, and it hands the status / navigation bar sizes to the panels. */
    private inner class HostFrame(context: Context, private val quick: QuickPanel, private val notifs: NotificationPage) : FrameLayout(context) {

        init {
            isFocusable = true
            isFocusableInTouchMode = true
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
        /** The running service (null when it is off); the Settings test button uses it. */
        @Volatile
        var instance: PanelAccessibilityService? = null
    }
}
