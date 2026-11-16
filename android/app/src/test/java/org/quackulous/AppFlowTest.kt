package org.quackulous

import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.quackulous.ui.AppViewModel
import org.quackulous.ui.MainActivity
import org.robolectric.RobolectricTestRunner

/**
 * Runs the app's screens on the JVM under Robolectric, with the real engine from the desktop native library: first
 * launch, word list import, analysis, simulation, and playing a suggested move. host-test.sh provides the library.
 * One test, since the app keeps its dictionary in storage that outlives a test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppFlowTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun requireNativeLibrary() {
            assumeTrue("no desktop native library; run host-test.sh", System.getProperty("quackulous.hostLibDir") != null)
        }
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val timeout = 60_000L

    private fun viewModel() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    /** Waits for [text] on screen, reporting the app's state if it never appears. */
    private fun waitFor(text: String, substring: Boolean = false) {
        try {
            compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeout)
        } catch (e: Throwable) {
            val model = viewModel()
            val analysis = model.analysis.value
            throw AssertionError(
                "\"$text\" never appeared. Screen: ${model.screen.value}; busy: ${analysis.busy}; " +
                    "message: ${analysis.message}; suggestions: ${analysis.suggestions.map { it.position + " " + it.word }}",
                e,
            )
        }
    }

    @Test
    fun firstRunThroughAnalysisAndPlay() {
        waitFor("Choose word list")

        val wordList = File(compose.activity.cacheDir, "words.txt")
        wordList.writeText("IMBARRED\nQI\nZA\nZAS\nAB a definition\nC3PO\nBA\nAE\nEA\n")
        val viewModel = viewModel()
        compose.runOnUiThread { viewModel.importWordList(Uri.fromFile(wordList)) }

        waitFor("Dictionary ready")
        compose.onNodeWithText("8 words.").assertExists()
        compose.onNodeWithText("1 entry was skipped because it uses characters that aren't tiles: C3PO").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()

        waitFor("Best moves")
        compose.onNodeWithText("Your rack (? for a blank)").performScrollTo().performTextInput("abdeimrr")
        compose.onNodeWithText("Best moves").performScrollTo().performClick()
        waitFor("8H IMBARRED")
        compose.onNodeWithText("Unseen tiles (81)").assertExists()

        compose.onNodeWithText("Simulate").performScrollTo().performClick()
        waitFor("%", substring = true)
        val simulated = viewModel.analysis.value.suggestions
        assertTrue(simulated.isNotEmpty() && simulated.all { it.iterations > 0 })

        compose.onNodeWithText("8H IMBARRED").performScrollTo().performClick()
        compose.onNodeWithText("Put 8H IMBARRED on the board").performScrollTo().performClick()
        compose.waitForIdle()
        val state = viewModel.analysis.value
        assertEquals("IMBARRED", (7..14).joinToString("") { state.board[7, it] })
        assertEquals("", state.rack)

        compose.onNodeWithText("Word list").performScrollTo().performClick()
        waitFor("Keep the current word list")
        compose.onNodeWithText("Keep the current word list").performScrollTo().performClick()
        waitFor("Best moves")
    }
}
