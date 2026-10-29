package com.oklabs.oneui9launcher

/** Every widget key starts with this: "@w" + the widget's id (Android's appWidgetId), e.g. "@w17". */
const val WIDGET_PREFIX = "@w"

/**
 * One thing sitting on the home grid: a single app, a folder of apps, or a widget.
 *  - App:    [key] is the app's component ("package/Class"), [members] is null, one cell.
 *  - Folder: [key] is "#f1", "#f2"... (never a component), [members] lists the app keys inside, in order. One cell.
 *  - Widget: [key] is "@w17" (see [WIDGET_PREFIX]); it covers [spanX] x [spanY] cells starting at ([col], [row]);
 *            [provider] is the widget's component ("package/Class") so a widget whose app is gone can be found.
 */
class HomeItem(
    val key: String,
    var page: Int,
    var col: Int,
    var row: Int,
    val members: ArrayList<String>? = null,
    var name: String = "",
    var spanX: Int = 1,
    var spanY: Int = 1,
    val provider: String = ""
) {
    val isFolder: Boolean get() = members != null

    val isWidget: Boolean get() = key.startsWith(WIDGET_PREFIX)

    /** The Android appWidgetId of a widget, or -1 for anything else. */
    val widgetId: Int get() = if (isWidget) key.substring(WIDGET_PREFIX.length).toIntOrNull() ?: -1 else -1

    /** True when this item covers cell ([c], [r]) of [p]. Apps and folders cover just their own cell. */
    fun covers(p: Int, c: Int, r: Int): Boolean =
        page == p && c >= col && c < col + spanX && r >= row && r < row + spanY

    /** True when this item's cells and the block of [sx] x [sy] cells whose top-left cell is ([c], [r]) share a cell (the page is not looked at). */
    fun overlaps(c: Int, r: Int, sx: Int, sy: Int): Boolean =
        col < c + sx && col + spanX > c && row < r + sy && row + spanY > r
}

/**
 * The saved home-screen layout: which app (or folder) sits in which cell of which page.
 * The dock is one more "page" ([DOCK_PAGE]) with [DOCK_SLOTS] cells in a single row, so moving things between the
 * home pages and the dock, folders in the dock and saving all use the same rules.
 * Plain Kotlin (no Android classes), so every rule here can be tested on a normal JVM.
 *
 * A folder always holds at least two apps: when one is taken out and a single app is left, the folder
 * turns back into that app, in the same cell; an empty folder disappears.
 * Step 6a: widgets are items too. A widget covers a block of cells ([HomeItem.spanX] x [HomeItem.spanY]); no other
 * item can sit under it, and apps never swap with it or merge into it.
 * Step 6b: a widget can be moved ([moveWidget]) and resized ([resizeWidget]); apps under its new block step aside.
 */
class HomeLayout(val cols: Int, val rows: Int) {

    private val items = ArrayList<HomeItem>()

    val all: List<HomeItem> get() = items

    /** Number of home pages (the dock does not count), never less than one. */
    val pageCount: Int get() = (items.filter { it.page != DOCK_PAGE }.maxOfOrNull { it.page } ?: 0) + 1

    /** True when something sits in the dock. */
    val hasDock: Boolean get() = items.any { it.page == DOCK_PAGE }

    /** True when ([page], [col], [row]) is a cell that exists: a cell of a home page, or one of the dock slots. */
    private fun inGrid(page: Int, col: Int, row: Int): Boolean =
        if (page == DOCK_PAGE) col in 0 until DOCK_SLOTS && row == 0
        else page in 0 until MAX_PAGES && col in 0 until cols && row in 0 until rows

    /** True when [key] is on the home screen, loose on a page or inside a folder. */
    fun contains(key: String): Boolean =
        items.any { it.key == key || it.members?.contains(key) == true }

    /** The loose item with this key (an app on a page, or a folder). Apps inside a folder are not found here. */
    fun find(key: String): HomeItem? = items.firstOrNull { it.key == key }

    /** The folder that holds the app [key], or null. */
    fun folderOf(key: String): HomeItem? = items.firstOrNull { it.members?.contains(key) == true }

    /** Every app on the home screen: loose ones and the ones inside folders (not widgets). A fresh list, safe to loop while changing the layout. */
    fun appKeys(): List<String> {
        val out = ArrayList<String>()
        for (item in items) {
            if (item.isWidget) continue // widgets are not apps
            val inside = item.members
            if (inside == null) out.add(item.key) else out.addAll(inside)
        }
        return out
    }

    fun itemsOnPage(page: Int): List<HomeItem> = items.filter { it.page == page }

    /** True when no item (a widget counts with all the cells it covers) sits on this cell. */
    fun isFree(page: Int, col: Int, row: Int): Boolean =
        items.none { it.covers(page, col, row) }

    /** The item that covers this cell: an app, a folder, or a widget that reaches into it. */
    private fun cellItem(page: Int, col: Int, row: Int): HomeItem? =
        items.firstOrNull { it.covers(page, col, row) }

    /** The widget that covers cell ([col], [row]) of [page], or null. */
    fun widgetAt(page: Int, col: Int, row: Int): HomeItem? =
        items.firstOrNull { it.isWidget && it.covers(page, col, row) }

    /** Every widget on the home screen. A fresh list, safe to loop while changing the layout. */
    fun widgets(): List<HomeItem> = items.filter { it.isWidget }

    /** True when a [spanX] x [spanY] block whose top-left cell is ([col], [row]) lies inside the page and every cell of it is free. */
    private fun rectFree(page: Int, col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        if (spanX < 1 || spanY < 1) return false
        if (!inGrid(page, col, row) || !inGrid(page, col + spanX - 1, row + spanY - 1)) return false
        for (r in row until row + spanY) {
            for (c in col until col + spanX) {
                if (!isFree(page, c, r)) return false
            }
        }
        return true
    }

    /**
     * Where a [spanX] x [spanY] widget fits: first on [preferredPage], then page by page (a brand-new page last, while there is room).
     * Top to bottom, left to right on each page. Null when there is no room anywhere.
     */
    fun findWidgetSpot(spanX: Int, spanY: Int, preferredPage: Int): Triple<Int, Int, Int>? {
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, rows)
        val first = preferredPage.coerceIn(0, pageCount)
        val order = ArrayList<Int>()
        order.add(first)
        for (p in 0..pageCount) if (p != first) order.add(p)
        for (page in order) {
            if (page >= MAX_PAGES) continue
            for (row in 0..(rows - sy)) {
                for (col in 0..(cols - sx)) {
                    if (rectFree(page, col, row, sx, sy)) return Triple(page, col, row)
                }
            }
        }
        return null
    }

    /**
     * Puts a new widget on the home screen: [key] ("@w17"), its component [provider] and its size in cells.
     * Returns where it went, or null (nothing changed) when there is no room or [key] is already used.
     */
    fun addWidget(key: String, provider: String, spanX: Int, spanY: Int, preferredPage: Int): HomeItem? {
        if (!key.startsWith(WIDGET_PREFIX) || contains(key)) return null
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, rows)
        val spot = findWidgetSpot(sx, sy, preferredPage) ?: return null
        val item = HomeItem(key, spot.first, spot.second, spot.third, null, "", sx, sy, provider)
        items.add(item)
        return item
    }

    /** A cell an app or folder has to step into so a widget can take its place. */
    private class Relocation(val item: HomeItem, val col: Int, val row: Int)

    /** True when no item except [except] covers this cell. */
    private fun isFreeExcept(page: Int, col: Int, row: Int, except: HomeItem): Boolean =
        items.none { it !== except && it.covers(page, col, row) }

    /**
     * What it takes to give the widget [widget] the block of [spanX] x [spanY] cells whose top-left cell is ([col], [row]) of
     * home [page]: the apps / folders under that block step aside to the nearest free cells of that page (not under the
     * widget). Null when the block is outside the page, another widget is in the way, or there are not enough free cells.
     * The cells the widget covers now count as free. Changes nothing. (Moving and resizing both use this.)
     */
    private fun planWidgetPlacement(widget: HomeItem, page: Int, col: Int, row: Int, spanX: Int, spanY: Int): List<Relocation>? {
        if (!widget.isWidget) return null
        if (page !in 0 until MAX_PAGES) return null // widgets never go into the dock
        if (spanX < 1 || spanY < 1) return null
        if (col < 0 || row < 0 || col + spanX > cols || row + spanY > rows) return null
        val blockers = items.filter {
            it !== widget && it.page == page && it.overlaps(col, row, spanX, spanY)
        }
        if (blockers.any { it.isWidget }) return null
        val taken = HashSet<Int>()
        val plan = ArrayList<Relocation>()
        for (blocker in blockers) {
            var best = -1
            var bestDistance = Int.MAX_VALUE
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val underWidget = c >= col && c < col + spanX && r >= row && r < row + spanY
                    if (underWidget || taken.contains(r * cols + c)) continue
                    if (!isFreeExcept(page, c, r, widget)) continue
                    val distance = (c - blocker.col) * (c - blocker.col) + (r - blocker.row) * (r - blocker.row)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = r * cols + c
                    }
                }
            }
            if (best < 0) return null
            taken.add(best)
            plan.add(Relocation(blocker, best % cols, best / cols))
        }
        return plan
    }

    /** Moving keeps the widget's size. */
    private fun planWidgetMove(widget: HomeItem, page: Int, col: Int, row: Int): List<Relocation>? =
        planWidgetPlacement(widget, page, col, row, widget.spanX, widget.spanY)

    /** True when [moveWidget] would succeed for the widget [key] and this spot. */
    fun canMoveWidget(key: String, page: Int, col: Int, row: Int): Boolean {
        val widget = find(key) ?: return false
        return planWidgetMove(widget, page, col, row) != null
    }

    /**
     * Moves the widget [key] so its top-left cell is ([col], [row]) of home [page] (maybe another page, maybe a brand-new one
     * right after the last). Apps and folders under the new spot step aside to the nearest free cells; another widget in
     * the way, or no room for the apps, makes it return false and change nothing. A page the widget left empty disappears,
     * so look the widget up again with [find] to see where it ended up.
     */
    fun moveWidget(key: String, page: Int, col: Int, row: Int): Boolean {
        val widget = find(key) ?: return false
        val plan = planWidgetMove(widget, page, col, row) ?: return false
        for (move in plan) {
            move.item.col = move.col
            move.item.row = move.row
        }
        widget.page = page
        widget.col = col
        widget.row = row
        dropEmptyPages()
        return true
    }

    /** True when [resizeWidget] would succeed for the widget [key] and this block of cells. */
    fun canResizeWidget(key: String, col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        val widget = find(key) ?: return false
        return planWidgetPlacement(widget, widget.page, col, row, spanX, spanY) != null
    }

    /**
     * Resizes the widget [key] (it stays on its page): afterwards it covers the [spanX] x [spanY] cells whose top-left
     * cell is ([col], [row]). Growing over apps / folders makes them step aside to the nearest free cells of the page;
     * another widget in the way, a block outside the page or no room for the apps makes it return false and change
     * nothing. Shrinking just frees the cells.
     */
    fun resizeWidget(key: String, col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        val widget = find(key) ?: return false
        val plan = planWidgetPlacement(widget, widget.page, col, row, spanX, spanY) ?: return false
        for (move in plan) {
            move.item.col = move.col
            move.item.row = move.row
        }
        widget.col = col
        widget.row = row
        widget.spanX = spanX
        widget.spanY = spanY
        return true
    }

    /**
     * Puts [key] into the first free cell: page by page, top to bottom, left to right.
     * Returns where it went, or null when it is already on the home screen or every page is full.
     */
    fun add(key: String): HomeItem? {
        if (contains(key)) return null
        val item = HomeItem(key, 0, 0, 0)
        return if (placeInFirstFree(item)) item else null
    }

    /** The first free cell as (page, col, row): page by page, top to bottom, left to right. Null when every page is full. */
    private fun firstFree(): Triple<Int, Int, Int>? {
        for (page in 0 until MAX_PAGES) {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (isFree(page, col, row)) return Triple(page, col, row)
                }
            }
        }
        return null
    }

    private fun placeInFirstFree(item: HomeItem): Boolean {
        val spot = firstFree() ?: return false
        item.page = spot.first
        item.col = spot.second
        item.row = spot.third
        items.add(item)
        return true
    }

    /** The free cell of [page] closest to ([col], [row]), or null when the page is full. */
    private fun nearestFree(page: Int, col: Int, row: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        val pageCols = if (page == DOCK_PAGE) DOCK_SLOTS else cols
        val pageRows = if (page == DOCK_PAGE) 1 else rows
        for (r in 0 until pageRows) {
            for (c in 0 until pageCols) {
                if (!isFree(page, c, r)) continue
                val distance = (c - col) * (c - col) + (r - row) * (r - row)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = Pair(c, r)
                }
            }
        }
        return best
    }

    private fun place(moving: HomeItem?, key: String, page: Int, col: Int, row: Int) {
        if (moving != null) {
            moving.page = page
            moving.col = col
            moving.row = row
        } else {
            items.add(HomeItem(key, page, col, row))
        }
    }

    /**
     * Drag and drop: puts [key] into cell ([col], [row]) of [page].
     *  - free cell: the icon moves there.
     *  - taken, and the icon came from the same page: the two icons swap places.
     *  - taken, icon came from another page or from the Apps screen: the icon that was there steps aside
     *    to the nearest free cell of the page.
     * [key] may be new to the home screen (dragged in from the Apps screen) or may sit inside a folder
     * (it leaves the folder). Returns false, changing nothing, when the cell is outside the grid or the
     * page has no room to move the other icon to.
     * A page the icon left empty disappears, so look the icon up again with [find] to see where it ended up.
     */
    fun drop(key: String, page: Int, col: Int, row: Int): Boolean {
        if (!inGrid(page, col, row)) return false
        val moving = find(key)
        val before = cellItem(page, col, row)
        if (before != null && before.isWidget) return false // a widget covers this cell: apps never swap with it
        if (before != null && before === moving) return true // dropped back where it was
        // An app taken out of a folder and dropped on that same folder's cell: it just stays inside.
        if (moving == null && before != null && before.members?.contains(key) == true) return true

        if (moving == null) {
            // Not a loose icon: new from the Apps screen, or inside a folder. Check there is room first.
            if (before != null && nearestFree(page, col, row) == null) return false
            detachFromFolder(key)
        }
        // A folder that just turned back into an app may have changed what sits in this cell.
        val occupant = cellItem(page, col, row)

        if (occupant == null) {
            place(moving, key, page, col, row)
        } else if (moving != null && moving.page == page) {
            occupant.col = moving.col
            occupant.row = moving.row
            moving.col = col
            moving.row = row
        } else {
            val spot = nearestFree(page, col, row) ?: return false
            occupant.col = spot.first
            occupant.row = spot.second
            place(moving, key, page, col, row)
        }
        dropEmptyPages()
        return true
    }

    /**
     * Folder drop: holds [key] over the icon in cell ([col], [row]) of [page] and lets go.
     *  - the cell holds an app: both apps go into a new folder that takes the cell.
     *  - the cell holds a folder: the app joins it (nothing happens if it is already inside).
     * [key] may come from the Apps screen, from another cell or from another folder.
     * Returns false, changing nothing, when there is no other icon in that cell or the folder is full.
     */
    fun mergeInto(key: String, page: Int, col: Int, row: Int): Boolean {
        if (!inGrid(page, col, row)) return false
        val target = cellItem(page, col, row) ?: return false
        if (target.isWidget || target.key == key) return false // nothing goes "into" a widget

        val inside = target.members
        if (inside != null) {
            if (inside.contains(key)) return true
            if (inside.size >= MAX_FOLDER_APPS) return false
            detach(key)
            inside.add(key)
        } else {
            detach(key)
            val folder = HomeItem(
                newFolderKey(), target.page, target.col, target.row,
                arrayListOf(target.key, key), DEFAULT_FOLDER_NAME
            )
            items.remove(target)
            items.add(folder)
        }
        dropEmptyPages()
        return true
    }

    /**
     * Folder card: takes the app [key] out of its folder and puts it in the free cell nearest to the folder
     * (another page when that page is full). A folder left with one app turns back into that app.
     * Returns false, changing nothing, when [key] is not in a folder or the home screen has no free cell.
     */
    fun takeOut(key: String): Boolean {
        val folder = folderOf(key) ?: return false
        val spot = nearestFree(folder.page, folder.col, folder.row)
            ?.let { Triple(folder.page, it.first, it.second) }
            ?: firstFree()
            ?: return false
        detachFromFolder(key)
        items.add(HomeItem(key, spot.first, spot.second, spot.third))
        dropEmptyPages()
        return true
    }

    /**
     * Ungroup: the folder [key] goes away and its apps spread out on the home screen. The first app takes the
     * folder's cell, the others the nearest free cells (other pages when the page is full).
     * Returns false, changing nothing, when [key] is not a folder or the apps do not all fit.
     */
    fun dissolve(key: String): Boolean {
        val folder = find(key) ?: return false
        val inside = folder.members ?: return false
        val backup = ArrayList(items)
        items.remove(folder)
        var ok = true
        for ((index, appKey) in inside.withIndex()) {
            val spot = if (index == 0) {
                Triple(folder.page, folder.col, folder.row)
            } else {
                nearestFree(folder.page, folder.col, folder.row)?.let { Triple(folder.page, it.first, it.second) }
                    ?: firstFree()
            }
            if (spot == null) {
                ok = false
                break
            }
            items.add(HomeItem(appKey, spot.first, spot.second, spot.third))
        }
        if (!ok) {
            items.clear()
            items.addAll(backup)
            return false
        }
        dropEmptyPages()
        return true
    }

    /**
     * Gives the folder [key] a new name: tabs / line breaks become spaces, ends are trimmed, at most
     * [MAX_FOLDER_NAME] characters. A blank name goes back to [DEFAULT_FOLDER_NAME].
     * Returns false when [key] is not a folder.
     */
    fun rename(key: String, name: String): Boolean {
        val folder = find(key) ?: return false
        if (folder.members == null) return false
        folder.name = cleanName(name).take(MAX_FOLDER_NAME).trim().ifEmpty { DEFAULT_FOLDER_NAME }
        return true
    }

    private fun newFolderKey(): String {
        var n = 1
        while (items.any { it.key == "#f$n" }) n++
        return "#f$n"
    }

    /** Takes [key] out of whatever holds it (a cell or a folder). Does not close page gaps. */
    private fun detach(key: String) {
        val loose = items.firstOrNull { it.key == key }
        if (loose != null) {
            items.remove(loose)
            return
        }
        detachFromFolder(key)
    }

    private fun detachFromFolder(key: String) {
        val folder = folderOf(key) ?: return
        folder.members?.remove(key)
        tidy(folder)
    }

    /** A folder with one app left turns back into that app; an empty one disappears. */
    private fun tidy(folder: HomeItem) {
        val inside = folder.members ?: return
        if (inside.isEmpty()) {
            items.remove(folder)
        } else if (inside.size == 1) {
            val index = items.indexOf(folder)
            if (index >= 0) items[index] = HomeItem(inside[0], folder.page, folder.col, folder.row)
        }
    }

    /** Takes [key] off the home screen (also out of a folder; a folder key removes the whole folder). */
    fun remove(key: String): Boolean {
        var changed = items.removeAll { it.key == key }
        if (!changed) {
            val folder = folderOf(key)
            if (folder != null) {
                folder.members?.remove(key)
                tidy(folder)
                changed = true
            }
        }
        if (changed) dropEmptyPages()
        return changed
    }

    /** Keeps only the apps [keep] says yes to (used to forget uninstalled apps). Folders are looked into. */
    fun retain(keep: (String) -> Boolean): Boolean {
        var changed = false
        for (item in items.toList()) {
            if (item.isWidget) continue
            val inside = item.members
            if (inside == null) {
                if (!keep(item.key)) {
                    items.remove(item)
                    changed = true
                }
            } else if (inside.removeAll { !keep(it) }) {
                changed = true
                tidy(item)
            }
        }
        if (changed) dropEmptyPages()
        return changed
    }

    /** An app kept its package but its launcher activity got a new name: it keeps its place (cell or folder). */
    fun replaceKey(oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        val index = items.indexOfFirst { it.key == oldKey }
        if (index >= 0) {
            if (contains(newKey)) {
                remove(oldKey)
            } else {
                val old = items[index]
                items[index] = HomeItem(newKey, old.page, old.col, old.row)
            }
            return
        }
        val folder = folderOf(oldKey) ?: return
        val inside = folder.members ?: return
        if (contains(newKey)) {
            remove(oldKey)
        } else {
            inside[inside.indexOf(oldKey)] = newKey
        }
    }

    /** Closes gaps: a page with no apps left disappears and the pages after it move up. */
    fun dropEmptyPages(): Boolean {
        var changed = false
        val used = items.map { it.page }.filter { it != DOCK_PAGE }.distinct().sorted()
        used.forEachIndexed { newIndex, oldPage ->
            if (newIndex != oldPage) {
                for (item in items) if (item.page == oldPage) item.page = newIndex
                changed = true
            }
        }
        return changed
    }

    /**
     * Text form that goes into SharedPreferences. First line is the header, then one line per icon:
     * "page TAB col TAB row TAB key". A folder line continues: "... TAB #f1 TAB name TAB app TAB app ...".
     * A widget line continues: "... TAB @w17 TAB spanX TAB spanY TAB provider".
     */
    fun serialize(): String {
        val out = StringBuilder("v3 $cols $rows")
        val ordered = items.sortedWith(compareBy({ it.page }, { it.row }, { it.col }))
        for (item in ordered) {
            out.append('\n').append(item.page).append('\t').append(item.col).append('\t')
                .append(item.row).append('\t').append(item.key)
            val inside = item.members
            if (inside != null) {
                out.append('\t').append(cleanName(item.name))
                for (member in inside) out.append('\t').append(member)
            } else if (item.isWidget) {
                out.append('\t').append(item.spanX).append('\t').append(item.spanY)
                    .append('\t').append(cleanName(item.provider))
            }
        }
        return out.toString()
    }

    private fun cleanName(text: String): String =
        text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    companion object {
        /** Most pages the home screen can have. */
        const val MAX_PAGES = 8

        /** Most apps one folder can hold. */
        const val MAX_FOLDER_APPS = 20

        /** The page number the dock is saved under (home pages are 0..MAX_PAGES-1). */
        const val DOCK_PAGE = 100

        /** Icons the dock holds. */
        const val DOCK_SLOTS = 5

        /** Longest folder name (what the keyboard allows when renaming). */
        const val MAX_FOLDER_NAME = 24

        /** Name of a folder nobody has renamed. */
        const val DEFAULT_FOLDER_NAME = "Folder"

        /**
         * Reads what [serialize] wrote (the older "v1" / "v2" layouts without widgets too). Returns null for missing
         * or unreadable data (the caller then starts a fresh layout). Bad lines are skipped; an icon whose saved
         * cell no longer exists (smaller grid) or is taken goes to the first free cell instead.
         */
        fun parse(text: String?, cols: Int, rows: Int): HomeLayout? {
            if (text.isNullOrBlank()) return null
            val lines = text.split('\n')
            val header = lines[0].trim().split(' ')
            if (header.size != 3 || (header[0] != "v1" && header[0] != "v2" && header[0] != "v3")) return null

            val layout = HomeLayout(cols, rows)
            val seen = HashSet<String>()
            val misplaced = ArrayList<HomeItem>()
            for (line in lines.drop(1)) {
                val parts = line.split('\t', limit = 4)
                if (parts.size != 4) continue
                val page = parts[0].toIntOrNull() ?: continue
                val col = parts[1].toIntOrNull() ?: continue
                val row = parts[2].toIntOrNull() ?: continue
                val item = readItem(parts[3], page, col, row, seen) ?: continue
                val fits = layout.rectFree(page, col, row, item.spanX, item.spanY)
                if (fits) {
                    layout.items.add(item)
                } else {
                    misplaced.add(item)
                }
            }
            for (item in misplaced) {
                if (item.isWidget) {
                    item.spanX = item.spanX.coerceIn(1, cols)
                    item.spanY = item.spanY.coerceIn(1, rows)
                    val spot = layout.findWidgetSpot(item.spanX, item.spanY, item.page)
                    if (spot != null) {
                        item.page = spot.first
                        item.col = spot.second
                        item.row = spot.third
                        layout.items.add(item)
                    }
                } else {
                    layout.placeInFirstFree(item)
                }
            }
            layout.dropEmptyPages()
            return layout
        }

        /** One saved icon: an app key, "#id TAB name TAB app TAB app...", or "@wId TAB spanX TAB spanY TAB provider". Null when there is nothing usable. */
        private fun readItem(field: String, page: Int, col: Int, row: Int, seen: MutableSet<String>): HomeItem? {
            if (field.startsWith(WIDGET_PREFIX)) {
                val parts = field.split('\t')
                val id = parts[0]
                if (id.length <= WIDGET_PREFIX.length || id.substring(WIDGET_PREFIX.length).toIntOrNull() == null) return null
                if (!seen.add(id)) return null
                val spanX = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val spanY = parts.getOrNull(2)?.toIntOrNull() ?: 1
                val provider = parts.getOrNull(3).orEmpty()
                return HomeItem(id, page, col, row, null, "", spanX.coerceAtLeast(1), spanY.coerceAtLeast(1), provider)
            }
            if (!field.startsWith("#")) {
                if (field.isEmpty() || !seen.add(field)) return null
                return HomeItem(field, page, col, row)
            }
            val fields = field.split('\t')
            val id = fields[0]
            if (id.length < 2 || !seen.add(id)) return null
            val name = if (fields.size > 1) fields[1] else ""
            val inside = ArrayList<String>()
            for (member in fields.drop(2)) {
                if (member.isNotEmpty() && !member.startsWith("#") && seen.add(member)) inside.add(member)
            }
            return when {
                inside.isEmpty() -> null
                inside.size == 1 -> HomeItem(inside[0], page, col, row)
                else -> HomeItem(id, page, col, row, inside, name.ifEmpty { DEFAULT_FOLDER_NAME })
            }
        }
    }
}
