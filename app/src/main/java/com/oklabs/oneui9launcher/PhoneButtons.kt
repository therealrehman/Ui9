package com.oklabs.oneui9launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Step 60: "Phone buttons" WITHOUT Accessibility (blueprint BLUEPRINT_PHONE_BUTTONS, honest limit in section 1).
 *
 * Android gives no way to read or press another app's / SystemUI's Quick Settings tiles without the Accessibility service. So these buttons can only
 * LIST what legal framework calls can find and OPEN the right screen or app when tapped. They can not show On / Off and can not switch an OEM tile.
 *
 * Three sources feed one list; the person ticks what they want; the ticked ones become buttons of the Quick panel:
 *  - A: Quick Settings tiles of installed apps (`app:<package>/<class>`), found with PackageManager (needs the QS_TILE `<queries>` line).
 *  - B: the phone's own screens (`scr:<key>`), a curated table; an entry shows only when the phone can open it.
 *  - C: the phone's own tile list (secure setting `sysui_qs_tiles`), read only when the person asks: shown / copied, or used to match the order.
 * The old `sys:` buttons (Accessibility scan, debug build only) are untouched.
 */
object PhoneButtons {
    const val APP_PREFIX = "app:"
    const val SCR_PREFIX = "scr:"
    const val KEY_PHONE = "qp_phone"
    private const val QS_TILE = "android.service.quicksettings.action.QS_TILE"
    private const val QS_TILE_PREFS = "android.service.quicksettings.action.QS_TILE_PREFERENCES"
    private const val TILE_SETTING = "sysui_qs_tiles"

    /** One phone button: its id and the name shown. */
    class Entry(val id: String, val label: String)

    // ------------------------------------------------------------------ pure helpers (no phone needed)

    fun isPhoneButtonId(id: String): Boolean = id.startsWith(APP_PREFIX) || id.startsWith(SCR_PREFIX)

    fun idForTile(pkg: String, cls: String): String = "$APP_PREFIX$pkg/$cls"

    /** "app:com.pkg/com.pkg.TileClass" -> ("com.pkg", "com.pkg.TileClass"); null for anything else. */
    fun splitTileId(id: String): Pair<String, String>? {
        if (!id.startsWith(APP_PREFIX)) return null
        val rest = id.removePrefix(APP_PREFIX)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.length - 1) return null
        return Pair(rest.substring(0, slash), rest.substring(slash + 1))
    }

    /** Lines "id|label" (split at the FIRST "|", so a label may contain one). A label never holds a new line. */
    fun encode(entries: List<Entry>): String =
        entries.joinToString("\n") { it.id + "|" + it.label.replace('\n', ' ').replace('\r', ' ').trim() }

    fun decode(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<Entry>()
        val seen = HashSet<String>()
        for (line in raw.split('\n')) {
            val bar = line.indexOf('|')
            if (bar <= 0) continue
            val id = line.substring(0, bar).trim()
            val label = line.substring(bar + 1).trim()
            if (!isPhoneButtonId(id) || label.isEmpty() || !seen.add(id)) continue
            out.add(Entry(id, label))
        }
        return out
    }

    /** Splits "wifi,cell,custom(com.a/.B),bt" on commas that are NOT inside brackets; trims, drops blanks. */
    fun parseTileSpecs(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        fun flush() {
            val t = sb.toString().trim()
            if (t.isNotEmpty()) out.add(t)
            sb.setLength(0)
        }
        for (ch in raw) {
            when {
                ch == '(' -> {
                    depth++
                    sb.append(ch)
                }
                ch == ')' -> {
                    if (depth > 0) depth--
                    sb.append(ch)
                }
                ch == ',' && depth == 0 -> flush()
                else -> sb.append(ch)
            }
        }
        flush()
        return out
    }

    /** "custom(com.pkg/.TileClass)" -> Source-A id "app:com.pkg/com.pkg.TileClass"; null when [spec] is not a custom tile. */
    fun customSpecToId(spec: String): String? {
        if (!spec.startsWith("custom(") || !spec.endsWith(")")) return null
        val inner = spec.substring(7, spec.length - 1).trim()
        val slash = inner.indexOf('/')
        if (slash <= 0 || slash == inner.length - 1) return null
        val pkg = inner.substring(0, slash)
        var cls = inner.substring(slash + 1)
        if (cls.startsWith(".")) cls = pkg + cls
        return idForTile(pkg, cls)
    }

    /** One spec of the phone's tile list -> a built-in button id, or an `app:` id for a custom tile; null = not known (unknown specs only show in the diagnostic). */
    fun mapSpecToBuiltin(spec: String): String? = when (spec) {
        "wifi", "internet" -> "wifi"
        "bt" -> "bluetooth"
        "cell" -> "data"
        "airplane" -> "airplane"
        "flashlight" -> "torch"
        "rotation" -> "rotate"
        "dnd" -> "dnd"
        "location" -> "location"
        "hotspot" -> "hotspot"
        "saver" -> "battery"
        "dark" -> "dark"
        "cast" -> "cast"
        "nfc" -> "nfc"
        "night" -> "night"
        else -> customSpecToId(spec)
    }

    class Match(val ids: List<String>, val newApps: List<Entry>)

    /**
     * "Match my phone's panel": the built-in buttons that are in the phone's list, in the phone's order, then the `app:` tiles found
     * ([appLabel] gives a tile's name, or null when this phone can not see that tile: it is skipped), and then every button the panel already
     * had that is not in the phone's list (nothing is lost; they stay after the matched ones). [known] = ids the panel can show.
     */
    fun match(specs: List<String>, current: List<String>, known: Set<String>, appLabel: (String, String) -> String?): Match {
        val ids = ArrayList<String>()
        val apps = ArrayList<Entry>()
        for (spec in specs) {
            val id = mapSpecToBuiltin(spec) ?: continue
            if (id in ids) continue
            if (id.startsWith(APP_PREFIX)) {
                val (pkg, cls) = splitTileId(id) ?: continue
                val label = appLabel(pkg, cls) ?: continue
                apps.add(Entry(id, label))
                ids.add(id)
            } else if (id in known) {
                ids.add(id)
            }
        }
        for (id in current) if (id !in ids) ids.add(id)
        return Match(ids, apps)
    }

    // ------------------------------------------------------------------ Source A: Quick Settings tiles of installed apps

    @Suppress("DEPRECATION")
    private fun queryTileServices(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(QS_TILE)
        return try {
            if (Build.VERSION.SDK_INT >= 33) pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            else pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The tiles other apps offer (this launcher's own tile is skipped), newest label order. Empty when the phone hides them. */
    fun appTiles(context: Context): List<Entry> {
        val pm = context.packageManager
        val out = ArrayList<Entry>()
        val seen = HashSet<String>()
        for (ri in queryTileServices(pm)) {
            val si = ri.serviceInfo ?: continue
            if (si.packageName == context.packageName) continue
            val id = idForTile(si.packageName, si.name)
            if (!seen.add(id)) continue
            var label = try {
                si.loadLabel(pm).toString().trim()
            } catch (e: Exception) {
                ""
            }
            if (label.isEmpty()) {
                label = try {
                    si.applicationInfo?.loadLabel(pm)?.toString()?.trim() ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
            if (label.isEmpty()) label = si.packageName
            out.add(Entry(id, label))
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** The name of one tile service, or null when this phone can not see it (used by "Match my phone's panel"). */
    fun tileLabel(context: Context, pkg: String, cls: String): String? = try {
        val pm = context.packageManager
        val si = pm.getServiceInfo(ComponentName(pkg, cls), 0)
        val l = si.loadLabel(pm).toString().trim()
        if (l.isNotEmpty()) l else si.applicationInfo?.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: pkg
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ Source B: the phone's own screens

    /** One candidate way to open a screen. Builds the intent, or null when it does not apply to this Android version. */
    private class Candidate(val standard: Boolean, val build: (PackageManager) -> Intent?)

    private class Screen(val key: String, val label: String, val glyph: Int, val candidates: List<Candidate>)

    private fun action(a: String, minSdk: Int = 0) = Candidate(true) { if (Build.VERSION.SDK_INT >= minSdk) Intent(a) else null }

    private fun component(pkg: String, cls: String) = Candidate(true) { Intent().setClassName(pkg, cls) }

    /** A launcher intent of a vendor app (UNVERIFIED guesses: hidden when the phone has no such app). */
    private fun launch(pkg: String) = Candidate(false) { it.getLaunchIntentForPackage(pkg) }

    /**
     * One line per screen. Standard Android first (safe), then Huawei / Honor extras which are UNVERIFIED guesses: any that does not resolve
     * on the phone is hidden and the owner reports which ones show up. Easy to extend: add one `Screen(...)` line.
     */
    private val SCREENS: List<Screen> = listOf(
        Screen("wifi", "Wi-Fi / Internet", QuickGlyph.WIFI, listOf(action(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, 29), action(Settings.ACTION_WIFI_SETTINGS))),
        Screen("bluetooth", "Bluetooth", QuickGlyph.BLUETOOTH, listOf(action(Settings.ACTION_BLUETOOTH_SETTINGS))),
        Screen("hotspot", "Hotspot / Tethering", QuickGlyph.HOTSPOT, listOf(component("com.android.settings", "com.android.settings.TetherSettings"), action(Settings.ACTION_WIRELESS_SETTINGS))),
        Screen("nfc", "NFC", QuickGlyph.NFC, listOf(action(Settings.ACTION_NFC_SETTINGS))),
        Screen("cast", "Cast / Screen mirroring", QuickGlyph.CAST, listOf(action(Settings.ACTION_CAST_SETTINGS))),
        Screen("data", "Data usage", QuickGlyph.DATA, listOf(action("android.settings.DATA_USAGE_SETTINGS"), action(Settings.ACTION_DATA_ROAMING_SETTINGS))),
        Screen("display", "Display", QuickGlyph.SUN, listOf(action(Settings.ACTION_DISPLAY_SETTINGS))),
        Screen("sound", "Sound", QuickGlyph.SOUND, listOf(action(Settings.ACTION_SOUND_SETTINGS))),
        Screen("saver", "Battery saver", QuickGlyph.BATTERY, listOf(action(Settings.ACTION_BATTERY_SAVER_SETTINGS))),
        Screen("location", "Location", QuickGlyph.LOCATION, listOf(action(Settings.ACTION_LOCATION_SOURCE_SETTINGS))),
        Screen("dnd", "Do not disturb", QuickGlyph.DND, listOf(action("android.settings.ZEN_MODE_PRIORITY_SETTINGS"), action("android.settings.ZEN_MODE_SETTINGS"))),
        Screen("date", "Date and time", QuickGlyph.ALARM, listOf(action(Settings.ACTION_DATE_SETTINGS))),
        Screen("access", "Accessibility settings", QuickGlyph.GEAR, listOf(action(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
        Screen("storage", "Storage", QuickGlyph.GRID, listOf(action(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))),
        // Huawei / Honor extras (UNVERIFIED)
        Screen("eye", "Eye comfort (opens Display)", QuickGlyphs.forLabel("eye comfort"), listOf(action(Settings.ACTION_DISPLAY_SETTINGS))),
        Screen("hw_record", "Screen recorder", QuickGlyphs.forLabel("screen record"), listOf(launch("com.huawei.screenrecorder"))),
        Screen("hw_share", "Huawei Share", QuickGlyphs.forLabel("share"), listOf(launch("com.huawei.android.instantshare"))),
        Screen("hw_manager", "Phone Manager", QuickGlyphs.forLabel("phone manager"), listOf(launch("com.huawei.systemmanager"))),
        Screen("hw_update", "System update", QuickGlyphs.forLabel("sync"), listOf(launch("com.huawei.android.hwouc")))
    )

    private fun key(id: String) = id.removePrefix(SCR_PREFIX)

    private fun screen(id: String): Screen? = SCREENS.firstOrNull { it.key == key(id) }

    /** True when the phone can show this intent. If even the main Settings screen does not resolve, package hiding is the cause, so a standard screen stays listed. */
    private fun canOpen(pm: PackageManager, intent: Intent, standard: Boolean): Boolean {
        val resolves = try {
            intent.resolveActivity(pm) != null
        } catch (e: Exception) {
            false
        }
        if (resolves) return true
        if (!standard || Build.VERSION.SDK_INT < 30) return false
        return try {
            Intent(Settings.ACTION_SETTINGS).resolveActivity(pm) == null
        } catch (e: Exception) {
            false
        }
    }

    /** The first way of [s] this phone can open, or null (the entry is then hidden). */
    private fun firstIntent(pm: PackageManager, s: Screen): Intent? {
        for (c in s.candidates) {
            val intent = try {
                c.build(pm)
            } catch (e: Exception) {
                null
            } ?: continue
            if (canOpen(pm, intent, c.standard)) return intent
        }
        return null
    }

    /** The phone's screens that exist on this phone. */
    fun screens(context: Context): List<Entry> {
        val pm = context.packageManager
        return SCREENS.filter { firstIntent(pm, it) != null }.map { Entry(SCR_PREFIX + it.key, it.label) }
    }

    /** The icon for a phone button (a screen has its own; an app tile is chosen by its name; the rest shows the first letter). */
    fun glyphFor(id: String, label: String): Int =
        if (id.startsWith(SCR_PREFIX)) screen(id)?.glyph ?: QuickGlyphs.forLabel(label) else QuickGlyphs.forLabel(label)

    // ------------------------------------------------------------------ tap

    /** Opens what the button stands for (NEW_TASK is added, so it also works from the overlay service). Says why when it can not. */
    fun open(context: Context, id: String, say: (String) -> Unit): Boolean {
        val pm = context.packageManager
        val intent: Intent? = if (id.startsWith(APP_PREFIX)) {
            val (pkg, cls) = splitTileId(id) ?: return false
            val prefs = Intent(QS_TILE_PREFS).setPackage(pkg).putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(pkg, cls))
            if (canOpen(pm, prefs, false)) prefs else pm.getLaunchIntentForPackage(pkg)
        } else {
            screen(id)?.let { firstIntent(pm, it) }
        }
        if (intent == null) {
            say("Can not open that on this phone")
            return false
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            say("This phone has no screen for that")
            false
        } catch (e: SecurityException) {
            say("This phone does not allow that")
            false
        } catch (e: Exception) {
            say("Can not open that on this phone")
            false
        }
    }

    // ------------------------------------------------------------------ Source C: the phone's own tile list

    /** The raw value of the phone's tile list, or null / empty when this phone does not share it (EMUI and some others). Never throws. */
    fun readTileList(context: Context): String? = try {
        Settings.Secure.getString(context.contentResolver, TILE_SETTING)
    } catch (e: Exception) {
        null
    }

    /** Text for the diagnostic dialog (and the Copy button): the raw value and the parsed list with what each spec maps to. */
    fun diagnostic(context: Context): String {
        val raw = readTileList(context)
        val specs = parseTileSpecs(raw)
        val sb = StringBuilder()
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" / ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        if (raw.isNullOrBlank()) {
            sb.append("sysui_qs_tiles: (empty - this phone does not share its tile list)")
            return sb.toString()
        }
        sb.append("sysui_qs_tiles: ").append(raw).append("\n\nParsed (").append(specs.size).append("):\n")
        for (s in specs) {
            val id = mapSpecToBuiltin(s)
            sb.append("- ").append(s).append("  ->  ").append(id ?: "unknown").append('\n')
        }
        return sb.toString().trimEnd()
    }
}
