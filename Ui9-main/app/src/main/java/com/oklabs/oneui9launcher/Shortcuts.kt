package com.oklabs.oneui9launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.Process

/** An app shortcut: the "Take a selfie" / "New message" entries that apps offer on long-press. */
class AppShortcut(val info: ShortcutInfo, val label: String, val icon: Bitmap?)

/** Reads and starts app shortcuts. Android only hands them to the phone's default home app. */
object Shortcuts {

    private const val MAX = 4

    /** Up to four shortcuts of [packageName]; empty when we are not the default home app yet. */
    fun load(context: Context, packageName: String, iconPx: Int): List<AppShortcut> {
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (launcherApps == null || !launcherApps.hasShortcutHostPermission()) {
                emptyList()
            } else {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(packageName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                val found = launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
                found.filter { it.isEnabled }
                    .sortedBy { it.rank }
                    .take(MAX)
                    .map { info -> describe(launcherApps, info, packageName, iconPx) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun describe(launcherApps: LauncherApps, info: ShortcutInfo, fallback: String, iconPx: Int): AppShortcut {
        val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty().ifEmpty { fallback }
        val icon = try {
            launcherApps.getShortcutIconDrawable(info, 0)?.let { IconShaper.shape(it, iconPx) }
        } catch (e: Exception) {
            null
        }
        return AppShortcut(info, label, icon)
    }

    /** Starts the shortcut. False when Android refused (for example the app was removed meanwhile). */
    fun start(context: Context, shortcut: AppShortcut): Boolean {
        return try {
            context.getSystemService(LauncherApps::class.java)?.startShortcut(shortcut.info, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
