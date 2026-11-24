package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.solver.HardCondition
import app.plansnap.core.geometry.solver.ShapeSolution
import app.plansnap.core.geometry.traverse.ShapeInput

/** Additional measurement recommended to determine a shape (DOMAIN section 5.4). */
sealed interface MeasurementRecommendation {
    /** Measure the diagonal between corners [fromCorner] and [toCorner], with `fromCorner < toCorner`. */
    data class DiagonalBetween(
        val fromCorner: Int,
        val toCorner: Int,
    ) : MeasurementRecommendation {
        init {
            require(fromCorner in 0 until toCorner) { "Expected 0 <= fromCorner < toCorner" }
        }
    }

    /** Measure the angle at [corner]. */
    data class CornerAngle(
        val corner: Int,
    ) : MeasurementRecommendation {
        init {
            require(corner >= 0) { "Corner index must not be negative" }
        }
    }
}

/**
 * Determinacy of a solved shape.
 *
 * @property freeVariables `|u_frei|`, the variables left after eliminating measured corners.
 * @property rank rank `ρ_J`, between 0 and [freeVariables].
 * @property recommendation next measurement. Only permitted while [missing] is positive.
 */
data class Determinacy(
    val freeVariables: Int,
    val rank: Int,
    val recommendation: MeasurementRecommendation?,
) {
    init {
        require(freeVariables >= 0) { "Free variables must not be negative" }
        require(rank in 0..freeVariables) { "Rank out of range: $rank" }
        require(recommendation == null || rank < freeVariables) { "A determined shape needs no recommendation" }
    }

    /** Number of missing measurements, `|u_frei| − ρ_J`. */
    val missing: Int get() = freeVariables - rank
}

/**
 * Pre-check of the hard conditions before any solving (DOMAIN section 5.4).
 *
 * @property freeVariables `v = |u_frei|`, the variables left after eliminating measured corners.
 * @property conditionCount `m`, the hard conditions: closure (2), measured closing corner and diagonals.
 * @property independentSelection deterministic independent subset of size `ρ` (DOMAIN section 5.4), in pivot order:
 * closure, measured closing corner, then diagonals sorted by `(min, max, length, input index)`. A diagonal between
 * adjacent corners repeats a wall and is never selected.
 */
data class ConstraintAnalysis(
    val freeVariables: Int,
    val conditionCount: Int,
    val independentSelection: List<HardCondition>,
) {
    init {
        require(freeVariables >= 0) { "Free variables must not be negative" }
        require(conditionCount >= CLOSURE_CONDITIONS) { "Closure alone yields $CLOSURE_CONDITIONS conditions" }
        require(independentSelection.size <= minOf(freeVariables, conditionCount)) {
            "Independent conditions out of range: ${independentSelection.size}"
        }
        require(independentSelection.distinct().size == independentSelection.size) {
            "Independent conditions must be distinct"
        }
    }

    /** `ρ`, the rank of the Jacobian at the routing point. */
    val independentConditions: Int get() = independentSelection.size

    /** Missing measurements at the routing point, `v − ρ`. The rank at the solution is authoritative. */
    val missing: Int get() = freeVariables - independentConditions

    /** Whether a hard condition is dependent, `m > ρ`. Such a shape takes branch B of DOMAIN section 5.5. */
    val isOverdetermined: Boolean get() = conditionCount > independentConditions

    private companion object {
        const val CLOSURE_CONDITIONS = 2
    }
}

/**
 * Measurement without a sufficient cross-check, `λ(3T) < λ₀(r)` (DOMAIN section 5.5). Information only.
 *
 * @property noncentrality `λ(3T) = (3T)² · r_i / σ_i²`, finite and not negative.
 * @property recommendation additional measurement that makes [observation] controllable, `null` if none exists.
 */
data class UncontrolledMeasurement(
    val observation: ObservationRef,
    val noncentrality: Double,
    val recommendation: MeasurementRecommendation?,
) {
    init {
        require(noncentrality.isFinite() && noncentrality >= 0.0) { "Invalid noncentrality: $noncentrality" }
    }
}

/**
 * Controllability of the measurements of a determined room (DOMAIN section 5.5). Never changes the status.
 *
 * @property redundancy `r`, not negative. With `r = 0` no measurement has a cross-check and [uncontrolled] is empty.
 * @property uncontrolled measurements below the control threshold, without repetition.
 */
data class MeasurementControl(
    val redundancy: Int,
    val uncontrolled: List<UncontrolledMeasurement>,
) {
    init {
        require(redundancy >= 0) { "Redundancy must not be negative: $redundancy" }
        require(redundancy > 0 || uncontrolled.isEmpty()) { "Without redundancy no single measurement is listed" }
        require(uncontrolled.distinctBy { it.observation }.size == uncontrolled.size) {
            "Expected each measurement at most once"
        }
    }
}

/**
 * Second valid shape of a determined room (DOMAIN section 5.6).
 *
 * @property alternative valid shape farthest from the displayed one, on the raw lengths in room-local coordinates.
 * @property distanceMm [shapeDistanceMm] between the displayed shape and [alternative], above [limitMm].
 * @property limitMm limit `T` of this room, positive.
 * @property recommendation measurement that separates both shapes: the free closing corner `V_0` if its turns differ by
 * more than [limitMm] as arc `|Δτ_0| · U / (2π)`, otherwise the unmeasured diagonal between non-adjacent corners with
 * the largest length difference, ties within `SOLVER_RESIDUAL_MM` to the smallest `(a, b)`. `null` if no such diagonal
 * differs by more than `EXACT_CLOSURE_MM`.
 */
data class ShapeAmbiguity(
    val alternative: Polygon2,
    val distanceMm: Double,
    val limitMm: Double,
    val recommendation: MeasurementRecommendation?,
) {
    init {
        require(limitMm.isFinite() && limitMm > 0.0) { "Invalid limit: $limitMm" }
        require(distanceMm.isFinite() && distanceMm > limitMm) { "An ambiguity needs a distance above the limit" }
        if (recommendation is MeasurementRecommendation.CornerAngle) {
            require(recommendation.corner == 0) { "Only the closing corner can be recommended" }
        }
        if (recommendation is MeasurementRecommendation.DiagonalBetween) {
            val n = alternative.vertices.size
            val from = recommendation.fromCorner
            val to = recommendation.toCorner
            require(to < n) { "Diagonal corner out of range: $to" }
            require(to - from >= 2 && !(from == 0 && to == n - 1)) { "Diagonal must join non-adjacent corners" }
        }
    }
}

/** Rank analysis of the hard conditions (DOMAIN sections 5.4 and 5.5). */
interface DeterminacyAnalyzer {
    /**
     * Determines the independent conditions without solving. The result only routes the evaluation.
     *
     * The rank is taken at [turnsRad], the target turns `τ̂`. If it is below `m` there, it is taken again at the fixed
     * probe point of DOMAIN section 5.4, and the larger rank counts, so that an accidental singularity of `τ̂` does not
     * count as overdetermination. The independent selection is made at the point with the larger rank, `τ̂` on a tie.
     *
     * @param turnsRad target turns `τ̂_0 … τ̂_{n-1}`, one per wall.
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun analyzeConstraints(
        input: ShapeInput,
        turnsRad: List<Double>,
        cancellation: CancellationSignal,
    ): ConstraintAnalysis

    /**
     * Determines the authoritative rank at a closed shape, from the solver or the adjustment (DOMAIN section 5.4).
     *
     * The pivot threshold is `max(RANK_TOLERANCE, RANK_SOLUTION_RESIDUAL_FACTOR · SOLVER_RESIDUAL_MM / U)`.
     *
     * @param lengthsMm effective wall lengths of the shape.
     * @param turnsRad effective turns of the shape.
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun analyze(
        input: ShapeInput,
        lengthsMm: List<Double>,
        turnsRad: List<Double>,
        cancellation: CancellationSignal,
    ): Determinacy

    /**
     * Compares the displayed shape of a determined room with the other solver results (DOMAIN section 5.6, steps A4
     * and B7). Only called with `missing = 0` and kept turn directions.
     *
     * @param outline displayed closed shape: the solver result (step A4) or the adjusted shape (step B7).
     * @param alternatives `SolverOutcome.Solved.alternatives` of step A1 or B1, empty without a solver result.
     * @param limitMm limit `T` of this room.
     * @return the ambiguity with the farthest alternative, ties within `SOLVER_RESIDUAL_MM` to the first in selection
     * order, or `null` if no alternative is farther than [limitMm].
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun analyzeAmbiguity(
        input: ShapeInput,
        outline: Polygon2,
        alternatives: List<ShapeSolution>,
        limitMm: Double,
        cancellation: CancellationSignal,
    ): ShapeAmbiguity?

    /**
     * Lists the measurements of a determined room that are not controllable (DOMAIN section 5.5).
     *
     * @param shape adjusted shape with its redundancy numbers.
     * @param redundancy `r`, not negative.
     * @param limitMm limit `T` of this room.
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun analyzeControl(
        input: ShapeInput,
        shape: AdjustedShape,
        redundancy: Int,
        limitMm: Double,
        cancellation: CancellationSignal,
    ): MeasurementControl
}
