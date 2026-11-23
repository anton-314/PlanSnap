package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlin.math.abs

/** Whether a room with this status appears in PDF, PNG and SVG (DOMAIN sections 4 and 12). */
val ShapeStatus.isExportable: Boolean
    get() = this != ShapeStatus.OPEN && this != ShapeStatus.CONFLICT

/** Whether a room with this status carries the mark "shape not confirmed" (REQ-EXP-006). */
val ShapeStatus.isUnconfirmed: Boolean
    get() = this == ShapeStatus.NEEDS_ADJUSTMENT || this == ShapeStatus.PROPOSED

/** Confirmation level of an adjustment (DOMAIN section 5.5, ADR 0018). */
enum class AdjustmentConfirmation {
    /** Ordinary confirmation of the preview. */
    SIMPLE,

    /** Separate, deliberate confirmation of a large correction. A single tap must not produce it. */
    EXPLICIT,
}

/**
 * Adjustment offered for confirmation.
 *
 * @property adjustment overlay to store, one entry per wall.
 * @property correctionSumMm sum norm `S` of the corrections.
 * @property maxCorrectionMm largest single correction, at most [correctionSumMm].
 * @property largestCorrection affected measurement of [maxCorrectionMm], `null` if all corrections are zero.
 * @property requiredConfirmation confirmation level that `AcceptAdjustment` must state.
 */
data class AdjustmentProposal(
    val adjustment: ClosureAdjustment,
    val correctionSumMm: Double,
    val maxCorrectionMm: Double,
    val largestCorrection: ObservationRef?,
    val requiredConfirmation: AdjustmentConfirmation,
) {
    init {
        require(maxCorrectionMm.isFinite() && maxCorrectionMm >= 0.0) { "Invalid correction: $maxCorrectionMm" }
        require(correctionSumMm.isFinite() && correctionSumMm >= maxCorrectionMm) {
            "Correction sum must be at least the largest correction: $correctionSumMm"
        }
    }

    /** Whether [given] satisfies [requiredConfirmation]. */
    fun isConfirmedBy(given: AdjustmentConfirmation): Boolean =
        requiredConfirmation == AdjustmentConfirmation.SIMPLE || given == AdjustmentConfirmation.EXPLICIT
}

/**
 * Corrections behind a proposed shape (DOMAIN section 5.5, step B7).
 *
 * @property correctionSumMm sum norm `S` of the corrections.
 * @property normalizedSquareSum `q²`, `null` without redundancy (`r = 0`), where it is not defined.
 * @property maxCorrectionMm largest single correction, at most [correctionSumMm].
 * @property largestCorrection affected measurement. Required once [maxCorrectionMm] exceeds `SOLVER_RESIDUAL_MM`.
 */
data class CorrectionSummary(
    val correctionSumMm: Double,
    val normalizedSquareSum: Double?,
    val maxCorrectionMm: Double,
    val largestCorrection: ObservationRef?,
) {
    init {
        require(maxCorrectionMm.isFinite() && maxCorrectionMm >= 0.0) { "Invalid correction: $maxCorrectionMm" }
        require(correctionSumMm.isFinite() && correctionSumMm >= maxCorrectionMm) {
            "Correction sum must be at least the largest correction: $correctionSumMm"
        }
        require(normalizedSquareSum == null || (normalizedSquareSum.isFinite() && normalizedSquareSum >= 0.0)) {
            "Invalid normalized square sum: $normalizedSquareSum"
        }
        require(largestCorrection != null || maxCorrectionMm <= GeometryTolerances.SOLVER_RESIDUAL_MM) {
            "A correction above the solver residual needs its measurement"
        }
    }
}

/**
 * Displayed closed shape in room-local coordinates.
 *
 * @property outline inner contour.
 * @property winding orientation of [outline].
 * @property lengthsMm effective wall lengths, one per vertex, positive and finite.
 * @property turnsRad effective turns `τ_0 … τ_{n-1}`, one per vertex, finite.
 * @property areaMm2 unrounded inner area, not negative and finite.
 * @property perimeterMm unrounded perimeter, positive and finite.
 */
data class EvaluatedShape(
    val outline: Polygon2,
    val winding: Winding,
    val lengthsMm: ImmutableList<Double>,
    val turnsRad: ImmutableList<Double>,
    val areaMm2: Double,
    val perimeterMm: Double,
) {
    init {
        val n = outline.vertices.size
        require(lengthsMm.size == n) { "Expected one length per vertex" }
        require(turnsRad.size == n) { "Expected one turn per vertex" }
        require(lengthsMm.all { it.isFinite() && it > 0.0 }) { "Effective lengths must be positive" }
        require(turnsRad.all { it.isFinite() }) { "Turns must be finite" }
        require(areaMm2.isFinite() && areaMm2 >= 0.0) { "Invalid area: $areaMm2" }
        require(perimeterMm.isFinite() && perimeterMm > 0.0) { "Invalid perimeter: $perimeterMm" }
    }
}

/** Result of evaluating one room (DOMAIN section 4). Each variant corresponds to exactly one [ShapeStatus]. */
sealed interface RoomEvaluation {
    /** Evaluated room. */
    val roomId: RoomId

    /** Derived status. */
    val status: ShapeStatus

    /** Displayed closed shape, `null` for [Open] and [Conflict]. */
    val shape: EvaluatedShape?

    /** Derived status of every opening of the room (DOMAIN section 9). */
    val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>

    /**
     * Fewer than three walls or not closed.
     *
     * @property chain walked points of the open chain in room-local coordinates, starting at `V_0`, never empty.
     */
    data class Open(
        override val roomId: RoomId,
        val chain: ImmutableList<Vec2>,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require(chain.isNotEmpty()) { "An open chain starts at V_0" }
        }

        override val status: ShapeStatus get() = ShapeStatus.OPEN
        override val shape: EvaluatedShape? get() = null
    }

    /**
     * Shape is unique and closed exactly or with corrections up to `EXACT_CLOSURE_MM`.
     *
     * @property measurementControl controllability of the measurements, `null` if the exact target shape needed no
     * rank analysis (DOMAIN section 4).
     */
    data class Confirmed(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
        val measurementControl: MeasurementControl? = null,
    ) : RoomEvaluation {
        override val status: ShapeStatus get() = ShapeStatus.CONFIRMED
    }

    /**
     * Correction of the measurements that awaits confirmation.
     *
     * @property shape adjustment preview.
     * @property closureError error components of the target shape.
     * @property proposal adjustment to pass to `AcceptAdjustment` with its required confirmation.
     * @property measurementControl controllability of the measurements (DOMAIN section 5.5), always present.
     */
    data class NeedsAdjustment(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        val closureError: ClosureError,
        val proposal: AdjustmentProposal,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
        val measurementControl: MeasurementControl,
    ) : RoomEvaluation {
        init {
            require(proposal.adjustment.adjustedLengthsMm.size == shape.outline.vertices.size) {
                "Expected one adjusted length per wall"
            }
        }

        override val status: ShapeStatus get() = ShapeStatus.NEEDS_ADJUSTMENT
    }

    /** Confirmed overlay that matches the raw values. It is checked before any recomputation (DOMAIN section 4). */
    data class Adjusted(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        override val status: ShapeStatus get() = ShapeStatus.ADJUSTED
    }

    /**
     * Proposed shape with missing measurements.
     *
     * @property determinacy rank analysis with at least one missing measurement and the recommended one.
     * @property rawLengthsMm raw wall lengths, one per wall.
     * @property correction corrections of the adjustment. Required as soon as an effective length differs from
     * [rawLengthsMm] by more than `SOLVER_RESIDUAL_MM`, `null` for a shape solved on the raw lengths.
     */
    data class Proposed(
        override val roomId: RoomId,
        override val shape: EvaluatedShape,
        val determinacy: Determinacy,
        val rawLengthsMm: ImmutableList<Long>,
        val correction: CorrectionSummary?,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require(determinacy.missing > 0) { "A proposal has at least one missing measurement" }
            require(rawLengthsMm.size == shape.lengthsMm.size) { "Expected one raw length per wall" }
            val deviates =
                shape.lengthsMm.indices.any {
                    abs(shape.lengthsMm[it] - rawLengthsMm[it]) > GeometryTolerances.SOLVER_RESIDUAL_MM
                }
            require(!deviates || correction != null) { "Lengths that differ from the raw values need a correction" }
        }

        override val status: ShapeStatus get() = ShapeStatus.PROPOSED
    }

    /**
     * Geometrically impossible measurements.
     *
     * @property diagonalId affected diagonal, present exactly for [ConflictReason.DIAGONAL_IMPOSSIBLE].
     * @property worstObservation measurement with the largest normalized residual, only for
     * [ConflictReason.MEASUREMENTS_INCONSISTENT].
     */
    data class Conflict(
        override val roomId: RoomId,
        val reason: ConflictReason,
        val diagonalId: DiagonalId?,
        val worstObservation: ObservationRef?,
        override val openingStatuses: ImmutableMap<OpeningId, OpeningStatus>,
    ) : RoomEvaluation {
        init {
            require((reason == ConflictReason.DIAGONAL_IMPOSSIBLE) == (diagonalId != null)) {
                "A diagonal is required exactly for DIAGONAL_IMPOSSIBLE"
            }
            require(worstObservation == null || reason == ConflictReason.MEASUREMENTS_INCONSISTENT) {
                "A worst observation is only valid for MEASUREMENTS_INCONSISTENT"
            }
        }

        override val status: ShapeStatus get() = ShapeStatus.CONFLICT
        override val shape: EvaluatedShape? get() = null
    }
}

/** Evaluates a room as specified in DOMAIN section 4. Deterministic, main-safe and cancellable (ADR 0016). */
fun interface RoomEvaluator {
    /**
     * Evaluates [room] against [tolerance].
     *
     * Runs on the default dispatcher and passes [currentCancellationSignal] to solver and adjustment.
     *
     * @return the evaluation, or `DomainError.InvalidValue` if a value violates the limits of `GeometryTolerances`.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun evaluate(
        room: Room,
        tolerance: ClosureTolerance,
    ): DomainResult<RoomEvaluation>
}
