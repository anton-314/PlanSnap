package app.plansnap.feature.export.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.export.ExportFormat
import app.plansnap.feature.export.R

@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun FormatSection(
    selected: ExportFormat,
    enabled: Boolean,
    onSelected: (ExportFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s)) {
        SectionTitle(stringResource(R.string.export_section_format))
        Column(modifier = Modifier.selectableGroup()) {
            ExportFormat.entries.forEach { format ->
                FormatRow(format, format == selected, enabled) { onSelected(format) }
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: ExportFormat,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minWidth = PlanSnapTheme.sizes.minTouchTarget, minHeight = PlanSnapTheme.sizes.minTouchTarget)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.padding(vertical = PlanSnapTheme.spacing.xs)) {
            Text(text = stringResource(formatLabel(format)), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(formatDescription(format)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PageSection(
    state: ExportUiState.Ready,
    actions: ExportActions,
) {
    val enabled = !state.isBusy
    Column(verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.m)) {
        SectionTitle(stringResource(R.string.export_section_page))
        ChoiceRow(
            label = stringResource(R.string.export_scale_label),
            automatic = stringResource(R.string.export_scale_auto),
            options = PlotScale.entries.map { it to stringResource(scaleLabel(it)) },
            selected = state.scale,
            enabled = enabled,
            onSelected = actions.onScaleSelected,
        )
        ChoiceRow(
            label = stringResource(R.string.export_paper_label),
            automatic = stringResource(R.string.export_paper_auto),
            options = PaperSize.entries.map { it to stringResource(paperLabel(it)) },
            selected = state.paper,
            enabled = enabled,
            onSelected = actions.onPaperSelected,
        )
        OrientationInfo(state.fit)
        FitResult(state.fit)
    }
}

@Suppress("LongParameterList") // Compose API: label, options, selection and callback of one choice row.
@Composable
private fun <T : Any> ChoiceRow(
    label: String,
    automatic: String,
    options: List<Pair<T, String>>,
    selected: T?,
    enabled: Boolean,
    onSelected: (T?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        // A flow row breaks to a second line instead of squeezing a text, which clipped it at large font scales.
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        ) {
            // "Automatic" stands in both rows, so a screen reader also names the row.
            val spoken = stringResource(R.string.export_a11y_choice, label, automatic)
            Choice(automatic, selected == null, enabled, Modifier.semantics { contentDescription = spoken }) {
                onSelected(null)
            }
            options.forEach { (value, text) ->
                Choice(text, value == selected, enabled, Modifier) { onSelected(value) }
            }
        }
    }
}

@Composable
private fun Choice(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier =
            modifier
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline),
    ) {
        Column(
            modifier =
                Modifier
                    .sizeIn(
                        minWidth = PlanSnapTheme.sizes.minTouchTarget,
                        minHeight = PlanSnapTheme.sizes.minTouchTarget,
                    ).padding(horizontal = PlanSnapTheme.spacing.s, vertical = PlanSnapTheme.spacing.s),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
}

@Composable
private fun OrientationInfo(fit: PlanFit) {
    val value =
        when (fit) {
            is PlanFit.Fits -> stringResource(orientationLabel(fit.orientation))
            else -> stringResource(R.string.export_orientation_pending)
        }
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(text = stringResource(R.string.export_orientation_label), style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.export_orientation_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FitResult(fit: PlanFit) {
    val color = MaterialTheme.colorScheme.onSurface
    when (fit) {
        PlanFit.NotApplicable -> {
            Unit
        }

        PlanFit.Checking -> {
            Text(stringResource(R.string.export_fit_checking), style = MaterialTheme.typography.bodyMedium)
        }

        is PlanFit.Fits -> {
            val text =
                stringResource(
                    R.string.export_fit_result,
                    stringResource(scaleLabel(fit.scale)),
                    stringResource(paperLabel(fit.paper)),
                    stringResource(orientationLabel(fit.orientation)),
                )
            Column {
                Text(text = text, style = MaterialTheme.typography.titleSmall, color = color)
                Text(
                    text = stringResource(R.string.export_title_block_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is PlanFit.Unavailable -> {
            MessageCard(
                title = null,
                message = stringResource(errorMessage(fit.error)),
                action = null,
                onAction = {},
                onDismiss = null,
            )
        }
    }
}
