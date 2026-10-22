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

    private const val FONT_COUNT = 10

    /** The ten text blocks (one per font) of widget_clock.xml and widget_date.xml, in the order of [WidgetConfig.FONT_NAMES]. */
    private val CLOCK_BOX = intArrayOf(R.id.clock_box_0, R.id.clock_box_1, R.id.clock_box_2, R.id.clock_box_3, R.id.clock_box_4, R.id.clock_box_5, R.id.clock_box_6, R.id.clock_box_7, R.id.clock_box_8, R.id.clock_box_9)
    private val CLOCK_TIME = intArrayOf(R.id.clock_time_0, R.id.clock_time_1, R.id.clock_time_2, R.id.clock_time_3, R.id.clock_time_4, R.id.clock_time_5, R.id.clock_time_6, R.id.clock_time_7, R.id.clock_time_8, R.id.clock_time_9)
    private val CLOCK_DATE = intArrayOf(R.id.clock_date_0, R.id.clock_date_1, R.id.clock_date_2, R.id.clock_date_3, R.id.clock_date_4, R.id.clock_date_5, R.id.clock_date_6, R.id.clock_date_7, R.id.clock_date_8, R.id.clock_date_9)
    private val DATE_BOX = intArrayOf(R.id.date_box_0, R.id.date_box_1, R.id.date_box_2, R.id.date_box_3, R.id.date_box_4, R.id.date_box_5, R.id.date_box_6, R.id.date_box_7, R.id.date_box_8, R.id.date_box_9)
    private val DATE_WEEKDAY = intArrayOf(R.id.date_weekday_0, R.id.date_weekday_1, R.id.date_weekday_2, R.id.date_weekday_3, R.id.date_weekday_4, R.id.date_weekday_5, R.id.date_weekday_6, R.id.date_weekday_7, R.id.date_weekday_8, R.id.date_weekday_9)
    private val DATE_DAY = intArrayOf(R.id.date_day_0, R.id.date_day_1, R.id.date_day_2, R.id.date_day_3, R.id.date_day_4, R.id.date_day_5, R.id.date_day_6, R.id.date_day_7, R.id.date_day_8, R.id.date_day_9)
    private val DATE_MONTH = intArrayOf(R.id.date_month_0, R.id.date_month_1, R.id.date_month_2, R.id.date_month_3, R.id.date_month_4, R.id.date_month_5, R.id.date_month_6, R.id.date_month_7, R.id.date_month_8, R.id.date_month_9)

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
            RemoteViews(context.packageName, ANALOG_LAYOUTS[if (config.color in ANALOG_LAYOUTS.indices) config.color else 0])
        } else {
            RemoteViews(context.packageName, R.layout.widget_clock)
        }

        val font = config.font.coerceIn(0, FONT_COUNT - 1)
        if (kind == BuiltInKind.DATE) {
            for (i in 0 until FONT_COUNT) views.setViewVisibility(DATE_BOX[i], if (i == font) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextColor(DATE_WEEKDAY[font], config.softColorInt)
            views.setTextColor(DATE_DAY[font], config.colorInt)
            views.setTextColor(DATE_MONTH[font], config.softColorInt)
            tapOpens(context, views, REQUEST_DATE, calendarApp(context))
        } else if (config.style == WidgetConfig.STYLE_ANALOG) {
            // the analog faces have their own picture of the date line
            val datePattern = bestPattern("EEEMMMd", "EEE, MMM d")
            views.setCharSequence(R.id.clock_date, "setFormat12Hour", datePattern)
            views.setCharSequence(R.id.clock_date, "setFormat24Hour", datePattern)
            views.setTextColor(R.id.clock_date, config.softColorInt)
            views.setViewVisibility(R.id.clock_date, if (config.showDate) android.view.View.VISIBLE else android.view.View.GONE)
            tapOpens(context, views, REQUEST_CLOCK, clockApp(context))
        } else {
            for (i in 0 until FONT_COUNT) views.setViewVisibility(CLOCK_BOX[i], if (i == font) android.view.View.VISIBLE else android.view.View.GONE)
            val datePattern = bestPattern("EEEMMMd", "EEE, MMM d")
            val date = CLOCK_DATE[font]
            val time = CLOCK_TIME[font]
            views.setCharSequence(date, "setFormat12Hour", datePattern)
            views.setCharSequence(date, "setFormat24Hour", datePattern)
            views.setTextColor(date, config.softColorInt)
            views.setViewVisibility(date, if (config.showDate) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextColor(time, config.colorInt)
            val stacked = config.style == WidgetConfig.STYLE_STACKED
            val twelve = when (config.hours) {
                WidgetConfig.HOURS_12 -> true
                WidgetConfig.HOURS_24 -> false
                else -> !DateFormat.is24HourFormat(context)
            }
            when {
                stacked -> setTimePattern(views, time, if (twelve) "h\nmm" else "HH\nmm")
                config.hours == WidgetConfig.HOURS_12 -> setTimePattern(views, time, "h:mm")
                config.hours == WidgetConfig.HOURS_24 -> setTimePattern(views, time, "HH:mm")
                // HOURS_PHONE: the layout already switches between h:mm and HH:mm with the phone's setting
            }
            tapOpens(context, views, REQUEST_CLOCK, clockApp(context))
        }

        val background = when (config.background) {
            WidgetConfig.BG_GLASS -> R.drawable.widget_glass_bg
            WidgetConfig.BG_DARK -> R.drawable.widget_dark_bg
            WidgetConfig.BG_AURORA -> R.drawable.widget_theme_aurora
            WidgetConfig.BG_SUNSET -> R.drawable.widget_theme_sunset
            WidgetConfig.BG_OCEAN -> R.drawable.widget_theme_ocean
            WidgetConfig.BG_FOREST -> R.drawable.widget_theme_forest
            WidgetConfig.BG_MIDNIGHT -> R.drawable.widget_theme_midnight
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

    private fun setTimePattern(views: RemoteViews, id: Int, pattern: String) {
        views.setCharSequence(id, "setFormat12Hour", pattern)
        views.setCharSequence(id, "setFormat24Hour", pattern)
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
