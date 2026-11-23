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
    /** A valid shape was found. */
    data class Solved(
        val solution: ShapeSolution,
    ) : SolverOutcome

    /** No start value produced a valid simple outline: `CONFLICT(NO_SIMPLE_SHAPE)`. */
    data object NoSimpleShape : SolverOutcome
}

/** Deterministic equality-constrained SQP solver (DOMAIN sections 5.1 to 5.3, ADR 0004). */
fun interface ShapeSolver {
    /**
     * Solves [input], which must not be overdetermined (DOMAIN section 5.4). Equal input yields a bit-identical
     * outcome.
     *
     * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
     */
    fun solve(
        input: ShapeInput,
        cancellation: CancellationSignal,
    ): SolverOutcome
}
