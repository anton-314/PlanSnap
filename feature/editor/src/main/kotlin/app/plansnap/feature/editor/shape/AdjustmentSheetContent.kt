package app.plansnap.feature.editor.shape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent

/**
 * Content of the adjustment sheet (DOMAIN section 6): what the adjustment changes, the measurements without a
 * check and the buttons. A proposal that needs the deliberate confirmation leads to a dialog first (ADR 0019).
 *
 * @param room the room to adjust.
 * @param evaluation its current evaluation with the proposal.
 * @param unit unit of the lengths in the texts.
 * @param onIntent receives the taps.
 */
@Composable
internal fun AdjustmentSheetContent(
    room: Room,
    evaluation: RoomEvaluation.NeedsAdjustment,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    val proposal = evaluation.proposal
    val explicit = proposal.requiredConfirmation == AdjustmentConfirmation.EXPLICIT
    val largest = proposal.largestCorrection
    val maxText = resources.lengthText(proposal.maxCorrectionMm, unit)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        SheetTitle(stringResource(R.string.shape_adjust_title), null)
        Text(text = stringResource(R.string.shape_adjust_hint), style = MaterialTheme.typography.bodyMedium)
        Text(
            text =
                if (largest == null) {
                    stringResource(R.string.shape_adjust_largest_unknown, maxText)
                } else {
                    stringResource(R.string.shape_adjust_largest, maxText, resources.observationName(largest, room))
                },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.shape_adjust_sum, resources.lengthText(proposal.correctionSumMm, unit)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (explicit) {
            Text(
                text = stringResource(R.string.shape_adjust_explicit_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        UncontrolledList(evaluation.measurementControl, room, onIntent)
        AdjustmentButtons(room, proposal, explicit, onIntent)
    }
}

@Composable
private fun AdjustmentButtons(
    room: Room,
    proposal: AdjustmentProposal,
    explicit: Boolean,
    onIntent: (EditorIntent) -> Unit,
) {
    PlanSnapButton(
        text = stringResource(if (explicit) R.string.shape_adjust_review else R.string.shape_adjust_apply),
        onClick = {
            if (explicit) {
                onIntent(ShapeIntent.RequestExplicitConfirmation)
            } else {
                onIntent(CoreIntent.AcceptAdjustment(room.id, proposal, AdjustmentConfirmation.SIMPLE))
                onIntent(ShapeIntent.Dismiss)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    PlanSnapOutlinedButton(
        text = stringResource(R.string.shape_sheet_cancel),
        onClick = { onIntent(ShapeIntent.Dismiss) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Content of the dialog that asks for the deliberate confirmation of a large correction (ADR 0019). It is a step of
 * its own, so a single tap on the sheet never produces `AdjustmentConfirmation.EXPLICIT`.
 *
 * @param room the room to adjust.
 * @param evaluation its current evaluation with the proposal.
 * @param unit unit of the lengths in the texts.
 * @param onIntent receives the taps.
 */
@Composable
internal fun ExplicitConfirmationContent(
    room: Room,
    evaluation: RoomEvaluation.NeedsAdjustment,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    val proposal = evaluation.proposal
    val maxText = resources.lengthText(proposal.maxCorrectionMm, unit)
    val sumText = resources.lengthText(proposal.correctionSumMm, unit)
    val largest = proposal.largestCorrection
    Column(
        // The dialog scrolls when a large font makes it higher than the screen, so both buttons stay reachable.
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.m),
    ) {
        Text(
            text = stringResource(R.string.shape_explicit_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text =
                if (largest == null) {
                    stringResource(R.string.shape_explicit_body_unknown, maxText, sumText)
                } else {
                    stringResource(
                        R.string.shape_explicit_body,
                        maxText,
                        resources.observationName(largest, room),
                        sumText,
                    )
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        PlanSnapOutlinedButton(
            text = stringResource(R.string.shape_explicit_cancel),
            onClick = { onIntent(ShapeIntent.CancelExplicitConfirmation) },
            modifier = Modifier.fillMaxWidth(),
        )
        PlanSnapButton(
            text = stringResource(R.string.shape_explicit_confirm),
            onClick = {
                onIntent(CoreIntent.AcceptAdjustment(room.id, proposal, AdjustmentConfirmation.EXPLICIT))
                onIntent(ShapeIntent.Dismiss)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
