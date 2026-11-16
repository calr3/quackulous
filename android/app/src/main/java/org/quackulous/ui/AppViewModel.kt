package org.quackulous.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.quackulous.data.AssetInstaller
import org.quackulous.data.WordListRecognizer
import org.quackulous.engine.BuildReport
import org.quackulous.engine.BuildStage
import org.quackulous.engine.EngineException
import org.quackulous.engine.EngineInfo
import org.quackulous.engine.QuackleEngine
import org.quackulous.engine.Suggestion

sealed interface Screen {
    data object Starting : Screen
    data class Failed(val message: String) : Screen
    /** [canCancel] when a dictionary is already loaded and this is a change of word list. */
    data class ChooseWordList(val error: String? = null, val canCancel: Boolean = false) : Screen
    data class Building(val stage: BuildStage, val fraction: Double) : Screen
    data class Built(val report: BuildReport, val recognisedAs: String?) : Screen
    data object Analysis : Screen
}

data class Busy(val label: String, val progress: Float?, val stoppable: Boolean)

data class AnalysisState(
    val board: BoardGrid,
    val selectedRow: Int,
    val selectedColumn: Int,
    val across: Boolean = true,
    val entry: String = "",
    val rack: String = "",
    val myScore: String = "",
    val opponentScore: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val simulated: Boolean = false,
    val previewIndex: Int? = null,
    val unseen: List<Int> = emptyList(),
    val busy: Busy? = null,
    val message: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val LEXICON = "user"
        private const val STATIC_MOVES = 20
        private const val SIMULATED_MOVES = 10
        private const val SIMULATION_ITERATIONS = 300
        private const val SIMULATION_PLIES = 2
    }

    private val prefs = application.getSharedPreferences("quackulous", Context.MODE_PRIVATE)
    private val stopRequested = AtomicBoolean(false)
    private var work: Job? = null
    private lateinit var dataDirectory: File

    private val _screen = MutableStateFlow<Screen>(Screen.Starting)
    val screen: StateFlow<Screen> = _screen

    private val _analysis = MutableStateFlow(AnalysisState(BoardGrid.empty(15, 15), 7, 7))
    val analysis: StateFlow<AnalysisState> = _analysis

    var engine: QuackleEngine? = null
        private set

    val info: EngineInfo? get() = engine?.info

    val wordListName: String? get() = prefs.getString("wordListName", null)

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        try {
            dataDirectory = withContext(Dispatchers.IO) { AssetInstaller.install(getApplication()) }
            val started = QuackleEngine.start(dataDirectory)
            engine = started
            val board = started.info.board
            _analysis.value = AnalysisState(
                board = BoardGrid.decode(board.width, board.height, prefs.getString("board", null))
                    ?: BoardGrid.empty(board.width, board.height),
                selectedRow = board.startRow,
                selectedColumn = board.startColumn,
                rack = prefs.getString("rack", "").orEmpty(),
            )
            if (started.hasBuiltLexicon(LEXICON)) {
                started.loadLexicon(LEXICON)
                _screen.value = Screen.Analysis
            } else {
                _screen.value = Screen.ChooseWordList()
            }
        } catch (e: Exception) {
            _screen.value = Screen.Failed(e.message ?: e.toString())
        }
    }

    // Setup

    fun importWordList(uri: Uri) {
        val engine = engine ?: return
        val hadLexicon = _screen.value.let { it is Screen.ChooseWordList && it.canCancel }
        work?.cancel()
        work = viewModelScope.launch {
            _screen.value = Screen.Building(BuildStage.Reading, 0.0)
            val copy = File(dataDirectory, "import.txt")
            try {
                withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw EngineException("couldn't open that file")
                    input.use { stream -> copy.outputStream().use { stream.copyTo(it) } }
                }
                val report = engine.buildLexicon(copy, LEXICON) { stage, fraction ->
                    _screen.value = Screen.Building(stage, fraction)
                }
                engine.loadLexicon(LEXICON)
                val recognised = WordListRecognizer.name(dataDirectory, report.hash)
                prefs.edit().putString("wordListName", recognised ?: displayName(uri)).apply()
                clearSuggestions()
                _screen.value = Screen.Built(report, recognised)
            } catch (e: EngineException) {
                _screen.value = Screen.ChooseWordList(e.message, canCancel = hadLexicon)
            } finally {
                withContext(Dispatchers.IO) { copy.delete() }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun changeWordList() {
        _screen.value = Screen.ChooseWordList(canCancel = true)
    }

    fun showAnalysis() {
        _screen.value = Screen.Analysis
    }

    // Board and rack

    fun selectSquare(row: Int, column: Int) = _analysis.update {
        if (it.selectedRow == row && it.selectedColumn == column) it.copy(across = !it.across)
        else it.copy(selectedRow = row, selectedColumn = column)
    }

    fun setEntry(text: String) = _analysis.update { it.copy(entry = text.filter { c -> c.isLetter() || c == '.' }) }

    fun placeEntry() {
        val state = _analysis.value
        if (state.entry.isEmpty()) return
        val placed = state.board.placeWord(state.selectedRow, state.selectedColumn, state.across, state.entry)
        if (placed == null) {
            _analysis.update { it.copy(message = "That doesn't fit on the board from the selected square.") }
            return
        }
        updateBoard(placed)
        _analysis.update { it.copy(entry = "") }
    }

    fun clearSquare() {
        val state = _analysis.value
        updateBoard(state.board.with(state.selectedRow, state.selectedColumn, ""))
    }

    fun clearBoard() = updateBoard(_analysis.value.board.cleared())

    private fun updateBoard(board: BoardGrid) {
        prefs.edit().putString("board", board.encode()).apply()
        _analysis.update { it.copy(board = board) }
        clearSuggestions()
    }

    fun setRack(text: String) {
        val info = info ?: return
        val rack = Racks.normalize(text, info.letters.map { it.text }.toSet(), info.rackSize)
        prefs.edit().putString("rack", rack).apply()
        _analysis.update { it.copy(rack = rack) }
        clearSuggestions()
    }

    fun setScores(mine: String, theirs: String) = _analysis.update {
        it.copy(myScore = mine.filter(Char::isDigit).take(4), opponentScore = theirs.filter(Char::isDigit).take(4))
    }

    fun dismissMessage() = _analysis.update { it.copy(message = null) }

    private fun clearSuggestions() = _analysis.update { it.copy(suggestions = emptyList(), previewIndex = null, simulated = false) }

    // Analysis

    fun findMoves() = analyse(simulate = false)

    fun simulate() = analyse(simulate = true)

    fun stop() = stopRequested.set(true)

    private fun analyse(simulate: Boolean) {
        val engine = engine ?: return
        val state = _analysis.value
        if (state.rack.isEmpty()) {
            _analysis.update { it.copy(message = "Enter your rack first.") }
            return
        }
        work?.cancel()
        stopRequested.set(false)
        work = viewModelScope.launch {
            val label = if (simulate) "Simulating" else "Finding moves"
            _analysis.update { it.copy(busy = Busy(label, if (simulate) 0f else null, simulate), message = null) }
            try {
                engine.setPosition(state.board.cells, state.rack, state.myScore.toIntOrNull() ?: 0, state.opponentScore.toIntOrNull() ?: 0)
                val unseen = engine.unseenCounts()
                val moves = if (simulate) {
                    engine.simulate(SIMULATED_MOVES, SIMULATION_ITERATIONS, SIMULATION_PLIES) { done ->
                        _analysis.update { it.copy(busy = Busy(label, done / SIMULATION_ITERATIONS.toFloat(), true)) }
                        !stopRequested.get()
                    }
                } else {
                    engine.staticMoves(STATIC_MOVES)
                }
                _analysis.update {
                    it.copy(suggestions = moves, simulated = simulate, previewIndex = null, unseen = unseen, busy = null,
                        message = if (moves.isEmpty()) "No moves found." else null)
                }
            } catch (e: EngineException) {
                _analysis.update { it.copy(busy = null, message = e.message) }
            }
        }
    }

    fun preview(index: Int) = _analysis.update { it.copy(previewIndex = if (it.previewIndex == index) null else index) }

    /** Puts the previewed move on the board and takes its tiles off the rack. */
    fun playPreview() {
        val state = _analysis.value
        val suggestion = state.previewIndex?.let { state.suggestions.getOrNull(it) } ?: return
        val rack = Racks.afterPlaying(state.rack, suggestion)
        prefs.edit().putString("rack", rack).apply()
        _analysis.update { it.copy(rack = rack) }
        updateBoard(state.board.play(suggestion))
    }
}
