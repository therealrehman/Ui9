package com.oklabs.oneui9launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * Step 12a: the launcher's own colourful icons for the phone's system apps (Phone, Messages, Contacts, Camera, Albums / Gallery,
 * Browser, Calendar, Clock, Calculator, Files, Settings, Weather). Every picture is drawn in code from simple shapes: nothing is copied.
 * Other apps keep their own icon (cut to the squircle as before). Off = every app keeps its own icon.
 * Themed icons (Step 8d) take over when they are on.
 */
object IconPack {

    private const val PREFS = "launcher"
    private const val KEY = "icon_pack"

    enum class Style { PHONE, MESSAGES, CONTACTS, CAMERA, GALLERY, BROWSER, CALENDAR, CLOCK, CALCULATOR, FILES, SETTINGS, WEATHER }

    @Volatile
    var enabled = true
        private set

    fun load(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun toggle(context: Context) {
        enabled = !enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }

    fun modeName(): String = if (enabled) "Colourful" else "Original"

    /** Part of the icon cache key: changes with the switch and, for the Calendar icon, with the day. */
    val signature: String
        get() = if (enabled) "pack" + Calendar.getInstance().get(Calendar.DAY_OF_YEAR) else "own"

    // ------------------------------------------------------------------ which app gets which picture

    private val BY_LABEL: Map<String, Style> = mapOf(
        "phone" to Style.PHONE, "dialer" to Style.PHONE,
        "messages" to Style.MESSAGES, "messaging" to Style.MESSAGES, "message" to Style.MESSAGES, "sms" to Style.MESSAGES,
        "contacts" to Style.CONTACTS,
        "camera" to Style.CAMERA,
        "albums" to Style.GALLERY, "gallery" to Style.GALLERY,
        "browser" to Style.BROWSER, "internet" to Style.BROWSER,
        "calendar" to Style.CALENDAR,
        "clock" to Style.CLOCK,
        "calculator" to Style.CALCULATOR,
        "files" to Style.FILES, "file manager" to Style.FILES, "my files" to Style.FILES,
        "settings" to Style.SETTINGS,
        "weather" to Style.WEATHER
    )

    /** Packages that answer Dial / SMS / Camera / Settings on this phone (label-independent, helps with other languages). */
    fun defaults(context: Context): Map<String, Style> {
        val pm = context.packageManager
        val self = context.packageName
        val map = HashMap<String, Style>()
        fun add(intent: Intent, style: Style) {
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (pkg != null && pkg != "android" && pkg != self && !map.containsKey(pkg)) map[pkg] = style
        }
        add(Intent(Intent.ACTION_DIAL), Style.PHONE)
        add(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), Style.MESSAGES)
        add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), Style.CAMERA)
        add(Intent(Settings.ACTION_SETTINGS), Style.SETTINGS)
        return map
    }

    /** The picture for an app, or null when it keeps its own icon. Only apps that came with the phone (also updated ones) are replaced. */
    fun styleFor(label: String, pkg: String, flags: Int, defaults: Map<String, Style>): Style? {
        if (!enabled || IconTheme.enabled) return null
        val system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (!system) return null
        return BY_LABEL[label.trim().lowercase()] ?: defaults[pkg]
    }

    // ------------------------------------------------------------------ drawing (a full square; the caller cuts the squircle)

    fun draw(canvas: Canvas, style: Style, size: Int) {
        val s = size.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (style) {
            Style.PHONE -> phone(canvas, p, s)
            Style.MESSAGES -> messages(canvas, p, s)
            Style.CONTACTS -> contacts(canvas, p, s)
            Style.CAMERA -> camera(canvas, p, s)
            Style.GALLERY -> gallery(canvas, p, s)
            Style.BROWSER -> browser(canvas, p, s)
            Style.CALENDAR -> calendar(canvas, p, s)
            Style.CLOCK -> clock(canvas, p, s)
            Style.CALCULATOR -> calculator(canvas, p, s)
            Style.FILES -> files(canvas, p, s)
            Style.SETTINGS -> settings(canvas, p, s)
            Style.WEATHER -> weather(canvas, p, s)
        }
    }

    private fun plate(canvas: Canvas, p: Paint, s: Float, top: Int, bottom: Int) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, s, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, s, s, p)
        p.shader = null
    }

    private fun white(p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
    }

    private fun phone(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(52, 214, 120), Color.rgb(22, 178, 92))
        // A handset: a thick arc with a round pad at each end.
        p.style = Paint.Style.STROKE
        p.color = Color.WHITE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.15f
        val r = s * 0.27f
        val cx = s * 0.60f
        val cy = s * 0.40f
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 92f, 86f, false, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx - r, cy + s * 0.01f, s * 0.105f, p)
        c.drawCircle(cx + s * 0.01f, cy + r, s * 0.105f, p)
    }

    private fun messages(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(64, 160, 255), Color.rgb(30, 110, 235))
        white(p)
        val body = RectF(s * 0.2f, s * 0.24f, s * 0.8f, s * 0.64f)
        c.drawRoundRect(body, s * 0.14f, s * 0.14f, p)
        val tail = Path()
        tail.moveTo(s * 0.30f, s * 0.60f)
        tail.lineTo(s * 0.26f, s * 0.80f)
        tail.lineTo(s * 0.48f, s * 0.62f)
        tail.close()
        c.drawPath(tail, p)
        p.color = Color.rgb(46, 128, 240)
        for (i in -1..1) c.drawCircle(s * 0.5f + i * s * 0.13f, s * 0.44f, s * 0.035f, p)
    }

    private fun contacts(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(255, 176, 60), Color.rgb(250, 130, 20))
        white(p)
        c.drawCircle(s * 0.5f, s * 0.36f, s * 0.135f, p)
        val shoulders = RectF(s * 0.26f, s * 0.54f, s * 0.74f, s * 1.0f)
        c.drawRoundRect(shoulders, s * 0.24f, s * 0.24f, p)
    }

    private fun camera(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(255, 70, 100), Color.rgb(222, 20, 60))
        white(p)
        c.drawRoundRect(RectF(s * 0.17f, s * 0.31f, s * 0.83f, s * 0.75f), s * 0.11f, s * 0.11f, p)
        c.drawRoundRect(RectF(s * 0.34f, s * 0.24f, s * 0.54f, s * 0.36f), s * 0.04f, s * 0.04f, p)
        p.color = Color.rgb(166, 8, 40)
        c.drawCircle(s * 0.5f, s * 0.53f, s * 0.15f, p)
        p.color = Color.rgb(255, 96, 124)
        c.drawCircle(s * 0.5f, s * 0.53f, s * 0.085f, p)
    }

    private fun gallery(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(236, 36, 86), Color.rgb(198, 10, 60))
        white(p)
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 - 90.0)
            c.drawCircle(s * 0.5f + (cos(a) * s * 0.15f).toFloat(), s * 0.5f + (sin(a) * s * 0.15f).toFloat(), s * 0.115f, p)
        }
        p.color = Color.rgb(198, 10, 60)
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.06f, p)
    }

    private fun browser(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(48, 176, 255), Color.rgb(20, 120, 240))
        p.style = Paint.Style.STROKE
        p.color = Color.WHITE
        p.strokeWidth = s * 0.07f
        val r = s * 0.27f
        c.drawCircle(s * 0.5f, s * 0.5f, r, p)
        c.drawOval(RectF(s * 0.5f - r * 0.45f, s * 0.5f - r, s * 0.5f + r * 0.45f, s * 0.5f + r), p)
        c.drawLine(s * 0.5f - r, s * 0.5f, s * 0.5f + r, s * 0.5f, p)
    }

    private fun calendar(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(46, 190, 140), Color.rgb(16, 150, 108))
        white(p)
        c.drawRoundRect(RectF(s * 0.22f, s * 0.2f, s * 0.78f, s * 0.8f), s * 0.1f, s * 0.1f, p)
        p.color = Color.rgb(16, 150, 108)
        c.drawRoundRect(RectF(s * 0.34f, s * 0.14f, s * 0.40f, s * 0.26f), s * 0.03f, s * 0.03f, p)
        c.drawRoundRect(RectF(s * 0.60f, s * 0.14f, s * 0.66f, s * 0.26f), s * 0.03f, s * 0.03f, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.textSize = s * 0.34f
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
        c.drawText(day, s * 0.5f, s * 0.64f, p)
    }

    private fun clock(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(250, 250, 252), Color.rgb(228, 230, 236))
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(205, 208, 216)
        p.strokeWidth = s * 0.05f
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.31f, p)
        p.color = Color.rgb(30, 32, 38)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.06f
        c.drawLine(s * 0.5f, s * 0.5f, s * 0.35f, s * 0.43f, p)
        p.strokeWidth = s * 0.045f
        c.drawLine(s * 0.5f, s * 0.5f, s * 0.6f, s * 0.29f, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(240, 60, 70)
        c.drawCircle(s * 0.5f, s * 0.5f, s * 0.03f, p)
    }

    private fun calculator(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(250, 250, 252), Color.rgb(232, 234, 240))
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.05f
        p.color = Color.rgb(90, 94, 104)
        val l = s * 0.07f
        // plus (top left)
        var x = s * 0.33f
        var y = s * 0.33f
        c.drawLine(x - l, y, x + l, y, p)
        c.drawLine(x, y - l, x, y + l, p)
        // times (top right)
        x = s * 0.67f
        c.drawLine(x - l, y - l, x + l, y + l, p)
        c.drawLine(x - l, y + l, x + l, y - l, p)
        // minus (bottom left)
        x = s * 0.33f
        y = s * 0.67f
        c.drawLine(x - l, y, x + l, y, p)
        // equals (bottom right, orange)
        p.color = Color.rgb(255, 150, 40)
        x = s * 0.67f
        c.drawLine(x - l, y - l * 0.5f, x + l, y - l * 0.5f, p)
        c.drawLine(x - l, y + l * 0.5f, x + l, y + l * 0.5f, p)
    }

    private fun files(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(246, 188, 70), Color.rgb(224, 146, 30))
        white(p)
        c.drawRoundRect(RectF(s * 0.2f, s * 0.27f, s * 0.46f, s * 0.42f), s * 0.05f, s * 0.05f, p)
        c.drawRoundRect(RectF(s * 0.2f, s * 0.34f, s * 0.8f, s * 0.72f), s * 0.07f, s * 0.07f, p)
    }

    private fun settings(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(104, 140, 176), Color.rgb(70, 104, 142))
        white(p)
        c.save()
        c.translate(s * 0.5f, s * 0.5f)
        for (i in 0 until 8) {
            c.save()
            c.rotate(i * 45f)
            c.drawRoundRect(RectF(-s * 0.05f, -s * 0.31f, s * 0.05f, -s * 0.2f), s * 0.025f, s * 0.025f, p)
            c.restore()
        }
        c.drawCircle(0f, 0f, s * 0.235f, p)
        p.color = Color.rgb(78, 112, 148)
        c.drawCircle(0f, 0f, s * 0.1f, p)
        c.restore()
    }

    private fun weather(c: Canvas, p: Paint, s: Float) {
        plate(c, p, s, Color.rgb(255, 202, 70), Color.rgb(255, 156, 30))
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 236, 150)
        c.drawCircle(s * 0.38f, s * 0.38f, s * 0.15f, p)
        white(p)
        c.drawCircle(s * 0.42f, s * 0.58f, s * 0.12f, p)
        c.drawCircle(s * 0.56f, s * 0.52f, s * 0.15f, p)
        c.drawCircle(s * 0.68f, s * 0.61f, s * 0.1f, p)
        c.drawRoundRect(RectF(s * 0.3f, s * 0.58f, s * 0.78f, s * 0.72f), s * 0.07f, s * 0.07f, p)
    }
}
