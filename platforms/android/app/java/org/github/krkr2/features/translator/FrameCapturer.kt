package org.github.krkr2.features.translator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** OCR-ready bitmap plus the scale applied to it relative to the source region. */
class ProcessedFrame(val bitmap: Bitmap, val scale: Float)

/**
 * Captures the rendered game frame from the GL [SurfaceView] using the Android [PixelCopy]
 * API. PixelCopy is the correct native way to read back a SurfaceView's rendered content
 * (it copies from the actual surface buffer), avoiding any glReadPixels / GL-thread timing
 * workarounds. The captured frame is cropped to the translation region and lightly
 * preprocessed (upscale + contrast normalization) to improve OCR of small game fonts.
 */
class FrameCapturer {

    private val thread = HandlerThread("krkr2-pixelcopy").apply { start() }
    private val handler = Handler(thread.looper)

    /** Capture the whole surface. The caller owns the returned bitmap. */
    private suspend fun captureSurface(surface: SurfaceView): Bitmap? {
        val w = surface.width
        val h = surface.height
        if (w <= 0 || h <= 0 || !surface.holder.surface.isValid) return null

        val dest = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val ok = suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(surface, dest, { result ->
                    if (result != PixelCopy.SUCCESS) Log.w(TAG, "PixelCopy failed: $result")
                    if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS)
                }, handler)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "PixelCopy request rejected", e)
                if (cont.isActive) cont.resume(false)
            }
        }
        if (!ok) {
            dest.recycle()
            return null
        }
        return dest
    }

    /**
     * Capture and produce an OCR-ready bitmap for the given region (fractions 0..1 of the
     * surface). Returns null if capture failed or the region is empty.
     */
    suspend fun captureRegion(
        surface: SurfaceView,
        left: Float, top: Float, right: Float, bottom: Float,
    ): Pair<ProcessedFrame, Rect>? {
        val src = captureSurface(surface) ?: return null
        val w = src.width
        val h = src.height

        val l = (left.coerceIn(0f, 1f) * w).toInt()
        val t = (top.coerceIn(0f, 1f) * h).toInt()
        val r = (right.coerceIn(0f, 1f) * w).toInt()
        val b = (bottom.coerceIn(0f, 1f) * h).toInt()
        val rectW = (r - l)
        val rectH = (b - t)
        if (rectW < 4 || rectH < 4) {
            src.recycle()
            return null
        }

        val region = Rect(l, t, r, b)
        val crop = try {
            Bitmap.createBitmap(src, l, t, rectW, rectH)
        } finally {
            src.recycle()
        }
        val processed = preprocess(crop)
        if (processed.bitmap !== crop) crop.recycle()
        return Pair(processed, region)
    }

    /** Upscale small crops and boost contrast to help recognition of stroked game fonts. */
    private fun preprocess(crop: Bitmap): ProcessedFrame {
        val minSide = minOf(crop.width, crop.height)
        var scale = 1f
        if (minSide < TARGET_MIN_SIDE) {
            scale = (TARGET_MIN_SIDE.toFloat() / minSide).coerceAtMost(MAX_SCALE)
        }
        val outW = (crop.width * scale).toInt().coerceAtMost(MAX_DIMEN)
        val outH = (crop.height * scale).toInt().coerceAtMost(MAX_DIMEN)
        val effScale = minOf(outW.toFloat() / crop.width, outH.toFloat() / crop.height)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Increase contrast and slightly desaturate to make text edges crisper for OCR.
        val cm = ColorMatrix().apply {
            val c = 1.35f
            val tr = (-0.5f * c + 0.5f) * 255f
            set(floatArrayOf(
                c, 0f, 0f, 0f, tr,
                0f, c, 0f, 0f, tr,
                0f, 0f, c, 0f, tr,
                0f, 0f, 0f, 1f, 0f,
            ))
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val srcRect = Rect(0, 0, crop.width, crop.height)
        val dstRect = Rect(0, 0, outW, outH)
        canvas.drawBitmap(crop, srcRect, dstRect, paint)
        return ProcessedFrame(out, effScale)
    }

    fun release() {
        thread.quitSafely()
    }

    companion object {
        private const val TAG = "FrameCapturer"
        private const val TARGET_MIN_SIDE = 320
        private const val MAX_SCALE = 2.5f
        private const val MAX_DIMEN = 3200
    }
}
