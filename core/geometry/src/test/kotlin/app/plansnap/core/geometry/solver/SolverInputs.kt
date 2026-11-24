package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import kotlin.random.Random

/** Convex counter-clockwise polygon on a rotated ellipse, so that the corners are generally not concyclic. */
fun randomConvexPolygon(
    random: Random,
    vertexCount: Int,
): List<Vec2> {
    val semiMajor = random.nextDouble(MIN_SEMI_AXIS_MM, MAX_SEMI_AXIS_MM)
    val semiMinor = semiMajor * random.nextDouble(MIN_AXIS_RATIO, 1.0)
    val rotation = random.nextDouble(0.0, FULL_TURN)
    val sector = FULL_TURN / vertexCount
    return List(vertexCount) { i ->
        val angle = i * sector + ANGLE_JITTER * sector * (2.0 * random.nextDouble() - 1.0)
        Vec2(semiMajor * StrictMath.cos(angle), semiMinor * StrictMath.sin(angle)).rotated(rotation)
    }
}

/** Raw wall lengths of [vertices], rounded to whole millimeters. */
fun roundedLengths(vertices: List<Vec2>): List<Long> =
    vertices.indices.map { i -> Math.round((vertices[(i + 1) % vertices.size] - vertices[i]).length()) }

/** Measured corner at [i] of [vertices] with its angle rounded to whole millidegrees. */
fun measuredCorner(
    vertices: List<Vec2>,
    i: Int,
): CornerInput.Measured {
    val n = vertices.size
    val incoming = vertices[i] - vertices[(i + n - 1) % n]
    val outgoing = vertices[(i + 1) % n] - vertices[i]
    val turn = StrictMath.toDegrees(StrictMath.atan2(incoming.cross(outgoing), incoming.dot(outgoing)))
    val direction = if (turn >= 0.0) TurnDirection.LEFT else TurnDirection.RIGHT
    val alpha = Math.round((STRAIGHT_DEG - StrictMath.abs(turn)) * MILLI_PER_DEGREE).toInt()
    return CornerInput.Measured(direction, Angle(alpha.coerceIn(1, STRAIGHT_MILLI_DEGREES)))
}

/** Diagonal between corners [a] and [b] of [vertices], rounded to whole millimeters. */
fun measuredDiagonal(
    vertices: List<Vec2>,
    a: Int,
    b: Int,
): DiagonalInput = DiagonalInput(a, b, Math.round((vertices[b] - vertices[a]).length()))

/** Non-adjacent corner pairs `(a, b)` with `a < b` of an `n`-gon in a random order. */
fun shuffledNonAdjacentPairs(
    random: Random,
    n: Int,
): List<Pair<Int, Int>> =
    (0 until n)
        .flatMap { a -> (a + 2 until n).map { b -> a to b } }
        .filterNot { (a, b) -> a == 0 && b == n - 1 }
        .shuffled(random)

/**
 * Determined or underdetermined input of a random convex polygon: free closing corner, at least two inner corners
 * declared right, the others measured, and at most `v − 2` diagonals, so that the rows never outnumber the variables.
 */
fun randomConvexInput(random: Random): ShapeInput {
    val vertices = randomConvexPolygon(random, random.nextInt(MIN_RANDOM_WALLS, MAX_RANDOM_WALLS + 1))
    val n = vertices.size
    val declared = (1 until n).shuffled(random).take(random.nextInt(CLOSURE_ROWS, n)).toSet()
    val corners =
        List(n) { i ->
            when (i) {
                0 -> CornerInput.Free
                in declared -> LEFT_RIGHT_ANGLE
                else -> measuredCorner(vertices, i)
            }
        }
    val free = corners.count { it is CornerInput.DeclaredRight }
    val diagonals =
        shuffledNonAdjacentPairs(random, n)
            .take(maxOf(0, free - CLOSURE_ROWS))
            .map { (a, b) -> measuredDiagonal(vertices, a, b) }
    return withoutDependentDiagonals(vertices, ShapeInput(roundedLengths(vertices), corners, diagonals))
}

/**
 * Drops every diagonal whose row does not raise the rank of the Jacobian at the true shape, as the independent
 * selection of DOMAIN section 5.4 does. A diagonal across a measured corner follows from two walls and the angle.
 */
fun withoutDependentDiagonals(
    vertices: List<Vec2>,
    input: ShapeInput,
): ShapeInput {
    val problem = SolverProblem(input)
    val n = vertices.size
    // The exactly closed true outline in the local frame: V_0 at the origin, w_0 along +x.
    val heading = (vertices[1] - vertices[0]).let { StrictMath.atan2(it.y, it.x) }
    val local = List(n + 1) { (vertices[it % n] - vertices[0]).rotated(-heading) }
    val chain = Chain(DoubleArray(n + 1) { local[it].x }, DoubleArray(n + 1) { local[it].y })
    val jacobian = problem.jacobian(problem.rows, chain)
    val independent = DenseLinearAlgebra.independentRows(jacobian, TRUE_SHAPE_RANK_TOLERANCE)
    val kept =
        independent
            .map { problem.rows[it] }
            .filterIsInstance<HardCondition.DiagonalLength>()
            .map { it.index }
            .sorted()
    return ShapeInput(input.lengthsMm, input.corners, kept.map { input.diagonals[it] })
}

/** Mirror image of [input]: every direction flipped. */
fun mirrored(input: ShapeInput): ShapeInput =
    ShapeInput(
        input.lengthsMm,
        input.corners.map {
            when (it) {
                is CornerInput.DeclaredRight -> CornerInput.DeclaredRight(opposite(it.direction))
                is CornerInput.Measured -> CornerInput.Measured(opposite(it.direction), it.angle)
                CornerInput.Free -> CornerInput.Free
            }
        },
        input.diagonals,
    )

/** Arbitrary valid input, including impossible, self-intersecting and degenerate ones. */
fun randomFuzzInput(random: Random): ShapeInput {
    val n = random.nextInt(3, MAX_FUZZ_WALLS + 1)
    val corners =
        List(n) { i ->
            when (random.nextInt(4)) {
                0 -> {
                    if (i == 0) CornerInput.Free else CornerInput.DeclaredRight(randomDirection(random))
                }

                1 -> {
                    CornerInput.DeclaredRight(randomDirection(random))
                }

                else -> {
                    CornerInput.Measured(
                        randomDirection(random),
                        Angle(random.nextInt(1, STRAIGHT_MILLI_DEGREES + 1)),
                    )
                }
            }
        }
    val diagonals =
        List(random.nextInt(MAX_FUZZ_DIAGONALS + 1)) {
            val a = random.nextInt(n)
            DiagonalInput(a, (a + random.nextInt(1, n)) % n, random.nextLong(1, 2 * GeometryTolerances.MAX_WALL_MM))
        }
    val lengths =
        List(n) {
            if (random.nextBoolean()) {
                random.nextLong(GeometryTolerances.MIN_WALL_MM, GeometryTolerances.MAX_WALL_MM + 1)
            } else {
                random.nextLong(FUZZ_SHORT_MIN_MM, FUZZ_SHORT_MAX_MM)
            }
        }
    return ShapeInput(lengths, corners, diagonals)
}

private fun randomDirection(random: Random): TurnDirection =
    if (random.nextBoolean()) TurnDirection.LEFT else TurnDirection.RIGHT

private fun opposite(direction: TurnDirection): TurnDirection =
    if (direction == TurnDirection.LEFT) TurnDirection.RIGHT else TurnDirection.LEFT

private const val FULL_TURN = 2.0 * StrictMath.PI
private const val TRUE_SHAPE_RANK_TOLERANCE = 1e-7
private const val MIN_SEMI_AXIS_MM = 2_000.0
private const val MAX_SEMI_AXIS_MM = 15_000.0
private const val MIN_AXIS_RATIO = 0.4
private const val ANGLE_JITTER = 0.35
private const val STRAIGHT_DEG = 180.0
private const val STRAIGHT_MILLI_DEGREES = 180_000
private const val MILLI_PER_DEGREE = 1_000.0
private const val MIN_RANDOM_WALLS = 3
private const val MAX_RANDOM_WALLS = 8
private const val CLOSURE_ROWS = 2
private const val MAX_FUZZ_WALLS = 9
private const val MAX_FUZZ_DIAGONALS = 3
private const val FUZZ_SHORT_MIN_MM = 1_000L
private const val FUZZ_SHORT_MAX_MM = 6_000L
