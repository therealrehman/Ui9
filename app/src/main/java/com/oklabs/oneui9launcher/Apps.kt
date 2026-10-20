package com.oklabs.oneui9launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** How the Apps screen is ordered. */
enum class SortMode(val key: String, val title: String) {
    AZ("az", "Alphabetical (A\u2013Z)"),
    NEWEST("newest", "Recently installed");

    companion object {
        fun from(key: String?): SortMode = values().firstOrNull { it.key == key } ?: AZ
    }
}

/** One launchable app on the Apps screen. */
class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: Bitmap,
    val installTime: Long,
    /** 'A'..'Z', or '#' for digits, symbols and non-Latin names. Drives A-Z order and the index bar. */
    val letter: Char
) {
    val packageName: String get() = component.packageName

    /** Lower-case, accent-free label used for searching. */
    val searchKey: String = AppText.plain(label)

    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
}

/** Loads every launchable app (off the main thread) and sorts / filters the list. */
object AppRepository {

    /**
     * Step 10a: shaped icons are kept between loads. Loading and cutting an icon is the slow part of listing the apps, and the
     * list is rebuilt after every install / update / theme change; a cached icon is reused until its app is updated, the icon
     * size changes or the themed-icon colours change (all of that is part of the key).
     */
    private val iconCache = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 12L).toInt().coerceIn(6 * 1024 * 1024, 48 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Builds the list on a background thread, then calls [done] on the main thread. */
    fun load(context: Context, iconPx: Int, done: (List<AppEntry>) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val apps: List<AppEntry> = try {
                loadNow(app, iconPx)
            } catch (e: Exception) {
                emptyList()
            }
            mainHandler.post { done(apps) }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadNow(context: Context, iconPx: Int): List<AppEntry> {
        val pm = context.packageManager
        val self = context.packageName
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installTimes = HashMap<String, LongArray>() // package -> (first install, last update)
        val themeKey = IconTheme.signature + "|" + IconPack.signature
        val packDefaults = if (IconPack.enabled && !IconTheme.enabled) IconPack.defaults(context) else emptyMap()
        val result = ArrayList<AppEntry>()

        for (info in pm.queryIntentActivities(query, 0)) {
            val activity = info.activityInfo ?: continue
            val pkg = activity.packageName
            // The launcher's own Home activity is not listed; its Settings activity is (Step 13: the Settings app).
            val isSettingsApp = pkg == self && activity.name.endsWith(".HomeSettingsActivity")
            if (pkg == self && !isSettingsApp) continue

            val label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
            val times = installTimes.getOrPut(pkg) {
                try {
                    val pkgInfo = pm.getPackageInfo(pkg, 0)
                    longArrayOf(pkgInfo.firstInstallTime, pkgInfo.lastUpdateTime)
                } catch (e: PackageManager.NameNotFoundException) {
                    longArrayOf(0L, 0L)
                }
            }
            val cacheKey = pkg + "/" + activity.name + "|" + iconPx + "|" + themeKey + "|" + times[1]
            var icon: Bitmap? = iconCache.get(cacheKey)
            if (icon == null) {
                val style = if (isSettingsApp) IconPack.Style.SETTINGS else IconPack.styleFor(label, pkg, activity.applicationInfo?.flags ?: 0, packDefaults)
                icon = try {
                    if (style != null) {
                        IconShaper.shapeDrawn(iconPx) { IconPack.draw(it, style, iconPx) }
                    } else {
                        IconShaper.shape(info.loadIcon(pm), iconPx)
                    }
                } catch (e: Exception) {
                    continue
                }
                iconCache.put(cacheKey, icon)
            }
            result.add(
                AppEntry(label, ComponentName(pkg, activity.name), icon, times[0], AppText.letterOf(label))
            )
        }
        return result
    }

    /** True when [a] and [b] show exactly the same apps with the same labels, icons and install times (the cache hands out the same icon objects). */
    fun sameApps(a: List<AppEntry>, b: List<AppEntry>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.component != y.component || x.label != y.label || x.icon !== y.icon || x.installTime != y.installTime) return false
        }
        return true
    }

    fun sort(apps: List<AppEntry>, mode: SortMode): List<AppEntry> = when (mode) {
        SortMode.AZ -> {
            val collator = AppText.collator()
            apps.sortedWith(Comparator { a, b ->
                val byGroup = AppText.groupOrder(a.letter) - AppText.groupOrder(b.letter)
                if (byGroup != 0) {
                    byGroup
                } else {
                    val byName = collator.compare(a.label, b.label)
                    if (byName != 0) byName else a.packageName.compareTo(b.packageName)
                }
            })
        }
        SortMode.NEWEST -> apps.sortedWith(
            compareByDescending<AppEntry> { it.installTime }.thenBy { it.searchKey }
        )
    }

    /** Names that start with the query first, then word starts, then anything containing it. */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = AppText.plain(query.trim())
        if (q.isEmpty()) return apps
        val starts = ArrayList<AppEntry>()
        val words = ArrayList<AppEntry>()
        val inside = ArrayList<AppEntry>()
        for (app in apps) {
            val key = app.searchKey
            when {
                key.startsWith(q) -> starts.add(app)
                key.contains(" $q") -> words.add(app)
                key.contains(q) -> inside.add(app)
            }
        }
        return starts + words + inside
    }
}
