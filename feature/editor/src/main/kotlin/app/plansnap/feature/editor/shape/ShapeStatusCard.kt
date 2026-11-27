package app.plansnap.feature.editor.shape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.plansnap.core.designsystem.component.ShapeStatus
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.evaluation.ProposalReason
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent

private val CARD_ELEVATION = 2.dp

/** Header and the body below it, `null` if there is nothing below the header. Only an expanded card shows it. */
private class CardParts(
    val header: CardHeader,
    val body: (@Composable ColumnScope.() -> Unit)?,
)

/**
 * Card below the plan that tells the user what the evaluation of the selected room means and what to do next
 * (UX section 3). It reads the [RoomEvaluation] and shows its result; it computes nothing about the shape. Status is
 * always a symbol and a text, never a color alone.
 *
 * The header with status, name and area wraps instead of clipping at large font scales, and the expanded card
 * scrolls when it gets less height than it needs. With a [toggle] the header is a button of at least 48 dp that
 * switches between the whole card and a compact header, for plan areas that are low.
 *
 * @param room the selected room, `null` while there is none.
 * @param result its evaluation or why it failed, `null` while it is pending.
 * @param unit unit of the lengths in the texts.
 * @param onIntent receives the actions of the card.
 * @param modifier modifier of the card.
 * @param toggle state and callback of the collapse toggle, `null` if the card cannot be collapsed.
 */
@Suppress("LongParameterList") // Compose API: the room, its result, the callbacks and the optional collapse toggle.
@Composable
fun ShapeStatusCard(
    room: Room?,
    result: DomainResult<RoomEvaluation>?,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
    toggle: StatusCardToggle? = null,
) {
    if (room == null) return
    val expanded = toggle?.expanded ?: true
    val parts = cardParts(room, result, unit, onIntent) ?: return
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
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .then(if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            val body = parts.body.takeIf { expanded }
            HeaderRow(room, parts.header, toggle, hasBody = body != null)
            if (body != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = PlanSnapTheme.spacing.m,
                                end = PlanSnapTheme.spacing.m,
                                bottom = PlanSnapTheme.spacing.m,
                            ),
                    verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
                    content = body,
                )
            }
        }
    }
}

@Composable
private fun cardParts(
    room: Room,
    result: DomainResult<RoomEvaluation>?,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
): CardParts? {
    val failure = (result as? DomainResult.Failure)?.error
    val evaluation = (result as? DomainResult.Success)?.value
    return when {
        failure != null -> {
            CardParts(CardHeader(ShapeStatus.Conflicting, R.string.shape_status_conflict, null)) {
                FailedBody(failure)
            }
        }

        evaluation is RoomEvaluation.Confirmed -> {
            val header =
                CardHeader(ShapeStatus.Confirmed, R.string.shape_status_confirmed, evaluation.shape.areaMm2)
            val control = evaluation.measurementControl?.takeIf { it.uncontrolled.isNotEmpty() }
            if (control == null) {
                CardParts(header, null)
            } else {
                CardParts(header) { UncontrolledList(control, room, onIntent) }
            }
        }

        evaluation is RoomEvaluation.Adjusted -> {
            CardParts(CardHeader(ShapeStatus.Confirmed, R.string.shape_status_adjusted, evaluation.shape.areaMm2)) {
                AdjustedBody(onIntent)
            }
        }

        evaluation is RoomEvaluation.NeedsAdjustment -> {
            CardParts(
                CardHeader(ShapeStatus.Unconfirmed, R.string.shape_status_unconfirmed, evaluation.shape.areaMm2),
            ) { NeedsAdjustmentBody(evaluation, unit, onIntent) }
        }

        evaluation is RoomEvaluation.Proposed -> {
            CardParts(
                CardHeader(ShapeStatus.Unconfirmed, R.string.shape_status_unconfirmed, evaluation.shape.areaMm2),
            ) { ProposedBody(evaluation, unit, onIntent) }
        }

        evaluation is RoomEvaluation.Conflict -> {
            CardParts(CardHeader(ShapeStatus.Conflicting, R.string.shape_status_conflict, null)) {
                ConflictBody(evaluation, room)
            }
        }

        else -> {
            null
        }
    }
}

@Composable
private fun AdjustedBody(onIntent: (EditorIntent) -> Unit) {
    PlanSnapOutlinedButton(
        text = stringResource(R.string.shape_action_revert),
        onClick = { onIntent(ShapeIntent.RevertAdjustment) },
    )
}

@Composable
private fun NeedsAdjustmentBody(
    evaluation: RoomEvaluation.NeedsAdjustment,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
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
private fun ProposedBody(
    evaluation: RoomEvaluation.Proposed,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
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
private fun ConflictBody(
    evaluation: RoomEvaluation.Conflict,
    room: Room,
) {
    val resources = rememberResources()
    Text(text = resources.conflictText(evaluation, room), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FailedBody(error: DomainError) {
    val resources = rememberResources()
    Text(
        text = stringResource(R.string.shape_status_failed, resources.errorMessage(error)),
        style = MaterialTheme.typography.bodyMedium,
    )
}
