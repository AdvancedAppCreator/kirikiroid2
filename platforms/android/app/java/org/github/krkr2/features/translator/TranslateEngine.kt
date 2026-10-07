package org.github.krkr2.features.translator

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * On-device translation via ML Kit NL Translate (offline once models are downloaded).
 *
 * Supports a fixed source language or "auto" (source detected per-text with ML Kit
 * language identification). Translators are cached per source language. Language models
 * are downloaded on demand; [prepareModels] pre-fetches the current pair.
 */
class TranslateEngine(
    sourceTag: String,
    targetTag: String,
) {
    @Volatile var sourceTag: String = sourceTag
        private set
    @Volatile var targetTag: String = targetTag
        private set

    private val autoSource: Boolean get() = sourceTag.equals("auto", ignoreCase = true)

    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()
    private val translators = HashMap<String, Translator>() // keyed by ML Kit source language code

    /** Reconfigure languages; disposes translators that no longer match. */
    @Synchronized
    fun configure(newSource: String, newTarget: String) {
        if (newSource == sourceTag && newTarget == targetTag) return
        sourceTag = newSource
        targetTag = newTarget
        closeTranslators()
    }

    /**
     * Download the models needed for the current configuration. Returns null on success
     * or a human-readable error message on failure (e.g. no network on first run).
     */
    fun prepareModels(): String? {
        return try {
            if (TranslateLanguage.fromLanguageTag(targetTag) == null) {
                return "Unsupported target language: $targetTag"
            }
            if (!autoSource) {
                val src = TranslateLanguage.fromLanguageTag(sourceTag)
                    ?: return "Unsupported source language: $sourceTag"
                getOrCreateTranslator(src)?.let { Tasks.await(it.downloadModelIfNeeded(conditions())) }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "prepareModels failed", e)
            "Translation model download failed (need network on first run)"
        }
    }

    /** Translate [text] synchronously (must be called off the main thread). */
    fun translate(text: String, cache: TranslationCache): String? {
        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return null

        val srcCode: String = if (autoSource) {
            val detected = Tasks.await(languageIdentifier.identifyLanguage(text))
            if (detected == "und") return null
            TranslateLanguage.fromLanguageTag(detected) ?: return null
        } else {
            TranslateLanguage.fromLanguageTag(sourceTag) ?: return null
        }

        if (srcCode == target) return text // already in the target language

        cache.get(text, srcCode, target)?.let { return it }

        val translator = getOrCreateTranslator(srcCode) ?: return null
        Tasks.await(translator.downloadModelIfNeeded(conditions()))
        val out = Tasks.await(translator.translate(text))
        cache.put(text, srcCode, target, out)
        return out
    }

    @Synchronized
    private fun getOrCreateTranslator(sourceCode: String): Translator? {
        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return null
        translators[sourceCode]?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(target)
            .build()
        val t = Translation.getClient(options)
        translators[sourceCode] = t
        return t
    }

    @Synchronized
    private fun closeTranslators() {
        translators.values.forEach { it.close() }
        translators.clear()
    }

    @Synchronized
    fun close() {
        closeTranslators()
        languageIdentifier.close()
    }

    private fun conditions() = DownloadConditions.Builder().build()

    companion object {
        private const val TAG = "TranslateEngine"
    }
}
