package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import java.util.Locale

/** Parses a user-entered corner angle `α` with `0° < α ≤ 180°` (DOMAIN section 3, REQ-MEAS-004). */
fun interface AngleParser {
    /**
     * Parses [text] in degrees without rounding.
     *
     * Accepts `,`, `.` and the decimal separator of [locale]. Failures are `DomainError.InvalidValue` for
     * [ValueField.CORNER_ANGLE]: `MALFORMED`, `TOO_SMALL`, `TOO_LARGE`, or `NOT_REPRESENTABLE` for more than three
     * decimals.
     */
    fun parse(
        text: String,
        locale: Locale,
    ): DomainResult<Angle>
}

/** Default [AngleParser] with exact decimal arithmetic. */
object DecimalAngleParser : AngleParser {
    private val MAX_DEGREES =
        Angle.STRAIGHT.milliDegrees
            .toBigDecimal()
            .movePointLeft(DecimalText.MILLI_DEGREE_DECIMALS)

    override fun parse(
        text: String,
        locale: Locale,
    ): DomainResult<Angle> {
        val degrees = DecimalText.parseOrNull(text, DecimalText.separatorOf(locale))
        val angle = degrees?.let(Angle::ofDegreesExactOrNull)
        return when {
            degrees == null -> failure(InvalidValueReason.MALFORMED)
            degrees.signum() <= 0 -> failure(InvalidValueReason.TOO_SMALL)
            degrees > MAX_DEGREES -> failure(InvalidValueReason.TOO_LARGE)
            angle == null -> failure(InvalidValueReason.NOT_REPRESENTABLE)
            else -> DomainResult.Success(angle)
        }
    }

    private fun failure(reason: InvalidValueReason): DomainResult.Failure =
        DomainResult.Failure(DomainError.InvalidValue(ValueField.CORNER_ANGLE, reason))
}
