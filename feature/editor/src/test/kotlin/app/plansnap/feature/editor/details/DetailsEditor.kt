package app.plansnap.feature.editor.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState

/**
 * Stands in for the view model in Compose tests: [DetailsIntent]s go to the real [DetailsHandler], the rest is
 * only recorded.
 */
internal class DetailsEditor(
    initial: EditorUiState,
) {
    val host = RecordingHost(initial)
    val intents: MutableList<EditorIntent> = mutableListOf()
    private val handler = DetailsHandler()

    /** Handles [intent] the way the screen would after it sent it to the view model. */
    fun onIntent(intent: EditorIntent) {
        intents += intent
        handler.handle(intent, host)
    }

    /** Intents of type [T] in the order they were sent. */
    inline fun <reified T : EditorIntent> sent(): List<T> = intents.filterIsInstance<T>()
}

/** The current state of [editor] as Compose state. */
@Composable
internal fun DetailsEditor.stateAsState(): State<EditorUiState> = remember(this) { host.state }.collectAsState()
