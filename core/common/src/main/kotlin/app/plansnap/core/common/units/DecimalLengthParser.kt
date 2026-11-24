package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import java.math.BigDecimal

/**
 * Default [LengthParser] with exact decimal arithmetic.
 *
 * Accepts `,` and `.` as decimal separator independent of the locale, because [LengthParser] has no locale parameter.
 * Input is never rounded: a value finer than 1 mm is `NOT_REPRESENTABLE`, as with [DecimalAngleParser]. Zero is
 * accepted, because minimum lengths belong to the consuming model.
 */
object DecimalLengthParser : LengthParser {
    override fun parse(
        text: String,
        unit: LengthUnit,
        field: ValueField,
    ): DomainResult<Length> {
        val value = DecimalText.parseOrNull(text, '.')
        val length = value?.let(unit::toLengthExactOrNull)
        return when {
            value == null -> failure(field, InvalidValueReason.MALFORMED)
            value.signum() < 0 -> failure(field, InvalidValueReason.TOO_SMALL)
            length != null -> DomainResult.Success(length)
            hasFractionInMm(value, unit) -> failure(field, InvalidValueReason.NOT_REPRESENTABLE)
            else -> failure(field, InvalidValueReason.TOO_LARGE)
        }
    }

    private fun hasFractionInMm(
        value: BigDecimal,
        unit: LengthUnit,
    ): Boolean = value.multiply(BigDecimal.valueOf(unit.mmPerUnit)).stripTrailingZeros().scale() > 0

    private fun failure(
        field: ValueField,
        reason: InvalidValueReason,
    ): DomainResult.Failure = DomainResult.Failure(DomainError.InvalidValue(field, reason))
}
