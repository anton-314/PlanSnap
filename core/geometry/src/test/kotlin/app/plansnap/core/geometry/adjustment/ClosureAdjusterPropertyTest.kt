package app.plansnap.core.geometry.adjustment

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.adjustOrFail
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.gapMm
import app.plansnap.core.geometry.adjustment.AdjustmentTestSupport.targetStart
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

class ClosureAdjusterPropertyTest {
    @Test
    fun `given noisy room when adjusted then the gap closes and raw values stay untouched`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_CLOSURE), noisyRooms) { room ->
                val lengths = ArrayList(room.input.lengthsMm)
                val corners = ArrayList(room.input.corners)
                val diagonals = ArrayList(room.input.diagonals)
                val start = ArrayList(targetStart(room.input))
                val startCopy = ArrayList(start)
                val shape = adjustOrFail(room.input, room.mode, start)
                assertTrue(gapMm(shape) <= GeometryTolerances.SOLVER_RESIDUAL_MM, "gap ${gapMm(shape)}")
                assertEquals(lengths, room.input.lengthsMm)
                assertEquals(corners, room.input.corners)
                assertEquals(diagonals, room.input.diagonals)
                assertEquals(startCopy.map(Double::toRawBits), start.map(Double::toRawBits))
                assertTrue(shape.redundancies.all { it.redundancy in 0.0..1.0 })
                assertEquals(observationCount(room.input, room.mode), shape.redundancies.size)
            }
        }

    @Test
    fun `given equal arguments when adjusted twice then the outcome is bit-identical`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_DETERMINISM), noisyRooms) { room ->
                val copy = ShapeInput(room.input.lengthsMm, room.input.corners, room.input.diagonals)
                val first = adjustOrFail(room.input, room.mode, targetStart(room.input))
                val second = adjustOrFail(copy, room.mode, targetStart(copy))
                assertEquals(first, second)
                assertEquals(bits(first), bits(second))
            }
        }

    @Test
    fun `given measured room when the start vertex moves then the adjustment moves with it`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_ROTATION), measuredRooms) { room ->
                val n = room.input.wallCount
                val k = room.shift
                val shifted =
                    ShapeInput(
                        room.input.lengthsMm.rotate(k),
                        room.input.corners.rotate(k),
                        room.input.diagonals.map {
                            DiagonalInput(
                                (it.fromCorner - k + n) % n,
                                (it.toCorner - k + n) % n,
                                it.lengthMm,
                            )
                        },
                    )
                val shape = adjustOrFail(room.input, room.mode, targetStart(room.input))
                val moved = adjustOrFail(shifted, room.mode, targetStart(shifted))
                assertSameAdjustment(shape.adjustedLengthsMm.rotate(k), shape.adjustedTurnsRad.rotate(k), shape, moved)
            }
        }

    @Test
    fun `given measured room when mirrored then the adjustment is mirrored`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_MIRROR), measuredRooms) { room ->
                val mirrored =
                    ShapeInput(
                        room.input.lengthsMm,
                        room.input.corners.map(::mirror),
                        room.input.diagonals,
                    )
                val shape = adjustOrFail(room.input, room.mode, targetStart(room.input))
                val image = adjustOrFail(mirrored, room.mode, targetStart(mirrored))
                assertSameAdjustment(shape.adjustedLengthsMm, shape.adjustedTurnsRad.map { -it }, shape, image)
            }
        }

    @Test
    fun `given adjustment sources then trigonometry and roots only use StrictMath`() {
        val sources = File(ADJUSTMENT_SOURCES).listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.size >= MIN_SOURCES, "no sources found in ${File(ADJUSTMENT_SOURCES).absolutePath}")
        sources.forEach { file ->
            val match = FORBIDDEN_MATH.find(file.readText())
            assertTrue(match == null, "non-strict math in ${file.name}: ${match?.value}")
        }
    }

    /** Compares [actual] with [expected] after moving its lengths and turns to [lengths] and [turns]. */
    private fun assertSameAdjustment(
        lengths: List<Double>,
        turns: List<Double>,
        expected: AdjustedShape,
        actual: AdjustedShape,
    ) {
        lengths.indices.forEach { i ->
            assertEquals(lengths[i], actual.adjustedLengthsMm[i], INVARIANCE_MM)
            assertEquals(0.0, wrapAngle(turns[i] - actual.adjustedTurnsRad[i]), INVARIANCE_RAD)
        }
        expected.adjustedDiagonalsMm.indices.forEach { k ->
            assertEquals(expected.adjustedDiagonalsMm[k], actual.adjustedDiagonalsMm[k], INVARIANCE_MM)
        }
        // The iteration stops once the conditions hold, not at the exact optimum (DOMAIN section 6). Both runs stop at
        // slightly different feasible points, and angle corrections count with U / (2π) mm per radian.
        listOf(
            expected.correctionSumMm to actual.correctionSumMm,
            expected.maxCorrectionMm to actual.maxCorrectionMm,
            expected.normalizedSquareSum to actual.normalizedSquareSum,
        ).forEach { (want, got) -> assertEquals(want, got, INVARIANCE_MM + INVARIANCE_RELATIVE * want) }
    }

    /** Room with its mode and a start vertex shift `k ≥ 1`. */
    private data class Room(
        val input: ShapeInput,
        val mode: AdjustmentMode,
        val shift: Int,
    )

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED_CLOSURE = 9_001L
        const val SEED_DETERMINISM = 9_002L
        const val SEED_ROTATION = 9_003L
        const val SEED_MIRROR = 9_004L
        const val MIN_WALLS = 3
        const val MAX_WALLS = 12
        const val MIN_RADIUS_MM = 1_500.0
        const val MAX_RADIUS_MM = 8_000.0
        const val ANGLE_JITTER = 0.3
        const val MAX_DIAGONALS = 2
        const val LENGTH_NOISE_MM = 5.0
        const val ANGLE_NOISE_DEG = 1.0
        const val MILLI_PER_DEGREE = 1_000.0
        const val STRAIGHT_MILLI_DEGREES = 180_000
        const val RECTANGLE_SIDE_MM = 1_000
        const val RECTANGLE_SPAN_MM = 9_000
        const val RECTANGLE_GAP_MM = 60
        const val INVARIANCE_MM = 0.05
        const val INVARIANCE_RAD = 1e-4
        const val INVARIANCE_RELATIVE = 0.01
        const val MIN_SOURCES = 4
        const val ADJUSTMENT_SOURCES = "src/main/kotlin/app/plansnap/core/geometry/adjustment"

        /** Non-strict trigonometry, roots or powers from Kotlin or `java.lang.Math`. */
        val FORBIDDEN_MATH =
            Regex(
                """kotlin\.math\.(\*|sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|ln|log|pow|cbrt)\b|""" +
                    """(?<!Strict)\bMath\.(sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|log|pow|cbrt)\(""",
            )

        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        fun <T> List<T>.rotate(shift: Int): List<T> = List(size) { this[(it + shift) % size] }

        fun bits(shape: AdjustedShape): List<Long> =
            (
                shape.adjustedLengthsMm + shape.adjustedTurnsRad + shape.adjustedDiagonalsMm +
                    listOf(shape.maxCorrectionMm, shape.correctionSumMm, shape.normalizedSquareSum) +
                    shape.redundancies.map { it.redundancy }
            ).map(Double::toRawBits)

        fun observationCount(
            input: ShapeInput,
            mode: AdjustmentMode,
        ): Int =
            input.wallCount + input.diagonals.size +
                input.corners.count {
                    it is CornerInput.Measured ||
                        (it is CornerInput.DeclaredRight && mode == AdjustmentMode.MEASUREMENT_ERROR)
                }

        fun mirror(corner: CornerInput): CornerInput =
            when (corner) {
                is CornerInput.DeclaredRight -> CornerInput.DeclaredRight(opposite(corner.direction))
                is CornerInput.Measured -> CornerInput.Measured(opposite(corner.direction), corner.angle)
                CornerInput.Free -> CornerInput.Free
            }

        fun opposite(direction: TurnDirection): TurnDirection =
            if (direction == TurnDirection.LEFT) TurnDirection.RIGHT else TurnDirection.LEFT

        fun gaussian(random: Random): Double {
            val u = 1.0 - random.nextDouble()
            return StrictMath.sqrt(-2.0 * StrictMath.log(u)) * StrictMath.cos(2.0 * StrictMath.PI * random.nextDouble())
        }

        fun mode(random: Random): AdjustmentMode =
            if (random.nextBoolean()) AdjustmentMode.MEASUREMENT_ERROR else AdjustmentMode.SHAPE_DEVIATION

        /** Star-shaped polygon in either orientation. */
        fun polygon(random: Random): List<Vec2> {
            val n = random.nextInt(MIN_WALLS, MAX_WALLS + 1)
            val sector = 2.0 * StrictMath.PI / n
            val points =
                List(n) { i ->
                    val angle = i * sector + ANGLE_JITTER * sector * (2.0 * random.nextDouble() - 1.0)
                    Vec2(random.nextDouble(MIN_RADIUS_MM, MAX_RADIUS_MM), 0.0).rotated(angle)
                }
            return if (random.nextBoolean()) points else points.reversed()
        }

        /** Every corner measured with rounding and normal noise, plus up to two noisy diagonals. */
        fun measuredInput(
            random: Random,
            points: List<Vec2>,
        ): ShapeInput {
            val n = points.size
            val edges = List(n) { i -> points[(i + 1) % n] - points[i] }
            val lengths = edges.map { Math.round(it.length() + LENGTH_NOISE_MM * gaussian(random)) }
            val corners =
                List(n) { i ->
                    val from = edges[(i + n - 1) % n]
                    val turnDeg = StrictMath.toDegrees(StrictMath.atan2(from.cross(edges[i]), from.dot(edges[i])))
                    val direction = if (turnDeg >= 0.0) TurnDirection.LEFT else TurnDirection.RIGHT
                    val alpha =
                        Math.round(
                            (180.0 - StrictMath.abs(turnDeg) + ANGLE_NOISE_DEG * gaussian(random)) * MILLI_PER_DEGREE,
                        )
                    CornerInput.Measured(direction, Angle(alpha.toInt().coerceIn(1, STRAIGHT_MILLI_DEGREES)))
                }
            val pairs =
                (0 until n).flatMap { a ->
                    (a + 2 until n).filter { b -> !(a == 0 && b == n - 1) }.map { b ->
                        a to
                            b
                    }
                }
            val diagonals =
                pairs.shuffled(random).take(random.nextInt(0, MAX_DIAGONALS + 1)).map { (a, b) ->
                    val mm = Math.round((points[b] - points[a]).length() + LENGTH_NOISE_MM * gaussian(random))
                    DiagonalInput(a, b, mm)
                }
            return ShapeInput(lengths, corners, diagonals)
        }

        val measuredRooms: Arb<Room> =
            arbitrary { rs ->
                val random = rs.random
                val input = measuredInput(random, polygon(random))
                Room(input, mode(random), random.nextInt(1, input.wallCount))
            }

        /** Measured polygons, and rectangles with declared corners and a gap of up to 60 mm. */
        val noisyRooms: Arb<Room> =
            arbitrary { rs ->
                val random = rs.random
                val input =
                    if (random.nextBoolean()) {
                        measuredInput(random, polygon(random))
                    } else {
                        val width = random.nextLong(RECTANGLE_SIDE_MM.toLong(), RECTANGLE_SPAN_MM.toLong())
                        val height = random.nextLong(RECTANGLE_SIDE_MM.toLong(), RECTANGLE_SPAN_MM.toLong())
                        val gap = random.nextLong(-RECTANGLE_GAP_MM.toLong(), RECTANGLE_GAP_MM + 1L)
                        val direction = if (random.nextBoolean()) TurnDirection.LEFT else TurnDirection.RIGHT
                        ShapeInput(
                            listOf(width + gap, height, width, height),
                            listOf(CornerInput.Free) + List(3) { CornerInput.DeclaredRight(direction) },
                        )
                    }
                Room(input, mode(random), 1)
            }
    }
}
