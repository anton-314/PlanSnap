package app.plansnap.feature.editor.details

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.DecimalLengthParser
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.model.AreaFactor

private const val MAX_FACTOR_PERCENT = 100

/**
 * Interpretation of the values typed into the details sheets. The sheets and their confirmation share it, so what a
 * field accepts is exactly what the command receives. Whether a height is possible for the room is decided by the
 * command.
 */
internal object DetailsValues {
    /** Longest room name in characters. */
    const val MAX_NAME_CHARS = 60

    /** The error of a name that is longer than [MAX_NAME_CHARS]. */
    val NAME_TOO_LONG = DomainError.InvalidValue(ValueField.NAME, InvalidValueReason.TOO_LARGE)

    private val DIGITS = Regex("""\d+""")
    private val NEGATIVE = Regex("""-\d+([.,]\d+)?""")
    private val FRACTION = Regex("""\d+[.,]\d+""")

    /** The name without surrounding blanks. An empty name is allowed: the room is then shown by its number. */
    fun parseName(text: String): DomainResult<String> {
        val name = text.trim()
        return if (name.length > MAX_NAME_CHARS) {
            DomainResult.Failure(NAME_TOO_LONG)
        } else {
            DomainResult.Success(name)
        }
    }

    /** The room height in [unit], `null` for an empty field, which means the project default height. */
    fun parseRoomHeight(
        text: String,
        unit: LengthUnit,
    ): DomainResult<Length?> =
        if (text.isBlank()) {
            DomainResult.Success(null)
        } else {
            parsePositive(text, unit, ValueField.ROOM_HEIGHT)
        }

    /** The default height of the project in [unit]. It cannot be empty. */
    fun parseProjectHeight(
        text: String,
        unit: LengthUnit,
    ): DomainResult<Length> =
        if (text.isBlank()) {
            failure(ValueField.ROOM_HEIGHT, InvalidValueReason.MALFORMED)
        } else {
            parsePositive(text, unit, ValueField.ROOM_HEIGHT)
        }

    /** The area factor in whole percent from 0 to 100. A trailing percent sign is accepted. */
    fun parseFactor(text: String): DomainResult<AreaFactor> {
        val value = text.trim().removeSuffix("%").trim()
        val reason =
            when {
                DIGITS.matches(value) -> return digitsToFactor(value)
                NEGATIVE.matches(value) -> InvalidValueReason.TOO_SMALL
                FRACTION.matches(value) -> InvalidValueReason.NOT_REPRESENTABLE
                else -> InvalidValueReason.MALFORMED
            }
        return failure(ValueField.AREA_FACTOR, reason)
    }

    /**
     * The errors of the typed values of [sheet] in [unit]. An empty factor has no error while typing, it only blocks
     * applying.
     */
    fun errorsOf(
        sheet: DetailsSheet.RoomDetails,
        unit: LengthUnit,
    ): Map<DetailsField, DomainError> =
        buildMap {
            put(DetailsField.NAME, parseName(sheet.name))
            put(DetailsField.HEIGHT, parseRoomHeight(sheet.heightText, unit))
            if (sheet.factorText.isNotBlank()) put(DetailsField.FACTOR, parseFactor(sheet.factorText))
        }.mapNotNull { (field, result) -> (result as? DomainResult.Failure)?.let { field to it.error } }.toMap()

    /** The error of the typed default height of [sheet] in [unit], if there is one while typing. */
    fun errorsOf(
        sheet: DetailsSheet.Overview,
        unit: LengthUnit,
    ): Map<DetailsField, DomainError> =
        if (sheet.projectHeightText.isBlank()) {
            emptyMap()
        } else {
            (parseProjectHeight(sheet.projectHeightText, unit) as? DomainResult.Failure)
                ?.let { mapOf(DetailsField.PROJECT_HEIGHT to it.error) }
                .orEmpty()
        }

    private fun digitsToFactor(value: String): DomainResult<AreaFactor> {
        val percent = value.toIntOrNull()
        return if (percent == null || percent > MAX_FACTOR_PERCENT) {
            failure(ValueField.AREA_FACTOR, InvalidValueReason.TOO_LARGE)
        } else {
            DomainResult.Success(AreaFactor(percent))
        }
    }

    private fun parsePositive(
        text: String,
        unit: LengthUnit,
        field: ValueField,
    ): DomainResult<Length> =
        when (val parsed = DecimalLengthParser.parse(text.trim(), unit, field)) {
            is DomainResult.Failure -> parsed
            is DomainResult.Success -> positive(parsed, field)
        }

    private fun positive(
        parsed: DomainResult.Success<Length>,
        field: ValueField,
    ): DomainResult<Length> = if (parsed.value > Length.ZERO) parsed else failure(field, InvalidValueReason.TOO_SMALL)

    private fun failure(
        field: ValueField,
        reason: InvalidValueReason,
    ): DomainResult.Failure = DomainResult.Failure(DomainError.InvalidValue(field, reason))
}
