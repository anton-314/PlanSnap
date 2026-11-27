package app.plansnap.feature.editor.details

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.errorMessage
import app.plansnap.feature.editor.shape.lengthText
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.shape.unitRes
import app.plansnap.feature.editor.state.EditorIntent

private const val NOTES_MIN_LINES = 3

/**
 * Content of the room details sheet: name, type, note, height and area factor of one room. Nothing is stored before
 * the apply button, which sends [DetailsIntent.ApplyRoomDetails].
 *
 * @param sheet the draft.
 * @param unit unit of the height, from the settings.
 * @param defaultHeight default height of the project, shown as the value an empty height field stands for.
 * @param onIntent receives the edits and taps.
 */
@Composable
internal fun RoomDetailsContent(
    sheet: DetailsSheet.RoomDetails,
    unit: LengthUnit,
    defaultHeight: Length,
    onIntent: (EditorIntent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.m),
    ) {
        Text(
            text = stringResource(R.string.details_room_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        DetailsTextField(
            value = sheet.name,
            spec = DetailsFieldSpec(stringResource(R.string.details_name_label), TAG_NAME),
            error = sheet.errors[DetailsField.NAME],
            onChange = { onIntent(DetailsIntent.NameChanged(it)) },
        )
        TypeChooser(sheet.typeKey) { onIntent(DetailsIntent.TypeChanged(it)) }
        DetailsTextField(
            value = sheet.notes,
            spec = DetailsFieldSpec(stringResource(R.string.details_notes_label), TAG_NOTES, singleLine = false),
            error = null,
            onChange = { onIntent(DetailsIntent.NotesChanged(it)) },
        )
        MeasureFields(sheet, unit, defaultHeight, onIntent)
        PlanSnapButton(
            text = stringResource(R.string.details_apply),
            onClick = { onIntent(DetailsIntent.ApplyRoomDetails) },
            modifier = Modifier.fillMaxWidth(),
            enabled = sheet.canApply,
        )
        PlanSnapOutlinedButton(
            text = stringResource(R.string.details_cancel),
            onClick = { onIntent(DetailsIntent.Dismiss) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The room types as chips, with "no type" first. Exactly one is selected, unless the stored key is unknown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeChooser(
    selected: RoomTypeKey?,
    onSelect: (RoomTypeKey?) -> Unit,
) {
    Text(text = stringResource(R.string.details_type_label), style = MaterialTheme.typography.titleSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs),
    ) {
        TypeChip(stringResource(R.string.details_type_none), selected == null) { onSelect(null) }
        RoomTypeCatalog.types.forEach { type ->
            TypeChip(stringResource(type.name), selected == type.key) { onSelect(type.key) }
        }
    }
}

/** The height of the room and its area factor. */
@Composable
private fun MeasureFields(
    sheet: DetailsSheet.RoomDetails,
    unit: LengthUnit,
    defaultHeight: Length,
    onIntent: (EditorIntent) -> Unit,
) {
    val defaultText = rememberResources().lengthText(defaultHeight.mm.toDouble(), unit)
    DetailsTextField(
        value = sheet.heightText,
        spec =
            DetailsFieldSpec(
                label = stringResource(R.string.details_height_label, stringResource(unitRes(unit))),
                tag = TAG_HEIGHT,
                hint = stringResource(R.string.details_height_hint, defaultText),
                keyboardType = KeyboardType.Decimal,
            ),
        error = sheet.errors[DetailsField.HEIGHT],
        onChange = { onIntent(DetailsIntent.HeightChanged(it)) },
    )
    DetailsTextField(
        value = sheet.factorText,
        spec =
            DetailsFieldSpec(
                label = stringResource(R.string.details_factor_label),
                tag = TAG_FACTOR,
                hint = stringResource(R.string.details_factor_hint),
                keyboardType = KeyboardType.Number,
            ),
        error = sheet.errors[DetailsField.FACTOR],
        onChange = { onIntent(DetailsIntent.FactorChanged(it)) },
    )
}

/**
 * The parts of a text field of the sheets that do not change while typing.
 *
 * @property label visible label and TalkBack label.
 * @property tag stable tag of the field.
 * @property hint explanation below the field, replaced by the error message while there is one.
 */
internal data class DetailsFieldSpec(
    val label: String,
    val tag: String,
    val hint: String? = null,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val singleLine: Boolean = true,
)

/** A text field with the error of its value shown at the field, in words and with a symbol, not only in color. */
@Composable
internal fun DetailsTextField(
    value: String,
    spec: DetailsFieldSpec,
    error: DomainError?,
    onChange: (String) -> Unit,
) {
    val message = error?.let { rememberResources().detailsErrorMessage(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PlanSnapTheme.sizes.minTouchTarget)
                .testTag(spec.tag)
                .semantics { if (message != null) error(message) },
        label = { Text(spec.label) },
        isError = message != null,
        singleLine = spec.singleLine,
        minLines = if (spec.singleLine) 1 else NOTES_MIN_LINES,
        keyboardOptions = KeyboardOptions(keyboardType = spec.keyboardType),
        supportingText =
            if (message != null) {
                { ErrorText(message) }
            } else if (spec.hint != null) {
                { Text(spec.hint) }
            } else {
                null
            },
    )
}

/**
 * Message of [error] at a field of the sheets. A name that is too long names the limit, everything else uses the
 * common error texts of the editor.
 */
private fun Resources.detailsErrorMessage(error: DomainError): String =
    if (error == DetailsValues.NAME_TOO_LONG) {
        val limit = DetailsValues.MAX_NAME_CHARS
        getQuantityString(R.plurals.details_error_name_too_long, limit, limit)
    } else {
        errorMessage(error)
    }

@Composable
private fun ErrorText(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs)) {
        Icon(
            imageVector = PlanSnapIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon =
            if (selected) {
                { Icon(imageVector = PlanSnapIcons.Check, contentDescription = null) }
            } else {
                null
            },
        modifier = Modifier.heightIn(min = PlanSnapTheme.sizes.minTouchTarget),
    )
}

internal const val TAG_NAME = "details-name"
internal const val TAG_NOTES = "details-notes"
internal const val TAG_HEIGHT = "details-height"
internal const val TAG_FACTOR = "details-factor"
internal const val TAG_PROJECT_HEIGHT = "details-project-height"
