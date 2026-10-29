package com.oklabs.oneui9launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.provider.AlarmClock

/**
 * Picks the apps for a brand-new home screen: the phone's own Gallery, Calendar, Clock, Contacts,
 * Calculator, Files, Maps, Play Store, Email, Music and Weather, plus a few well-known apps.
 * Anything already in the dock is skipped. After that the user arranges the home screen freely.
 */
object DefaultHome {

    private const val WANTED = 12
    private const val AT_LEAST = 8

    private val wellKnown = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.photos",
        "com.google.android.apps.maps",
        "com.android.vending",
        "com.google.android.gm"
    )

    /** Component keys ("package/Class") in the order they should appear. */
    fun pick(context: Context, apps: List<AppEntry>, dockPackages: Set<String>): List<String> {
        val pm = context.packageManager
        val byPackage = apps.groupBy { it.packageName }
        val chosen = LinkedHashMap<String, AppEntry>()

        fun take(pkg: String?, activity: String?) {
            if (pkg == null || chosen.size >= WANTED) return
            if (pkg in dockPackages || chosen.containsKey(pkg)) return
            val candidates = byPackage[pkg] ?: return
            chosen[pkg] = candidates.firstOrNull { it.component.className == activity } ?: candidates.first()
        }

        val probes = listOf(
            main(Intent.CATEGORY_APP_GALLERY),
            main(Intent.CATEGORY_APP_CALENDAR),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            main(Intent.CATEGORY_APP_CONTACTS),
            main(Intent.CATEGORY_APP_CALCULATOR),
            main("android.intent.category.APP_FILES"),
            main(Intent.CATEGORY_APP_MAPS),
            main(Intent.CATEGORY_APP_MARKET),
            main(Intent.CATEGORY_APP_EMAIL),
            main(Intent.CATEGORY_APP_MUSIC),
            main("android.intent.category.APP_WEATHER")
        )
        for (probe in probes) {
            val info = resolve(pm, probe)
            take(info?.packageName, info?.name)
        }
        for (pkg in wellKnown) take(pkg, null)

        // A phone with few of those (stripped-down ROM)? Fill up alphabetically so the screen is not bare.
        if (chosen.size < AT_LEAST) {
            for (app in apps.sortedBy { it.searchKey }) {
                if (chosen.size >= AT_LEAST) break
                take(app.packageName, app.component.className)
            }
        }
        return chosen.values.map { it.component.flattenToString() }
    }

    private fun main(category: String): Intent =
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    /** The app that handles [intent]; skips the system "choose an app" dialog. */
    private fun resolve(pm: PackageManager, intent: Intent): ActivityInfo? {
        val best = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (best != null && best.packageName != "android") return best
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .firstOrNull { it != null && it.packageName != "android" }
    }
}
