package com.oklabs.oneui9launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import kotlin.math.abs

/**
 * Home screen: wallpaper, edge-to-edge, the swipeable home pages with their app grid (Step 3), page dots,
 * the Favorites dock and the Apps screen that opens with a swipe up (Step 2).
 * Step 4: long-press an icon for the quick menu, keep the finger down to drag it around.
 * Step 5a: rest a dragged icon on another icon to make a folder; tap a folder to open it.
 * Step 5b: rename a folder, drag apps out of its card, long-press a folder to move / ungroup / remove it.
 * Step 5c: the dock is part of the saved layout: drag icons in and out of it, make folders in it.
 * Step 6a: widgets. Long-press an empty spot > Widgets opens the picker; a widget is added to the home page (it can
 * ask to be allowed and to be set up first), shows live on the page, is saved with the layout and can be removed.
 * Step 6c2: the launcher's own widgets have a setup screen (style, colour, time format, date, background) that opens when
 * they are added and again from the widget's menu ("Widget settings").
 * Step 6b1: a widget can be moved: long-press lifts it (menu: Remove widget), keep the finger down and drag it to another
 * spot or page; the apps under the new spot step aside.
 * Step 6b2: a widget can be resized: long-press it > Resize shows a frame with handles; drag a handle and the widget
 * snaps to whole cells (apps under the new size step aside).
 * Later steps add gestures...
 */
class LauncherActivity : Activity() {

    private lateinit var root: LauncherRoot
    private lateinit var homeLayer: FrameLayout
    private lateinit var pager: HomePager
    private lateinit var dock: DockView
    private lateinit var dots: PageDots
    private lateinit var searchBar: HomeSearchBar
    private lateinit var editScrim: View
    private lateinit var editPanel: android.widget.LinearLayout
    private lateinit var editSearchButton: TextView
    private lateinit var dockSheet: DockStyleSheet

    /** True while the zoomed-out edit screen shows (Step 7c). */
    private var editMode = false
    private lateinit var edgePanel: EdgePanel
    private lateinit var quickPanel: QuickPanel
    private lateinit var notifPage: NotificationPage
    private lateinit var edgeHandle: FrameLayout
    private var insetLeft = 0
    private var insetRight = 0
    private lateinit var defaultChip: TextView
    private lateinit var apps: AppsScreen
    private lateinit var dragLayer: DragLayer
    private lateinit var folderLayer: FolderLayer
    private lateinit var widgetPicker: WidgetPicker
    private lateinit var widgetHost: WidgetHost
    private lateinit var resizeLayer: ResizeLayer

    /** The live widget views by widget id. A page rebuild moves them to the new page instead of making them again. */
    private val widgetFrames = HashMap<Int, WidgetFrame>()

    // A widget that is being added: the id is allocated, the person may still be asked to allow it / set it up.
    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null
    private var pendingSpanX = 1
    private var pendingSpanY = 1
    private var pendingPage = 0
    private var pickerLoading = false

    /** Every launchable app as last loaded. The home pages are built from this plus [homeLayout]. */
    private var allApps: List<AppEntry> = emptyList()

    /** Which app sits where on the home pages. Null until the first app list has arrived. */
    private var homeLayout: HomeLayout? = null

    private var insetTop = 0
    private var insetBottom = 0
    private var imeBottom = 0

    /** True while we are the visible app. Home pressed from another app must close the drawer without animation. */
    private var isForeground = false

    private val handler = Handler(Looper.getMainLooper())

    /** What the drag layer needs from the home screen: pages, drops, the Apps screen. */
    private val dragHost = object : DragHost {
        override fun dock(): DockView? = this@LauncherActivity.dock

        override fun currentPageIndex(): Int = pager.page

        override fun currentPage(): CellLayout? = pager.getChildAt(pager.page) as? CellLayout

        override fun slidePage(direction: Int): Boolean {
            val target = pager.page + direction
            if (target < 0) return false
            if (target >= pager.pageCount) {
                if (pager.pageCount >= HomeLayout.MAX_PAGES) return false
                pager.addPage(newHomePage()) // a fresh empty page to drop on; it vanishes again if left empty
            }
            pager.setPage(target, animated = true)
            return true
        }

        override fun homeReady(): Boolean = apps.progress == 0f

        override fun settleHome() {
            apps.close(animated = false)
        }

        override fun onDragStarted(item: DragItem) {
            if (item.origin == DragOrigin.DRAWER) apps.close(animated = true)
            if (item.origin == DragOrigin.FOLDER) folderLayer.close(animated = true) // the app leaves the card
        }

        override fun canPlaceWidget(item: DragItem, page: Int, col: Int, row: Int): Boolean =
            homeLayout?.canMoveWidget(item.key, page, col, row) == true

        override fun canMerge(item: DragItem, page: Int, col: Int, row: Int): Boolean {
            if (item.isFolder || item.isWidget) return false // folders and widgets do not go into folders
            val layout = homeLayout ?: return false
            val key = item.key
            val occupant = layout.itemsOnPage(page).firstOrNull { it.covers(page, col, row) } ?: return false
            if (occupant.isWidget || occupant.key == key) return false
            val inside = occupant.members
            if (inside != null && (inside.contains(key) || inside.size >= HomeLayout.MAX_FOLDER_APPS)) return false
            return true
        }

        override fun onDropOnCell(item: DragItem, page: Int, col: Int, row: Int, merge: Boolean) {
            val layout = homeLayout ?: return
            val key = item.key
            if (item.isWidget) {
                // A widget was dropped: its top-left cell is (col, row); apps under it step aside.
                if (!layout.moveWidget(key, page, col, row)) {
                    toast("No room for the widget there")
                    return
                }
                HomeStore.save(this@LauncherActivity, layout)
                return
            }
            val wasOnHome = layout.contains(key)
            val joinFolder = merge && !item.isFolder
            val done = if (joinFolder) layout.mergeInto(key, page, col, row) else layout.drop(key, page, col, row)
            if (!done) {
                toast(
                    when {
                        layout.widgetAt(page, col, row) != null -> "A widget is on this spot"
                        joinFolder -> "This folder is full"
                        page == HomeLayout.DOCK_PAGE -> "The dock is full"
                        else -> "This page is full"
                    }
                )
                return
            }
            HomeStore.save(this@LauncherActivity, layout)
            if (!wasOnHome && !joinFolder) toast("Added to Home screen")
        }

        override fun onDropOnAction(item: DragItem, action: DragAction) {
            if (item.isWidget) {
                if (action == DragAction.REMOVE) removeWidget(item.key)
                return
            }
            if (item.isFolder) {
                if (action == DragAction.REMOVE) confirmRemoveFolder(item.key)
                return
            }
            val app = item.app ?: return
            when (action) {
                DragAction.REMOVE -> removeFromHome(app)
                DragAction.APP_INFO -> openAppInfo(app)
                DragAction.UNINSTALL -> uninstall(app)
            }
        }

        override fun onDragEnded() {
            refreshHome()
        }
    }

    /** What the resize frame needs from the home screen: where the widget is, whether a new size fits, applying it. */
    private val resizeHost = object : ResizeHost {
        override fun currentPage(): CellLayout? = pager.getChildAt(pager.page) as? CellLayout

        override fun currentPageIndex(): Int = pager.page

        override fun widgetCells(key: String): IntArray? {
            val item = homeLayout?.find(key) ?: return null
            if (!item.isWidget) return null
            return intArrayOf(item.page, item.col, item.row, item.spanX, item.spanY)
        }

        override fun canResize(key: String, col: Int, row: Int, spanX: Int, spanY: Int): Boolean =
            homeLayout?.canResizeWidget(key, col, row, spanX, spanY) == true

        override fun onResized(key: String, col: Int, row: Int, spanX: Int, spanY: Int) {
            val layout = homeLayout ?: return
            if (!layout.resizeWidget(key, col, row, spanX, spanY)) {
                toast("No room to resize the widget there")
                resizeLayer.onHomeRebuilt() // put the frame back on the widget as it really is
                return
            }
            HomeStore.save(this@LauncherActivity, layout)
            refreshHome() // the widget view is kept; it only gets its new size (and tells the widget)
        }

        override fun onResizeBlocked() {
            toast("No room to resize the widget there")
        }
    }

    private val reloadApps = Runnable { loadApps() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Installs / updates send several broadcasts in a row - reload once.
            handler.removeCallbacks(reloadApps)
            handler.postDelayed(reloadApps, 600)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Backup.reloadHome = false
        HomeStore.loadConfig(this) // grid size, icon size, hidden apps (Step 9a); before any page is built
        // Step 9b: the Home screen turns with the phone only when Home settings say so.
        requestedOrientation = if (HomeStore.landscape) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Look.load(this) // dark / light panels (Step 8c)
        IconPack.load(this) // Step 12a: the launcher's own system-app icons
        IconTheme.load(this) // themed icons (Step 8d); after Look, because the plate follows dark / light
        makeWindowEdgeToEdge()
        widgetHost = WidgetHost(this)

        root = LauncherRoot(this)
        setContentView(root)
        buildHome()
        buildAppsScreen()
        buildFolderLayer()
        buildEditPanel()
        buildWidgetPicker()
        buildEdgePanel()
        buildQuickPanel()
        buildNotifPage()
        buildResizeLayer()
        buildDragLayer()

        root.setOnApplyWindowInsetsListener { _, insets ->
            readInsets(insets)
            insets
        }

        // Back must never close the launcher (Android 13+ uses a callback, older uses onBackPressed).
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { goHome() }
            )
        }

        registerPackageReceiver()
        loadApps()
        askToBeDefaultOnce()
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening() // widgets on the pages update live while we are visible
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        if (Backup.reloadHome || HomeStore.configChanged(this)) { // Home settings changed something / a backup was restored: build the screen again
            recreate()
            return
        }
        defaultChip.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
        if (LockScreen.isOn(this)) LockScreen.sync(this) // keeps the lock screen service running
        updateWallpaperOffset(pager.scrollFraction())
        if (IconTheme.refresh(this)) loadApps() // the wallpaper changed: the themed icons follow its colour
    }

    override fun onPause() {
        dragLayer.dismiss()
        folderLayer.close(animated = false)
        widgetPicker.close(animated = false)
        edgePanel.close(animated = false)
        quickPanel.close(animated = false)
        notifPage.close(animated = false)
        resizeLayer.close(animated = false)
        exitEditMode(animated = false)
        isForeground = false
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(reloadApps)
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // already unregistered
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we are already open (or coming back from another app).
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            goHome()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    /** Back from the system's "allow this widget?" screen or from a widget's own setup screen. */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_WIDGET_BIND -> if (resultCode == RESULT_OK) afterWidgetBound() else cancelPendingWidget()
            REQ_WIDGET_CONFIGURE -> if (resultCode == RESULT_OK) finishPendingWidget() else cancelPendingWidget()
            REQ_LOCK_ADMIN -> if (LockAdmin.isActive(this)) {
                setPref(KEY_LOCK_TAP, true)
                toast("Double-tap an empty spot to lock")
            }
            // REQ_WIDGET_RECONFIGURE: nothing to do, the widget's own app updates the widget and the system redraws it here.
        }
    }

    /**
     * Home / back: closes the quick menu or a drag first, then resize mode, then the widget picker, then an open folder, then the Apps screen (and its search + keyboard);
     * with nothing open it returns to the first home page.
     * If we are not on screen (Home pressed inside another app) it snaps instead of animating unseen.
     */
    private fun goHome() {
        if (dragLayer.dismiss()) return // an open quick menu / a drag goes first
        if (quickPanel.isOpen) { // then the Quick panel (Step 16a)
            quickPanel.back(animated = isForeground)
            return
        }
        if (notifPage.isOpen) { // then the Notifications page (Step 17a)
            notifPage.close(animated = isForeground)
            return
        }
        if (resizeLayer.isActive) { // then resize mode (the frame around a widget)
            resizeLayer.close(animated = isForeground)
            return
        }
        if (edgePanel.isOpen) { // then the Edge panel
            edgePanel.close(animated = isForeground)
            return
        }
        if (widgetPicker.isOpen) { // then the widget picker
            widgetPicker.close(animated = isForeground)
            return
        }
        if (editMode) { // then the edit screen
            exitEditMode(animated = isForeground)
            return
        }
        if (folderLayer.isOpen) { // then an open folder
            folderLayer.close(animated = isForeground)
            return
        }
        if (apps.progress > 0f) {
            apps.close(animated = isForeground)
        } else {
            pager.setPage(0, animated = isForeground)
        }
    }

    // ---------------------------------------------------------------- window

    @Suppress("DEPRECATION")
    private fun makeWindowEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    @Suppress("DEPRECATION")
    private fun readInsets(insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            insetTop = bars.top
            insetBottom = bars.bottom
            insetLeft = bars.left
            insetRight = bars.right
            imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        } else {
            insetLeft = insets.systemWindowInsetLeft
            insetRight = insets.systemWindowInsetRight
            insetTop = insets.systemWindowInsetTop
            insetBottom = insets.stableInsetBottom
            imeBottom = insets.systemWindowInsetBottom
        }
        placeHome()
    }

    // ---------------------------------------------------------------- layout

    private fun buildHome() {
        val screenWidth = resources.displayMetrics.widthPixels
        val dockWidth = minOf(screenWidth - dp(28), dp(560))

        // Everything that belongs to the home screen lives in one layer, so the Apps screen
        // can fade and shrink it as a whole while it opens.
        homeLayer = FrameLayout(this)
        root.addView(
            homeLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // The home pages fill the space above the dots; the dock and dots are added after, so they draw on top.
        pager = HomePager(this)
        homeLayer.addView(
            pager,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        pager.onPageChanged = { page, count ->
            dots.setPages(count, page)
            updateWallpaperOffset(pager.scrollFraction())
        }
        pager.onScrolled = { fraction -> updateWallpaperOffset(fraction) }
        pager.onEmptyLongPress = { x, y -> onEmptyLongPress(x, y) }
        pager.onDoubleTap = { onHomeDoubleTap() }
        // Page height decides the icon size, so rebuild once the pages know how tall they really are.
        pager.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop && homeLayout != null) pager.post { refreshHome() }
        }

        dock = DockView(this)
        homeLayer.addView(
            dock,
            FrameLayout.LayoutParams(
                dockWidth,
                dp(DockView.HEIGHT_DP),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )

        dots = PageDots(this)
        dots.setPages(1, 0)
        homeLayer.addView(
            dots,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )

        searchBar = HomeSearchBar(this)
        searchBar.setGradient(getSharedPreferences("launcher", MODE_PRIVATE).getInt(KEY_SEARCH_GRADIENT, 0))
        searchBar.visibility = if (showSearchBar()) View.VISIBLE else View.GONE
        searchBar.setOnClickListener {
            if (apps.progress == 0f && !folderLayer.isOpen) apps.openWithSearch()
        }
        homeLayer.addView(
            searchBar,
            FrameLayout.LayoutParams(dockWidth, dp(HomeSearchBar.HEIGHT_DP), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )

        defaultChip = TextView(this).apply {
            text = "Set as default home app"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = roundRect(Color.argb(150, 0, 0, 0), dpf(20f))
            visibility = View.GONE
            setOnClickListener { requestDefaultHome() }
        }
        homeLayer.addView(
            defaultChip,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )

        applyDockStyle()
        placeHome()
    }

    /** The Apps screen sits on top of the home layer and starts hidden. */
    private fun buildAppsScreen() {
        apps = AppsScreen(this) { view, intent -> launch(view, intent) }
        root.apps = apps
        root.addView(
            apps,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        apps.onProgress = { p -> applyDrawerProgress(p) }
        apps.onAppLongPress = { view, app -> beginDrag(DragOrigin.DRAWER, view, app) }
        apps.setInsets(insetTop, maxOf(insetBottom, imeBottom))
    }

    /** The open-folder card: above the Apps screen, below the drag layer. */
    private fun buildFolderLayer() {
        folderLayer = FolderLayer(this) { view, intent -> launch(view, intent) }
        folderLayer.onAppLongPress = { view, app -> beginDrag(DragOrigin.FOLDER, view, app) }
        root.folders = folderLayer
        folderLayer.onOpenChanged = { animateOverlayBlur() }
        root.addView(
            folderLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        folderLayer.setInsets(insetTop, maxOf(insetBottom, imeBottom))
    }

    /** The widget picker sheet: above the folder card, below the drag layer (which carries menus). */
    private fun buildWidgetPicker() {
        widgetPicker = WidgetPicker(this) { entry -> addWidgetFromPicker(entry) }
        root.picker = widgetPicker
        widgetPicker.onOpenChanged = { animateOverlayBlur() }
        root.editMode = { editMode }
        root.pinchEnabled = {
            !editMode && apps.progress == 0f && !folderLayer.isOpen && !widgetPicker.isOpen &&
                !resizeLayer.isActive && !dragLayer.isTouchActive && !edgePanel.isOpen && !quickPanel.isOpen && !notifPage.isOpen
        }
        root.onPinchIn = { enterEditMode() }
        root.swipeDownEnabled = {
            notifSwipeOn() && apps.progress == 0f && !folderLayer.isOpen && !widgetPicker.isOpen && !resizeLayer.isActive &&
                !edgePanel.isOpen && !quickPanel.isOpen && !notifPage.isOpen
        }
        root.onSwipeDown = { openSwipeDownPanel() }
        root.addView(
            widgetPicker,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        widgetPicker.setInsets(insetTop, maxOf(insetBottom, imeBottom))
    }

    /** The resize frame of a widget: above the pages and the picker, below the drag layer (which carries the menu). */
    private fun buildResizeLayer() {
        resizeLayer = ResizeLayer(this, resizeHost)
        root.resize = resizeLayer
        root.addView(
            resizeLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Topmost layer: carries the lifted icon, the quick menu and the drop zones above everything. */
    private fun buildDragLayer() {
        dragLayer = DragLayer(this, dragHost)
        root.drag = dragLayer
        root.addView(
            dragLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        dragLayer.setInsets(insetTop, maxOf(insetBottom, imeBottom))
    }

    /** Home screen fades out and shrinks slightly while the Apps screen comes in. */
    private fun applyDrawerProgress(p: Float) {
        homeLayer.visibility = if (p >= 1f) View.INVISIBLE else View.VISIBLE
        homeLayer.alpha = 1f - (p * 1.4f).coerceAtMost(1f)
        val scale = 1f - 0.06f * p
        homeLayer.scaleX = scale
        homeLayer.scaleY = scale
        applyBlur()
        // A light Apps screen needs dark status / navigation bar icons.
        setDarkBarIcons(!Look.dark && p > 0.5f)
    }

    private var darkBarIcons = false

    /** Dark (true) or light (false) icons in the status and navigation bar. */
    @Suppress("DEPRECATION")
    private fun setDarkBarIcons(dark: Boolean) {
        if (dark == darkBarIcons) return
        darkBarIcons = dark
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

    // ---------------------------------------------------------------- blur (Step 8b)

    private var overlayBlur = 0f
    private var blurAnimator: ValueAnimator? = null
    private var appliedBlur = -1

    private fun blurOn(): Boolean =
        getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_BLUR, true)

    /**
     * Android 12+: the wallpaper behind the launcher is blurred while the Apps screen is open (following your finger),
     * and while a folder or the widget picker is open. Off when the person switched it off or the phone has window blur
     * disabled (battery saver, some low-end phones).
     */
    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < 31 || !::apps.isInitialized) return
        applyContentBlur()
        val level = maxOf(apps.progress, overlayBlur)
        val allowed = blurOn() && windowManager.isCrossWindowBlurEnabled
        val radius = if (allowed) (level * dp(BLUR_DP)).toInt() else 0
        if (radius == appliedBlur) return
        appliedBlur = radius
        try {
            window.setBackgroundBlurRadius(radius)
        } catch (e: Exception) {
            // the phone can not blur: nothing to do
        }
    }

    /**
     * Step 18: while a panel, folder or the widget picker is open, the icons, widgets and dock of the Home screen behind it are
     * blurred too (the launcher's own picture), not only the wallpaper. This works on every Android 12+ phone, also where the
     * phone refuses to blur the wallpaper.
     */
    private var appliedContentBlur = -1f

    private fun applyContentBlur() {
        if (Build.VERSION.SDK_INT < 31 || !::homeLayer.isInitialized) return
        val radius = if (blurOn()) overlayBlur * dp(CONTENT_BLUR_DP) else 0f
        if (abs(radius - appliedContentBlur) < 0.5f) return
        appliedContentBlur = radius
        try {
            homeLayer.setRenderEffect(
                if (radius < 0.5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            )
        } catch (e: Exception) {
            // the phone can not blur views: nothing to do
        }
    }

    private fun animateOverlayBlur() {
        val open = folderLayer.isOpen || (::widgetPicker.isInitialized && widgetPicker.isOpen) ||
            (::quickPanel.isInitialized && quickPanel.wantsBlur) ||
            (::notifPage.isInitialized && notifPage.wantsBlur)
        blurAnimator?.cancel()
        val anim = ValueAnimator.ofFloat(overlayBlur, if (open) 1f else 0f)
        anim.duration = 220
        anim.addUpdateListener {
            overlayBlur = it.animatedValue as Float
            applyBlur()
        }
        blurAnimator = anim
        anim.start()
    }

    /** Keeps dock, dots and chip clear of the navigation bar / gesture area, and the Apps screen clear of the keyboard. */
    private fun placeHome() {
        val dockBottom = maxOf(insetBottom, dp(6)) + dp(8)
        val dotsBottom = dockBottom + dp(DockView.HEIGHT_DP) + dp(14)
        val searchShown = showSearchBar()
        val searchBottom = dotsBottom + dp(24)
        val searchSpace = if (searchShown) dp(HomeSearchBar.HEIGHT_DP) + dp(10) else 0
        val chipBottom = dotsBottom + dp(26) + searchSpace
        val pagesTop = maxOf(insetTop, dp(24)) + dp(12)
        val pagesBottom = dotsBottom + dp(10) + dp(14) + searchSpace
        searchBar.visibility = if (searchShown) View.VISIBLE else View.GONE
        (searchBar.layoutParams as FrameLayout.LayoutParams).bottomMargin = searchBottom
        homeLayer.setPadding(insetLeft, 0, insetRight, 0) // landscape: keep clear of a side navigation bar / the notch

        (pager.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = pagesTop
            bottomMargin = pagesBottom
        }
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = dockBottom
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = dotsBottom
        (defaultChip.layoutParams as FrameLayout.LayoutParams).bottomMargin = chipBottom
        homeLayer.requestLayout()

        if (::apps.isInitialized) apps.setInsets(insetTop, maxOf(insetBottom, imeBottom))
        if (::folderLayer.isInitialized) folderLayer.setInsets(insetTop, maxOf(insetBottom, imeBottom))
        if (::quickPanel.isInitialized) quickPanel.setInsets(insetTop, insetBottom)
        if (::notifPage.isInitialized) notifPage.setInsets(insetTop, insetBottom)
        if (::widgetPicker.isInitialized) widgetPicker.setInsets(insetTop, maxOf(insetBottom, imeBottom))
        if (::editPanel.isInitialized) {
            (editPanel.layoutParams as FrameLayout.LayoutParams).bottomMargin = maxOf(insetBottom, dp(12)) + dp(28)
            editPanel.requestLayout()
            (dockSheet.layoutParams as FrameLayout.LayoutParams).bottomMargin = maxOf(insetBottom, dp(12)) + dp(28) + dp(64)
            dockSheet.requestLayout()
        }
        if (::dragLayer.isInitialized) dragLayer.setInsets(insetTop, maxOf(insetBottom, imeBottom))
    }

    // ---------------------------------------------------------------- apps

    private fun loadApps() {
        AppRepository.load(this, dp(AppsScreen.ICON_LOAD_DP)) { list ->
            if (!isDestroyed) onAppsLoaded(list)
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
    }

    // ---------------------------------------------------------------- home pages

    private fun onAppsLoaded(list: List<AppEntry>) {
        // Step 10a: install / update broadcasts often change nothing we show; then the pages are not rebuilt (no flicker).
        if (homeLayout != null && AppRepository.sameApps(allApps, list)) return
        allApps = list
        val hiddenKeys = HomeStore.hidden
        apps.setApps(if (hiddenKeys.isEmpty()) list else list.filter { !hiddenKeys.contains(it.component.flattenToString()) })
        syncHomeLayout(list)
    }

    /**
     * Brings the saved layout in line with the installed apps and shows it.
     * First run: the home screen gets a starter set. Later: apps that are really gone are forgotten,
     * and an app whose launcher activity was renamed keeps its cell.
     */
    private fun syncHomeLayout(list: List<AppEntry>) {
        // A failed or empty load must never wipe the saved layout.
        if (list.isEmpty()) return

        val keys = HashSet<String>()
        val firstOfPackage = HashMap<String, AppEntry>()
        for (app in list) {
            keys.add(app.component.flattenToString())
            if (!firstOfPackage.containsKey(app.packageName)) firstOfPackage[app.packageName] = app
        }

        var layout = homeLayout ?: HomeStore.load(this)
        var changed = false
        if (layout == null) {
            layout = HomeLayout(HomeStore.COLS, HomeStore.ROWS)
            // First run: the dock gets the phone's default apps, the pages get the starter set (without those).
            seedDock(layout, list)
            val dockPackages = layout.itemsOnPage(HomeLayout.DOCK_PAGE)
                .flatMap { it.members ?: listOf(it.key) }
                .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
                .toSet()
            for (key in DefaultHome.pick(this, list, dockPackages)) layout.add(key)
            changed = true
        } else {
            for (appKey in layout.appKeys()) { // hidden apps leave the Home screen too (Step 9a)
                if (HomeStore.hidden.contains(appKey)) {
                    layout.remove(appKey)
                    changed = true
                }
            }
            for (appKey in layout.appKeys()) { // loose apps and the ones inside folders
                if (keys.contains(appKey)) continue
                val pkg = ComponentName.unflattenFromString(appKey)?.packageName
                val renamed = if (pkg != null) firstOfPackage[pkg] else null
                if (renamed != null) {
                    layout.replaceKey(appKey, renamed.component.flattenToString())
                    changed = true
                } else if (pkg == null || !isInstalled(pkg)) {
                    layout.remove(appKey)
                    changed = true
                }
            }
            // A widget whose app was uninstalled is forgotten. A widget the system merely does not answer for right now
            // (its app is being updated) stays: it only goes when the app is really gone.
            for (widget in layout.widgets()) {
                val provider = ComponentName.unflattenFromString(widget.provider) ?: continue
                if (widgetHost.info(widget.widgetId) == null && !isInstalled(provider.packageName)) {
                    if (widget.widgetId >= 0) widgetHost.deleteId(widget.widgetId)
                    widgetFrames.remove(widget.widgetId)
                    layout.remove(widget.key)
                    changed = true
                }
            }
            // A layout saved before the dock was editable (Steps 3-5b): the dock gets the default apps once.
            if (!layout.hasDock && !getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_DOCK_SEEDED, false)) {
                seedDock(layout, list)
                changed = true
            }
        }
        homeLayout = layout
        if (changed) {
            HomeStore.save(this, layout)
            folderLayer.close(animated = false) // what it shows may be out of date now
        }
        refreshHome()
    }

    /** Puts the phone's default Phone / Messages / Browser / Camera / Settings apps into the dock (once, ever). */
    private fun seedDock(layout: HomeLayout, list: List<AppEntry>) {
        for ((slot, key) in Favorites.defaultKeys(this, list).withIndex()) {
            layout.drop(key, HomeLayout.DOCK_PAGE, slot, 0) // an app already on a home page moves here
        }
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putBoolean(KEY_DOCK_SEEDED, true).apply()
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Rebuilds every home page from the layout. Keeps the page you are on. */
    private fun refreshHome() {
        val layout = homeLayout ?: return
        val byKey = HashMap<String, AppEntry>()
        for (app in allApps) byKey[app.component.flattenToString()] = app

        val iconDp = homeIconDp()
        val pages = ArrayList<View>()
        for (page in 0 until layout.pageCount) {
            val cells = newHomePage()
            for (item in layout.itemsOnPage(page)) {
                if (item.isWidget) {
                    val frame = widgetFrameFor(item)
                    if (frame != null) {
                        cells.addView(
                            frame,
                            CellLayout.CellParams(item.col, item.row, item.spanX, item.spanY, widget = true)
                        )
                    }
                } else if (item.members != null) {
                    cells.addView(makeFolderIcon(item, byKey, iconDp), CellLayout.CellParams(item.col, item.row))
                } else {
                    val app = byKey[item.key] ?: continue
                    cells.addView(makeHomeIcon(app, iconDp), CellLayout.CellParams(item.col, item.row))
                }
            }
            pages.add(cells)
        }
        pager.setPages(pages, keepPage = true)
        refreshDock(layout, byKey, iconDp = DockView.ICON_DP)

        // Widget views of widgets that are gone from the layout are let go.
        val liveIds = HashSet<Int>()
        for (widget in layout.widgets()) liveIds.add(widget.widgetId)
        for (id in widgetFrames.keys.toList()) {
            if (!liveIds.contains(id)) widgetFrames.remove(id)
        }

        // Resize mode follows its widget onto the rebuilt page (or ends when the widget is gone).
        if (::resizeLayer.isInitialized) resizeLayer.onHomeRebuilt()
    }

    // ---------------------------------------------------------------- widgets (Step 6a)

    /** The live view of a widget on the home screen: the one made before when there is one, else a new one. Null when the system can not make it. */
    private fun widgetFrameFor(item: HomeItem): WidgetFrame? {
        val id = item.widgetId
        if (id < 0) return null
        var frame = widgetFrames[id]
        if (frame == null) {
            frame = widgetHost.createFrame(id) ?: return null
            val key = item.key
            frame.onLongPress = { view -> beginWidgetDrag(key, view) }
            widgetFrames[id] = frame
        }
        (frame.parent as? ViewGroup)?.removeView(frame) // it moves from the old page to the new one
        return frame
    }

    /** Long-press on an empty spot of a home page: a small menu with Widgets (Wallpaper and Settings come with later steps). */
    private fun onEmptyLongPress(rawX: Float, rawY: Float) {
        if (apps.progress > 0f || folderLayer.isOpen || widgetPicker.isOpen || resizeLayer.isActive) return
        val reach = dp(24)
        val spot = Rect((rawX - reach).toInt(), (rawY - reach).toInt(), (rawX + reach).toInt(), (rawY + reach).toInt())
        val searchLabel = if (showSearchBar()) "Hide search bar" else "Show search bar"
        dragLayer.openMenu(
            spot,
            listOf(
                MenuEntry("Edit Home screen", null) { enterEditMode() },
                MenuEntry("Widgets", null) { openWidgetPicker() },
                MenuEntry("Wallpaper", null) {
                    startActivity(Intent(this, HomeSettingsActivity::class.java).putExtra(HomeSettingsActivity.EXTRA_PAGE, HomeSettingsActivity.PAGE_WALLPAPER))
                },
                MenuEntry("Settings", null) { startActivity(Intent(this, HomeSettingsActivity::class.java)) },
                MenuEntry("Panels: " + Look.modeName(), null) {
                    Look.cycle(this)
                    recreate() // every panel is built again in the new colours
                },
                MenuEntry("Icon style: " + IconPack.modeName(), null) {
                    IconPack.toggle(this)
                    loadApps() // every icon is drawn again
                },
                MenuEntry("Themed icons: " + IconTheme.modeName(), null) {
                    IconTheme.cycle(this)
                    loadApps() // every icon is drawn again in the new colours
                },
                MenuEntry(if (blurOn()) "Blur: On" else "Blur: Off", null) {
                    setPref(KEY_BLUR, !blurOn())
                    appliedBlur = -1
                    applyBlur()
                },
                MenuEntry(if (wallpaperScrollOn()) "Wallpaper scroll: On" else "Wallpaper scroll: Off", null) {
                    setPref(KEY_WALLPAPER_SCROLL, !wallpaperScrollOn())
                    updateWallpaperOffset(pager.scrollFraction())
                },
                MenuEntry(searchLabel, null) { setShowSearchBar(!showSearchBar()) },
                MenuEntry(if (notifSwipeOn()) "Swipe down on Home: On" else "Swipe down on Home: Off", null) {
                    setPref(KEY_NOTIF_SWIPE, !notifSwipeOn())
                },
                MenuEntry(if (lockTapOn()) "Double-tap to lock: On" else "Double-tap to lock: Off", null) {
                    toggleDoubleTapLock()
                }
            )
        )
    }

    // ---------------------------------------------------------------- edit screen (Step 7c)

    /** The bar at the bottom of the edit screen (Widgets / Search bar / Done) and the layer that catches taps on the zoomed-out home. */
    private fun buildEditPanel() {
        editScrim = View(this)
        editScrim.visibility = View.GONE
        editScrim.setOnClickListener { exitEditMode(animated = true) }
        root.addView(editScrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        editPanel = android.widget.LinearLayout(this)
        editPanel.orientation = android.widget.LinearLayout.HORIZONTAL
        editPanel.gravity = Gravity.CENTER
        editPanel.visibility = View.GONE
        editSearchButton = editButton("") {
            setShowSearchBar(!showSearchBar())
            refreshEditLabels()
        }
        dockSheet = DockStyleSheet(this) { color, opacity, gradient ->
            getSharedPreferences("launcher", MODE_PRIVATE).edit()
                .putInt(KEY_DOCK_COLOR, color).putInt(KEY_DOCK_OPACITY, opacity).putInt(KEY_DOCK_GRADIENT, gradient).apply()
            applyDockStyle()
        }
        dockSheet.visibility = View.GONE
        root.addView(
            dockSheet,
            FrameLayout.LayoutParams(
                minOf(resources.displayMetrics.widthPixels - dp(32), dp(360)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
        editPanel.addView(editButton("Widgets") { openWidgetPicker() })
        editPanel.addView(editButton("Dock") { toggleDockSheet() })
        editPanel.addView(editSearchButton)
        editPanel.addView(editButton("Done") { exitEditMode(animated = true) })
        root.addView(
            editPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
    }

    private fun editButton(label: String, onClick: () -> Unit): TextView {
        val button = TextView(this)
        button.text = label
        button.setTextColor(Color.WHITE)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        button.gravity = Gravity.CENTER
        button.setPadding(dp(16), dp(11), dp(16), dp(11))
        button.background = roundRect(Color.argb(170, 18, 20, 28), dpf(24f))
        button.isClickable = true
        button.setOnClickListener { onClick() }
        val lp = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(4), 0, dp(4), 0)
        button.layoutParams = lp
        return button
    }

    private fun toggleDockSheet() {
        if (dockSheet.visibility == View.VISIBLE) {
            dockSheet.visibility = View.GONE
            return
        }
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        dockSheet.show(
            prefs.getInt(KEY_DOCK_COLOR, Color.WHITE),
            prefs.getInt(KEY_DOCK_OPACITY, DockStyleSheet.DEFAULT_OPACITY),
            prefs.getInt(KEY_DOCK_GRADIENT, 0)
        )
        dockSheet.visibility = View.VISIBLE
    }

    /** Dock colour + opacity from the saved look. */
    private fun applyDockStyle() {
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        dock.setStyle(
            prefs.getInt(KEY_DOCK_COLOR, Color.WHITE),
            prefs.getInt(KEY_DOCK_OPACITY, DockStyleSheet.DEFAULT_OPACITY),
            prefs.getInt(KEY_DOCK_GRADIENT, 0)
        )
    }

    // ---------------------------------------------------------------- wallpaper scroll (Step 8a)

    private fun wallpaperScrollOn(): Boolean =
        getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_WALLPAPER_SCROLL, true)

    /** The wallpaper slides sideways a little while the pages slide ([fraction] 0..1). Off = it stays in the middle. */
    private fun updateWallpaperOffset(fraction: Float) {
        val token = root.windowToken ?: return
        try {
            val manager = WallpaperManager.getInstance(this)
            val steps = if (pager.pageCount > 1) 1f / (pager.pageCount - 1) else 1f
            manager.setWallpaperOffsetSteps(steps, 1f)
            manager.setWallpaperOffsets(token, if (wallpaperScrollOn()) fraction else 0.5f, 0.5f)
        } catch (e: Exception) {
            // a phone without a wallpaper service: nothing to move
        }
    }

    private fun refreshEditLabels() {
        editSearchButton.text = if (showSearchBar()) "Search: On" else "Search: Off"
    }

    /** Pinch in (or long-press > Edit Home screen): the home screen shrinks back and the edit bar slides up. */
    private fun enterEditMode() {
        if (editMode || apps.progress > 0f || folderLayer.isOpen || widgetPicker.isOpen || resizeLayer.isActive) return
        dragLayer.dismiss()
        editMode = true
        refreshEditLabels()
        editScrim.visibility = View.VISIBLE
        editPanel.visibility = View.VISIBLE
        editPanel.alpha = 0f
        editPanel.translationY = dp(40).toFloat()
        editPanel.animate().alpha(1f).translationY(0f).setDuration(240).start()
        homeLayer.animate().scaleX(0.8f).scaleY(0.8f).translationY(-dpf(24f)).setDuration(260).start()
    }

    private fun exitEditMode(animated: Boolean) {
        if (!editMode) return
        editMode = false
        dockSheet.visibility = View.GONE
        editScrim.visibility = View.GONE
        homeLayer.animate().cancel()
        editPanel.animate().cancel()
        if (animated) {
            homeLayer.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(220).start()
            editPanel.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(180)
                .withEndAction { if (!editMode) editPanel.visibility = View.GONE }.start()
        } else {
            homeLayer.scaleX = 1f
            homeLayer.scaleY = 1f
            homeLayer.translationY = 0f
            editPanel.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------- gestures (Step 7b)

    private fun notifSwipeOn(): Boolean =
        getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_NOTIF_SWIPE, true)

    private fun lockTapOn(): Boolean =
        getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_LOCK_TAP, false)

    private fun setPref(key: String, value: Boolean) {
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    /** Settings > Gestures > "Swipe down opens": notifications, the Quick panel, or split (left = notifications, right = Quick panel). */
    private fun swipeDownMode(): Int =
        getSharedPreferences("launcher", MODE_PRIVATE).getInt(KEY_SWIPE_MODE, SWIPE_SPLIT).coerceIn(SWIPE_NOTIF, SWIPE_SPLIT)

    private fun openSwipeDownPanel() {
        val quick = when (swipeDownMode()) {
            SWIPE_QUICK -> true
            SWIPE_SPLIT -> CardLogic.splitOpensQuick(root.downFraction, getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_QP_LEFT, false))
            else -> false
        }
        if (quick) openQuickPanel() else openNotificationPanel()
    }

    // ---------------------------------------------------------------- quick panel (Step 16a)

    /** The Quick panel: above the Edge panel, below the resize frame and the drag layer. */
    private fun buildQuickPanel() {
        val system = QuickSystem(this) { toast(it) }
        quickPanel = QuickPanel(this, system) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                toast("Can't open Settings")
            }
        }
        quickPanel.onOpenChanged = { animateOverlayBlur() }
        root.quick = quickPanel
        root.addView(
            quickPanel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        quickPanel.setInsets(insetTop, insetBottom)
    }

    private fun openQuickPanel() {
        if (editMode || apps.progress > 0f || folderLayer.isOpen || widgetPicker.isOpen || resizeLayer.isActive ||
            dragLayer.isTouchActive || edgePanel.isOpen || quickPanel.isOpen || notifPage.isOpen
        ) return
        dragLayer.dismiss()
        quickPanel.open()
    }

    // ---------------------------------------------------------------- notifications page (Step 17a)

    /** The Notifications page: same layer level as the Quick panel (only one of them is open at a time). */
    private fun buildNotifPage() {
        notifPage = NotificationPage(this) { openNotificationSettings() }
        notifPage.onOpenChanged = { animateOverlayBlur() }
        notifPage.onSwitchPage = { switchPanels(toNotifs = false) }
        quickPanel.onSwitchPage = { switchPanels(toNotifs = true) }
        root.notifPage = notifPage
        root.addView(
            notifPage,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        notifPage.setInsets(insetTop, insetBottom)
    }

    /** Step 17c: a sideways swipe moves between the Quick panel and the Notifications page, without going back to Home in between. */
    private fun switchPanels(toNotifs: Boolean) {
        if (toNotifs) {
            if (!quickPanel.isOpen || !ownNotifPage()) return
            quickPanel.close(animated = false)
            notifPage.open()
        } else {
            if (!notifPage.isOpen) return
            notifPage.close(animated = false)
            quickPanel.open()
        }
    }

    /** Settings > Quick panel > Notification panel: the launcher's own page (default) or the phone's. */
    private fun ownNotifPage(): Boolean =
        getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(NotificationPage.PREF_KEY, true)

    private fun openNotifPage() {
        if (editMode || apps.progress > 0f || folderLayer.isOpen || widgetPicker.isOpen || resizeLayer.isActive ||
            dragLayer.isTouchActive || edgePanel.isOpen || quickPanel.isOpen || notifPage.isOpen
        ) return
        dragLayer.dismiss()
        try {
            notifPage.open()
        } catch (e: Exception) {
            // Safety net (Step 18): never let a drawing problem in the page take the whole launcher down.
            try {
                notifPage.close(animated = false)
            } catch (ignored: Exception) {
            }
            toast("Could not open the notifications page")
        }
    }

    /** "Notification settings" on the page: the phone's notification settings (the first screen the phone has). */
    private fun openNotificationSettings() {
        val tries = listOf(
            Intent("android.settings.NOTIFICATION_SETTINGS"),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in tries) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
        toast("Can't open Settings")
    }

    /** Swipe down on the Home screen: pull the notification panel open (the phone decides how it looks). */
    private fun openNotificationPanel() {
        if (ownNotifPage()) {
            openNotifPage()
            return
        }
        try {
            val bar = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(bar)
        } catch (e: Exception) {
            toast("This phone does not let a launcher open the notification panel")
        }
    }

    /** Menu switch: turning it on first asks Android to allow "lock the screen" (once). */
    private fun toggleDoubleTapLock() {
        if (lockTapOn()) {
            setPref(KEY_LOCK_TAP, false)
            toast("Double-tap to lock is off")
            return
        }
        if (LockAdmin.isActive(this)) {
            setPref(KEY_LOCK_TAP, true)
            toast("Double-tap an empty spot to lock")
            return
        }
        try {
            startActivityForResult(LockAdmin.requestIntent(this), REQ_LOCK_ADMIN)
        } catch (e: Exception) {
            toast("Can't ask for this permission on this phone")
        }
    }

    /** Two quick taps on an empty spot of a home page. */
    private fun onHomeDoubleTap() {
        if (!lockTapOn() || apps.progress > 0f || folderLayer.isOpen || widgetPicker.isOpen || resizeLayer.isActive) return
        if (!LockAdmin.lock(this)) {
            setPref(KEY_LOCK_TAP, false)
            toast("Double-tap to lock needs the permission again")
        }
    }

    /** "Show search on Home screen": on by default. Step 9 puts this switch into Home settings too. */
    private fun showSearchBar(): Boolean =
        getSharedPreferences("launcher", MODE_PRIVATE).getBoolean(KEY_HOME_SEARCH, true)

    private fun setShowSearchBar(on: Boolean) {
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putBoolean(KEY_HOME_SEARCH, on).apply()
        placeHome() // pages grow / shrink; the pager rebuilds its icons by itself
    }

    /**
     * Long-press on a widget: it lifts and its menu opens (Resize when the widget allows it, Remove widget);
     * keep the finger down and move to drag it.
     */
    private fun beginWidgetDrag(key: String, view: View) {
        val item = homeLayout?.find(key) ?: return
        val entries = ArrayList<MenuEntry>()
        val limits = widgetLimits(item)
        if (limits != null && limits.canResize) entries.add(MenuEntry("Resize", null) { startResize(key) })
        val info = widgetHost.info(item.widgetId)
        if (info != null && canReconfigure(info)) {
            entries.add(MenuEntry("Widget settings", null) { openWidgetSettings(item.widgetId) })
        }
        entries.add(MenuEntry("Remove widget", null) { removeWidget(key) })
        dragLayer.begin(DragItem.ofWidget(key, item.spanX, item.spanY), view, root.lastRawX, root.lastRawY, entries)
    }

    /** True when the widget has a setup screen that may be opened again after the widget is on the home screen. */
    private fun canReconfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (info.provider?.packageName == packageName) return true // our own widgets (Step 6c2)
        return Build.VERSION.SDK_INT >= 28 &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    /** "Widget settings" in the widget's menu: the widget's own setup screen opens again. */
    private fun openWidgetSettings(id: Int) {
        if (id < 0 || !widgetHost.startConfigure(this, id, REQ_WIDGET_RECONFIGURE)) {
            toast("Can't open the widget's settings")
        }
    }

    /** Width and height of one cell of the page in front, in pixels (the real ones when the page is laid out). */
    private fun currentCellSize(): Pair<Int, Int> {
        val page = pager.getChildAt(pager.page) as? CellLayout
        if (page != null && page.width > 0 && page.height > 0) {
            val origin = page.cellBounds(0, 0)
            if (origin.width() > 0 && origin.height() > 0) return Pair(origin.width(), origin.height())
        }
        return Pair(cellWidthPx(), cellHeightPx())
    }

    /** How big the widget may be made, or null when the system does not know the widget right now. */
    private fun widgetLimits(item: HomeItem): ResizeLimits? {
        val info = widgetHost.info(item.widgetId) ?: return null
        val cell = currentCellSize()
        return WidgetResize.limitsFor(info, cell.first, cell.second, HomeStore.COLS, HomeStore.ROWS, item.spanX, item.spanY)
    }

    /** "Resize" in the widget's menu: the frame with handles appears around the widget. */
    private fun startResize(key: String) {
        val item = homeLayout?.find(key) ?: return
        if (!item.isWidget) return
        val limits = widgetLimits(item)
        if (limits == null || !limits.canResize) {
            toast("This widget can't be resized")
            return
        }
        resizeLayer.start(key, limits)
    }

    private fun removeWidget(key: String) {
        val layout = homeLayout ?: return
        val item = layout.find(key) ?: return
        val id = item.widgetId
        if (!layout.remove(key)) return
        if (id >= 0) {
            widgetHost.deleteId(id)
            widgetFrames.remove(id)
        }
        HomeStore.save(this, layout)
        refreshHome()
        toast("Widget removed")
    }

    /** Width and height of one home cell in pixels (same maths as the pages use). */
    private fun cellWidthPx(): Int = (resources.displayMetrics.widthPixels - dp(20)) / HomeStore.COLS

    private fun cellHeightPx(): Int {
        val pageHeight = if (pager.height > 0) pager.height else resources.displayMetrics.heightPixels - dp(220)
        return pageHeight / HomeStore.ROWS
    }

    /** Finds every widget on the phone (in the background, it can take a moment) and opens the picker. */
    private fun openWidgetPicker() {
        if (pickerLoading || widgetPicker.isOpen) return
        pickerLoading = true
        val iconPx = dp(42)
        val cellW = cellWidthPx()
        val cellH = cellHeightPx()
        val manager = widgetHost.manager
        Thread {
            val groups: List<WidgetGroup> = try {
                WidgetCatalog.load(this, manager, iconPx, cellW, cellH)
            } catch (e: Exception) {
                emptyList()
            }
            handler.post {
                pickerLoading = false
                if (!isDestroyed) widgetPicker.open(groups)
            }
        }.start()
    }

    /** A card in the picker was tapped: reserve an id, get the system's permission to bind it, let the widget set itself up, place it. */
    private fun addWidgetFromPicker(entry: WidgetEntry) {
        val layout = homeLayout ?: return
        if (pendingId >= 0) cancelPendingWidget() // an earlier attempt never finished
        val page = pager.page
        if (layout.findWidgetSpot(entry.spanX, entry.spanY, page) == null) {
            toast("No room for this widget on the Home screen")
            return
        }
        val id = widgetHost.allocateId()
        if (id < 0) {
            toast("Can't add this widget")
            return
        }
        pendingId = id
        pendingInfo = entry.info
        pendingSpanX = entry.spanX
        pendingSpanY = entry.spanY
        pendingPage = page

        val provider = entry.info.provider
        if (provider != null && widgetHost.bindIfAllowed(id, provider)) {
            afterWidgetBound()
            return
        }
        try {
            startActivityForResult(widgetHost.bindIntent(id, entry.info), REQ_WIDGET_BIND)
        } catch (e: Exception) {
            cancelPendingWidget()
            toast("Can't add this widget")
        }
    }

    /** The widget is bound to its app. Some widgets need a setup screen first. */
    private fun afterWidgetBound() {
        val id = pendingId
        val info = pendingInfo
        if (id < 0 || info == null) return
        if (info.configure != null && widgetHost.startConfigure(this, id, REQ_WIDGET_CONFIGURE)) return
        finishPendingWidget()
    }

    /** Everything the widget needed is done: put it on the home screen. */
    private fun finishPendingWidget() {
        val id = pendingId
        val info = pendingInfo
        pendingId = -1
        pendingInfo = null
        val layout = homeLayout
        if (id < 0) return
        if (layout == null || info == null) {
            widgetHost.deleteId(id)
            return
        }
        val provider = info.provider?.flattenToString().orEmpty()
        val placed = layout.addWidget(WIDGET_PREFIX + id, provider, pendingSpanX, pendingSpanY, pendingPage)
        if (placed == null) {
            widgetHost.deleteId(id)
            toast("No room for this widget on the Home screen")
            return
        }
        HomeStore.save(this, layout)
        refreshHome()
        pager.setPage(placed.page, animated = false)
        toast("Widget added")
    }

    /** The person said no (or the widget's setup was cancelled): give the id back. */
    private fun cancelPendingWidget() {
        if (pendingId >= 0) widgetHost.deleteId(pendingId)
        pendingId = -1
        pendingInfo = null
    }

    /** Rebuilds the dock from the layout: an icon or a folder tile in each used slot. */
    private fun refreshDock(layout: HomeLayout, byKey: Map<String, AppEntry>, iconDp: Int) {
        val views = HashMap<Int, View>()
        for (item in layout.itemsOnPage(HomeLayout.DOCK_PAGE)) {
            if (item.members != null) {
                views[item.col] = makeFolderIcon(item, byKey, iconDp, DragOrigin.DOCK, labeled = false)
            } else {
                val app = byKey[item.key] ?: continue
                views[item.col] = makeDockIcon(app, iconDp)
            }
        }
        dock.setViews(views)
    }

    /** True while the phone is turned sideways (only possible when Home settings allow landscape). */
    private fun isLandscape(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ---------------------------------------------------------------- edge panel (Step 9b)

    /** The Edge panel (above the widget picker, below the resize frame and the drag layer) and its handle on the Home screen. */
    private fun buildEdgePanel() {
        edgePanel = EdgePanel(this) { view, intent -> launch(view, intent) }
        root.edge = edgePanel
        root.addView(
            edgePanel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // The handle: a thin light bar on the right edge. Tap it, or drag it to the left, to open the panel.
        val frame = FrameLayout(this)
        val bar = View(this)
        bar.background = roundRect(Color.argb(150, 255, 255, 255), dpf(2f))
        val barParams = FrameLayout.LayoutParams(dp(4), dp(58), Gravity.END or Gravity.CENTER_VERTICAL)
        barParams.rightMargin = dp(3)
        frame.addView(bar, barParams)
        var downX = 0f
        var downY = 0f
        var opened = false
        frame.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    opened = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!opened && downX - ev.rawX > dp(14)) {
                        opened = true
                        openEdgePanel()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!opened && abs(ev.rawX - downX) < dp(8) && abs(ev.rawY - downY) < dp(8)) openEdgePanel()
                }
            }
            true
        }
        homeLayer.addView(frame, FrameLayout.LayoutParams(dp(28), dp(120), Gravity.END or Gravity.CENTER_VERTICAL))
        frame.visibility = if (HomeStore.edgeOn) View.VISIBLE else View.GONE
        edgeHandle = frame
    }

    /** Slides the Edge panel in with the apps chosen in Home settings (A-Z, hidden apps left out). */
    private fun openEdgePanel() {
        if (!HomeStore.edgeOn || editMode || apps.progress > 0f || folderLayer.isOpen || widgetPicker.isOpen ||
            resizeLayer.isActive || dragLayer.isTouchActive || edgePanel.isOpen
        ) return
        dragLayer.dismiss()
        val chosen = allApps.filter {
            val key = it.component.flattenToString()
            HomeStore.edgeApps.contains(key) && !HomeStore.hidden.contains(key)
        }
        edgePanel.open(AppRepository.sort(chosen, SortMode.AZ))
    }

    /** Icon size that still leaves room for the label in one grid row (54dp like the Apps screen when there is space). */
    private fun homeIconDp(): Int {
        val metrics = resources.displayMetrics
        val pageHeight = if (pager.height > 0) pager.height else metrics.heightPixels - dp(220)
        val rowDp = pageHeight / HomeStore.ROWS / metrics.density
        val landscape = isLandscape() // Step 9b: no labels in landscape, so the icon can use nearly the whole row
        val normal = if (landscape) {
            minOf(rowDp - 8f, AppsScreen.ICON_DP.toFloat()).coerceAtLeast(22f)
        } else {
            minOf(rowDp - 24f, AppsScreen.ICON_DP.toFloat()).coerceAtLeast(36f)
        }
        val scale = when (HomeStore.iconSize) {
            HomeStore.ICON_SMALL -> 0.84f
            HomeStore.ICON_LARGE -> 1.14f
            else -> 1f
        }
        // Large never grows past what leaves room for the label under it.
        return (normal * scale).coerceAtMost(rowDp - (if (landscape) 4f else 18f)).toInt().coerceIn(if (landscape) 20 else 32, 66)
    }

    private fun makeHomeIcon(app: AppEntry, iconDp: Int): AppIconView =
        AppIconView(this, app.icon, if (isLandscape()) null else app.label, iconDp).apply {
            setPadding(dp(2), 0, dp(2), 0)
            badgePackage = app.packageName
            contentDescription = app.label
            setOnClickListener { launch(it, app.launchIntent) }
            setOnLongClickListener {
                beginDrag(DragOrigin.HOME, it, app)
                true
            }
        }

    /** An app icon in the dock: no label, long-press lifts it like on the home pages. */
    private fun makeDockIcon(app: AppEntry, iconDp: Int): AppIconView =
        AppIconView(this, app.icon, null, iconDp).apply {
            badgePackage = app.packageName
            contentDescription = app.label
            setOnClickListener { launch(it, app.launchIntent) }
            setOnLongClickListener {
                beginDrag(DragOrigin.DOCK, it, app)
                true
            }
        }

    /**
     * A folder: the glass tile with its first four apps, and its name underneath ([labeled]; the dock has no labels).
     * [origin] says where it sits (home page or dock).
     */
    private fun makeFolderIcon(
        item: HomeItem,
        byKey: Map<String, AppEntry>,
        iconDp: Int,
        origin: DragOrigin = DragOrigin.HOME,
        labeled: Boolean = true
    ): AppIconView {
        val inside = ArrayList<AppEntry>()
        for (key in item.members.orEmpty()) {
            val app = byKey[key]
            if (app != null) inside.add(app)
        }
        val tile = FolderIcon.render(inside.take(4).map { it.icon }, dp(iconDp))
        val title = item.name.ifEmpty { HomeLayout.DEFAULT_FOLDER_NAME }
        val folderKey = item.key
        val showLabel = labeled && !isLandscape()
        return AppIconView(this, tile, if (showLabel) title else null, iconDp).apply {
            if (showLabel) setPadding(dp(2), 0, dp(2), 0)
            contentDescription = "$title folder"
            setOnClickListener { openFolder(folderKey, it) }
            setOnLongClickListener {
                beginFolderDrag(folderKey, it, origin)
                true
            }
        }
    }

    /** Tap on a folder: its card opens over the home screen. With [rename] the name is ready to type. */
    private fun openFolder(folderKey: String, anchor: View, rename: Boolean = false) {
        val folder = homeLayout?.find(folderKey) ?: return
        val inside = ArrayList<AppEntry>()
        for (key in folder.members.orEmpty()) {
            val app = allApps.firstOrNull { it.component.flattenToString() == key }
            if (app != null) inside.add(app)
        }
        if (inside.isEmpty()) return
        folderLayer.onRename = { typed -> renameFolder(folderKey, typed) }
        folderLayer.open(folder.name.ifEmpty { HomeLayout.DEFAULT_FOLDER_NAME }, inside, anchor, rename)
    }

    /** The person finished typing a folder name. Saves it and returns the name as stored. */
    private fun renameFolder(folderKey: String, typed: String): String {
        val layout = homeLayout ?: return typed.trim()
        val folder = layout.find(folderKey) ?: return typed.trim()
        val before = folder.name
        layout.rename(folderKey, typed)
        if (folder.name != before) {
            HomeStore.save(this, layout)
            refreshHome() // the label under the folder icon
        }
        return folder.name
    }

    /** One empty home page (the grid every page uses). */
    private fun newHomePage(): CellLayout {
        val cells = CellLayout(this, HomeStore.COLS, HomeStore.ROWS)
        cells.setPadding(dp(10), 0, dp(10), 0)
        return cells
    }

    /** Long-press on an icon: it lifts and the quick menu opens; keep the finger down and move to drag it. */
    private fun beginDrag(origin: DragOrigin, view: View, app: AppEntry) {
        val item = DragItem.ofApp(origin, app, isUserApp(app.packageName))
        dragLayer.begin(item, view, root.lastRawX, root.lastRawY, menuEntries(origin, app))
    }

    /** Long-press on a folder: it lifts and its menu opens (Rename / Ungroup / Remove folder); move the finger to drag it. */
    private fun beginFolderDrag(folderKey: String, view: View, origin: DragOrigin) {
        val entries = listOf(
            MenuEntry("Rename", null) { openFolder(folderKey, view, rename = true) },
            MenuEntry("Ungroup", null) { ungroupFolder(folderKey) },
            MenuEntry("Remove folder", null) { confirmRemoveFolder(folderKey) }
        )
        dragLayer.begin(DragItem.ofFolder(origin, folderKey), view, root.lastRawX, root.lastRawY, entries)
    }

    /** Ungroup: the folder goes away, its apps spread over the home screen. */
    private fun ungroupFolder(folderKey: String) {
        val layout = homeLayout ?: return
        if (!layout.dissolve(folderKey)) {
            toast("Not enough room on the Home screen")
            return
        }
        HomeStore.save(this, layout)
        folderLayer.close(animated = false)
        refreshHome()
        toast("Folder ungrouped")
    }

    /** Asks before a whole folder is removed from the home screen (its apps stay in the Apps screen). */
    private fun confirmRemoveFolder(folderKey: String) {
        val folder = homeLayout?.find(folderKey) ?: return
        val title = folder.name.ifEmpty { HomeLayout.DEFAULT_FOLDER_NAME }
        AlertDialog.Builder(this)
            .setTitle("Remove \"$title\" folder?")
            .setMessage("The apps stay in the Apps screen.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> removeFolder(folderKey) }
            .show()
    }

    private fun removeFolder(folderKey: String) {
        val layout = homeLayout ?: return
        if (layout.remove(folderKey)) {
            HomeStore.save(this, layout)
            folderLayer.close(animated = false)
            refreshHome()
            toast("Folder removed")
        }
    }

    /** Quick menu of an app in the folder card: take it out of the folder (to a free cell nearby). */
    private fun takeOutOfFolder(app: AppEntry) {
        val layout = homeLayout ?: return
        if (!layout.takeOut(app.component.flattenToString())) {
            toast("Home screen is full")
            return
        }
        HomeStore.save(this, layout)
        folderLayer.close(animated = true) // what it shows is out of date now
        refreshHome()
        toast("Removed from folder")
    }

    /** App shortcuts first, then Add / Remove, App info and Uninstall (not offered for system apps). */
    private fun menuEntries(origin: DragOrigin, app: AppEntry): List<MenuEntry> {
        val entries = ArrayList<MenuEntry>()
        for (shortcut in Shortcuts.load(this, app.packageName, dp(24))) {
            entries.add(
                MenuEntry(shortcut.label, shortcut.icon) {
                    if (!Shortcuts.start(this, shortcut)) toast("Can't open this shortcut")
                }
            )
        }
        val onHome = homeLayout?.contains(app.component.flattenToString()) == true
        if (origin == DragOrigin.FOLDER) {
            entries.add(MenuEntry("Remove from folder", null) { takeOutOfFolder(app) })
            entries.add(MenuEntry("Remove from Home screen", null) { removeFromHome(app) })
        } else if (origin == DragOrigin.DOCK) {
            entries.add(MenuEntry("Remove from Dock", null) { removeFromHome(app) })
        } else if (origin == DragOrigin.HOME || (origin == DragOrigin.DRAWER && onHome)) {
            entries.add(MenuEntry("Remove from Home screen", null) { removeFromHome(app) })
        } else if (origin == DragOrigin.DRAWER) {
            entries.add(MenuEntry("Add to Home screen", null) { addToHome(app) })
        }
        entries.add(MenuEntry("App info", null) { openAppInfo(app) })
        if (isUserApp(app.packageName)) entries.add(MenuEntry("Uninstall", null) { uninstall(app) })
        return entries
    }

    @Suppress("DEPRECATION")
    private fun isUserApp(pkg: String): Boolean = try {
        (packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun uninstall(app: AppEntry) {
        try {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast("Can't uninstall this app")
        }
    }

    private fun addToHome(app: AppEntry) {
        val layout = homeLayout ?: return
        val placed = layout.add(app.component.flattenToString())
        if (placed == null) {
            toast("Home screen is full")
            return
        }
        HomeStore.save(this, layout)
        refreshHome()
        pager.setPage(placed.page, animated = false) // already in view when the Apps screen closes
        toast("Added to Home screen")
    }

    private fun removeFromHome(app: AppEntry) {
        val layout = homeLayout ?: return
        if (layout.remove(app.component.flattenToString())) {
            HomeStore.save(this, layout)
            folderLayer.close(animated = true) // a folder card may still show the app
            refreshHome()
            toast("Removed")
        }
    }

    private fun openAppInfo(app: AppEntry) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast("Can't open App info")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- launching

    private fun launch(from: View, intent: Intent) {
        val options = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
        try {
            startActivity(
                Intent(intent).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                ),
                options.toBundle()
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Can't open this app", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Can't open this app", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- default home app

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return pkg == packageName
    }

    private fun askToBeDefaultOnce() {
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        if (!isDefaultHome() && !prefs.getBoolean("asked_default", false)) {
            prefs.edit().putBoolean("asked_default", true).apply()
            requestDefaultHome()
        }
    }

    private fun requestDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) &&
                    !roles.isRoleHeld(RoleManager.ROLE_HOME)
                ) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open Settings > Apps > Default apps > Home app", Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val REQ_HOME_ROLE = 1001
        const val REQ_WIDGET_BIND = 1002
        const val REQ_WIDGET_CONFIGURE = 1003
        const val REQ_WIDGET_RECONFIGURE = 1004
        const val KEY_DOCK_SEEDED = "dock_seeded"
        const val KEY_HOME_SEARCH = "home_search"
        const val KEY_NOTIF_SWIPE = "notif_swipe"
        const val KEY_SWIPE_MODE = "swipe_down_mode"
        const val KEY_QP_LEFT = "qp_left"
        const val SWIPE_NOTIF = 0
        const val SWIPE_QUICK = 1
        const val SWIPE_SPLIT = 2
        const val KEY_BLUR = "blur"
        const val BLUR_DP = 28

        /** Blur of the Home screen's own icons behind a panel (Step 18). */
        const val CONTENT_BLUR_DP = 9
        const val KEY_DOCK_COLOR = "dock_color"
        const val KEY_DOCK_OPACITY = "dock_opacity"
        const val KEY_DOCK_GRADIENT = "dock_gradient"
        const val KEY_SEARCH_GRADIENT = "search_gradient"
        const val KEY_WALLPAPER_SCROLL = "wallpaper_scroll"
        const val KEY_LOCK_TAP = "lock_tap"
        const val REQ_LOCK_ADMIN = 1010
    }
}
