package app.plansnap.core.geometry.primitives

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class PrimitivesTest {
    @Test
    fun `given non finite component when created then vector is rejected`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertThrows(IllegalArgumentException::class.java) { Vec2(it, 0.0) }
            assertThrows(IllegalArgumentException::class.java) { Vec2(0.0, it) }
        }
    }

    @Test
    fun `given overflowing operation when computed then result is rejected`() {
        val huge = Vec2(Double.MAX_VALUE, 0.0)
        assertThrows(IllegalArgumentException::class.java) { huge + huge }
        assertThrows(IllegalArgumentException::class.java) { huge * 2.0 }
    }

    @Test
    fun `given vectors when combined then arithmetic is correct`() {
        val a = Vec2(1.0, 2.0)
        val b = Vec2(3.0, -4.0)
        assertTrue((a + b).isCloseTo(Vec2(4.0, -2.0), 0.0))
        assertTrue((a - b).isCloseTo(Vec2(-2.0, 6.0), 0.0))
        assertTrue((a * 2.0).isCloseTo(Vec2(2.0, 4.0), 0.0))
        assertEquals(-5.0, a.dot(b), 0.0)
        assertEquals(-10.0, a.cross(b), 0.0)
        assertEquals(5.0, b.length(), 0.0)
    }

    @Test
    fun `given vector when rotated a quarter turn then it is counter clockwise`() {
        assertTrue(Vec2(1.0, 0.0).rotated(PI / 2.0).isCloseTo(Vec2(0.0, 1.0), 1e-15))
    }

    @Test
    fun `given vectors when compared then both axes must be close`() {
        assertFalse(Vec2.ZERO.isCloseTo(Vec2(0.0, 2.0), 1.0))
        assertFalse(Vec2.ZERO.isCloseTo(Vec2(2.0, 0.0), 1.0))
        assertTrue(Vec2.ZERO.isCloseTo(Vec2(1.0, 1.0), 1.0))
    }

    @Test
    fun `given too few vertices when polygon is created then it is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Polygon2(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Polygon2(listOf(Vec2.ZERO, Vec2(1.0, 0.0))) }
    }

    @Test
    fun `given mutable source when polygon is created then later changes do not leak`() {
        val source = mutableListOf(Vec2.ZERO, Vec2(1.0, 0.0), Vec2(0.0, 1.0))
        val polygon = Polygon2(source)
        source.clear()
        assertEquals(3, polygon.vertices.size)
    }

    @Test
    fun `given polygons when compared then equality follows vertices`() {
        val vertices = listOf(Vec2.ZERO, Vec2(1.0, 0.0), Vec2(0.0, 1.0))
        val polygon = Polygon2(vertices)
        assertEquals(polygon, Polygon2(vertices))
        assertEquals(polygon.hashCode(), Polygon2(vertices).hashCode())
        assertNotEquals(polygon, Polygon2(vertices.reversed()))
        assertTrue(polygon.toString().contains("Polygon2"))
    }

    @Test
    fun `given windings when read then signs match the convention`() {
        assertEquals(1, Winding.COUNTER_CLOCKWISE.sign)
        assertEquals(-1, Winding.CLOCKWISE.sign)
    }
}
