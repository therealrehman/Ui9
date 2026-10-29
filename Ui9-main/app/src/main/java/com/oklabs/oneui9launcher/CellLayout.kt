package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * One home-screen page: an invisible [cols] x [rows] grid. Every child sits in one cell ([CellParams]) and is
 * centred inside it. Cells use the whole page, so the icons spread over the full height like they do in One UI.
 * Step 6a: a widget child covers a block of cells ([CellParams.spanX] x [CellParams.spanY]) and fills that block
 * (with a small gap around it so two widgets never touch).
 */
class CellLayout(context: Context, val cols: Int, val rows: Int) : ViewGroup(context) {

    /** Which cell a child lives in. A widget also says how many cells it covers. */
    class CellParams(
        val col: Int,
        val row: Int,
        val spanX: Int = 1,
        val spanY: Int = 1,
        val widget: Boolean = false
    ) : ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is CellParams

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = CellParams(0, 0)

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = CellParams(0, 0)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = CellParams(0, 0)

    private fun cellWidth(total: Int): Int = ((total - paddingLeft - paddingRight) / cols).coerceAtLeast(0)

    private fun cellHeight(total: Int): Int = ((total - paddingTop - paddingBottom) / rows).coerceAtLeast(0)

    /** Gap kept around a widget inside the cells it covers. */
    private val widgetGap: Int get() = context.dp(4)

    /** Column and row of the cell under ([x], [y]) in this view's own coordinates, or null outside the grid. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val cw = cellWidth(width)
        val ch = cellHeight(height)
        if (cw <= 0 || ch <= 0) return null
        val px = x - paddingLeft
        val py = y - paddingTop
        if (px < 0f || py < 0f) return null
        val col = (px / cw).toInt()
        val row = (py / ch).toInt()
        return if (col < cols && row < rows) Pair(col, row) else null
    }

    /** Where a cell is, in this view's own coordinates. */
    fun cellBounds(col: Int, row: Int): Rect {
        val cw = cellWidth(width)
        val ch = cellHeight(height)
        val left = paddingLeft + col * cw
        val top = paddingTop + row * ch
        return Rect(left, top, left + cw, top + ch)
    }

    /** Where a block of cells is (a widget's area), in this view's own coordinates. */
    fun blockBounds(col: Int, row: Int, spanX: Int, spanY: Int): Rect {
        val cw = cellWidth(width)
        val ch = cellHeight(height)
        val left = paddingLeft + col * cw
        val top = paddingTop + row * ch
        return Rect(left, top, left + spanX * cw, top + spanY * ch)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = View.MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = cellWidth(w)
        val ch = cellHeight(h)
        val gap = widgetGap
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val cell = child.layoutParams as CellParams
            if (cell.widget) {
                val blockW = (cell.spanX * cw - 2 * gap).coerceAtLeast(0)
                val blockH = (cell.spanY * ch - 2 * gap).coerceAtLeast(0)
                child.measure(
                    View.MeasureSpec.makeMeasureSpec(blockW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(blockH, View.MeasureSpec.EXACTLY)
                )
            } else {
                child.measure(
                    View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.AT_MOST)
                )
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellWidth(r - l)
        val ch = cellHeight(b - t)
        val gap = widgetGap
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val cell = child.layoutParams as CellParams
            if (cell.widget) {
                val left = paddingLeft + cell.col * cw + gap
                val top = paddingTop + cell.row * ch + gap
                child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            } else {
                val left = paddingLeft + cell.col * cw
                val top = paddingTop + cell.row * ch + (ch - child.measuredHeight) / 2
                child.layout(left, top, left + cw, top + child.measuredHeight)
            }
        }
    }
}
