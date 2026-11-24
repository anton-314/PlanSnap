package app.plansnap.core.geometry.bands

import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

class WallBandPropertyTest {
    private val builder: WallBandBuilder = DefaultWallBandBuilder()
    private val analyzer = DefaultPolygonAnalyzer()

    @Test
    fun `given random room when rotated and translated then the layout moves along`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_RIGID), bandInputs, rigidMotions) { input, motion ->
                val layout = builder.build(input)
                val moved =
                    builder.build(BandInput(motion.apply(input.outline), input.winding, input.thicknessesMm))

                assertEquals(layout.corners.map { it.join }, moved.corners.map { it.join })
                assertEquals(layout.bands.map { it::class }, moved.bands.map { it::class })
                layout.bands.zip(moved.bands).forEach { (original, transformed) ->
                    assertAllClose(original.points().map(motion::apply), transformed.points(), MOTION_TOLERANCE_MM)
                }
                layout.corners.zip(moved.corners).forEach { (original, transformed) ->
                    val expected =
                        original.fill
                            ?.vertices
                            ?.map(motion::apply)
                            .orEmpty()
                    assertAllClose(expected, transformed.fill?.vertices.orEmpty(), MOTION_TOLERANCE_MM)
                }
            }
        }

    @Test
    fun `given random room when thicknesses change then the inner contour and its area stay unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_AREA), bandInputs) { input ->
                val thin = BandInput(input.outline, input.winding, List(input.thicknessesMm.size) { 0L })
                val area = analyzer.areaMm2(input.outline)

                for (layout in listOf(builder.build(input), builder.build(thin))) {
                    val inner = Polygon2(layout.bands.map { it.points()[0] })
                    assertEquals(input.outline, inner)
                    assertEquals(area, analyzer.areaMm2(inner))
                    assertEquals(analyzer.perimeterMm(input.outline), analyzer.perimeterMm(inner))
                }
            }
        }

    @Test
    fun `given random room when built then every band lies between its wall and the outer offset line`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_OUTSIDE), bandInputs) { input ->
                val layout = builder.build(input)
                val vertices = input.outline.vertices
                val n = vertices.size
                layout.bands.forEachIndexed { i, band ->
                    val start = vertices[i]
                    val end = vertices[(i + 1) % n]
                    val thickness = input.thicknessesMm[i].toDouble()
                    val normal = outwardNormal(start, end, input.winding)
                    when (band) {
                        is WallBand.Line -> {
                            assertEquals(0L, input.thicknessesMm[i])
                            assertEquals(WallBand.Line(i, start, end), band)
                        }

                        is WallBand.Filled -> {
                            val shape = band.shape.vertices
                            assertEquals(listOf(start, end), shape.subList(0, 2))
                            shape.subList(2, 4).forEach { outer ->
                                assertEquals(thickness, (outer - start).dot(normal), OFFSET_TOLERANCE_MM)
                            }
                        }
                    }
                }
            }
        }

    @Test
    fun `given random room when built then joins and fills satisfy the corner rules`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_JOINS), bandInputs) { input ->
                val layout = builder.build(input)
                val vertices = input.outline.vertices
                val n = vertices.size
                layout.corners.forEachIndexed { i, corner ->
                    val previous = (i + n - 1) % n
                    val tA = input.thicknessesMm[previous].toDouble()
                    val tB = input.thicknessesMm[i].toDouble()
                    val dA = unit(vertices[i] - vertices[previous])
                    val dB = unit(vertices[(i + 1) % n] - vertices[i])
                    val turn = input.winding.sign * dA.cross(dB)
                    assertEquals(i, corner.cornerIndex)
                    when (corner.join) {
                        CornerJoin.CONCAVE -> {
                            assertTrue(turn < 0.0)
                        }

                        CornerJoin.COLLINEAR -> {
                            assertTrue(abs(turn) < GeometryTolerances.NEAR_COLLINEAR_SIN)
                        }

                        CornerJoin.MITER, CornerJoin.BEVEL -> {
                            assertTrue(turn >= GeometryTolerances.NEAR_COLLINEAR_SIN)
                            val miter = miterDistance(dA, dB, tA, tB, input.winding)
                            val limit = GeometryTolerances.MITER_LIMIT_FACTOR * maxOf(tA, tB)
                            assertEquals(corner.join == CornerJoin.MITER, miter <= limit)
                        }
                    }
                    if (corner.join == CornerJoin.BEVEL) {
                        val fill = requireNotNull(corner.fill).vertices
                        assertEquals(vertices[i], fill[0])
                        val normalA = outwardNormal(vertices[previous], vertices[i], input.winding)
                        val normalB = outwardNormal(vertices[i], vertices[(i + 1) % n], input.winding)
                        assertAllClose(
                            listOf(vertices[i] + normalA * tA, vertices[i] + normalB * tB),
                            fill.subList(1, 3),
                            OFFSET_TOLERANCE_MM,
                        )
                    }
                }
            }
        }

    @Test
    fun `given random room when mirrored with reversed winding then the layout is mirrored`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_MIRROR), bandInputs) { input ->
                val mirrored =
                    BandInput(Polygon2(input.outline.vertices.map(::mirror)), Winding.CLOCKWISE, input.thicknessesMm)
                val layout = builder.build(input)
                val reflected = builder.build(mirrored)

                assertEquals(layout.corners.map { it.join }, reflected.corners.map { it.join })
                layout.bands.zip(reflected.bands).forEach { (original, image) ->
                    assertAllClose(original.points().map(::mirror), image.points(), OFFSET_TOLERANCE_MM)
                }
            }
        }

    private data class RigidMotion(
        val radians: Double,
        val offset: Vec2,
    ) {
        fun apply(point: Vec2): Vec2 = point.rotated(radians) + offset

        fun apply(polygon: Polygon2): Polygon2 = Polygon2(polygon.vertices.map(::apply))
    }

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED_RIGID = 8_301L
        const val SEED_AREA = 8_302L
        const val SEED_OUTSIDE = 8_303L
        const val SEED_JOINS = 8_304L
        const val SEED_MIRROR = 8_305L
        const val MIN_VERTICES = 3
        const val MAX_VERTICES = 24
        const val MIN_RADIUS_MM = 2_000.0
        const val MAX_RADIUS_MM = 10_000.0
        const val ANGLE_JITTER = 0.45
        const val MAX_OFFSET_MM = 1_000_000.0
        const val ZERO_THICKNESS_ONE_IN = 5
        const val MOTION_TOLERANCE_MM = 1e-6
        const val OFFSET_TOLERANCE_MM = 1e-7

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        /**
         * Counter-clockwise star-shaped rooms around the origin, always simple. The jitter produces convex, concave and
         * acute corners. Every fifth thickness is zero, the others are uniform in `1..MAX_THICKNESS_MM`.
         */
        val bandInputs: Arb<BandInput> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(MIN_VERTICES, MAX_VERTICES + 1)
                val sector = 2.0 * PI / n
                val vertices =
                    (0 until n).map { i ->
                        val angle = i * sector + ANGLE_JITTER * sector * (2.0 * random.nextDouble() - 1.0)
                        Vec2(random.nextDouble(MIN_RADIUS_MM, MAX_RADIUS_MM), 0.0).rotated(angle)
                    }
                val thicknesses =
                    List(n) {
                        if (random.nextInt(ZERO_THICKNESS_ONE_IN) == 0) {
                            0L
                        } else {
                            random.nextLong(1L, GeometryTolerances.MAX_THICKNESS_MM + 1)
                        }
                    }
                BandInput(Polygon2(vertices), Winding.COUNTER_CLOCKWISE, thicknesses)
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

        fun mirror(point: Vec2) = Vec2(point.x, -point.y)

        fun unit(vector: Vec2): Vec2 = vector * (1.0 / vector.length())

        fun outwardNormal(
            from: Vec2,
            to: Vec2,
            winding: Winding,
        ): Vec2 {
            val d = unit(to - from)
            return Vec2(winding.sign * d.y, -winding.sign * d.x)
        }

        /**
         * Independent oracle for the miter distance: the offset lines meet at `t_a n_a + s d_a` with
         * `s = (t_b − t_a cos τ) / sin τ`, where `cos τ = d_a · d_b` and `sin τ = s_F · d_a × d_b`.
         */
        fun miterDistance(
            dA: Vec2,
            dB: Vec2,
            tA: Double,
            tB: Double,
            winding: Winding,
        ): Double {
            val sin = winding.sign * dA.cross(dB)
            val along = (tB - tA * dA.dot(dB)) / sin
            return StrictMath.hypot(tA, along)
        }

        fun WallBand.points(): List<Vec2> =
            when (this) {
                is WallBand.Line -> listOf(start, end)
                is WallBand.Filled -> shape.vertices
            }

        fun assertAllClose(
            expected: List<Vec2>,
            actual: List<Vec2>,
            tolerance: Double,
        ) {
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEach { (e, a) ->
                assertTrue(a.isCloseTo(e, tolerance), "expected $e but was $a")
            }
        }
    }
}
