package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Settings (Step 9a / 9b, rebuilt in Step 13 as a full Settings app). Opened from the Home screen's long-press menu ("Settings"),
 * from the three-dot menu of the Apps screen ("Apps screen grid...") and from the launcher icon's long-press shortcut.
 *
 * The main page lists the categories; each opens its own page:
 *  Home screen (grid, icon size, search bar) - Apps screen (columns, rows, icon size, names, sort) - Dock (colour, gradient, opacity) - Wallpaper (gradients) -
 *  Icons (colourful / original, themed) - Look and feel (panels, blur, wallpaper scroll) - Gestures (notifications, double-tap lock) -
 *  Notification badges - Edge panel - Hide apps - Screen turning - Backup and restore - Contact us - Privacy policy -
 *  Terms of use - Permissions and data use - About.
 * Every choice is saved at once; the launcher builds its screen again when you go back to it.
 */
class HomeSettingsActivity : Activity() {

    private lateinit var holder: FrameLayout
    private lateinit var kit: SettingsKit

    /** The pages that are open, the last one is on screen. Back goes one page up; from the main page it closes Settings. */
    private val stack = ArrayList<String>()
    private val hidden = HashSet<String>()
    private val edgeApps = HashSet<String>()

    // The app list page is shared by "Hide apps" and "Edge panel apps": which set it edits and where it saves it.
    private var pickerSet: HashSet<String> = hidden
    private var pickerMax = Int.MAX_VALUE
    private var pickerSave: (Set<String>) -> Unit = {}
    private var appList: List<AppEntry>? = null
    private var adapter: HiddenAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setup()
        } catch (e: Throwable) {
            // Never leave the person with a window that does not open: show what went wrong instead.
            CrashLog.save(this, e)
            setContentView(crashView(), ViewGroup.LayoutParams(MATCH, MATCH))
        }
    }

    private fun setup() {
        Look.load(this)
        IconPack.load(this)
        IconTheme.load(this)
        hidden.addAll(HomeStore.savedHidden(this))
        edgeApps.addAll(HomeStore.savedEdgeApps(this))

        window.setBackgroundDrawable(ColorDrawable(Look.panel(255)))
        kit = SettingsKit(this)
        holder = FrameLayout(this)
        // Android 15+ draws apps edge to edge: keep the pages clear of the status and navigation bars.
        holder.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(holder, ViewGroup.LayoutParams(MATCH, MATCH))
        try {
            setDarkBarIcons(!Look.dark)
        } catch (e: Throwable) {
            // only the colour of the status bar icons
        }

        stack.add(MAIN)
        val first = intent?.getStringExtra(EXTRA_PAGE)
        if (first != null && first != MAIN && first in PAGES) stack.add(first)
        show()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { goBack() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the phone's own settings screens (default Home app, notification access): show the new state.
        if (::holder.isInitialized && (stack.last() == MAIN || stack.last() == BADGES)) show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goBack()
    }

    private fun goBack() {
        if (stack.size <= 1) {
            finish()
        } else {
            stack.removeAt(stack.size - 1)
            show()
        }
    }

    private fun go(page: String) {
        stack.add(page)
        show()
    }

    /** Draws the page on top of the stack. */
    private fun show() {
        holder.removeAllViews()
        val view = try {
            if (CrashLog.read(this) != null && !crashSeen) crashView() else buildPage()
        } catch (e: Throwable) {
            CrashLog.save(this, e)
            crashSeen = false
            crashView()
        }
        holder.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        holder.requestApplyInsets()
    }

    /** True once the person has seen the saved crash note this time and chose to continue. */
    private var crashSeen = false

    private fun buildPage(): View {
        return when (stack.last()) {
            HOME -> homePage()
            APPS -> appsPage()
            DOCK -> dockPage()
            WALLPAPER -> wallpaperPage()
            ICONS -> iconsPage()
            LOOK -> lookPage()
            GESTURES -> gesturesPage()
            BADGES -> badgesPage()
            EDGE -> edgePage()
            HIDE -> pickerPage(
                "Hide apps",
                "Hidden apps disappear from the Home screen and the Apps screen. Switch an app off to get it back.",
                hidden, Int.MAX_VALUE
            ) { HomeStore.setHidden(this, it) }
            EDGE_APPS -> pickerPage(
                "Edge panel apps",
                "Pick up to ${HomeStore.EDGE_MAX_APPS} apps for the panel that slides in from the right edge.",
                edgeApps, HomeStore.EDGE_MAX_APPS
            ) { HomeStore.setEdgeApps(this, it) }
            ROTATION -> rotationPage()
            BACKUP -> backupPage()
            CONTACT -> contactPage()
            PRIVACY -> privacyPage()
            TERMS -> termsPage()
            PERMISSIONS -> permissionsPage()
            ABOUT -> aboutPage()
            OPEN_SOURCE -> openSourcePage()
            else -> mainPage()
        }
    }

    /** Plain views only (no Settings building blocks), so it can show even when those are what failed. */
    private fun crashView(): View {
        val note = CrashLog.read(this) ?: "No details were saved."
        val dark = Look.dark
        val fg = if (dark) Color.WHITE else Color.rgb(22, 24, 30)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(if (dark) Color.rgb(34, 36, 44) else Color.rgb(250, 251, 254))
        page.setPadding(dp(18), dp(40), dp(18), dp(18))

        val title = TextView(this)
        title.text = "Something went wrong last time"
        title.setTextColor(fg)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        page.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
        val hint = TextView(this)
        hint.text = "Please send this text to " + getString(R.string.contact_email) + " (the button below opens your email app). It stays on your phone until you send it."
        hint.setTextColor(fg)
        hint.alpha = 0.7f
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        hint.setPadding(0, dp(8), 0, dp(10))
        page.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = android.widget.ScrollView(this)
        val text = TextView(this)
        text.text = note
        text.setTextIsSelectable(true)
        text.setTextColor(fg)
        text.typeface = android.graphics.Typeface.MONOSPACE
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        scroll.addView(text, FrameLayout.LayoutParams(MATCH, WRAP))
        page.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        fun button(label: String, onTap: () -> Unit) {
            val b = android.widget.Button(this)
            b.text = label
            b.setOnClickListener { onTap() }
            page.addView(b, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        button("Email this to the developer") {
            val email = getString(R.string.contact_email)
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + ": crash note")
                .putExtra(Intent.EXTRA_TEXT, note)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                toast("No email app found. Use Copy instead.")
            }
        }
        button("Copy") {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Crash note", note))
            toast("Copied")
        }
        button("Clear and continue") {
            CrashLog.clear(this)
            crashSeen = true
            if (::holder.isInitialized) show() else recreate()
        }
        return page
    }

    private fun prefs() = getSharedPreferences("launcher", MODE_PRIVATE)

    /** Something changed that the Home screen has to build again (it does so when it comes back to the front). */
    private fun changed() {
        Backup.reloadHome = true
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ main page

    private fun mainPage(): View = kit.page("Settings", { goBack() }) { box ->
        if (!isDefaultHome()) {
            kit.space(box, 6)
            kit.card(
                box,
                kit.row(SettingsTile.HOME, SettingsTile.ORANGE, "Make this your Home app", "It is not your default Home app yet") {
                    requestDefaultHome()
                }
            )
        }

        kit.label(box, "Screens")
        kit.card(
            box,
            kit.row(SettingsTile.HOME, SettingsTile.BLUE, "Home screen", homeSummary()) { go(HOME) },
            kit.row(SettingsTile.APPS, SettingsTile.PURPLE, "Apps screen", appsSummary()) { go(APPS) },
            kit.row(SettingsTile.DOCK, SettingsTile.TEAL, "Dock", "Colour, gradient and opacity") { go(DOCK) }
        )

        kit.label(box, "Look")
        kit.card(
            box,
            kit.row(SettingsTile.WALLPAPER, SettingsTile.ORANGE, "Wallpaper", "Gradient wallpapers · portrait and landscape") { go(WALLPAPER) },
            kit.row(SettingsTile.ICONS, SettingsTile.PINK, "Icons", IconPack.modeName() + " · Themed: " + IconTheme.choiceNames[IconTheme.savedMode(this)]) { go(ICONS) },
            kit.row(SettingsTile.LOOK, SettingsTile.SLATE, "Look and feel", "Panels: " + panelModeName() + " · Blur " + onOff(prefs().getBoolean(K_BLUR, true))) { go(LOOK) }
        )

        kit.label(box, "Features")
        kit.card(
            box,
            kit.row(SettingsTile.GESTURES, SettingsTile.GREEN, "Gestures", "Swipe down " + onOff(prefs().getBoolean(K_NOTIF_SWIPE, true)) + " · Double-tap lock " + onOff(prefs().getBoolean(K_LOCK_TAP, false))) { go(GESTURES) },
            kit.row(SettingsTile.BADGES, SettingsTile.RED, "Notification badges", listOf("Off", "Dot", "Number")[HomeStore.savedBadgeMode(this)]) { go(BADGES) },
            kit.row(SettingsTile.EDGE, SettingsTile.PURPLE, "Edge panel", onOff(HomeStore.savedEdgeOn(this))) { go(EDGE) },
            kit.row(SettingsTile.HIDE, SettingsTile.SLATE, "Hide apps", countText(hidden.size, "No app is hidden", "app is hidden", "apps are hidden")) { go(HIDE) },
            kit.row(SettingsTile.ROTATE, SettingsTile.TEAL, "Screen turning", if (HomeStore.savedLandscape(this)) "Landscape allowed" else "Portrait only") { go(ROTATION) }
        )

        kit.label(box, "Data")
        kit.card(
            box,
            kit.row(SettingsTile.BACKUP, SettingsTile.BLUE, "Backup and restore", "Save or restore your layout and settings") { go(BACKUP) }
        )

        kit.label(box, "Contact and policies")
        kit.card(
            box,
            kit.row(SettingsTile.MAIL, SettingsTile.BLUE, "Contact us", getString(R.string.contact_email)) { go(CONTACT) },
            kit.row(SettingsTile.PRIVACY, SettingsTile.GREEN, "Privacy policy", "What the app does with your data") { go(PRIVACY) },
            kit.row(SettingsTile.DOC, SettingsTile.ORANGE, "Terms of use", "The rules for using the app") { go(TERMS) },
            kit.row(SettingsTile.LOCK, SettingsTile.PURPLE, "Permissions and data use", "What each permission is for") { go(PERMISSIONS) }
        )

        kit.label(box, "About")
        kit.card(
            box,
            kit.row(SettingsTile.INFO, SettingsTile.SLATE, getString(R.string.app_name), "Version " + versionText()) { go(ABOUT) }
        )
        kit.note(box, "Changes apply when you go back to the Home screen.")
    }

    private fun onOff(on: Boolean) = if (on) "On" else "Off"

    private fun countText(n: Int, none: String, one: String, many: String) =
        if (n == 0) none else if (n == 1) "1 $one" else "$n $many"

    private fun panelModeName() = listOf("Auto", "Light", "Dark")[Look.savedMode(this)]

    private fun homeSummary(): String =
        HomeStore.savedCols(this).toString() + " x " + HomeStore.savedRows(this) + " · " +
            listOf("Small", "Medium", "Large")[HomeStore.savedIconSize(this)] + " icons"

    private fun appsSummary(): String {
        val cols = HomeStore.savedAppsCols(this)
        val rows = HomeStore.savedAppsRows(this)
        val grid = if (cols == 0 && rows == 0) "Auto grid" else (if (cols == 0) "Auto" else cols.toString()) + " x " + (if (rows == 0) "Auto" else rows.toString())
        return grid + " · " + SortMode.from(prefs().getString(K_APPS_SORT, null)).title
    }

    // ------------------------------------------------------------------ Home screen

    private fun homePage(): View = kit.page("Home screen", { goBack() }) { box ->
        kit.label(box, "Grid")
        val gridNames = HomeStore.GRIDS.map { "${it.first} x ${it.second}" }
        val currentGrid = HomeStore.GRIDS.indexOfFirst {
            it.first == HomeStore.savedCols(this) && it.second == HomeStore.savedRows(this)
        }.coerceAtLeast(1)
        kit.card(box, kit.chips("Home screen grid", gridNames, currentGrid) { index ->
            HomeStore.setGrid(this, HomeStore.GRIDS[index].first, HomeStore.GRIDS[index].second)
        })
        kit.note(box, "Columns x rows of icons on every page. Icons and widgets that no longer fit move to free spots.")

        kit.label(box, "Icons")
        kit.card(box, kit.chips("Icon size", listOf("Small", "Medium", "Large"), HomeStore.savedIconSize(this)) { index ->
            HomeStore.setIconSize(this, index)
        })

        kit.label(box, "Search")
        kit.card(
            box,
            kit.toggle(-1, 0, "Show search bar", "A search bar on the Home screen; tap it to search your apps", prefs().getBoolean(K_HOME_SEARCH, true)) { on ->
                prefs().edit().putBoolean(K_HOME_SEARCH, on).apply()
                changed()
            }
        )
    }

    // ------------------------------------------------------------------ Apps screen (Step 13)

    private fun appsPage(): View = kit.page("Apps screen", { goBack() }) { box ->
        var cols = HomeStore.savedAppsCols(this)
        var rows = HomeStore.savedAppsRows(this)

        val preview = GridPreview(this)
        val info = TextView(this)
        info.setTextColor(Look.textA(190))
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        info.gravity = Gravity.CENTER
        fun refreshPreview() {
            preview.setGrid(cols, rows)
            info.text = when {
                cols > 0 && rows > 0 -> "$cols columns x $rows rows = ${cols * rows} apps per page"
                cols == 0 && rows == 0 -> "Auto: the grid fits your screen"
                else -> (if (cols == 0) "Auto" else "$cols") + " columns x " + (if (rows == 0) "Auto" else "$rows") + " rows"
            }
        }
        refreshPreview()
        val previewBox = LinearLayout(this)
        previewBox.orientation = LinearLayout.VERTICAL
        previewBox.gravity = Gravity.CENTER_HORIZONTAL
        previewBox.setPadding(dp(16), dp(16), dp(16), dp(14))
        previewBox.addView(preview, LinearLayout.LayoutParams(dp(110), dp(150)))
        val infoLp = LinearLayout.LayoutParams(WRAP, WRAP)
        infoLp.topMargin = dp(12)
        previewBox.addView(info, infoLp)
        kit.card(box, previewBox)

        kit.label(box, "Grid")
        val colChoices = HomeStore.APPS_COL_CHOICES
        val rowChoices = HomeStore.APPS_ROW_CHOICES
        kit.card(
            box,
            kit.chips("Columns", colChoices.map { if (it == 0) "Auto" else it.toString() }, colChoices.indexOf(cols).coerceAtLeast(0)) { i ->
                cols = colChoices[i]
                HomeStore.setAppsCols(this, cols)
                refreshPreview()
            },
            kit.chips("Rows", rowChoices.map { if (it == 0) "Auto" else it.toString() }, rowChoices.indexOf(rows).coerceAtLeast(0)) { i ->
                rows = rowChoices[i]
                HomeStore.setAppsRows(this, rows)
                refreshPreview()
            }
        )
        kit.note(box, "Auto picks what fits your screen. The Apps screen is in pages: swipe left and right. Icons shrink by themselves when the cells get small.")

        kit.label(box, "Icons")
        kit.card(
            box,
            kit.chips("Icon size", listOf("Small", "Medium", "Large"), HomeStore.savedAppsIconSize(this)) { HomeStore.setAppsIconSize(this, it) },
            kit.toggle(-1, 0, "Show app names", "Names under the icons", HomeStore.savedAppsLabels(this)) { HomeStore.setAppsLabels(this, it) }
        )

        kit.label(box, "Order")
        val modes = SortMode.values()
        val currentMode = SortMode.from(prefs().getString(K_APPS_SORT, null))
        kit.card(box, kit.value("Sort apps", currentMode.title) {
            choose("Sort apps", modes.map { it.title }, modes.indexOf(currentMode)) { index ->
                prefs().edit().putString(K_APPS_SORT, modes[index].key).apply()
                changed()
                show()
            }
        })
        kit.card(
            box,
            kit.row(SettingsTile.HIDE, SettingsTile.SLATE, "Hide apps", countText(hidden.size, "No app is hidden", "app is hidden", "apps are hidden")) { go(HIDE) }
        )
    }

    // ------------------------------------------------------------------ Dock

    private fun dockPage(): View = kit.page("Dock", { goBack() }) { box ->
        val color = prefs().getInt(K_DOCK_COLOR, Color.WHITE)
        val opacity = prefs().getInt(K_DOCK_OPACITY, DockStyleSheet.DEFAULT_OPACITY)
        val gradient = prefs().getInt(K_DOCK_GRADIENT, 0)

        // A live picture of the dock on a dark backdrop (it follows the choices below).
        val preview = FrameLayout(this)
        preview.background = roundRect(Color.rgb(34, 38, 52), dpf(22f))
        val pill = View(this)
        pill.background = DockView.styleDrawable(this, color, opacity, gradient)
        preview.addView(pill, FrameLayout.LayoutParams(MATCH, dp(64), Gravity.CENTER).also { it.setMargins(dp(18), dp(22), dp(18), dp(22)) })
        box.addView(preview, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })

        kit.label(box, "Background")
        kit.card(
            box,
            kit.colorDots("Background colour", DockStyleSheet.COLORS, color) { picked ->
                // a plain colour switches the gradient off
                prefs().edit().putInt(K_DOCK_COLOR, picked).putInt(K_DOCK_GRADIENT, 0).apply()
                changed()
                show()
            },
            kit.gradientDots("Gradient", gradient) { picked ->
                val edit = prefs().edit().putInt(K_DOCK_GRADIENT, picked)
                // a gradient at the old 22% is hard to see: lift it to a clear glass look
                if (picked > 0 && opacity < DockStyleSheet.GRADIENT_MIN_OPACITY) edit.putInt(K_DOCK_OPACITY, DockStyleSheet.GRADIENT_OPACITY)
                edit.apply()
                changed()
                show()
            },
            kit.slider("Background opacity", 100, opacity, { "$it%" }) { value ->
                prefs().edit().putInt(K_DOCK_OPACITY, value).apply()
                changed()
                pill.background = DockView.styleDrawable(this, prefs().getInt(K_DOCK_COLOR, Color.WHITE), value, prefs().getInt(K_DOCK_GRADIENT, 0))
            }
        )
        kit.note(box, "The dock is the bar of favourite apps at the bottom of the Home screen. To change its apps, long-press an empty spot > Edit Home screen.")
    }

    // ------------------------------------------------------------------ Wallpaper (Step 15)

    /** What the wallpaper page has picked: the gradient (index in [Gradients.all], -1 = none yet), how it is cut, and where it is set. */
    private var wpSel = -1
    private var wpMode = Gradients.PORTRAIT
    private var wpTarget = 2

    private fun wallpaperPage(): View = kit.page("Wallpaper", { goBack() }) { box ->
        kit.card(
            box,
            kit.chips("Made for", listOf("Portrait", "Landscape", "Both"), wpMode) { wpMode = it; show() },
            kit.chips("Set on", listOf("Home", "Lock", "Both"), wpTarget) { wpTarget = it }
        )
        kit.note(
            box,
            when (wpMode) {
                Gradients.LANDSCAPE -> "Drawn wide, for a phone held sideways."
                Gradients.BOTH -> "Drawn square, so it looks right held either way (the phone crops it)."
                else -> "Drawn tall, for a phone held upright."
            }
        )

        kit.label(box, "Gradient wallpapers")
        val columns = if (wpMode == Gradients.LANDSCAPE) 2 else 3
        val gap = dp(10)
        val screenWidth = resources.displayMetrics.widthPixels - dp(36)
        val tileWidth = (screenWidth - gap * (columns - 1)) / columns
        val tileHeight = when (wpMode) {
            Gradients.LANDSCAPE -> tileWidth * 9 / 16
            Gradients.BOTH -> tileWidth
            else -> tileWidth * 16 / 9
        }
        var rowView: LinearLayout? = null
        for ((index, def) in Gradients.all.withIndex()) {
            if (index % columns == 0) {
                rowView = LinearLayout(this)
                rowView.orientation = LinearLayout.HORIZONTAL
                val rowLp = LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT)
                rowLp.bottomMargin = gap
                box.addView(rowView, rowLp)
            }
            rowView?.addView(wallpaperTile(def, index, tileWidth, tileHeight), LinearLayout.LayoutParams(tileWidth, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                if (index % columns != 0) it.leftMargin = gap
            })
        }

        val button = TextView(this)
        button.text = if (wpSel >= 0) "Set \u201C${Gradients.all[wpSel].name}\u201D as wallpaper" else "Pick a wallpaper"
        button.gravity = Gravity.CENTER
        button.setTextColor(Look.panel(255))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        button.setPadding(dp(16), dp(14), dp(16), dp(14))
        button.background = roundRect(if (wpSel >= 0) Look.text else Look.inkA(90), dpf(26f))
        button.isClickable = true
        button.setOnClickListener { applyWallpaper() }
        box.addView(button, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        kit.note(box, "Your phone keeps one wallpaper picture; the launcher draws it at your screen's size. Pick it again any time to change it.")
    }

    /** One tile: the gradient drawn small, its name, and a ring when it is the picked one. */
    private fun wallpaperTile(def: GradientDef, index: Int, width: Int, height: Int): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.isClickable = true
        cell.contentDescription = def.name
        cell.setOnClickListener {
            wpSel = index
            show()
        }
        val on = index == wpSel
        val frame = FrameLayout(this)
        frame.setPadding(dp(3), dp(3), dp(3), dp(3))
        frame.background = GradientDrawable().apply {
            cornerRadius = dpf(18f)
            setColor(Color.TRANSPARENT)
            setStroke(dp(if (on) 3 else 0), Look.text)
        }
        val image = ImageView(this)
        image.setImageBitmap(Gradients.render(def, width, height))
        image.scaleType = ImageView.ScaleType.FIT_XY
        image.clipToOutline = true
        image.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpf(15f))
            }
        }
        frame.addView(image, FrameLayout.LayoutParams(width - dp(6), height - dp(6)))
        cell.addView(frame, LinearLayout.LayoutParams(width, height))
        val name = TextView(this)
        name.text = def.name
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.setTextColor(if (on) Look.text else Look.textA(170))
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        name.setPadding(0, dp(5), 0, 0)
        cell.addView(name, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        return cell
    }

    /** Draws the picked gradient at the phone's screen size (portrait, landscape or square) and makes it the wallpaper. */
    private fun applyWallpaper() {
        val def = Gradients.all.getOrNull(wpSel)
        if (def == null) {
            toast("Pick a wallpaper first")
            return
        }
        val manager = WallpaperManager.getInstance(this)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
            toast("This phone does not let apps change the wallpaper.")
            return
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val (width, height) = Gradients.sizeFor(wpMode, metrics.widthPixels, metrics.heightPixels)
        val which = when (wpTarget) {
            0 -> WallpaperManager.FLAG_SYSTEM
            1 -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        toast("Setting the wallpaper...")
        Thread {
            val message = try {
                val bitmap = Gradients.render(def, width, height)
                manager.setBitmap(bitmap, null, true, which)
                bitmap.recycle()
                "Wallpaper set: ${def.name}"
            } catch (e: Throwable) {
                "Could not set the wallpaper"
            }
            runOnUiThread { toast(message) }
        }.start()
    }

    // ------------------------------------------------------------------ Icons

    private fun iconsPage(): View = kit.page("Icons", { goBack() }) { box ->
        kit.card(
            box,
            kit.chips("Icon style", listOf("Colourful", "Original"), if (IconPack.enabled) 0 else 1) { index ->
                IconPack.set(this, index == 0)
                changed()
            }
        )
        kit.note(
            box,
            "Colourful: the launcher's own icons replace the phone's Phone, Messages, Contacts, Camera, Gallery, Browser, Calendar, Clock, " +
                "Calculator, Files, Settings, Weather, Mail, Music, Videos, Maps, Notes, Recorder, Downloads, Games and Store apps. " +
                "Every other app keeps its own icon. Original: every app keeps its own icon."
        )
        kit.label(box, "Themed icons")
        val names = IconTheme.choiceNames
        kit.card(box, kit.value("Themed icons", names[IconTheme.savedMode(this)]) {
            choose("Themed icons", names, IconTheme.savedMode(this)) { index ->
                IconTheme.setMode(this, index)
                changed()
                show()
            }
        })
        kit.note(box, "Draws every icon in one colour: from your wallpaper or a colour you pick. While it is on it replaces the Colourful icons.")
    }

    // ------------------------------------------------------------------ Look and feel

    private fun lookPage(): View = kit.page("Look and feel", { goBack() }) { box ->
        kit.card(
            box,
            kit.chips("Panels", listOf("Auto", "Light", "Dark"), Look.savedMode(this)) { index ->
                Look.set(this, index)
                changed()
            }
        )
        kit.note(box, "Colours of the Apps screen, folders, menus and this Settings screen. Auto follows the phone's dark mode.")

        kit.label(box, "Effects")
        kit.card(
            box,
            kit.toggle(-1, 0, "Blur", "Blurs the wallpaper behind the Apps screen, folders and the widget picker (Android 12+)", prefs().getBoolean(K_BLUR, true)) { on ->
                prefs().edit().putBoolean(K_BLUR, on).apply()
                changed()
            },
            kit.toggle(-1, 0, "Wallpaper scroll", "The wallpaper slides a little when you swipe between pages", prefs().getBoolean(K_WALLPAPER_SCROLL, true)) { on ->
                prefs().edit().putBoolean(K_WALLPAPER_SCROLL, on).apply()
                changed()
            }
        )
    }

    // ------------------------------------------------------------------ Gestures

    private fun gesturesPage(): View = kit.page("Gestures", { goBack() }) { box ->
        kit.card(
            box,
            kit.toggle(-1, 0, "Swipe down for notifications", "Swipe down on the Home screen to open the notification panel", prefs().getBoolean(K_NOTIF_SWIPE, true)) { on ->
                prefs().edit().putBoolean(K_NOTIF_SWIPE, on).apply()
                changed()
            },
            kit.toggle(-1, 0, "Double-tap to lock", "Double-tap an empty spot on the Home screen to lock the screen", prefs().getBoolean(K_LOCK_TAP, false)) { on ->
                if (!on) {
                    prefs().edit().putBoolean(K_LOCK_TAP, false).apply()
                    changed()
                } else if (LockAdmin.isActive(this)) {
                    prefs().edit().putBoolean(K_LOCK_TAP, true).apply()
                    changed()
                } else {
                    askLockAccess()
                }
            }
        )
        kit.note(
            box,
            "Double-tap to lock needs Android's \"device administrator\" access with the single power \"lock the screen\". " +
                "It cannot read, change or erase anything. You can switch it off here or in the phone's Settings > Security > Device admin apps."
        )
        kit.label(box, "Other gestures")
        kit.card(
            box,
            kit.paragraph("Swipe up on the Home screen: opens the Apps screen.\nPinch on the Home screen: opens the edit screen.\nLong-press an empty spot: opens the Home menu.\nLong-press an icon: opens its quick menu.")
        )
    }

    private fun askLockAccess() {
        AlertDialog.Builder(this)
            .setTitle("Allow \"lock the screen\"")
            .setMessage(
                "To lock the screen, Android needs you to make " + getString(R.string.app_name) + " a device administrator. " +
                    "The only thing it can do with this is lock the screen when you double-tap an empty spot. " +
                    "It cannot wipe data, change passwords or read anything. You can switch it off at any time."
            )
            .setPositiveButton("Continue") { _, _ ->
                try {
                    startActivityForResult(LockAdmin.requestIntent(this), REQ_LOCK)
                } catch (e: Exception) {
                    toast("Can't ask for this permission on this phone")
                    show()
                }
            }
            .setNegativeButton("Not now") { _, _ -> show() }
            .setOnCancelListener { show() }
            .show()
    }

    // ------------------------------------------------------------------ Notification badges

    private fun badgesPage(): View = kit.page("Notification badges", { goBack() }) { box ->
        kit.card(
            box,
            kit.chips("Badges on app icons", listOf("Off", "Dot", "Number"), HomeStore.savedBadgeMode(this)) { index ->
                HomeStore.setBadgeMode(this, index)
                if (index != HomeStore.BADGE_OFF && !Badges.accessGranted(this)) askNotificationAccess()
            }
        )
        kit.note(box, "Shows a red dot or the number of notifications on an app's icon. The launcher only counts notifications; it never reads or stores them.")
        kit.label(box, "Access")
        kit.card(
            box,
            kit.row(
                SettingsTile.BADGES, SettingsTile.RED, "Notification access",
                if (Badges.accessGranted(this)) "Allowed" else "Not allowed yet"
            ) { askNotificationAccess() }
        )
    }

    private fun askNotificationAccess() {
        // Play Store: say clearly what the access is for before the phone's own screen opens.
        AlertDialog.Builder(this)
            .setTitle("Notification access")
            .setMessage(
                "To show badges, " + getString(R.string.app_name) + " needs Notification access. " +
                    "It only counts the notifications of each app on this phone. It never reads, stores or sends " +
                    "their content, and it has no internet access. Nothing is shown until you allow it. " +
                    "You can turn it off at any time in the phone's settings."
            )
            .setPositiveButton("Continue") { _, _ -> Badges.openAccessSettings(this) }
            .setNegativeButton("Not now", null)
            .show()
    }

    // ------------------------------------------------------------------ Edge panel

    private fun edgePage(): View = kit.page("Edge panel", { goBack() }) { box ->
        kit.card(
            box,
            kit.chips("Edge panel", listOf("Off", "On"), if (HomeStore.savedEdgeOn(this)) 1 else 0) { index ->
                HomeStore.setEdgeOn(this, index == 1)
            }
        )
        kit.note(box, "A thin handle appears on the right edge of the Home screen. Tap it or drag it to the left.")
        kit.label(box, "Apps")
        kit.card(
            box,
            kit.row(-1, 0, "Edge panel apps", countText(edgeApps.size, "No app chosen", "app", "apps")) { go(EDGE_APPS) }
        )
    }

    // ------------------------------------------------------------------ Screen turning

    private fun rotationPage(): View = kit.page("Screen turning", { goBack() }) { box ->
        kit.card(
            box,
            kit.chips("Home screen", listOf("Portrait only", "Allow landscape"), if (HomeStore.savedLandscape(this)) 1 else 0) { index ->
                HomeStore.setLandscape(this, index == 1)
            }
        )
        kit.note(box, "In landscape the icons lose their labels so they fit the shorter page.")
    }

    // ------------------------------------------------------------------ backup and restore (Step 10b)

    private fun backupPage(): View = kit.page("Backup and restore", { goBack() }) { box ->
        kit.card(
            box,
            kit.row(SettingsTile.BACKUP, SettingsTile.BLUE, "Back up layout and settings", "Saves a file; you choose where") { startBackup() },
            kit.row(SettingsTile.BACKUP, SettingsTile.GREEN, "Restore from a backup", "Replaces your layout and settings") { startRestore() }
        )
        kit.note(box, "A backup holds the Home layout, folders, dock and all settings. Widgets are not included: add them again after a restore. The file stays with you; the app never sends it anywhere.")
    }

    private fun startBackup() {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, "OkLauncher-backup-$stamp.txt")
        try {
            startActivityForResult(intent, REQ_BACKUP)
        } catch (e: Exception) {
            toast("This phone has no file picker")
        }
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try {
            startActivityForResult(intent, REQ_RESTORE)
        } catch (e: Exception) {
            toast("This phone has no file picker")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOCK) {
            if (LockAdmin.isActive(this)) {
                prefs().edit().putBoolean(K_LOCK_TAP, true).apply()
                changed()
            }
            show()
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_BACKUP -> {
                val saved = try {
                    contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toByteArray(Charsets.UTF_8)) } != null
                } catch (e: Exception) {
                    false
                }
                toast(if (saved) "Backup saved" else "Could not save the backup")
            }
            REQ_RESTORE -> {
                val text = try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size <= MAX_BACKUP_BYTES) String(bytes, Charsets.UTF_8) else null
                } catch (e: Exception) {
                    null
                }
                if (text == null || !Backup.isBackup(text)) {
                    toast("This is not a " + getString(R.string.app_name) + " backup")
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle("Restore this backup?")
                    .setMessage("Your Home layout and settings are replaced. Widgets are not part of a backup, add them again afterwards.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore") { _, _ -> finishRestore(text) }
                    .show()
            }
        }
    }

    private fun finishRestore(text: String) {
        if (Backup.restore(this, text) < 0) {
            toast("Could not restore")
            return
        }
        hidden.clear()
        hidden.addAll(HomeStore.savedHidden(this))
        edgeApps.clear()
        edgeApps.addAll(HomeStore.savedEdgeApps(this))
        IconPack.load(this)
        IconTheme.load(this)
        Look.load(this)
        show()
        toast("Restored. Go back to the Home screen; add your widgets again.")
    }

    // ------------------------------------------------------------------ Contact us (Step 13)

    private fun contactPage(): View = kit.page("Contact us", { goBack() }) { box ->
        val email = getString(R.string.contact_email)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(18), dp(16), dp(18), dp(16))
        head.addView(kit.paragraph(getString(R.string.developer_name), bold = true).also { it.setPadding(0, 0, 0, 0) })
        head.addView(
            kit.paragraph("Questions, bug reports, ideas and privacy requests are welcome at:\n$email").also { it.setPadding(0, dp(6), 0, 0) }
        )
        kit.card(box, head)

        kit.label(box, "Write to us")
        kit.card(
            box,
            kit.row(SettingsTile.MAIL, SettingsTile.BLUE, "Email us", "Feedback, questions, ideas") { composeEmail("Feedback", "") },
            kit.row(SettingsTile.BUG, SettingsTile.ORANGE, "Report a problem", "Adds your app, Android and phone model") { composeEmail("Problem report", problemInfo()) },
            kit.row(SettingsTile.PRIVACY, SettingsTile.GREEN, "Privacy question or request", "About your data and this app") { composeEmail("Privacy question", "") },
            kit.row(SettingsTile.COPY, SettingsTile.SLATE, "Copy the email address", email) { copyEmail() }
        )
        kit.note(
            box,
            "These open your own email app with a message to $email. You can read and change it first; nothing is sent until you press Send. " +
                "\"Report a problem\" fills in the app version, the Android version and the phone model to help find the bug; delete them if you prefer."
        )

        kit.label(box, "Policies")
        kit.card(
            box,
            kit.row(SettingsTile.PRIVACY, SettingsTile.GREEN, "Privacy policy", "What the app does with your data") { go(PRIVACY) },
            kit.row(SettingsTile.DOC, SettingsTile.ORANGE, "Terms of use", "The rules for using the app") { go(TERMS) },
            kit.row(SettingsTile.LOCK, SettingsTile.PURPLE, "Permissions and data use", "What each permission is for") { go(PERMISSIONS) }
        )

        if (onlinePagesReady()) {
            kit.label(box, "Online")
            kit.card(
                box,
                kit.row(SettingsTile.GLOBE, SettingsTile.TEAL, "Privacy policy online", "Opens in your browser") { openLink(R.string.privacy_url) },
                kit.row(SettingsTile.GLOBE, SettingsTile.TEAL, "Terms of use online", "Opens in your browser") { openLink(R.string.terms_url) }
            )
        }
    }

    /** The policy pages are online once YOUR_NAME in strings.xml is replaced (see PLAY_STORE.md). Until then the online rows stay hidden. */
    private fun onlinePagesReady(): Boolean = !getString(R.string.privacy_url).contains("YOUR_NAME")

    private fun openLink(urlRes: Int) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlRes))))
        } catch (e: Exception) {
            toast("No browser found")
        }
    }

    private fun problemInfo(): String =
        "Describe the problem here:\n\n\n--- Added to help us find the bug (you can delete this) ---\n" +
            getString(R.string.app_name) + " " + versionText() + "\n" +
            "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
            "Phone: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"

    private fun composeEmail(subject: String, body: String) {
        val email = getString(R.string.contact_email)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + ": " + subject)
        if (body.isNotEmpty()) intent.putExtra(Intent.EXTRA_TEXT, body)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            copyEmail()
            toast("No email app found. The address was copied: $email")
        }
    }

    private fun copyEmail() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Email", getString(R.string.contact_email)))
        toast("Email address copied")
    }

    // ------------------------------------------------------------------ policies (Step 13)

    private fun textBlock(section: LegalText.Section): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(kit.paragraph(LegalText.fill(this, section.heading), bold = true))
        for (text in section.paragraphs) col.addView(kit.paragraph(LegalText.fill(this, text)))
        return col
    }

    private fun legalIntro(box: LinearLayout, text: String) {
        kit.note(box, LegalText.fill(this, text) + "\nEffective date: " + LegalText.EFFECTIVE)
    }

    private fun privacyPage(): View = kit.page("Privacy policy", { goBack() }) { box ->
        legalIntro(box, "{app} by {company}")
        for (section in LegalText.privacy) {
            val block = textBlock(section)
            if (section.heading.startsWith("4.")) {
                for (permission in LegalText.permissions) block.addView(permissionView(permission))
            }
            kit.space(box, 10)
            kit.card(box, block)
        }
        footer(box)
    }

    private fun termsPage(): View = kit.page("Terms of use", { goBack() }) { box ->
        legalIntro(box, "{app} by {company}")
        for (section in LegalText.terms) {
            kit.space(box, 10)
            kit.card(box, textBlock(section))
        }
        footer(box)
    }

    private fun permissionsPage(): View = kit.page("Permissions and data use", { goBack() }) { box ->
        kit.note(box, "What {app} can access on this phone and why. It collects no data and has no internet access.".let { LegalText.fill(this, it) })
        kit.label(box, "Always on")
        kit.card(box, *LegalText.permissions.filter { !it.optional }.map { permissionView(it) }.toTypedArray())
        kit.label(box, "Only if you turn the feature on")
        kit.card(box, *LegalText.permissions.filter { it.optional }.map { permissionView(it) }.toTypedArray())
        kit.label(box, "Data collected")
        kit.card(box, kit.paragraph("None. Your layout and settings stay on this phone. You can erase them by clearing the app's storage or uninstalling it."))
        footer(box)
    }

    private fun permissionView(permission: LegalText.Permission): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(kit.paragraph(permission.name + if (permission.optional) " (optional)" else "", bold = true))
        col.addView(kit.paragraph(permission.why))
        return col
    }

    /** Contact line at the end of every policy page. */
    private fun footer(box: LinearLayout) {
        kit.space(box, 14)
        kit.card(
            box,
            kit.row(SettingsTile.MAIL, SettingsTile.BLUE, "Questions about this?", getString(R.string.contact_email)) { go(CONTACT) }
        )
    }

    // ------------------------------------------------------------------ About (Step 10c)

    @Suppress("DEPRECATION")
    private fun versionText(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        (info.versionName ?: "?") + " (" + info.versionCode + ")"
    } catch (e: Exception) {
        "?"
    }

    private fun aboutPage(): View = kit.page("About", { goBack() }) { box ->
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.gravity = Gravity.CENTER_HORIZONTAL
        head.setPadding(dp(16), dp(10), dp(16), dp(6))

        val icon = ImageView(this)
        icon.setImageDrawable(applicationInfo.loadIcon(packageManager))
        head.addView(icon, LinearLayout.LayoutParams(dp(88), dp(88)))

        val name = TextView(this)
        name.text = getString(R.string.app_name)
        name.setTextColor(Look.text)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        name.gravity = Gravity.CENTER
        val nameLp = LinearLayout.LayoutParams(WRAP, WRAP)
        nameLp.topMargin = dp(14)
        head.addView(name, nameLp)

        val version = TextView(this)
        version.text = "Version " + versionText()
        version.setTextColor(Look.textA(160))
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        head.addView(version, LinearLayout.LayoutParams(WRAP, WRAP))

        val about = TextView(this)
        about.text = getString(R.string.app_tagline) + "\n" + getString(R.string.app_credits)
        about.setTextColor(Look.textA(200))
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
        about.gravity = Gravity.CENTER
        val aboutLp = LinearLayout.LayoutParams(WRAP, WRAP)
        aboutLp.topMargin = dp(16)
        head.addView(about, aboutLp)
        box.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        kit.space(box, 8)
        kit.card(
            box,
            kit.row(SettingsTile.STAR, SettingsTile.YELLOW, "Rate this app", "Opens the store page") { rateApp() },
            kit.row(SettingsTile.SHARE, SettingsTile.BLUE, "Share this app", "Send the store link to a friend") { shareApp() },
            kit.row(SettingsTile.BUG, SettingsTile.ORANGE, "Report a problem", "Email " + getString(R.string.contact_email)) { composeEmail("Problem report", problemInfo()) }
        )

        kit.label(box, "Contact and policies")
        kit.card(
            box,
            kit.row(SettingsTile.MAIL, SettingsTile.BLUE, "Contact us", getString(R.string.contact_email)) { go(CONTACT) },
            kit.row(SettingsTile.PRIVACY, SettingsTile.GREEN, "Privacy policy", null) { go(PRIVACY) },
            kit.row(SettingsTile.DOC, SettingsTile.ORANGE, "Terms of use", null) { go(TERMS) },
            kit.row(SettingsTile.LOCK, SettingsTile.PURPLE, "Permissions and data use", null) { go(PERMISSIONS) },
            kit.row(SettingsTile.INFO, SettingsTile.SLATE, "Code, icons and trademarks", null) { go(OPEN_SOURCE) }
        )
    }

    private fun openSourcePage(): View = kit.page("Code, icons and trademarks", { goBack() }) { box ->
        for (section in LegalText.openSource) {
            kit.space(box, 10)
            kit.card(box, textBlock(section))
        }
    }

    private fun storeUrl() = "https://play.google.com/store/apps/details?id=$packageName"

    private fun rateApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl())))
            } catch (e2: Exception) {
                toast("No app can open the store page")
            }
        }
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.app_name) + ": " + storeUrl())
        try {
            startActivity(Intent.createChooser(send, "Share " + getString(R.string.app_name)))
        } catch (e: Exception) {
            toast("Nothing to share with")
        }
    }

    // ------------------------------------------------------------------ default Home app

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return pkg == packageName
    }

    private fun requestDefaultHome() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                toast("Open Settings > Apps > Default apps > Home app")
            }
        }
    }

    // ------------------------------------------------------------------ choose from a list

    private fun choose(title: String, names: List<String>, selected: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(names.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                onPick(which)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ hide apps / edge panel apps

    private fun pickerPage(titleText: String, noteText: String, set: HashSet<String>, max: Int, save: (Set<String>) -> Unit): View {
        pickerSet = set
        pickerMax = max
        pickerSave = save

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.addView(kit.topBar(titleText) { goBack() }, LinearLayout.LayoutParams(MATCH, WRAP))
        val note = TextView(this)
        note.text = noteText
        note.setTextColor(Look.textA(140))
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        note.setPadding(dp(24), dp(4), dp(24), dp(4))
        page.addView(note, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = ListView(this)
        list.divider = null
        list.isVerticalScrollBarEnabled = false
        list.setPadding(dp(20), 0, dp(20), 0)
        list.clipToPadding = false
        val listAdapter = HiddenAdapter()
        adapter = listAdapter
        list.adapter = listAdapter
        val lp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        lp.topMargin = dp(8)
        page.addView(list, lp)

        val loaded = appList
        if (loaded != null) {
            listAdapter.set(loaded)
        } else {
            AppRepository.load(this, dp(40)) { found ->
                if (isDestroyed) return@load
                val sorted = AppRepository.sort(found, SortMode.AZ)
                appList = sorted
                adapter?.set(sorted)
            }
        }
        return page
    }

    private inner class HiddenAdapter : BaseAdapter() {
        private var items: List<AppEntry> = emptyList()

        fun set(list: List<AppEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = items[position]
            val row: LinearLayout
            if (convertView is LinearLayout) {
                row = convertView
            } else {
                row = LinearLayout(this@HomeSettingsActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(7), 0, dp(7))
                val icon = ImageView(this@HomeSettingsActivity)
                row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
                val name = TextView(this@HomeSettingsActivity)
                name.setTextColor(Look.text)
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                name.maxLines = 1
                name.ellipsize = android.text.TextUtils.TruncateAt.END
                val nameLp = LinearLayout.LayoutParams(0, WRAP, 1f)
                nameLp.leftMargin = dp(14)
                nameLp.rightMargin = dp(10)
                row.addView(name, nameLp)
                val toggle = Switch(this@HomeSettingsActivity)
                toggle.isClickable = false // the whole row is the button
                row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP))
            }
            (row.getChildAt(0) as ImageView).setImageBitmap(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            val toggle = row.getChildAt(2) as Switch
            val key = app.component.flattenToString()
            toggle.isChecked = pickerSet.contains(key)
            row.setOnClickListener {
                if (!pickerSet.remove(key)) {
                    if (pickerSet.size >= pickerMax) {
                        Toast.makeText(this@HomeSettingsActivity, "Up to $pickerMax apps", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    pickerSet.add(key)
                }
                toggle.isChecked = pickerSet.contains(key)
                pickerSave(pickerSet)
            }
            return row
        }
    }

    // ------------------------------------------------------------------ small helpers

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    /** Dark (true) or light (false) icons in the status and navigation bar. */
    @Suppress("DEPRECATION")
    private fun setDarkBarIcons(dark: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) mask else 0, mask)
        } else {
            var flags = window.decorView.systemUiVisibility
            val wanted = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            flags = if (dark) flags or wanted else flags and wanted.inv()
            window.decorView.systemUiVisibility = flags
        }
    }

    companion object {
        /** Start Settings on a page other than the main one, e.g. "apps" (used by the Apps screen's menu and the icon shortcut). */
        const val EXTRA_PAGE = "page"

        /** The page of Settings > Wallpaper (the Home screen menu opens it with [EXTRA_PAGE]). */
        const val PAGE_WALLPAPER = "wallpaper"

        private const val MAIN = "main"
        private const val HOME = "home"
        private const val APPS = "apps"
        private const val DOCK = "dock"
        private const val WALLPAPER = "wallpaper"
        private const val ICONS = "icons"
        private const val LOOK = "look"
        private const val GESTURES = "gestures"
        private const val BADGES = "badges"
        private const val EDGE = "edge"
        private const val HIDE = "hide"
        private const val EDGE_APPS = "edgeapps"
        private const val ROTATION = "rotation"
        private const val BACKUP = "backup"
        private const val CONTACT = "contact"
        private const val PRIVACY = "privacy"
        private const val TERMS = "terms"
        private const val PERMISSIONS = "permissions"
        private const val ABOUT = "about"
        private const val OPEN_SOURCE = "opensource"

        /** Pages a caller may ask for with [EXTRA_PAGE]. */
        private val PAGES = setOf(
            HOME, APPS, DOCK, WALLPAPER, ICONS, LOOK, GESTURES, BADGES, EDGE, HIDE, ROTATION, BACKUP, CONTACT, PRIVACY, TERMS, PERMISSIONS, ABOUT
        )

        // The launcher's saved settings (same keys the Home screen reads).
        private const val K_HOME_SEARCH = "home_search"
        private const val K_NOTIF_SWIPE = "notif_swipe"
        private const val K_LOCK_TAP = "lock_tap"
        private const val K_BLUR = "blur"
        private const val K_WALLPAPER_SCROLL = "wallpaper_scroll"
        private const val K_DOCK_COLOR = "dock_color"
        private const val K_DOCK_OPACITY = "dock_opacity"
        private const val K_DOCK_GRADIENT = "dock_gradient"
        private const val K_APPS_SORT = "apps_sort"

        private const val REQ_BACKUP = 2001
        private const val REQ_RESTORE = 2002
        private const val REQ_LOCK = 2003
        private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
    }
}
