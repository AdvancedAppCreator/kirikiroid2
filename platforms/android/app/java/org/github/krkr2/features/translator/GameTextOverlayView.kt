package org.github.krkr2.features.translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

/** A translated string positioned at the original text's on-screen bounding box. */
data class TranslatedItem(val text: String, val screenBox: Rect)

/**
 * Draws translated strings as subtitle-like boxes on top of the game, each positioned at
 * the original text's screen bounding box. This view never consumes touches, so the game
 * stays fully playable while translations are shown.
 */
class GameTextOverlayView(context: Context) : View(context) {

    private var items: List<TranslatedItem> = emptyList()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6101014.toInt()
        style = Paint.Style.FILL
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    init {
        // Presentation-only overlay: not focusable/clickable so touches fall through to
        // the game surface underneath.
        isClickable = false
        isFocusable = false
    }

    fun setItems(newItems: List<TranslatedItem>) {
        items = newItems
        postInvalidateOnAnimation()
    }

    fun clear() {
        if (items.isEmpty()) return
        items = emptyList()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        val pad = dp(6f)
        for (item in items) {
            val box = item.screenBox
            if (box.width() <= 0) continue

            val textSize = (box.height().toFloat() * 0.7f).coerceIn(dp(12f), dp(30f))
            textPaint.textSize = textSize
            val availWidth = (box.width() + pad * 2).toInt().coerceAtLeast(dp(40f).toInt())

            val layout = StaticLayout.Builder
                .obtain(item.text, 0, item.text.length, textPaint, availWidth)
                .build()

            val left = box.left.toFloat()
            var top = box.top.toFloat() - pad
            val bgRect = RectF(
                left - pad,
                top - pad,
                left + layout.width + pad,
                top + layout.height + pad,
            )
            // Keep the box on screen.
            val dx = when {
                bgRect.right > width -> width - bgRect.right
                bgRect.left < 0 -> -bgRect.left
                else -> 0f
            }
            bgRect.offset(dx, 0f)
            if (bgRect.bottom > height) {
                val dy = height - bgRect.bottom
                bgRect.offset(0f, dy)
                top += dy
            }

            canvas.drawRoundRect(bgRect, dp(6f), dp(6f), bgPaint)
            canvas.save()
            canvas.translate(bgRect.left + pad, bgRect.top + pad)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
