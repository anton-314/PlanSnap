package app.plansnap.core.common.units

import java.math.BigDecimal
import java.util.Locale

/**
 * Default [LengthFormatter]: exact decimal digits with the places of the unit ([LengthUnit.fromLength]), so `3450` mm
 * is `345,0` cm and `3,450` m. No unit suffix and no grouping separator.
 */
object DecimalLengthFormatter : LengthFormatter {
    override fun format(
        length: Length,
        unit: LengthUnit,
        locale: Locale,
    ): String = DecimalText.toLocalized(unit.fromLength(length), locale)
}

/**
 * Default [AngleFormatter]: exact decimal degrees up to the precision of a raw angle (ADR 0002, 0,001°), without
 * superfluous trailing zeros, so `90000` is `90`, `109471` is `109,471` and `45500` is `45,5`. No degree sign and
 * no grouping separator. The value is exact, so parsing the result gives the same [Angle] again.
 */
object DecimalAngleFormatter : AngleFormatter {
    override fun format(
        angle: Angle,
        locale: Locale,
    ): String =
        DecimalText.toLocalized(
            BigDecimal.valueOf(angle.milliDegrees.toLong(), DecimalText.MILLI_DEGREE_DECIMALS).stripTrailingZeros(),
            locale,
        )
}
