package app.plansnap.feature.editor.state

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.DecimalLengthParser
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room

/** Interpretation of the running input as the next wall. The preview and the confirmation share it. */
internal object WallInput {
    /**
     * Parses [text] as a wall length in [unit]. A length of zero is rejected here, because a wall needs a positive
     * length to exist at all. The limits of the model are checked by the command that adds the wall.
     */
    fun parseLength(
        text: String,
        unit: LengthUnit,
    ): DomainResult<Length> =
        when (val parsed = DecimalLengthParser.parse(text.trim(), unit, ValueField.WALL_LENGTH)) {
            is DomainResult.Failure -> {
                parsed
            }

            is DomainResult.Success -> {
                if (parsed.value > Length.ZERO) {
                    parsed
                } else {
                    DomainResult.Failure(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL))
                }
            }
        }

    /** Corner entered before the next wall: unspecified for the first wall, otherwise [nextCorner]. */
    fun startCorner(
        room: Room,
        nextCorner: CornerSpec,
    ): CornerSpec = if (room.walls.isEmpty()) CornerSpec.Unspecified else nextCorner
}
