package com.oklabs.oneui9launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Step 16d: the cards under the brightness slider of the Quick panel:
 *  - a row with **Media output** (where the sound goes; opens the phone's output switcher) and **Devices** (SmartThings / Google Home, or Bluetooth settings),
 *  - the **media card** (what is playing: art, title, artist, previous / play-pause / next) - only while something plays, and only with Notification access,
 *  - the **volume card**: a Media slider and an arrow that opens Ring and Alarm sliders.
 * [say] shows a short message; [onLeave] closes the panel (after something opened another screen).
 */
class QuickCards(context: Context, private val say: (String) -> Unit, private val onLeave: () -> Unit) : LinearLayout(context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val sessions = try {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    } catch (e: Exception) {
        null
    }
    private val listenerComponent = ComponentName(context, BadgeService::class.java)

    private var controller: MediaController? = null
    private var started = false
    private var receiverOn = false
    private var expanded = false

    private val outputSub = TextView(context)
    private val devicesSub = TextView(context)
    private val mediaCard = LinearLayout(context)
    private val art = ImageView(context)
    private val mediaApp = TextView(context)
    private val mediaTitle = TextView(context)
    private val mediaArtist = TextView(context)
    private val playGlyph = QuickGlyph(context, QuickGlyph.PLAY)
    private val sliders = ArrayList<Triple<Int, PillSlider, Int>>() // stream, slider, (unused)
    private val extraSliders = ArrayList<View>()
    private val chevron = QuickGlyph(context, QuickGlyph.SOUND)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            bindMedia()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            bindMedia()
        }

        override fun onSessionDestroyed() {
            controller = null
            bindMedia()
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVolumes()
        }
    }

    init {
        orientation = VERTICAL
        val c = context
        addView(deviceRow(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        buildMediaCard()
        mediaCard.visibility = GONE
        val mp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mp.topMargin = c.dp(12)
        addView(mediaCard, mp)

        val volume = buildVolumeCard()
        val vp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        vp.topMargin = c.dp(12)
        addView(volume, vp)
    }

    // ------------------------------------------------------------------ device row

    private fun deviceRow(): View {
        val c = context
        val row = LinearLayout(c)
        row.orientation = HORIZONTAL
        val output = pill(QuickGlyph.OUTPUT, "Media output", outputSub) { openOutput() }
        val devices = pill(QuickGlyph.DEVICES, "Devices", devicesSub) { openDevices() }
        val a = LayoutParams(0, c.dp(62), 1f)
        a.rightMargin = c.dp(5)
        val b = LayoutParams(0, c.dp(62), 1f)
        b.leftMargin = c.dp(5)
        row.addView(output, a)
        row.addView(devices, b)
        return row
    }

    private fun pill(glyphKind: Int, title: String, sub: TextView, onTap: () -> Unit): View {
        val c = context
        val box = LinearLayout(c)
        box.orientation = HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(c.dp(10), 0, c.dp(10), 0)
        box.background = roundRect(Look.inkA(34), c.dpf(31f))
        box.isClickable = true
        box.setOnClickListener { onTap() }
        val circle = FrameLayout(c)
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Look.inkA(30))
        }
        val glyph = QuickGlyph(c, glyphKind)
        glyph.tint = Look.textA(225)
        circle.addView(glyph, FrameLayout.LayoutParams(c.dp(26), c.dp(26), Gravity.CENTER))
        box.addView(circle, LayoutParams(c.dp(42), c.dp(42)))
        val texts = LinearLayout(c)
        texts.orientation = VERTICAL
        val label = TextView(c)
        label.text = title
        label.setTextColor(Look.text)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        label.maxLines = 1
        texts.addView(label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sub.setTextColor(Look.textA(170))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END
        texts.addView(sub, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tp.leftMargin = c.dp(10)
        box.addView(texts, tp)
        return box
    }

    /** Where the sound goes now: a Bluetooth speaker / headphones by name, a wired headset, or the phone. */
    private fun outputName(): String {
        val am = audio ?: return "Phone"
        try {
            val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val remote = outs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
            if (remote != null) return remote.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            val wired = outs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wired != null) return "Headphones"
        } catch (e: Throwable) {
            // an older phone that cannot list outputs: it is the phone
        }
        return "Phone"
    }

    /** The first of SmartThings / Google Home that is installed, or null. */
    private fun devicesApp(): Pair<String, String>? {
        val pm = context.packageManager
        for (pkg in listOf("com.samsung.android.oneconnect", "com.google.android.apps.chromecast.app")) {
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    return Pair(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
                }
            } catch (e: Exception) {
                // not installed (or not visible)
            }
        }
        return null
    }

    private fun startFirst(vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // try the next one
            }
        }
        return false
    }

    /**
     * Android has no public "switch output" screen, so this is best effort: the system's media output panel (with the playing app),
     * else the volume panel, else Bluetooth settings.
     */
    private fun openOutput() {
        val media = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
        media.putExtra("com.android.settings.panel.extra.PACKAGE_NAME", controller?.packageName ?: context.packageName)
        val ok = startFirst(
            media,
            Intent(Settings.Panel.ACTION_VOLUME),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        if (!ok) say("This phone has no screen for that") else onLeave()
    }

    private fun openDevices() {
        val app = devicesApp()
        val launch = app?.let { context.packageManager.getLaunchIntentForPackage(it.first) }
        val ok = if (launch != null) startFirst(launch) else startFirst(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        if (!ok) say("This phone has no screen for that") else onLeave()
    }

    // ------------------------------------------------------------------ media card

    private fun buildMediaCard() {
        val c = context
        mediaCard.orientation = HORIZONTAL
        mediaCard.gravity = Gravity.CENTER_VERTICAL
        mediaCard.setPadding(c.dp(12), c.dp(12), c.dp(8), c.dp(12))
        mediaCard.background = roundRect(Look.inkA(30), c.dpf(26f))
        mediaCard.isClickable = true
        mediaCard.setOnClickListener { openPlayer() }

        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.clipToOutline = true
        art.background = roundRect(Look.inkA(40), c.dpf(14f))
        art.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, c.dpf(14f))
            }
        }
        mediaCard.addView(art, LayoutParams(c.dp(56), c.dp(56)))

        val texts = LinearLayout(c)
        texts.orientation = VERTICAL
        mediaApp.setTextColor(Look.textA(150))
        mediaApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        mediaApp.maxLines = 1
        mediaApp.ellipsize = TextUtils.TruncateAt.END
        texts.addView(mediaApp, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        mediaTitle.setTextColor(Look.text)
        mediaTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        mediaTitle.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        mediaTitle.maxLines = 1
        mediaTitle.ellipsize = TextUtils.TruncateAt.END
        texts.addView(mediaTitle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        mediaArtist.setTextColor(Look.textA(190))
        mediaArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        mediaArtist.maxLines = 1
        mediaArtist.ellipsize = TextUtils.TruncateAt.END
        texts.addView(mediaArtist, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tp.leftMargin = c.dp(12)
        mediaCard.addView(texts, tp)

        mediaCard.addView(control(QuickGlyph(c, QuickGlyph.PREV), "Previous") { controller?.transportControls?.skipToPrevious() }, LayoutParams(c.dp(40), c.dp(40)))
        playGlyph.tint = Look.text
        mediaCard.addView(control(playGlyph, "Play or pause") {
            val ctl = controller
            if (ctl != null) {
                if (CardLogic.isPlaying(ctl.playbackState?.state ?: 0)) ctl.transportControls.pause() else ctl.transportControls.play()
            }
        }, LayoutParams(c.dp(44), c.dp(44)))
        mediaCard.addView(control(QuickGlyph(c, QuickGlyph.NEXT), "Next") { controller?.transportControls?.skipToNext() }, LayoutParams(c.dp(40), c.dp(40)))
    }

    private fun control(glyph: QuickGlyph, description: String, onTap: () -> Unit): View {
        glyph.tint = Look.text
        glyph.contentDescription = description
        glyph.isClickable = true
        glyph.setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        glyph.setOnClickListener { onTap() }
        return glyph
    }

    private fun openPlayer() {
        val ctl = controller ?: return
        try {
            val pi = ctl.sessionActivity
            if (pi != null) {
                pi.send()
                onLeave()
                return
            }
        } catch (e: Exception) {
            // fall back to opening the app
        }
        val launch = context.packageManager.getLaunchIntentForPackage(ctl.packageName)
        if (launch != null && startFirst(launch)) onLeave()
    }

    /** Chooses which session to show, and listens to it. */
    private fun pick(list: List<MediaController>) {
        val index = CardLogic.pickSession(
            list.map { it.playbackState?.state ?: 0 },
            list.map { it.metadata?.let { m -> titleOf(m).isNotEmpty() } ?: false }
        )
        val next = list.getOrNull(index)
        if (next?.sessionToken != controller?.sessionToken) {
            try {
                controller?.unregisterCallback(controllerCallback)
            } catch (e: Exception) {
                // already gone
            }
            controller = next
            try {
                next?.registerCallback(controllerCallback)
            } catch (e: Exception) {
                // the card still draws once
            }
        }
        bindMedia()
    }

    private fun titleOf(m: MediaMetadata): String =
        NotifLogic.firstText(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), m.getString(MediaMetadata.METADATA_KEY_TITLE))

    private fun bindMedia() {
        val ctl = controller
        val meta = ctl?.metadata
        if (ctl == null || meta == null || titleOf(meta).isEmpty()) {
            mediaCard.visibility = GONE
            return
        }
        mediaCard.visibility = VISIBLE
        mediaTitle.text = titleOf(meta)
        mediaArtist.text = NotifLogic.firstText(
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST), meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM)
        )
        mediaArtist.visibility = if (mediaArtist.text.isEmpty()) GONE else VISIBLE
        mediaApp.text = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(ctl.packageName, 0)).toString()
        } catch (e: Exception) {
            ctl.packageName
        }
        val bitmap: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art.setImageBitmap(bitmap)
        playGlyph.show(if (CardLogic.isPlaying(ctl.playbackState?.state ?: 0)) QuickGlyph.PAUSE else QuickGlyph.PLAY)
    }

    // ------------------------------------------------------------------ volume card

    private fun buildVolumeCard(): View {
        val c = context
        val card = LinearLayout(c)
        card.orientation = VERTICAL

        val top = LinearLayout(c)
        top.orientation = HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(volumeSlider(AudioManager.STREAM_MUSIC, QuickGlyph.SOUND, "Media volume"), LayoutParams(0, c.dp(PillSlider.HEIGHT_DP), 1f))
        val speaker = roundGlyphButton(c, chevron, "More volume sliders") { toggleExpanded() }
        val speakerLp = LayoutParams(c.dp(52), c.dp(52))
        speakerLp.leftMargin = c.dp(10)
        top.addView(speaker, speakerLp)
        card.addView(top, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        for ((stream, glyph, name) in listOf(
            Triple(AudioManager.STREAM_RING, QuickGlyph.BELL, "Ring volume"),
            Triple(AudioManager.STREAM_ALARM, QuickGlyph.ALARM, "Alarm volume")
        )) {
            val s = volumeSlider(stream, glyph, name)
            s.visibility = GONE
            val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(PillSlider.HEIGHT_DP))
            lp.topMargin = c.dp(8)
            lp.rightMargin = c.dp(62)
            card.addView(s, lp)
            extraSliders.add(s)
        }
        return card
    }

    private fun volumeSlider(stream: Int, glyph: Int, name: String): PillSlider {
        val slider = PillSlider(context, glyph)
        slider.contentDescription = name
        slider.onChange = { p, _ ->
            val am = audio
            if (am != null) {
                try {
                    am.setStreamVolume(stream, CardLogic.levelFor(p, am.getStreamMaxVolume(stream)), 0)
                } catch (e: SecurityException) {
                    // ring to silent while Do not disturb is limiting changes: Android says no
                    say("Android does not allow that change right now")
                }
            }
        }
        sliders.add(Triple(stream, slider, 0))
        return slider
    }

    private fun toggleExpanded() {
        expanded = !expanded
        for (v in extraSliders) v.visibility = if (expanded) VISIBLE else GONE
    }

    /** Moves the sliders to the phone's volumes (never one that is being dragged). */
    fun refreshVolumes() {
        val am = audio ?: return
        for ((stream, slider, _) in sliders) {
            if (!slider.isDragging) slider.progress = CardLogic.progressFor(am.getStreamVolume(stream), am.getStreamMaxVolume(stream))
        }
    }

    // ------------------------------------------------------------------ start / stop

    /** The panel opened: read the phone's state and listen for changes. */
    fun start() {
        if (started) return
        started = true
        refreshVolumes()
        outputSub.text = outputName()
        devicesSub.text = devicesApp()?.second ?: "Bluetooth"
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            filter.addAction(AudioManager.ACTION_HEADSET_PLUG)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(volumeReceiver, filter)
            }
            receiverOn = true
        } catch (e: Exception) {
            // the sliders then only update when the panel opens
        }
        // Media sessions can only be read with Notification access.
        if (Badges.accessGranted(context)) {
            try {
                sessions?.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                pick(sessions?.getActiveSessions(listenerComponent) ?: emptyList())
            } catch (e: SecurityException) {
                mediaCard.visibility = GONE
            } catch (e: Exception) {
                mediaCard.visibility = GONE
            }
        }
    }

    /** The panel closed: stop listening. */
    fun stop() {
        if (!started) return
        started = false
        if (receiverOn) {
            receiverOn = false
            try {
                context.unregisterReceiver(volumeReceiver)
            } catch (e: Exception) {
                // already gone
            }
        }
        try {
            sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
            controller?.unregisterCallback(controllerCallback)
        } catch (e: Exception) {
            // already gone
        }
        controller = null
    }

    companion object {
        /** "Volume and media in Quick panel" (Settings > Gestures), on by default. */
        const val PREF_KEY = "qp_cards"
    }
}
