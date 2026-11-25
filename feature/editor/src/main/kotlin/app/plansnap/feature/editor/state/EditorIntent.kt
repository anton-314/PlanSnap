package app.plansnap.feature.editor.state

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.RoomId

/**
 * Everything the screen can ask of the editor. The interface is open on purpose: a sub-function (T-EDT-002 to
 * T-EDT-009) declares its own intents and an [EditorHandler] that consumes them, so the view model does not change.
 */
interface EditorIntent

/** Loads the project again after [EditorLoadState.Failed]. */
data object ReloadProject : EditorIntent

/** Intents of the core of the editor, handled by the view model itself. */
sealed interface CoreIntent : EditorIntent {
    /** The running input changed to [text], exactly as typed. */
    data class InputChanged(
        val text: String,
    ) : CoreIntent

    /** Selects the room that receives the input, `null` clears the selection. */
    data class SelectRoom(
        val roomId: RoomId?,
    ) : CoreIntent

    /** The plan was panned or zoomed to [view]. */
    data class ViewChanged(
        val view: PlanView?,
    ) : CoreIntent

    /** Sets the entered corner that precedes the next wall. */
    data class SetNextCorner(
        val corner: CornerSpec,
    ) : CoreIntent

    /** Sets the thickness of the next wall, `null` returns to the last stored thickness. */
    data class SetThickness(
        val thickness: Length?,
    ) : CoreIntent

    /** Confirms the running input as the next wall of the selected room. */
    data object ConfirmWall : CoreIntent

    /** Executes a confirmed [edit] as one undo step. */
    data class Submit(
        val edit: ProjectEdit,
    ) : CoreIntent

    /**
     * Accepts the closure adjustment of [roomId]. [proposal] is exactly the proposal that the user saw. It goes
     * unchanged into the command, so a proposal that went stale since it was displayed is rejected with
     * `ADJUSTMENT_NOT_AVAILABLE` and never replaced silently (ADR 0019).
     */
    data class AcceptAdjustment(
        val roomId: RoomId,
        val proposal: AdjustmentProposal,
        val confirmation: AdjustmentConfirmation,
    ) : CoreIntent

    /** Undoes the last confirmed command. */
    data object Undo : CoreIntent

    /** Redoes the last undone command. */
    data object Redo : CoreIntent

    /** Stores the pending changes again after [SaveStatus.Failed]. */
    data object RetrySave : CoreIntent
}

/**
 * One-time events for the screen (ARCHITECTURE section 4). Open for the same reason as [EditorIntent].
 */
interface EditorEffect {
    /**
     * An expected failure the user must hear about, for example a rejected command. The screen translates [error]
     * into a message.
     */
    data class Error(
        val error: DomainError,
    ) : EditorEffect
}
