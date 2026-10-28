package com.oklabs.oneui9launcher

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.nfc.NfcAdapter

/** What a Quick panel button shows right now: lit or not, the small second line, and which icon. */
class TileState(val on: Boolean, val sub: String, val glyph: Int)

/** One kind of button the Quick panel can hold. */
class QuickTileDef(val id: String, val label: String, val glyph: Int)

/** The buttons the Quick panel knows, and which of them are on the panel (in which order). */
object QuickTiles {

    val BUILTIN: List<QuickTileDef> = listOf(
        QuickTileDef("wifi", "Wi-Fi", QuickGlyph.WIFI),
        QuickTileDef("bluetooth", "Bluetooth", QuickGlyph.BLUETOOTH),
        QuickTileDef("data", "Mobile data", QuickGlyph.DATA),
        QuickTileDef("airplane", "Flight mode", QuickGlyph.AIRPLANE),
        QuickTileDef("sound", "Sound", QuickGlyph.SOUND),
        QuickTileDef("torch", "Torch", QuickGlyph.TORCH),
        QuickTileDef("rotate", "Auto rotate", QuickGlyph.ROTATE),
        QuickTileDef("dnd", "Do not disturb", QuickGlyph.DND),
        QuickTileDef("location", "Location", QuickGlyph.LOCATION),
        QuickTileDef("hotspot", "Mobile hotspot", QuickGlyph.HOTSPOT),
        QuickTileDef("battery", "Battery saver", QuickGlyph.BATTERY),
        QuickTileDef("dark", "Dark mode", QuickGlyph.DARK),
        QuickTileDef("cast", "Screen cast", QuickGlyph.CAST),
        QuickTileDef("camera", "Camera", QuickGlyph.CAMERA),
        QuickTileDef("nfc", "NFC", QuickGlyph.NFC),
        QuickTileDef("night", "Night light", QuickGlyph.NIGHT)
    )

    /** Step 27: buttons found in the phone's own Quick settings that the launcher has no built-in button for (Speed up, Record screen, vivoshare...). */
    @Volatile
    private var custom: List<QuickTileDef> = emptyList()

    const val CUSTOM_PREFIX = "sys:"
    private const val KEY_CUSTOM = "qp_custom"

    val ALL: List<QuickTileDef> get() = BUILTIN + custom

    fun loadCustom(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CUSTOM, "") ?: ""
        custom = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .map { QuickTileDef(CUSTOM_PREFIX + it, it, QuickGlyphs.forLabel(it)) }
    }

    /** Remembers the found buttons; the ones never seen before are put at the end of the panel. */
    fun saveCustom(context: Context, labels: List<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadCustom(context)
        val known = custom.map { it.label }.toSet()
        val fresh = labels.map { it.trim() }.filter { it.isNotEmpty() && it !in known }.distinct()
        if (fresh.isEmpty()) return
        val all = custom.map { it.label } + fresh
        prefs.edit().putString(KEY_CUSTOM, all.joinToString("\n")).apply()
        loadCustom(context)
        val ids = order(context).map { it.id }.toMutableList()
        for (l in fresh) if (CUSTOM_PREFIX + l !in ids) ids.add(CUSTOM_PREFIX + l)
        save(context, ids)
    }

    fun cachedState(context: Context, id: String): Boolean? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains("qp_cs:$id")) p.getBoolean("qp_cs:$id", false) else null
    }

    fun cacheState(context: Context, id: String, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("qp_cs:$id", on).apply()
    }

    /** What a fresh install shows (the first ten); the other buttons wait under "Available buttons" in the editor. */
    val DEFAULT_ORDER: List<String> =
        listOf("wifi", "bluetooth", "data", "airplane", "sound", "torch", "rotate", "dnd", "location", "hotspot")

    /** The panel must keep at least this many buttons. */
    const val MIN_BUTTONS = 6

    /** Moves the button at [from] so it sits at insertion slot [slot] (0..size, slots are counted before the move). */
    fun moved(list: List<String>, from: Int, slot: Int): List<String> {
        if (from !in list.indices) return list
        val out = ArrayList(list)
        val id = out.removeAt(from)
        val at = (if (slot > from) slot - 1 else slot).coerceIn(0, out.size)
        out.add(at, id)
        return out
    }

    /** [list] with [id] inserted at [slot] (0..size); unchanged when it is already there or unknown. */
    fun added(list: List<String>, id: String, slot: Int): List<String> {
        if (def(id) == null || id in list) return list
        val out = ArrayList(list)
        out.add(slot.coerceIn(0, out.size), id)
        return out
    }

    /** [list] without [id]; unchanged when that would leave fewer than [MIN_BUTTONS]. */
    fun removed(list: List<String>, id: String): List<String> =
        if (list.size <= MIN_BUTTONS || id !in list) list else list.filter { it != id }

    private const val PREFS = "launcher"
    private const val KEY = "qp_tiles"

    fun def(id: String): QuickTileDef? = ALL.firstOrNull { it.id == id }

    /** Turns a saved "wifi,bluetooth,..." text into a list of known, different ids; falls back to the default when too few are left. */
    fun parse(saved: String?): List<String> {
        if (saved.isNullOrBlank()) return DEFAULT_ORDER
        val ids = saved.split(',').map { it.trim() }.filter { def(it) != null }.distinct()
        return if (ids.size >= 2) ids else DEFAULT_ORDER
    }

    /** The buttons on the panel, in order (the first two are drawn wide). */
    fun order(context: Context): List<QuickTileDef> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadCustom(context)
        var ids = parse(prefs.getString(KEY, null))
        // Step 26b: once, every button the panel knows is put on it (the extra pages hold the ones that did not fit), then the editor decides
        if (!prefs.getBoolean(KEY_ALL, false)) {
            ids = ids + ALL.map { it.id }.filter { it !in ids }
            prefs.edit().putString(KEY, ids.joinToString(",")).putBoolean(KEY_ALL, true).apply()
        }
        return ids.mapNotNull { def(it) }
    }

    private const val KEY_ALL = "qp_tiles_all26"

    fun save(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, ids.joinToString(",")).apply()
    }
}

/**
 * Step 16a: the phone side of the Quick panel buttons: reading each state and doing the tap / long-press.
 *
 * Android does not let an ordinary app switch Wi-Fi, Bluetooth, mobile data, flight mode or the hotspot on or off (only the system can),
 * so those buttons show the real state and open the phone's own small settings panel / page on tap. The torch, sound mode, auto rotate,
 * Do not disturb and brightness work directly (the last three after you allow them once in the phone's settings).
 */
class QuickSystem(context: Context, private val say: (String) -> Unit) {

    private val ctx: Context = context
    private val app: Context = context.applicationContext

    /** Called when something changed on its own (the torch was switched somewhere else). */
    var onChanged: (() -> Unit)? = null

    private var torchId: String? = null
    private var torchOn = false
    private var torchWatching = false
    private val camera: CameraManager? = try {
        app.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    } catch (e: Exception) {
        null
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != torchId) return
            torchOn = enabled
            onChanged?.invoke()
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId != torchId) return
            torchOn = false
            onChanged?.invoke()
        }
    }

    /** Start / stop listening to the torch (call when the panel opens / closes). */
    fun start() {
        val cm = camera ?: return
        if (torchId == null) torchId = findTorch(cm)
        if (torchId == null || torchWatching) return
        try {
            cm.registerTorchCallback(torchCallback, null)
            torchWatching = true
        } catch (e: Exception) {
            // no torch updates; the button still works
        }
    }

    fun stop() {
        val cm = camera ?: return
        if (!torchWatching) return
        try {
            cm.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            // already gone
        }
        torchWatching = false
    }

    private fun findTorch(cm: CameraManager): String? = try {
        var found: String? = null
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            if (c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
            if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                found = id
                break
            }
            if (found == null) found = id
        }
        found
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ states

    fun state(id: String): TileState = try {
        when (id) {
            "wifi" -> {
                val on = wifiOn()
                TileState(on, if (on) "On" else "Off", QuickGlyph.WIFI)
            }
            "bluetooth" -> {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) TileState(false, "Not available", QuickGlyph.BLUETOOTH)
                else TileState(adapter.isEnabled, if (adapter.isEnabled) "On" else "Off", QuickGlyph.BLUETOOTH)
            }
            "data" -> {
                val on = cellularKnown()
                TileState(on, if (on) "On" else "Off", QuickGlyph.DATA)
            }
            "airplane" -> {
                val on = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                TileState(on, if (on) "On" else "Off", QuickGlyph.AIRPLANE)
            }
            "sound" -> when (audio()?.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> TileState(false, "Vibrate", QuickGlyph.VIBRATE)
                AudioManager.RINGER_MODE_SILENT -> TileState(false, "Mute", QuickGlyph.MUTE)
                else -> TileState(true, "Sound", QuickGlyph.SOUND)
            }
            "torch" -> {
                if (torchId == null && camera != null) torchId = findTorch(camera)
                if (torchId == null) TileState(false, "Not available", QuickGlyph.TORCH)
                else TileState(torchOn, if (torchOn) "On" else "Off", QuickGlyph.TORCH)
            }
            "rotate" -> {
                val on = Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                TileState(on, if (on) "Auto rotate" else "Portrait", QuickGlyph.ROTATE)
            }
            "dnd" -> {
                val filter = notifications()?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                val on = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                TileState(on, if (on) "On" else "Off", QuickGlyph.DND)
            }
            "location" -> {
                val on = locationOn()
                TileState(on, if (on) "On" else "Off", QuickGlyph.LOCATION)
            }
            "hotspot" -> {
                val on = hotspotOn()
                TileState(on, if (on) "On" else "Off", QuickGlyph.HOTSPOT)
            }
            "nfc" -> {
                val adapter = NfcAdapter.getDefaultAdapter(app)
                if (adapter == null) TileState(false, "Not available", QuickGlyph.NFC)
                else TileState(adapter.isEnabled, if (adapter.isEnabled) "On" else "Off", QuickGlyph.NFC)
            }
            "night" -> {
                val on = nightOn()
                TileState(on, if (on) "On" else "Off", QuickGlyph.NIGHT)
            }
            "battery" -> {
                val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val on = pm?.isPowerSaveMode == true
                TileState(on, if (on) "On" else "Off", QuickGlyph.BATTERY)
            }
            "dark" -> {
                val night = android.content.res.Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val on = night == Configuration.UI_MODE_NIGHT_YES
                TileState(on, if (on) "On" else "Off", QuickGlyph.DARK)
            }
            "cast" -> TileState(false, "Tap", QuickGlyph.CAST)
            "camera" -> TileState(false, "Open", QuickGlyph.CAMERA)
            else -> if (id.startsWith(QuickTiles.CUSTOM_PREFIX)) {
                val on = QuickTiles.cachedState(app, id)
                TileState(on == true, when (on) { true -> "On"; false -> "Off"; null -> "" }, QuickTiles.def(id)?.glyph ?: QuickGlyph.GRID)
            } else TileState(false, "", QuickGlyph.GEAR)
        }
    } catch (e: Exception) {
        TileState(false, "", QuickTiles.def(id)?.glyph ?: QuickGlyph.GEAR)
    }

    private fun audio(): AudioManager? = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun notifications(): NotificationManager? = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    @Suppress("DEPRECATION")
    private fun wifiOn(): Boolean {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wifi.isWifiEnabled
    }

    /** True when a mobile-data network exists (it is there while mobile data is on, even when Wi-Fi is the one in use). */
    @Suppress("DEPRECATION")
    private fun cellularKnown(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) return true
        }
        return false
    }

    /** The hotspot state is not in the public API; the hidden call is tried and a phone that blocks it just shows "Off". */
    private fun hotspotOn(): Boolean = try {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val m = WifiManager::class.java.getDeclaredMethod("isWifiApEnabled")
        m.isAccessible = true
        (m.invoke(wifi) as? Boolean) == true
    } catch (e: Throwable) {
        false
    }

    /** Night light (stock Android) or Eye comfort (Samsung / some others). */
    private fun nightOn(): Boolean = try {
        Settings.Secure.getInt(app.contentResolver, "night_display_activated", 0) == 1 ||
            Settings.System.getInt(app.contentResolver, "blue_light_filter", 0) == 1
    } catch (e: Exception) {
        false
    }

    private fun locationOn(): Boolean {
        if (Build.VERSION.SDK_INT >= 28) {
            val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            return lm.isLocationEnabled
        }
        @Suppress("DEPRECATION")
        return Settings.Secure.getInt(app.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
    }

    // ------------------------------------------------------------------ taps

    /**
     * Step 26: every button switches with ONE tap and no popup. What the app may change itself (torch, sound mode, and auto rotate / Do not
     * disturb once allowed) is switched directly. Everything else Android keeps for the system (Wi-Fi, Bluetooth, mobile data, flight mode,
     * location, hotspot, battery saver, dark mode, cast, NFC, night light) is switched by the Accessibility service: it opens the phone's own
     * Quick settings for a moment, presses that button for you and closes it again. Without the service the phone's own screen opens instead.
     */
    fun tap(id: String) {
        when (id) {
            "sound" -> cycleSound()
            "torch" -> toggleTorch()
            "camera" -> {
                open(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                onLeave?.invoke()
            }
            "rotate" -> if (Settings.System.canWrite(app)) toggleRotate() else force(id)
            "dnd" -> if (notifications()?.isNotificationPolicyAccessGranted == true) toggleDnd() else force(id)
            else -> if (!secureToggle(id)) force(id)
        }
    }

    private fun canSecure(): Boolean =
        app.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Step 28b: with the one-time adb grant (WRITE_SECURE_SETTINGS) these are switched by writing the phone's own setting, with no help of the
     * Accessibility service. If the write fails or the phone does not take it, the usual way (the phone's Quick settings) is used.
     * Returns true when the switch was done here.
     */
    private fun secureToggle(id: String): Boolean {
        if (!canSecure()) return false
        val cr = app.contentResolver
        try {
            when (id) {
                "location" -> {
                    val on = locationOn()
                    Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, if (on) Settings.Secure.LOCATION_MODE_OFF else Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
                    return Settings.Secure.getInt(cr, Settings.Secure.LOCATION_MODE, -1) == (if (on) Settings.Secure.LOCATION_MODE_OFF else Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
                }
                "dark" -> {
                    val night = android.content.res.Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val want = if (night == Configuration.UI_MODE_NIGHT_YES) 1 else 2
                    Settings.Secure.putInt(cr, "ui_night_mode", want)
                    return Settings.Secure.getInt(cr, "ui_night_mode", -1) == want
                }
                "battery" -> {
                    val on = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
                    val want = if (on) 0 else 1
                    Settings.Global.putInt(cr, "low_power", want)
                    return Settings.Global.getInt(cr, "low_power", -1) == want
                }
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    /** Called when a tap sent the person to a phone screen: the panel should close. */
    var onLeave: (() -> Unit)? = null

    private fun labels(id: String): List<String> = when (id) {
        "wifi" -> listOf("wi-fi", "wifi", "internet", "@wide0")
        "bluetooth" -> listOf("bluetooth")
        "data" -> listOf("mobile data", "cellular data", "mobile network", "internet", "@wide1")
        "airplane" -> listOf("flight mode", "airplane mode", "aeroplane mode", "airplane")
        "rotate" -> listOf("auto rotate", "auto rotation", "autorotate", "rotation lock", "rotate", "portrait", "landscape")
        "dnd" -> listOf("do not disturb", "dnd")
        "location" -> listOf("location")
        "hotspot" -> listOf("mobile hotspot", "personal hotspot", "hotspot", "tethering")
        "battery" -> listOf("battery saver", "power saving", "power saver", "battery saving")
        "dark" -> listOf("dark mode", "dark theme", "dark")
        "cast" -> listOf("screen cast", "cast screen", "smart view", "screen mirroring", "cast")
        "nfc" -> listOf("nfc")
        "night" -> listOf("night light", "eye comfort", "eye protection", "night mode", "reading mode")
        else -> if (id.startsWith(QuickTiles.CUSTOM_PREFIX)) listOf(id.removePrefix(QuickTiles.CUSTOM_PREFIX)) else emptyList()
    }

    private fun norm(t: String): String = t.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex(" +"), " ")

    /** True when the phone's button [label] is one the launcher already has a button for (so it is not added a second time). */
    fun isBuiltinLabel(label: String): Boolean {
        val l = norm(label)
        val words = listOf("torch", "flashlight", "vibration", "vibrate", "sound", "mute", "silent", "camera", "wi fi", "wifi", "bluetooth")
        val all = QuickTiles.BUILTIN.flatMap { labels(it.id) }.filter { !it.startsWith("@") }.map { norm(it) } + words
        return all.any { v -> l == v || l.startsWith("$v ") }
    }

    /**
     * Reads the phone's own Quick settings (hidden behind a picture) and keeps every button the launcher has no button for.
     * [done]: worked, one line of what was seen, how many phone buttons are on the panel now.
     */
    fun scan(done: (Boolean, String, Int) -> Unit) {
        val svc = PanelAccessibilityService.instance
        if (svc == null) {
            done(false, "the Quick panel service is off", 0)
            return
        }
        svc.scanTiles { found, note ->
            if (found == null) {
                done(false, note, 0)
            } else {
                val custom = found.filter { !isBuiltinLabel(it.first) }
                QuickTiles.saveCustom(app, custom.map { it.first })
                for ((label, on) in custom) if (on != null) QuickTiles.cacheState(app, QuickTiles.CUSTOM_PREFIX + label, on)
                done(true, note, QuickTiles.ALL.count { it.id.startsWith(QuickTiles.CUSTOM_PREFIX) })
            }
        }
    }

    private var forceBusy = false
    private var forceAt = 0L

    private fun force(id: String) {
        val svc = PanelAccessibilityService.instance
        if (svc == null && !SystemPanel.wanted(app)) {
            // Step 50: the Accessibility service is not used (the panel over other apps needs none): the phone's own screen for this button
            if (id.startsWith(QuickTiles.CUSTOM_PREFIX)) say("This button of the phone needs the Accessibility service") else fallback(id)
            return
        }
        if (svc == null) {
            say("Turn on \"Quick panel\" in Accessibility: then every button switches in one tap")
            open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            onLeave?.invoke()
            return
        }
        if (forceBusy && android.os.SystemClock.uptimeMillis() - forceAt < 9000L) return
        forceBusy = true
        forceAt = android.os.SystemClock.uptimeMillis()
        svc.forceToggle(labels(id)) { ok, on ->
            forceBusy = false
            if (!ok) {
                say("Could not find that button. A report was copied: paste it in the chat with Claude")
                fallback(id)
            } else {
                if (on != null && id.startsWith(QuickTiles.CUSTOM_PREFIX)) QuickTiles.cacheState(app, id, on)
                onChanged?.invoke()
            }
        }
    }

    /** No service (or the phone has no such button): the phone's own screen for it. */
    private fun fallback(id: String) {
        if (id.startsWith(QuickTiles.CUSTOM_PREFIX)) return
        when (id) {
            "wifi" -> open(
                when {
                    Build.VERSION.SDK_INT >= 30 -> Settings.Panel.ACTION_WIFI
                    Build.VERSION.SDK_INT >= 29 -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                    else -> Settings.ACTION_WIFI_SETTINGS
                }
            )
            "bluetooth" -> open(Settings.ACTION_BLUETOOTH_SETTINGS)
            "data" -> open(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
            "airplane" -> open(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "rotate", "dark", "night" -> open(Settings.ACTION_DISPLAY_SETTINGS)
            "dnd" -> open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "location" -> open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "hotspot" -> openHotspot()
            "battery" -> open(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "cast" -> open(Settings.ACTION_CAST_SETTINGS)
            "nfc" -> open(Settings.ACTION_NFC_SETTINGS)
        }
        onLeave?.invoke()
    }

    /** Long-press: the phone's full settings page for the button. */
    fun longPress(id: String) {
        when (id) {
            "wifi" -> open(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> open(Settings.ACTION_BLUETOOTH_SETTINGS)
            "data" -> open(Settings.ACTION_DATA_ROAMING_SETTINGS)
            "airplane" -> open(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "sound", "dnd" -> open(Settings.ACTION_SOUND_SETTINGS)
            "torch" -> say("Torch has no settings page")
            "rotate", "dark" -> open(Settings.ACTION_DISPLAY_SETTINGS)
            "location" -> open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "hotspot" -> openHotspot()
            "battery" -> open(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "cast" -> open(Settings.ACTION_CAST_SETTINGS)
            "camera" -> open(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "nfc" -> open(Settings.ACTION_NFC_SETTINGS)
            "night" -> open(Settings.ACTION_DISPLAY_SETTINGS)
        }
    }

    private fun toggleTorch() {
        val cm = camera
        val idNow = torchId
        if (cm == null || idNow == null) {
            say("This phone has no torch")
            return
        }
        try {
            cm.setTorchMode(idNow, !torchOn)
            torchOn = !torchOn // the callback confirms it a moment later
        } catch (e: CameraAccessException) {
            say("The camera is busy right now")
        } catch (e: Exception) {
            say("Can't switch the torch")
        }
    }

    private fun cycleSound() {
        val am = audio() ?: return
        try {
            when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> {
                    try {
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (e: SecurityException) {
                        am.ringerMode = AudioManager.RINGER_MODE_NORMAL // Mute needs Do not disturb access: skip it
                    }
                }
                else -> am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            say("Can't change the sound mode")
        }
    }

    private fun toggleRotate() {
        if (!Settings.System.canWrite(app)) {
            askWriteSettings()
            return
        }
        try {
            val now = Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (now == 1) 0 else 1)
        } catch (e: Exception) {
            say("Can't change auto rotate")
        }
    }

    private fun toggleDnd() {
        val nm = notifications() ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            say("Allow \"Do not disturb\" access for this launcher, then tap again")
            open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return
        }
        try {
            val on = nm.currentInterruptionFilter.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        } catch (e: Exception) {
            say("Can't change Do not disturb")
        }
    }

    private fun openHotspot() {
        try {
            val tether = Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
            tether.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(tether)
        } catch (e: Exception) {
            open(Settings.ACTION_WIRELESS_SETTINGS)
        }
    }

    // ------------------------------------------------------------------ brightness

    /** Screen brightness 0..255 as the phone has it now. */
    fun brightness(): Int = try {
        Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(0, 255)
    } catch (e: Exception) {
        128
    }

    /** True when the launcher may change the brightness (the person allowed "Modify system settings"). */
    fun canSetBrightness(): Boolean = Settings.System.canWrite(app)

    /** Sets the brightness (1..255). False when the permission is missing (the caller then calls [askWriteSettings]). */
    fun setBrightness(value: Int): Boolean {
        if (!Settings.System.canWrite(app)) return false
        return try {
            Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun askWriteSettings() {
        say("Allow \"Modify system settings\" for this launcher, then try again")
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + app.packageName))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            open(Settings.ACTION_SETTINGS)
        }
    }

    /** Opens the phone's settings page for [action]; says so when the phone has none. */
    private fun open(action: String) {
        try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            say("This phone has no screen for that")
        } catch (e: SecurityException) {
            say("This phone does not allow that")
        }
    }
}
