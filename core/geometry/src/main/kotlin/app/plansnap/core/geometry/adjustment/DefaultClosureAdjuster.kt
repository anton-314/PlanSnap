package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * Stateless Gauss-Helmert [ClosureAdjuster] after DOMAIN section 6 and ADR 0005 and 0018.
 *
 * Starting at `x_1 = x_0` with the turns `startTurnsRad`, each step computes
 * `x_{k+1} = x̃_k − Q Aᵀ (A Q Aᵀ)⁻¹ (C(x_k) + A (x̃_k − x_k))` until `‖C‖_∞ ≤ SOLVER_RESIDUAL_MM`, for at most
 * `ADJUSTMENT_MAX_ITERATIONS` steps. The limit is checked before every step, also at `x_1`. `‖C‖_∞` is measured in mm
 * (ADR 0018, addendum CR-W1-01): `max(|(c_x, c_y)|, |c_τ| · U / (2π), max_k |c_k|)`. The closure counts as the
 * Euclidean gap, so the gap of an adjusted outline never exceeds `SOLVER_RESIDUAL_MM`. The turn-sum row
 * `c_τ = wrap(Σ τ_i − 2π·s)` counts as arc length with `U` the perimeter of the raw lengths, so a start that closes
 * but misses the turn sum still takes steps. The outcome is [AdjustmentOutcome.NotConverged] if the
 * limit is not reached, the normal matrix `A Q Aᵀ` is singular, or a step leaves a variable non-finite or a wall or
 * diagonal length not positive.
 *
 * Every result is bit-identical on every device: dense matrices with a fixed loop order, no random numbers, and
 * [StrictMath] for all trigonometry and roots (DOMAIN section 11). The input is only read, never changed.
 */
class DefaultClosureAdjuster : ClosureAdjuster {
    private val traverse = DefaultTraverse()

    override val version: Int = VERSION

    override fun adjust(
        input: ShapeInput,
        mode: AdjustmentMode,
        startTurnsRad: List<Double>,
        cancellation: CancellationSignal,
    ): AdjustmentOutcome {
        require(startTurnsRad.size == input.wallCount) {
            "Expected ${input.wallCount} start turns, got ${startTurnsRad.size}"
        }
        require(startTurnsRad.all { it.isFinite() }) { "Start turns must be finite" }
        val problem = AdjustmentProblem(input, mode, traverse.targets(input))
        val start = problem.observations.copyOf()
        for (i in 0 until problem.wallCount) start[problem.turnIndex(i)] = startTurnsRad[i]
        val solution = iterate(problem, start, cancellation)
        val jacobian = solution?.let(problem::jacobian)
        val system = jacobian?.let { NormalSystem.factor(it, problem.variances, cancellation) }
        return if (solution != null && jacobian != null && system != null) {
            AdjustmentOutcome.Adjusted(adjustedShapeOf(problem, solution, jacobian, system))
        } else {
            AdjustmentOutcome.NotConverged
        }
    }

    /** Runs the iteration from [start]. Returns the converged variables or `null`. */
    private fun iterate(
        problem: AdjustmentProblem,
        start: DoubleArray,
        cancellation: CancellationSignal,
    ): DoubleArray? {
        var x: DoubleArray? = start
        var steps = 0
        while (x != null) {
            cancellation.checkpoint()
            val conditions = problem.conditions(x)
            if (problem.residual(conditions) <= GeometryTolerances.SOLVER_RESIDUAL_MM) return x
            val mayStep = steps < GeometryTolerances.ADJUSTMENT_MAX_ITERATIONS
            x = if (mayStep) step(problem, x, conditions, cancellation) else null
            steps++
        }
        return null
    }

    /** One Gauss-Helmert step from [x]. Returns `null` for a singular system or an invalid result. */
    private fun step(
        problem: AdjustmentProblem,
        x: DoubleArray,
        conditions: DoubleArray,
        cancellation: CancellationSignal,
    ): DoubleArray? {
        val a = problem.jacobian(x)
        val system = NormalSystem.factor(a, problem.variances, cancellation) ?: return null
        val reference = problem.reference(x)
        val rhs = DoubleArray(a.size) { row -> conditions[row] + dot(a[row], reference, x) }
        val w = system.solve(rhs)
        val next =
            DoubleArray(x.size) { j ->
                var correction = 0.0
                for (row in a.indices) correction += a[row][j] * w[row]
                reference[j] - problem.variances[j] * correction
            }
        val valid =
            next.all { it.isFinite() } &&
                (0 until problem.wallCount).all { next[it] > 0.0 } &&
                (0 until problem.diagonalCount).all { next[problem.diagonalIndex(it)] > 0.0 }
        return next.takeIf { valid }
    }

    private companion object {
        /** Version 1: first production port of the prototype in `docs/research/evaluation-prototype`. */
        const val VERSION = 1

        /** `Σ_j row_j · (reference_j − x_j)`. */
        fun dot(
            row: DoubleArray,
            reference: DoubleArray,
            x: DoubleArray,
        ): Double {
            var sum = 0.0
            for (j in row.indices) sum += row[j] * (reference[j] - x[j])
            return sum
        }
    }
}
