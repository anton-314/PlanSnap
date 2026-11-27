package app.plansnap.feature.editor.rooms

import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.canvas.RoomGestureStart
import app.plansnap.feature.editor.canvas.RoomGestureUpdate
import app.plansnap.feature.editor.state.EditorIntent

/**
 * Intents of the rooms function: adding rooms, docking, grid and snapping, and the long-press gesture that moves and
 * rotates a room (REQ-MOD-006 to REQ-MOD-008, REQ-UX-010). A [RoomsHandler] consumes them.
 */
sealed interface RoomsIntent : EditorIntent {
    /** Adds an empty room next to the existing ones and selects it, so its walls can be entered. */
    data object AddRoom : RoomsIntent

    /** Opens the dock sheet for the selected room, if it has a wall and another room has one too. */
    data object OpenDock : RoomsIntent

    /** Closes the dock sheet or the selection of the host on the plan. */
    data object Dismiss : RoomsIntent

    /** Hides the dock sheet so the host wall or opening can be tapped on the plan. */
    data object StartPicking : RoomsIntent

    /** Returns from the selection on the plan to the dock sheet. */
    data object StopPicking : RoomsIntent

    /** Chooses [roomId] as host room. Wall and opening have to be chosen again. */
    data class SelectHostRoom(
        val roomId: RoomId,
    ) : RoomsIntent

    /** Chooses the wall [wallId] of [roomId] as host, or the opening [openingId] on that wall. */
    data class SelectHost(
        val roomId: RoomId,
        val wallId: WallId,
        val openingId: OpeningId? = null,
    ) : RoomsIntent

    /** Chooses the own wall that is placed against the host. */
    data class SelectOwnWall(
        val wallId: WallId,
    ) : RoomsIntent

    /** The typed offset changed to [text], digits and one separator, without sign. */
    data class OffsetChanged(
        val text: String,
    ) : RoomsIntent

    /** Chooses whether the offset runs against the measuring direction of the host wall. */
    data class OffsetNegative(
        val negative: Boolean,
    ) : RoomsIntent

    /** Docks the room as one command. */
    data object ConfirmDock : RoomsIntent

    /** Switches the grid on or off. It is drawn and snapped to at once. */
    data class SetGrid(
        val enabled: Boolean,
    ) : RoomsIntent

    /** Switches snapping to corners, walls and the grid on or off. */
    data class SetSnap(
        val enabled: Boolean,
    ) : RoomsIntent

    /** A long press on a room started a gesture. */
    data class GestureStarted(
        val start: RoomGestureStart,
    ) : RoomsIntent

    /** The gesture progressed. Only the preview changes, never the model. */
    data class GestureMoved(
        val update: RoomGestureUpdate,
    ) : RoomsIntent

    /** All fingers were lifted: the room is placed with exactly one command. */
    data object GestureEnded : RoomsIntent

    /** The gesture was interrupted: nothing is placed. */
    data object GestureCancelled : RoomsIntent

    /** A tap during the selection of the host hit nothing usable. It changes nothing. */
    data object PickIgnored : RoomsIntent
}
