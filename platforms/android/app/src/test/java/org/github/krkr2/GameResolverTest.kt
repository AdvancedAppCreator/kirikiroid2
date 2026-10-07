package org.github.krkr2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun autoDetectsLooseStartupScript() {
        val game = temporaryFolder.newFolder("loose")
        File(game, "startup.tjs").writeText("Scripts.execStorage('main.tjs');")

        val result = GameResolver.resolve(game.absolutePath, null)

        assertEquals(
            game.canonicalPath + File.separator,
            (result as GameResolver.Result.Ok).bootPath,
        )
    }

    @Test
    fun explicitStartupScriptBootsItsContainingFolder() {
        val game = temporaryFolder.newFolder("explicit-loose")
        val startup = File(game, "startup.tjs").apply {
            writeText("Scripts.execStorage('main.tjs');")
        }

        val result = GameResolver.resolve(game.absolutePath, startup.absolutePath)

        assertEquals(
            game.canonicalPath + File.separator,
            (result as GameResolver.Result.Ok).bootPath,
        )
    }

    @Test
    fun redirectsAssetArchiveToDataArchive() {
        val game = temporaryFolder.newFolder("archive")
        val data = File(game, "data.xp3").apply { writeBytes(byteArrayOf(1)) }
        val bgm = File(game, "bgm.xp3").apply { writeBytes(byteArrayOf(2)) }

        val result = GameResolver.resolve(game.absolutePath, bgm.absolutePath)

        assertEquals(data.canonicalPath, (result as GameResolver.Result.Ok).bootPath)
    }

    @Test
    fun bootsDataArchiveButPreservesExecutableName() {
        val game = temporaryFolder.newFolder("protected")
        val data = File(game, "data.xp3").apply { writeBytes(byteArrayOf(1)) }
        val executable = File(game, "Game.exe").apply { writeBytes(byteArrayOf(2)) }

        val result = GameResolver.resolve(game.absolutePath, executable.absolutePath)

        result as GameResolver.Result.Ok
        assertEquals(data.canonicalPath, result.bootPath)
        assertEquals(executable.canonicalPath, result.executablePath)
    }

    @Test
    fun refusesStartupOutsideGameFolder() {
        val game = temporaryFolder.newFolder("inside")
        val outside = temporaryFolder.newFile("outside.xp3")

        val result = GameResolver.resolve(game.absolutePath, outside.absolutePath)

        assertTrue(result is GameResolver.Result.Error)
        assertEquals(
            LaunchContract.ERR_UNSUPPORTED,
            (result as GameResolver.Result.Error).code,
        )
    }

    @Test
    fun refusesWindowsExecutableWithoutDataArchive() {
        val game = temporaryFolder.newFolder("missing-data")
        val executable = File(game, "Game.exe").apply { writeBytes(byteArrayOf(1)) }

        val result = GameResolver.resolve(game.absolutePath, executable.absolutePath)

        assertTrue(result is GameResolver.Result.Error)
        assertEquals(
            LaunchContract.ERR_STARTUP_NOT_FOUND,
            (result as GameResolver.Result.Error).code,
        )
    }
}
