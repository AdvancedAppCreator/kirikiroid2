package org.github.krkr2.features

import android.content.Context
import android.content.SharedPreferences
import org.tvp.kirikiri2.KR2Activity

/**
 * Persistent settings for the on-screen feature layer (translator + auto-clicker).
 *
 * Values are stored in a single [SharedPreferences] file. Keys are namespaced by the
 * launched game's identity when it is known (external launch), so per-game overrides
 * are kept separate; when no game identity is available (e.g. the in-engine file selector)
 * a shared "default" scope is used.
 */
class FeaturePrefs private constructor(private val sp: SharedPreferences, private val scope: String) {

    private fun key(name: String) = "$scope::$name"

    fun getBool(name: String, def: Boolean) = sp.getBoolean(key(name), def)
    fun putBool(name: String, value: Boolean) = sp.edit().putBoolean(key(name), value).apply()

    fun getInt(name: String, def: Int) = sp.getInt(key(name), def)
    fun putInt(name: String, value: Int) = sp.edit().putInt(key(name), value).apply()

    fun getFloat(name: String, def: Float) = sp.getFloat(key(name), def)
    fun putFloat(name: String, value: Float) = sp.edit().putFloat(key(name), value).apply()

    fun getString(name: String, def: String): String = sp.getString(key(name), def) ?: def
    fun putString(name: String, value: String) = sp.edit().putString(key(name), value).apply()

    companion object {
        private const val FILE = "krkr2_features"

        // Auto-clicker
        const val KEY_CLICK_X = "clicker_x"          // fraction 0..1 of surface width
        const val KEY_CLICK_Y = "clicker_y"          // fraction 0..1 of surface height
        const val KEY_CLICK_INTERVAL = "clicker_interval_ms"

        // Translator
        const val KEY_TR_SOURCE = "tr_source_lang"   // BCP-47 or "auto"
        const val KEY_TR_TARGET = "tr_target_lang"   // BCP-47
        const val KEY_TR_SCRIPT = "tr_ocr_script"    // OcrScript name
        const val KEY_TR_REGION_L = "tr_region_left"
        const val KEY_TR_REGION_T = "tr_region_top"
        const val KEY_TR_REGION_R = "tr_region_right"
        const val KEY_TR_REGION_B = "tr_region_bottom"

        // Defaults tuned for the target use case (Japanese visual novels -> English).
        const val DEF_CLICK_X = 0.5f
        const val DEF_CLICK_Y = 0.85f
        const val DEF_CLICK_INTERVAL = 1000
        const val MIN_CLICK_INTERVAL = 50
        const val DEF_TR_SOURCE = "ja"
        const val DEF_TR_TARGET = "en"
        const val DEF_TR_SCRIPT = "JAPANESE"
        const val DEF_REGION_L = 0.06f
        const val DEF_REGION_T = 0.62f
        const val DEF_REGION_R = 0.94f
        const val DEF_REGION_B = 0.97f

        fun get(context: Context): FeaturePrefs {
            val sp = context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val identity = KR2Activity.getGameIdentity()
            val scope = if (identity.isNullOrEmpty()) "default" else sanitize(identity)
            return FeaturePrefs(sp, scope)
        }

        private fun sanitize(id: String): String {
            // Keep keys stable and file-safe; a hash keeps very long paths bounded.
            return "g" + Integer.toHexString(id.hashCode())
        }
    }
}
