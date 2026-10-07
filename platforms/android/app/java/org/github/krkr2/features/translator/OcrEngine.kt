package org.github.krkr2.features.translator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Script family selecting which bundled ML Kit recognizer to use. */
enum class OcrScript { LATIN, JAPANESE, CHINESE, KOREAN, DEVANAGARI }

/** A recognised text block and its bounding box, in the coordinate space of the OCR bitmap. */
data class OcrBlock(val text: String, val box: Rect)

/**
 * Wraps ML Kit on-device text recognition. The recognizer is created lazily for the
 * configured [OcrScript] and rebuilt only when the script changes.
 */
class OcrEngine {

    private var script: OcrScript? = null
    private var recognizer: TextRecognizer? = null

    @Synchronized
    fun setScript(newScript: OcrScript) {
        if (newScript == script && recognizer != null) return
        recognizer?.close()
        recognizer = when (newScript) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
        script = newScript
    }

    /** Runs OCR synchronously (must be called off the main thread). */
    fun recognize(bitmap: Bitmap): List<OcrBlock> {
        val rec = recognizer ?: return emptyList()
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(rec.process(image))
        val blocks = ArrayList<OcrBlock>()
        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            val text = block.text.trim()
            if (text.isNotEmpty()) blocks.add(OcrBlock(text, Rect(box)))
        }
        return blocks
    }

    @Synchronized
    fun close() {
        recognizer?.close()
        recognizer = null
        script = null
    }
}
