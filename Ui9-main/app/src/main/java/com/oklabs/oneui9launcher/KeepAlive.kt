package com.oklabs.oneui9launcher

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings

/**
 * Huawei plan, step 7: keeps the Quick panel service alive. Phones with a strong "cleaner" (EMUI, MIUI, ColorOS...) stop background apps.
 * What the launcher can do by itself: start again after a reboot or an update, start again a few seconds after it was stopped / swiped away,
 * and check every ~15 minutes that it is running. What only the person can do: allow Auto-launch / "run in background" and leave battery
 * optimisation (the Setup check page shows these and opens the right screens).
 */
object KeepAlive {
    const val PREF_BOOT = "ka_boot" // start after the phone restarts
    const val PREF_RESTART = "ka_restart" // start again after the service is stopped + 15-minute check

    const val ACTION_RESTART = "com.oklabs.oneui9launcher.KEEPALIVE_RESTART"
    const val PKG = "com.oklabs.oneui9launcher"

    private const val WATCHDOG_MS = 15 * 60 * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun boot(c: Context): Boolean = prefs(c).getBoolean(PREF_BOOT, true)
    fun restart(c: Context): Boolean = prefs(c).getBoolean(PREF_RESTART, true)

    private fun pending(c: Context, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, code, Intent(c, KeepAliveReceiver::class.java).setAction(ACTION_RESTART), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Asks the phone to start the service again in [delayMs] (an inexact alarm: no extra permission). */
    fun scheduleRestart(c: Context, delayMs: Long) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = SystemClock.elapsedRealtime() + delayMs
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c, 11))
            else am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c, 11))
        } catch (e: Exception) {
            // the phone refused the alarm: the other ways still work
        }
    }

    /** The 15-minute "is it still running?" check. Safe to call again (it replaces the old one). */
    fun scheduleWatchdog(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + WATCHDOG_MS, WATCHDOG_MS, pending(c, 12))
        } catch (e: Exception) {
            // see above
        }
    }

    fun cancelAlarms(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(c, 11))
            am.cancel(pending(c, 12))
        } catch (e: Exception) {
            // nothing was scheduled
        }
    }

    // ------------------------------------------------------------------ what the Setup check page shows

    fun notificationsOk(c: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
        else (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    } catch (e: Exception) {
        true
    }

    /** True when the phone's battery optimisation does not limit this app. */
    fun batteryOk(c: Context): Boolean = try {
        Build.VERSION.SDK_INT < 23 || (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
    } catch (e: Exception) {
        true
    }

    fun isHuaweiFamily(): Boolean = Build.MANUFACTURER.equals("HUAWEI", true) || Build.MANUFACTURER.equals("HONOR", true) ||
        Build.BRAND.equals("HUAWEI", true) || Build.BRAND.equals("HONOR", true)

    /** The phone's list of apps with battery optimisation (a plain system screen: no special permission). */
    fun openBatterySettings(c: Context) {
        launchFirst(c, listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)), true)
    }

    /**
     * Opens the phone's own "Auto-launch / Protected apps / App launch" screen. The names differ per phone and version, so it tries the known
     * ones and falls back to this app's info screen. Returns true when a phone-specific screen opened.
     */
    fun openAutoLaunch(c: Context): Boolean {
        val list = ArrayList<Intent>()
        fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        // Huawei / Honor (EMUI, Magic UI)
        list.add(comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
        list.add(comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
        list.add(comp("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
        list.add(comp("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
        // other makers (best effort)
        list.add(comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        list.add(comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
        list.add(comp("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"))
        list.add(comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
        if (launchFirst(c, list, false)) return true
        launchFirst(c, listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))), true)
        return false
    }

    private fun launchFirst(c: Context, intents: List<Intent>, @Suppress("UNUSED_PARAMETER") unused: Boolean): Boolean {
        for (i in intents) {
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // try the next one
            }
        }
        return false
    }

    /** One-time adb commands (run on a computer with the phone connected by USB) for the strongest settings. */
    fun adbCommands(): String = listOf(
        "adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow",
        "adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS",
        "adb shell dumpsys deviceidle whitelist +$PKG",
        "adb shell cmd appops set $PKG RUN_IN_BACKGROUND allow",
        "adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow",
        "adb shell am set-standby-bucket $PKG active"
    ).joinToString("\n")
}

/** Starts the Quick panel service again after a reboot / an update / an alarm (only when the person has it switched on). */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            when (intent?.action) {
                Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> if (!KeepAlive.boot(context)) return
                Intent.ACTION_MY_PACKAGE_REPLACED -> if (!KeepAlive.boot(context)) return
                KeepAlive.ACTION_RESTART -> if (!KeepAlive.restart(context)) return
                else -> return
            }
            OverlayPanel.sync(context) // does nothing when the switch is off, the permission is missing or the service already runs
        } catch (e: Exception) {
            // a phone that does not allow a background start: the notification / tile / shortcut still open the panel
        }
    }
}
