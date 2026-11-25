package app.plansnap.feature.editor.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal val TEST_ROOM_ID = RoomId("r1")

/** Intents received by a screen under test, in order. */
internal class IntentLog {
    val intents: MutableList<EditorIntent> = mutableListOf()
}

/** A loaded editor with a room of [walls] open walls (at most 3) and the typed [input]. */
internal fun editorState(
    input: String = "",
    walls: Int = 0,
    closed: Boolean = false,
    error: DomainError? = null,
    settings: AppSettings = AppSettings(),
): EditorUiState {
    val nextCorner = CornerSpec.DeclaredRight(TurnDirection.LEFT)
    val lengths = listOf(4000L, 3000L, 4000L)
    val room =
        Room(
            id = TEST_ROOM_ID,
            walls =
                List(walls) { Wall(WallId("w$it"), Length(lengths[it]), Length(120)) }.toImmutableList(),
            corners =
                List(walls) { if (it == 0) CornerSpec.Unspecified else nextCorner }.toImmutableList(),
            isClosed = closed,
        )
    return EditorUiState(
        load = EditorLoadState.Loaded,
        project =
            Project(
                id = ProjectId("p1"),
                name = "Wohnung Süd",
                defaultHeight = Length(2500),
                rooms = persistentListOf(room),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        selectedRoomId = TEST_ROOM_ID,
        inputText = input,
        settings = settings,
        inputError = error,
    )
}

/** The error the editor reports for text it cannot parse. */
internal fun malformedLength(): DomainError =
    DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.MALFORMED)

/**
 * Applies the intents that edit the state the way the view model does (input text and corner) and records all of
 * them, so a test can type several keys in a row without the real view model.
 */
@Composable
internal fun rememberFakeEditor(
    initial: EditorUiState,
    log: IntentLog,
): Pair<EditorUiState, (EditorIntent) -> Unit> {
    var state by remember { mutableStateOf(initial) }
    val onIntent: (EditorIntent) -> Unit = { intent ->
        log.intents += intent
        state =
            when (intent) {
                is CoreIntent.InputChanged -> state.copy(inputText = intent.text)
                is CoreIntent.SetNextCorner -> state.copy(nextCorner = intent.corner)
                else -> state
            }
    }
    return state to onIntent
}
