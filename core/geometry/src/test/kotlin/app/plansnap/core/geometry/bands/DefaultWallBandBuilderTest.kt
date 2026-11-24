package app.plansnap.core.geometry.bands

import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultWallBandBuilderTest {
    private val builder: WallBandBuilder = DefaultWallBandBuilder()

    @Test
    fun `given square with equal thickness when built then every convex corner is mitered`() {
        val layout = build(SQUARE, 120L, 120L, 120L, 120L)

        assertEquals(List(4) { CornerJoin.MITER }, layout.corners.map { it.join })
        assertTrue(layout.corners.all { it.fill == null })
        assertEquals((0..3).toList(), layout.corners.map { it.cornerIndex })
        assertEquals((0..3).toList(), layout.bands.map { it.wallIndex })
        assertShape(
            listOf(v(0.0, 0.0), v(3000.0, 0.0), v(3120.0, -120.0), v(-120.0, -120.0)),
            layout.bands[0],
        )
        assertShape(
            listOf(v(3000.0, 0.0), v(3000.0, 3000.0), v(3120.0, 3120.0), v(3120.0, -120.0)),
            layout.bands[1],
        )
        assertShape(
            listOf(v(3000.0, 3000.0), v(0.0, 3000.0), v(-120.0, 3120.0), v(3120.0, 3120.0)),
            layout.bands[2],
        )
        assertShape(
            listOf(v(0.0, 3000.0), v(0.0, 0.0), v(-120.0, -120.0), v(-120.0, 3120.0)),
            layout.bands[3],
        )
    }

    @Test
    fun `given clockwise square when built then bands still lie outside`() {
        val clockwise = listOf(v(0.0, 0.0), v(0.0, 3000.0), v(3000.0, 3000.0), v(3000.0, 0.0))
        val layout = builder.build(BandInput(Polygon2(clockwise), Winding.CLOCKWISE, List(4) { 120L }))

        assertEquals(List(4) { CornerJoin.MITER }, layout.corners.map { it.join })
        assertShape(
            listOf(v(0.0, 0.0), v(0.0, 3000.0), v(-120.0, 3120.0), v(-120.0, -120.0)),
            layout.bands[0],
        )
    }

    @Test
    fun `given mixed thicknesses at right corner when built then larger thickness sets the miter limit`() {
        val layout = build(SQUARE, 120L, 480L, 120L, 480L)

        assertEquals(List(4) { CornerJoin.MITER }, layout.corners.map { it.join })
        assertShape(
            listOf(v(0.0, 0.0), v(3000.0, 0.0), v(3480.0, -120.0), v(-480.0, -120.0)),
            layout.bands[0],
        )
        assertShape(
            listOf(v(3000.0, 0.0), v(3000.0, 3000.0), v(3480.0, 3120.0), v(3480.0, -120.0)),
            layout.bands[1],
        )
    }

    @Test
    fun `given zero thickness wall when built then it is a line and the neighbor miters onto its line`() {
        val layout = build(SQUARE, 0L, 120L, 0L, 120L)

        assertEquals(WallBand.Line(0, v(0.0, 0.0), v(3000.0, 0.0)), layout.bands[0])
        assertEquals(WallBand.Line(2, v(3000.0, 3000.0), v(0.0, 3000.0)), layout.bands[2])
        assertEquals(List(4) { CornerJoin.MITER }, layout.corners.map { it.join })
        assertShape(
            listOf(v(3000.0, 0.0), v(3000.0, 3000.0), v(3120.0, 3000.0), v(3120.0, 0.0)),
            layout.bands[1],
        )
    }

    @Test
    fun `given all thicknesses zero when built then only lines remain and corners join in a point`() {
        val layout = build(SQUARE, 0L, 0L, 0L, 0L)

        assertTrue(layout.bands.all { it is WallBand.Line })
        assertEquals(List(4) { CornerJoin.MITER }, layout.corners.map { it.join })
        assertTrue(layout.corners.all { it.fill == null })
    }

    @Test
    fun `given reflex corner when built then bands end perpendicular at the inner corner`() {
        val layout = build(L_SHAPE, 120L, 120L, 120L, 120L, 120L, 120L)

        assertEquals(
            listOf(
                CornerJoin.MITER,
                CornerJoin.MITER,
                CornerJoin.MITER,
                CornerJoin.CONCAVE,
                CornerJoin.MITER,
                CornerJoin.MITER,
            ),
            layout.corners.map { it.join },
        )
        assertNull(layout.corners[3].fill)
        assertShape(
            listOf(v(6000.0, 2000.0), v(3000.0, 2000.0), v(3000.0, 2120.0), v(6120.0, 2120.0)),
            layout.bands[2],
        )
        assertShape(
            listOf(v(3000.0, 2000.0), v(3000.0, 4000.0), v(3120.0, 4120.0), v(3120.0, 2000.0)),
            layout.bands[3],
        )
    }

    @Test
    fun `given collinear vertex with different thicknesses when built then both bands end perpendicular`() {
        val outline = listOf(v(0.0, 0.0), v(2000.0, 0.0), v(4000.0, 0.0), v(4000.0, 3000.0), v(0.0, 3000.0))
        val layout = build(outline, 120L, 240L, 120L, 120L, 120L)

        assertEquals(CornerJoin.COLLINEAR, layout.corners[1].join)
        assertNull(layout.corners[1].fill)
        assertShape(
            listOf(v(0.0, 0.0), v(2000.0, 0.0), v(2000.0, -120.0), v(-120.0, -120.0)),
            layout.bands[0],
        )
        assertShape(
            listOf(v(2000.0, 0.0), v(4000.0, 0.0), v(4120.0, -240.0), v(2000.0, -240.0)),
            layout.bands[1],
        )
    }

    @Test
    fun `given turn just below the collinear limit when built then the corner is collinear`() {
        // sin τ = 0.0025 / 5000 = 5e-7 < NEAR_COLLINEAR_SIN.
        val layout = build(nearCollinear(0.0025), 120L, 120L, 120L, 120L, 120L)

        assertEquals(CornerJoin.COLLINEAR, layout.corners[1].join)
    }

    @Test
    fun `given turn just above the collinear limit when built then equal thicknesses miter`() {
        // sin τ = 0.01 / 5000 = 2e-6 ≥ NEAR_COLLINEAR_SIN, the miter point lies about t from the corner.
        val layout = build(nearCollinear(0.01), 120L, 120L, 120L, 120L, 120L)

        assertEquals(CornerJoin.MITER, layout.corners[1].join)
        val outerEnd = (layout.bands[0] as WallBand.Filled).shape.vertices[2]
        assertEquals(5000.0, outerEnd.x, 1e-3)
        assertEquals(-120.0, outerEnd.y, 1e-6)
    }

    @Test
    fun `given slight convex turn with different thicknesses when built then the corner is beveled`() {
        // |m| ≈ |t_b − t_a| / sin τ = 60 mm / 2e-6, far beyond the miter limit.
        val layout = build(nearCollinear(0.01), 120L, 180L, 120L, 120L, 120L)

        val corner = layout.corners[1]
        assertEquals(CornerJoin.BEVEL, corner.join)
        val fill = requireNotNull(corner.fill).vertices
        assertClose(v(5000.0, 0.0), fill[0])
        assertClose(v(5000.0, -120.0), fill[1])
        assertClose(v(5000.0, -180.0), fill[2], 1e-3)
    }

    @Test
    fun `given acute corner of twenty degrees when built then it is beveled with a triangle`() {
        val layout = build(ACUTE_TRIANGLE, 120L, 120L, 120L)

        assertEquals(
            listOf(CornerJoin.BEVEL, CornerJoin.MITER, CornerJoin.MITER),
            layout.corners.map { it.join },
        )
        val fill = requireNotNull(layout.corners[0].fill).vertices
        assertClose(v(0.0, 0.0), fill[0])
        assertClose(v(-120.0 * SIN_20, 120.0 * COS_20), fill[1])
        assertClose(v(0.0, -120.0), fill[2])
        val band0 = (layout.bands[0] as WallBand.Filled).shape.vertices
        assertClose(v(0.0, -120.0), band0[3])
        val band2 = (layout.bands[2] as WallBand.Filled).shape.vertices
        assertClose(v(-120.0 * SIN_20, 120.0 * COS_20), band2[2])
    }

    @Test
    fun `given acute corner of eighty degrees when built then miter lies t over cos fifty from the corner`() {
        val layout = build(ACUTE_TRIANGLE, 120L, 120L, 120L)

        val miter = (layout.bands[0] as WallBand.Filled).shape.vertices[2]
        assertEquals(120.0 / COS_50, (miter - v(10_000.0, 0.0)).length(), 1e-9)
        assertEquals(-120.0, miter.y, 1e-9)
    }

    @Test
    fun `given forty five degree corner when built then miter point matches the analytic value`() {
        val triangle = listOf(v(0.0, 0.0), v(3000.0, 0.0), v(0.0, 3000.0))
        val layout = build(triangle, 120L, 120L, 120L)

        assertEquals(List(3) { CornerJoin.MITER }, layout.corners.map { it.join })
        val miter = (layout.bands[0] as WallBand.Filled).shape.vertices[2]
        assertClose(v(3000.0 + 120.0 * (1.0 + SQRT_2), -120.0), miter)
    }

    @Test
    fun `given corners on both sides of the miter limit when built then joins switch from miter to bevel`() {
        assertEquals(CornerJoin.MITER, build(isosceles(30.0), 120L, 120L, 120L).corners[0].join)
        assertEquals(CornerJoin.BEVEL, build(isosceles(28.0), 120L, 120L, 120L).corners[0].join)
        assertEquals(CornerJoin.MITER, build(isosceles(28.0), 120L, 120L, 0L).corners[0].join)
    }

    @Test
    fun `given reference rooms when built then joins follow the interior angles`() {
        for (reference in ReferenceRooms.ALL) {
            val n = reference.vertices.size
            val layout = build(reference.vertices, *LongArray(n) { 120L })
            val expected = reference.expectedInteriorAnglesDeg.map(::expectedJoin)
            assertEquals(expected, layout.corners.map { it.join }, reference.key)
        }
    }

    @Test
    fun `given reference rooms when thickness changes then the inner contour and its area stay unchanged`() {
        val analyzer = DefaultPolygonAnalyzer()
        for (reference in ReferenceRooms.ALL) {
            val n = reference.vertices.size
            for (thickness in listOf(0L, 120L, 1000L)) {
                val layout = build(reference.vertices, *LongArray(n) { thickness })
                val inner = Polygon2(layout.bands.map { it.innerStart() })
                assertEquals(reference.vertices, inner.vertices, reference.key)
                assertEquals(reference.vertices, layout.bands.map { it.innerEnd() }.rotateRight(), reference.key)
                assertEquals(
                    reference.expectedAreaMm2,
                    analyzer.areaMm2(inner),
                    reference.expectedAreaMm2 * 1e-12,
                    reference.key,
                )
            }
        }
    }

    @Test
    fun `given wall of length zero when built then it is rejected`() {
        val outline = listOf(v(0.0, 0.0), v(3000.0, 0.0), v(3000.0, 0.0), v(0.0, 3000.0))

        assertThrows<IllegalArgumentException> { build(outline, 120L, 120L, 120L, 120L) }
    }

    @Test
    fun `given bevel join when constructing corner band without fill then it is rejected`() {
        assertThrows<IllegalArgumentException> { CornerBand(0, CornerJoin.BEVEL, null) }
        assertThrows<IllegalArgumentException> {
            CornerBand(0, CornerJoin.MITER, Polygon2(listOf(v(0.0, 0.0), v(1.0, 0.0), v(0.0, 1.0))))
        }
    }

    private fun build(
        vertices: List<Vec2>,
        vararg thicknessesMm: Long,
    ): BandLayout = builder.build(BandInput(Polygon2(vertices), Winding.COUNTER_CLOCKWISE, thicknessesMm.toList()))

    private companion object {
        const val SIN_20 = 0.3420201433256687
        const val COS_20 = 0.9396926207859084
        const val COS_50 = 0.6427876096865394
        const val SQRT_2 = 1.4142135623730951
        const val STRAIGHT_DEG = 180.0
        const val ANGLE_EPSILON_DEG = 1e-6

        /** Smallest interior angle with a miter at equal thicknesses: `1 / cos((180° − α) / 2) ≤ 4`. */
        const val MITER_MIN_ANGLE_DEG = 28.95502437185984
        const val TOLERANCE_MM = 1e-9

        val SQUARE = listOf(v(0.0, 0.0), v(3000.0, 0.0), v(3000.0, 3000.0), v(0.0, 3000.0))
        val L_SHAPE =
            listOf(
                v(0.0, 0.0),
                v(6000.0, 0.0),
                v(6000.0, 2000.0),
                v(3000.0, 2000.0),
                v(3000.0, 4000.0),
                v(0.0, 4000.0),
            )

        /** Triangle with 20° at `V_0` and 80° at the two other corners. */
        val ACUTE_TRIANGLE = listOf(v(0.0, 0.0), v(10_000.0, 0.0), v(10_000.0 * COS_20, 10_000.0 * SIN_20))

        fun v(
            x: Double,
            y: Double,
        ) = Vec2(x, y)

        /** Pentagon whose corner `V_1` turns left by `sin τ ≈ rise / 5000`. */
        fun nearCollinear(rise: Double) =
            listOf(v(0.0, 0.0), v(5000.0, 0.0), v(10_000.0, rise), v(10_000.0, 3000.0), v(0.0, 3000.0))

        /** Isosceles triangle with the interior angle [apexDeg] at `V_0`. */
        fun isosceles(apexDeg: Double): List<Vec2> {
            val apex = StrictMath.toRadians(apexDeg)
            return listOf(v(0.0, 0.0), v(10_000.0, 0.0), v(10_000.0, 0.0).rotated(apex))
        }

        fun expectedJoin(interiorDeg: Double): CornerJoin =
            when {
                interiorDeg > STRAIGHT_DEG + ANGLE_EPSILON_DEG -> CornerJoin.CONCAVE
                interiorDeg > STRAIGHT_DEG - ANGLE_EPSILON_DEG -> CornerJoin.COLLINEAR
                interiorDeg >= MITER_MIN_ANGLE_DEG -> CornerJoin.MITER
                else -> CornerJoin.BEVEL
            }

        fun WallBand.innerStart(): Vec2 =
            when (this) {
                is WallBand.Line -> start
                is WallBand.Filled -> shape.vertices[0]
            }

        fun WallBand.innerEnd(): Vec2 =
            when (this) {
                is WallBand.Line -> end
                is WallBand.Filled -> shape.vertices[1]
            }

        fun <T> List<T>.rotateRight(): List<T> = listOf(last()) + dropLast(1)

        fun assertShape(
            expected: List<Vec2>,
            band: WallBand,
        ) {
            val shape = (band as WallBand.Filled).shape.vertices
            assertEquals(expected.size, shape.size)
            expected.zip(shape).forEach { (e, a) -> assertClose(e, a) }
        }

        fun assertClose(
            expected: Vec2,
            actual: Vec2,
            tolerance: Double = TOLERANCE_MM,
        ) {
            assertTrue(actual.isCloseTo(expected, tolerance), "expected $expected but was $actual")
        }
    }
}
