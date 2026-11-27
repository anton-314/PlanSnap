package app.plansnap.feature.editor.hints

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState

/** From this number of walls on the input bar offers closing the room (UX section 3). */
private const val WALLS_TO_OFFER_CLOSING = 3

/**
 * The one hint to show for [state], `null` for none (UX section 5).
 *
 * Every situation of the selected room has at most one hint, so at most one is ever shown. A closed hint is not
 * replaced by another one in the same situation. No hint appears while the project is not loaded or while the host of
 * a dock is being chosen, because the plan then has its own message.
 */
internal fun activeHint(state: EditorUiState): EditorHint? {
    val docking = state.section(RoomsState::class)?.dock != null
    if (state.load != EditorLoadState.Loaded || docking) return null
    val closing = state.section(HintsState::class)?.closing.orEmpty()
    return situationHint(state)?.takeUnless { it in state.settings.dismissedHints || it in closing }
}

private fun situationHint(state: EditorUiState): EditorHint? {
    val room = state.selectedRoom
    return when {
        // A project without rooms: the first wall creates one.
        room == null -> EditorHint.NUMPAD.takeIf { state.project?.rooms?.isEmpty() == true }

        room.isClosed -> evaluationHint(state, room)

        room.walls.isEmpty() -> EditorHint.NUMPAD

        room.walls.size < WALLS_TO_OFFER_CLOSING -> EditorHint.DIRECTION

        else -> EditorHint.CLOSE
    }
}

private fun evaluationHint(
    state: EditorUiState,
    room: Room,
): EditorHint? =
    when (state.evaluations[room.id]) {
        is RoomEvaluation.Proposed -> EditorHint.DIAGONAL
        is RoomEvaluation.NeedsAdjustment -> EditorHint.ADJUSTMENT
        else -> null
    }
