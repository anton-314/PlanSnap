package app.plansnap.core.geometry.determinacy

import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ShapeAmbiguityTest {
    private val shape =
        listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(4500.0, 2500.0), Vec2(1000.0, 3000.0), Vec2(-500.0, 1500.0))
    private val other =
        listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(3500.0, 2500.0), Vec2(1000.0, 3400.0), Vec2(-800.0, 1500.0))
    private val pentagon = Polygon2(other)

    @Test
    fun `given equal shapes then the distance is zero`() {
        assertEquals(0.0, shapeDistanceMm(Polygon2(shape), Polygon2(shape)), EPSILON)
    }

    @Test
    fun `given known offset of one vertex then the distance matches the hand calculation`() {
        val square = listOf(Vec2(0.0, 0.0), Vec2(1000.0, 0.0), Vec2(1000.0, 1000.0), Vec2(0.0, 1000.0))
        val scaled = square.map { it * 2.0 }

        assertEquals(sqrt2Times(500.0), shapeDistanceMm(Polygon2(square), Polygon2(scaled)), EPSILON)
    }

    @Test
    fun `given moved and rotated shape then the distance does not change`() {
        val expected = shapeDistanceMm(Polygon2(shape), Polygon2(other))
        val moved = shape.map { it.rotated(ROTATION_RAD) + Vec2(-7300.0, 1200.0) }

        assertEquals(expected, shapeDistanceMm(Polygon2(moved), Polygon2(other)), EPSILON)
        assertEquals(expected, shapeDistanceMm(Polygon2(other), Polygon2(moved)), EPSILON)
    }

    @Test
    fun `given another start corner for both shapes then the distance does not change`() {
        val expected = shapeDistanceMm(Polygon2(shape), Polygon2(other))
        val shiftedShape = shape.drop(2) + shape.take(2)
        val shiftedOther = other.drop(2) + other.take(2)

        assertEquals(expected, shapeDistanceMm(Polygon2(shiftedShape), Polygon2(shiftedOther)), EPSILON)
    }

    @Test
    fun `given mirror image then it counts as a different shape`() {
        val mirrored = shape.map { Vec2(it.x, -it.y) }

        assert(shapeDistanceMm(Polygon2(shape), Polygon2(mirrored)) > MIRROR_MIN_DISTANCE_MM)
    }

    @Test
    fun `given different vertex counts then the distance is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            shapeDistanceMm(Polygon2(shape), Polygon2(shape.dropLast(1)))
        }
    }

    @Test
    fun `given valid recommendations then the ambiguity is accepted`() {
        listOf(
            null,
            MeasurementRecommendation.CornerAngle(0),
            MeasurementRecommendation.DiagonalBetween(0, 2),
            MeasurementRecommendation.DiagonalBetween(1, 4),
        ).forEach { recommendation ->
            assertDoesNotThrow { ShapeAmbiguity(pentagon, 200.0, 100.0, recommendation) }
        }
    }

    @Test
    fun `given distance at or below the limit then the ambiguity is rejected`() {
        listOf(100.0, 99.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { distance ->
            assertRejected { ShapeAmbiguity(pentagon, distance, 100.0, null) }
        }
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { limit ->
            assertRejected { ShapeAmbiguity(pentagon, 200.0, limit, null) }
        }
    }

    @Test
    fun `given recommendation that cannot separate the shapes then the ambiguity is rejected`() {
        listOf(
            MeasurementRecommendation.CornerAngle(1),
            MeasurementRecommendation.DiagonalBetween(0, 1),
            MeasurementRecommendation.DiagonalBetween(0, 4),
            MeasurementRecommendation.DiagonalBetween(2, 5),
        ).forEach { recommendation ->
            assertRejected { ShapeAmbiguity(pentagon, 200.0, 100.0, recommendation) }
        }
    }

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private fun sqrt2Times(value: Double): Double = kotlin.math.sqrt(2.0) * value

    private companion object {
        const val EPSILON = 1e-6
        const val ROTATION_RAD = 1.234
        const val MIRROR_MIN_DISTANCE_MM = 1000.0
    }
}
