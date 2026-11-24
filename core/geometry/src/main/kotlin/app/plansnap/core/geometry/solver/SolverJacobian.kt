package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.traverse.DiagonalInput

/** Analytic Jacobian `∂c/∂u` of [selected] at [chain], distance rows divided by `U` (DOMAIN section 5.2). */
internal fun SolverProblem.jacobian(
    selected: List<HardCondition>,
    chain: Chain,
): DenseMatrix {
    val matrix = DenseMatrix(selected.size, free.size)
    for (r in selected.indices) {
        for (k in free.indices) matrix[r, k] = derivative(selected[r], free[k], chain)
    }
    return matrix
}

/** A turn at `j` rotates every corner after `V_j` about `V_j`. */
private fun SolverProblem.derivative(
    row: HardCondition,
    j: Int,
    chain: Chain,
): Double =
    when (row) {
        HardCondition.ClosureX -> -(chain.y[n] - chain.y[j]) / perimeter
        HardCondition.ClosureY -> (chain.x[n] - chain.x[j]) / perimeter
        HardCondition.ClosingCorner -> -1.0
        is HardCondition.DiagonalLength -> diagonalDerivative(input.diagonals[row.index], j, chain)
    }

/** Only corners in `(j, b]` move relative to `V_a`, rotating about `V_max(a, j)`. */
private fun SolverProblem.diagonalDerivative(
    diagonal: DiagonalInput,
    j: Int,
    chain: Chain,
): Double {
    val a = minOf(diagonal.fromCorner, diagonal.toCorner)
    val b = maxOf(diagonal.fromCorner, diagonal.toCorner)
    if (j >= b) return 0.0
    val pivot = if (j <= a) a else j
    val dvx = chain.x[b] - chain.x[pivot]
    val dvy = chain.y[b] - chain.y[pivot]
    val diffX = chain.x[b] - chain.x[a]
    val diffY = chain.y[b] - chain.y[a]
    return (diffX * -dvy + diffY * dvx) / (diagonal.lengthMm.toDouble() * perimeter)
}
