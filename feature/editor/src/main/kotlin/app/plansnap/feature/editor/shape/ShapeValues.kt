package app.plansnap.feature.editor.shape

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.DecimalAngleParser
import app.plansnap.core.common.units.DecimalLengthParser
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.geometry.primitives.GeometryTolerances
import java.util.Locale

/**
 * Interpretation of the values typed into the shape sheets. The sheets and their confirmation share it, so what the
 * sheet accepts is exactly what the command receives. Limits that depend on the room are checked by the command.
 */
internal object ShapeValues {
    /** Parses [text] as a corner angle in degrees, `0° < α ≤ 180°` (DOMAIN section 3). */
    fun parseAngle(
        text: String,
        locale: Locale,
    ): DomainResult<Angle> = DecimalAngleParser.parse(text.trim(), locale)

    /** Parses [text] as a wall thickness in [unit]. Zero is allowed, because it draws the wall as a line. */
    fun parseThickness(
        text: String,
        unit: LengthUnit,
    ): DomainResult<Length> =
        when (val parsed = DecimalLengthParser.parse(text.trim(), unit, ValueField.WALL_THICKNESS)) {
            is DomainResult.Failure -> {
                parsed
            }

            is DomainResult.Success -> {
                if (parsed.value.mm > GeometryTolerances.MAX_THICKNESS_MM) {
                    failure(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE)
                } else {
                    parsed
                }
            }
        }

    /** Parses [text] as the length of a diagonal in [unit]. A diagonal needs a positive length. */
    fun parseDiagonal(
        text: String,
        unit: LengthUnit,
    ): DomainResult<Length> =
        when (val parsed = DecimalLengthParser.parse(text.trim(), unit, ValueField.DIAGONAL_LENGTH)) {
            is DomainResult.Failure -> {
                parsed
            }

            is DomainResult.Success -> {
                if (parsed.value > Length.ZERO) {
                    parsed
                } else {
                    failure(ValueField.DIAGONAL_LENGTH, InvalidValueReason.TOO_SMALL)
                }
            }
        }

    /** The error to show for the typed value of [sheet], `null` for an empty or valid value. */
    fun errorOf(
        sheet: ShapeSheet,
        unit: LengthUnit,
        locale: Locale,
    ): DomainError? {
        if (sheet is ShapeSheet.Diagonal && sheet.from == sheet.to) {
            return DomainError.RuleViolation(DomainRule.DIAGONAL_INVALID_CORNERS)
        }
        val result =
            when {
                sheet.text.isBlank() -> null
                sheet is ShapeSheet.Corner -> parseAngle(sheet.text, locale)
                sheet is ShapeSheet.Diagonal -> parseDiagonal(sheet.text, unit)
                sheet is ShapeSheet.Thickness -> parseThickness(sheet.text, unit)
                else -> null
            }
        return (result as? DomainResult.Failure)?.error
    }

    private fun failure(
        field: ValueField,
        reason: InvalidValueReason,
    ): DomainResult.Failure = DomainResult.Failure(DomainError.InvalidValue(field, reason))
}
