package app.plansnap.feature.editor.opening

import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.state.EditorIntent

/**
 * Intents of the opening sheets (DOMAIN section 9, UX section 3). An [OpeningHandler] consumes them. Storing,
 * changing and removing are commands, so every change can be undone.
 */
sealed interface OpeningIntent : EditorIntent {
    /** Opens the form of the stored opening [openingId]. */
    data class Edit(
        val roomId: RoomId,
        val openingId: OpeningId,
    ) : OpeningIntent

    /** Opens the list of the openings of [wallId], from which one is changed or a new one is added. */
    data class OpenWall(
        val roomId: RoomId,
        val wallId: WallId,
    ) : OpeningIntent

    /** Opens the form of a new opening of [type] on [wallId], filled with usual values. */
    data class Add(
        val roomId: RoomId,
        val wallId: WallId,
        val type: OpeningType,
    ) : OpeningIntent

    /**
     * Opens the form of a new opening of [type] on the last wall of the selected room, where the form can move it to
     * another wall. Nothing happens without a selected room that has a wall.
     */
    data class AddToSelectedRoom(
        val type: OpeningType,
    ) : OpeningIntent

    /** Closes the open sheet and the dialog. */
    data object Dismiss : OpeningIntent

    /** A value of the open form changed. The form is checked again. */
    sealed interface FormChange : OpeningIntent

    /** The form changes the type of the opening. */
    data class TypeChanged(
        val type: OpeningType,
    ) : FormChange

    /** The form moves the opening to [wallId]. */
    data class WallChanged(
        val wallId: WallId,
    ) : FormChange

    /** The typed offset changed to [text], exactly as typed. */
    data class OffsetChanged(
        val text: String,
    ) : FormChange

    /** The typed width changed to [text], exactly as typed. */
    data class WidthChanged(
        val text: String,
    ) : FormChange

    /** The typed height changed to [text], exactly as typed. */
    data class HeightChanged(
        val text: String,
    ) : FormChange

    /** The typed sill height changed to [text], exactly as typed. */
    data class SillChanged(
        val text: String,
    ) : FormChange

    /** The form chooses the door swing, `null` leaves it unspecified. */
    data class SwingChanged(
        val swing: DoorSwing?,
    ) : FormChange

    /** Stores the form as a command and closes the sheet. A form with a conflict is not stored. */
    data object Confirm : OpeningIntent

    /** Asks for the deletion of the opening of the form. */
    data object RequestDelete : OpeningIntent

    /** Withdraws the request for the deletion. */
    data object CancelDelete : OpeningIntent

    /** Removes the opening of the form. */
    data object ConfirmDelete : OpeningIntent
}
