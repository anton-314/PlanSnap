package app.plansnap.core.domain.snap

import app.plansnap.core.geometry.primitives.Vec2
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

/** Table tests of snapping (DOMAIN section 10, REQ-UX-010). */
@OptIn(ExperimentalKotest::class)
class DefaultSnapperTest {
    private val snapper = DefaultSnapper()
    private val corner = Vec2(1000.0, 1000.0)
    private val wall = SnapSegment(Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    private val targets = SnapTargets(persistentListOf(corner), persistentListOf(wall))
    private val none = SnapTargets(persistentListOf(), persistentListOf())

    private fun query(
        x: Double,
        y: Double,
        max: Double = 100.0,
        grid: Double? = null,
        enabled: Boolean = true,
    ) = SnapQuery(Vec2(x, y), max, grid, enabled)

    @Test
    fun `given corner and wall both in range when snapping then corner wins even if wall is closer`() {
        val closeWall = SnapSegment(Vec2(0.0, 1010.0), Vec2(2000.0, 1010.0))
        val both = SnapTargets(persistentListOf(corner), persistentListOf(closeWall))
        val result = snapper.snap(query(1050.0, 1010.0), both)
        assertEquals(SnapResult(corner, SnapKind.CORNER), result)
    }

    @Test
    fun `given only wall in range when snapping then point is projected onto the wall`() {
        assertEquals(SnapResult(Vec2(500.0, 0.0), SnapKind.WALL), snapper.snap(query(500.0, 40.0), targets))
    }

    @Test
    fun `given wall and grid in range when snapping then wall wins`() {
        val result = snapper.snap(query(520.0, 30.0, grid = 500.0), targets)
        assertEquals(SnapResult(Vec2(520.0, 0.0), SnapKind.WALL), result)
    }

    @Test
    fun `given only grid in range when snapping then point goes to nearest grid point`() {
        assertEquals(
            SnapResult(Vec2(500.0, 3000.0), SnapKind.GRID),
            snapper.snap(query(520.0, 2980.0, grid = 500.0), none),
        )
    }

    @Test
    fun `given nothing in range when snapping then input is unchanged without kind`() {
        val input = query(520.0, 2800.0, grid = 500.0)
        assertEquals(SnapResult(input.point, null), snapper.snap(input, targets))
    }

    @Test
    fun `given wall end projection beyond the segment when snapping then the end point is used`() {
        assertEquals(SnapResult(Vec2(2000.0, 0.0), SnapKind.WALL), snapper.snap(query(2050.0, 0.0), targets))
    }

    @Test
    fun `given degenerate wall when snapping then its point is used`() {
        val point = Vec2(10.0, 10.0)
        val degenerate = SnapTargets(persistentListOf(), persistentListOf(SnapSegment(point, point)))
        assertEquals(SnapResult(point, SnapKind.WALL), snapper.snap(query(20.0, 10.0), degenerate))
    }

    @Test
    fun `given distance exactly at limit when snapping then candidate qualifies`() {
        assertEquals(SnapKind.CORNER, snapper.snap(query(1100.0, 1000.0), targets).kind)
        assertEquals(SnapKind.WALL, snapper.snap(query(500.0, 100.0), targets).kind)
        assertEquals(SnapKind.GRID, snapper.snap(query(600.0, 1000.0, grid = 500.0, max = 100.0), none).kind)
    }

    @Test
    fun `given distance just beyond limit when snapping then candidate is ignored`() {
        assertNull(snapper.snap(query(1100.01, 1000.0), SnapTargets(persistentListOf(corner), persistentListOf())).kind)
        assertNull(snapper.snap(query(500.0, 100.01), targets).kind)
        assertNull(snapper.snap(query(600.01, 1000.0, grid = 500.0), none).kind)
    }

    @Test
    fun `given two equidistant corners when snapping then the first listed wins`() {
        val a = Vec2(-50.0, 0.0)
        val b = Vec2(50.0, 0.0)
        val ab = SnapTargets(persistentListOf(a, b), persistentListOf())
        val ba = SnapTargets(persistentListOf(b, a), persistentListOf())
        assertEquals(a, snapper.snap(query(0.0, 0.0), ab).point)
        assertEquals(b, snapper.snap(query(0.0, 0.0), ba).point)
    }

    @Test
    fun `given two corners in range when snapping then the nearer one wins`() {
        val far = Vec2(90.0, 0.0)
        val near = Vec2(-20.0, 0.0)
        val result = snapper.snap(query(0.0, 0.0), SnapTargets(persistentListOf(far, near), persistentListOf()))
        assertEquals(near, result.point)
    }

    @Test
    fun `given two walls in range when snapping then the nearer one wins`() {
        val farWall = SnapSegment(Vec2(-500.0, 80.0), Vec2(500.0, 80.0))
        val nearWall = SnapSegment(Vec2(-500.0, -30.0), Vec2(500.0, -30.0))
        val result = snapper.snap(query(0.0, 0.0), SnapTargets(persistentListOf(), persistentListOf(farWall, nearWall)))
        assertEquals(SnapResult(Vec2(0.0, -30.0), SnapKind.WALL), result)
    }

    @Test
    fun `given negative coordinates when snapping to grid then alignment follows multiples of the step`() {
        assertEquals(Vec2(-500.0, -1000.0), snapper.snap(query(-480.0, -980.0, grid = 500.0), none).point)
        assertEquals(Vec2(-500.0, -500.0), snapper.snap(query(-510.0, -520.0, grid = 500.0), none).point)
        assertEquals(Vec2(0.0, 0.0), snapper.snap(query(-20.0, 30.0, grid = 500.0), none).point)
    }

    @Test
    fun `given coordinate exactly between grid lines when snapping then it rounds up deterministically`() {
        val result = snapper.snap(query(-250.0, 250.0, max = 400.0, grid = 500.0), none)
        assertEquals(Vec2(0.0, 500.0), result.point)
    }

    @Test
    fun `given grid point beyond snap distance when snapping then input stays`() {
        // Nearest grid point (250, 250 away from 0/500 lines) is about 353 mm away.
        val input = query(250.0, 250.0, max = 300.0, grid = 500.0)
        assertEquals(SnapResult(input.point, null), snapper.snap(input, none))
    }

    @Test
    fun `given snapping disabled when snapping then the input is returned bit-identical`() {
        val input = query(1000.0, 1000.0, grid = 500.0, enabled = false)
        val result = snapper.snap(input, targets)
        assertSame(input.point, result.point)
        assertNull(result.kind)
        val negativeZero = query(-0.0, 0.1, enabled = false)
        assertEquals(
            (-0.0).toRawBits(),
            snapper
                .snap(negativeZero, targets)
                .point.x
                .toRawBits(),
        )
    }

    @Test
    fun `given identical input when snapping twice then results are equal`() {
        val input = query(990.0, 1010.0, grid = 500.0)
        assertEquals(snapper.snap(input, targets), snapper.snap(input, targets))
    }

    @Test
    fun `given invalid query values when constructing then it is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { query(0.0, 0.0, max = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { query(0.0, 0.0, max = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { query(0.0, 0.0, grid = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { query(0.0, 0.0, grid = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `given any point when snapping then the result is within the snap distance and disabled is identity`() =
        runBlocking<Unit> {
            val config = PropTestConfig(seed = SEED, iterations = ITERATIONS)
            val coordinate = Arb.int(-5_000_000..5_000_000).map { it / 1000.0 }
            checkAll(config, coordinate, coordinate) { x, y ->
                val on = query(x, y, grid = 125.0)
                val result = snapper.snap(on, targets)
                val moved = hypot(result.point.x - x, result.point.y - y)
                assertTrue(moved <= on.maxDistanceMm + EPSILON) { "moved $moved at ($x, $y)" }
                val off = on.copy(enabled = false)
                assertEquals(SnapResult(off.point, null), snapper.snap(off, targets))
            }
        }

    private companion object {
        const val SEED = 20_260_101L
        const val ITERATIONS = 500
        const val EPSILON = 1e-9
    }
}
