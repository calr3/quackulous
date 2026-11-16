package org.quackulous.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.quackulous.engine.BuildStage

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
fun StartingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun FailedScreen(message: String) = Page {
    Text("Quackulous couldn't start", style = MaterialTheme.typography.headlineSmall)
    Text(message)
}

@Composable
fun ChooseWordListScreen(state: Screen.ChooseWordList, onChosen: (Uri) -> Unit, onCancel: () -> Unit) = Page {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onChosen) }
    Text("Choose a word list", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Quackulous doesn't come with a dictionary. Choose the word list you play with: a text file with one word " +
            "per line. Anything after the first space on a line is ignored, so lists with definitions work too.",
    )
    Text("The list is turned into a dictionary on this phone and isn't sent anywhere. This takes a few seconds.")
    state.error?.let { Text("That didn't work: $it", color = MaterialTheme.colorScheme.error) }
    Button(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }, Modifier.fillMaxWidth()) {
        Text("Choose word list")
    }
    if (state.canCancel) TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("Keep the current word list") }
}

@Composable
fun BuildingScreen(state: Screen.Building) = Page {
    Text("Building the dictionary", style = MaterialTheme.typography.headlineSmall)
    Text(
        when (state.stage) {
            BuildStage.Reading -> "Reading the word list"
            BuildStage.Dawg -> "Building the word index"
            BuildStage.Gaddag -> "Building the move generator's index"
        },
    )
    LinearProgressIndicator(progress = { state.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun BuiltScreen(state: Screen.Built, onContinue: () -> Unit) = Page {
    val number = NumberFormat.getIntegerInstance()
    val report = state.report
    Text("Dictionary ready", style = MaterialTheme.typography.headlineSmall)
    Text("${number.format(report.words)} words.")
    state.recognisedAs?.let { Text("This is $it.") }
    if (report.duplicates == 1) Text("1 repeated word was counted once.")
    if (report.duplicates > 1) Text("${number.format(report.duplicates)} repeated words were counted once.")
    if (report.unencodable > 0) {
        val examples = report.unencodableExamples.take(5).joinToString(", ")
        Text(
            if (report.unencodable == 1) "1 entry was skipped because it uses characters that aren't tiles: $examples"
            else "${number.format(report.unencodable)} entries were skipped because they use characters that aren't tiles, such as: $examples",
        )
    }
    Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text("Continue") }
}
