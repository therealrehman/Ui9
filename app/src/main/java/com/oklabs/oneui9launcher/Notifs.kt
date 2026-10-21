package com.oklabs.oneui9launcher

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper

/** One notification as the Quick panel shows it. Only what is needed to draw the card; kept in memory, never saved or sent. */
class NotifItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val whenMs: Long,
    /** False for notifications the app marked "no clear". */
    val clearable: Boolean,
    /** What a tap on the card starts (the app's own intent), or null. */
    val intent: PendingIntent?,
    /** The app asked for the notification to go away when tapped. */
    val autoCancel: Boolean
)

/**
 * Step 16c: the notifications for the Quick panel. [BadgeService] fills it (only while "Notifications in Quick panel" is on and
 * notification access is allowed); [QuickNotifs] shows it. Swiping a card away or "Clear all" asks the phone to remove them.
 */
object Notifs {

    const val PREF_KEY = "qp_notifs"

    private var items: List<NotifItem> = emptyList()
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** The running service (null when notification access is off); it does the removing. */
    @Volatile
    var service: BadgeService? = null

    fun current(): List<NotifItem> = items

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** The service's new list (any thread). */
    fun publish(list: List<NotifItem>) {
        main.post {
            items = list
            fire()
        }
    }

    /** Removes one notification: from the phone, and from the list at once. */
    fun dismiss(key: String) {
        try {
            service?.cancelNotification(key)
        } catch (e: Exception) {
            // the system may refuse for a moment; the next update brings it back if it is still there
        }
        items = items.filter { it.key != key }
        fire()
    }

    /** Removes every notification that can be removed. */
    fun clearAll() {
        try {
            service?.cancelAllNotifications()
        } catch (e: Exception) {
            // see dismiss()
        }
        items = items.filter { !it.clearable }
        fire()
    }

    private fun fire() {
        for (listener in ArrayList(listeners)) listener()
    }
}
