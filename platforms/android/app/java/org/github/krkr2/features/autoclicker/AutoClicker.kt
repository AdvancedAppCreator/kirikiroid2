package org.github.krkr2.features.autoclicker

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View

/**
 * Auto-clicker: repeatedly injects a single tap at one fixed point.
 *
 * The tap is delivered as a real [MotionEvent] pair (DOWN then UP) dispatched to the
 * cocos2d GL surface view, i.e. through the exact same path a physical finger tap takes.
 * This is the correct native mechanism (no input-injection hacks, no private APIs) and
 * guarantees the engine treats the synthetic tap identically to a user tap.
 *
 * Timing is a fixed interval driven by a main-thread [Handler]; each cycle is
 * DOWN, [HOLD_MS] hold, UP, then wait (interval - HOLD_MS).
 */
class AutoClicker(private val target: View) {

    private val handler = Handler(Looper.getMainLooper())

    /** Click location as a fraction (0..1) of the surface size. */
    @Volatile var xFraction: Float = 0.5f
    @Volatile var yFraction: Float = 0.85f
    /** Interval between taps in milliseconds (clamped to [MIN_INTERVAL_MS]). */
    @Volatile var intervalMs: Int = 1000
        set(value) { field = value.coerceAtLeast(MIN_INTERVAL_MS) }

    var isRunning: Boolean = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            performTap()
            handler.postDelayed(this, intervalMs.toLong())
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        handler.post(tick)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(tick)
    }

    private fun performTap() {
        val w = target.width
        val h = target.height
        if (w <= 0 || h <= 0) return
        val x = (xFraction.coerceIn(0f, 1f)) * w
        val y = (yFraction.coerceIn(0f, 1f)) * h
        Log.d(TAG, "tap at ($x, $y) interval=$intervalMs")

        val down = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        target.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        handler.postDelayed({
            val up = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, x, y, 0)
            target.dispatchTouchEvent(upEvent)
            upEvent.recycle()
        }, HOLD_MS)
    }

    companion object {
        private const val TAG = "AutoClicker"
        const val MIN_INTERVAL_MS = 50
        private const val HOLD_MS = 30L
    }
}
