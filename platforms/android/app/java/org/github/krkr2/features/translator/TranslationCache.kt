package org.github.krkr2.features.translator

import android.util.LruCache

/**
 * Caches translated strings keyed on (source text + language pair + engine version), so
 * repeated/unchanged lines in continuous mode are not re-translated. Keeping the engine
 * version in the key invalidates the cache automatically if the ML Kit version changes.
 */
class TranslationCache(maxEntries: Int = 500) {

    private val cache = LruCache<String, String>(maxEntries)

    private fun key(source: String, from: String, to: String): String =
        "$from|$to|$ENGINE_VERSION|$source"

    fun get(source: String, from: String, to: String): String? =
        cache.get(key(source, from, to))

    fun put(source: String, from: String, to: String, translation: String) {
        cache.put(key(source, from, to), translation)
    }

    companion object {
        const val ENGINE_VERSION = "mlkit-translate-17.0.3"
    }
}
