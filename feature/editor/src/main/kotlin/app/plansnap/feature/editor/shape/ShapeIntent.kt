package app.plansnap.feature.editor.shape

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.feature.editor.state.EditorIntent

/** Which corner the angle sheet edits. */
sealed interface CornerTarget {
    /** The corner that precedes the next wall. It is only stored in the editor state until the wall is confirmed. */
    data object Next : CornerTarget

    /** The stored corner `V_index` of the selected room. */
    data class Existing(
        val index: Int,
    ) : CornerTarget
}

/**
 * Intents of the shape sheets: corner angle, diagonal, thickness, closing and adjustment (DOMAIN sections 4 to 6,
 * UX section 3). A [ShapeHandler] consumes them. Accepting an adjustment is no intent of its own: the sheet sends
 * `CoreIntent.AcceptAdjustment`, which evaluates the proposal again right before the command runs (ADR 0019).
 */
sealed interface ShapeIntent : EditorIntent {
    /** Opens the angle sheet for [target]. */
    data class OpenCorner(
        val target: CornerTarget = CornerTarget.Next,
    ) : ShapeIntent

    /** Opens the diagonal sheet, optionally with the recommended corners. */
    data class OpenDiagonal(
        val from: Int? = null,
        val to: Int? = null,
    ) : ShapeIntent

    /** Opens the thickness sheet for the next wall. */
    data object OpenThickness : ShapeIntent

    /** Opens the adjustment sheet of the selected room, if it has a proposal. */
    data object OpenAdjustment : ShapeIntent

    /** Closes the open sheet and the confirmation dialog. */
    data object Dismiss : ShapeIntent

    /** The value of the open sheet changed to [text], exactly as typed. */
    data class SheetTextChanged(
        val text: String,
    ) : ShapeIntent

    /** The angle sheet turns left or right. */
    data class SheetDirectionChanged(
        val direction: TurnDirection,
    ) : ShapeIntent

    /** The diagonal sheet connects the corners [from] and [to]. */
    data class DiagonalCornersChanged(
        val from: Int,
        val to: Int,
    ) : ShapeIntent

    /** Stores the value of the open sheet. */
    data object ConfirmSheet : ShapeIntent

    /** Closes the selected room, which evaluates it (UX section 3). */
    data object CloseRoom : ShapeIntent

    /** Asks for the deliberate confirmation of a large correction (ADR 0019). */
    data object RequestExplicitConfirmation : ShapeIntent

    /** Withdraws the request for the deliberate confirmation. */
    data object CancelExplicitConfirmation : ShapeIntent

    /** Removes the confirmed adjustment of the selected room. The raw values stay as they are. */
    data object RevertAdjustment : ShapeIntent
}
