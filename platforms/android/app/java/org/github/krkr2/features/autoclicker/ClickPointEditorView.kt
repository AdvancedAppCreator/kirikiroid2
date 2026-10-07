package org.github.krkr2.features.autoclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Full-screen modal overlay for configuring the auto-clicker: a draggable crosshair sets
 * the click point (stored as a fraction of the surface) and a slider sets the interval.
 * While shown it consumes all touches; "Done" persists the values and removes the overlay.
 */
class ClickPointEditorView(
    context: Context,
    private var xFraction: Float,
    private var yFraction: Float,
    private var intervalMs: Int,
    private val onDone: (x: Float, y: Float, interval: Int) -> Unit,
    private val onRemove: (View) -> Unit,
) : FrameLayout(context) {

    private val crosshair = CrosshairView(context)

    init {
        setBackgroundColor(0x66000000) // dim so the crosshair stands out
        isClickable = true

        addView(crosshair, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC202020.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val panelParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(24)
        }

        val title = TextView(context).apply {
            text = "Drag the crosshair to set the auto-click point"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val intervalLabel = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        fun updateIntervalLabel() { intervalLabel.text = "Interval: ${intervalMs} ms" }
        updateIntervalLabel()

        val slider = SeekBar(context).apply {
            max = MAX_INTERVAL - AutoClicker.MIN_INTERVAL_MS
            progress = intervalMs.coerceIn(AutoClicker.MIN_INTERVAL_MS, MAX_INTERVAL) - AutoClicker.MIN_INTERVAL_MS
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    intervalMs = progress + AutoClicker.MIN_INTERVAL_MS
                    updateIntervalLabel()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val doneBtn = Button(context).apply {
            text = "Done"
            setOnClickListener {
                onDone(xFraction, yFraction, intervalMs)
                onRemove(this@ClickPointEditorView)
            }
        }

        panel.addView(title)
        panel.addView(intervalLabel)
        panel.addView(slider, LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(doneBtn)
        addView(panel, panelParams)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val w = width
                val h = height
                if (w > 0 && h > 0) {
                    xFraction = (event.x / w).coerceIn(0f, 1f)
                    yFraction = (event.y / h).coerceIn(0f, 1f)
                    crosshair.invalidate()
                }
                return true
            }
        }
        return true
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private inner class CrosshairView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF4081")
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x554081FF
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            val cx = xFraction * width
            val cy = yFraction * height
            val r = dp(18).toFloat()
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx - r * 1.6f, cy, cx + r * 1.6f, cy, paint)
            canvas.drawLine(cx, cy - r * 1.6f, cx, cy + r * 1.6f, paint)
        }
    }

    companion object {
        private const val MAX_INTERVAL = 5000
    }
}
