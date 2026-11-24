package app.plansnap.core.geometry.polygon

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.testing.fixtures.rooms.ReferenceRoom
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DefaultPolygonAnalyzerTest {
    private val analyzer: PolygonAnalyzer = DefaultPolygonAnalyzer()

    @ParameterizedTest(name = "{0}")
    @MethodSource("referenceRooms")
    fun `given analytic reference room when analyzed then metrics match`(reference: ReferenceRoom) {
        val polygon = Polygon2(reference.vertices)
        val area = reference.expectedAreaMm2
        val perimeter = reference.expectedPerimeterMm
        assertEquals(area, analyzer.signedAreaMm2(polygon), area * RELATIVE_TOLERANCE)
        assertEquals(area, analyzer.areaMm2(polygon), area * RELATIVE_TOLERANCE)
        assertEquals(perimeter, analyzer.perimeterMm(polygon), perimeter * RELATIVE_TOLERANCE)
        assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(polygon))
        assertTrue(analyzer.isSimple(polygon))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("referenceRooms")
    fun `given clockwise reference room when analyzed then sign flips and area stays`(reference: ReferenceRoom) {
        val polygon = Polygon2(reference.vertices.reversed())
        val area = reference.expectedAreaMm2
        assertEquals(-area, analyzer.signedAreaMm2(polygon), area * RELATIVE_TOLERANCE)
        assertEquals(area, analyzer.areaMm2(polygon), area * RELATIVE_TOLERANCE)
        assertEquals(Winding.CLOCKWISE, analyzer.winding(polygon))
        assertTrue(analyzer.isSimple(polygon))
    }

    @Test
    fun `given integer rectangle when analyzed then values are exact`() {
        val polygon = rectangle(4000.0, 3000.0)
        assertEquals(12_000_000.0, analyzer.signedAreaMm2(polygon), 0.0)
        assertEquals(14_000.0, analyzer.perimeterMm(polygon), 0.0)
    }

    @Test
    fun `given rectangle far from origin when analyzed then area keeps full precision`() {
        val offset = Vec2(1e12, -1e12)
        val polygon = Polygon2(rectangle(4000.0, 3000.0).vertices.map { it + offset })
        assertEquals(12_000_000.0, analyzer.areaMm2(polygon), 0.0)
        assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(polygon))
        assertTrue(analyzer.isSimple(polygon))
    }

    @Test
    fun `given polygon with 128 walls when analyzed then it is simple and matches the regular polygon`() {
        val n = GeometryTolerances.MAX_WALLS_PER_ROOM
        val radius = 50_000.0
        val vertices = (0 until n).map { Vec2(radius, 0.0).rotated(2.0 * Math.PI * it / n) }
        val polygon = Polygon2(vertices)
        val expectedArea = 0.5 * n * radius * radius * StrictMath.sin(2.0 * Math.PI / n)
        val expectedPerimeter = 2.0 * n * radius * StrictMath.sin(Math.PI / n)
        assertEquals(expectedArea, analyzer.areaMm2(polygon), expectedArea * RELATIVE_TOLERANCE)
        assertEquals(expectedPerimeter, analyzer.perimeterMm(polygon), expectedPerimeter * RELATIVE_TOLERANCE)
        assertTrue(analyzer.isSimple(polygon))
    }

    @Test
    fun `given collinear vertices when analyzed then polygon has no area winding or simplicity`() {
        val polygon = polygonOf(0.0 to 0.0, 10.0 to 0.0, 20.0 to 0.0)
        assertEquals(0.0, analyzer.signedAreaMm2(polygon), 0.0)
        assertNull(analyzer.winding(polygon))
        assertFalse(analyzer.isSimple(polygon))
        assertEquals(40.0, analyzer.perimeterMm(polygon), 0.0)
    }

    @Test
    fun `given identical vertices when analyzed then everything is zero and not simple`() {
        val polygon = polygonOf(1.0 to 1.0, 1.0 to 1.0, 1.0 to 1.0)
        assertEquals(0.0, analyzer.areaMm2(polygon), 0.0)
        assertEquals(0.0, analyzer.perimeterMm(polygon), 0.0)
        assertNull(analyzer.winding(polygon))
        assertFalse(analyzer.isSimple(polygon))
    }

    @Test
    fun `given duplicate consecutive vertex when checked then polygon is not simple`() {
        val polygon = polygonOf(0.0 to 0.0, 0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0)
        assertEquals(50.0, analyzer.areaMm2(polygon), 0.0)
        assertFalse(analyzer.isSimple(polygon))
        assertFalse(analyzer.isSimple(polygon, 0.0))
    }

    @Test
    fun `given straight collinear corner when checked then polygon stays simple`() {
        val polygon = polygonOf(0.0 to 0.0, 10.0 to 0.0, 20.0 to 0.0, 20.0 to 10.0, 0.0 to 10.0)
        assertEquals(200.0, analyzer.areaMm2(polygon), 0.0)
        assertTrue(analyzer.isSimple(polygon))
    }

    @Test
    fun `given wall folding back onto its neighbor when checked then polygon is not simple`() {
        val spike = polygonOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 10.0 to 5.0, 0.0 to 10.0)
        assertFalse(analyzer.isSimple(spike, 0.0))
        val overshoot = polygonOf(5.0 to 0.0, 10.0 to 0.0, 0.0 to 0.0, 0.0 to 10.0)
        assertFalse(analyzer.isSimple(overshoot, 0.0))
    }

    @Test
    fun `given crossing edges when checked then polygon is not simple`() {
        val bowtie = polygonOf(0.0 to 0.0, 10.0 to 10.0, 10.0 to 0.0, 0.0 to 10.0)
        assertNull(analyzer.winding(bowtie))
        assertFalse(analyzer.isSimple(bowtie))
        val crossed = polygonOf(0.0 to 0.0, 30.0 to 10.0, 30.0 to 0.0, 0.0 to 20.0)
        assertEquals(150.0, analyzer.signedAreaMm2(crossed), 0.0)
        assertFalse(analyzer.isSimple(crossed, 0.0))
    }

    @Test
    fun `given vertex touching a non adjacent edge when checked then polygon is not simple`() {
        val polygon = polygonOf(0.0 to 0.0, 20.0 to 0.0, 20.0 to 20.0, 10.0 to 0.0, 0.0 to 20.0)
        assertFalse(analyzer.isSimple(polygon, 0.0))
    }

    @Test
    fun `given collinear overlapping non adjacent edges when checked then polygon is not simple`() {
        assertFalse(analyzer.isSimple(slit(0.0), 0.0))
    }

    @Test
    fun `given slit exactly min feature wide when checked then polygon is simple`() {
        assertTrue(analyzer.isSimple(slit(GeometryTolerances.MIN_FEATURE_MM)))
    }

    @Test
    fun `given slit narrower than min feature when checked then polygon is not simple`() {
        assertFalse(analyzer.isSimple(slit(0.999)))
        assertTrue(analyzer.isSimple(slit(0.999), 0.5))
    }

    @Test
    fun `given vertex closer than min feature to an edge when checked then polygon is not simple`() {
        val polygon = polygonOf(0.0 to 0.0, 20.0 to 0.0, 20.0 to 20.0, 10.0 to 0.5, 0.0 to 20.0)
        assertTrue(analyzer.isSimple(polygon, 0.0))
        assertFalse(analyzer.isSimple(polygon))
    }

    @Test
    fun `given oblique edges when checked then the distance threshold is measured perpendicular`() {
        val angle = 0.5
        assertFalse(analyzer.isSimple(rotated(slit(0.9), angle)))
        assertTrue(analyzer.isSimple(rotated(slit(1.1), angle)))
        assertTrue(analyzer.isSimple(rotated(slit(0.9), angle), 0.8))
        val near = polygonOf(0.0 to 0.0, 20.0 to 0.0, 20.0 to 20.0, 10.0 to 0.5, 0.0 to 20.0)
        assertFalse(analyzer.isSimple(rotated(near, angle)))
        assertTrue(analyzer.isSimple(rotated(near, angle), 0.4))
    }

    @Test
    fun `given vertex beyond the end of a non adjacent edge when checked then endpoint distance counts`() {
        // Vertex (10.6, 10.6) lies 0.6 · √2 ≈ 0.85 mm diagonally beyond the endpoint (10, 10) of edge (0, 10)-(10, 10).
        val polygon =
            polygonOf(
                0.0 to 0.0,
                30.0 to 0.0,
                30.0 to 30.0,
                10.6 to 10.6,
                10.6 to 30.0,
                -10.0 to 30.0,
                -10.0 to 10.0,
                10.0 to 10.0,
                10.0 to 5.0,
                0.0 to 5.0,
            )
        assertTrue(analyzer.isSimple(polygon, 0.8))
        assertFalse(analyzer.isSimple(polygon, 0.9))
    }

    @Test
    fun `given vertex exactly min feature from edge endpoints when checked then the bound is inclusive`() {
        val hook = notchedHook()
        assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(hook))
        assertTrue(analyzer.isSimple(hook, HOOK_GAP_MM))
        assertFalse(analyzer.isSimple(hook, Math.nextUp(HOOK_GAP_MM)))
        val rotatedHook = rotated(hook, 1.0)
        assertTrue(analyzer.isSimple(rotatedHook, HOOK_GAP_MM - 1e-6))
        assertFalse(analyzer.isSimple(rotatedHook, HOOK_GAP_MM + 1e-6))
    }

    @Test
    fun `given near contact when start vertex shifts then every edge role yields the same distance verdict`() {
        val near = polygonOf(0.0 to 0.0, 20.0 to 0.0, 20.0 to 20.0, 10.0 to 0.5, 0.0 to 20.0)
        val cases = listOf(Triple(near, 0.4, 0.6), Triple(slit(0.999), 0.9, 1.0), Triple(notchedHook(), 4.9, 5.1))
        for ((polygon, simpleAt, conflictAt) in cases) {
            for (shifted in cyclicShifts(polygon)) {
                assertTrue(analyzer.isSimple(shifted, simpleAt), "simple at $simpleAt: $shifted")
                assertFalse(analyzer.isSimple(shifted, conflictAt), "conflict at $conflictAt: $shifted")
            }
        }
    }

    @Test
    fun `given invalid min feature when checked then only exact contacts count or every pair conflicts`() {
        assertTrue(analyzer.isSimple(slit(0.5), Double.NaN))
        assertTrue(analyzer.isSimple(slit(0.5), -1.0))
        assertFalse(analyzer.isSimple(slit(0.0), Double.NaN))
        assertFalse(analyzer.isSimple(rectangle(4000.0, 3000.0), Double.POSITIVE_INFINITY))
        val triangle = polygonOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0)
        assertTrue(analyzer.isSimple(triangle, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `given very small and very large coordinates when analyzed then results stay defined`() {
        val tiny = polygonOf(0.0 to 0.0, 1e-200 to 0.0, 0.0 to 1e-200)
        assertEquals(0.0, analyzer.signedAreaMm2(tiny), 0.0)
        assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(tiny))
        val huge = rectangle(1e300, 1e300)
        assertEquals(Double.POSITIVE_INFINITY, analyzer.areaMm2(huge), 0.0)
        assertEquals(Double.NEGATIVE_INFINITY, analyzer.signedAreaMm2(Polygon2(huge.vertices.reversed())), 0.0)
        assertEquals(4e300, analyzer.perimeterMm(huge), 4e300 * RELATIVE_TOLERANCE)
        assertTrue(analyzer.isSimple(huge))
        val extreme = polygonOf(-Double.MAX_VALUE to 0.0, Double.MAX_VALUE to 0.0, 0.0 to Double.MAX_VALUE)
        assertEquals(Double.POSITIVE_INFINITY, analyzer.areaMm2(extreme), 0.0)
        assertEquals(Double.POSITIVE_INFINITY, analyzer.perimeterMm(extreme), 0.0)
        assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(extreme))
    }

    @Test
    fun `given fewer than three or non finite vertices when built then the contract rejects them`() {
        assertThrows(IllegalArgumentException::class.java) { Polygon2(listOf(Vec2.ZERO, Vec2(1.0, 0.0))) }
        assertThrows(IllegalArgumentException::class.java) { Vec2(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Vec2(0.0, Double.POSITIVE_INFINITY) }
    }

    private companion object {
        const val RELATIVE_TOLERANCE = 1e-12

        /** Distance of the 3-4-5 offset between vertex `E` and vertex `A` in [notchedHook]. */
        const val HOOK_GAP_MM = 5.0

        /** The analytic reference rooms of TESTING section 3, named by key. */
        @JvmStatic
        fun referenceRooms(): List<Named<ReferenceRoom>> = ReferenceRooms.ALL.map { Named.of(it.key, it) }

        fun polygonOf(vararg points: Pair<Double, Double>): Polygon2 =
            Polygon2(points.map { Vec2(it.first, it.second) })

        fun rectangle(
            width: Double,
            height: Double,
        ): Polygon2 = polygonOf(0.0 to 0.0, width to 0.0, width to height, 0.0 to height)

        fun rotated(
            polygon: Polygon2,
            radians: Double,
        ): Polygon2 = Polygon2(polygon.vertices.map { it.rotated(radians) })

        /**
         * Outline whose only close approach is vertex `E = (13, 14)` at exactly [HOOK_GAP_MM] from vertex
         * `A = (10, 10)` by the offset (3, 4). `E` lies beyond the end of edge `HA` and before the start of edge `AB`,
         * so both clamps of the projection decide the distance. All other non-adjacent edges are farther apart.
         */
        fun notchedHook(): Polygon2 =
            polygonOf(
                10.0 to 10.0,
                10.0 to -30.0,
                60.0 to -30.0,
                60.0 to 60.0,
                13.0 to 14.0,
                13.0 to 60.0,
                -20.0 to 60.0,
                -20.0 to 10.0,
            )

        /** All cyclic shifts of the vertex list of [polygon]. */
        fun cyclicShifts(polygon: Polygon2): List<Polygon2> =
            polygon.vertices.indices.map { shift ->
                Polygon2(polygon.vertices.drop(shift) + polygon.vertices.take(shift))
            }

        /** Square 1000 mm with a slit of [width] mm cut in from the top edge down to y = 100. */
        fun slit(width: Double): Polygon2 =
            polygonOf(
                0.0 to 0.0,
                1000.0 to 0.0,
                1000.0 to 1000.0,
                500.0 + width to 1000.0,
                500.0 + width to 100.0,
                500.0 to 100.0,
                500.0 to 1000.0,
                0.0 to 1000.0,
            )
    }
}
