package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.cancellation.CancellationSignal
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
 * @property independentConditions `ρ`, the rank of their Jacobian, at most `min(v, m)`.
 */
data class ConstraintAnalysis(
    val freeVariables: Int,
    val conditionCount: Int,
    val independentConditions: Int,
) {
    init {
        require(freeVariables >= 0) { "Free variables must not be negative" }
        require(conditionCount >= CLOSURE_CONDITIONS) { "Closure alone yields $CLOSURE_CONDITIONS conditions" }
        require(independentConditions in 0..minOf(freeVariables, conditionCount)) {
            "Independent conditions out of range: $independentConditions"
        }
    }

    /** Whether a hard condition is dependent, `m > ρ`. Such a shape goes to DOMAIN section 5.5, not to the solver. */
    val isOverdetermined: Boolean get() = conditionCount > independentConditions

    private companion object {
        const val CLOSURE_CONDITIONS = 2
    }
}

/** Rank analysis of the hard conditions (DOMAIN sections 5.4 and 5.5). */
interface DeterminacyAnalyzer {
    /**
     * Determines the independent conditions without solving.
     *
     * The rank is taken at [turnsRad], the target turns `τ̂`. If it is below `m` there, it is taken again at the fixed
     * probe point of DOMAIN section 5.4, and the larger rank counts, so that an accidental singularity of `τ̂` does not
     * count as overdetermination.
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
     * Analyzes [input] at [solution] of a shape that is not overdetermined.
     *
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun analyze(
        input: ShapeInput,
        solution: ShapeSolution,
        cancellation: CancellationSignal,
    ): Determinacy
}
