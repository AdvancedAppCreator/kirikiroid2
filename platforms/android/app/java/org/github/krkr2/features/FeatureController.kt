package org.github.krkr2.features

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import org.github.krkr2.features.autoclicker.AutoClicker
import org.github.krkr2.features.autoclicker.ClickPointEditorView
import org.github.krkr2.features.translator.GameTextOverlayView
import org.github.krkr2.features.translator.OcrScript
import org.github.krkr2.features.translator.RegionSelectorView
import org.github.krkr2.features.translator.ScreenTranslator
import org.github.krkr2.features.ui.FeatureBarView
import org.tvp.kirikiri2.KR2Activity

/**
 * Owns and coordinates the on-screen feature layer (translator + auto-clicker) for a game
 * session. Created from the game activity after its content view exists; forwards lifecycle
 * events so features pause with the app.
 */
class FeatureController(private val activity: KR2Activity) : FeatureBarView.Callbacks {

    private val root: FrameLayout = activity.mFrameLayout
    private val surface = activity.getGLSurfaceView()
    private val prefs = FeaturePrefs.get(activity)

    private val overlay = GameTextOverlayView(activity)
    private val bar = FeatureBarView(activity, this)

    private val autoClicker = AutoClicker(surface)
    private val translator = ScreenTranslator(surface, overlay) { msg -> toast(msg) }

    private var wasTranslatingBeforePause = false

    fun attach() {
        // Subtitle overlay sits above the GL surface but below the control bar, and never
        // consumes touches, so gameplay is unaffected.
        overlay.visibility = View.GONE
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(8)
            leftMargin = dp(8)
        }
        root.addView(bar, barParams)

        applyConfig()
    }

    private fun applyConfig() {
        autoClicker.xFraction = prefs.getFloat(FeaturePrefs.KEY_CLICK_X, FeaturePrefs.DEF_CLICK_X)
        autoClicker.yFraction = prefs.getFloat(FeaturePrefs.KEY_CLICK_Y, FeaturePrefs.DEF_CLICK_Y)
        autoClicker.intervalMs = prefs.getInt(FeaturePrefs.KEY_CLICK_INTERVAL, FeaturePrefs.DEF_CLICK_INTERVAL)

        translator.setScript(parseScript(prefs.getString(FeaturePrefs.KEY_TR_SCRIPT, FeaturePrefs.DEF_TR_SCRIPT)))
        translator.setLanguages(
            prefs.getString(FeaturePrefs.KEY_TR_SOURCE, FeaturePrefs.DEF_TR_SOURCE),
            prefs.getString(FeaturePrefs.KEY_TR_TARGET, FeaturePrefs.DEF_TR_TARGET),
        )
        translator.setRegion(
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_L, FeaturePrefs.DEF_REGION_L),
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_T, FeaturePrefs.DEF_REGION_T),
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_R, FeaturePrefs.DEF_REGION_R),
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_B, FeaturePrefs.DEF_REGION_B),
        )
    }

    // ---- FeatureBarView.Callbacks ----

    override fun onToggleTranslator() {
        if (translator.isRunning) {
            translator.stop()
            bar.setTranslatorActive(false)
        } else {
            translator.start()
            bar.setTranslatorActive(true)
            toast("Translator on")
        }
    }

    override fun onEditTranslatorRegion() {
        val editor = RegionSelectorView(
            activity,
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_L, FeaturePrefs.DEF_REGION_L),
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_T, FeaturePrefs.DEF_REGION_T),
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_R, FeaturePrefs.DEF_REGION_R),
            prefs.getFloat(FeaturePrefs.KEY_TR_REGION_B, FeaturePrefs.DEF_REGION_B),
            onDone = { l, t, r, b ->
                prefs.putFloat(FeaturePrefs.KEY_TR_REGION_L, l)
                prefs.putFloat(FeaturePrefs.KEY_TR_REGION_T, t)
                prefs.putFloat(FeaturePrefs.KEY_TR_REGION_R, r)
                prefs.putFloat(FeaturePrefs.KEY_TR_REGION_B, b)
                translator.setRegion(l, t, r, b)
            },
            onRemove = { v -> root.removeView(v) },
        )
        addModal(editor)
    }

    override fun onToggleAutoClicker() {
        if (autoClicker.isRunning) {
            autoClicker.stop()
            bar.setClickerActive(false)
        } else {
            autoClicker.start()
            bar.setClickerActive(true)
            toast("Auto-clicker on")
        }
    }

    override fun onEditAutoClicker() {
        val editor = ClickPointEditorView(
            activity,
            prefs.getFloat(FeaturePrefs.KEY_CLICK_X, FeaturePrefs.DEF_CLICK_X),
            prefs.getFloat(FeaturePrefs.KEY_CLICK_Y, FeaturePrefs.DEF_CLICK_Y),
            prefs.getInt(FeaturePrefs.KEY_CLICK_INTERVAL, FeaturePrefs.DEF_CLICK_INTERVAL),
            onDone = { x, y, interval ->
                prefs.putFloat(FeaturePrefs.KEY_CLICK_X, x)
                prefs.putFloat(FeaturePrefs.KEY_CLICK_Y, y)
                prefs.putInt(FeaturePrefs.KEY_CLICK_INTERVAL, interval)
                autoClicker.xFraction = x
                autoClicker.yFraction = y
                autoClicker.intervalMs = interval
            },
            onRemove = { v -> root.removeView(v) },
        )
        addModal(editor)
    }

    // ---- lifecycle ----

    fun onPause() {
        wasTranslatingBeforePause = translator.isRunning
        translator.stop()
        autoClicker.stop()
        bar.setClickerActive(false)
        bar.setTranslatorActive(false)
    }

    fun onResume() {
        if (wasTranslatingBeforePause) {
            translator.start()
            bar.setTranslatorActive(true)
        }
    }

    fun onDestroy() {
        translator.release()
        autoClicker.stop()
    }

    private fun addModal(view: View) {
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun parseScript(name: String): OcrScript =
        runCatching { OcrScript.valueOf(name) }.getOrDefault(OcrScript.JAPANESE)

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
}
