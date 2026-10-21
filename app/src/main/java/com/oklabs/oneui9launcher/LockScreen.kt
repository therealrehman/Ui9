package com.oklabs.oneui9launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings

/**
 * Step 21b: the launcher's own lock screen. [LockService] waits for "screen off" and then opens [LockScreenActivity] above the
 * phone's lock screen. Android needs "Display over other apps" for an app to open a screen from the background, so the switch
 * in Settings > Lock screen asks for it. Nothing is read or saved: the service only listens for the screen turning off.
 */
object LockScreen {

    const val PREF_KEY = "lockscreen_on"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean(PREF_KEY, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putBoolean(PREF_KEY, on).apply()
        sync(context)
    }

    /** True when Android lets this app open its lock screen from the background. */
    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /** The phone's own "Display over other apps" screen for this app. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts or stops the service so it matches the switch (and the permission). Safe to call any time. */
    fun sync(context: Context) {
        val app = context.applicationContext
        try {
            val intent = Intent(app, LockService::class.java)
            if (isOn(app) && canShow(app)) {
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
            } else {
                app.stopService(intent)
            }
        } catch (e: Exception) {
            // the phone refused to start it in the background; the next time the launcher opens it tries again
        }
    }

    /** Opens the lock screen now. */
    fun show(context: Context) {
        try {
            context.startActivity(
                Intent(context, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
        } catch (e: Exception) {
            // not allowed right now: the phone's own lock screen stays
        }
    }
}

/** Keeps running (with a small silent notification, which Android requires) and opens the lock screen when the screen goes off. */
class LockService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!LockScreen.isOn(context) || !LockScreen.canShow(context)) return
            LockScreen.show(context)
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else registerReceiver(receiver, filter)
            registered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // already gone
            }
            registered = false
        }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Lock screen", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            manager?.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val note = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.app_name) + " lock screen is on")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, note, 0x40000000) // foreground service type "special use"
            else startForeground(NOTE_ID, note)
        } catch (e: Exception) {
            // not allowed to run in the foreground right now
        }
    }

    private companion object {
        const val CHANNEL = "lockscreen"
        const val NOTE_ID = 2101
    }
}

/** Starts the lock screen service again after the phone restarts (only when the switch is on). */
class LockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (LockScreen.isOn(context)) LockScreen.sync(context)
        }
    }
}
