package app.plansnap.feature.editor.shape

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.component.PlanSnapStatusBadge
import app.plansnap.core.designsystem.component.ShapeStatus
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.ProposalReason
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val CARD_ELEVATION = 2.dp

/**
 * Card below the plan that tells the user what the evaluation of the selected room means and what to do next
 * (UX section 3). It reads the [RoomEvaluation] and shows its result; it computes nothing about the shape. Status is
 * always a symbol and a text, never a color alone.
 *
 * @param room the selected room, `null` while there is none.
 * @param result its evaluation or why it failed, `null` while it is pending.
 * @param unit unit of the lengths in the texts.
 * @param onIntent receives the actions of the card.
 * @param modifier modifier of the card.
 */
@Composable
fun ShapeStatusCard(
    room: Room?,
    result: DomainResult<RoomEvaluation>?,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (room == null) return
    val failure = (result as? DomainResult.Failure)?.error
    val evaluation = (result as? DomainResult.Success)?.value
    val content: (@Composable () -> Unit)? =
        when {
            failure != null -> {
                { FailedContent(room, failure) }
            }

            evaluation is RoomEvaluation.Confirmed -> {
                { ConfirmedContent(room, evaluation, onIntent) }
            }

            evaluation is RoomEvaluation.Adjusted -> {
                { AdjustedContent(room, evaluation, onIntent) }
            }

            evaluation is RoomEvaluation.NeedsAdjustment -> {
                { NeedsAdjustmentContent(room, evaluation, unit, onIntent) }
            }

            evaluation is RoomEvaluation.Proposed -> {
                { ProposedContent(room, evaluation, unit, onIntent) }
            }

            evaluation is RoomEvaluation.Conflict -> {
                { ConflictContent(room, evaluation) }
            }

            else -> {
                null
            }
        }
    if (content == null) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = CARD_ELEVATION,
        shadowElevation = CARD_ELEVATION,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(PlanSnapTheme.spacing.m)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        ) {
            content()
        }
    }
}

@Composable
private fun ConfirmedContent(
    room: Room,
    evaluation: RoomEvaluation.Confirmed,
    onIntent: (EditorIntent) -> Unit,
) {
    StatusRow(room, ShapeStatus.Confirmed, R.string.shape_status_confirmed, evaluation.shape.areaMm2)
    evaluation.measurementControl?.let { UncontrolledList(it, room, onIntent) }
}

@Composable
private fun AdjustedContent(
    room: Room,
    evaluation: RoomEvaluation.Adjusted,
    onIntent: (EditorIntent) -> Unit,
) {
    StatusRow(room, ShapeStatus.Confirmed, R.string.shape_status_adjusted, evaluation.shape.areaMm2)
    PlanSnapOutlinedButton(
        text = stringResource(R.string.shape_action_revert),
        onClick = { onIntent(ShapeIntent.RevertAdjustment) },
    )
}

@Composable
private fun NeedsAdjustmentContent(
    room: Room,
    evaluation: RoomEvaluation.NeedsAdjustment,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    StatusRow(room, ShapeStatus.Unconfirmed, R.string.shape_status_unconfirmed, evaluation.shape.areaMm2)
    Text(
        text =
            stringResource(
                R.string.shape_status_needs_adjustment,
                resources.lengthText(evaluation.proposal.maxCorrectionMm, unit),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )
    PlanSnapButton(
        text = stringResource(R.string.shape_action_adjust),
        onClick = { onIntent(ShapeIntent.OpenAdjustment) },
    )
}

@Composable
private fun ProposedContent(
    room: Room,
    evaluation: RoomEvaluation.Proposed,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    StatusRow(room, ShapeStatus.Unconfirmed, R.string.shape_status_unconfirmed, evaluation.shape.areaMm2)
    val reason =
        when (evaluation.reason) {
            ProposalReason.UNDERDETERMINED -> R.string.shape_status_proposed_missing
            ProposalReason.AMBIGUOUS_SHAPE -> R.string.shape_status_proposed_ambiguous
        }
    Text(text = stringResource(reason), style = MaterialTheme.typography.bodyMedium)
    evaluation.correction?.let {
        Text(
            text =
                stringResource(
                    R.string.shape_status_proposed_correction,
                    resources.lengthText(it.maxCorrectionMm, unit),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    RecommendationBlock(evaluation.recommendation, onIntent)
}

@Composable
private fun ConflictContent(
    room: Room,
    evaluation: RoomEvaluation.Conflict,
) {
    val resources = rememberResources()
    StatusRow(room, ShapeStatus.Conflicting, R.string.shape_status_conflict, null)
    Text(text = resources.conflictText(evaluation, room), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FailedContent(
    room: Room,
    error: DomainError,
) {
    val resources = rememberResources()
    StatusRow(room, ShapeStatus.Conflicting, R.string.shape_status_conflict, null)
    Text(
        text = stringResource(R.string.shape_status_failed, resources.errorMessage(error)),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun StatusRow(
    room: Room?,
    status: ShapeStatus,
    @StringRes label: Int,
    areaMm2: Double?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanSnapStatusBadge(status = status, label = stringResource(label))
        val name = room?.name?.takeIf { it.isNotBlank() }
        if (name != null) Text(text = name, style = MaterialTheme.typography.titleSmall)
        if (areaMm2 != null) {
            Text(
                text = stringResource(R.string.shape_status_area, areaText(areaMm2)),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
