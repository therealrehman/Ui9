package com.oklabs.oneui9launcher

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** The app class: only installs [CrashLog]. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/**
 * Step 13: if the app crashes, a short note (the error, app / Android version, phone model) is saved in the app's private storage.
 * Settings shows it the next time it opens, so you can copy it or email it to the developer. It is never sent anywhere by the app.
 */
object CrashLog {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            save(app, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun save(context: Context, error: Throwable) {
        try {
            val writer = StringWriter()
            error.printStackTrace(PrintWriter(writer))
            var version = "?"
            try {
                version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (e: Exception) {
                // keep "?"
            }
            val head = context.getString(R.string.app_name) + " " + version + "\n" +
                "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Phone: " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
            File(context.filesDir, FILE).writeText(head + writer.toString().take(6000))
        } catch (e: Throwable) {
            // nothing more can be done
        }
    }

    /** The saved note, or null when there is none. */
    fun read(context: Context): String? = try {
        val file = File(context.filesDir, FILE)
        if (file.exists()) file.readText() else null
    } catch (e: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (e: Throwable) {
            // ignore
        }
    }
}
