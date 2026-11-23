package app.plansnap.core.geometry.primitives

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryTolerancesTest {
    @Test
    fun `given domain table when constants are read then integer values match`() {
        assertEquals(10L, GeometryTolerances.MIN_WALL_MM)
        assertEquals(100_000L, GeometryTolerances.MAX_WALL_MM)
        assertEquals(1_000L, GeometryTolerances.MAX_THICKNESS_MM)
        assertEquals(120L, GeometryTolerances.DEFAULT_THICKNESS_MM)
        assertEquals(128, GeometryTolerances.MAX_WALLS_PER_ROOM)
        assertEquals(200, GeometryTolerances.SOLVER_MAX_ITERATIONS)
        assertEquals(50L, GeometryTolerances.DEFAULT_TOLERANCE_ABS_MM)
        assertEquals(50, GeometryTolerances.ADJUSTMENT_MAX_ITERATIONS)
    }

    @Test
    fun `given domain table when constants are read then decimal values match`() {
        val expected =
            mapOf(
                GeometryTolerances.EXACT_CLOSURE_MM to 1.0,
                GeometryTolerances.SOLVER_RESIDUAL_MM to 0.01,
                GeometryTolerances.SOLVER_GRADIENT to 1e-9,
                GeometryTolerances.MIN_FEATURE_MM to 1.0,
                GeometryTolerances.DEFAULT_TOLERANCE_REL to 0.01,
                GeometryTolerances.SOLVER_REGULARIZATION to 1e-8,
                GeometryTolerances.SOLUTION_ROUNDING_RAD to 1e-6,
                GeometryTolerances.SIGMA_LENGTH_MM to 5.0,
                GeometryTolerances.SIGMA_RIGHT_ANGLE_DEG to 0.5,
                GeometryTolerances.SIGMA_FREE_RAD to 10.0,
                GeometryTolerances.MITER_LIMIT_FACTOR to 4.0,
            )
        expected.forEach { (actual, value) -> assertEquals(value, actual, 0.0) }
        assertEquals(1e-9, GeometryTolerances.RANK_TOLERANCE, 0.0)
        assertEquals(1e-9, GeometryTolerances.COST_TIE, 0.0)
        assertEquals(5.0, GeometryTolerances.SIGMA_DIAGONAL_MM, 0.0)
        assertEquals(1.0, GeometryTolerances.SIGMA_MEASURED_ANGLE_DEG, 0.0)
        assertEquals(1e-6, GeometryTolerances.NEAR_COLLINEAR_SIN, 0.0)
    }

    @Test
    fun `given values when compared then tolerance is inclusive`() {
        assertTrue(1.0.isCloseTo(1.5, 0.5))
        assertFalse(1.0.isCloseTo(1.5000001, 0.5))
        assertTrue(0.0.isCloseTo(-0.0, 0.0))
    }

    @Test
    fun `given non finite operands when compared then they are never close`() {
        val inf = Double.POSITIVE_INFINITY
        assertFalse(Double.NaN.isCloseTo(Double.NaN, 1.0))
        assertFalse(inf.isCloseTo(inf, 1.0))
        assertFalse(1.0.isCloseTo(Double.NaN, 1.0))
        assertFalse(1.0.isCloseTo(Double.NEGATIVE_INFINITY, 1.0))
    }

    @Test
    fun `given invalid tolerance when compared then it is rejected`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { 1.0.isCloseTo(1.0, it) }
        }
    }

    @Test
    fun `given perimeter of 14 m when limit is computed then relative part wins`() {
        assertEquals(140.0, ClosureTolerance().limitMm(14_000.0), 1e-9)
    }

    @Test
    fun `given small perimeter when limit is computed then absolute part wins`() {
        assertEquals(50.0, ClosureTolerance().limitMm(1_000.0), 0.0)
        assertEquals(50.0, ClosureTolerance().limitMm(0.0), 0.0)
    }

    @Test
    fun `given invalid perimeter when limit is computed then it is rejected`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { ClosureTolerance().limitMm(it) }
        }
    }

    @Test
    fun `given invalid tolerance settings when created then they are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClosureTolerance(absoluteMm = -1L) }
        listOf(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { ClosureTolerance(relative = it) }
        }
    }

    @Test
    fun `given boundary tolerance settings when created then they are accepted`() {
        assertEquals(0.0, ClosureTolerance(0L, 0.0).limitMm(1_000.0), 0.0)
        assertEquals(1_000.0, ClosureTolerance(0L, 1.0).limitMm(1_000.0), 0.0)
    }
}
