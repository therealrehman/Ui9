package com.oklabs.oneui9launcher

import kotlin.math.max
import kotlin.math.min

/** How many cells wide / tall a widget may be made. Plain numbers, so the rules can be tested on a normal JVM. */
class ResizeLimits(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
    /** The width can change. */
    val canX: Boolean get() = maxX > minX

    /** The height can change. */
    val canY: Boolean get() = maxY > minY

    /** At least one side can change: the widget gets a Resize entry and resize handles. */
    val canResize: Boolean get() = canX || canY
}

object ResizeRules {

    /** The widget says (AppWidgetProviderInfo.resizeMode): 1 = wider / narrower, 2 = taller / shorter, 3 = both, 0 = fixed size. */
    const val MODE_HORIZONTAL = 1
    const val MODE_VERTICAL = 2

    /**
     * The sizes (in cells) a widget that now covers [spanX] x [spanY] cells may be resized to, on a page of [cols] x [rows] cells
     * that are [cellW] x [cellH] pixels. The smallest size follows the widget's own minimum resize size, but is never bigger
     * than the current size (so a widget never has to grow just to be allowed to shrink); the biggest is the whole page,
     * or the widget's own maximum when it states one (0 = none). A side the widget does not allow stays as it is.
     */
    fun limits(
        resizeMode: Int,
        minResizeW: Int,
        minResizeH: Int,
        maxResizeW: Int,
        maxResizeH: Int,
        cellW: Int,
        cellH: Int,
        cols: Int,
        rows: Int,
        spanX: Int,
        spanY: Int
    ): ResizeLimits {
        val curX = spanX.coerceIn(1, max(1, cols))
        val curY = spanY.coerceIn(1, max(1, rows))
        var minX = curX
        var maxX = curX
        var minY = curY
        var maxY = curY
        if ((resizeMode and MODE_HORIZONTAL) != 0) {
            minX = (if (minResizeW > 0 && cellW > 0) ceilDiv(minResizeW, cellW) else 1).coerceIn(1, curX)
            maxX = (if (maxResizeW > 0 && cellW > 0) max(curX, maxResizeW / cellW) else cols).coerceIn(curX, max(curX, cols))
        }
        if ((resizeMode and MODE_VERTICAL) != 0) {
            minY = (if (minResizeH > 0 && cellH > 0) ceilDiv(minResizeH, cellH) else 1).coerceIn(1, curY)
            maxY = (if (maxResizeH > 0 && cellH > 0) max(curY, maxResizeH / cellH) else rows).coerceIn(curY, max(curY, rows))
        }
        return ResizeLimits(minX, min(maxX, max(curX, cols)), minY, min(maxY, max(curY, rows)))
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
