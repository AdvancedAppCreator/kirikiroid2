package org.github.krkr2

import java.io.File

/**
 * Resolves the native boot path for a launch request, mirroring the in-app file
 * selector's behaviour (TVPMainFileSelectorForm::runFromPath):
 *  - a folder containing a loose `startup.tjs` boots the FOLDER
 *  - an `.xp3` archive (e.g. `data.xp3`) boots the ARCHIVE FILE itself
 *  - a matching executable is retained separately for `System.exeName`
 *
 * Shared by [LaunchGameActivity] and unit tests.
 */
object GameResolver {

    sealed class Result {
        data class Ok(
            val bootPath: String,
            val executablePath: String? = null,
        ) : Result()
        data class Error(val code: String, val message: String) : Result()
    }

    /**
     * @param gamePath absolute game folder (required)
     * @param startupFile optional absolute file to boot directly
     */
    fun resolve(gamePath: String?, startupFile: String?): Result {
        if (gamePath.isNullOrEmpty()) {
            return Result.Error(LaunchContract.ERR_GAME_NOT_FOUND, "Missing game_path")
        }
        val gameDir = File(gamePath).canonicalFile
        if (!gameDir.exists() || !gameDir.isDirectory) {
            return Result.Error(LaunchContract.ERR_GAME_NOT_FOUND, "Game folder not found")
        }
        if (!gameDir.canRead()) {
            return Result.Error(LaunchContract.ERR_UNREADABLE, "Game folder not readable")
        }

        val configuredExecutable = gameDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("cf", ignoreCase = true) }
            ?.map { File(gameDir, "${it.nameWithoutExtension}.exe") }
            ?.filter { it.isFile && it.canRead() }
            ?.sortedBy { it.name.lowercase() }
            ?.singleOrNull()

        // Explicit startup file wins.
        if (!startupFile.isNullOrEmpty()) {
            val sf = File(startupFile).canonicalFile
            if (!sf.toPath().startsWith(gameDir.toPath())) {
                return Result.Error(
                    LaunchContract.ERR_UNSUPPORTED,
                    "startup_file must be inside game_path",
                )
            }
            if (!sf.exists()) {
                return Result.Error(LaunchContract.ERR_STARTUP_NOT_FOUND, "startup_file not found")
            }
            if (!sf.canRead()) {
                return Result.Error(LaunchContract.ERR_UNREADABLE, "startup_file not readable")
            }
            val parent = sf.parentFile
                ?: return Result.Error(
                    LaunchContract.ERR_UNSUPPORTED,
                    "startup_file has no parent folder",
                )
            if (sf.extension.equals("exe", ignoreCase = true)) {
                val dataXp3 = File(parent, "data.xp3")
                if (!dataXp3.isFile || !dataXp3.canRead()) {
                    return Result.Error(
                        LaunchContract.ERR_STARTUP_NOT_FOUND,
                        "A Windows executable requires a readable sibling data.xp3",
                    )
                }
                return Result.Ok(
                    bootPath = dataXp3.absolutePath,
                    executablePath = sf.absolutePath,
                )
            }
            if (!sf.name.equals("startup.tjs", ignoreCase = true) &&
                !sf.extension.equals("xp3", ignoreCase = true)
            ) {
                return Result.Error(
                    LaunchContract.ERR_UNSUPPORTED,
                    "Unsupported startup file",
                )
            }
            if (sf.name.equals("startup.tjs", ignoreCase = true)) {
                return Result.Ok(
                    parent.canonicalPath + File.separator,
                    configuredExecutable?.absolutePath,
                )
            }
            val dataXp3 = File(parent, "data.xp3")
            val bootFile = if (
                sf.extension.equals("xp3", ignoreCase = true) &&
                !sf.name.equals("data.xp3", ignoreCase = true) &&
                dataXp3.isFile &&
                dataXp3.canRead()
            ) {
                dataXp3
            } else {
                sf
            }
            return Result.Ok(bootFile.absolutePath, configuredExecutable?.absolutePath)
        }

        // Auto-detect.
        val startupTjs = File(gameDir, "startup.tjs")
        val dataXp3 = File(gameDir, "data.xp3")
        return when {
            startupTjs.isFile && startupTjs.canRead() ->
                Result.Ok(
                    gameDir.canonicalPath + File.separator,
                    configuredExecutable?.absolutePath,
                )
            dataXp3.isFile && dataXp3.canRead() ->
                Result.Ok(dataXp3.absolutePath, configuredExecutable?.absolutePath)
            else -> Result.Error(
                LaunchContract.ERR_STARTUP_NOT_FOUND,
                "No ${LaunchContract.AUTO_STARTUP_NAMES.joinToString(" / ")} found"
            )
        }
    }
}
