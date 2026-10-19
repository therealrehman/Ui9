package com.oklabs.oneui9launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

/** Picks the phone's default Phone / Messages / Browser / Camera / Settings apps for a brand-new dock. */
object Favorites {

    private const val SLOTS = HomeLayout.DOCK_SLOTS

    /**
     * Component keys ("package/Class") of up to 5 apps for the dock, in order. Only used once (first run, or the
     * first time a layout from before the editable dock is opened); after that the dock is part of the saved layout.
     */
    fun defaultKeys(context: Context, apps: List<AppEntry>): List<String> {
        val pm = context.packageManager
        val self = context.packageName
        val packages = LinkedHashSet<String>()

        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (probe in probes) {
            findPackage(pm, probe, self)?.let { packages.add(it) }
        }

        val byPackage = apps.groupBy { it.packageName }
        // Fewer than 5 defaults on this phone? Top up with other launchable apps, A-Z.
        if (packages.size < SLOTS) {
            for (app in apps.sortedBy { it.searchKey }) {
                if (packages.size >= SLOTS) break
                if (app.packageName != self) packages.add(app.packageName)
            }
        }

        val keys = ArrayList<String>()
        for (pkg in packages) {
            val candidates = byPackage[pkg] ?: continue
            val launcher = pm.getLaunchIntentForPackage(pkg)?.component?.className
            val app = candidates.firstOrNull { it.component.className == launcher } ?: candidates.first()
            keys.add(app.component.flattenToString())
            if (keys.size >= SLOTS) break
        }
        return keys
    }

    /** The app that handles [probe]; skips the "choose an app" system dialog and ourselves. */
    private fun findPackage(pm: PackageManager, probe: Intent, self: String): String? {
        val best = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (best != null && best != "android" && best != self) return best
        return pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != "android" && it != self }
    }
}
