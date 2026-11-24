package app.plansnap.core.geometry.solver

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DenseLinearAlgebraTest {
    @Test
    fun `given regular system that needs a row swap when solved then the exact solution results`() {
        // The first pivot is zero, so partial pivoting must swap rows. Solution (1, 2, 3).
        val a =
            matrix(arrayOf(doubleArrayOf(0.0, 2.0, 1.0), doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(2.0, 1.0, 3.0)))
        val x = DenseLinearAlgebra.solve(a, doubleArrayOf(7.0, 6.0, 13.0))
        assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), x, TOLERANCE)
    }

    @Test
    fun `given system with small first pivot when solved then the larger pivot keeps it accurate`() {
        val a = matrix(arrayOf(doubleArrayOf(1e-20, 1.0), doubleArrayOf(1.0, 1.0)))
        val x = DenseLinearAlgebra.solve(a, doubleArrayOf(1.0, 2.0))
        assertArrayEquals(doubleArrayOf(1.0, 1.0), x, TOLERANCE)
    }

    @Test
    fun `given singular system when solved then no solution is returned`() {
        val a = matrix(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0)))
        assertNull(DenseLinearAlgebra.solve(a, doubleArrayOf(1.0, 2.0)))
    }

    @Test
    fun `given system whose solution overflows when solved then no solution is returned`() {
        val a = matrix(arrayOf(doubleArrayOf(1e-300, 0.0), doubleArrayOf(0.0, 1.0)))
        assertNull(DenseLinearAlgebra.solve(a, doubleArrayOf(1e300, 1.0)))
    }

    @Test
    fun `given non square system when solved then it is rejected`() {
        assertThrows<IllegalArgumentException> { DenseLinearAlgebra.solve(DenseMatrix(2, 3), doubleArrayOf(1.0, 2.0)) }
        assertThrows<IllegalArgumentException> { DenseLinearAlgebra.solve(DenseMatrix(3, 2), doubleArrayOf(1.0, 2.0)) }
    }

    @Test
    fun `given empty system when solved then the empty solution results`() {
        assertArrayEquals(doubleArrayOf(), DenseLinearAlgebra.solve(DenseMatrix(0, 0), doubleArrayOf()), 0.0)
    }

    @Test
    fun `given dependent rows when thinned then the first independent rows remain in order`() {
        val m =
            matrix(
                arrayOf(
                    doubleArrayOf(1.0, 0.0, 1.0),
                    doubleArrayOf(2.0, 0.0, 2.0),
                    doubleArrayOf(0.0, 1.0, 0.0),
                    doubleArrayOf(1.0, 1.0, 1.0),
                    doubleArrayOf(0.0, 0.0, 1.0),
                ),
            )
        assertArrayEquals(intArrayOf(0, 2, 4), DenseLinearAlgebra.independentRows(m, 1e-9))
    }

    @Test
    fun `given row below the relative threshold when thinned then it counts as dependent`() {
        val m = matrix(arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1e-12), doubleArrayOf(0.0, 1e-3)))
        assertArrayEquals(intArrayOf(0, 2), DenseLinearAlgebra.independentRows(m, 1e-9))
    }

    @Test
    fun `given zero matrix when thinned then no row remains`() {
        assertArrayEquals(intArrayOf(), DenseLinearAlgebra.independentRows(DenseMatrix(2, 2), 1e-9))
        assertArrayEquals(intArrayOf(), DenseLinearAlgebra.independentRows(DenseMatrix(2, 0), 1e-9))
    }

    @Test
    fun `given matrix when the largest entry is negative then max abs is its magnitude`() {
        val m = matrix(arrayOf(doubleArrayOf(1.0, -5.0), doubleArrayOf(3.0, 2.0)))
        assertEquals(5.0, m.maxAbs(), 0.0)
        assertEquals(0.0, DenseMatrix(0, 0).maxAbs(), 0.0)
    }

    private fun matrix(rows: Array<DoubleArray>): DenseMatrix {
        val m = DenseMatrix(rows.size, rows[0].size)
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, value -> m[r, c] = value } }
        return m
    }

    private companion object {
        const val TOLERANCE = 1e-12
    }
}
