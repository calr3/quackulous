package org.quackulous.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.quackulous.engine.EngineInfo
import org.quackulous.engine.MoveKind
import org.quackulous.engine.Suggestion

@Composable
fun AnalysisScreen(viewModel: AppViewModel) {
    val state by viewModel.analysis.collectAsStateWithLifecycle()
    val info = viewModel.info ?: return
    val preview = state.previewIndex?.let { state.suggestions.getOrNull(it) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                viewModel.wordListName ?: "Quackulous",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            TextButton(onClick = viewModel::changeWordList) { Text("Word list") }
            TextButton(onClick = viewModel::clearBoard) { Text("Clear board") }
        }

        BoardView(
            info = info,
            board = state.board,
            selectedRow = state.selectedRow,
            selectedColumn = state.selectedColumn,
            across = state.across,
            preview = preview,
            onSquareTapped = viewModel::selectSquare,
        )

        Text(
            "Tap a square to start there; tap it again to switch between across and down. Type lowercase for a blank, " +
                "and '.' to skip a square that's already filled.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.entry,
                onValueChange = viewModel::setEntry,
                label = { Text(if (state.across) "Word across" else "Word down") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.placeEntry() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::placeEntry) { Text("Place") }
            OutlinedButton(onClick = viewModel::clearSquare) { Text("Erase") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.rack,
                onValueChange = viewModel::setRack,
                label = { Text("Your rack (? for a blank)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = state.myScore,
                onValueChange = { viewModel.setScores(it, state.opponentScore) },
                label = { Text("You") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.opponentScore,
                onValueChange = { viewModel.setScores(state.myScore, it) },
                label = { Text("Them") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        val busy = state.busy
        if (busy == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::findMoves, Modifier.weight(1f)) { Text("Best moves") }
                OutlinedButton(onClick = viewModel::simulate, Modifier.weight(1f)) { Text("Simulate") }
            }
        } else {
            Text(busy.label)
            if (busy.progress != null) LinearProgressIndicator(progress = { busy.progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            if (busy.stoppable) OutlinedButton(onClick = viewModel::stop) { Text("Stop and show results") }
        }

        state.message?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissMessage) { Text("OK") }
            }
        }

        if (preview != null) {
            Button(onClick = viewModel::playPreview, Modifier.fillMaxWidth()) { Text("Put ${describe(preview)} on the board") }
        }

        state.suggestions.forEachIndexed { index, suggestion ->
            SuggestionRow(suggestion, state.simulated, selected = index == state.previewIndex) { viewModel.preview(index) }
        }

        if (state.unseen.isNotEmpty()) UnseenTiles(info, state.unseen)
    }
}

private fun describe(suggestion: Suggestion): String = when (suggestion.kind) {
    MoveKind.Place -> "${suggestion.position} ${suggestion.word}"
    MoveKind.Exchange -> "swap ${suggestion.word}"
    MoveKind.Pass -> "pass"
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, simulated: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(describe(suggestion), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${suggestion.score}", modifier = Modifier.width(40.dp))
        Text(String.format(Locale.ROOT, "%.1f", suggestion.equity), modifier = Modifier.width(52.dp))
        if (simulated) Text(String.format(Locale.ROOT, "%.0f%%", suggestion.win * 100), modifier = Modifier.width(44.dp))
        Text(suggestion.leave.ifEmpty { "–" }, fontFamily = FontFamily.Monospace, modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun UnseenTiles(info: EngineInfo, unseen: List<Int>) {
    val total = unseen.sum()
    val text = info.tiles.zip(unseen).filter { it.second > 0 }.joinToString("  ") { (tile, count) -> "${tile.text}$count" }
    Column {
        Text("Unseen tiles ($total)", style = MaterialTheme.typography.titleSmall)
        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
