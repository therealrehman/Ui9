package com.oklabs.oneui9launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Step 20: the music player in the lower area of the Quick panel (above the notifications).
 * It is always there. While something plays it shows the art, title, artist, a seek bar and previous / play-pause / next
 * (what is playing needs Notification access). When nothing plays it shows the phone's own music app (its icon and name);
 * the buttons then send the phone's media keys, and a tap opens that app. A tap on the card opens the playing app.
 */
class QuickPlayer(context: Context, private val onLeave: () -> Unit) : LinearLayout(context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val sessions = try {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    } catch (e: Exception) {
        null
    }
    private val listenerComponent = ComponentName(context, BadgeService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var controller: MediaController? = null
    private var started = false

    private val art = ImageView(context)
    private val appLabel = TextView(context)
    private val title = TextView(context)
    private val artist = TextView(context)
    private val playGlyph = QuickGlyph(context, QuickGlyph.PLAY)
    private val seek = Seek(context)
    private val musicPackage: String? by lazy { findMusicApp() }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            bind()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            bind()
        }

        override fun onSessionDestroyed() {
            controller = null
            bind()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateSeek()
            if (started && CardLogic.isPlaying(controller?.playbackState?.state ?: 0)) handler.postDelayed(this, 1000)
        }
    }

    init {
        orientation = VERTICAL
        val c = context
        setPadding(c.dp(12), c.dp(10), c.dp(12), c.dp(4))
        background = roundRect(Look.glass(110), c.dpf(26f))
        isClickable = true
        setOnClickListener { openPlayer() }

        val top = LinearLayout(c)
        top.orientation = HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.clipToOutline = true
        art.background = roundRect(Look.inkA(40), c.dpf(16f))
        art.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, c.dpf(16f))
            }
        }
        top.addView(art, LayoutParams(c.dp(56), c.dp(56)))

        val texts = LinearLayout(c)
        texts.orientation = VERTICAL
        appLabel.setTextColor(Look.textA(150))
        appLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        appLabel.maxLines = 1
        appLabel.ellipsize = TextUtils.TruncateAt.END
        texts.addView(appLabel, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        texts.addView(title, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        artist.setTextColor(Look.textA(190))
        artist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        artist.maxLines = 1
        artist.ellipsize = TextUtils.TruncateAt.END
        texts.addView(artist, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tp.leftMargin = c.dp(14)
        top.addView(texts, tp)
        addView(top, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        seek.onSeek = { p ->
            val ctl = controller
            val dur = ctl?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (ctl != null && dur > 0) ctl.transportControls.seekTo((p * dur).toLong())
        }
        val sp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(22))
        sp.topMargin = c.dp(2)
        addView(seek, sp)

        val controls = LinearLayout(c)
        controls.orientation = HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.addView(control(QuickGlyph(c, QuickGlyph.PREV), "Previous") {
            val ctl = controller
            if (ctl != null) ctl.transportControls.skipToPrevious() else sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }, LayoutParams(c.dp(52), c.dp(44)))

        val playWrap = FrameLayout(c)
        playWrap.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Look.text)
        }
        playGlyph.tint = if (Look.dark) Color.rgb(24, 26, 32) else Color.WHITE
        playWrap.addView(playGlyph, FrameLayout.LayoutParams(c.dp(30), c.dp(30), Gravity.CENTER))
        playWrap.contentDescription = "Play or pause"
        playWrap.isClickable = true
        playWrap.setOnClickListener {
            val ctl = controller
            if (ctl != null) {
                if (CardLogic.isPlaying(ctl.playbackState?.state ?: 0)) ctl.transportControls.pause() else ctl.transportControls.play()
            } else {
                sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
        val pp = LayoutParams(c.dp(48), c.dp(48))
        pp.leftMargin = c.dp(14)
        pp.rightMargin = c.dp(14)
        controls.addView(playWrap, pp)

        controls.addView(control(QuickGlyph(c, QuickGlyph.NEXT), "Next") {
            val ctl = controller
            if (ctl != null) ctl.transportControls.skipToNext() else sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }, LayoutParams(c.dp(52), c.dp(44)))
        addView(controls, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        bind()
    }

    private fun control(glyph: QuickGlyph, description: String, onTap: () -> Unit): View {
        glyph.tint = Look.text
        glyph.contentDescription = description
        glyph.isClickable = true
        glyph.setPadding(context.dp(9), context.dp(9), context.dp(9), context.dp(9))
        glyph.setOnClickListener { onTap() }
        return glyph
    }

    /** The media key goes to whatever the phone is playing (or last played). */
    private fun sendKey(code: Int) {
        try {
            val am = audio ?: return
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) {
            // the phone refused: nothing to do
        }
    }

    // ------------------------------------------------------------------ the phone's own music app

    private fun findMusicApp(): String? {
        val pm = context.packageManager
        try {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            val pkg = pm.resolveActivity(intent, 0)?.activityInfo?.packageName
            if (pkg != null && pkg != "android" && pm.getLaunchIntentForPackage(pkg) != null) return pkg
        } catch (e: Exception) {
            // try the known names
        }
        for (pkg in KNOWN_MUSIC) {
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) return pkg
            } catch (e: Exception) {
                // not installed
            }
        }
        return null
    }

    private fun appName(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun appIcon(pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    private fun openApp(pkg: String?): Boolean {
        if (pkg == null) return false
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openPlayer() {
        val ctl = controller
        if (ctl != null) {
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
            if (openApp(ctl.packageName)) onLeave()
            return
        }
        if (openApp(musicPackage)) onLeave()
    }

    // ------------------------------------------------------------------ what is playing

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
        bind()
    }

    private fun titleOf(m: MediaMetadata): String =
        NotifLogic.firstText(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), m.getString(MediaMetadata.METADATA_KEY_TITLE))

    private fun bind() {
        val ctl = controller
        val meta = ctl?.metadata
        if (ctl == null || meta == null || titleOf(meta).isEmpty()) {
            bindIdle()
            return
        }
        title.text = titleOf(meta)
        val by = NotifLogic.firstText(
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST), meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM)
        )
        artist.text = by
        artist.visibility = if (by.isEmpty()) GONE else VISIBLE
        appLabel.text = appName(ctl.packageName)
        val bitmap: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) art.setImageBitmap(bitmap) else art.setImageDrawable(appIcon(ctl.packageName))
        val playing = CardLogic.isPlaying(ctl.playbackState?.state ?: 0)
        playGlyph.show(if (playing) QuickGlyph.PAUSE else QuickGlyph.PLAY)
        updateSeek()
        handler.removeCallbacks(ticker)
        if (started && playing) handler.postDelayed(ticker, 1000)
    }

    /** Nothing is playing (or Notification access is off): the phone's own music app. */
    private fun bindIdle() {
        val pkg = musicPackage
        appLabel.text = if (pkg != null) appName(pkg) else "Music"
        title.text = "Nothing playing"
        artist.text = if (pkg != null) "Tap to open" else "No music app found"
        artist.visibility = VISIBLE
        art.setImageDrawable(if (pkg != null) appIcon(pkg) else null)
        playGlyph.show(QuickGlyph.PLAY)
        seek.visibility = GONE
        handler.removeCallbacks(ticker)
    }

    private fun updateSeek() {
        val ctl = controller ?: return
        val st = ctl.playbackState
        val dur = ctl.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (st == null || dur <= 0L || st.position < 0L) {
            seek.visibility = GONE
            return
        }
        seek.visibility = VISIBLE
        var pos = st.position
        if (CardLogic.isPlaying(st.state) && st.lastPositionUpdateTime > 0L) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        if (!seek.isDragging) seek.progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------ start / stop

    /** The panel opened: find what is playing and listen for changes. */
    fun start() {
        if (started) return
        started = true
        if (Badges.accessGranted(context)) {
            try {
                sessions?.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                pick(sessions?.getActiveSessions(listenerComponent) ?: emptyList())
            } catch (e: Exception) {
                bindIdle()
            }
        } else {
            bindIdle()
        }
    }

    /** The panel closed: stop listening. */
    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(ticker)
        try {
            sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
            controller?.unregisterCallback(controllerCallback)
        } catch (e: Exception) {
            // already gone
        }
        controller = null
    }

    // ------------------------------------------------------------------ the seek bar

    /** A thin bar: tap or drag to jump in the song. */
    private class Seek(context: Context) : View(context) {
        var progress = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        var onSeek: ((Float) -> Unit)? = null
        var isDragging = false
            private set

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val h = context.dpf(4f)
            val top = (height - h) / 2f
            val r = h / 2f
            paint.color = Look.inkA(55)
            rect.set(0f, top, width.toFloat(), top + h)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.color = Look.text
            rect.set(0f, top, width * progress, top + h)
            canvas.drawRoundRect(rect, r, r, paint)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    progress = (ev.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                }
                MotionEvent.ACTION_MOVE -> progress = (ev.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                MotionEvent.ACTION_UP -> {
                    progress = (ev.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    isDragging = false
                    onSeek?.invoke(progress)
                }
                MotionEvent.ACTION_CANCEL -> isDragging = false
            }
            return true
        }
    }

    private companion object {
        /** Music apps that come with phones, tried when Android does not name a default one. */
        val KNOWN_MUSIC = listOf(
            "com.android.bbkmusic", "com.vivo.music", "com.sec.android.app.music", "com.samsung.android.app.musicplayer",
            "com.miui.player", "com.heytap.music", "com.oppo.music", "com.coloros.music", "com.android.music",
            "com.google.android.apps.youtube.music", "com.spotify.music"
        )
    }
}
