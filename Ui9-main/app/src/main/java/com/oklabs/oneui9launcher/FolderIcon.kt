package com.oklabs.oneui9launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Draws the folder tile of the home screen: a soft glass squircle with up to four small app icons inside (2 x 2). */
object FolderIcon {

    /** A square [size] x [size] bitmap, ready to be used like any app icon. */
    fun render(icons: List<Bitmap>, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()

        val plate = Paint(Paint.ANTI_ALIAS_FLAG)
        plate.color = Color.argb(96, 255, 255, 255)
        canvas.drawRoundRect(RectF(0f, 0f, s, s), s * 0.30f, s * 0.30f, plate)

        val pad = s * 0.14f
        val gap = s * 0.07f
        val mini = (s - 2f * pad - gap) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val count = minOf(icons.size, 4)
        for (i in 0 until count) {
            val left = pad + (i % 2) * (mini + gap)
            val top = pad + (i / 2) * (mini + gap)
            canvas.drawBitmap(icons[i], null, RectF(left, top, left + mini, top + mini), paint)
        }
        return bitmap
    }
}
