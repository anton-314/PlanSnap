package app.plansnap.core.geometry.determinacy.rig

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput

/**
 * One generated rig case.
 *
 * @property tag class tag of `stress.py`, for example `K1a` or `K4-thin`.
 * @property truth true shape, `null` for cases without a known truth (K2, K3 and the gap cases).
 * @property gapMm gap of a K4 gap case.
 */
internal data class RigCase(
    val tag: String,
    val input: ShapeInput,
    val truth: List<Vec2>?,
    val gapMm: Int? = null,
)

/** Case generators of `stress.py` with the seed rules of the prototype: one generator per case and class. */
internal object RigCases {
    const val SEED = 20_261_124L

    /** K1a (honest) or K1b (shape deviation) case [index]. */
    fun classOne(
        index: Int,
        honest: Boolean,
    ): RigCase {
        val random = PythonRandom(SEED * 2 + index * 2L + if (honest) 1 else 0)
        val truth = RigShapes.truth(random)
        return RigCase(if (honest) "K1a" else "K1b", build(truth.points, random, honest), truth.points)
    }

    /** K2 case [index]: flipped directions or random right-angled chains. */
    fun classTwo(index: Int): RigCase {
        val random = PythonRandom(SEED * 3 + index)
        if (random.random() < FLIP_SHARE) {
            val truth = RigShapes.truth(random, 4, 12)
            val input = build(truth.points, random, honest = false)
            val n = input.wallCount
            val flips = random.sample((1 until n).toList(), minOf(random.randint(1, 2), n - 1))
            val corners = input.corners.mapIndexed { i, corner -> if (i in flips) flipped(corner) else corner }
            return RigCase("K2", ShapeInput(input.lengthsMm, corners, input.diagonals), null)
        }
        val n = random.randint(4, 10)
        val lengths = List(n) { random.choice(CHAIN_LENGTHS) }
        val corners =
            listOf(CornerInput.Free) + List(n - 1) { CornerInput.DeclaredRight(direction(random.choice(SIGNS))) }
        return RigCase("K2", ShapeInput(lengths, corners), null)
    }

    /** K3 case [index]: underdetermined rooms with at least two corners far from 90°. */
    fun classThree(index: Int): RigCase {
        val random = PythonRandom(SEED * 5 + index)
        var points: List<Vec2>
        while (true) {
            points = RigShapes.truth(random, 4, 12).points
            val skewed = RigGeometry.turns(points).count { StrictMath.abs(StrictMath.abs(degrees(it)) - 90.0) > 3.0 }
            if (skewed >= 2) break
        }
        val turns = RigGeometry.turns(points)
        val n = points.size
        val measured = random.sample((1 until n).toList(), random.randint(0, maxOf(0, n - 4))).toSet()
        val corners = listOf(CornerInput.Free) + (1 until n).map { cornerOf(turns[it], random, it !in measured) }
        val lengths = RigGeometry.lengths(points).map { StrictMath.rint(it).toLong() }
        return RigCase("K3", ShapeInput(lengths, corners), null)
    }

    /** K4 case [index]: boundary shapes, every fifth a rectangle with a gap. */
    fun classFour(index: Int): RigCase {
        val random = PythonRandom(SEED * 7 + index)
        if (index % GAP_EVERY == 0) {
            val gap = random.choice(GAPS_MM)
            val corners = listOf(CornerInput.Free) + List(3) { CornerInput.DeclaredRight(TurnDirection.LEFT) }
            return RigCase("K4-gap", ShapeInput(listOf(4000L + gap, 3000L, 4000L, 3000L), corners), null, gap)
        }
        val truth = RigShapes.boundary(random)
        val points = truth.points
        val n = points.size
        val honest = random.random() < HALF
        val turns = RigGeometry.turns(points)
        val lengths = RigGeometry.lengths(points).map { maxOf(10L, StrictMath.rint(it).toLong()) }
        val corners =
            (0 until n).map { i ->
                val declared = isRight(turns[i]) && honest && random.random() < BOUNDARY_DECLARED_SHARE
                if (i == 0 && random.random() < BOUNDARY_FREE_SHARE) {
                    CornerInput.Free
                } else {
                    cornerOf(turns[i], random, declared)
                }
            }
        val pairs = diagonalPairs(n).toMutableList()
        random.shuffle(pairs)
        val diagonals = pairs.take(random.randint(0, 2)).map { (a, b) -> diagonal(points, a, b) }
        return RigCase("K4-${truth.kind}", ShapeInput(lengths, corners, diagonals), points)
    }

    /**
     * Compact text of [input] in the notation of the fixtures: lengths, corners (`F`, `L`/`R` declared, `M`/`m`
     * measured left or right with millidegrees) and diagonals `a-b=mm`, separated by ` | `.
     */
    fun describe(input: ShapeInput): String {
        val corners =
            input.corners.joinToString(" ") {
                when (it) {
                    CornerInput.Free -> {
                        "F"
                    }

                    is CornerInput.DeclaredRight -> {
                        if (it.direction == TurnDirection.LEFT) "L" else "R"
                    }

                    is CornerInput.Measured -> {
                        (if (it.direction == TurnDirection.LEFT) "M" else "m") + it.angle.milliDegrees
                    }
                }
            }
        val diagonals = input.diagonals.joinToString(" ") { "${it.fromCorner}-${it.toCorner}=${it.lengthMm}" }
        return "${input.lengthsMm.joinToString(" ")} | $corners | $diagonals"
    }

    /** `stress.build`: rounded lengths, corner inputs, diagonals and repeated walls. */
    private fun build(
        points: List<Vec2>,
        random: PythonRandom,
        honest: Boolean,
    ): ShapeInput {
        val n = points.size
        val turns = RigGeometry.turns(points)
        val lengths = RigGeometry.lengths(points).map { maxOf(10L, StrictMath.rint(it).toLong()) }
        val corners =
            (0 until n).map { i ->
                val right = isRight(turns[i])
                val declared =
                    if (honest) right && random.random() < HONEST_DECLARED_SHARE else random.random() < DECLARED_SHARE
                if (i == 0) closingCorner(turns[0], random, honest, right) else cornerOf(turns[i], random, declared)
            }
        val pairs = diagonalPairs(n).toMutableList()
        random.shuffle(pairs)
        val diagonals =
            pairs
                .take(random.randint(0, minOf(MAX_DIAGONALS, n)))
                .map { (a, b) ->
                    diagonal(points, a, b)
                }.toMutableList()
        val repeats = if (random.random() < REPEAT_SHARE) random.randint(0, MAX_REPEATS) else 0
        repeat(repeats) {
            val w = random.randrange(n)
            val a = w
            val b = (w + 1) % n
            diagonals += DiagonalInput(minOf(a, b), maxOf(a, b), lengths[w])
        }
        return ShapeInput(lengths, corners, diagonals)
    }

    private fun closingCorner(
        turn: Double,
        random: PythonRandom,
        honest: Boolean,
        right: Boolean,
    ): CornerInput {
        val u = random.random()
        return when {
            u < FREE_SHARE -> CornerInput.Free
            u < MEASURED_LIMIT || (honest && !right) -> cornerOf(turn, random, false)
            else -> cornerOf(turn, random, true)
        }
    }

    /** `stress.corner_of`: a declared right angle or the measured angle in whole millidegrees. */
    private fun cornerOf(
        turn: Double,
        random: PythonRandom,
        declared: Boolean,
    ): CornerInput {
        val deg = degrees(turn)
        val alpha = StrictMath.rint((180.0 - StrictMath.abs(deg)) * 1000.0).toInt()
        var sign = if (deg > 0.0) 1 else -1
        if (StrictMath.abs(deg) < 1e-9) sign = random.choice(SIGNS)
        if (declared) return CornerInput.DeclaredRight(direction(sign))
        return CornerInput.Measured(direction(sign), Angle(alpha.coerceIn(1, 180_000)))
    }

    private fun flipped(corner: CornerInput): CornerInput =
        when (corner) {
            is CornerInput.DeclaredRight -> CornerInput.DeclaredRight(opposite(corner.direction))
            is CornerInput.Measured -> CornerInput.Measured(opposite(corner.direction), corner.angle)
            CornerInput.Free -> corner
        }

    private fun diagonalPairs(n: Int): List<Pair<Int, Int>> =
        (0 until n).flatMap { a -> (a + 2 until n).filterNot { b -> a == 0 && b == n - 1 }.map { b -> a to b } }

    private fun diagonal(
        points: List<Vec2>,
        a: Int,
        b: Int,
    ): DiagonalInput = DiagonalInput(a, b, StrictMath.rint((points[b] - points[a]).length()).toLong())

    private fun isRight(turn: Double): Boolean = StrictMath.abs(StrictMath.abs(degrees(turn)) - 90.0) < 1e-6

    private fun degrees(radians: Double): Double = StrictMath.toDegrees(radians)

    private fun direction(sign: Int): TurnDirection = if (sign > 0) TurnDirection.LEFT else TurnDirection.RIGHT

    private fun opposite(direction: TurnDirection): TurnDirection =
        if (direction == TurnDirection.LEFT) TurnDirection.RIGHT else TurnDirection.LEFT

    private val SIGNS = listOf(1, -1)
    private val CHAIN_LENGTHS = listOf(1000L, 2000L, 3000L, 4000L)
    private val GAPS_MM = listOf(0, 1, 2, 139, 140, 141, 49, 50, 51)
    private const val FLIP_SHARE = 0.6
    private const val HALF = 0.5
    private const val GAP_EVERY = 5
    private const val BOUNDARY_DECLARED_SHARE = 0.7
    private const val BOUNDARY_FREE_SHARE = 0.6
    private const val HONEST_DECLARED_SHARE = 0.8
    private const val DECLARED_SHARE = 0.6
    private const val FREE_SHARE = 0.65
    private const val MEASURED_LIMIT = 0.95
    private const val REPEAT_SHARE = 0.3
    private const val MAX_REPEATS = 2
    private const val MAX_DIAGONALS = 4
}
