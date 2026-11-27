package app.plansnap.feature.editor.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.common.units.DecimalLengthParser
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType

// Reading the typed values of the opening form. Nothing is rounded, a value finer than 1 mm is rejected.

/** The typed values of this draft as the opening [id], or why a value is not accepted. */
internal fun OpeningDraft.toOpening(
    id: OpeningId,
    unit: LengthUnit,
): DomainResult<Opening> {
    val start = length(offset, unit, ValueField.OPENING_OFFSET, minimum = Length.ZERO)
    val span = length(width, unit, ValueField.OPENING_WIDTH, minimum = Length(1))
    val tall = optional(height, unit, ValueField.OPENING_HEIGHT, minimum = Length(1))
    val parapet =
        if (type == OpeningType.WINDOW) {
            optional(sill, unit, ValueField.SILL_HEIGHT, minimum = Length.ZERO)
        } else {
            DomainResult.Success(null)
        }
    return start.flatMap { from ->
        span.flatMap { across ->
            tall.flatMap { high ->
                parapet.flatMap { below ->
                    if (from.mm > Long.MAX_VALUE - across.mm) {
                        DomainResult.Failure(
                            DomainError.InvalidValue(ValueField.OPENING_OFFSET, InvalidValueReason.TOO_LARGE),
                        )
                    } else {
                        DomainResult.Success(
                            Opening(
                                id = id,
                                wallId = wallId,
                                type = type,
                                offset = from,
                                width = across,
                                height = high,
                                sillHeight = below,
                                swing = swing.takeIf { type == OpeningType.DOOR },
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun length(
    text: String,
    unit: LengthUnit,
    field: ValueField,
    minimum: Length,
): DomainResult<Length> =
    when (val parsed = DecimalLengthParser.parse(text.trim(), unit, field)) {
        is DomainResult.Failure -> {
            parsed
        }

        is DomainResult.Success -> {
            if (parsed.value >= minimum) {
                parsed
            } else {
                DomainResult.Failure(DomainError.InvalidValue(field, InvalidValueReason.TOO_SMALL))
            }
        }
    }

private fun optional(
    text: String,
    unit: LengthUnit,
    field: ValueField,
    minimum: Length,
): DomainResult<Length?> =
    if (text.isBlank()) {
        DomainResult.Success(null)
    } else {
        when (val parsed = length(text, unit, field, minimum)) {
            is DomainResult.Failure -> parsed
            is DomainResult.Success -> DomainResult.Success(parsed.value)
        }
    }
