package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/**
 * Keeps the launcher as the phone's Home app. Android (and some makers' systems, after a restart) can put the phone's own launcher back,
 * and NO app may switch the default Home app silently: only the person can say yes, in Android's own dialog. So when the launcher notices it is
 * not the Home app any more (after a restart, an update, the 15-minute check, or when it is opened) it makes that one tap as easy as possible:
 * a notification "Tap to make Glassy launcher your Home app" and, when "Draw over other apps" is allowed, it opens Android's dialog by itself.
 */
object HomeKeeper {
    const val PREF_ON = "hk_on"
    private const val PREF_LAST_AUTO = "hk_last_auto"
    private const val CHANNEL = "home_keeper"
    private const val NOTE_ID = 4712
    private const val AUTO_GAP_MS = 3 * 60 * 1000L
    private const val PREF_LOG = "hk_log"
    private const val PREF_WAS = "hk_was_default"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(c: Context): Boolean = prefs(c).getBoolean(PREF_ON, true)

    /** True when this launcher is the phone's Home app right now. */
    fun isDefault(c: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = c.getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pkg = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            return pkg == c.packageName
        } catch (e: Exception) {
            return true // when in doubt do not nag
        }
    }

    /** The package that answers the Home button right now (who took over). */
    fun holder(c: Context): String = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** The last events, newest last: shown in Settings so we can see WHEN the Home role was lost and to WHICH launcher. */
    fun log(c: Context): String = prefs(c).getString(PREF_LOG, "") ?: ""

    fun addLog(c: Context, text: String) {
        val stamp = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val lines = (log(c).split("\n").filter { it.isNotBlank() } + (stamp + "  " + text)).takeLast(12)
        prefs(c).edit().putString(PREF_LOG, lines.joinToString("\n")).apply()
    }

    /**
     * Called every few seconds by the panel service (cheap: one lookup). When the Home role changes hands it writes it to the log (with the
     * package that took it, and whether this app had crashed) and, at once, asks again (notification + Android's dialog).
     */
    fun watch(c: Context) {
        try {
            if (!wanted(c)) return
            val now = isDefault(c)
            val was = prefs(c).getBoolean(PREF_WAS, true)
            if (now != was) {
                prefs(c).edit().putBoolean(PREF_WAS, now).apply()
                if (now) addLog(c, "this launcher is the Home app again")
                else addLog(c, "LOST the Home role to " + holder(c) + (if (CrashLog.read(c) != null) " (a crash note exists: see Settings)" else ""))
            }
            if (!now) check(c, true)
        } catch (e: Exception) {
            // never let a check end the service
        }
    }

    /** Decides what to do: nothing when it is the Home app (the notification goes away), otherwise the notification and maybe the dialog. */
    fun check(c: Context, mayOpenDialog: Boolean) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!wanted(c) || isDefault(c)) {
                nm.cancel(NOTE_ID)
                return
            }
            showNotification(c, nm)
            if (mayOpenDialog && OverlayPanel.canDraw(c) && OverlayPanel.running()) {
                // "Draw over other apps" lets an app start a screen from the background; not more often than every 20 minutes
                val now = System.currentTimeMillis()
                if (now - prefs(c).getLong(PREF_LAST_AUTO, 0L) >= AUTO_GAP_MS) {
                    prefs(c).edit().putLong(PREF_LAST_AUTO, now).apply()
                    c.startActivity(Intent(c, HomeRoleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        } catch (e: Exception) {
            // the phone refused: the notification (if it was shown) is still there
        }
    }

    private fun showNotification(c: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Home app", NotificationManager.IMPORTANCE_HIGH)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            c, 3, Intent(c, HomeRoleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CHANNEL) else Notification.Builder(c)
        nm.notify(
            NOTE_ID,
            builder.setSmallIcon(R.drawable.ic_stat_panel)
                .setContentTitle(c.getString(R.string.app_name) + " is not your Home app")
                .setContentText("Tap to make it your Home app again")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }
}

/** A see-through screen that opens Android's "Home app" question and closes itself. */
class HomeRoleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (HomeKeeper.isDefault(this)) {
            finish()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                // no Settings app
            }
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // "Don't ask again" (Android blocks the question after two refusals): open the phone's own Home app screen instead
        if (resultCode != RESULT_OK && !HomeKeeper.isDefault(this)) {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                // no such screen
            }
        }
        finish()
    }
}
