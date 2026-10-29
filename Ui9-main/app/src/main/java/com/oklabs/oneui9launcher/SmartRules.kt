package com.oklabs.oneui9launcher

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.WindowInsets

/**
 * Huawei plan, step 5: smart rules. They decide when the top handle and the floating button are HIDDEN
 * (full-screen apps / videos, keyboard open, lock screen, screen off, landscape). The rules only hide the two
 * touch windows; the Quick panel itself, the notification, the tile and the shortcut are never affected.
 */
object SmartRules {
    const val PREF_FULLSCREEN = "sr_fullscreen" // hide in full-screen apps and videos
    const val PREF_KEYBOARD = "sr_keyboard" // hide while the keyboard is open
    const val PREF_LOCK = "sr_lock" // hide on the lock screen
    const val PREF_SCREEN_OFF = "sr_screen_off" // hide (remove the windows) while the screen is off
    const val PREF_LANDSCAPE = "sr_landscape" // true = also show in landscape, false = hide in landscape

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun fullscreen(c: Context): Boolean = prefs(c).getBoolean(PREF_FULLSCREEN, true)
    fun keyboard(c: Context): Boolean = prefs(c).getBoolean(PREF_KEYBOARD, true)
    fun lock(c: Context): Boolean = prefs(c).getBoolean(PREF_LOCK, true)
    fun screenOff(c: Context): Boolean = prefs(c).getBoolean(PREF_SCREEN_OFF, true)
    fun landscape(c: Context): Boolean = prefs(c).getBoolean(PREF_LANDSCAPE, true)

    /** What the phone is doing right now. */
    class State(
        val fullscreen: Boolean,
        val keyboard: Boolean,
        val locked: Boolean,
        val interactive: Boolean, // false = screen off
        val landscape: Boolean
    )

    /** The switches, so that [hidden] stays a pure function (easy to test without a phone). */
    class Rules(
        val fullscreen: Boolean,
        val keyboard: Boolean,
        val lock: Boolean,
        val screenOff: Boolean,
        val landscapeAllowed: Boolean
    )

    fun rules(c: Context) = Rules(fullscreen(c), keyboard(c), lock(c), screenOff(c), landscape(c))

    /** True when the handle / button must not be on screen. A rule that is switched off never hides anything. */
    fun hidden(r: Rules, s: State): Boolean =
        (r.screenOff && !s.interactive) ||
            (r.lock && s.locked) ||
            (r.fullscreen && s.fullscreen) ||
            (r.keyboard && s.keyboard) ||
            (!r.landscapeAllowed && s.landscape)

    /** The invisible probe window is only needed for the two rules that look at the other app's screen. */
    fun needsProbe(c: Context): Boolean =
        (fullscreen(c) || keyboard(c)) && (TopHandle.wanted(c) || FloatButton.wanted(c))

    fun isInteractive(c: Context): Boolean = try {
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    } catch (e: Exception) {
        true
    }

    fun isLocked(c: Context): Boolean = try {
        (c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    } catch (e: Exception) {
        false
    }

    fun isLandscape(c: Context): Boolean =
        c.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /**
     * From the insets the probe window gets: is the status bar hidden (= a full-screen app / video) and is the keyboard shown.
     * Pure, so it can be tested. [statusVisible] / [imeVisible] come from the Android 11+ API (null on older phones),
     * [topInset] / [bottomInset] / [screenH] are the fallback for Android 8-10.
     */
    fun fullscreenFrom(statusVisible: Boolean?, topInset: Int): Boolean = if (statusVisible != null) !statusVisible else topInset <= 0

    fun keyboardFrom(imeVisible: Boolean?, bottomInset: Int, screenH: Int): Boolean =
        if (imeVisible != null) imeVisible else (screenH > 0 && bottomInset > screenH * 0.15f)
}

/**
 * A 1-pixel-wide invisible window the size of the screen height. It takes no touches and no focus. It only reports what
 * Android tells every window about the bars: status bar gone = full-screen app; keyboard inset = keyboard open.
 * Honest limit: some phones do not give the keyboard inset to windows of other apps; then only the full-screen rule works.
 */
class SmartProbeView(context: Context, private val onState: (Boolean, Boolean) -> Unit) : View(context) {
    private var lastFs = false
    private var lastKb = false
    private var reported = false

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        evaluate(insets)
        return super.onApplyWindowInsets(insets)
    }

    /** Looks at the insets now (the service calls this about once a second as a safety net). */
    fun recheck() {
        val insets = rootWindowInsets ?: return
        evaluate(insets)
    }

    @Suppress("DEPRECATION")
    private fun evaluate(insets: WindowInsets) {
        val screenH = resources.displayMetrics.heightPixels
        val fs: Boolean
        val kb: Boolean
        if (Build.VERSION.SDK_INT >= 30) {
            fs = SmartRules.fullscreenFrom(insets.isVisible(WindowInsets.Type.statusBars()), 0)
            kb = SmartRules.keyboardFrom(insets.isVisible(WindowInsets.Type.ime()), 0, screenH)
        } else {
            fs = SmartRules.fullscreenFrom(null, insets.systemWindowInsetTop)
            kb = SmartRules.keyboardFrom(null, insets.systemWindowInsetBottom, screenH)
        }
        if (!reported || fs != lastFs || kb != lastKb) {
            reported = true
            lastFs = fs
            lastKb = kb
            onState(fs, kb)
        }
    }
}
