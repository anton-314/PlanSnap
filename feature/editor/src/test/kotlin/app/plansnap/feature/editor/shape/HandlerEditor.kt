package app.plansnap.feature.editor.shape

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentMapOf

/**
 * Stands in for the view model in Compose tests: [ShapeIntent]s go to the real [ShapeHandler], everything else is
 * only recorded. Intents that edit the typed input are applied like the view model does.
 */
internal class HandlerEditor(
    initial: EditorUiState,
) {
    val host = RecordingHost(initial)
    val intents: MutableList<EditorIntent> = mutableListOf()
    private val handler = ShapeHandler(SequentialIdGenerator("gen"))

    /** Handles [intent] the way the screen would after it sent it to the view model. */
    fun onIntent(intent: EditorIntent) {
        intents += intent
        when (intent) {
            is ShapeIntent -> handler.handle(intent, host)
            is CoreIntent.InputChanged -> host.update { it.copy(inputText = intent.text) }
            else -> Unit
        }
    }

    /** Replaces the evaluation of the selected room, as a newer evaluation of the view model would. */
    fun evaluated(evaluation: RoomEvaluation?) {
        host.update {
            it.copy(
                evaluations =
                    if (evaluation ==
                        null
                    ) {
                        persistentMapOf()
                    } else {
                        persistentMapOf(evaluation.roomId to evaluation)
                    },
            )
        }
    }

    /** Intents of type [T] in the order they were sent. */
    inline fun <reified T : EditorIntent> sent(): List<T> = intents.filterIsInstance<T>()
}

/** The current state of [editor] as Compose state. */
@Composable
internal fun HandlerEditor.stateAsState(): State<EditorUiState> = remember(this) { host.state }.collectAsState()
