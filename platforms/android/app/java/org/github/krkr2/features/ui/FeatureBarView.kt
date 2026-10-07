package org.github.krkr2.features.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Small floating, draggable, collapsible control bar for the feature layer. The handle can
 * be dragged to reposition the bar and tapped to collapse/expand the feature buttons. It
 * only occupies its own bounds, so the rest of the screen stays interactive.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FeatureBarView(
    context: Context,
    callbacks: Callbacks,
) : LinearLayout(context) {

    interface Callbacks {
        fun onToggleTranslator()
        fun onEditTranslatorRegion()
        fun onToggleAutoClicker()
        fun onEditAutoClicker()
    }

    private val content: LinearLayout
    private val translatorBtn: TextView
    private val clickerBtn: TextView

    init {
        orientation = HORIZONTAL
        background = pill(0xCC202028.toInt())
        val padH = dp(4); val padV = dp(4)
        setPadding(padH, padV, padH, padV)

        val handle = makeButton("\u2630", 0x00000000) // hamburger; transparent bg
        content = LinearLayout(context).apply { orientation = HORIZONTAL }

        translatorBtn = makeButton("\uD83C\uDF10 T", INACTIVE) // globe + T
        val translatorCfg = makeButton("\u2699 T", INACTIVE)   // gear + T
        clickerBtn = makeButton("\u25B6", INACTIVE)            // play
        val clickerCfg = makeButton("\u2699 \u25B6", INACTIVE) // gear + play

        translatorBtn.setOnClickListener { callbacks.onToggleTranslator() }
        translatorCfg.setOnClickListener { callbacks.onEditTranslatorRegion() }
        clickerBtn.setOnClickListener { callbacks.onToggleAutoClicker() }
        clickerCfg.setOnClickListener { callbacks.onEditAutoClicker() }

        content.addView(translatorBtn)
        content.addView(translatorCfg)
        content.addView(clickerBtn)
        content.addView(clickerCfg)

        addView(handle)
        addView(content)

        attachDragAndCollapse(handle)
    }

    fun setTranslatorActive(active: Boolean) {
        translatorBtn.background = pill(if (active) ACTIVE else INACTIVE)
    }

    fun setClickerActive(active: Boolean) {
        clickerBtn.background = pill(if (active) ACTIVE else INACTIVE)
    }

    private fun attachDragAndCollapse(handle: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startTransX = 0f
        var startTransY = 0f
        var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX; downRawY = e.rawY
                    startTransX = translationX; startTransY = translationY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    translationX = startTransX + dx
                    translationY = startTransY + dy
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) content.visibility =
                        if (content.visibility == VISIBLE) GONE else VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun makeButton(label: String, bgColor: Int): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            val p = dp(8)
            setPadding(p, dp(6), p, dp(6))
            background = pill(bgColor)
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(3), 0, dp(3), 0)
            layoutParams = lp
        }
    }

    private fun pill(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val touchSlop = dp(6).toFloat()

    companion object {
        private val ACTIVE = Color.parseColor("#FF4081")
        private val INACTIVE = Color.parseColor("#40506080")
    }
}
