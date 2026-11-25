package app.plansnap.feature.editor.shape

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.EditorIntent

/** The recommended additional measurement with the button that enters it, nothing without a recommendation. */
@Composable
internal fun RecommendationBlock(
    recommendation: MeasurementRecommendation?,
    onIntent: (EditorIntent) -> Unit,
) {
    val resources = rememberResources()
    Text(text = resources.recommendationText(recommendation), style = MaterialTheme.typography.bodyMedium)
    when (recommendation) {
        is MeasurementRecommendation.DiagonalBetween -> {
            PlanSnapButton(
                text = stringResource(R.string.shape_action_add_diagonal),
                onClick = { onIntent(ShapeIntent.OpenDiagonal(recommendation.fromCorner, recommendation.toCorner)) },
            )
        }

        is MeasurementRecommendation.CornerAngle -> {
            PlanSnapButton(
                text = stringResource(R.string.shape_action_enter_angle),
                onClick = { onIntent(ShapeIntent.OpenCorner(CornerTarget.Existing(recommendation.corner))) },
            )
        }

        null -> {
            Unit
        }
    }
}

/**
 * The measurements that cannot be checked (DOMAIN section 5.5), each with the additional measurement that would
 * make it checkable. Shows nothing if every measurement has a check.
 */
@Composable
internal fun UncontrolledList(
    control: MeasurementControl,
    room: Room,
    onIntent: (EditorIntent) -> Unit,
) {
    if (control.uncontrolled.isEmpty()) return
    val resources = rememberResources()
    Text(
        text = stringResource(R.string.shape_uncontrolled_title),
        style = MaterialTheme.typography.titleSmall,
    )
    control.uncontrolled.forEach { item ->
        val recommendation = item.recommendation
        Text(
            text =
                stringResource(
                    R.string.shape_uncontrolled_item,
                    resources.observationName(item.observation, room),
                    resources.recommendationText(recommendation),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (recommendation != null) RecommendationAction(recommendation, onIntent)
    }
}

@Composable
private fun RecommendationAction(
    recommendation: MeasurementRecommendation,
    onIntent: (EditorIntent) -> Unit,
) {
    when (recommendation) {
        is MeasurementRecommendation.DiagonalBetween -> {
            PlanSnapOutlinedButton(
                text = stringResource(R.string.shape_action_add_diagonal),
                onClick = { onIntent(ShapeIntent.OpenDiagonal(recommendation.fromCorner, recommendation.toCorner)) },
            )
        }

        is MeasurementRecommendation.CornerAngle -> {
            PlanSnapOutlinedButton(
                text = stringResource(R.string.shape_action_enter_angle),
                onClick = { onIntent(ShapeIntent.OpenCorner(CornerTarget.Existing(recommendation.corner))) },
            )
        }
    }
}
