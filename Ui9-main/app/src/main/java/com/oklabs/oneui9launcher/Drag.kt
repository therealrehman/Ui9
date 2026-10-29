package com.oklabs.oneui9launcher

import android.graphics.Bitmap

/** Where a long-pressed icon came from. HOME / DOCK also cover a whole folder; FOLDER is an app inside the open folder card. */
enum class DragOrigin { HOME, DRAWER, DOCK, FOLDER }

/** What the drop zones at the top of the screen do while an icon is carried. */
enum class DragAction { REMOVE, APP_INFO, UNINSTALL }

/**
 * The icon being carried (or menu-ed): an app, a whole folder or a widget from the home screen.
 * [key] is what the home layout knows it by (the app's component, "#f1" or "@w17"). [app] is null for a folder and a widget.
 * [canUninstall] is false for system apps, folders and widgets. [spanX] x [spanY] is the size of a widget in cells (1 x 1 otherwise).
 */
class DragItem private constructor(
    val origin: DragOrigin,
    val key: String,
    val app: AppEntry?,
    val canUninstall: Boolean,
    val spanX: Int = 1,
    val spanY: Int = 1
) {
    val isWidget: Boolean get() = key.startsWith(WIDGET_PREFIX)

    val isFolder: Boolean get() = app == null && !isWidget

    companion object {
        fun ofApp(origin: DragOrigin, app: AppEntry, canUninstall: Boolean): DragItem =
            DragItem(origin, app.component.flattenToString(), app, canUninstall)

        fun ofFolder(origin: DragOrigin, folderKey: String): DragItem = DragItem(origin, folderKey, null, false)

        fun ofWidget(widgetKey: String, spanX: Int, spanY: Int): DragItem =
            DragItem(DragOrigin.HOME, widgetKey, null, false, spanX, spanY)
    }
}

/** One row of the quick menu: an optional small icon, a label and what happens when it is tapped. */
class MenuEntry(val label: String, val icon: Bitmap?, val action: () -> Unit)

/** What the drag layer needs from the home screen. Implemented by [LauncherActivity]. */
interface DragHost {
    /** Index of the home page nearest the middle of the screen. */
    fun currentPageIndex(): Int

    /** That page's grid, or null while the home screen is not built yet. */
    fun currentPage(): CellLayout?

    /** The dock, or null while the home screen is not built yet. Its slots are drop targets too (page [HomeLayout.DOCK_PAGE]). */
    fun dock(): DockView?

    /** Slides one page left (-1) or right (+1). Right of the last page opens a fresh empty page while there is room. */
    fun slidePage(direction: Int): Boolean

    /** True once the Apps screen is completely closed, so cell positions can be trusted. */
    fun homeReady(): Boolean

    /** The Apps screen is still closing: finish that right now, no animation. */
    fun settleHome()

    /** A real drag began (the finger moved). Drags that start in the Apps screen or in the folder card close it. */
    fun onDragStarted(item: DragItem)

    /**
     * True when cell ([col], [row]) of home page [page] (or of the dock, page [HomeLayout.DOCK_PAGE]) holds another app or a folder that [item] could go into.
     * Holding the icon over such a cell for a moment lights it up as a folder drop. Never true for a folder being carried.
     */
    fun canMerge(item: DragItem, page: Int, col: Int, row: Int): Boolean

    /** For a widget being carried: true when it can be dropped with its top-left cell on ([col], [row]) of home page [page]. */
    fun canPlaceWidget(item: DragItem, page: Int, col: Int, row: Int): Boolean

    /**
     * The icon was dropped on a cell of home page [page] (or a dock slot). [merge] = it was held there long enough: make / fill a folder.
     * For a widget ([col], [row]) is the top-left cell of the block it covers.
     */
    fun onDropOnCell(item: DragItem, page: Int, col: Int, row: Int, merge: Boolean)

    /** The icon was dropped on one of the zones at the top. */
    fun onDropOnAction(item: DragItem, action: DragAction)

    /** A drag is over, whatever the result: rebuild the pages (also drops an empty temporary page). */
    fun onDragEnded()
}
