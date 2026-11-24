package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.traverse.ShapeInput

/** Fixed start values of the solver in their mandatory order (DOMAIN section 5.3). */
enum class SolverStart {
    /** Target turns. */
    TARGET_TURNS,

    /** Mean of target turns and the regular polygon turn. */
    MEAN_TURNS,

    /** Turns of the chord polygon. */
    CHORD_POLYGON,

    /** Equal turn magnitude with the declared and measured directions. */
    DIRECTION_TURNS,
}

/** One hard condition of DOMAIN section 5.1, used to select an independent set (DOMAIN section 5.4). */
sealed interface HardCondition {
    /** Closure in x. */
    data object ClosureX : HardCondition

    /** Closure in y. */
    data object ClosureY : HardCondition

    /** Measured closing corner. */
    data object ClosingCorner : HardCondition

    /** Diagonal at [index] in the input order. */
    data class DiagonalLength(
        val index: Int,
    ) : HardCondition {
        init {
            require(index >= 0) { "Diagonal index must not be negative: $index" }
        }
    }
}

/**
 * Selected solution of the shape solver.
 *
 * @property turnsRad turns `τ_0 … τ_{n-1}` in radians.
 * @property outline resulting inner contour in room-local coordinates.
 * @property cost objective value `f`, finite and not negative.
 * @property start start value that produced this solution.
 * @property honorsDirections whether all declared and measured turn directions are kept.
 */
data class ShapeSolution(
    val turnsRad: List<Double>,
    val outline: Polygon2,
    val cost: Double,
    val start: SolverStart,
    val honorsDirections: Boolean,
) {
    init {
        require(turnsRad.size == outline.vertices.size) { "Expected one turn per vertex" }
        require(turnsRad.all { it.isFinite() }) { "Turns must be finite" }
        require(cost.isFinite() && cost >= 0.0) { "Invalid cost: $cost" }
    }
}

/** Result of the shape solver. */
sealed interface SolverOutcome {
    /**
     * A valid shape was found.
     *
     * @property solution selected result (DOMAIN section 5.3).
     * @property alternatives every other valid result of the fixed starts that keeps all turn directions and meets all
     * hard conditions of the input on the raw lengths within `EXACT_CLOSURE_MM`, in selection order (DOMAIN section
     * 5.6). This includes the conditions outside the solved set `I` (step B1). Near-duplicates of [solution] are
     * allowed. The list is the input of the ambiguity check in steps A4 and B7. An empty list does not prove that the
     * shape is unique.
     */
    data class Solved(
        val solution: ShapeSolution,
        val alternatives: List<ShapeSolution>,
    ) : SolverOutcome {
        init {
            require(alternatives.all { it.outline.vertices.size == solution.outline.vertices.size }) {
                "Alternatives must have the vertex count of the solution"
            }
            require(alternatives.all { it.honorsDirections }) { "Alternatives must keep all turn directions" }
        }
    }

    /** No start value produced a valid simple outline. Not a final status: the evaluation continues at step B2. */
    data object NoSimpleShape : SolverOutcome
}

/** Deterministic equality-constrained SQP solver (DOMAIN sections 5.1 to 5.3, ADR 0004). */
fun interface ShapeSolver {
    /**
     * Solves [input] under the hard [conditions], normally `ConstraintAnalysis.independentSelection` (DOMAIN sections
     * 5.4 and 5.5). Hard conditions outside [conditions] are not enforced. They only refine the start and rank the
     * candidates by their residual (DOMAIN section 5.3). If [conditions] omits a hard condition, the result is only a
     * linearization start for the adjustment and is never displayed. Equal arguments yield a bit-identical outcome.
     *
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun solve(
        input: ShapeInput,
        conditions: List<HardCondition>,
        cancellation: CancellationSignal,
    ): SolverOutcome
}
