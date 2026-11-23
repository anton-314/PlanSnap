package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainResult
import java.math.BigDecimal
import java.util.Locale

private const val MM_PER_CENTIMETER = 10L
private const val MM_PER_METER = 1_000L
private const val CENTIMETER_DECIMALS = 1
private const val METER_DECIMALS = 3

/** Display and input unit. Conversion happens only at the input, display and export edges (REQ-NFR-003). */
enum class LengthUnit(
    val mmPerUnit: Long,
    private val decimals: Int,
) {
    /** Millimeters. */
    MILLIMETER(1L, 0),

    /** Centimeters, the default input unit (REQ-MEAS-002). */
    CENTIMETER(MM_PER_CENTIMETER, CENTIMETER_DECIMALS),

    /** Meters. */
    METER(MM_PER_METER, METER_DECIMALS),
    ;

    /**
     * Converts a decimal value in this unit to a raw length without rounding.
     *
     * @return the length, or `null` if it is finer than 1 mm or exceeds the [Long] range.
     */
    fun toLengthExactOrNull(value: BigDecimal): Length? =
        try {
            Length(value.multiply(BigDecimal.valueOf(mmPerUnit)).longValueExact())
        } catch (_: ArithmeticException) {
            null
        }

    /** Converts a raw length to an exact decimal value in this unit. */
    fun fromLength(length: Length): BigDecimal = BigDecimal.valueOf(length.mm, decimals)
}

/** Parses user-entered length text. Accepts both `,` and `.` as decimal separator. */
fun interface LengthParser {
    /** Parses [text] in [unit], returning a domain error for malformed or unrepresentable input. */
    fun parse(
        text: String,
        unit: LengthUnit,
    ): DomainResult<Length>
}

/** Formats a raw length for display. */
fun interface LengthFormatter {
    /** Formats [length] in [unit] using the number format of [locale]. */
    fun format(
        length: Length,
        unit: LengthUnit,
        locale: Locale,
    ): String
}

/** Formats a raw angle for display. */
fun interface AngleFormatter {
    /** Formats [angle] using the number format of [locale]. */
    fun format(
        angle: Angle,
        locale: Locale,
    ): String
}
