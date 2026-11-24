package app.plansnap.core.geometry.adjustment

import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.rightAngled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdjustmentProblemTest {
    private val input = rightAngled(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(0, 2, 5000)))
    private val problem =
        AdjustmentProblem(input, AdjustmentMode.MEASUREMENT_ERROR, AdjustmentTestSupport.traverse.targets(input))

    @Test
    fun `given only a turn sum residual then it counts as arc length of the raw perimeter`() {
        // U = 14000 mm: 0.0052 rad of turn sum is 11.59 mm of arc (DOMAIN section 6, CR-W1-01).
        val residual = problem.residual(doubleArrayOf(0.0, 0.0, 0.0052, 0.0))
        assertEquals(0.0052 * 14_000.0 / (2.0 * StrictMath.PI), residual, EXACT)
    }

    @Test
    fun `given negative turn sum residual then its absolute arc counts`() {
        val residual = problem.residual(doubleArrayOf(0.0, 0.0, -0.001, 0.0))
        assertEquals(0.001 * 14_000.0 / (2.0 * StrictMath.PI), residual, EXACT)
    }

    @Test
    fun `given closure residual then it counts as Euclidean gap`() {
        assertEquals(5.0, problem.residual(doubleArrayOf(3.0, -4.0, 0.0, 0.0)), EXACT)
    }

    @Test
    fun `given diagonal residual then it counts directly in mm`() {
        assertEquals(7.0, problem.residual(doubleArrayOf(0.0, 0.0, 0.0, -7.0)), EXACT)
    }

    @Test
    fun `given several rows then the largest mm value wins`() {
        val turnArc = 0.003 * 14_000.0 / (2.0 * StrictMath.PI)
        assertEquals(turnArc, problem.residual(doubleArrayOf(3.0, 4.0, 0.003, 6.0)), EXACT)
        assertEquals(9.0, problem.residual(doubleArrayOf(3.0, 4.0, 0.003, 9.0)), EXACT)
        assertEquals(10.0, problem.residual(doubleArrayOf(6.0, 8.0, 0.003, 9.0)), EXACT)
    }

    private companion object {
        const val EXACT = 1e-12
    }
}
