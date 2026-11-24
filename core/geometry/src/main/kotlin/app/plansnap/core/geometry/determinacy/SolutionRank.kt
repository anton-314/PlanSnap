package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.solver.HardCondition
import app.plansnap.core.geometry.solver.SolverProblem
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * Authoritative rank `ρ_L` at a closed shape and the recommended measurement (DOMAIN section 5.4).
 *
 * The pivot threshold is `max(RANK_TOLERANCE, RANK_SOLUTION_RESIDUAL_FACTOR · SOLVER_RESIDUAL_MM / U)` with `U` the
 * perimeter of the walked lengths.
 */
internal class SolutionRank(
    private val input: ShapeInput,
    private val problem: SolverProblem,
    private val chain: WalkedChain,
    private val cancellation: CancellationSignal,
) {
    private val rows: List<RankRow> = problem.rows.map { RankRow.Condition(it) }
    private val jacobian: Array<DoubleArray> = rankJacobian(input, problem.free, rows, chain)
    private val tolerance: Double =
        maxOf(
            GeometryTolerances.RANK_TOLERANCE,
            GeometryTolerances.RANK_SOLUTION_RESIDUAL_FACTOR * GeometryTolerances.SOLVER_RESIDUAL_MM / chain.perimeter,
        )

    /** `ρ_L`. */
    val rank: Int = rankOf(jacobian, tolerance, cancellation)

    /** The next measurement while `ρ_L < v`: a diagonal first, else a corner angle, `null` if none helps. */
    fun recommendation(): MeasurementRecommendation? = diagonal() ?: corner()

    /** Longest unmeasured diagonal between non-adjacent corners that raises the rank, ties to the smallest `(a, b)`. */
    private fun diagonal(): MeasurementRecommendation.DiagonalBetween? {
        val measured = input.diagonals.map { minOf(it.fromCorner, it.toCorner) to maxOf(it.fromCorner, it.toCorner) }
        val raising = ArrayList<Pair<MeasurementRecommendation.DiagonalBetween, Double>>()
        for ((a, b) in nonAdjacentPairs(input.wallCount)) {
            val length = chain.distance(a, b)
            if ((a to b) in measured || !(length > 0.0)) continue
            val extended = jacobian + rankJacobian(input, problem.free, listOf(RankRow.ExtraDiagonal(a, b)), chain)
            if (rankOf(extended, tolerance, cancellation) > rank) {
                raising += MeasurementRecommendation.DiagonalBetween(a, b) to length
            }
        }
        val longest = raising.maxOfOrNull { it.second } ?: return null
        return raising.first { it.second >= longest - GeometryTolerances.SOLVER_RESIDUAL_MM }.first
    }

    /** First corner by index that is not measured and whose angle lowers `missing`. */
    private fun corner(): MeasurementRecommendation.CornerAngle? {
        val free = problem.free
        val index =
            input.corners.indices.firstOrNull { i ->
                when {
                    input.corners[i] is CornerInput.Measured -> false
                    i == 0 -> raisesRankWithClosingCorner()
                    else -> keepsRankWithout(free.indexOf(i))
                }
            }
        return index?.let { MeasurementRecommendation.CornerAngle(it) }
    }

    private fun raisesRankWithClosingCorner(): Boolean {
        val row = rankJacobian(input, problem.free, listOf(RankRow.Condition(HardCondition.ClosingCorner)), chain)
        return rankOf(jacobian + row, tolerance, cancellation) > rank
    }

    /** Measuring the corner of [column] removes it from `u_frei`. `missing` drops if the rank stays. */
    private fun keepsRankWithout(column: Int): Boolean {
        val reduced = Array(jacobian.size) { r -> jacobian[r].filterIndexed { k, _ -> k != column }.toDoubleArray() }
        return rankOf(reduced, tolerance, cancellation) >= rank
    }
}

/** Pairs `(a, b)` with `a < b` of non-adjacent corners in lexicographic order. */
internal fun nonAdjacentPairs(n: Int): List<Pair<Int, Int>> =
    (0 until n).flatMap { a -> (a + 2 until n).filter { b -> !(a == 0 && b == n - 1) }.map { b -> a to b } }
