package app.plansnap.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.feature.settings.R

@Composable
internal fun ToleranceSection(
    state: SettingsUiState.Loaded,
    actions: SettingsActions,
) {
    Section(R.string.settings_section_tolerance) {
        Description(stringResource(R.string.settings_tolerance_description))
        ToleranceField(
            value = state.absoluteToleranceText,
            label = stringResource(R.string.settings_tolerance_absolute_label, stringResource(unitSymbol(state.unit))),
            error = state.absoluteToleranceError?.let { absoluteErrorText(it) },
            onValueChange = actions.onAbsoluteToleranceChanged,
        )
        ToleranceField(
            value = state.relativeToleranceText,
            label = stringResource(R.string.settings_tolerance_relative_label),
            error = state.relativeToleranceError?.let { relativeErrorText(it) },
            onValueChange = actions.onRelativeToleranceChanged,
        )
    }
}

@Composable
private fun absoluteErrorText(reason: InvalidValueReason): String =
    when (reason) {
        InvalidValueReason.TOO_SMALL -> {
            stringResource(
                R.string.settings_tolerance_error_absolute_too_small,
                GeometryTolerances.MIN_TOLERANCE_ABS_MM,
            )
        }

        InvalidValueReason.TOO_LARGE -> {
            stringResource(R.string.settings_tolerance_error_too_large)
        }

        InvalidValueReason.NOT_REPRESENTABLE -> {
            stringResource(R.string.settings_tolerance_error_not_representable)
        }

        InvalidValueReason.MALFORMED, InvalidValueReason.NOT_APPLICABLE -> {
            stringResource(R.string.settings_tolerance_error_malformed)
        }
    }

@Composable
private fun relativeErrorText(reason: InvalidValueReason): String =
    when (reason) {
        InvalidValueReason.TOO_SMALL -> {
            stringResource(R.string.settings_tolerance_error_relative_negative)
        }

        InvalidValueReason.TOO_LARGE -> {
            stringResource(R.string.settings_tolerance_error_relative_too_large)
        }

        InvalidValueReason.NOT_REPRESENTABLE -> {
            stringResource(R.string.settings_tolerance_error_too_large)
        }

        InvalidValueReason.MALFORMED, InvalidValueReason.NOT_APPLICABLE -> {
            stringResource(R.string.settings_tolerance_error_malformed)
        }
    }

@Composable
private fun ToleranceField(
    value: String,
    label: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().heightIn(min = PlanSnapTheme.sizes.minTouchTarget),
    )
}

@StringRes
private fun unitSymbol(unit: LengthUnit): Int =
    when (unit) {
        LengthUnit.MILLIMETER -> R.string.settings_unit_symbol_millimeter
        LengthUnit.CENTIMETER -> R.string.settings_unit_symbol_centimeter
        LengthUnit.METER -> R.string.settings_unit_symbol_meter
    }
