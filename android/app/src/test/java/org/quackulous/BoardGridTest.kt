package org.quackulous

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quackulous.data.WordListRecognizer
import org.quackulous.engine.MoveKind
import org.quackulous.engine.PlacedTile
import org.quackulous.engine.Suggestion
import org.quackulous.ui.BoardGrid
import org.quackulous.ui.Racks

class BoardGridTest {
    private fun place(word: String, tiles: List<PlacedTile>) =
        Suggestion(MoveKind.Place, "8H", word, "", 0, 0.0, 0.0, 0, tiles)

    @Test
    fun placesAcrossAndDown() {
        val board = BoardGrid.empty(15, 15).placeWord(7, 7, true, "IMBARRED")!!.placeWord(7, 9, false, ".OXeN")!!
        assertEquals("I", board[7, 7])
        assertEquals("D", board[7, 14])
        assertEquals("B", board[7, 9])
        assertEquals("O", board[8, 9])
        assertEquals("e", board[10, 9])
    }

    @Test
    fun refusesWordsOffTheBoard() {
        assertNull(BoardGrid.empty(15, 15).placeWord(7, 8, true, "IMBARRED"))
        assertNull(BoardGrid.empty(15, 15).placeWord(14, 0, false, "AB"))
    }

    @Test
    fun encodesAndDecodes() {
        val board = BoardGrid.empty(15, 15).placeWord(0, 0, true, "QuA")!!
        assertEquals(board, BoardGrid.decode(15, 15, board.encode()))
        assertNull(BoardGrid.decode(15, 15, "A|B"))
    }

    @Test
    fun playsOnlyNewTiles() {
        val board = BoardGrid.empty(15, 15).placeWord(7, 7, true, "AB")!!
        val played = board.play(place("ABs", listOf(PlacedTile(7, 7, "A", false), PlacedTile(7, 8, "B", false), PlacedTile(7, 9, "s", true))))
        assertEquals("s", played[7, 9])
        assertEquals("A", played[7, 7])
    }

    @Test
    fun racksKeepOnlyTiles() {
        val letters = ('A'..'Z').map { it.toString() }.toSet()
        assertEquals("ABC?DEFG", Racks.normalize("ab c?1defgh", letters, 8))
    }

    @Test
    fun rackAfterPlayingRemovesBlanksAndLetters() {
        val move = place("QuA", listOf(PlacedTile(0, 0, "Q", true), PlacedTile(0, 1, "u", true), PlacedTile(0, 2, "A", false)))
        assertEquals("ZE", Racks.afterPlaying("QZ?E", move))
        val swap = Suggestion(MoveKind.Exchange, "", "Q?", "", 0, 0.0, 0.0, 0, emptyList())
        assertEquals("ZE", Racks.afterPlaying("QZ?E", swap))
    }

    @Test
    fun recognisesKnownWordLists() {
        val lines = listOf(
            "155a50a1:NASPA Word List 2023 (NWL2023), Copyright © 2023 North American SCRABBLE Players Association.:naspa.gif",
            "7f0e9ef8:Collins Offical Scrabble™ Wordlist 2024, Published under license with Collins",
        )
        assertEquals("Collins Offical Scrabble™ Wordlist 2024", WordListRecognizer.name(lines, "7f0e9ef8fde8d6ef986acb7de33b1c98"))
        assertEquals("NASPA Word List 2023 (NWL2023)", WordListRecognizer.name(lines, "155a50a1000000000000000000000000"))
        assertNull(WordListRecognizer.name(lines, "0123456789abcdef0123456789abcdef"))
        assertTrue(WordListRecognizer.name(emptyList(), "7f0e9ef8") == null)
    }
}
