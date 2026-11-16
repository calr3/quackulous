package org.quackulous.engine

data class TileInfo(val text: String, val score: Int, val count: Int, val vowel: Boolean)

data class BoardLayout(
    val width: Int,
    val height: Int,
    val startRow: Int,
    val startColumn: Int,
    val letterMultipliers: List<Int>,
    val wordMultipliers: List<Int>,
) {
    fun letterMultiplier(row: Int, column: Int) = letterMultipliers[row * width + column]
    fun wordMultiplier(row: Int, column: Int) = wordMultipliers[row * width + column]
}

data class EngineInfo(val rackSize: Int, val tiles: List<TileInfo>, val board: BoardLayout) {
    /** The blank is the last tile and is written "?". */
    val blank: TileInfo get() = tiles.last()
    val letters: List<TileInfo> get() = tiles.dropLast(1)

    /** Score of a tile as written on the board: lowercase is a blank. */
    fun scoreOf(text: String): Int =
        if (text.isEmpty() || text.lowercase() == text) blank.score else letters.firstOrNull { it.text == text }?.score ?: 0
}

enum class BuildStage { Reading, Dawg, Gaddag }

data class BuildReport(
    val words: Int,
    val duplicates: Int,
    val unencodable: Int,
    val unencodableExamples: List<String>,
    /** MD5 identity of the word list, as Quackle's lexica/copyrights.txt keys it. */
    val hash: String,
)

data class PlacedTile(val row: Int, val column: Int, val text: String, val placed: Boolean)

enum class MoveKind { Place, Exchange, Pass }

data class Suggestion(
    val kind: MoveKind,
    /** Quackle's notation for a placement: "8H" runs across from row 8, column H; "H8" runs down. */
    val position: String,
    /** The whole word for a placement, or the tiles swapped for an exchange. Lowercase letters are blanks. */
    val word: String,
    val leave: String,
    val score: Int,
    val equity: Double,
    /** Win probability from 0 to 1; simulation only. */
    val win: Double,
    /** Games simulated for this move; 0 for static analysis. */
    val iterations: Int,
    val tiles: List<PlacedTile>,
)

class EngineException(message: String) : Exception(message)
