package org.github.krkr2

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import org.tvp.kirikiri2.KR2Activity
import java.io.File

/**
 * Exported entry point for launching a specific Kirikiri game directly,
 * bypassing the in-app file selector.
 *
 * The manifest permission makes callers opt into the public launch contract.
 * This activity validates the action and paths before starting the engine.
 *
 * This activity never shows UI; it finishes immediately after handing control
 * to [MainActivity].
 */
class LaunchGameActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleLaunch()
        } catch (_: Exception) {
            reject(LaunchContract.ERR_INTERNAL, "Unable to process launch request")
        }
    }

    private fun handleLaunch() {
        if (intent.action != LaunchContract.ACTION_LAUNCH_GAME) {
            reject(LaunchContract.ERR_UNSUPPORTED, "Unsupported launch action")
            return
        }

        val gameId = intent.getStringExtra(LaunchContract.EXTRA_GAME_ID)
        val gamePath = intent.getStringExtra(LaunchContract.EXTRA_GAME_PATH)
        val startupFile = intent.getStringExtra(LaunchContract.EXTRA_STARTUP_FILE)

        if (gameId.isNullOrEmpty()) {
            reject(LaunchContract.ERR_UNSUPPORTED, "Missing game_id")
            return
        }
        if (gamePath.isNullOrEmpty()) {
            reject(LaunchContract.ERR_GAME_NOT_FOUND, "Missing game_path", gameId)
            return
        }
        if (KR2Activity.isGameActive()) {
            reject(LaunchContract.ERR_BUSY, "A game is already running", gameId)
            return
        }

        // We rely on all-files access to read arbitrary game folders.
        if (!hasStorageAccess()) {
            reject(
                LaunchContract.ERR_PERMISSION_REQUIRED,
                "All-files access not granted to the player",
                gameId
            )
            return
        }

        val launch = when (val r = GameResolver.resolve(gamePath, startupFile)) {
            is GameResolver.Result.Ok -> r
            is GameResolver.Result.Error -> {
                reject(r.code, r.message, gameId)
                return
            }
        }

        // Hand the path to the native engine and start the player.
        KR2Activity.setStartupPath(launch.bootPath, launch.executablePath)
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(i)

        accept(gameId)
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun accept(gameId: String) {
        val data = Intent().apply {
            putExtra(LaunchContract.EXTRA_SUCCESS, true)
            putExtra(LaunchContract.EXTRA_GAME_ID, gameId)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun reject(errorCode: String, message: String, gameId: String? = null) {
        val data = Intent().apply {
            putExtra(LaunchContract.EXTRA_SUCCESS, false)
            putExtra(LaunchContract.EXTRA_ERROR_CODE, errorCode)
            putExtra(LaunchContract.EXTRA_ERROR_MESSAGE, message)
            if (gameId != null) putExtra(LaunchContract.EXTRA_GAME_ID, gameId)
        }
        // success=false is carried even with RESULT_OK so the caller always gets
        // a structured error.
        setResult(RESULT_OK, data)
        finish()
    }
}
