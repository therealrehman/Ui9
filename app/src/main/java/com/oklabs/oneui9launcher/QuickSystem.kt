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

/** What a Quick panel button shows right now: lit or not, the small second line, and which icon. */
class TileState(val on: Boolean, val sub: String, val glyph: Int)

/** One kind of button the Quick panel can hold. */
class QuickTileDef(val id: String, val label: String, val glyph: Int)

/** The buttons the Quick panel knows, and which of them are on the panel (in which order). */
object QuickTiles {

    val ALL: List<QuickTileDef> = listOf(
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
        QuickTileDef("camera", "Camera", QuickGlyph.CAMERA)
    )

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
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return parse(saved).mapNotNull { def(it) }
    }

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
            "hotspot" -> TileState(false, "Settings", QuickGlyph.HOTSPOT)
            "battery" -> {
                val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val on = pm?.isPowerSaveMode == true
                TileState(on, if (on) "On" else "Off", QuickGlyph.BATTERY)
            }
            "dark" -> {
                val night = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val on = night == Configuration.UI_MODE_NIGHT_YES
                TileState(on, if (on) "On" else "Off", QuickGlyph.DARK)
            }
            "cast" -> TileState(false, "Settings", QuickGlyph.CAST)
            "camera" -> TileState(false, "Open", QuickGlyph.CAMERA)
            else -> TileState(false, "", QuickGlyph.GEAR)
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

    private fun locationOn(): Boolean {
        if (Build.VERSION.SDK_INT >= 28) {
            val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            return lm.isLocationEnabled
        }
        @Suppress("DEPRECATION")
        return Settings.Secure.getInt(app.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
    }

    // ------------------------------------------------------------------ taps

    fun tap(id: String) {
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
            "sound" -> cycleSound()
            "torch" -> toggleTorch()
            "rotate" -> toggleRotate()
            "dnd" -> toggleDnd()
            "location" -> open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "hotspot" -> openHotspot()
            "battery" -> open(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "dark" -> open(Settings.ACTION_DISPLAY_SETTINGS)
            "cast" -> open(Settings.ACTION_CAST_SETTINGS)
            "camera" -> open(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        }
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
                        say("Mute needs \"Do not disturb\" access: allow it, then tap again")
                        open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
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
