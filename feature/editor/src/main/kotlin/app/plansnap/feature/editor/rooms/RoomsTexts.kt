package app.plansnap.feature.editor.rooms

import android.content.res.Resources
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.errorMessage

// Texts of the rooms function. Corners and walls are shown one-based, like in the shape sheets.

/** Name of [room] as the user entered it, or "Room n" for the [index]-th room (zero-based) without a name. */
internal fun Resources.roomLabel(
    room: Room,
    index: Int,
): String = room.name.ifBlank { getString(R.string.rooms_room_default, index + 1) }

/**
 * Label of [opening], such as "Door on wall 2, at 50 cm", where [wallNumber] is the one-based number of its wall and
 * [start] the formatted distance of the opening from the start of that wall. It tells openings on one wall apart.
 */
internal fun Resources.openingLabel(
    opening: Opening,
    wallNumber: Int,
    start: String,
): String =
    getString(
        when (opening.type) {
            OpeningType.DOOR -> R.string.rooms_dock_opening_door
            OpeningType.WINDOW -> R.string.rooms_dock_opening_window
            OpeningType.PASSAGE -> R.string.rooms_dock_opening_passage
        },
        wallNumber,
        start,
    )

/**
 * Explanation of a rejected dock. The cycle and the opening on another wall get words that fit the sheet, every other
 * error uses the general texts of the editor.
 */
internal fun Resources.dockErrorMessage(error: DomainError): String =
    when ((error as? DomainError.RuleViolation)?.rule) {
        DomainRule.DOCK_CYCLE -> getString(R.string.rooms_error_dock_cycle)
        DomainRule.DOCK_OPENING_NOT_ON_WALL -> getString(R.string.rooms_error_dock_opening_not_on_wall)
        else -> errorMessage(error)
    }
