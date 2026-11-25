package app.plansnap.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.settings.R

@Composable
internal fun UnitSection(
    selected: LengthUnit,
    onSelected: (LengthUnit) -> Unit,
) {
    Section(R.string.settings_section_unit) {
        Description(stringResource(R.string.settings_unit_description))
        Column(modifier = Modifier.selectableGroup()) {
            LengthUnit.entries.forEach { unit ->
                UnitRow(unit, unit == selected) { onSelected(unit) }
            }
        }
    }
}

@Composable
private fun UnitRow(
    unit: LengthUnit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PlanSnapTheme.sizes.minTouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = stringResource(unitLabel(unit)), style = MaterialTheme.typography.bodyLarge)
    }
}

@StringRes
private fun unitLabel(unit: LengthUnit): Int =
    when (unit) {
        LengthUnit.MILLIMETER -> R.string.settings_unit_millimeter
        LengthUnit.CENTIMETER -> R.string.settings_unit_centimeter
        LengthUnit.METER -> R.string.settings_unit_meter
    }

@Composable
internal fun GridSection(
    state: SettingsUiState.Loaded,
    actions: SettingsActions,
) {
    Section(R.string.settings_section_grid) {
        SwitchRow(stringResource(R.string.settings_grid_show), state.gridEnabled, actions.onGridChanged)
        SwitchRow(stringResource(R.string.settings_snap_enable), state.snapEnabled, actions.onSnapChanged)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PlanSnapTheme.sizes.minTouchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun HintsSection(
    dismissedCount: Int,
    onReset: () -> Unit,
) {
    Section(R.string.settings_section_hints) {
        Description(
            if (dismissedCount == 0) {
                stringResource(R.string.settings_hints_all_active)
            } else {
                pluralStringResource(R.plurals.settings_hints_dismissed, dismissedCount, dismissedCount)
            },
        )
        PlanSnapOutlinedButton(
            text = stringResource(R.string.settings_hints_reset),
            onClick = onReset,
            enabled = dismissedCount > 0,
        )
    }
}
