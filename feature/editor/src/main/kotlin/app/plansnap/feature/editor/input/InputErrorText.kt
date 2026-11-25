package app.plansnap.feature.editor.input

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.feature.editor.R

/** Returns the message that explains why the typed text cannot be appended as a wall. */
@Composable
internal fun inputErrorText(error: DomainError): String = stringResource(inputErrorRes(error))

/** String resource that explains [error]. */
@StringRes
internal fun inputErrorRes(error: DomainError): Int =
    when (error) {
        is DomainError.InvalidValue -> {
            when (error.reason) {
                InvalidValueReason.MALFORMED -> R.string.input_error_malformed
                InvalidValueReason.TOO_SMALL -> R.string.input_error_too_small
                InvalidValueReason.TOO_LARGE -> R.string.input_error_too_large
                InvalidValueReason.NOT_REPRESENTABLE -> R.string.input_error_not_representable
                InvalidValueReason.NOT_APPLICABLE -> R.string.input_error_generic
            }
        }

        is DomainError.RuleViolation -> {
            when (error.rule) {
                DomainRule.TOO_MANY_WALLS -> R.string.input_error_too_many_walls
                DomainRule.ROOM_ALREADY_CLOSED -> R.string.input_error_room_closed
                else -> R.string.input_error_generic
            }
        }

        else -> {
            R.string.input_error_generic
        }
    }
