package com.oklabs.oneui9launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
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
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The open folder: the home screen dims and a rounded card grows out of the folder icon, showing the
 * folder's name and its apps (4 per row, scrolls when there are many). Tap an app to start it;
 * tap outside the card, Back or Home to close.
 * Step 5b: tap the name to rename the folder (keyboard opens, Done or tapping outside saves it) and
 * long-press an app to take it out of the folder (the activity starts the drag).
 */
class FolderLayer(
    context: Context,
    private val onLaunch: (View, Intent) -> Unit
) : FrameLayout(context) {

    /** True from the moment the card starts opening until it is completely gone. */
    val isOpen: Boolean get() = visibility == View.VISIBLE

    /** Step 8b: called when the card appears (true) or is completely gone (false). */
    var onOpenChanged: ((Boolean) -> Unit)? = null

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) onOpenChanged?.invoke(visibility == View.VISIBLE)
    }

    /** Set by the activity: an app in the card was long-pressed (quick menu, drag it out of the folder). */
    var onAppLongPress: ((View, AppEntry) -> Unit)? = null

    /**
     * Set by the activity each time a folder is opened: the person finished typing a new name.
     * Returns the name as it was saved (trimmed, blank = "Folder"), which the card then shows.
     */
    var onRename: ((String) -> String)? = null

    /** True while the name is being typed (the keyboard is up). */
    var isEditing: Boolean = false
        private set

    private val scrim = View(context)
    private var card: LinearLayout? = null
    private var titleView: TextView? = null
    private var titleEdit: EditText? = null
    private var currentName = ""

    /** The folder icon the card grew out of: hidden while the card is open, shown again as it shrinks back. */
    private var anchorView: View? = null
    private var cardTop = 0
    private var cardHeight = 0
    private var closing = false
    private var insetTop = 0
    private var insetBottom = 0
    private val ownPos = IntArray(2)
    private val anchorPos = IntArray(2)
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

    init {
        visibility = View.GONE
        scrim.setBackgroundColor(Color.argb(120, 0, 0, 0))
        scrim.isClickable = true
        // While typing a name the first tap outside only puts the keyboard away.
        scrim.setOnClickListener { if (isEditing) finishEditing() else close(true) }
        addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /** Keeps the card clear of the status bar and the navigation bar. */
    fun setInsets(top: Int, bottom: Int) {
        insetTop = top
        insetBottom = bottom
        if (card != null && !closing) applyShift() // the keyboard came or went
    }

    /**
     * Opens the card for [apps], growing out of [anchor] (the folder icon that was tapped).
     * With [rename] the name is ready to type right away (quick menu "Rename").
     */
    fun open(name: String, apps: List<AppEntry>, anchor: View, rename: Boolean = false) {
        if (apps.isEmpty()) return
        removeCard()
        anchorView?.alpha = 1f
        anchorView = null
        closing = false
        isEditing = false
        currentName = name

        val metrics = resources.displayMetrics
        val cardW = min(metrics.widthPixels - context.dp(32), context.dp(380))
        val pad = context.dp(12)
        val cellW = (cardW - 2 * pad) / COLS
        val rowH = context.dp(ROW_DP)
        val titleH = context.dp(54)
        val bottomPad = context.dp(12)
        val rowCount = (apps.size + COLS - 1) / COLS

        val layerH = if (height > 0) height else metrics.heightPixels
        val layerW = if (width > 0) width else metrics.widthPixels
        val areaTop = max(insetTop, context.dp(24)) + context.dp(16)
        val areaBottom = layerH - max(insetBottom, context.dp(12)) - context.dp(16)
        val maxContent = max(rowH, areaBottom - areaTop - titleH - bottomPad)
        val contentH = min(rowCount * rowH, maxContent)

        // The grid of apps: rows of COLS equal cells.
        val rows = LinearLayout(context)
        rows.orientation = LinearLayout.VERTICAL
        var index = 0
        val popIcons = ArrayList<View>()
        for (r in 0 until rowCount) {
            val line = LinearLayout(context)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until COLS) {
                val holder = FrameLayout(context)
                if (index < apps.size) {
                    val app = apps[index]
                    val icon = AppIconView(context, app.icon, app.label, ICON_DP, Look.text)
                    icon.contentDescription = app.label
                    icon.badgePackage = app.packageName
                    icon.setOnClickListener { view ->
                        onLaunch(view, app.launchIntent)
                        close(false)
                    }
                    icon.setOnLongClickListener { view ->
                        val handler = onAppLongPress
                        if (handler != null) finishEditing()
                        handler?.invoke(view, app)
                        handler != null
                    }
                    holder.addView(
                        icon,
                        FrameLayout.LayoutParams(cellW - context.dp(6), WRAP, Gravity.CENTER)
                    )
                    popIcons.add(icon)
                }
                line.addView(holder, LinearLayout.LayoutParams(cellW, rowH))
                index++
            }
            rows.addView(line, LinearLayout.LayoutParams(cellW * COLS, rowH))
        }

        val content: View
        if (rowCount * rowH > contentH) {
            val scroll = ScrollView(context)
            scroll.isVerticalScrollBarEnabled = false
            scroll.overScrollMode = View.OVER_SCROLL_NEVER
            scroll.addView(rows, FrameLayout.LayoutParams(cellW * COLS, WRAP))
            content = scroll
        } else {
            content = rows
        }

        // The name: plain text; tapping it swaps in a text field.
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val title = TextView(context)
        title.text = name
        title.setTextColor(Look.text)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        title.typeface = medium
        title.gravity = Gravity.CENTER
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.isClickable = true
        title.contentDescription = "$name, tap to rename"
        title.setOnClickListener { startEditing() }

        val edit = EditText(context)
        edit.setTextColor(Look.text)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        edit.typeface = medium
        edit.gravity = Gravity.CENTER
        edit.maxLines = 1
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        edit.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        edit.filters = arrayOf(InputFilter.LengthFilter(HomeLayout.MAX_FOLDER_NAME))
        edit.background = roundRect(Look.inkA(46), context.dpf(16f))
        edit.setPadding(context.dp(14), 0, context.dp(14), 0)
        edit.visibility = View.GONE
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishEditing()
                true
            } else {
                false
            }
        }

        val titleHolder = FrameLayout(context)
        titleHolder.addView(title, FrameLayout.LayoutParams(MATCH, MATCH))
        titleHolder.addView(edit, FrameLayout.LayoutParams(MATCH, context.dp(40), Gravity.CENTER))
        titleView = title
        titleEdit = edit

        val newCard = LinearLayout(context)
        newCard.orientation = LinearLayout.VERTICAL
        newCard.gravity = Gravity.CENTER_HORIZONTAL
        newCard.isClickable = true // taps on the card itself must not reach the scrim
        newCard.background = roundRect(Look.panel(232), context.dpf(30f))
        newCard.elevation = context.dpf(18f)
        newCard.setPadding(pad, 0, pad, bottomPad)
        newCard.addView(titleHolder, LinearLayout.LayoutParams(MATCH, titleH))
        newCard.addView(content, LinearLayout.LayoutParams(cellW * COLS, contentH))

        val cardH = titleH + contentH + bottomPad
        val top = areaTop + max(0, ((areaBottom - areaTop - cardH) * 0.4f).toInt())
        val lp = FrameLayout.LayoutParams(cardW, cardH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = top
        addView(newCard, lp)
        card = newCard
        cardTop = top
        cardHeight = cardH

        visibility = View.VISIBLE
        scrim.animate().cancel()
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(200).start()

        // The card grows out of the folder icon.
        anchor.getLocationOnScreen(anchorPos)
        getLocationOnScreen(ownPos)
        val centerX = anchorPos[0] - ownPos[0] + anchor.width / 2f
        val centerY = anchorPos[1] - ownPos[1] + anchor.height / 2f
        newCard.pivotX = centerX - (layerW - cardW) / 2f
        newCard.pivotY = centerY - top
        newCard.scaleX = START_SCALE
        newCard.scaleY = START_SCALE
        newCard.alpha = 0f
        newCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(260)
            .setInterpolator(ease)
            .start()

        // The folder icon "turns into" the card: it fades out underneath while the card grows.
        anchorView = anchor
        anchor.animate().alpha(0f).setDuration(120).start()

        // The apps pop in one after another (the first dozen; a long list just appears).
        for ((i, icon) in popIcons.withIndex()) {
            if (i >= POP_MAX) break
            icon.alpha = 0f
            icon.scaleX = POP_FROM
            icon.scaleY = POP_FROM
            icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(70L + i * 22L)
                .setDuration(190)
                .setInterpolator(ease)
                .withEndAction { icon.animate().setStartDelay(0) } // later presses must not wait
                .start()
        }

        if (rename) {
            // Let the card finish growing, then bring the keyboard up.
            postDelayed({ if (card === newCard && !closing) startEditing() }, 260)
        }
    }

    // ------------------------------------------------------------------ renaming

    /** The name becomes a text field with the keyboard up. */
    fun startEditing() {
        val edit = titleEdit ?: return
        val title = titleView ?: return
        if (card == null || closing || isEditing) return
        isEditing = true
        title.visibility = View.INVISIBLE
        edit.setText(currentName)
        edit.visibility = View.VISIBLE
        edit.requestFocus()
        edit.selectAll()
        val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        edit.post { keyboard?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT) }
    }

    /** Saves the typed name (through [onRename]), puts the keyboard away and shows the name as plain text again. */
    fun finishEditing() {
        if (!isEditing) return
        isEditing = false
        val edit = titleEdit
        val title = titleView
        val typed = edit?.text?.toString().orEmpty()
        val saved = onRename?.invoke(typed) ?: typed.trim().ifEmpty { currentName }
        currentName = saved
        if (title != null) {
            title.text = saved
            title.contentDescription = "$saved, tap to rename"
            title.visibility = View.VISIBLE
        }
        if (edit != null) {
            edit.clearFocus()
            edit.visibility = View.GONE
            val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            keyboard?.hideSoftInputFromWindow(edit.windowToken, 0)
        }
        if (card != null && !closing) applyShift()
    }

    /** With the keyboard up the card slides up just enough to stay above it (never under the status bar). */
    private fun applyShift() {
        val current = card ?: return
        val layerH = if (height > 0) height else resources.displayMetrics.heightPixels
        val bottomLimit = layerH - max(insetBottom, context.dp(12)) - context.dp(12)
        val minTop = max(insetTop, context.dp(24)) + context.dp(8)
        var shift = 0
        if (cardTop + cardHeight > bottomLimit) {
            shift = min(cardTop + cardHeight - bottomLimit, cardTop - minTop).coerceAtLeast(0)
        }
        current.animate().translationY(-shift.toFloat()).setDuration(160).setInterpolator(ease).start()
    }

    /** Closes the card, shrinking back into the folder icon, or at once when [animated] is false. */
    fun close(animated: Boolean) {
        if (!isOpen) return
        finishEditing() // a name being typed is saved, not lost
        val current = card
        if (!animated || current == null) {
            finishClose()
            return
        }
        if (closing) return
        closing = true
        scrim.animate().alpha(0f).setDuration(180).start()
        anchorView?.animate()?.alpha(1f)?.setStartDelay(70)?.setDuration(130)?.start()
        current.animate()
            .scaleX(START_SCALE)
            .scaleY(START_SCALE)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(ease)
            .withEndAction { finishClose() }
            .start()
    }

    private fun finishClose() {
        anchorView?.let {
            it.animate().cancel()
            it.animate().setStartDelay(0) // the icon's press animation must not wait
            it.alpha = 1f
        }
        anchorView = null
        scrim.animate().cancel()
        removeCard()
        closing = false
        visibility = View.GONE
    }

    private fun removeCard() {
        val old = card ?: return
        card = null
        titleView = null
        titleEdit = null
        isEditing = false
        old.animate().cancel()
        removeView(old)
    }

    private companion object {
        const val COLS = 4
        const val ICON_DP = 52
        const val ROW_DP = 88
        const val START_SCALE = 0.3f
        const val POP_FROM = 0.82f
        const val POP_MAX = 12
    }
}
