package app.plansnap.core.geometry.primitives

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.sign

class RobustPredicatesTest {
    @Test
    fun `given clear turns when orientation is evaluated then sign follows the turn`() {
        val a = Vec2(0.0, 0.0)
        val b = Vec2(10.0, 0.0)
        assertEquals(1, RobustPredicates.orientation(a, b, Vec2(5.0, 1.0)))
        assertEquals(-1, RobustPredicates.orientation(a, b, Vec2(5.0, -1.0)))
        assertEquals(0, RobustPredicates.orientation(a, b, Vec2(20.0, 0.0)))
        assertEquals(0, RobustPredicates.orientation(a, a, a))
    }

    @Test
    fun `given near collinear grid when orientation is evaluated then it matches exact arithmetic`() {
        // Classroom example of Kettner et al.: naive double orientation fails for many of these points.
        val q = Vec2(12.0, 12.0)
        val r = Vec2(24.0, 24.0)
        val step = StrictMath.ulp(0.5)
        var naiveFailures = 0
        for (i in 0 until GRID) {
            for (j in 0 until GRID) {
                val p = Vec2(0.5 + i * step, 0.5 + j * step)
                val expected = oracleOrientation(p, q, r)
                assertEquals(expected, RobustPredicates.orientation(p, q, r), "p=$p")
                if (naiveOrientation(p, q, r) != expected) naiveFailures++
            }
        }
        assertTrue(naiveFailures > 0, "grid must exercise the exact fallback")
    }

    @Test
    fun `given coordinates whose products overflow when orientation is evaluated then sign is exact`() {
        val big = 1e308
        assertEquals(1, RobustPredicates.orientation(Vec2(-big, 0.0), Vec2(big, 0.0), Vec2(0.0, big)))
        assertEquals(-1, RobustPredicates.orientation(Vec2(-big, 0.0), Vec2(big, 0.0), Vec2(0.0, -big)))
        assertEquals(0, RobustPredicates.orientation(Vec2(-big, -big), Vec2(0.0, 0.0), Vec2(big, big)))
    }

    @Test
    fun `given coordinates whose products underflow when orientation is evaluated then sign is exact`() {
        val tiny = Double.MIN_VALUE
        assertEquals(1, RobustPredicates.orientation(Vec2.ZERO, Vec2(tiny, 0.0), Vec2(0.0, tiny)))
        assertEquals(-1, RobustPredicates.orientation(Vec2.ZERO, Vec2(0.0, tiny), Vec2(tiny, 0.0)))
        val small = 1e-160
        assertEquals(1, RobustPredicates.orientation(Vec2.ZERO, Vec2(small, 0.0), Vec2(0.0, small)))
    }

    @Test
    fun `given rings when shoelace sign is evaluated then it is the exact sign`() {
        val square = listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(1.0, 1.0), Vec2(0.0, 1.0))
        assertEquals(1, RobustPredicates.shoelaceSign(square))
        assertEquals(-1, RobustPredicates.shoelaceSign(square.reversed()))
        assertEquals(0, RobustPredicates.shoelaceSign(listOf(Vec2.ZERO, Vec2(1.0, 1.0), Vec2(2.0, 2.0))))
        val tiny = listOf(Vec2.ZERO, Vec2(1e-200, 0.0), Vec2(0.0, 1e-200))
        assertEquals(1, RobustPredicates.shoelaceSign(tiny))
        val huge = listOf(Vec2.ZERO, Vec2(1e300, 0.0), Vec2(1e300, 1e300), Vec2(0.0, 1e300))
        assertEquals(1, RobustPredicates.shoelaceSign(huge))
        assertEquals(-1, RobustPredicates.shoelaceSign(huge.reversed()))
    }

    @Test
    fun `given near collinear triangles when shoelace sign is evaluated then it matches exact arithmetic`() {
        val q = Vec2(12.0, 12.0)
        val r = Vec2(24.0, 24.0)
        val step = StrictMath.ulp(0.5)
        for (i in 0 until GRID) {
            for (j in 0 until GRID) {
                val p = Vec2(0.5 + i * step, 0.5 + j * step)
                assertEquals(oracleOrientation(p, q, r), RobustPredicates.shoelaceSign(listOf(p, q, r)), "p=$p")
            }
        }
    }

    @Test
    fun `given rings when exact signed area is evaluated then it is half the exact sum`() {
        val square = listOf(Vec2(0.0, 0.0), Vec2(4.0, 0.0), Vec2(4.0, 3.0), Vec2(0.0, 3.0))
        assertEquals(12.0, RobustPredicates.exactSignedArea(square), 0.0)
        assertEquals(-12.0, RobustPredicates.exactSignedArea(square.reversed()), 0.0)
        val huge = listOf(Vec2.ZERO, Vec2(1e300, 0.0), Vec2(1e300, 1e300), Vec2(0.0, 1e300))
        assertEquals(Double.POSITIVE_INFINITY, RobustPredicates.exactSignedArea(huge), 0.0)
    }

    private fun naiveOrientation(
        a: Vec2,
        b: Vec2,
        c: Vec2,
    ): Int = ((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)).sign.toInt()

    private companion object {
        const val GRID = 64

        fun oracleOrientation(
            a: Vec2,
            b: Vec2,
            c: Vec2,
        ): Int {
            val ax = BigDecimal(a.x)
            val ay = BigDecimal(a.y)
            val det =
                (BigDecimal(b.x) - ax) * (BigDecimal(c.y) - ay) - (BigDecimal(b.y) - ay) * (BigDecimal(c.x) - ax)
            return det.signum()
        }
    }
}
