package com.oklabs.oneui9launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A widget on a home page: the system's [AppWidgetHostView] (the other app draws it) inside a frame that notices a
 * long-press. Taps and scrolls inside the widget work as in the widget's own app; a long-press (the finger stays
 * put for about half a second) is taken by the launcher instead: the widget lifts, its menu opens and it can be dragged (Step 6b1).
 */
class WidgetFrame(
    context: Context,
    val appWidgetId: Int,
    private val hostView: AppWidgetHostView
) : FrameLayout(context) {

    /** Set by the activity: the widget was long-pressed. */
    var onLongPress: ((WidgetFrame) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    private val fire = Runnable {
        fired = true
        // No "disallow intercept" here: the drag layer takes over the rest of this touch through LauncherRoot,
        // and that only works while the root may still intercept (the widget can be dragged from now on).
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke(this)
    }

    init {
        addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Tells the widget how big it is (in dp), so widgets that change their look with their size can do that. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val wDp = (w / density).toInt()
        val hDp = (h / density).toInt()
        try {
            hostView.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
        } catch (e: Exception) {
            // a misbehaving widget must not take the launcher down
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // The pager / root may steal the touch (a swipe): the pending long-press must not fire afterwards.
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(fire)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                removeCallbacks(fire)
                postDelayed(fire, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(fire)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(fire)
        }
        // After the long-press fired the widget gets a cancel and the launcher keeps the rest of the touch.
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onDetachedFromWindow() {
        removeCallbacks(fire)
        super.onDetachedFromWindow()
    }
}

/**
 * The launcher's side of the Android widget system: it owns the [AppWidgetHost] (the thing that receives widget
 * updates), hands out widget ids, binds a widget to its provider and builds the views.
 * [context] must be the activity, so widgets are drawn with the launcher's own theme.
 */
class WidgetHost(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val host = AppWidgetHost(context, HOST_ID)
    private var listening = false

    /** Start receiving widget updates (call when the launcher becomes visible). */
    fun startListening() {
        if (listening) return
        try {
            host.startListening()
            listening = true
        } catch (e: Exception) {
            // some phones throw here when the system service is busy; widgets then show their last picture
        }
    }

    fun stopListening() {
        if (!listening) return
        try {
            host.stopListening()
        } catch (e: Exception) {
            // nothing to do
        }
        listening = false
    }

    /** A new, still unbound widget id. Returns -1 when the system refuses. */
    fun allocateId(): Int = try {
        host.allocateAppWidgetId()
    } catch (e: Exception) {
        -1
    }

    /** Gives a widget id back to the system (the widget is gone for good). */
    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
            // already gone
        }
    }

    /** What the system knows about this widget, or null (its app is gone, or being updated right now). */
    fun info(id: Int): AppWidgetProviderInfo? = try {
        manager.getAppWidgetInfo(id)
    } catch (e: Exception) {
        null
    }

    /** Binds the id to [provider] when the system allows it without asking. False = ask the person ([bindIntent]). */
    fun bindIfAllowed(id: Int, provider: ComponentName): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, provider)
    } catch (e: Exception) {
        false
    }

    /** The system's "Allow this app to create widgets?" screen. Start it for a result. */
    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    /** Opens the widget's own setup screen. False when it can not be opened. */
    fun startConfigure(activity: Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: Exception) {
        false
    }

    /** The view for a bound widget, or null when the system can not make one. */
    fun createFrame(id: Int): WidgetFrame? {
        val view: AppWidgetHostView = try {
            host.createView(context, id, info(id))
        } catch (e: Exception) {
            return null
        }
        return WidgetFrame(context, id, view)
    }

    private companion object {
        /** Any number; it only has to stay the same between runs. */
        const val HOST_ID = 4096
    }
}

/** How many home cells a widget covers. */
object WidgetSizes {

    /**
     * (columns, rows) for [info] on a grid of [cols] x [rows] cells that are [cellW] x [cellH] pixels.
     * Widgets state their smallest size with the old "70 dp per cell minus 30" rule, so a "2 x 2" widget (110 dp)
     * gets 2 x 2 cells here. Phones with Android 12+ can say it in cells directly. The size is never smaller than the
     * widget's minimum and never bigger than the page.
     */
    fun spans(info: AppWidgetProviderInfo, density: Float, cellW: Int, cellH: Int, cols: Int, rows: Int): Pair<Int, Int> {
        val minW = info.minWidth
        val minH = info.minHeight
        var spanX = max(1, ((minW / density + 30f) / 70f).roundToInt())
        var spanY = max(1, ((minH / density + 30f) / 70f).roundToInt())
        if (Build.VERSION.SDK_INT >= 31) {
            if (info.targetCellWidth > 0) spanX = info.targetCellWidth
            if (info.targetCellHeight > 0) spanY = info.targetCellHeight
        }
        if (cellW > 0) spanX = max(spanX, ceil(minW.toFloat() / cellW).toInt())
        if (cellH > 0) spanY = max(spanY, ceil(minH.toFloat() / cellH).toInt())
        return Pair(spanX.coerceIn(1, cols), spanY.coerceIn(1, rows))
    }
}

/** What a widget allows when it is resized. */
object WidgetResize {

    /** How many cells wide / tall [info] may be made, for a widget that now covers [spanX] x [spanY] cells of a page of [cols] x [rows] cells. */
    fun limitsFor(info: AppWidgetProviderInfo, cellW: Int, cellH: Int, cols: Int, rows: Int, spanX: Int, spanY: Int): ResizeLimits {
        var maxW = 0
        var maxH = 0
        if (Build.VERSION.SDK_INT >= 31) {
            maxW = info.maxResizeWidth
            maxH = info.maxResizeHeight
        }
        return ResizeRules.limits(
            info.resizeMode, info.minResizeWidth, info.minResizeHeight, maxW, maxH,
            cellW, cellH, cols, rows, spanX, spanY
        )
    }
}

/** One widget in the picker: the system's description of it, its name and its size in cells. */
class WidgetEntry(val info: AppWidgetProviderInfo, val label: String, val spanX: Int, val spanY: Int)

/** All widgets of one app, as the picker shows them. */
class WidgetGroup(val packageName: String, val label: String, val icon: Bitmap?, val entries: List<WidgetEntry>)

/** Finds every widget installed on the phone, grouped by app. Call it off the main thread. */
object WidgetCatalog {

    @Suppress("DEPRECATION")
    fun load(context: Context, manager: AppWidgetManager, iconPx: Int, cellW: Int, cellH: Int): List<WidgetGroup> {
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        val providers: List<AppWidgetProviderInfo> = try {
            manager.installedProviders
        } catch (e: Exception) {
            emptyList()
        }

        val byPackage = LinkedHashMap<String, ArrayList<WidgetEntry>>()
        for (info in providers) {
            val component = info.provider ?: continue
            // Lock-screen-only widgets do not belong on a home screen.
            if ((info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue
            val pkg = component.packageName
            var name = try {
                info.loadLabel(pm)?.trim().orEmpty()
            } catch (e: Exception) {
                ""
            }
            if (name.isEmpty()) name = component.shortClassName.trimStart('.')
            val spans = WidgetSizes.spans(info, density, cellW, cellH, HomeStore.COLS, HomeStore.ROWS)
            byPackage.getOrPut(pkg) { ArrayList() }.add(WidgetEntry(info, name, spans.first, spans.second))
        }

        val collator = AppText.collator()
        val groups = ArrayList<WidgetGroup>()
        for ((pkg, entries) in byPackage) {
            var appLabel = pkg
            var icon: Bitmap? = null
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                appLabel = pm.getApplicationLabel(appInfo).toString().trim().ifEmpty { pkg }
                icon = IconShaper.shape(pm.getApplicationIcon(appInfo), iconPx)
            } catch (e: Exception) {
                // an app the launcher may not look at: show it by its package name, without an icon
            }
            val sorted = entries.sortedWith(Comparator { a, b ->
                val byName = collator.compare(a.label, b.label)
                if (byName != 0) byName else (a.spanX * a.spanY) - (b.spanX * b.spanY)
            })
            groups.add(WidgetGroup(pkg, appLabel, icon, sorted))
        }
        groups.sortWith(Comparator { a, b -> collator.compare(a.label, b.label) })
        // The launcher's own widgets (Clock, Date) go first so they are easy to find.
        val own = groups.indexOfFirst { it.packageName == context.packageName }
        if (own > 0) groups.add(0, groups.removeAt(own))
        return groups
    }
}
