package app.plansnap.core.geometry.traverse

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
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

class TraversePropertyTest {
    private val traverse = DefaultTraverse()

    @Test
    fun `given closed turn sum when the chain starts at another corner then the gap is unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_WALK_SHIFT), radianChains) { chain ->
                val points = traverse.walk(chain.lengths, chain.turns)
                val gap = (points.last() - points.first()).length()
                val n = chain.lengths.size
                val k = chain.shift
                val shifted = traverse.walk(chain.lengths.rotate(k), chain.turns.rotate(k))
                val shiftedGap = (shifted.last() - shifted.first()).length()
                assertEquals(gap, shiftedGap, chain.perimeter * RELATIVE_TOLERANCE)
                // The shifted chain is the original one moved rigidly, V_k to the origin and w_k along +x.
                val heading = chain.turns.subList(1, k + 1).sum()
                for (j in 0..n - k) {
                    val expected = (points[k + j] - points[k]).rotated(-heading)
                    assertTrue(expected.isCloseTo(shifted[j], chain.perimeter * RELATIVE_TOLERANCE))
                }
            }
        }

    @Test
    fun `given closed polygon when its lengths and turns are walked then the gap vanishes`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_POLYGON), closedPolygons) { vertices ->
                val n = vertices.size
                val edges = List(n) { i -> vertices[(i + 1) % n] - vertices[i] }
                val lengths = edges.map { it.length() }
                val turns = List(n) { i -> turnBetween(edges[(i + n - 1) % n], edges[i]) }
                val points = traverse.walk(lengths, turns)
                assertTrue(points.last().length() <= lengths.sum() * RELATIVE_TOLERANCE, "gap ${points.last()}")
                // Rotating by the heading of w_0 and translating by V_0 maps the walk onto the polygon.
                val heading = StrictMath.atan2(edges[0].y, edges[0].x)
                for (i in 0 until n) {
                    val placed = points[i].rotated(heading) + vertices[0]
                    assertTrue(placed.isCloseTo(vertices[i], lengths.sum() * RELATIVE_TOLERANCE))
                }
            }
        }

    @Test
    fun `given right angled rectangle or L shape when closure computed then gap is exactly zero`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_RIGHT_ANGLED), rightAngledInputs) { input ->
                val error = traverse.closureError(input)
                assertEquals(0.0.toRawBits(), error.gapMm.toRawBits())
                assertEquals(ClosureClass.EXACT, traverse.classify(input, error, ClosureTolerance()))
            }
        }

    @Test
    fun `given regular polygon with integer angles when closure computed then it closes within rounding`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_REGULAR), regularPolygons) { input ->
                val error = traverse.closureError(input)
                val perimeter = input.lengthsMm.sum().toDouble()
                assertTrue(error.gapMm <= perimeter * RELATIVE_TOLERANCE, "gap ${error.gapMm}")
                assertEquals(0.0, error.closingAngleResidualMm, 0.0)
                assertEquals(ClosureClass.EXACT, traverse.classify(input, error, ClosureTolerance()))
            }
        }

    @Test
    fun `given fixed closing corner when the input starts at another corner then the closure error is unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_INPUT_SHIFT), closedTurnInputs) { (input, shift) ->
                val error = traverse.closureError(input)
                val shifted =
                    ShapeInput(input.lengthsMm.rotate(shift), input.corners.rotate(shift), input.diagonals)
                val shiftedError = traverse.closureError(shifted)
                val tolerance = input.lengthsMm.sum() * RELATIVE_TOLERANCE
                assertEquals(error.gapMm, shiftedError.gapMm, tolerance)
                assertEquals(0.0, error.closingAngleResidualMm, 0.0)
                assertEquals(0.0, shiftedError.closingAngleResidualMm, 0.0)
            }
        }

    @Test
    fun `given any input when mirrored then the closure error is unchanged`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_MIRROR), anyInputs) { input ->
                val mirrored = ShapeInput(input.lengthsMm, input.corners.map(::mirror), input.diagonals)
                val error = traverse.closureError(input)
                val mirroredError = traverse.closureError(mirrored)
                val tolerance = input.lengthsMm.sum() * RELATIVE_TOLERANCE
                assertEquals(error.gapMm, mirroredError.gapMm, tolerance)
                assertEquals(error.maxDiagonalResidualMm, mirroredError.maxDiagonalResidualMm, tolerance)
                assertEquals(error.closingAngleResidualMm, mirroredError.closingAngleResidualMm, tolerance)
            }
        }

    @Test
    fun `given same input when evaluated twice then results are bit-identical`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_DETERMINISM), anyInputs) { input ->
                val copy = ShapeInput(ArrayList(input.lengthsMm), ArrayList(input.corners), ArrayList(input.diagonals))
                val first = DefaultTraverse().closureError(input)
                val second = DefaultTraverse().closureError(copy)
                assertEquals(first.gapMm.toRawBits(), second.gapMm.toRawBits())
                assertEquals(first.maxDiagonalResidualMm.toRawBits(), second.maxDiagonalResidualMm.toRawBits())
                assertEquals(first.closingAngleResidualMm.toRawBits(), second.closingAngleResidualMm.toRawBits())
                assertEquals(traverse.targets(input), traverse.targets(copy))
                val turns = traverse.targets(input).targetTurns.map { it.toRadians() }
                val lengths = input.lengthsMm.map { it.toDouble() }
                val walked = traverse.walk(lengths, turns).flatMap { listOf(it.x.toRawBits(), it.y.toRawBits()) }
                val again = traverse.walk(lengths, turns).flatMap { listOf(it.x.toRawBits(), it.y.toRawBits()) }
                assertEquals(walked, again)
            }
        }

    @Test
    fun `given traverse sources then trigonometry and roots only use StrictMath`() {
        val sources = File(TRAVERSE_SOURCES).listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.isNotEmpty(), "no sources found in ${File(TRAVERSE_SOURCES).absolutePath}")
        sources.forEach { file ->
            val text = file.readText()
            assertTrue(
                FORBIDDEN_MATH.find(text) == null,
                "non-strict math in ${file.name}: ${FORBIDDEN_MATH.find(text)?.value}",
            )
        }
    }

    private data class RadianChain(
        val lengths: List<Double>,
        val turns: List<Double>,
        val shift: Int,
    ) {
        val perimeter: Double get() = lengths.sum()
    }

    private companion object {
        const val ITERATIONS = 500
        const val SEED_WALK_SHIFT = 8_001L
        const val SEED_POLYGON = 8_002L
        const val SEED_RIGHT_ANGLED = 8_003L
        const val SEED_REGULAR = 8_004L
        const val SEED_INPUT_SHIFT = 8_005L
        const val SEED_MIRROR = 8_006L
        const val SEED_DETERMINISM = 8_007L
        const val MIN_WALLS = 3
        const val MAX_RANDOM_WALLS = 24
        const val MAX_TURN_MILLI_DEGREES = 179_999
        const val FULL_MILLI_DEGREES = 360_000
        const val RELATIVE_TOLERANCE = 1e-9
        const val MIN_RADIUS_MM = 2_000.0
        const val MAX_RADIUS_MM = 40_000.0
        const val ANGLE_JITTER = 0.3
        const val MAX_DIAGONALS = 3
        const val TRAVERSE_SOURCES = "src/main/kotlin/app/plansnap/core/geometry/traverse"

        /** Non-strict trigonometry, roots or powers from Kotlin or `java.lang.Math`. */
        val FORBIDDEN_MATH =
            Regex(
                """kotlin\.math\.(\*|sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|ln|log|pow|cbrt)\b|""" +
                    """(?<!Strict)\bMath\.(sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|log|pow|cbrt)\(""",
            )

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        fun <T> List<T>.rotate(shift: Int): List<T> = List(size) { this[(it + shift) % size] }

        fun turnBetween(
            from: Vec2,
            to: Vec2,
        ): Double = StrictMath.atan2(from.cross(to), from.dot(to))

        fun mirror(corner: CornerInput): CornerInput =
            when (corner) {
                is CornerInput.DeclaredRight -> CornerInput.DeclaredRight(opposite(corner.direction))
                is CornerInput.Measured -> CornerInput.Measured(opposite(corner.direction), corner.angle)
                CornerInput.Free -> CornerInput.Free
            }

        fun opposite(direction: TurnDirection): TurnDirection =
            if (direction == TurnDirection.LEFT) TurnDirection.RIGHT else TurnDirection.LEFT

        fun randomLength(random: Random): Long =
            random.nextLong(GeometryTolerances.MIN_WALL_MM, GeometryTolerances.MAX_WALL_MM + 1)

        fun randomTurn(random: Random): Int = random.nextInt(-MAX_TURN_MILLI_DEGREES, MAX_TURN_MILLI_DEGREES + 1)

        /** Chains with arbitrary turns whose total is exactly `2π` up to rounding, plus a start corner `k ≥ 1`. */
        val radianChains: Arb<RadianChain> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(MIN_WALLS, MAX_RANDOM_WALLS + 1)
                val lengths = List(n) { randomLength(random).toDouble() }
                val inner = List(n - 1) { random.nextDouble(-StrictMath.PI, StrictMath.PI) }
                RadianChain(lengths, listOf(2.0 * StrictMath.PI - inner.sum()) + inner, random.nextInt(1, n))
            }

        /** Star-shaped, counter-clockwise polygons around a random center. */
        val closedPolygons: Arb<List<Vec2>> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(MIN_WALLS, MAX_RANDOM_WALLS + 1)
                val sector = 2.0 * StrictMath.PI / n
                val center =
                    Vec2(
                        random.nextDouble(-MAX_RADIUS_MM, MAX_RADIUS_MM),
                        random.nextDouble(-MAX_RADIUS_MM, MAX_RADIUS_MM),
                    )
                List(n) { i ->
                    val angle = i * sector + ANGLE_JITTER * sector * (2.0 * random.nextDouble() - 1.0)
                    center + Vec2(random.nextDouble(MIN_RADIUS_MM, MAX_RADIUS_MM), 0.0).rotated(angle)
                }
            }

        /** Rectangles and L shapes with whole-millimeter walls in either direction. */
        val rightAngledInputs: Arb<ShapeInput> =
            arbitrary { rs ->
                val random = rs.random
                val direction = if (random.nextBoolean()) TurnDirection.LEFT else TurnDirection.RIGHT
                val width = random.nextLong(2 * GeometryTolerances.MIN_WALL_MM, GeometryTolerances.MAX_WALL_MM + 1)
                val height = random.nextLong(2 * GeometryTolerances.MIN_WALL_MM, GeometryTolerances.MAX_WALL_MM + 1)
                if (random.nextBoolean()) {
                    rightAngled(width, height, width, height, direction = direction)
                } else {
                    val cutX =
                        random.nextLong(
                            GeometryTolerances.MIN_WALL_MM,
                            width - GeometryTolerances.MIN_WALL_MM + 1,
                        )
                    val cutY =
                        random.nextLong(
                            GeometryTolerances.MIN_WALL_MM,
                            height - GeometryTolerances.MIN_WALL_MM + 1,
                        )
                    val same = CornerInput.DeclaredRight(direction)
                    val reflex = CornerInput.DeclaredRight(opposite(direction))
                    ShapeInput(
                        lengthsMm = listOf(width, height - cutY, cutX, cutY, width - cutX, height),
                        corners = listOf(CornerInput.Free, same, same, reflex, same, same),
                    )
                }
            }

        /** Regular polygons whose exterior angle is a whole number of millidegrees, closing corner fixed or free. */
        val regularPolygons: Arb<ShapeInput> =
            arbitrary { rs ->
                val random = rs.random
                val sides = (MIN_WALLS..GeometryTolerances.MAX_WALLS_PER_ROOM).filter { FULL_MILLI_DEGREES % it == 0 }
                val n = sides[random.nextInt(sides.size)]
                val sign = if (random.nextBoolean()) 1 else -1
                val corner = turnCorner(sign * FULL_MILLI_DEGREES / n)
                val closing = if (random.nextBoolean()) corner else CornerInput.Free
                val side = randomLength(random)
                ShapeInput(
                    List(n) { side },
                    listOf(closing) + List(n - 1) { corner },
                )
            }

        /** Random walls, turns that sum to exactly `360° · s` including a fixed `V_0`, and a start corner `k ≥ 1`. */
        val closedTurnInputs: Arb<Pair<ShapeInput, Int>> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(MIN_WALLS, MAX_RANDOM_WALLS + 1)
                var inner: List<Int>
                var closing: Int
                do {
                    inner = List(n - 1) { randomTurn(random) }
                    val sign = if (inner.sum() >= 0) 1 else -1
                    closing = sign * FULL_MILLI_DEGREES - inner.sum()
                } while (closing !in -MAX_TURN_MILLI_DEGREES..MAX_TURN_MILLI_DEGREES)
                val input = ShapeInput(List(n) { randomLength(random) }, (listOf(closing) + inner).map(::turnCorner))
                input to random.nextInt(1, n)
            }

        /** Arbitrary valid inputs with random turns, closing corner and diagonals. */
        val anyInputs: Arb<ShapeInput> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(MIN_WALLS, MAX_RANDOM_WALLS + 1)
                val closing = if (random.nextBoolean()) CornerInput.Free else turnCorner(randomTurn(random))
                val diagonals =
                    List(random.nextInt(MAX_DIAGONALS + 1)) {
                        val from = random.nextInt(n)
                        DiagonalInput(from, (from + random.nextInt(1, n)) % n, randomLength(random))
                    }
                ShapeInput(
                    List(n) { randomLength(random) },
                    listOf(closing) + List(n - 1) { turnCorner(randomTurn(random)) },
                    diagonals,
                )
            }
    }
}
