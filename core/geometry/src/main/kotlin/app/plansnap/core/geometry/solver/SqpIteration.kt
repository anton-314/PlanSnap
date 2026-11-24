package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import kotlin.math.abs

/**
 * Equality-constrained SQP of DOMAIN section 5.2 for one start value.
 *
 * Once an iterate is closed and simple, a step is only accepted if it keeps the outline simple. A singular Jacobian is
 * thinned by the rank determination of DOMAIN section 5.4 before the KKT system is solved.
 */
internal class SqpIteration(
    private val problem: SolverProblem,
    private val selected: List<HardCondition>,
    private val cancellation: CancellationSignal,
) {
    /** Whether [selected] contains both closure rows, so that the result is a closed outline. */
    private val closed: Boolean = HardCondition.ClosureX in selected && HardCondition.ClosureY in selected

    /**
     * Runs at most [GeometryTolerances.SOLVER_MAX_ITERATIONS] steps from [start] and returns the full turns of the
     * result, or `null` if no closed and, for a closed outline, simple point was reached.
     */
    fun solve(start: DoubleArray): DoubleArray? {
        val run = Run(problem.freeValues(start))
        var iteration = 0
        while (run.running && iteration < GeometryTolerances.SOLVER_MAX_ITERATIONS) {
            cancellation.checkpoint()
            advance(run)
            iteration++
        }
        if (run.converged) return run.result
        val last = evaluate(run.u)
        return if (last.feasible && (!closed || last.keepsSimple)) last.turns else run.best
    }

    /** One SQP step: stops on convergence, a singular system or a failed line search, otherwise moves [run]. */
    private fun advance(run: Run) {
        val point = evaluate(run.u)
        if (point.keepsSimple) run.best = point.turns
        val step = kktStep(point)
        when {
            step == null -> {
                run.running = false
            }

            point.feasible && step.stationarity <= GeometryTolerances.SOLVER_GRADIENT -> {
                run.result = if (closed && !point.keepsSimple) run.best else point.turns
                run.converged = true
                run.running = false
            }

            else -> {
                run.penalty = maxOf(run.penalty, step.penaltyCandidate)
                val next = lineSearch(point, step.direction, run.penalty)
                if (next == null) run.running = false else run.u = next
            }
        }
    }

    private fun evaluate(u: DoubleArray): Iterate {
        val turns = problem.fullTurns(u)
        val chain = problem.walk(turns)
        val residuals = problem.residuals(selected, turns, chain)
        val feasible = isFeasible(residuals)
        return Iterate(u, turns, residuals, feasible, feasible && closed && problem.isSimple(chain), chain)
    }

    private fun isFeasible(residuals: DoubleArray): Boolean =
        maxAbs(residuals) * problem.perimeter <= GeometryTolerances.SOLVER_RESIDUAL_MM

    /** Solves `[H Jᵀ; J 0][δ; λ] = [−g; −c]` on the independent rows of `J`. Dropped rows get `λ = 0`. */
    private fun kktStep(point: Iterate): KktStep? {
        val jacobian = problem.jacobian(selected, point.chain)
        val gradient = problem.gradient(point.turns)
        val kept = DenseLinearAlgebra.independentRows(jacobian, GeometryTolerances.RANK_TOLERANCE)
        val v = problem.free.size
        val size = v + kept.size
        val system = DenseMatrix(size, size)
        val rhs = DoubleArray(size)
        val offDiagonal = if (problem.closingIsSoft) SOFT_CLOSING_CURVATURE else 0.0
        for (i in 0 until v) {
            for (j in 0 until v) system[i, j] = offDiagonal
            system[i, i] = TURN_CURVATURE + offDiagonal + GeometryTolerances.SOLVER_REGULARIZATION
            rhs[i] = -gradient[i]
        }
        for ((r, row) in kept.withIndex()) {
            for (j in 0 until v) {
                system[v + r, j] = jacobian[row, j]
                system[j, v + r] = jacobian[row, j]
            }
            rhs[v + r] = -point.residuals[row]
        }
        val solution = DenseLinearAlgebra.solve(system, rhs) ?: return null
        val multipliers = DoubleArray(selected.size)
        for ((r, row) in kept.withIndex()) multipliers[row] = solution[v + r]
        val stationarity =
            maxAbs(DoubleArray(v) { j -> gradient[j] + (selected.indices).sumOf { jacobian[it, j] * multipliers[it] } })
        val penaltyCandidate = if (selected.isEmpty()) 1.0 else 2.0 * maxAbs(multipliers)
        return KktStep(solution.copyOf(v), stationarity, penaltyCandidate)
    }

    /** Step lengths `1, ½, ¼ …` down to `2⁻²⁰` until the merit `f + ρ‖c‖₁` decreases. */
    private fun lineSearch(
        point: Iterate,
        direction: DoubleArray,
        penalty: Double,
    ): DoubleArray? {
        val merit = problem.cost(point.turns) + penalty * l1(point.residuals)
        var step = 1.0
        repeat(LINE_SEARCH_TRIALS) {
            val candidate = DoubleArray(point.u.size) { point.u[it] + step * direction[it] }
            val turns = problem.fullTurns(candidate)
            val chain = problem.walk(turns)
            val candidateMerit = problem.cost(turns) + penalty * l1(problem.residuals(selected, turns, chain))
            if (candidateMerit < merit && (!point.keepsSimple || problem.isSimple(chain))) return candidate
            step /= 2.0
        }
        return null
    }

    /** Mutable state of one run from a start value. */
    private class Run(
        var u: DoubleArray,
    ) {
        var penalty: Double = 1.0
        var best: DoubleArray? = null
        var result: DoubleArray? = null
        var converged: Boolean = false
        var running: Boolean = true
    }

    private class Iterate(
        val u: DoubleArray,
        val turns: DoubleArray,
        val residuals: DoubleArray,
        val feasible: Boolean,
        val keepsSimple: Boolean,
        val chain: Chain,
    )

    private class KktStep(
        val direction: DoubleArray,
        val stationarity: Double,
        val penaltyCandidate: Double,
    )

    private companion object {
        /** Step lengths `2⁰ … 2⁻²⁰`. */
        const val LINE_SEARCH_TRIALS = 21

        /** Second derivative `2` of each squared turn deviation. */
        const val TURN_CURVATURE = 2.0

        /** Second derivative `2` of the squared closing deviation by any pair of inner turns. */
        const val SOFT_CLOSING_CURVATURE = 2.0

        fun maxAbs(values: DoubleArray): Double = values.fold(0.0) { acc, value -> maxOf(acc, abs(value)) }

        fun l1(values: DoubleArray): Double = values.fold(0.0) { acc, value -> acc + abs(value) }
    }
}
