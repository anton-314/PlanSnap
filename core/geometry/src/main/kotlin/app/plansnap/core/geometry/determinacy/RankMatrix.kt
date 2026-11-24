package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.solver.HardCondition
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * Chain walked with arbitrary lengths and turns, `V_0` at the origin and `w_0` along +x (DOMAIN section 2).
 *
 * The heading of `w_i` is `Σ_{1 ≤ j ≤ i} τ_j`, `τ_0` is ignored. Trigonometry uses [StrictMath] (DOMAIN section 11).
 */
internal class WalkedChain(
    lengthsMm: DoubleArray,
    turnsRad: DoubleArray,
) {
    /** Number of walls `n`. */
    val n: Int = lengthsMm.size

    /** Perimeter `U = Σ L_i` of the walked lengths. */
    val perimeter: Double = lengthsMm.sum()

    /** x of `P_0 … P_n`. */
    val x = DoubleArray(n + 1)

    /** y of `P_0 … P_n`. */
    val y = DoubleArray(n + 1)

    init {
        var heading = 0.0
        for (i in 0 until n) {
            if (i > 0) heading += turnsRad[i]
            x[i + 1] = x[i] + lengthsMm[i] * StrictMath.cos(heading)
            y[i + 1] = y[i] + lengthsMm[i] * StrictMath.sin(heading)
        }
    }

    /** Distance `|P_a P_b|`. */
    fun distance(
        a: Int,
        b: Int,
    ): Double = StrictMath.hypot(x[b] - x[a], y[b] - y[a])
}

/** One row of the Jacobian `J = ∂c/∂u_frei` (DOMAIN section 5.4). */
internal sealed interface RankRow {
    /** A hard condition of the input. */
    data class Condition(
        val condition: HardCondition,
    ) : RankRow

    /** A diagonal that is not measured yet, between the corners `a < b`, normalized by its walked length. */
    data class ExtraDiagonal(
        val from: Int,
        val to: Int,
    ) : RankRow
}

/**
 * Builds `J` of [rows] by the free turns [free] at [chain], distance rows divided by `D · U` as in the solver
 * (DOMAIN section 5.1). A turn at `j` rotates every corner after `V_j` about `V_j`.
 */
internal fun rankJacobian(
    input: ShapeInput,
    free: IntArray,
    rows: List<RankRow>,
    chain: WalkedChain,
): Array<DoubleArray> =
    Array(rows.size) { r ->
        DoubleArray(free.size) { k -> derivative(input, rows[r], free[k], chain) }
    }

private fun derivative(
    input: ShapeInput,
    row: RankRow,
    j: Int,
    chain: WalkedChain,
): Double =
    when (row) {
        is RankRow.ExtraDiagonal -> {
            diagonalDerivative(row.from, row.to, chain.distance(row.from, row.to), j, chain)
        }

        is RankRow.Condition -> {
            when (val condition = row.condition) {
                HardCondition.ClosureX -> {
                    -(chain.y[chain.n] - chain.y[j]) / chain.perimeter
                }

                HardCondition.ClosureY -> {
                    (chain.x[chain.n] - chain.x[j]) / chain.perimeter
                }

                HardCondition.ClosingCorner -> {
                    -1.0
                }

                is HardCondition.DiagonalLength -> {
                    val diagonal = input.diagonals[condition.index]
                    val a = minOf(diagonal.fromCorner, diagonal.toCorner)
                    val b = maxOf(diagonal.fromCorner, diagonal.toCorner)
                    diagonalDerivative(a, b, diagonal.lengthMm.toDouble(), j, chain)
                }
            }
        }
    }

/** Only corners in `(j, b]` move relative to `V_a`, rotating about `V_max(a, j)`. */
private fun diagonalDerivative(
    a: Int,
    b: Int,
    lengthMm: Double,
    j: Int,
    chain: WalkedChain,
): Double {
    if (j >= b) return 0.0
    val pivot = if (j <= a) a else j
    val dvx = chain.x[b] - chain.x[pivot]
    val dvy = chain.y[b] - chain.y[pivot]
    val diffX = chain.x[b] - chain.x[a]
    val diffY = chain.y[b] - chain.y[a]
    return (diffX * -dvy + diffY * dvx) / (lengthMm * chain.perimeter)
}

/**
 * Rank of [matrix] by Gaussian elimination with complete pivoting (DOMAIN section 5.4).
 *
 * The elimination stops when the largest remaining entry is at most `relativeTolerance · max|matrix|`. Ties of the
 * pivot go to the first entry in row-major order, so the result is deterministic. [matrix] is not changed.
 *
 * @throws java.util.concurrent.CancellationException if [cancellation] cancels the computation.
 */
internal fun rankOf(
    matrix: Array<DoubleArray>,
    relativeTolerance: Double,
    cancellation: CancellationSignal,
): Int {
    val rows = matrix.size
    val columns = if (rows == 0) 0 else matrix[0].size
    val a = Array(rows) { matrix[it].copyOf() }
    val threshold = relativeTolerance * maxAbs(a)
    if (!(threshold > 0.0)) return 0
    var rank = 0
    while (rank < minOf(rows, columns)) {
        cancellation.checkpoint()
        val (pivotRow, pivotColumn) = largestEntry(a, rank)
        if (!(StrictMath.abs(a[pivotRow][pivotColumn]) > threshold)) break
        swapRows(a, rank, pivotRow)
        swapColumns(a, rank, pivotColumn)
        eliminateBelow(a, rank)
        rank++
    }
    return rank
}

/**
 * Indices of [rows] taken in their order while each one raises the rank (DOMAIN section 5.4, step 3). The rank of
 * each trial set uses [relativeTolerance] against its own largest entry.
 */
internal fun independentRows(
    matrix: Array<DoubleArray>,
    relativeTolerance: Double,
    cancellation: CancellationSignal,
): List<Int> {
    val kept = ArrayList<Int>()
    for (row in matrix.indices) {
        val trial = Array(kept.size + 1) { if (it < kept.size) matrix[kept[it]] else matrix[row] }
        if (rankOf(trial, relativeTolerance, cancellation) > kept.size) kept += row
    }
    return kept
}

private fun maxAbs(a: Array<DoubleArray>): Double {
    var max = 0.0
    for (row in a) for (value in row) max = maxOf(max, StrictMath.abs(value))
    return max
}

/** Position of the largest absolute entry of the submatrix from `(start, start)`, first in row-major order. */
private fun largestEntry(
    a: Array<DoubleArray>,
    start: Int,
): Pair<Int, Int> {
    var bestRow = start
    var bestColumn = start
    var best = -1.0
    for (i in start until a.size) {
        for (j in start until a[i].size) {
            val value = StrictMath.abs(a[i][j])
            if (value > best) {
                best = value
                bestRow = i
                bestColumn = j
            }
        }
    }
    return bestRow to bestColumn
}

private fun swapRows(
    a: Array<DoubleArray>,
    i: Int,
    k: Int,
) {
    val row = a[i]
    a[i] = a[k]
    a[k] = row
}

private fun swapColumns(
    a: Array<DoubleArray>,
    j: Int,
    k: Int,
) {
    for (row in a) {
        val value = row[j]
        row[j] = row[k]
        row[k] = value
    }
}

private fun eliminateBelow(
    a: Array<DoubleArray>,
    pivot: Int,
) {
    for (i in pivot + 1 until a.size) {
        val factor = a[i][pivot] / a[pivot][pivot]
        for (j in pivot until a[i].size) a[i][j] -= factor * a[pivot][j]
    }
}
