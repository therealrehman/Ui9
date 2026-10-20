package com.oklabs.oneui9launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The Apps screen. It sits on top of the home screen and is driven by [progress]
 * (0 = hidden, 1 = fully open), either by a finger ([LauncherRoot]) or by [open] / [close].
 *
 * Step 12b: swipe up from Home opens it (as before); inside, the apps are PAGES you swipe left / right (4 columns, up to 5 rows),
 * with page dots, the search pill at the bottom and the sort menu (three dots) at the top right.
 */
class AppsScreen(
    context: Context,
    private val onLaunch: (View, Intent) -> Unit
) : FrameLayout(context) {

    /** 0 = closed and hidden, 1 = fully open. */
    var progress = 0f
        private set

    /** Called on every progress change so the home screen can fade and shrink behind the drawer. */
    var onProgress: ((Float) -> Unit)? = null

    /** Long-press on an app in the list (Step 3: the "Add to Home screen" menu). */
    var onAppLongPress: ((View, AppEntry) -> Unit)? = null

    val isAnimating: Boolean
        get() = animator?.isRunning == true

    /** How far a finger has to travel to go from closed to fully open. */
    val travelPx: Float
        get() = max(context.dpf(260f), height * 0.42f)

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val panel = FrameLayout(context)
    private val pager = HomePager(context)
    private val dots = PageDots(context)
    private val pill = LinearLayout(context)
    private val searchField = EditText(context)
    private val clearButton = GlyphView(context, GlyphView.CLEAR).also { it.tint = Look.text }
    private val moreButton = GlyphView(context, GlyphView.MORE).also { it.tint = Look.text }
    private val emptyText = TextView(context)

    private var baseApps: List<AppEntry> = emptyList()
    private var sortedApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var loaded = false
    private var sortMode = SortMode.from(prefs.getString(KEY_SORT, null))

    private var insetTop = 0
    private var insetBottom = 0
    private var animator: ValueAnimator? = null
    private var rebuilding = false

    init {
        visibility = View.GONE
        isClickable = true            // swallow touches so nothing behind the drawer reacts
        isFocusable = true
        isFocusableInTouchMode = true // the drawer itself takes focus, so the search box never grabs it

        addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- the pages
        pager.onPageChanged = { page, count ->
            dots.setPages(count, page)
            dots.visibility = if (count > 1) View.VISIBLE else View.INVISIBLE
        }
        pager.onScrolled = {
            // The finger moved the pages: put the keyboard away (not while the pages are being rebuilt by typing).
            if (!rebuilding && searchField.hasFocus()) {
                searchField.clearFocus()
                hideKeyboard()
            }
        }
        pager.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) post { rebuildPages(true) }
        }
        panel.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- "No apps found"
        emptyText.text = "No apps found"
        emptyText.setTextColor(Look.textA(170))
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        emptyText.gravity = Gravity.CENTER_HORIZONTAL
        emptyText.visibility = View.GONE
        panel.addView(emptyText, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // --- page dots
        dots.tint = Look.text
        dots.visibility = View.INVISIBLE
        panel.addView(dots, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        // --- sort menu (three dots, top right)
        moreButton.setOnClickListener { showSortMenu(it) }
        panel.addView(moreButton, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END))

        // --- search pill (bottom)
        buildPill()
        val pillParams = FrameLayout.LayoutParams(MATCH, dp(PILL_HEIGHT_DP), Gravity.BOTTOM)
        pillParams.leftMargin = dp(44)
        pillParams.rightMargin = dp(44)
        panel.addView(pill, pillParams)

        applyInsets()
    }

    // ------------------------------------------------------------------ building

    private fun buildPill() {
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = roundRect(Look.inkA(46), dpf(24f))
        pill.setPadding(dp(16), 0, dp(6), 0)
        pill.setOnClickListener { focusSearch() }

        pill.addView(GlyphView(context, GlyphView.SEARCH).also { it.tint = Look.text }, LinearLayout.LayoutParams(dp(22), dp(22)))

        searchField.hint = "Search"
        searchField.setHintTextColor(Look.textA(150))
        searchField.setTextColor(Look.text)
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        searchField.background = null
        searchField.setPadding(0, 0, 0, 0)
        searchField.gravity = Gravity.CENTER_VERTICAL
        searchField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        searchField.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(toFirstPage = true)
            }
        })
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                val first = shown.firstOrNull()
                if (first != null && searchField.text.isNotEmpty()) {
                    val anchor = (pager.getChildAt(0) as? ViewGroup)?.getChildAt(0) ?: pager
                    onLaunch(anchor, first.launchIntent)
                }
                true
            } else {
                false
            }
        }
        val fieldParams = LinearLayout.LayoutParams(0, MATCH, 1f)
        fieldParams.leftMargin = dp(10)
        pill.addView(searchField, fieldParams)

        clearButton.visibility = View.GONE
        clearButton.setOnClickListener { searchField.setText("") }
        pill.addView(clearButton, LinearLayout.LayoutParams(dp(40), dp(40)))
    }

    /** Step 13: the number chosen in Settings > Apps screen, or (Auto) as many 84 dp columns as fit. */
    private fun columnCount(): Int {
        if (HomeStore.appsCols > 0) return HomeStore.appsCols
        val metrics = resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        return (widthDp / 84f).toInt().coerceIn(4, 8)
    }

    private fun dp(value: Int): Int = context.dp(value)
    private fun dpf(value: Float): Float = context.dpf(value)

    /** Keeps the menu below the status bar and the search pill above the navigation bar / keyboard. */
    fun setInsets(top: Int, bottom: Int) {
        if (top == insetTop && bottom == insetBottom) return
        insetTop = top
        insetBottom = bottom
        applyInsets()
    }

    private fun applyInsets() {
        val top = max(insetTop, dp(24))
        val bottom = insetBottom + dp(14)
        val aboveDots = bottom + dp(PILL_HEIGHT_DP) + dp(12)

        (pill.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottom
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = aboveDots
        (moreButton.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = top
            rightMargin = dp(10)
        }
        (pager.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = top + dp(48)
            bottomMargin = aboveDots + dp(18)
        }
        (emptyText.layoutParams as FrameLayout.LayoutParams).topMargin = top + dp(140)
        panel.requestLayout()
    }

    // ------------------------------------------------------------------ data

    fun setApps(apps: List<AppEntry>) {
        baseApps = apps
        loaded = true
        sortedApps = AppRepository.sort(baseApps, sortMode)
        applyFilter(toFirstPage = false)
    }

    private fun setSort(mode: SortMode) {
        if (mode == sortMode) return
        sortMode = mode
        prefs.edit().putString(KEY_SORT, mode.key).apply()
        sortedApps = AppRepository.sort(baseApps, sortMode)
        applyFilter(toFirstPage = true)
    }

    private fun applyFilter(toFirstPage: Boolean) {
        val query = searchField.text?.toString().orEmpty()
        clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        shown = AppRepository.filter(sortedApps, query)
        emptyText.visibility = if (loaded && shown.isEmpty()) View.VISIBLE else View.GONE
        rebuildPages(!toFirstPage)
    }

    /** Cuts [shown] into pages of columns x rows icons for the size the pager has now. */
    private fun rebuildPages(keepPage: Boolean) {
        val w = pager.width
        val h = pager.height
        if (w <= 0 || h <= 0) return // the layout listener calls this again once the size is known
        val cols = columnCount()
        val rows = if (HomeStore.appsRows > 0) HomeStore.appsRows else (h / dp(CELL_DP)).coerceIn(2, MAX_ROWS)
        val perPage = cols * rows
        iconDp = fitIconDp(w / cols, h / rows)

        rebuilding = true
        val pages = ArrayList<View>()
        var start = 0
        while (start < shown.size) {
            val page = AppsPage(context, cols, rows)
            for (i in start until min(start + perPage, shown.size)) page.addView(makeIcon(shown[i]))
            pages.add(page)
            start += perPage
        }
        if (pages.isEmpty()) pages.add(AppsPage(context, cols, rows))
        pager.setPages(pages, keepPage)
        rebuilding = false
    }

    /** Icon size of the current page layout: the size chosen in Settings, shrunk when the cells are too small for it. */
    private var iconDp = ICON_DP

    private fun fitIconDp(cellWpx: Int, cellHpx: Int): Int {
        val density = resources.displayMetrics.density
        val chosen = HomeStore.APPS_ICON_DP[HomeStore.appsIconSize]
        val byWidth = cellWpx / density - 8f
        val byHeight = cellHpx / density - (if (HomeStore.appsLabels) 34f else 14f)
        return minOf(chosen.toFloat(), byWidth, byHeight).toInt().coerceAtLeast(32)
    }

    private fun makeIcon(app: AppEntry): View {
        val view = AppIconView(context, app.icon, if (HomeStore.appsLabels) app.label else null, iconDp, Look.text)
        view.setPadding(dp(2), dp(6), dp(2), dp(6))
        view.gravity = Gravity.CENTER
        view.badgePackage = app.packageName
        view.contentDescription = app.label
        view.setOnClickListener { onLaunch(it, app.launchIntent) }
        view.setOnLongClickListener { v ->
            val handler = onAppLongPress
            handler?.invoke(v, app)
            handler != null
        }
        return view
    }

    private fun showSortMenu(anchor: View) {
        hideKeyboard()
        val popup = PopupMenu(context, anchor, Gravity.END)
        for (mode in SortMode.values()) {
            val item = popup.menu.add(0, mode.ordinal, mode.ordinal, mode.title)
            item.isCheckable = true
            item.isChecked = mode == sortMode
        }
        popup.menu.setGroupCheckable(0, true, true)
        val gridId = SortMode.values().size
        popup.menu.add(1, gridId, gridId, "Apps screen grid\u2026")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == gridId) {
                // Step 13: the grid (columns / rows / icon size / names) lives in Settings > Apps screen.
                context.startActivity(
                    Intent(context, HomeSettingsActivity::class.java).putExtra(HomeSettingsActivity.EXTRA_PAGE, "apps")
                )
            } else {
                setSort(SortMode.values()[item.itemId])
            }
            true
        }
        popup.show()
    }

    /** Opens the Apps screen with the cursor already in the search box (keyboard up). Used by the Home search bar. */
    fun openWithSearch() {
        open(true)
        postDelayed({ if (progress > 0f) focusSearch() }, 220)
    }

    private fun focusSearch() {
        searchField.requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(searchField, 0)
    }

    private fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    // ------------------------------------------------------------------ open / close

    /** Applies the look for [p]: scrim, panel fade / slide / grow. */
    fun setProgress(p: Float) {
        val value = p.coerceIn(0f, 1f)
        progress = value
        if (value > 0f && visibility != View.VISIBLE) visibility = View.VISIBLE
        if (value == 0f && visibility == View.VISIBLE) visibility = View.GONE

        setBackgroundColor(Look.veil((value * Look.veilStrength).toInt()))
        panel.alpha = (value * 1.25f).coerceAtMost(1f)
        val scale = 0.94f + 0.06f * value
        panel.scaleX = scale
        panel.scaleY = scale
        panel.translationY = (1f - value) * dpf(56f)
        onProgress?.invoke(value)
    }

    /** A finger took over the drawer: stop any running animation and draw through a layer. */
    fun beginDrag() {
        animator?.cancel()
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun open(animated: Boolean = true) = go(1f, animated)

    fun close(animated: Boolean = true) = go(0f, animated)

    /** Settles the drawer at [target] (0 or 1) with the standard decelerating curve. */
    fun animateTo(target: Float) = go(target, true)

    /** Back / Home: closes the drawer if it is open. Returns true when it was. */
    fun handleBack(): Boolean {
        if (progress <= 0f) return false
        close(true)
        return true
    }

    private fun go(target: Float, animated: Boolean) {
        animator?.cancel()
        if (target == 0f) {
            hideKeyboard()
            requestFocus()
        }
        if (!animated || progress == target) {
            setProgress(target)
            finishTransition(target)
            return
        }
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val distance = abs(target - progress)
        val anim = ValueAnimator.ofFloat(progress, target)
        anim.duration = (150 + 210 * distance).toLong()
        anim.interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        anim.addUpdateListener { setProgress(it.animatedValue as Float) }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) finishTransition(target)
            }
        })
        animator = anim
        anim.start()
    }

    private fun finishTransition(target: Float) {
        panel.setLayerType(View.LAYER_TYPE_NONE, null)
        if (target == 0f) {
            // Next time the drawer opens it starts clean: no old search, first page.
            searchField.setText("")
            pager.setPage(0, false)
        }
    }

    /** The pages move sideways only, so a swipe down always closes the drawer. */
    fun canStartCloseDrag(): Boolean = true

    // ------------------------------------------------------------------ one page of icons

    /** A page: [cols] x [rows] equal cells, filled left to right, top to bottom. */
    private class AppsPage(context: Context, private val cols: Int, private val rows: Int) : ViewGroup(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = View.MeasureSpec.getSize(widthMeasureSpec)
            val h = View.MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)
            val cellW = w / cols
            val cellH = h / rows
            val wSpec = View.MeasureSpec.makeMeasureSpec(cellW, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(cellH, View.MeasureSpec.EXACTLY)
            for (i in 0 until childCount) getChildAt(i).measure(wSpec, hSpec)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val cellW = (r - l) / cols
            val cellH = (b - t) / rows
            for (i in 0 until childCount) {
                val x = (i % cols) * cellW
                val y = (i / cols) * cellH
                getChildAt(i).layout(x, y, x + cellW, y + cellH)
            }
        }
    }

    companion object {
        const val ICON_DP = 58

        /** The icon pictures are made this big (Settings > Apps screen > Icon size: Large); smaller sizes just scale them down. */
        const val ICON_LOAD_DP = 64
        private const val PILL_HEIGHT_DP = 48
        private const val CELL_DP = 112
        private const val MAX_ROWS = 5
        private const val KEY_SORT = "apps_sort"
    }
}
