package app.plansnap.core.common.units

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Decimal text handling shared by the length and angle parsers and formatters. */
internal object DecimalText {
    /** Longest accepted input in characters, counted after trimming. */
    const val MAX_INPUT_CHARS = 32

    /** Decimal places of a raw angle in degrees (ADR 0002, 0,001°). */
    const val MILLI_DEGREE_DECIMALS = 3

    private val NUMBER = Regex("""-?[0-9]+(\.[0-9]+)?""")

    /**
     * Parses a signed plain decimal without rounding. `,`, `.` and [separator] are accepted as decimal separator.
     *
     * @return the exact value, or `null` if the text is blank, longer than [MAX_INPUT_CHARS] or not a plain decimal.
     */
    fun parseOrNull(
        text: String,
        separator: Char,
    ): BigDecimal? {
        val normalized = text.trim().replace(separator, '.').replace(',', '.')
        return normalized.takeIf { it.length <= MAX_INPUT_CHARS && NUMBER.matches(it) }?.let(::BigDecimal)
    }

    /** Decimal separator of [locale]. */
    fun separatorOf(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /** Writes [value] as plain digits with the decimal separator of [locale] and no grouping. */
    fun toLocalized(
        value: BigDecimal,
        locale: Locale,
    ): String = value.toPlainString().replace('.', separatorOf(locale))
}
