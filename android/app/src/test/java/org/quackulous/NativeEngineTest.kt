package org.quackulous

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.quackulous.engine.BuildStage
import org.quackulous.engine.EngineException
import org.quackulous.engine.MoveKind
import org.quackulous.engine.QuackleEngine

/**
 * Drives the real engine through the JNI bridge, using a desktop build of the native library. host-test.sh builds
 * it and passes -PhostLibDir; without that, these tests are skipped.
 */
class NativeEngineTest {
    companion object {
        private lateinit var engine: QuackleEngine
        private lateinit var dataDirectory: File

        @BeforeClass
        @JvmStatic
        fun startEngine() {
            assumeTrue("no desktop native library; run host-test.sh", System.getProperty("quackulous.hostLibDir") != null)
            val repoData = File(System.getProperty("quackulous.dataDir")!!)
            dataDirectory = Files.createTempDirectory("quackulous").toFile()
            // The same layout AssetInstaller unpacks from the app's assets.
            repoData.resolve("alphabets/lexulous.quackle_alphabet").copyTo(dataDirectory.resolve("alphabets/lexulous.quackle_alphabet"))
            repoData.resolve("strategy/default").copyRecursively(dataDirectory.resolve("strategy/default"))
            repoData.resolve("strategy/default_english").copyRecursively(dataDirectory.resolve("strategy/default_english"))
            repoData.resolve("lexica/copyrights.txt").copyTo(dataDirectory.resolve("lexica/copyrights.txt"))
            engine = runBlocking { QuackleEngine.start(dataDirectory) }
        }
    }

    private fun emptyBoard() = List(engine.info.board.width * engine.info.board.height) { "" }

    private fun loadWords(vararg lines: String) = runBlocking {
        val list = File(dataDirectory, "words.txt")
        list.writeText(lines.joinToString("\n"))
        val stages = mutableSetOf<BuildStage>()
        val report = engine.buildLexicon(list, "test") { stage, _ -> stages += stage }
        engine.loadLexicon("test")
        report to stages
    }

    @Test
    fun describesTheLexulousGame() {
        assertEquals(8, engine.info.rackSize)
        assertEquals(15, engine.info.board.width)
        assertEquals(89, engine.info.tiles.sumOf { it.count })
        assertEquals("?", engine.info.blank.text)
        assertEquals(12, engine.info.scoreOf("Z"))
        assertEquals(0, engine.info.scoreOf("z"))
        assertEquals(3, engine.info.board.wordMultiplier(0, 0))
        assertEquals(2, engine.info.board.wordMultiplier(7, 7))
    }

    @Test
    fun buildsLoadsAndAnalyses() = runBlocking {
        val (report, stages) = loadWords("# test list", "IMBARRED", "imbarred", "QI", "ZA", "ZAS", "AB a definition", "C3PO")
        assertEquals(5, report.words)
        assertEquals(1, report.duplicates)
        assertEquals(1, report.unencodable)
        assertEquals(listOf("C3PO"), report.unencodableExamples)
        assertTrue(stages.containsAll(BuildStage.entries))
        assertTrue(engine.hasBuiltLexicon("test"))
        assertTrue(engine.isWord("QI"))
        assertTrue(!engine.isWord("QIS"))

        engine.setPosition(emptyBoard(), "ABDEIMRR")
        val best = engine.staticMoves(5).first()
        assertEquals(MoveKind.Place, best.kind)
        assertEquals("8H", best.position)
        assertEquals("IMBARRED", best.word)
        assertEquals(140, best.score)
        assertEquals(8, best.tiles.count { it.placed })
        assertEquals(89 - 8, engine.unseenCounts().sum())

        var calls = 0
        val simulated = engine.simulate(3, 20, 2) { calls++; true }
        assertTrue(simulated.isNotEmpty())
        assertTrue(calls > 0)
        assertTrue(simulated.all { it.iterations == 20 })
    }

    @Test
    fun playsThroughTilesAndBlanks() = runBlocking {
        loadWords("ZA", "ZAS", "QI", "AB")
        val cells = emptyBoard().toMutableList()
        cells[7 * 15 + 7] = "Z"
        cells[7 * 15 + 8] = "a"
        engine.setPosition(cells, "S")
        val zas = engine.staticMoves(10).first { it.word == "ZaS" }
        assertEquals(listOf(false, false, true), zas.tiles.map { it.placed })
        assertEquals(listOf("Z", "a", "S"), zas.tiles.map { it.text })
    }

    @Test
    fun rejectsImpossiblePositions() = runBlocking {
        loadWords("ZA")
        try {
            engine.setPosition(emptyBoard(), "ZZ")
            fail("two Zs")
        } catch (e: EngineException) {
            assertTrue(e.message!!, e.message!!.contains("the set only has 1"))
        }
    }
}
