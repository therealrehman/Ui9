package com.oklabs.oneui9launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Step 9b: notification badges. [BadgeService] counts the notifications that are showing for each app and hands the numbers
 * to [Badges]; every [AppIconView] that has a [AppIconView.badgePackage] asks here and redraws when the numbers change.
 * Badges only keep the counts. Step 16c: while "Notifications in Quick panel" is on, the title and text are also held in memory for the panel (see [Notifs]), never saved or sent.
 * Needs the phone's "Notification access".
 */
object Badges {

    private val counts = HashMap<String, Int>()
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Notifications showing for [pkg], or 0 when badges are off. */
    fun count(pkg: String): Int =
        if (HomeStore.badgeMode == HomeStore.BADGE_OFF) 0 else (counts[pkg] ?: 0)

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** New counts from the service (any thread). */
    fun publish(newCounts: Map<String, Int>) {
        main.post {
            counts.clear()
            counts.putAll(newCounts)
            for (listener in ArrayList(listeners)) listener()
        }
    }

    /** True when the person allowed this launcher to see notifications (Settings > Notification access). */
    fun accessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
    }

    /** Opens the phone's "Notification access" list. */
    fun openAccessSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // a phone without that screen: nothing more to do
        }
    }
}

/** Listens to the phone's notifications (once the person allowed it) and reports how many each app has. */
class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        Notifs.service = this
        recount()
    }

    override fun onListenerDisconnected() {
        if (Notifs.service === this) Notifs.service = null
        Notifs.publish(emptyList())
    }

    override fun onDestroy() {
        if (Notifs.service === this) Notifs.service = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recount()
    }

    /** Ongoing ones (music player, downloads) and group summaries do not count. */
    private fun recount() {
        val map = HashMap<String, Int>()
        val list = ArrayList<NotifItem>()
        // Step 16c: the text is only taken while "Notifications in Quick panel" is on.
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        val forPanel = prefs.getBoolean(Notifs.PREF_KEY, true) || prefs.getBoolean(NotificationPage.PREF_KEY, true)
        try {
            val showing = activeNotifications
            if (showing != null) {
                for (sbn in showing) {
                    val n = sbn.notification ?: continue
                    if (sbn.isOngoing) continue
                    if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                    map[sbn.packageName] = (map[sbn.packageName] ?: 0) + 1
                    if (forPanel && n.visibility != Notification.VISIBILITY_SECRET) {
                        val extras = n.extras
                        val title = NotifLogic.firstText(extras?.getCharSequence(Notification.EXTRA_TITLE))
                        val text = NotifLogic.firstText(
                            extras?.getCharSequence(Notification.EXTRA_TEXT),
                            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
                            extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)
                        )
                        if (title.isNotEmpty() || text.isNotEmpty()) {
                            list.add(
                                NotifItem(
                                    sbn.key, sbn.packageName, title, text,
                                    if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
                                    sbn.isClearable, n.contentIntent,
                                    (n.flags and Notification.FLAG_AUTO_CANCEL) != 0
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // the system may refuse for a moment; the next change counts again
        }
        Badges.publish(map)
        Notifs.publish(NotifLogic.newestFirst(list) { it.whenMs })
    }
}
