package com.oklabs.oneui9launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.util.Locale

/**
 * The launcher's own widgets (Step 6c1, setup screen and analog clock in 6c2). They are ordinary Android widgets that
 * happen to come from this app, so they go through the same picker, the same add / move / resize / remove and the same
 * saved layout as every other widget.
 *
 * The clock and the date are drawn by TextClock / AnalogClock views, which tick by themselves - nothing has to wake up
 * every minute. Their text fits the size of the widget (auto-size), so a resized widget never clips its numbers.
 * What the widget looks like (style, colour, 12 / 24 h, date, background) is chosen on [WidgetSetupActivity] and kept
 * per widget by [WidgetConfigStore].
 */
abstract class BuiltInWidgetProvider : AppWidgetProvider() {

    protected abstract val kind: BuiltInKind

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val config = WidgetConfigStore.load(context, id, kind)
            try {
                manager.updateAppWidget(id, BuiltInWidgets.build(context, kind, config))
            } catch (e: Exception) {
                // the system may be busy; the widget keeps showing its last picture and updates next time
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetConfigStore.forget(context, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // A new phone language changes the order of the date ("Mon, Oct 19" / "19 Oct, Mon").
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
        }
    }
}

/** Clock, 4 x 2 cells: big time with the date underneath. */
class ClockWidgetProvider : BuiltInWidgetProvider() {
    override val kind: BuiltInKind get() = BuiltInKind.CLOCK
}

/** Mini clock, 2 x 2 cells: the same clock, smaller. */
class ClockSmallWidgetProvider : BuiltInWidgetProvider() {
    override val kind: BuiltInKind get() = BuiltInKind.MINI_CLOCK
}

/** Analog clock, 2 x 2 cells: a round face with hour and minute hands, the date underneath. */
class AnalogClockWidgetProvider : BuiltInWidgetProvider() {
    override val kind: BuiltInKind get() = BuiltInKind.ANALOG_CLOCK
}

/** Date, 2 x 2 cells: weekday, big day number and month on a soft glass tile. */
class DateWidgetProvider : BuiltInWidgetProvider() {
    override val kind: BuiltInKind get() = BuiltInKind.DATE
}

/** Remembers each widget's choices (one small text per widget id). */
object WidgetConfigStore {

    private const val PREFS = "builtin_widgets"

    fun load(context: Context, id: Int, kind: BuiltInKind): WidgetConfig =
        WidgetConfig.decode(prefs(context).getString(key(id), null), kind.defaults)

    fun save(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit().putString(key(id), config.encode()).apply()
    }

    /** The widgets with these ids were removed: forget what they looked like. */
    fun forget(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        for (id in ids) edit.remove(key(id))
        edit.apply()
    }

    private fun key(id: Int) = "w$id"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

object BuiltInWidgets {

    /** One face of the analog clock per colour of [WidgetConfig.COLORS], in the same order. */
    private val ANALOG_LAYOUTS = intArrayOf(
        R.layout.widget_clock_analog_0,
        R.layout.widget_clock_analog_1,
        R.layout.widget_clock_analog_2,
        R.layout.widget_clock_analog_3,
        R.layout.widget_clock_analog_4,
        R.layout.widget_clock_analog_5
    )

    /** Everything the widget shows, for [kind] looking the way [config] says. Used by the widget and by the setup screen's preview. */
    fun build(context: Context, kind: BuiltInKind, config: WidgetConfig): RemoteViews {
        val views = if (kind == BuiltInKind.DATE) {
            RemoteViews(context.packageName, R.layout.widget_date)
        } else if (config.style == WidgetConfig.STYLE_ANALOG) {
            RemoteViews(context.packageName, ANALOG_LAYOUTS[config.color.coerceIn(0, ANALOG_LAYOUTS.size - 1)])
        } else {
            RemoteViews(context.packageName, R.layout.widget_clock)
        }

        if (kind == BuiltInKind.DATE) {
            views.setTextColor(R.id.date_weekday, config.softColorInt)
            views.setTextColor(R.id.date_day, config.colorInt)
            views.setTextColor(R.id.date_month, config.softColorInt)
            tapOpens(context, views, REQUEST_DATE, calendarApp(context))
        } else {
            val datePattern = bestPattern("EEEMMMd", "EEE, MMM d")
            views.setCharSequence(R.id.clock_date, "setFormat12Hour", datePattern)
            views.setCharSequence(R.id.clock_date, "setFormat24Hour", datePattern)
            views.setTextColor(R.id.clock_date, config.softColorInt)
            views.setViewVisibility(R.id.clock_date, if (config.showDate) android.view.View.VISIBLE else android.view.View.GONE)
            if (config.style != WidgetConfig.STYLE_ANALOG) {
                views.setTextColor(R.id.clock_time, config.colorInt)
                when (config.hours) {
                    WidgetConfig.HOURS_12 -> setTimePattern(views, "h:mm")
                    WidgetConfig.HOURS_24 -> setTimePattern(views, "HH:mm")
                    // HOURS_PHONE: the layout already switches between h:mm and HH:mm with the phone's setting
                }
            }
            tapOpens(context, views, REQUEST_CLOCK, clockApp(context))
        }

        val background = when (config.background) {
            WidgetConfig.BG_GLASS -> R.drawable.widget_glass_bg
            WidgetConfig.BG_DARK -> R.drawable.widget_dark_bg
            else -> 0
        }
        views.setInt(R.id.widget_root, "setBackgroundResource", background)
        return views
    }

    /** Pushes a new look to a widget that is already on the screen (setup screen "Done"). */
    fun update(context: Context, id: Int, kind: BuiltInKind, config: WidgetConfig) {
        try {
            AppWidgetManager.getInstance(context).updateAppWidget(id, build(context, kind, config))
        } catch (e: Exception) {
            // the widget keeps its old look; the next system update will pick up the saved choice
        }
    }

    private fun setTimePattern(views: RemoteViews, pattern: String) {
        views.setCharSequence(R.id.clock_time, "setFormat12Hour", pattern)
        views.setCharSequence(R.id.clock_time, "setFormat24Hour", pattern)
    }

    /** The phone's best pattern for [skeleton] in its language; [fallback] when the system can not tell. */
    private fun bestPattern(skeleton: String, fallback: String): String = try {
        DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton).ifEmpty { fallback }
    } catch (e: Exception) {
        fallback
    }

    /** Tapping the widget starts [intent]. Nothing is set when the phone has no app for it. */
    private fun tapOpens(context: Context, views: RemoteViews, requestCode: Int, intent: Intent?) {
        if (intent == null) return
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
    }

    /** The phone's alarm / clock app, or null. */
    private fun clockApp(context: Context): Intent? {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        return if (canOpen(context, intent)) intent else null
    }

    /** The phone's calendar app, or null. */
    private fun calendarApp(context: Context): Intent? {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        return if (canOpen(context, intent)) intent else null
    }

    private fun canOpen(context: Context, intent: Intent): Boolean = try {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    } catch (e: Exception) {
        false
    }

    private const val REQUEST_CLOCK = 61
    private const val REQUEST_DATE = 62
}
