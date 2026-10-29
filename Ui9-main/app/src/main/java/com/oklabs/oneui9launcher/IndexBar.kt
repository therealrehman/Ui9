package com.oklabs.oneui9launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * A-Z fast-scroll strip on the right edge of the Apps screen.
 * The view is wide so the letter bubble has room, but only the right edge reacts to touch -
 * everything else falls through to the app grid underneath.
 */
class IndexBar(context: Context) : View(context) {

    /** Called with the letter the finger is on (always a letter that has apps). */
    var onLetter: ((Char) -> Unit)? = null

    private val letters: List<Char> = ('A'..'Z').toList() + '#'
    private var present: Set<Char> = emptySet()
    private var current: Char? = null
    private var picked: Char? = null
    private var touching = false
    private var touchY = 0f

    private val hitWidth = context.dpf(30f)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 34, 38, 50)
    }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = context.dpf(28f)
    }
    private val bubbleRect = RectF()

    /** Which letters have at least one app. */
    fun setLetters(available: Collection<Char>) {
        present = available.toSet()
        invalidate()
    }

    /** Highlights the letter of the first visible app while the list scrolls. */
    fun setCurrent(letter: Char?) {
        if (touching || letter == current) return
        current = letter
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val slot = height / letters.size.toFloat()
        textPaint.textSize = min(context.dpf(10.5f), slot * 0.66f)
        val cx = width - hitWidth / 2f
        val highlighted = if (touching) picked else current

        for (i in letters.indices) {
            val ch = letters[i]
            textPaint.color = when {
                ch == highlighted -> Look.text
                ch in present -> Look.textA(185)
                else -> Look.textA(60)
            }
            textPaint.isFakeBoldText = ch == highlighted
            val cy = slot * i + slot / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(ch.toString(), cx, cy, textPaint)
        }

        val bubbleLetter = picked
        if (touching && bubbleLetter != null) {
            val size = context.dpf(56f)
            val left = cx - hitWidth / 2f - context.dpf(14f) - size
            val top = (touchY - size / 2f).coerceIn(0f, max(0f, height - size))
            bubbleRect.set(left, top, left + size, top + size)
            canvas.drawRoundRect(bubbleRect, size / 2.6f, size / 2.6f, bubblePaint)
            val baseline = bubbleRect.centerY() - (bubbleText.ascent() + bubbleText.descent()) / 2f
            canvas.drawText(bubbleLetter.toString(), bubbleRect.centerX(), baseline, bubbleText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x < width - hitWidth) return false
                touching = true
                parent?.requestDisallowInterceptTouchEvent(true)
                pick(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touching) {
                    pick(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touching) {
                    touching = false
                    current = picked
                    picked = null
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    /** Finds the letter under the finger, or the closest letter that has apps. */
    private fun pick(y: Float) {
        touchY = y
        if (height <= 0) return
        val raw = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        var best = -1
        for (d in 0 until letters.size) {
            val before = raw - d
            val after = raw + d
            if (before >= 0 && letters[before] in present) {
                best = before
                break
            }
            if (after < letters.size && letters[after] in present) {
                best = after
                break
            }
        }
        if (best >= 0) {
            val letter = letters[best]
            if (letter != picked) {
                picked = letter
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onLetter?.invoke(letter)
            }
        }
        invalidate()
    }
}
