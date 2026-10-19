package com.oklabs.oneui9launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The Apps screen. It sits on top of the home screen and is driven by [progress]
 * (0 = hidden, 1 = fully open), either by a finger ([LauncherRoot]) or by [open] / [close].
 *
 * Search pill on top, one vertically scrolling grid of every app, A-Z index on the right edge.
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
    private val grid = GridView(context)
    private val pill = LinearLayout(context)
    private val searchField = EditText(context)
    private val clearButton = GlyphView(context, GlyphView.CLEAR).also { it.tint = Look.text }
    private val moreButton = GlyphView(context, GlyphView.MORE).also { it.tint = Look.text }
    private val emptyText = TextView(context)
    private val indexBar = IndexBar(context)

    private var baseApps: List<AppEntry> = emptyList()
    private var sortedApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var loaded = false
    private var sortMode = SortMode.from(prefs.getString(KEY_SORT, null))
    private val sectionStart = HashMap<Char, Int>()

    private var insetTop = 0
    private var insetBottom = 0
    private var animator: ValueAnimator? = null

    private val adapter = AppsAdapter()

    private val scrollListener = object : AbsListView.OnScrollListener {
        override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
        }

        override fun onScroll(
            view: AbsListView,
            firstVisibleItem: Int,
            visibleItemCount: Int,
            totalItemCount: Int
        ) {
            shown.getOrNull(firstVisibleItem)?.let { indexBar.setCurrent(it.letter) }
        }
    }

    init {
        visibility = View.GONE
        isClickable = true            // swallow touches so nothing behind the drawer reacts
        isFocusable = true
        isFocusableInTouchMode = true // the drawer itself takes focus, so the search box never grabs it

        addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- app grid
        grid.numColumns = columnCount()
        grid.verticalSpacing = dp(10)
        grid.horizontalSpacing = 0
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.setSelector(ColorDrawable(Color.TRANSPARENT))
        grid.isVerticalScrollBarEnabled = false
        grid.isVerticalFadingEdgeEnabled = true
        grid.setFadingEdgeLength(dp(28))
        grid.clipToPadding = false
        grid.setAdapter(adapter)
        grid.setOnScrollListener(scrollListener)
        panel.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- "No apps found"
        emptyText.text = "No apps found"
        emptyText.setTextColor(Look.textA(170))
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        emptyText.gravity = Gravity.CENTER_HORIZONTAL
        emptyText.visibility = View.GONE
        panel.addView(emptyText, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // --- A-Z index (right edge)
        indexBar.visibility = View.GONE
        indexBar.onLetter = { letter -> sectionStart[letter]?.let { grid.setSelection(it) } }
        panel.addView(indexBar, FrameLayout.LayoutParams(dp(96), MATCH, Gravity.END))

        // --- search pill
        buildPill()
        val pillParams = FrameLayout.LayoutParams(MATCH, dp(PILL_HEIGHT_DP), Gravity.TOP)
        pillParams.leftMargin = dp(16)
        pillParams.rightMargin = dp(16)
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
                applyFilter(scrollToTop = true)
            }
        })
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                val first = shown.firstOrNull()
                if (first != null && searchField.text.isNotEmpty()) {
                    onLaunch(grid.getChildAt(0) ?: grid, first.launchIntent)
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

        moreButton.setOnClickListener { showSortMenu(it) }
        pill.addView(moreButton, LinearLayout.LayoutParams(dp(40), dp(40)))
    }

    private fun columnCount(): Int {
        val metrics = resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        return (widthDp / 72f).toInt().coerceIn(4, 8)
    }

    private fun dp(value: Int): Int = context.dp(value)
    private fun dpf(value: Float): Float = context.dpf(value)

    /** Keeps the pill below the status bar and the last row above the navigation bar / keyboard. */
    fun setInsets(top: Int, bottom: Int) {
        if (top == insetTop && bottom == insetBottom) return
        insetTop = top
        insetBottom = bottom
        applyInsets()
    }

    private fun applyInsets() {
        val pillTop = max(insetTop, dp(24)) + dp(10)
        val gridTop = pillTop + dp(PILL_HEIGHT_DP) + dp(6)
        val bottom = insetBottom + dp(12)

        (pill.layoutParams as FrameLayout.LayoutParams).topMargin = pillTop
        (grid.layoutParams as FrameLayout.LayoutParams).topMargin = gridTop
        grid.setPadding(dp(8), dp(4), dp(8), bottom)
        (indexBar.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = gridTop
            bottomMargin = insetBottom + dp(8)
        }
        (emptyText.layoutParams as FrameLayout.LayoutParams).topMargin = gridTop + dp(70)
        panel.requestLayout()
    }

    // ------------------------------------------------------------------ data

    fun setApps(apps: List<AppEntry>) {
        baseApps = apps
        loaded = true
        sortedApps = AppRepository.sort(baseApps, sortMode)
        applyFilter(scrollToTop = false)
    }

    private fun setSort(mode: SortMode) {
        if (mode == sortMode) return
        sortMode = mode
        prefs.edit().putString(KEY_SORT, mode.key).apply()
        sortedApps = AppRepository.sort(baseApps, sortMode)
        applyFilter(scrollToTop = true)
    }

    private fun applyFilter(scrollToTop: Boolean) {
        val query = searchField.text?.toString().orEmpty()
        clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        shown = AppRepository.filter(sortedApps, query)
        adapter.notifyDataSetChanged()
        if (scrollToTop) grid.setSelection(0)
        emptyText.visibility = if (loaded && shown.isEmpty()) View.VISIBLE else View.GONE
        refreshIndex(query.isEmpty())
    }

    /** The A-Z strip only makes sense for the alphabetical list without a search. */
    private fun refreshIndex(noQuery: Boolean) {
        sectionStart.clear()
        if (sortMode == SortMode.AZ && noQuery) {
            shown.forEachIndexed { i, app ->
                if (!sectionStart.containsKey(app.letter)) sectionStart[app.letter] = i
            }
        }
        val visible = sectionStart.size >= 3
        indexBar.visibility = if (visible) View.VISIBLE else View.GONE
        indexBar.setLetters(sectionStart.keys)
        indexBar.setCurrent(shown.getOrNull(grid.firstVisiblePosition)?.letter)
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
        popup.setOnMenuItemClickListener { item ->
            setSort(SortMode.values()[item.itemId])
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
            // Next time the drawer opens it starts clean: no old search, list at the top.
            searchField.setText("")
            grid.setSelection(0)
        }
    }

    /** True when a downward swipe should close the drawer instead of scrolling the list. */
    fun canStartCloseDrag(): Boolean {
        if (progress < 1f) return true
        if (shown.isEmpty()) return true
        if (grid.firstVisiblePosition > 0) return false
        val first = grid.getChildAt(0) ?: return true
        return first.top >= grid.paddingTop
    }

    // ------------------------------------------------------------------ grid adapter

    private inner class AppsAdapter : BaseAdapter() {

        override fun getCount(): Int = shown.size

        override fun getItem(position: Int): Any = shown[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = shown[position]
            val view = (convertView as? AppIconView)
                ?: AppIconView(context, app.icon, app.label, ICON_DP, Look.text).also {
                    it.setPadding(dp(2), dp(6), dp(2), dp(6))
                }
            view.bind(app.icon, app.label)
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
    }

    companion object {
        const val ICON_DP = 54
        private const val PILL_HEIGHT_DP = 48
        private const val KEY_SORT = "apps_sort"
    }
}
