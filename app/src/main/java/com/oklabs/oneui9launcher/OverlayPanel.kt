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
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
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
        PanelTileService.requestUpdate(context) // Step 52: the Quick Settings tile looks again
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
    private var handle: TopHandleView? = null // Step 2: the thin strip under the status bar
    private var button: FloatingButtonView? = null // Step 4: the floating button
    private var buttonParams: WindowManager.LayoutParams? = null

    // Step 5: smart rules (hide the handle / button in full-screen apps, with the keyboard, on the lock screen, screen off, landscape)
    private var probe: SmartProbeView? = null
    private var probeFullscreen = false
    private var probeKeyboard = false
    private var lastHidden = false

    /** Step 70: the Home screen's panel is gone: show the strip and the button again (see [notifyHomePanelOpen]). */
    private val homeShow = Runnable {
        if (homePanelOpen) {
            homePanelOpen = false
            refreshHandle()
            refreshButton()
        }
    }

    /** Step 70: the smart rules said "show again" and the answer stayed so for a moment (see [refreshSmart]). */
    private val smartShow = Runnable {
        try {
            if (lastHidden && !smartHidden()) {
                lastHidden = false
                refreshHandle()
                refreshButton()
            }
        } catch (e: Exception) {
            // the rules then stay as they were
        }
    }
    private var alignedY = -1
    private var alignedKey = ""
    val uiHandler = Handler(Looper.getMainLooper())
    private val probeTick = object : Runnable {
        override fun run() {
            val p = probe ?: return
            p.recheck() // safety net: the insets callback alone is not reliable on every phone
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val handleKeys = setOf(
        TopHandle.PREF_ON, TopHandle.PREF_WIDTH, TopHandle.PREF_HEIGHT, TopHandle.PREF_OFFSET,
        TopHandle.PREF_SIDE, TopHandle.PREF_EDGE, TopHandle.PREF_VISIBLE, TopHandle.PREF_HAPTIC,
        TopHandle.PREF_SHAPE, TopHandle.PREF_THICK, TopHandle.PREF_COLOR, TopHandle.PREF_GRAD, PanelTheme.KEY // Step 58: the strip follows the panel colour too
    )
    private val buttonKeys = setOf(FloatButton.PREF_ON, FloatButton.PREF_SIZE, FloatButton.PREF_ALPHA, FloatButton.PREF_SNAP, FloatButton.PREF_FX, FloatButton.PREF_FY, FloatButton.PREF_HAPTIC)
    private val triggerKeys = setOf(MoreTriggers.PREF_DOUBLE, MoreTriggers.PREF_LONG, MoreTriggers.PREF_SPLIT)
    private val shakeKeys = setOf(MoreTriggers.PREF_SHAKE, MoreTriggers.PREF_SHAKE_LEVEL)
    private val noteKeys = setOf(PanelNote.PREF_ACTION, PanelNote.PREF_TAP)
    private val smartKeys = setOf(
        SmartRules.PREF_FULLSCREEN, SmartRules.PREF_KEYBOARD, SmartRules.PREF_LOCK, SmartRules.PREF_SCREEN_OFF, SmartRules.PREF_LANDSCAPE
    )
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in handleKeys) refreshHandle()
        if (key != null && key in triggerKeys) refreshHandle() // Step 6
        if (key != null && key in shakeKeys) updateShake()
        if (key == KeepAlive.PREF_RESTART) { if (KeepAlive.restart(this)) KeepAlive.scheduleWatchdog(this) else KeepAlive.cancelAlarms(this) } // Step 7
        if (key != null && key in buttonKeys) refreshButton() // Step 53
        if (key != null && key in noteKeys) refreshNotification() // Step 52
        if (key != null && (key in smartKeys || key == TopHandle.PREF_ON || key == FloatButton.PREF_ON)) refreshSmart() // Step 54
        if (key == OverlayPanel.PREF_ON) PanelTileService.requestUpdate(this) // Step 52: the tile looks again
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) hidePanel()
            refreshSmart() // Step 54: screen on / off and unlock change what the smart rules say
            updateShake() // Step 6: the sensor listens only while the screen is on
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Step 54: the phone turned (landscape rule) and the screen size changed (handle width, button place)
        refreshSmart()
        refreshHandle()
        refreshButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val homeWatch = object : Runnable {
        override fun run() {
            HomeKeeper.watch(this@PanelOverlayService)
            uiHandler.postDelayed(this, 8000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startAsForeground()
        try {
            getSharedPreferences("launcher", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        } catch (e: Exception) {
            // the handle then follows the settings only when the service starts again
        }
        homeWatch.run() // Home app watch: every 8 s (see HomeKeeper.watch)
        lastHidden = smartHidden()
        refreshHandle()
        refreshButton()
        updateProbe()
        updateShake()
        if (KeepAlive.restart(this)) KeepAlive.scheduleWatchdog(this) else KeepAlive.cancelAlarms(this) // Step 7
        try {
            registerReceiver(
                screenOff,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
            )
        } catch (e: Exception) {
            // not important: the panel then stays until it is closed
        }
    }

    /** Step 7: the person swiped the app away from Recents: ask the phone to start the service again in a moment. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (OverlayPanel.wanted(this) && KeepAlive.restart(this)) KeepAlive.scheduleRestart(this, 3000L)
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
        uiHandler.removeCallbacks(homeWatch)
        try {
            getSharedPreferences("launcher", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (e: Exception) {
            // not registered
        }
        stopShake()
        removeProbe()
        removeHandle()
        removeButton()
        hidePanel()
        try {
            unregisterReceiver(screenOff)
        } catch (e: Exception) {
            // was not registered
        }
        if (instance === this) instance = null
        // Step 7: stopped by the phone (the switch is still on): come back in a few seconds. Switched off by the person: no more alarms.
        if (OverlayPanel.wanted(this) && OverlayPanel.canDraw(this) && KeepAlive.restart(this)) KeepAlive.scheduleRestart(this, 5000L)
        else KeepAlive.cancelAlarms(this)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the foreground notification (also the first way to open the panel)

    private fun refreshNotification() {
        try {
            startAsForeground()
        } catch (e: Exception) {
            // the notification keeps its old look
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Quick panel", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val tapNotifs = PanelNote.tapPage(this) == 1
        val open = PendingIntent.getService(
            this, 1, Intent(this, PanelOverlayService::class.java).setAction(if (tapNotifs) ACTION_SHOW_NOTIFS else ACTION_SHOW), flags
        )
        val settings = PendingIntent.getActivity(
            this, 2, Intent(this, HomeSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        builder
            .setSmallIcon(R.drawable.ic_stat_panel)
            .setContentTitle(getString(R.string.app_name) + " Quick panel")
            .setContentText(if (tapNotifs) "Tap to open the Notifications page" else "Tap to open the Quick panel")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
        if (PanelNote.showAction(this)) builder.addAction(Notification.Action.Builder(null, "Settings", settings).build())
        val note = builder.build()
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
            // Step 69: the panels come up from the bottom (sheet) only when the strip is at the bottom; with the strip at the top they open from the top, like a swipe down on the Home screen
            val fromBottom = TopHandle.atBottom(this)
            quick.sheetAllowed = fromBottom
            val notifs = NotificationPage(ctx) {
                try {
                    ctx.startActivity(Intent("android.settings.NOTIFICATION_SETTINGS"))
                } catch (e: Exception) {
                    // no such screen
                }
            }
            notifs.sheetAllowed = fromBottom
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
            removeHandle() // the panel covers the strip while it is open
            removeButton()
            wm.addView(built.host, params)
            built.host.applyFallbackInsets()
            built.host.coverNavigationBar() // Step 61
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
        refreshHandle() // the strip comes back when the panel is gone
        refreshButton()
        probe?.recheck() // Step 54: the other app may have changed while the panel was open
    }

    // ------------------------------------------------------------------ Step 2: the top handle

    /** Puts the strip on screen, takes it away or moves it, so that it matches the settings (and is not there while the panel is open). */
    fun refreshHandle() {
        try {
            val want = TopHandle.wanted(this) && OverlayPanel.canDraw(this) && frame == null && !homePanelOpen && !smartHidden()
            if (!want) {
                removeHandle()
                return
            }
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = handle ?: TopHandleView(ServiceUiContext(this), true, true) { notifs -> android.os.Handler(android.os.Looper.getMainLooper()).post { showPanel(notifs) } }.also { handle = it }
            view.showPill = TopHandle.visible(this)
            view.buzz = TopHandle.haptic(this)
            Look.load(this) // Step 58: light / dark of the panel for the strip colour
            view.shape = TopHandle.shape(this)
            view.thickPx = dp(TopHandle.thickDp(this)).toFloat()
            view.colors = TopHandle.colors(this)
            view.splitSides = MoreTriggers.split(this) // Step 6
            view.atBottom = TopHandle.atBottom(this) // Step 63
            view.doubleAction = MoreTriggers.doubleTap(this)
            view.longAction = MoreTriggers.longPress(this)
            val screenW = resources.displayMetrics.widthPixels
            val w = (screenW * TopHandle.widthPct(this) / 100f).toInt().coerceAtLeast(dp(48))
            val params = WindowManager.LayoutParams(
                w, dp(TopHandle.heightDp(this)),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, // Step 65: the keyboard is drawn OVER the strip, so the strip never sits on the keys
                PixelFormat.TRANSLUCENT
            )
            params.gravity = (if (TopHandle.atBottom(this)) Gravity.BOTTOM else Gravity.TOP) or when (TopHandle.side(this)) {
                1 -> Gravity.START
                2 -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            // Step 65: at the bottom the window is measured from the edge of the usable screen; the first version also added the bar height,
            // which put the strip far too high. y is only the user's distance now, and a one-time check below corrects any phone that differs.
            // Step 70: the corrected place is kept: every refresh used to put the strip back to the uncorrected place and then move it again (a visible jump / blink)
            val alignKey = "" + TopHandle.offsetDp(this) + "|" + TopHandle.heightDp(this) + "|" + resources.displayMetrics.heightPixels + "|" + w
            params.y = if (TopHandle.atBottom(this)) {
                if (alignedKey == alignKey && alignedY >= 0) alignedY else dp(TopHandle.offsetDp(this))
            } else statusBarPx() + dp(TopHandle.offsetDp(this))
            params.setTitle("Quick panel handle")
            if (view.parent != null) {
                wm.updateViewLayout(view, params)
                view.invalidate()
            } else {
                wm.addView(view, params)
            }
            if (TopHandle.atBottom(this) && !(alignedKey == alignKey && alignedY >= 0)) alignAboveNavBar(view, params, wm, alignKey)
        } catch (e: Exception) {
            handle = null // a window that could not be added is made again next time
        }
    }

    /**
     * Step 65: once the strip is on the screen, looks where its bottom really is and moves it so it sits right on top of the navigation bar
     * (plus the user's distance). Phones disagree about where a bottom-gravity overlay starts, so it is measured, not assumed. Runs once per refresh.
     */
    private fun alignAboveNavBar(view: View, params: WindowManager.LayoutParams, wm: WindowManager, key: String) {
        view.post {
            try {
                if (view.parent == null || view.height == 0 || probeKeyboard) return@post // not while the keyboard is up: the place measured then is not the real one
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                val m = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(m)
                val wantBottom = m.heightPixels - navBarPx() - dp(TopHandle.offsetDp(this))
                val diff = (loc[1] + view.height) - wantBottom
                if (kotlin.math.abs(diff) > dp(2)) {
                    params.y = (params.y + diff).coerceAtLeast(0)
                    wm.updateViewLayout(view, params)
                }
                alignedY = params.y
                alignedKey = key
            } catch (e: Exception) {
                // keep the position it has
            }
        }
    }

    // ------------------------------------------------------------------ Step 6: shake to open

    private var shakeSensors: android.hardware.SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private val shakeListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            val d = shakeDetector ?: return
            if (frame != null) return
            if (d.onReading(event.values[0], event.values[1], event.values[2], android.os.SystemClock.elapsedRealtime())) {
                if (!SmartRules.isLocked(this@PanelOverlayService)) showPanel(false)
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    /** Listens to the motion sensor only when "Shake to open" is on, the screen is on and drawing is allowed (so the battery is not touched otherwise). */
    private fun updateShake() {
        try {
            val want = MoreTriggers.shake(this) && OverlayPanel.canDraw(this) && SmartRules.isInteractive(this)
            stopShake()
            if (!want) return
            val sm = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
            shakeDetector = ShakeDetector(MoreTriggers.threshold(MoreTriggers.shakeLevel(this)))
            shakeSensors = sm
            sm.registerListener(shakeListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        } catch (e: Exception) {
            shakeSensors = null
            shakeDetector = null
        }
    }

    private fun stopShake() {
        try {
            shakeSensors?.unregisterListener(shakeListener)
        } catch (e: Exception) {
            // was not listening
        }
        shakeSensors = null
        shakeDetector = null
    }

    // ------------------------------------------------------------------ Step 4: the floating button

    private fun freeW(): Int = (resources.displayMetrics.widthPixels - dp(FloatButton.sizeDp(this))).coerceAtLeast(1)
    private fun freeH(): Int = (resources.displayMetrics.heightPixels - dp(FloatButton.sizeDp(this))).coerceAtLeast(1)

    /** Puts the button on screen, takes it away, or changes its size / look / place, so that it matches the settings. */
    fun refreshButton() {
        try {
            val want = FloatButton.wanted(this) && OverlayPanel.canDraw(this) && frame == null && !homePanelOpen && !smartHidden()
            if (!want) {
                removeButton()
                return
            }
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = button ?: FloatingButtonView(
                ServiceUiContext(this), true,
                { android.os.Handler(android.os.Looper.getMainLooper()).post { showPanel(false) } },
                { x, y -> moveButton(x, y) },
                { x, y -> releaseButton(x, y) }
            ).also { button = it }
            view.buzz = FloatButton.haptic(this)
            view.alpha = FloatButton.alphaPct(this) / 100f
            val size = dp(FloatButton.sizeDp(this))
            val params = buttonParams ?: WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.setTitle("Quick panel button")
                buttonParams = it
            }
            params.width = size
            params.height = size
            params.x = FloatButton.fx(this) * freeW() / 1000
            params.y = FloatButton.fy(this) * freeH() / 1000
            view.winX = params.x
            view.winY = params.y
            if (view.parent != null) {
                wm.updateViewLayout(view, params)
                view.invalidate()
            } else {
                wm.addView(view, params)
            }
        } catch (e: Exception) {
            button = null // a window that could not be added is made again next time
            buttonParams = null
        }
    }

    private fun moveButton(x: Int, y: Int) {
        val view = button ?: return
        val params = buttonParams ?: return
        try {
            params.x = x.coerceIn(0, freeW())
            params.y = y.coerceIn(0, freeH())
            view.winX = params.x
            view.winY = params.y
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(view, params)
        } catch (e: Exception) {
            // the window is gone
        }
    }

    /** The finger left the button: glue it to the nearest side edge (when that is on) and save the place. */
    private fun releaseButton(x: Int, y: Int) {
        val view = button ?: return
        val fw = freeW()
        val fh = freeH()
        val toX = if (FloatButton.snap(this)) (if (x + dp(FloatButton.sizeDp(this)) / 2 < resources.displayMetrics.widthPixels / 2) 0 else fw) else x.coerceIn(0, fw)
        val toY = y.coerceIn(0, fh)
        val save = { FloatButton.savePosition(this, toX, toY, fw, fh) }
        if (toX == view.winX) {
            moveButton(toX, toY)
            save()
        } else {
            view.animateTo(toX, toY, { px, py -> moveButton(px, py) }) { moveButton(toX, toY); save() }
        }
    }

    private fun removeButton() {
        val view = button ?: return
        button = null
        buttonParams = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        } catch (e: Exception) {
            // already removed
        }
    }

    // ------------------------------------------------------------------ Step 5: smart rules

    /** True when the smart rules say: no handle and no button on screen right now. */
    private fun smartHidden(): Boolean = SmartRules.hidden(
        SmartRules.rules(this),
        SmartRules.State(probeFullscreen, probeKeyboard, SmartRules.isLocked(this), SmartRules.isInteractive(this), SmartRules.isLandscape(this))
    )

    /** Looks at the rules again: adds / removes the probe window and shows / hides the handle and the button when the answer changed. */
    private fun refreshSmart() {
        try {
            updateProbe()
            val h = smartHidden()
            if (h) {
                // hide at once
                uiHandler.removeCallbacks(smartShow)
                if (!lastHidden) {
                    lastHidden = true
                    refreshHandle()
                    refreshButton()
                }
            } else if (lastHidden) {
                // Step 70: show again only when "not hidden" stayed true for half a second. A copy bubble, a heads-up notification or the keyboard
                // moving made the answer flip for a moment, and the strip blinked on and off over the keys.
                uiHandler.removeCallbacks(smartShow)
                uiHandler.postDelayed(smartShow, 500L)
            }
        } catch (e: Exception) {
            // the rules then stay as they were
        }
    }

    /** What the probe window reports. Ignored while the panel is open (the panel itself changes the bars). */
    private fun onProbe(fullscreen: Boolean, keyboard: Boolean) {
        if (frame != null) return
        probeFullscreen = fullscreen
        probeKeyboard = keyboard
        refreshSmart()
    }

    private fun updateProbe() {
        val want = SmartRules.needsProbe(this) && OverlayPanel.canDraw(this) && SmartRules.isInteractive(this)
        if (!want) {
            removeProbe()
            return
        }
        if (probe != null) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = SmartProbeView(ServiceUiContext(this)) { fs, kb -> uiHandler.post { onProbe(fs, kb) } }
            val params = WindowManager.LayoutParams(
                1, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.setTitle("Quick panel probe")
            wm.addView(view, params)
            probe = view
            uiHandler.removeCallbacks(probeTick)
            uiHandler.postDelayed(probeTick, 1000L)
        } catch (e: Exception) {
            probe = null
        }
    }

    private fun removeProbe() {
        uiHandler.removeCallbacks(probeTick)
        probeFullscreen = false // with no probe the other app is unknown: nothing is hidden because of it
        probeKeyboard = false
        val view = probe ?: return
        probe = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        } catch (e: Exception) {
            // already removed
        }
    }

    private fun removeHandle() {
        val view = handle ?: return
        handle = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        } catch (e: Exception) {
            // already removed
        }
    }

    private fun navBarPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(48)
    }

    private fun statusBarPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

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

        /**
         * Step 61: the panel covers the whole screen, also the navigation bar. A window of this kind is drawn BELOW the navigation bar, so the
         * only way is to ask the phone to hide the bar while the panel is open (it comes back with a swipe up from the bottom edge, and when the
         * panel closes). Best effort: a phone that refuses simply keeps its bar. Switch: Settings > Quick panel > "Cover the navigation bar".
         */
        @Suppress("DEPRECATION")
        fun coverNavigationBar() {
            if (!context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(PREF_COVER_NAV, false)) return
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    windowInsetsController?.let {
                        it.hide(WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }
            } catch (e: Exception) {
                // the phone keeps its bar
            }
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
        const val PREF_COVER_NAV = "qp_cover_nav2" // Step 61; Step 66: new key, OFF by default (hiding the bar made it slide away and back, and the apps behind jumped)
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

        /** Step 62: the Quick panel / Notifications page of the Home screen itself is open: the strip and the floating button are taken away meanwhile. */
        @Volatile
        var homePanelOpen = false

        fun notifyHomePanelOpen(open: Boolean) {
            val svc = instance
            if (svc == null) {
                homePanelOpen = open
                return
            }
            svc.uiHandler.removeCallbacks(svc.homeShow)
            if (open) {
                // hide the strip at once
                if (homePanelOpen) return
                homePanelOpen = true
                svc.uiHandler.post { svc.refreshHandle(); svc.refreshButton() }
            } else {
                // Step 70: bring it back a moment later: a switch between the Quick panel and the Notifications page closes one and opens the other
                // in the same instant, and the strip must not flash in between
                svc.uiHandler.postDelayed(svc.homeShow, 120L)
            }
        }
    }
}
