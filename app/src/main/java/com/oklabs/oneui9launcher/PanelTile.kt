package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Huawei plan, step 3: the settings of the notification that keeps the Quick panel ready (it is the foreground service's notification). */
object PanelNote {
    const val PREF_ACTION = "pn_action" // show the "Settings" action button
    const val PREF_TAP = "pn_tap" // 0 = tap opens the Quick panel, 1 = tap opens the Notifications page

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun showAction(c: Context): Boolean = prefs(c).getBoolean(PREF_ACTION, true)
    fun tapPage(c: Context): Int = prefs(c).getInt(PREF_TAP, 0).coerceIn(0, 1)
}

/**
 * Huawei plan, step 3: one small invisible screen that opens the Quick panel. The Quick Settings tile and the App shortcut both start it.
 * (A tile or a shortcut starts an activity, and an activity that is on screen is allowed to start the panel service on every Android version.)
 * It opens nothing when the panel is not ready, and says why.
 */
class PanelShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun open() {
        val notifs = intent?.getBooleanExtra(EXTRA_NOTIFS, false) == true
        if (!OverlayPanel.wanted(this) || !OverlayPanel.canDraw(this)) {
            Toast.makeText(
                this, "Switch on \"Quick panel over other apps\" and allow \"Draw over other apps\" in the launcher settings first", Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(Intent(this, HomeSettingsActivity::class.java))
            } catch (e: Exception) {
                // nothing more to do
            }
            return
        }
        try {
            if (!OverlayPanel.running()) OverlayPanel.sync(this)
            val service = Intent(this, PanelOverlayService::class.java)
                .setAction(if (notifs) PanelOverlayService.ACTION_SHOW_NOTIFS else PanelOverlayService.ACTION_SHOW)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
        } catch (e: Exception) {
            Toast.makeText(this, "The Quick panel could not start", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_NOTIFS = "notifs"
    }
}

/** Huawei plan, step 3: the "Quick panel" tile for the phone's own Quick Settings (the person adds it with the pencil / edit button there). */
class PanelTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val ready = OverlayPanel.wanted(this) && OverlayPanel.canDraw(this)
        tile.state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Quick panel"
        try {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_panel)
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (ready) "Tap to open" else "Set up first"
        } catch (e: Exception) {
            // the phone keeps the old look
        }
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, PanelShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // startActivityAndCollapse(PendingIntent): called by name so that the file also compiles against older SDK platforms
                val pi = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                TileService::class.java.getMethod("startActivityAndCollapse", PendingIntent::class.java).invoke(this, pi)
            } else {
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            try {
                startActivity(intent)
            } catch (e2: Exception) {
                // the phone refused: nothing opens
            }
        }
    }

    companion object {
        /** Asks the phone to look at the tile again (after the master switch changes). */
        fun requestUpdate(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= 24) requestListeningState(context, ComponentName(context, PanelTileService::class.java))
            } catch (e: Exception) {
                // the tile is not added: nothing to update
            }
        }
    }
}
