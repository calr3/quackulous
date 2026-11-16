package org.quackulous.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.quackulous.engine.EngineInfo
import org.quackulous.engine.Suggestion

private val tripleWord = Color(0xFFD32F2F)
private val doubleWord = Color(0xFFF8BBD0)
private val tripleLetter = Color(0xFF3F6FD8)
private val doubleLetter = Color(0xFFBBDEFB)
private val plainSquare = Color(0xFFE6E6E6)
private val tile = Color(0xFFFDD835)
private val previewTile = Color(0xFF81C784)
private val tileText = Color(0xFF212121)
private val blankText = Color(0xFF6A1B9A)

/** The board with its premium squares, the tiles on it, and a suggestion's new tiles if one is being previewed. */
@Composable
fun BoardView(
    info: EngineInfo,
    board: BoardGrid,
    selectedRow: Int,
    selectedColumn: Int,
    across: Boolean,
    preview: Suggestion?,
    onSquareTapped: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = info.board
    val previewed = preview?.tiles?.filter { it.placed }?.associateBy { it.row * layout.width + it.column }.orEmpty()
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(layout.width / layout.height.toFloat())) {
        val square: Dp = maxWidth / layout.width
        val letterSize = with(LocalDensity.current) { (square * 0.55f).toSp() }
        val smallSize = with(LocalDensity.current) { (square * 0.28f).toSp() }
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until layout.height) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until layout.width) {
                        val previewTileHere = previewed[row * layout.width + column]
                        val text = previewTileHere?.text ?: board[row, column]
                        val selected = row == selectedRow && column == selectedColumn
                        val background = when {
                            previewTileHere != null -> previewTile
                            text.isNotEmpty() -> tile
                            layout.wordMultiplier(row, column) == 3 -> tripleWord
                            layout.wordMultiplier(row, column) == 2 -> doubleWord
                            layout.letterMultiplier(row, column) == 3 -> tripleLetter
                            layout.letterMultiplier(row, column) == 2 -> doubleLetter
                            else -> plainSquare
                        }
                        Box(
                            Modifier
                                .size(square)
                                .padding(0.5.dp)
                                .background(background)
                                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
                                .clickable { onSquareTapped(row, column) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                text.isNotEmpty() -> {
                                    val blank = text != text.uppercase()
                                    Text(text.uppercase(), fontSize = letterSize, fontWeight = FontWeight.Bold, color = if (blank) blankText else tileText)
                                    if (!blank) {
                                        Text(
                                            info.scoreOf(text).toString(),
                                            fontSize = smallSize,
                                            color = tileText,
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 1.dp),
                                        )
                                    }
                                }
                                selected -> Text(if (across) "→" else "↓", fontSize = letterSize, color = MaterialTheme.colorScheme.primary)
                                row == layout.startRow && column == layout.startColumn -> Text("+", fontSize = letterSize, color = tileText)
                                layout.wordMultiplier(row, column) > 1 ->
                                    Text("${layout.wordMultiplier(row, column)}W", fontSize = smallSize, color = if (layout.wordMultiplier(row, column) == 3) Color.White else tileText)
                                layout.letterMultiplier(row, column) > 1 ->
                                    Text("${layout.letterMultiplier(row, column)}L", fontSize = smallSize, color = if (layout.letterMultiplier(row, column) == 3) Color.White else tileText)
                            }
                        }
                    }
                }
            }
        }
    }
}
