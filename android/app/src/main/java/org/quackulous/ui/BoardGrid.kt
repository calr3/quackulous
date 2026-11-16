package org.quackulous.ui

import org.quackulous.engine.MoveKind
import org.quackulous.engine.Suggestion

/** The tiles on the board: "" for an empty square, an uppercase letter for a tile, lowercase for a blank. */
data class BoardGrid(val width: Int, val height: Int, val cells: List<String>) {
    init {
        require(cells.size == width * height) { "a $width by $height board needs ${width * height} squares" }
    }

    operator fun get(row: Int, column: Int): String = cells[row * width + column]

    val isEmpty: Boolean get() = cells.all { it.isEmpty() }

    fun with(row: Int, column: Int, text: String): BoardGrid =
        copy(cells = cells.toMutableList().also { it[row * width + column] = text })

    /**
     * Writes [word] from the given square: uppercase letters are tiles, lowercase letters are blanks, and '.' leaves a
     * square as it is. Returns null if the word runs off the board.
     */
    fun placeWord(row: Int, column: Int, across: Boolean, word: String): BoardGrid? {
        val end = (if (across) column else row) + word.length - 1
        if (end >= (if (across) width else height)) return null
        val updated = cells.toMutableList()
        word.forEachIndexed { i, c ->
            if (c == '.') return@forEachIndexed
            val r = if (across) row else row + i
            val col = if (across) column + i else column
            updated[r * width + col] = c.toString()
        }
        return copy(cells = updated)
    }

    /** The board after a suggested placement. Other kinds of move leave it unchanged. */
    fun play(suggestion: Suggestion): BoardGrid {
        if (suggestion.kind != MoveKind.Place) return this
        val updated = cells.toMutableList()
        for (tile in suggestion.tiles) if (tile.placed) updated[tile.row * width + tile.column] = tile.text
        return copy(cells = updated)
    }

    fun cleared(): BoardGrid = empty(width, height)

    fun encode(): String = cells.joinToString("|")

    companion object {
        fun empty(width: Int, height: Int) = BoardGrid(width, height, List(width * height) { "" })

        fun decode(width: Int, height: Int, text: String?): BoardGrid? {
            val cells = text?.split("|") ?: return null
            return if (cells.size == width * height) BoardGrid(width, height, cells) else null
        }
    }
}

object Racks {
    /** Keeps only tiles a rack can hold, uppercased, with "?" for a blank. */
    fun normalize(input: String, letters: Set<String>, rackSize: Int): String =
        input.uppercase().filter { it == '?' || it.toString() in letters }.take(rackSize)

    /** The rack left after playing a suggestion, before drawing. */
    fun afterPlaying(rack: String, suggestion: Suggestion): String {
        val remaining = StringBuilder(rack)
        val used = when (suggestion.kind) {
            MoveKind.Place -> suggestion.tiles.filter { it.placed }.map { if (it.text == it.text.uppercase()) it.text else "?" }
            MoveKind.Exchange -> suggestion.word.map { it.toString() }
            MoveKind.Pass -> emptyList()
        }
        for (tile in used) {
            val index = remaining.indexOf(tile)
            if (index >= 0) remaining.deleteCharAt(index)
        }
        return remaining.toString()
    }
}
