package app.plansnap.feature.editor.shape

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.details.DetailsFormat
import java.util.Locale

// Texts of the shape sheets. Every function resolves string resources of the current configuration, so a message that
// was created before a language change is still shown in the language the user sees now. Corners and walls are shown
// one-based: "corner 1" is `V_0`, the closing corner.

/** The resources of the current configuration, for text that is not a composable. */
@Composable
internal fun rememberResources(): Resources = LocalResources.current

/** Message that explains the rejected [error] in plain words and says what to do (UX section 7). */
internal fun Resources.errorMessage(error: DomainError): String =
    when (error) {
        is DomainError.InvalidValue -> invalidValueMessage(error)
        is DomainError.NotFound -> getString(R.string.error_not_found, getString(entityRes(error.entity)))
        is DomainError.RuleViolation -> getString(ruleRes(error.rule))
        is DomainError.Document -> getString(R.string.error_document)
        is DomainError.Storage -> getString(storageRes(error.problem))
        is DomainError.Export -> getString(R.string.error_export)
    }

private fun Resources.invalidValueMessage(error: DomainError.InvalidValue): String {
    val angleRange =
        error.field == ValueField.CORNER_ANGLE &&
            (error.reason == InvalidValueReason.TOO_SMALL || error.reason == InvalidValueReason.TOO_LARGE)
    return if (angleRange) {
        getString(R.string.error_corner_angle_range)
    } else {
        getString(R.string.error_with_field, getString(fieldRes(error.field)), getString(reasonRes(error.reason)))
    }
}

/** Length of [mm] in [unit] with its unit, rounded to a whole millimeter. */
internal fun Resources.lengthText(
    mm: Double,
    unit: LengthUnit,
): String {
    val number = DecimalLengthFormatter.format(Length(Math.round(mm)), unit, Locale.getDefault())
    return "$number ${getString(unitRes(unit))}"
}

/** Name of the measurement [ref] inside a sentence, for example "wall 3". [room] resolves diagonals by corner. */
internal fun Resources.observationName(
    ref: ObservationRef,
    room: Room?,
): String =
    when (ref) {
        is ObservationRef.WallLength -> {
            getString(R.string.shape_observation_wall, ref.index + 1)
        }

        is ObservationRef.CornerAngle -> {
            getString(R.string.shape_observation_corner, ref.index + 1)
        }

        is ObservationRef.DiagonalLength -> {
            val diagonal = room?.diagonals?.getOrNull(ref.index)
            if (diagonal == null) {
                getString(R.string.shape_observation_diagonal_number, ref.index + 1)
            } else {
                getString(R.string.shape_observation_diagonal, diagonal.fromCorner + 1, diagonal.toCorner + 1)
            }
        }
    }

/** Sentence that asks for the additional measurement [recommendation], or says that none would help. */
internal fun Resources.recommendationText(recommendation: MeasurementRecommendation?): String =
    when (recommendation) {
        is MeasurementRecommendation.DiagonalBetween -> {
            getString(R.string.shape_recommend_diagonal, recommendation.fromCorner + 1, recommendation.toCorner + 1)
        }

        is MeasurementRecommendation.CornerAngle -> {
            getString(R.string.shape_recommend_corner, recommendation.corner + 1)
        }

        null -> {
            getString(R.string.shape_recommend_none)
        }
    }

/** Explanation of a conflict: what is impossible and what to change, in plain words. */
internal fun Resources.conflictText(
    conflict: RoomEvaluation.Conflict,
    room: Room,
): String =
    when (conflict.reason) {
        ConflictReason.WALL_TOO_LONG -> {
            getString(R.string.shape_conflict_wall_too_long)
        }

        ConflictReason.DIAGONAL_IMPOSSIBLE -> {
            val diagonal = room.diagonals.firstOrNull { it.id == conflict.diagonalId }
            if (diagonal == null) {
                getString(R.string.shape_conflict_diagonal_impossible_unknown)
            } else {
                getString(R.string.shape_conflict_diagonal_impossible, diagonal.fromCorner + 1, diagonal.toCorner + 1)
            }
        }

        ConflictReason.NO_SIMPLE_SHAPE -> {
            getString(R.string.shape_conflict_no_simple_shape)
        }

        ConflictReason.MEASUREMENTS_INCONSISTENT -> {
            val worst = conflict.worstObservation
            if (worst == null) {
                getString(R.string.shape_conflict_inconsistent_unknown)
            } else {
                getString(R.string.shape_conflict_inconsistent, observationName(worst, room))
            }
        }

        ConflictReason.ADJUSTMENT_NOT_CONVERGED -> {
            getString(R.string.shape_conflict_not_converged)
        }

        ConflictReason.TURN_DIRECTION_VIOLATED -> {
            getString(R.string.shape_conflict_turn_direction)
        }
    }

/** Area in square meters in the default locale, rounded exactly like the area overview ([DetailsFormat.area]). */
internal fun areaText(areaMm2: Double): String = DetailsFormat.area(areaMm2, Locale.getDefault())
