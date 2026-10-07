package org.github.krkr2

/**
 * Public external-launch contract (v1) for Android game-library apps.
 *
 * Clients start [LaunchGameActivity] for a result with an explicit component
 * and the action below. The activity validates the request, resolves the game,
 * hands the startup path to the native engine, and returns a synchronous result.
 */
object LaunchContract {
    const val API_VERSION = 1

    // Intent action (explicit component is still required; the action is only a
    // secondary, human-readable marker / filter).
    const val ACTION_LAUNCH_GAME = "com.agm.kirikiri.action.LAUNCH_GAME"

    const val PERMISSION_LAUNCH_GAME = "org.github.krkr2.permission.LAUNCH_GAME"

    // Constant class name of the launcher activity.
    const val LAUNCH_ACTIVITY_CLASS = "org.github.krkr2.LaunchGameActivity"

    // Request extras (all String).
    const val EXTRA_GAME_ID = "game_id"        // always present, opaque, not persisted
    const val EXTRA_GAME_PATH = "game_path"    // always present, absolute folder
    const val EXTRA_STARTUP_FILE = "startup_file" // optional absolute file
    const val EXTRA_TITLE = "title"            // optional

    // Result extras.
    const val EXTRA_SUCCESS = "success"        // boolean
    const val EXTRA_ERROR_CODE = "error_code"  // String, one of ERR_*
    const val EXTRA_ERROR_MESSAGE = "error_message" // String

    // Error codes.
    const val ERR_GAME_NOT_FOUND = "GAME_NOT_FOUND"
    const val ERR_STARTUP_NOT_FOUND = "STARTUP_NOT_FOUND"
    const val ERR_UNREADABLE = "UNREADABLE"
    const val ERR_UNSUPPORTED = "UNSUPPORTED"
    const val ERR_BUSY = "BUSY"
    const val ERR_INTERNAL = "INTERNAL"
    const val ERR_PERMISSION_REQUIRED = "PERMISSION_REQUIRED"

    // Capabilities (async exit events / SAF are not implemented in v1).
    const val CAP_EXIT_EVENT = false
    const val CAP_SAF_URI = false

    // Startup files the engine can boot when [EXTRA_STARTUP_FILE] is absent.
    val AUTO_STARTUP_NAMES = listOf("startup.tjs", "data.xp3")
}
