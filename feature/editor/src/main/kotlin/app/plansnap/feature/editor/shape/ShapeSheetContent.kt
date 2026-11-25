package app.plansnap.feature.editor.shape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.input.CornerChoice
import app.plansnap.feature.editor.input.Keypad
import app.plansnap.feature.editor.input.KeypadActions
import app.plansnap.feature.editor.input.KeypadText
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import java.text.DecimalFormatSymbols
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * Content of the angle sheet: direction, the typed angle and the keypad.
 *
 * @param sheet the open sheet.
 * @param room the room whose corner is edited.
 * @param shape state of the sheets, which holds the error of the typed angle.
 * @param onIntent receives the taps.
 */
@Composable
internal fun CornerSheetContent(
    sheet: ShapeSheet.Corner,
    room: Room,
    shape: ShapeState,
    onIntent: (EditorIntent) -> Unit,
) {
    val subtitle =
        when (val target = sheet.target) {
            CornerTarget.Next -> stringResource(R.string.shape_corner_target_next, room.walls.size + 1)
            is CornerTarget.Existing -> stringResource(R.string.shape_corner_target_existing, target.index + 1)
        }
    val title = stringResource(R.string.shape_corner_title)
    ValueSheet(
        spec =
            ValueSheetSpec(
                title = title,
                subtitle = subtitle,
                hint = stringResource(R.string.shape_corner_hint),
                text = sheet.text,
                unitLabel = stringResource(R.string.shape_corner_unit),
                unitSpoken = stringResource(R.string.shape_corner_unit_spoken),
            ),
        shape = shape,
        onIntent = onIntent,
    ) {
        CornerChoice(
            selected = sheet.direction,
            enabled = true,
            onSelect = { onIntent(ShapeIntent.SheetDirectionChanged(it)) },
        )
    }
}

/**
 * Content of the diagonal sheet: the two corners, the typed length and the keypad.
 *
 * @param sheet the open sheet.
 * @param room the room that receives the diagonal.
 * @param unit unit of the typed length.
 * @param shape state of the sheets, which holds the error of the typed diagonal.
 * @param onIntent receives the taps.
 */
@Composable
internal fun DiagonalSheetContent(
    sheet: ShapeSheet.Diagonal,
    room: Room,
    unit: LengthUnit,
    shape: ShapeState,
    onIntent: (EditorIntent) -> Unit,
) {
    val title = stringResource(R.string.shape_diagonal_title)
    ValueSheet(
        spec =
            ValueSheetSpec(
                title = title,
                subtitle = null,
                hint = stringResource(R.string.shape_diagonal_hint),
                text = sheet.text,
                unitLabel = stringResource(unitRes(unit)),
                unitSpoken = stringResource(unitSpokenRes(unit)),
            ),
        shape = shape,
        onIntent = onIntent,
    ) {
        CornerChips(
            label = stringResource(R.string.shape_diagonal_from),
            count = room.walls.size,
            selected = sheet.from,
            onSelect = { onIntent(ShapeIntent.DiagonalCornersChanged(it, sheet.to)) },
        )
        CornerChips(
            label = stringResource(R.string.shape_diagonal_to),
            count = room.walls.size,
            selected = sheet.to,
            onSelect = { onIntent(ShapeIntent.DiagonalCornersChanged(sheet.from, it)) },
        )
    }
}

/**
 * Content of the thickness sheet: the typed thickness and the keypad.
 *
 * @param sheet the open sheet.
 * @param unit unit of the typed thickness.
 * @param shape state of the sheets, which holds the error of the typed thickness.
 * @param onIntent receives the taps.
 */
@Composable
internal fun ThicknessSheetContent(
    sheet: ShapeSheet.Thickness,
    unit: LengthUnit,
    shape: ShapeState,
    onIntent: (EditorIntent) -> Unit,
) {
    val title = stringResource(R.string.shape_thickness_title)
    ValueSheet(
        spec =
            ValueSheetSpec(
                title = title,
                subtitle = null,
                hint = stringResource(R.string.shape_thickness_hint),
                text = sheet.text,
                unitLabel = stringResource(unitRes(unit)),
                unitSpoken = stringResource(unitSpokenRes(unit)),
            ),
        shape = shape,
        onIntent = onIntent,
    )
}

/** What differs between the value sheets: their texts and the value typed so far. */
private class ValueSheetSpec(
    val title: String,
    val subtitle: String?,
    val hint: String,
    val text: String,
    val unitLabel: String,
    val unitSpoken: String,
)

@Composable
private fun ValueSheet(
    spec: ValueSheetSpec,
    shape: ShapeState,
    onIntent: (EditorIntent) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val locale = ComposeLocale.current.platformLocale
    val separator = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val resources = rememberResources()
    val applyDescription = stringResource(R.string.shape_sheet_apply_for, spec.title)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        SheetTitle(spec.title, spec.subtitle)
        extra()
        ValueDisplay(spec.text, spec.unitLabel, spec.unitSpoken, spec.title)
        shape.error?.let { ErrorLine(resources.errorMessage(it)) }
        Text(
            text = spec.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Keypad(
            separator = separator,
            enabled = true,
            canConfirm = shape.canConfirm,
            extraKeys = emptyList(),
            actions =
                KeypadActions(
                    onKey = { key ->
                        val next = KeypadText.apply(spec.text, key, separator)
                        if (next != spec.text) onIntent(ShapeIntent.SheetTextChanged(next))
                    },
                    onConfirm = { onIntent(ShapeIntent.ConfirmSheet) },
                    confirmDescription = applyDescription,
                ),
        )
    }
}

@Composable
internal fun SheetTitle(
    title: String,
    subtitle: String?,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueDisplay(
    text: String,
    unitLabel: String,
    unitSpoken: String,
    title: String,
) {
    val empty = text.isEmpty()
    val spoken =
        if (empty) {
            stringResource(R.string.shape_sheet_value_spoken, title, stringResource(R.string.shape_sheet_value_empty))
        } else {
            stringResource(R.string.shape_sheet_value_spoken, title, "$text $unitSpoken")
        }
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = if (empty) "0" else text,
            style = MaterialTheme.typography.headlineLarge,
            color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorLine(message: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = PlanSnapIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CornerChips(
    label: String,
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(text = label, style = MaterialTheme.typography.titleSmall)
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        items(count) { index ->
            val description = stringResource(R.string.shape_diagonal_corner, index + 1)
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text((index + 1).toString()) },
                leadingIcon =
                    if (index == selected) {
                        { Icon(imageVector = PlanSnapIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}
