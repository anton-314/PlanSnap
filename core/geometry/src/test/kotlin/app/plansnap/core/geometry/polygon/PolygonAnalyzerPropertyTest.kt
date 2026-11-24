package app.plansnap.core.geometry.polygon

import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.RobustPredicates
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.abs

class PolygonAnalyzerPropertyTest {
    private val analyzer: PolygonAnalyzer = DefaultPolygonAnalyzer()

    @Test
    fun `given random polygon when rotated and translated then area and perimeter are unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_RIGID), starPolygons, rigidMotions) { polygon, motion ->
                val moved = motion.apply(polygon)
                val area = analyzer.areaMm2(polygon)
                val perimeter = analyzer.perimeterMm(polygon)
                assertEquals(area, analyzer.areaMm2(moved), area * MOTION_TOLERANCE)
                assertEquals(perimeter, analyzer.perimeterMm(moved), perimeter * MOTION_TOLERANCE)
                assertEquals(analyzer.winding(polygon), analyzer.winding(moved))
            }
        }

    @Test
    fun `given random polygon when vertex order is reversed then orientation flips and area stays`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_REVERSE), starPolygons) { polygon ->
                val reversed = Polygon2(polygon.vertices.reversed())
                val area = analyzer.signedAreaMm2(polygon)
                assertEquals(-area, analyzer.signedAreaMm2(reversed), abs(area) * ORDER_TOLERANCE)
                val perimeter = analyzer.perimeterMm(polygon)
                assertEquals(perimeter, analyzer.perimeterMm(reversed), perimeter * ORDER_TOLERANCE)
                assertEquals(Winding.COUNTER_CLOCKWISE, analyzer.winding(polygon))
                assertEquals(Winding.CLOCKWISE, analyzer.winding(reversed))
            }
        }

    @Test
    fun `given simple polygon when rotated and translated then it stays simple`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_SIMPLE), starPolygons, rigidMotions) { polygon, motion ->
                assertTrue(analyzer.isSimple(polygon), "star-shaped generator yields simple polygons")
                assertTrue(analyzer.isSimple(motion.apply(polygon)))
            }
        }

    @Test
    fun `given crossing polygon when rotated and translated then it stays non simple`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_CROSSED), bowties, rigidMotions) { crossed, motion ->
                assertFalse(analyzer.isSimple(crossed))
                assertFalse(analyzer.isSimple(motion.apply(crossed)))
            }
        }

    @Test
    fun `given random polygon when start vertex is shifted then no metric changes`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_SHIFT), starPolygons) { polygon ->
                val shifted = Polygon2(polygon.vertices.drop(1) + polygon.vertices.first())
                val area = analyzer.signedAreaMm2(polygon)
                assertEquals(area, analyzer.signedAreaMm2(shifted), abs(area) * ORDER_TOLERANCE)
                assertEquals(analyzer.winding(polygon), analyzer.winding(shifted))
                assertEquals(analyzer.isSimple(polygon), analyzer.isSimple(shifted))
            }
        }

    @Test
    fun `given near collinear points when orientation is evaluated then it matches exact arithmetic`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_ORIENTATION), nearCollinearTriples) { (a, b, c) ->
                assertEquals(oracleOrientation(a, b, c), RobustPredicates.orientation(a, b, c))
                val triangle = Polygon2(listOf(a, b, c))
                val expectedWinding =
                    when (oracleOrientation(a, b, c)) {
                        1 -> Winding.COUNTER_CLOCKWISE
                        -1 -> Winding.CLOCKWISE
                        else -> null
                    }
                assertEquals(expectedWinding, analyzer.winding(triangle))
            }
        }

    private data class RigidMotion(
        val radians: Double,
        val offset: Vec2,
    ) {
        fun apply(polygon: Polygon2): Polygon2 = Polygon2(polygon.vertices.map { it.rotated(radians) + offset })
    }

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED_RIGID = 7_001L
        const val SEED_REVERSE = 7_002L
        const val SEED_SIMPLE = 7_003L
        const val SEED_CROSSED = 7_004L
        const val SEED_SHIFT = 7_005L
        const val SEED_ORIENTATION = 7_006L
        const val MIN_VERTICES = 3
        const val MAX_VERTICES = 32
        const val MIN_CROSSING_ANGLE = 0.2
        const val MIN_RADIUS_MM = 2_000.0
        const val MAX_RADIUS_MM = 10_000.0
        const val ANGLE_JITTER = 0.3
        const val MAX_OFFSET_MM = 1_000_000.0
        const val MOTION_TOLERANCE = 1e-9
        const val ORDER_TOLERANCE = 1e-12
        const val NEAR_COLLINEAR_ULPS = 64

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        /** Star-shaped polygons around the origin with strictly increasing angles: always simple. */
        val starPolygons: Arb<Polygon2> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(MIN_VERTICES, MAX_VERTICES + 1)
                val sector = 2.0 * PI / n
                val vertices =
                    (0 until n).map { i ->
                        val angle = i * sector + ANGLE_JITTER * sector * (2.0 * random.nextDouble() - 1.0)
                        val radius = random.nextDouble(MIN_RADIUS_MM, MAX_RADIUS_MM)
                        Vec2(radius, 0.0).rotated(angle)
                    }
                Polygon2(vertices)
            }

        /**
         * Quadrilaterals whose edges 0 and 2 both pass through a common center at an angle of at least
         * [MIN_CROSSING_ANGLE], so these non-adjacent edges cross by construction.
         */
        val bowties: Arb<Polygon2> =
            arbitrary { rs ->
                val random = rs.random
                val center = Vec2(random.nextDouble(-MAX_RADIUS_MM, MAX_RADIUS_MM), random.nextDouble(-1.0, 1.0))
                val first = random.nextDouble(-PI, PI)
                val second = first + random.nextDouble(MIN_CROSSING_ANGLE, PI - MIN_CROSSING_ANGLE)

                fun arm(angle: Double) = Vec2(random.nextDouble(MIN_RADIUS_MM, MAX_RADIUS_MM), 0.0).rotated(angle)
                Polygon2(
                    listOf(
                        center + arm(first),
                        center + arm(first + PI),
                        center + arm(second),
                        center + arm(second + PI),
                    ),
                )
            }

        val rigidMotions: Arb<RigidMotion> =
            arbitrary { rs ->
                val random = rs.random
                RigidMotion(
                    radians = random.nextDouble(-PI, PI),
                    offset =
                        Vec2(
                            random.nextDouble(-MAX_OFFSET_MM, MAX_OFFSET_MM),
                            random.nextDouble(-MAX_OFFSET_MM, MAX_OFFSET_MM),
                        ),
                )
            }

        /** A point within a few ulps of the line through two random points, so naive arithmetic is unreliable. */
        val nearCollinearTriples: Arb<Triple<Vec2, Vec2, Vec2>> =
            arbitrary { rs ->
                val random = rs.random
                val a = Vec2(random.nextDouble(-MAX_OFFSET_MM, MAX_OFFSET_MM), random.nextDouble(-1.0, 1.0))
                val b = Vec2(random.nextDouble(-MAX_OFFSET_MM, MAX_OFFSET_MM), random.nextDouble(-1.0, 1.0))
                val t = random.nextDouble()
                val onLine = Vec2(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                val nudgeX = random.nextInt(-NEAR_COLLINEAR_ULPS, NEAR_COLLINEAR_ULPS + 1) * StrictMath.ulp(onLine.x)
                val nudgeY = random.nextInt(-NEAR_COLLINEAR_ULPS, NEAR_COLLINEAR_ULPS + 1) * StrictMath.ulp(onLine.y)
                Triple(a, b, Vec2(onLine.x + nudgeX, onLine.y + nudgeY))
            }

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
