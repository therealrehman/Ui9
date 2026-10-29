package com.oklabs.oneui9launcher

import android.content.Context
import android.util.Base64

/**
 * Step 10b: backup and restore. [export] turns the launcher's saved layout and settings into one small text; [restore] puts such a
 * text back. Text values are written in Base64 so line breaks and tabs inside them (the layout has plenty) survive.
 *
 * Widgets are not part of a backup: Android hands every widget an id that only exists on this phone, so a restored layout comes back
 * without its widgets and they are added again by hand. The widgets' own colour / style settings are not saved either.
 */
object Backup {

    private const val HEADER = "OneUI9LauncherBackup 1"
    private const val PREFS = "launcher"

    /** Not worth restoring: the one-time "make me the default home app" question. */
    private val SKIP = setOf("asked_default")

    /** Set after a restore: the launcher then builds its screen again from the restored data the next time it comes to the front. */
    @Volatile
    var reloadHome = false

    /** Everything the launcher saved, as text. */
    fun export(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = StringBuilder(HEADER).append('\n')
        for ((key, value) in prefs.all.toSortedMap()) {
            if (key in SKIP || key.contains('\t') || key.contains('\n')) continue
            when (value) {
                is Boolean -> out.append("b\t").append(key).append('\t').append(value).append('\n')
                is Int -> out.append("i\t").append(key).append('\t').append(value).append('\n')
                is Long -> out.append("l\t").append(key).append('\t').append(value).append('\n')
                is Float -> out.append("f\t").append(key).append('\t').append(value).append('\n')
                is String -> out.append("s\t").append(key).append('\t').append(encode(value)).append('\n')
                is Set<*> -> {
                    val parts = value.filterIsInstance<String>().sorted().joinToString(",") { encode(it) }
                    out.append("t\t").append(key).append('\t').append(parts).append('\n')
                }
            }
        }
        return out.toString()
    }

    /** True when [text] looks like something [export] wrote. */
    fun isBackup(text: String): Boolean = text.lineSequence().firstOrNull()?.trim() == HEADER

    /**
     * Puts the saved values from [text] back. Returns how many were restored, or -1 when [text] is not a backup.
     * Widgets in the saved layout are dropped (see the note at the top).
     */
    fun restore(context: Context, text: String): Int {
        if (!isBackup(text)) return -1
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        var count = 0
        for (line in text.split('\n').drop(1)) {
            val parts = line.trimEnd('\r').split('\t', limit = 3)
            if (parts.size < 3) continue
            val key = parts[1]
            val raw = parts[2]
            if (key.isEmpty() || key in SKIP) continue
            when (parts[0]) {
                "b" -> edit.putBoolean(key, raw == "true")
                "i" -> edit.putInt(key, raw.toIntOrNull() ?: continue)
                "l" -> edit.putLong(key, raw.toLongOrNull() ?: continue)
                "f" -> edit.putFloat(key, raw.toFloatOrNull() ?: continue)
                "s" -> {
                    val value = decode(raw) ?: continue
                    edit.putString(key, if (key == "home_layout") withoutWidgets(value) else value)
                }
                "t" -> {
                    val items = HashSet<String>()
                    for (piece in raw.split(',')) {
                        if (piece.isEmpty()) continue
                        items.add(decode(piece) ?: continue)
                    }
                    edit.putStringSet(key, items)
                }
                else -> continue
            }
            count++
        }
        edit.commit() // written before anything restarts
        reloadHome = true
        return count
    }

    /** The saved layout without its widget lines ("page TAB col TAB row TAB @w17 ..."). */
    private fun withoutWidgets(layout: String): String =
        layout.split('\n').filter { line ->
            val fields = line.split('\t', limit = 5)
            !(fields.size >= 4 && fields[3].startsWith(WIDGET_PREFIX))
        }.joinToString("\n")

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String? = try {
        String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }
}
