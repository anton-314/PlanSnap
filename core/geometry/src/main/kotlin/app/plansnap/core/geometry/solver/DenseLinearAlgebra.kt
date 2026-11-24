package app.plansnap.core.geometry.solver

import kotlin.math.abs

/** Dense row-major matrix of the solver (DOMAIN section 5.2). Values are mutable, the shape is fixed. */
internal class DenseMatrix(
    val rowCount: Int,
    val columnCount: Int,
) {
    private val values = DoubleArray(rowCount * columnCount)

    operator fun get(
        row: Int,
        column: Int,
    ): Double = values[row * columnCount + column]

    operator fun set(
        row: Int,
        column: Int,
        value: Double,
    ) {
        values[row * columnCount + column] = value
    }

    /** Largest absolute entry, `0.0` for an empty matrix. */
    fun maxAbs(): Double = values.fold(0.0) { acc, value -> maxOf(acc, abs(value)) }
}

/** Deterministic dense linear algebra without dependencies. The evaluation order is fixed. */
internal object DenseLinearAlgebra {
    /**
     * Solves the square system `a · x = b` by LU elimination with partial pivoting.
     *
     * Returns `null` if no pivot is usable, that is a column has only zero entries, or if the solution is not finite.
     * This is the exact singularity of the IEEE values, not a geometric tolerance.
     */
    fun solve(
        a: DenseMatrix,
        b: DoubleArray,
    ): DoubleArray? {
        val size = b.size
        require(a.rowCount == size && a.columnCount == size) { "Expected a square system of size $size" }
        val m = Array(size) { row -> DoubleArray(size) { column -> a[row, column] } }
        val x = b.copyOf()
        for (k in 0 until size) {
            val pivotRow = (k until size).maxBy { abs(m[it][k]) }
            if (!(abs(m[pivotRow][k]) > 0.0)) return null
            swap(m, x, k, pivotRow)
            eliminateBelow(m, x, k)
        }
        for (k in size - 1 downTo 0) {
            var sum = x[k]
            for (j in k + 1 until size) sum -= m[k][j] * x[j]
            x[k] = sum / m[k][k]
        }
        return x.takeIf { solution -> solution.all { it.isFinite() } }
    }

    /**
     * Returns the indices of a maximal set of linearly independent rows of [matrix], taken greedily in row order.
     *
     * Each row is reduced against the rows kept so far. It is kept if its largest remaining entry exceeds
     * `relativeTolerance · max|matrix|`, and that entry becomes its pivot column (DOMAIN section 5.4).
     */
    fun independentRows(
        matrix: DenseMatrix,
        relativeTolerance: Double,
    ): IntArray {
        val threshold = relativeTolerance * matrix.maxAbs()
        val basis = ArrayList<Pair<DoubleArray, Int>>()
        val kept = ArrayList<Int>()
        for (row in 0 until matrix.rowCount) {
            val reduced = DoubleArray(matrix.columnCount) { matrix[row, it] }
            for ((vector, pivot) in basis) {
                val factor = reduced[pivot] / vector[pivot]
                for (j in reduced.indices) reduced[j] -= factor * vector[j]
            }
            val pivot = reduced.indices.maxByOrNull { abs(reduced[it]) }
            if (pivot != null && abs(reduced[pivot]) > threshold) {
                basis += reduced to pivot
                kept += row
            }
        }
        return kept.toIntArray()
    }

    private fun swap(
        m: Array<DoubleArray>,
        x: DoubleArray,
        k: Int,
        pivotRow: Int,
    ) {
        if (pivotRow == k) return
        val row = m[k]
        m[k] = m[pivotRow]
        m[pivotRow] = row
        val value = x[k]
        x[k] = x[pivotRow]
        x[pivotRow] = value
    }

    private fun eliminateBelow(
        m: Array<DoubleArray>,
        x: DoubleArray,
        k: Int,
    ) {
        for (i in k + 1 until m.size) {
            val factor = m[i][k] / m[k][k]
            for (j in k until m.size) m[i][j] -= factor * m[k][j]
            x[i] -= factor * x[k]
        }
    }
}
