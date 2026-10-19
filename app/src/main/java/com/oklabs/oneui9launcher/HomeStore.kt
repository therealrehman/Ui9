package com.oklabs.oneui9launcher

import android.content.Context

/**
 * Saves and loads the home-screen layout (one small text value in the launcher's preferences) and keeps the Home settings
 * of Step 9a: the grid size, the icon size and the list of hidden apps; and of Step 9b: notification badges (off / dot / number),
 * the Edge panel (on / off and its apps) and landscape (on / off).
 *
 * The settings screen only writes the preferences; the launcher reads them with [loadConfig] when it starts and notices a
 * difference with [configChanged] when it comes back to the front (it then restarts its screen once).
 */
object HomeStore {

    /** Home grid columns and rows as loaded by [loadConfig] (4 x 6 until then, and by default). */
    var COLS = 4
        private set
    var ROWS = 6
        private set

    /** Icon size on the home pages: [ICON_SMALL], [ICON_MEDIUM] or [ICON_LARGE]. */
    var iconSize = ICON_MEDIUM
        private set

    /** Apps the person hid (component keys "package/Class"): gone from the Home screen and the Apps screen. */
    var hidden: Set<String> = emptySet()
        private set

    /** Notification badges on app icons: [BADGE_OFF], [BADGE_DOT] or [BADGE_NUMBER]. */
    var badgeMode = BADGE_OFF
        private set

    /** The Edge panel (a handle on the right edge that opens a column of favourite apps). */
    var edgeOn = false
        private set

    /** Apps shown in the Edge panel (component keys). */
    var edgeApps: Set<String> = emptySet()
        private set

    /** True when the Home screen may turn sideways with the phone. */
    var landscape = false
        private set

    const val BADGE_OFF = 0
    const val BADGE_DOT = 1
    const val BADGE_NUMBER = 2

    /** Most apps the Edge panel holds. */
    const val EDGE_MAX_APPS = 12

    const val ICON_SMALL = 0
    const val ICON_MEDIUM = 1
    const val ICON_LARGE = 2

    /** Grids the settings offer, as (columns, rows). */
    val GRIDS = listOf(Pair(4, 5), Pair(4, 6), Pair(5, 5), Pair(5, 6))

    private const val KEY = "home_layout"
    private const val KEY_COLS = "grid_cols"
    private const val KEY_ROWS = "grid_rows"
    private const val KEY_ICON = "home_icon_size"
    private const val KEY_HIDDEN = "hidden_apps"
    private const val KEY_BADGES = "badge_mode"
    private const val KEY_EDGE_ON = "edge_on"
    private const val KEY_EDGE_APPS = "edge_apps"
    private const val KEY_LANDSCAPE = "landscape_on"

    /** Reads the settings into [COLS], [ROWS], [iconSize] and [hidden]. Call first thing when the launcher starts. */
    fun loadConfig(context: Context) {
        COLS = savedCols(context)
        ROWS = savedRows(context)
        iconSize = savedIconSize(context)
        hidden = savedHidden(context)
        badgeMode = savedBadgeMode(context)
        edgeOn = savedEdgeOn(context)
        edgeApps = savedEdgeApps(context)
        landscape = savedLandscape(context)
    }

    /** True when the saved settings are not the ones the launcher is showing right now. */
    fun configChanged(context: Context): Boolean =
        savedCols(context) != COLS || savedRows(context) != ROWS ||
            savedIconSize(context) != iconSize || savedHidden(context) != hidden ||
            savedBadgeMode(context) != badgeMode || savedEdgeOn(context) != edgeOn ||
            savedEdgeApps(context) != edgeApps || savedLandscape(context) != landscape

    fun savedCols(context: Context): Int = prefs(context).getInt(KEY_COLS, 4).coerceIn(4, 5)

    fun savedRows(context: Context): Int = prefs(context).getInt(KEY_ROWS, 6).coerceIn(5, 6)

    fun savedIconSize(context: Context): Int = prefs(context).getInt(KEY_ICON, ICON_MEDIUM).coerceIn(ICON_SMALL, ICON_LARGE)

    fun savedHidden(context: Context): Set<String> =
        HashSet(prefs(context).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet<String>())

    fun savedBadgeMode(context: Context): Int = prefs(context).getInt(KEY_BADGES, BADGE_OFF).coerceIn(BADGE_OFF, BADGE_NUMBER)

    fun savedEdgeOn(context: Context): Boolean = prefs(context).getBoolean(KEY_EDGE_ON, false)

    fun savedEdgeApps(context: Context): Set<String> =
        HashSet(prefs(context).getStringSet(KEY_EDGE_APPS, emptySet()) ?: emptySet<String>())

    fun savedLandscape(context: Context): Boolean = prefs(context).getBoolean(KEY_LANDSCAPE, false)

    fun setBadgeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_BADGES, mode.coerceIn(BADGE_OFF, BADGE_NUMBER)).apply()
    }

    fun setEdgeOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_EDGE_ON, on).apply()
    }

    fun setEdgeApps(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet(KEY_EDGE_APPS, HashSet(keys)).apply()
    }

    fun setLandscape(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_LANDSCAPE, on).apply()
    }

    fun setGrid(context: Context, cols: Int, rows: Int) {
        prefs(context).edit().putInt(KEY_COLS, cols.coerceIn(4, 5)).putInt(KEY_ROWS, rows.coerceIn(5, 6)).apply()
    }

    fun setIconSize(context: Context, size: Int) {
        prefs(context).edit().putInt(KEY_ICON, size.coerceIn(ICON_SMALL, ICON_LARGE)).apply()
    }

    fun setHidden(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet(KEY_HIDDEN, HashSet(keys)).apply()
    }

    /** The saved layout, or null on the very first run (nothing saved yet) or if the data is damaged. */
    fun load(context: Context): HomeLayout? =
        HomeLayout.parse(prefs(context).getString(KEY, null), COLS, ROWS)

    fun save(context: Context, layout: HomeLayout) {
        prefs(context).edit().putString(KEY, layout.serialize()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
}
