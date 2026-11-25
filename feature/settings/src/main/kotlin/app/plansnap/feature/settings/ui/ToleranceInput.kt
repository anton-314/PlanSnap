package app.plansnap.feature.settings.ui

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.DecimalLengthParser
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.geometry.primitives.GeometryTolerances
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val PERCENT = 100
private const val MAX_INPUT_CHARS = 32
private val PERCENT_FORMAT = Regex("""[-+]?[0-9]+(\.[0-9]+)?""")

/** Result of checking typed text against the domain limits. */
internal sealed interface ParsedInput<out T> {
    /** The text is valid and stands for [value]. */
    data class Accepted<T>(
        val value: T,
    ) : ParsedInput<T>

    /** The text is rejected for [reason] and must not be stored. */
    data class Rejected(
        val reason: InvalidValueReason,
    ) : ParsedInput<Nothing>
}

/** Text of the two tolerance fields while the user types, `null` while a field mirrors the stored value. */
internal data class ToleranceDrafts(
    val absoluteText: String? = null,
    val absoluteError: InvalidValueReason? = null,
    val relativeText: String? = null,
    val relativeError: InvalidValueReason? = null,
)

/** Parsing and formatting of the closure tolerance fields, the only place that knows the domain limits. */
internal object ToleranceInput {
    /** Parses the absolute limit in [unit]; the result in mm is at least [GeometryTolerances.MIN_TOLERANCE_ABS_MM]. */
    fun parseAbsolute(
        text: String,
        unit: LengthUnit,
    ): ParsedInput<Long> =
        when (val result = DecimalLengthParser.parse(text, unit, ValueField.TOLERANCE)) {
            is DomainResult.Failure -> {
                ParsedInput.Rejected(reasonOf(result.error))
            }

            is DomainResult.Success -> {
                if (result.value.mm < GeometryTolerances.MIN_TOLERANCE_ABS_MM) {
                    ParsedInput.Rejected(InvalidValueReason.TOO_SMALL)
                } else {
                    ParsedInput.Accepted(result.value.mm)
                }
            }
        }

    /** Parses the relative limit entered in percent; the result is a fraction in `[0, 1]`. */
    fun parseRelative(text: String): ParsedInput<Double> {
        val normalized = text.trim().replace(',', '.')
        val percent =
            normalized.takeIf { it.length <= MAX_INPUT_CHARS && PERCENT_FORMAT.matches(it) }?.let(::BigDecimal)
        return when {
            percent == null -> ParsedInput.Rejected(InvalidValueReason.MALFORMED)
            percent.signum() < 0 -> ParsedInput.Rejected(InvalidValueReason.TOO_SMALL)
            percent > BigDecimal(PERCENT) -> ParsedInput.Rejected(InvalidValueReason.TOO_LARGE)
            else -> ParsedInput.Accepted(percent.movePointLeft(2).toDouble())
        }
    }

    /** Formats the stored absolute limit [absoluteMm] in [unit] for the device language. */
    fun formatAbsolute(
        absoluteMm: Long,
        unit: LengthUnit,
    ): String = DecimalLengthFormatter.format(Length(absoluteMm), unit, Locale.getDefault())

    /** Formats the stored relative limit [relative] (a fraction) as percent for the device language. */
    fun formatRelative(relative: Double): String {
        val separator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        return BigDecimal(relative.toString())
            .movePointRight(2)
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', separator)
    }

    private fun reasonOf(error: DomainError): InvalidValueReason =
        (error as? DomainError.InvalidValue)?.reason ?: InvalidValueReason.MALFORMED
}

internal fun <T> ParsedInput<T>.accepted(): T? = (this as? ParsedInput.Accepted)?.value

internal fun ParsedInput<*>.rejection(): InvalidValueReason? = (this as? ParsedInput.Rejected)?.reason
