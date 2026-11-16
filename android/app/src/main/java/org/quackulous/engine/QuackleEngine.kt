package org.quackulous.engine

import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/**
 * The engine with Lexulous rules. Quackle keeps its state in one global, so there is one engine per process and
 * every call runs on a single dedicated thread.
 */
class QuackleEngine private constructor(val info: EngineInfo) {

    companion object {
        private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "quackle-engine").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        /** [dataDirectory] is laid out like Quackle's data directory; see AssetInstaller. */
        suspend fun start(dataDirectory: File): QuackleEngine = withContext(dispatcher) {
            val json = JSONObject(NativeEngine.nativeInit(dataDirectory.absolutePath))
            json.optString("error").takeIf { it.isNotEmpty() }?.let { throw EngineException(it) }
            QuackleEngine(parseInfo(json))
        }

        internal fun parseInfo(json: JSONObject): EngineInfo {
            val tiles = json.getJSONArray("tiles").objects().map {
                TileInfo(it.getString("text"), it.getInt("score"), it.getInt("count"), it.getBoolean("vowel"))
            }
            val board = json.getJSONObject("board")
            val layout = BoardLayout(
                width = board.getInt("width"),
                height = board.getInt("height"),
                startRow = board.getInt("startRow"),
                startColumn = board.getInt("startColumn"),
                letterMultipliers = board.getJSONArray("letterMultipliers").ints(),
                wordMultipliers = board.getJSONArray("wordMultipliers").ints(),
            )
            return EngineInfo(json.getInt("rackSize"), tiles, layout)
        }

        internal fun parseSuggestions(text: String): List<Suggestion> = JSONArray(text).objects().map {
            Suggestion(
                kind = when (it.getString("kind")) {
                    "place" -> MoveKind.Place
                    "exchange" -> MoveKind.Exchange
                    else -> MoveKind.Pass
                },
                position = it.getString("position"),
                word = it.getString("word"),
                leave = it.getString("leave"),
                score = it.getInt("score"),
                equity = it.getDouble("equity"),
                win = it.getDouble("win"),
                iterations = it.getInt("iterations"),
                tiles = it.getJSONArray("tiles").objects().map { tile ->
                    PlacedTile(tile.getInt("row"), tile.getInt("column"), tile.getString("text"), tile.getBoolean("placed"))
                },
            )
        }

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }
    }

    /**
     * Builds the dictionary files for [name] from a word list: one word per line, anything after the first space
     * ignored. Throws [EngineException] with the reason if the build fails or is cancelled.
     */
    suspend fun buildLexicon(wordList: File, name: String, onProgress: (BuildStage, Double) -> Unit): BuildReport {
        val job = coroutineContext[Job]
        return withContext(dispatcher) {
            val listener = NativeEngine.BuildListener { stage, fraction ->
                onProgress(BuildStage.entries[stage], fraction)
                job?.isActive != false
            }
            val json = JSONObject(NativeEngine.nativeBuildLexicon(wordList.absolutePath, name, listener))
            json.optString("error").takeIf { it.isNotEmpty() }?.let { throw EngineException(it) }
            val examples = json.getJSONArray("unencodableExamples")
            BuildReport(
                words = json.getInt("words"),
                duplicates = json.getInt("duplicates"),
                unencodable = json.getInt("unencodable"),
                unencodableExamples = (0 until examples.length()).map { examples.getString(it) },
                hash = json.getString("hash"),
            )
        }
    }

    suspend fun hasBuiltLexicon(name: String): Boolean = withContext(dispatcher) { NativeEngine.nativeHasBuiltLexicon(name) }

    suspend fun loadLexicon(name: String) = withContext(dispatcher) {
        NativeEngine.nativeLoadLexicon(name)?.let { throw EngineException(it) }
    }

    suspend fun isWord(word: String): Boolean = withContext(dispatcher) { NativeEngine.nativeIsWord(word) }

    /**
     * [cells] holds width * height entries, row-major: "" for an empty square, a letter, or a lowercase letter for a
     * blank. The rack writes a blank as "?". Throws [EngineException] if the position is impossible.
     */
    suspend fun setPosition(cells: List<String>, rack: String, myScore: Int = 0, opponentScore: Int = 0) =
        withContext(dispatcher) {
            NativeEngine.nativeSetPosition(cells.toTypedArray(), rack, myScore, opponentScore)?.let { throw EngineException(it) }
        }

    /** Tiles neither on the board nor on the rack, counted in [EngineInfo.tiles] order. */
    suspend fun unseenCounts(): List<Int> = withContext(dispatcher) { NativeEngine.nativeUnseenCounts().toList() }

    /** The best moves for the current position by score plus leave value. */
    suspend fun staticMoves(count: Int): List<Suggestion> =
        withContext(dispatcher) { parseSuggestions(NativeEngine.nativeStaticMoves(count)) }

    /**
     * Simulates the best [candidates] static moves [plies] turns ahead. [onProgress] gets the iterations done so far and
     * returns false to stop early with the results so far.
     */
    suspend fun simulate(candidates: Int, iterations: Int, plies: Int, onProgress: (Int) -> Boolean): List<Suggestion> =
        withContext(dispatcher) {
            val listener = NativeEngine.SimulationListener { done -> onProgress(done) }
            parseSuggestions(NativeEngine.nativeSimulate(candidates, iterations, plies, listener))
        }
}
