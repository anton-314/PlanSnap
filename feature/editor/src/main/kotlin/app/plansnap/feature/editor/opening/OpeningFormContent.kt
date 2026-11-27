package app.plansnap.feature.editor.opening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.SheetTitle
import app.plansnap.feature.editor.shape.errorMessage
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.shape.unitRes
import app.plansnap.feature.editor.state.EditorIntent

/**
 * Content of the form of one opening: type, wall, the typed values and the explanation why it cannot be stored.
 *
 * @param room room that carries the opening.
 * @param sheet the open form.
 * @param opening state of the sheets, which holds the errors of the typed values.
 * @param unit unit of the typed lengths.
 * @param onIntent receives the taps and typed values.
 */
@Composable
internal fun OpeningFormContent(
    room: Room,
    sheet: OpeningSheet.Form,
    opening: OpeningState,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val draft = sheet.draft
    val wallNumber = room.walls.indexOfFirst { it.id == draft.wallId } + 1
    val unitLabel = stringResource(unitRes(unit))
    val title = stringResource(if (sheet.editingId == null) R.string.opening_title_new else R.string.opening_title_edit)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        SheetTitle(title, stringResource(R.string.opening_wall_title, wallNumber))
        TypeChips(draft.type, onIntent)
        WallChips(room, wallNumber - 1, onIntent)
        PlacementFields(draft, wallNumber, unitLabel, onIntent)
        // Directly below the required values, so that a conflict is seen while they are typed.
        opening.valueError?.let { ProblemLine(it, conflict = false) }
        opening.conflict?.let { ProblemLine(it, conflict = true) }
        OptionalFields(draft, unitLabel, onIntent)
        if (draft.type == OpeningType.DOOR) SwingChips(draft.swing, onIntent)
        PlanSnapButton(
            text = stringResource(R.string.opening_save),
            onClick = { onIntent(OpeningIntent.Confirm) },
            enabled = opening.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sheet.editingId != null) {
            PlanSnapOutlinedButton(
                text = stringResource(R.string.opening_delete),
                onClick = { onIntent(OpeningIntent.RequestDelete) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Offset and width, which every opening needs. */
@Composable
private fun PlacementFields(
    draft: OpeningDraft,
    wallNumber: Int,
    unitLabel: String,
    onIntent: (EditorIntent) -> Unit,
) {
    NumberField(
        value = draft.offset,
        label = stringResource(R.string.opening_field_offset, unitLabel),
        hint = stringResource(R.string.opening_field_offset_hint, wallNumber),
        onChange = { onIntent(OpeningIntent.OffsetChanged(it)) },
    )
    NumberField(
        value = draft.width,
        label = stringResource(R.string.opening_field_width, unitLabel),
        onChange = { onIntent(OpeningIntent.WidthChanged(it)) },
    )
}

/** Height, and the sill of a window; each may stay blank (REQ-MOD-002). */
@Composable
private fun OptionalFields(
    draft: OpeningDraft,
    unitLabel: String,
    onIntent: (EditorIntent) -> Unit,
) {
    NumberField(
        value = draft.height,
        label = stringResource(R.string.opening_field_height, unitLabel),
        hint = stringResource(R.string.opening_field_optional),
        onChange = { onIntent(OpeningIntent.HeightChanged(it)) },
    )
    if (draft.type == OpeningType.WINDOW) {
        NumberField(
            value = draft.sill,
            label = stringResource(R.string.opening_field_sill, unitLabel),
            hint = stringResource(R.string.opening_field_optional),
            onChange = { onIntent(OpeningIntent.SillChanged(it)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeChips(
    selected: OpeningType,
    onIntent: (EditorIntent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        OpeningType.entries.forEach { type ->
            ChoiceChip(
                label = stringResource(typeRes(type)),
                selected = type == selected,
                onClick = { onIntent(OpeningIntent.TypeChanged(type)) },
            )
        }
    }
}

@Composable
private fun WallChips(
    room: Room,
    selected: Int,
    onIntent: (EditorIntent) -> Unit,
) {
    Text(text = stringResource(R.string.opening_field_wall), style = MaterialTheme.typography.titleSmall)
    // Opens at the chosen wall, which may be far to the right in a room with many walls.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceAtLeast(0))
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        itemsIndexed(room.walls) { index, wall ->
            ChoiceChip(
                label = stringResource(R.string.opening_wall_chip, index + 1),
                selected = index == selected,
                onClick = { onIntent(OpeningIntent.WallChanged(wall.id)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwingChips(
    selected: DoorSwing?,
    onIntent: (EditorIntent) -> Unit,
) {
    Text(text = stringResource(R.string.opening_field_swing), style = MaterialTheme.typography.titleSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        (listOf<DoorSwing?>(null) + DoorSwing.entries).forEach { swing ->
            ChoiceChip(
                label = stringResource(swingRes(swing)),
                selected = swing == selected,
                onClick = { onIntent(OpeningIntent.SwingChanged(swing)) },
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.padding(vertical = PlanSnapTheme.spacing.xs)) },
        leadingIcon =
            if (selected) {
                { Icon(imageVector = PlanSnapIcons.Check, contentDescription = null) }
            } else {
                null
            },
        modifier = Modifier.heightIn(min = MIN_TARGET),
    )
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        // Explanations go below the field: a label that wraps would cover the content above at large font sizes.
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_FIELD),
    )
}

/** A rejected value or rule, in words, with a symbol so that it is not only told by color (UX section 6). */
@Composable
private fun ProblemLine(
    error: DomainError,
    conflict: Boolean,
) {
    val message = rememberResources().errorMessage(error)
    val color = if (conflict) PlanSnapTheme.colors.conflict else MaterialTheme.colorScheme.error
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (conflict) PlanSnapIcons.Conflict else PlanSnapIcons.Warning,
            contentDescription = null,
            tint = color,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

private val MIN_TARGET = 48.dp
private val MIN_FIELD = 56.dp
