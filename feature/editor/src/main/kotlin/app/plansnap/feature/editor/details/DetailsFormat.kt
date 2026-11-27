package app.plansnap.feature.editor.details

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private const val AREA_DECIMALS = 2
private const val SQUARE_MM_TO_SQUARE_M_SHIFT = 6
private const val CUBIC_MM_TO_CUBIC_M_SHIFT = 9

/**
 * Display formats of the overview. Values arrive unrounded and are rounded here, half up, as the last step
 * (DOMAIN section 7). The conversion uses the exact decimal value of the double, so a value that lies exactly on a
 * half is rounded up and never down by binary representation error.
 */
internal object DetailsFormat {
    /** Area in square meters with two decimals, without unit. */
    fun area(
        mm2: Double,
        locale: Locale,
    ): String = fixed(BigDecimal(mm2).movePointLeft(SQUARE_MM_TO_SQUARE_M_SHIFT), locale)

    /** Volume in cubic meters with two decimals, without unit. */
    fun volume(
        mm3: Double,
        locale: Locale,
    ): String = fixed(BigDecimal(mm3).movePointLeft(CUBIC_MM_TO_CUBIC_M_SHIFT), locale)

    private fun fixed(
        value: BigDecimal,
        locale: Locale,
    ): String =
        NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = AREA_DECIMALS
                maximumFractionDigits = AREA_DECIMALS
                roundingMode = RoundingMode.HALF_UP
                isGroupingUsed = false
            }.format(value)
}
