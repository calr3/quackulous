package org.quackulous.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuackulousTheme {
                Surface(Modifier.fillMaxSize()) { App(viewModel) }
            }
        }
    }
}

@Composable
fun QuackulousTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF7FC3F0), secondary = Color(0xFFFDD835))
    } else {
        lightColorScheme(primary = Color(0xFF0277BD), secondary = Color(0xFFF9A825))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun App(viewModel: AppViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    when (val current = screen) {
        Screen.Starting -> StartingScreen()
        is Screen.Failed -> FailedScreen(current.message)
        is Screen.ChooseWordList -> ChooseWordListScreen(current, viewModel::importWordList, viewModel::showAnalysis)
        is Screen.Building -> BuildingScreen(current)
        is Screen.Built -> BuiltScreen(current, viewModel::showAnalysis)
        Screen.Analysis -> AnalysisScreen(viewModel)
    }
}
