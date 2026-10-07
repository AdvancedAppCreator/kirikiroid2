package org.github.krkr2.features.translator

import android.graphics.Rect
import android.util.Log
import android.view.SurfaceView
import org.github.krkr2.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the on-screen translator: continuously captures the configured region, runs OCR,
 * translates each block on-device, and updates the overlay. Unchanged frames are skipped
 * via a content signature, and repeated lines are served from the translation cache.
 */
class ScreenTranslator(
    private val surface: SurfaceView,
    private val overlay: GameTextOverlayView,
    private val onStatus: (String) -> Unit,
) {
    private val capturer = FrameCapturer()
    private val ocr = OcrEngine()
    private val cache = TranslationCache()
    private var translate = TranslateEngine("ja", "en")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var released = false

    @Volatile private var region = floatArrayOf(0.06f, 0.62f, 0.94f, 0.97f)
    @Volatile private var lastSignature: String = ""

    var isRunning: Boolean = false
        private set

    fun setScript(script: OcrScript) = ocr.setScript(script)

    fun setLanguages(source: String, target: String) {
        translate.configure(source, target)
        lastSignature = "" // force re-translation with the new languages
    }

    fun setRegion(l: Float, t: Float, r: Float, b: Float) {
        region = floatArrayOf(l, t, r, b)
        lastSignature = ""
    }

    fun start() {
        if (isRunning || released) return
        isRunning = true
        overlay.visibility = android.view.View.VISIBLE
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()
            if (!isActive || !isRunning) return@launch
            translate.prepareModels()?.let { msg -> withContext(Dispatchers.Main) { onStatus(msg) } }
            loop()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job?.cancel()
        job = null
        lastSignature = ""
        overlay.clear()
        overlay.visibility = android.view.View.GONE
    }

    private suspend fun loop() {
        while (scope.isActive && isRunning) {
            val started = System.currentTimeMillis()
            try {
                processOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "translate cycle failed", e)
            }
            val elapsed = System.currentTimeMillis() - started
            val wait = THROTTLE_MS - elapsed
            if (wait > 0) delay(wait)
        }
    }

    private suspend fun processOnce() {
        val reg = region
        val cap = capturer.captureRegion(surface, reg[0], reg[1], reg[2], reg[3]) ?: return
        val (frame, screenRegion) = cap
        val blocks: List<OcrBlock>
        try {
            blocks = ocr.recognize(frame.bitmap)
        } finally {
            frame.bitmap.recycle()
        }
        Log.d(TAG, "capture ok region=$screenRegion ocrBlocks=${blocks.size}")

        val signature = blocks.joinToString("\n") { it.text }
        if (signature == lastSignature) return
        lastSignature = signature

        if (blocks.isEmpty()) {
            withContext(Dispatchers.Main) { overlay.clear() }
            return
        }

        val items = ArrayList<TranslatedItem>(blocks.size)
        for (block in blocks) {
            val translated = translate.translate(block.text, cache) ?: continue
            if (BuildConfig.DEBUG) Log.i(TAG, "translate: '${block.text}' -> '$translated'")
            items.add(TranslatedItem(translated, mapBox(block.box, frame.scale, screenRegion)))
        }
        withContext(Dispatchers.Main) { overlay.setItems(items) }
    }

    /** Map an OCR bounding box (processed-bitmap px) back to on-screen surface px. */
    private fun mapBox(box: Rect, scale: Float, region: Rect): Rect {
        val inv = if (scale != 0f) 1f / scale else 1f
        return Rect(
            region.left + (box.left * inv).toInt(),
            region.top + (box.top * inv).toInt(),
            region.left + (box.right * inv).toInt(),
            region.top + (box.bottom * inv).toInt(),
        )
    }

    fun release() {
        if (released) return
        released = true
        isRunning = false
        val previous = job
        previous?.cancel()
        job = scope.launch {
            previous?.cancelAndJoin()
            capturer.release()
            ocr.close()
            translate.close()
            scope.cancel()
        }
    }

    companion object {
        private const val TAG = "ScreenTranslator"
        private const val THROTTLE_MS = 700L
    }
}
