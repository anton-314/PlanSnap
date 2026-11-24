package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import kotlin.math.abs

/**
 * Start refinement of DOMAIN section 5.3: Levenberg-Marquardt on the variables `u` over all rows of DOMAIN section 5.4
 * in their fixed order. Each step solves `(JᵀJ + δI) d = −Jᵀc` and is accepted if `‖c‖₂` decreases.
 */
internal class StartRefinement(
    private val problem: SolverProblem,
    private val cancellation: CancellationSignal,
) {
    /** Returns the refined full turns of [turns]. Only the variables at `SolverProblem.free` change. */
    fun refine(turns: DoubleArray): DoubleArray {
        val start = problem.freeValues(turns)
        val startResiduals = residualsAt(start)
        var current = Trial(start, startResiduals, dot(startResiduals, startResiduals))
        var damping = GeometryTolerances.START_REFINEMENT_INITIAL_DAMPING
        var iteration = 0
        var finished = false
        while (!finished && iteration < GeometryTolerances.START_REFINEMENT_MAX_ITERATIONS) {
            cancellation.checkpoint()
            val jacobian = problem.jacobian(problem.rows, problem.walk(problem.fullTurns(current.u)))
            val gradient = transposedTimes(jacobian, current.residuals)
            val stationary = gradient.all { abs(it) < GeometryTolerances.START_REFINEMENT_GRADIENT }
            val (accepted, nextDamping) =
                if (stationary) null to damping else dampedStep(current, normalMatrix(jacobian), gradient, damping)
            damping = nextDamping
            if (accepted == null) finished = true else current = accepted
            iteration++
        }
        return problem.fullTurns(current.u)
    }

    /**
     * Tries at most [GeometryTolerances.START_REFINEMENT_MAX_TRIALS] dampings. Returns the accepted trial, `null` if
     * none reduces `‖c‖₂`, and the damping for the next step.
     */
    private fun dampedStep(
        current: Trial,
        normal: DenseMatrix,
        gradient: DoubleArray,
        initialDamping: Double,
    ): Pair<Trial?, Double> {
        var damping = initialDamping
        var accepted: Trial? = null
        var trial = 0
        while (accepted == null && trial < GeometryTolerances.START_REFINEMENT_MAX_TRIALS) {
            accepted = attempt(current, normal, gradient, damping)
            damping =
                if (accepted == null) {
                    damping * GeometryTolerances.START_REFINEMENT_DAMPING_INCREASE
                } else {
                    maxOf(
                        damping / GeometryTolerances.START_REFINEMENT_DAMPING_DECREASE,
                        GeometryTolerances.START_REFINEMENT_MIN_DAMPING,
                    )
                }
            trial++
        }
        return accepted to damping
    }

    /** One damped trial, `null` if the system is not solvable or `‖c‖₂` does not decrease. */
    private fun attempt(
        current: Trial,
        normal: DenseMatrix,
        gradient: DoubleArray,
        damping: Double,
    ): Trial? {
        val u = current.u
        val size = u.size
        val damped = DenseMatrix(size, size)
        for (i in 0 until size) {
            for (j in 0 until size) damped[i, j] = normal[i, j]
            damped[i, i] = normal[i, i] + damping
        }
        val step = DenseLinearAlgebra.solve(damped, DoubleArray(size) { -gradient[it] }) ?: return null
        val candidate = DoubleArray(size) { u[it] + step[it] }
        val residuals = residualsAt(candidate)
        val candidateNorm = dot(residuals, residuals)
        return if (candidateNorm < current.squareNorm) Trial(candidate, residuals, candidateNorm) else null
    }

    private fun residualsAt(u: DoubleArray): DoubleArray {
        val turns = problem.fullTurns(u)
        return problem.residuals(problem.rows, turns, problem.walk(turns))
    }

    private class Trial(
        val u: DoubleArray,
        val residuals: DoubleArray,
        val squareNorm: Double,
    )

    private companion object {
        fun dot(
            a: DoubleArray,
            b: DoubleArray,
        ): Double {
            var sum = 0.0
            for (i in a.indices) sum += a[i] * b[i]
            return sum
        }

        /** `Jᵀ · values`. */
        fun transposedTimes(
            jacobian: DenseMatrix,
            values: DoubleArray,
        ): DoubleArray =
            DoubleArray(jacobian.columnCount) { j ->
                var sum = 0.0
                for (r in 0 until jacobian.rowCount) sum += jacobian[r, j] * values[r]
                sum
            }

        /** `JᵀJ`. */
        fun normalMatrix(jacobian: DenseMatrix): DenseMatrix {
            val size = jacobian.columnCount
            val normal = DenseMatrix(size, size)
            for (i in 0 until size) {
                for (j in 0 until size) {
                    var sum = 0.0
                    for (r in 0 until jacobian.rowCount) sum += jacobian[r, i] * jacobian[r, j]
                    normal[i, j] = sum
                }
            }
            return normal
        }
    }
}
