package com.oklabs.oneui9launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Huawei plan, step 1: the Quick panel over other apps WITHOUT the Accessibility service.
 *
 * It needs only "Draw over other apps" and a small foreground service (its notification is the first way to open the panel; later steps
 * add the top handle, the Quick Settings tile and the floating button). Android shows this kind of window ABOVE apps but BELOW the status bar,
 * so the phone's own status bar can not be replaced: the panel is opened from our own handle / button / tile, not from the status bar itself.
 */
object OverlayPanel {

    /** The master switch ("Quick panel over other apps"). */
    const val PREF_ON = "op_on"

    private fun prefs(context: Context) = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(context: Context): Boolean = prefs(context).getBoolean(PREF_ON, false)

    /** Saves the switch and starts / stops the service to match. */
    fun setWanted(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(PREF_ON, on).apply()
        sync(context)
    }

    /** "Draw over other apps" allowed for this app? */
    fun canDraw(context: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun running(): Boolean = PanelOverlayService.instance != null

    /** Opens the phone's own "Draw over other apps" screen of this app. */
    fun openPermissionScreen(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                // a phone without that screen
            }
        }
    }

    /** Makes the service match the switch and the permission (safe to call as often as you like). */
    fun sync(context: Context) {
        val want = wanted(context) && canDraw(context)
        val intent = Intent(context, PanelOverlayService::class.java)
        try {
            if (want) {
                if (running()) return
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } else if (running()) {
                context.stopService(intent)
            }
        } catch (e: Exception) {
            // the phone does not let a service start right now (nothing on screen): the next call from the launcher tries again
        }
    }

    /** Shows the panel now ("Show panel now" test). False when the service is not running. */
    fun showNow(): Boolean {
        val s = PanelOverlayService.instance ?: return false
        s.showPanel()
        return true
    }
}

/** Starts activities from the overlay: a service has no task, so every start needs NEW_TASK. */
private class ServiceUiContext(base: Context) : ContextWrapper(base) {
    override fun startActivity(intent: Intent) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun startActivity(intent: Intent, options: android.os.Bundle?) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
    }
}

class PanelOverlayService : Service() {

    private var frame: HostFrame? = null

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            hidePanel()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startAsForeground()
        try {
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        } catch (e: Exception) {
            // not important: the panel then stays until it is closed
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showPanel(false)
            ACTION_SHOW_NOTIFS -> showPanel(true)
            ACTION_HIDE -> hidePanel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hidePanel()
        try {
            unregisterReceiver(screenOff)
        } catch (e: Exception) {
            // was not registered
        }
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the foreground notification (also the first way to open the panel)

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Quick panel", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getService(this, 1, Intent(this, PanelOverlayService::class.java).setAction(ACTION_SHOW), flags)
        val settings = PendingIntent.getActivity(
            this, 2, Intent(this, HomeSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val note = builder
            .setSmallIcon(R.drawable.ic_stat_panel)
            .setContentTitle(getString(R.string.app_name) + " Quick panel")
            .setContentText("Tap to open the Quick panel")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Settings", settings).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, note, FGS_SPECIAL_USE) else startForeground(NOTE_ID, note)
    }

    // ------------------------------------------------------------------ the panel window

    private class Built(val host: HostFrame, val quick: QuickPanel, val notifs: NotificationPage)

    /** The panel pages inside one overlay frame; nothing is on screen until [showPanel] adds it. */
    private fun buildHost(): Built? {
        return try {
            val ctx = ServiceUiContext(this)
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
            Built(host, quick, notifs)
        } catch (e: Exception) {
            null
        }
    }

    fun showPanel(startOnNotifs: Boolean = false) {
        if (frame != null) return
        if (!OverlayPanel.canDraw(this)) return
        val built = buildHost() ?: return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.setTitle("Quick panel")
            frame = built.host
            wm.addView(built.host, params)
            built.host.applyFallbackInsets()
            if (startOnNotifs) built.notifs.open() else built.quick.open()
            built.host.requestFocus()
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
        const val ACTION_SHOW = "com.oklabs.oneui9launcher.PANEL_SHOW"
        const val ACTION_SHOW_NOTIFS = "com.oklabs.oneui9launcher.PANEL_SHOW_NOTIFS"
        const val ACTION_HIDE = "com.oklabs.oneui9launcher.PANEL_HIDE"
        private const val CHANNEL = "quick_panel_overlay"
        private const val NOTE_ID = 4711

        /** ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE (Android 14). */
        private const val FGS_SPECIAL_USE = 0x40000000

        /** The running service (null when it is off). */
        @Volatile
        var instance: PanelOverlayService? = null
    }
}
