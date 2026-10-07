package org.github.krkr2.features.translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen modal overlay to drag out the translation capture region. The region is
 * stored as fractions (0..1) of the surface so it is resolution independent. Consumes all
 * touches while shown; "Done" persists the region and removes the overlay.
 */
class RegionSelectorView(
    context: Context,
    left: Float, top: Float, right: Float, bottom: Float,
    private val onDone: (l: Float, t: Float, r: Float, b: Float) -> Unit,
    private val onRemove: (View) -> Unit,
) : FrameLayout(context) {

    // Current selection in fractions.
    private var fl = left
    private var ft = top
    private var fr = right
    private var fb = bottom

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragging = false

    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x224081FF
        style = Paint.Style.FILL
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
        isClickable = true

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC202020.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val panelParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(24)
        }
        panel.addView(TextView(context).apply {
            text = "Drag to select the text region to translate"
            setTextColor(Color.WHITE)
            textSize = 14f
        })
        panel.addView(Button(context).apply {
            text = "Done"
            setOnClickListener {
                val l = minOf(fl, fr); val r = maxOf(fl, fr)
                val t = minOf(ft, fb); val b = maxOf(ft, fb)
                if (r - l < MIN_REGION_FRACTION || b - t < MIN_REGION_FRACTION) {
                    Toast.makeText(
                        context,
                        "Drag a larger text region before saving",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@setOnClickListener
                }
                onDone(l, t, r, b)
                onRemove(this@RegionSelectorView)
            }
        })
        addView(panel, panelParams)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragStartY = event.y
                dragging = true
                fl = event.x / w; ft = event.y / h
                fr = fl; fb = ft
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    fl = (dragStartX / w).coerceIn(0f, 1f)
                    ft = (dragStartY / h).coerceIn(0f, 1f)
                    fr = (event.x / w).coerceIn(0f, 1f)
                    fb = (event.y / h).coerceIn(0f, 1f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val l = minOf(fl, fr) * w
        val t = minOf(ft, fb) * h
        val r = maxOf(fl, fr) * w
        val b = maxOf(ft, fb) * h

        // Dim everything outside the selection.
        canvas.drawRect(0f, 0f, w, t, dimPaint)
        canvas.drawRect(0f, b, w, h, dimPaint)
        canvas.drawRect(0f, t, l, b, dimPaint)
        canvas.drawRect(r, t, w, b, dimPaint)

        val sel = RectF(l, t, r, b)
        canvas.drawRect(sel, fillPaint)
        canvas.drawRect(sel, selPaint)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MIN_REGION_FRACTION = 0.02f
    }
}
