package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

/** Every row of DOMAIN section 5.4 of [input], the selection of a room that is not overdetermined. */
fun allRows(input: ShapeInput): List<HardCondition> = SolverProblem(input).rows

/** Solves [input] on all rows and returns the solved outcome, failing the test otherwise. */
fun solveAll(
    input: ShapeInput,
    solver: ShapeSolver = DefaultShapeSolver(),
): SolverOutcome.Solved = solved(solver.solve(input, allRows(input), CancellationSignal.NONE))

/** Casts [outcome] to [SolverOutcome.Solved]. */
fun solved(outcome: SolverOutcome): SolverOutcome.Solved =
    outcome as? SolverOutcome.Solved ?: fail("Expected a solved outcome, got $outcome")

/** Absolute shoelace area in mm². */
fun areaMm2(outline: Polygon2): Double = DefaultPolygonAnalyzer().areaMm2(outline)

/** Interior angles in degrees, `180° − s_F · wrap(τ)` with the orientation of the outline (DOMAIN section 3). */
fun interiorAnglesDeg(outline: Polygon2): List<Double> {
    val vertices = outline.vertices
    val n = vertices.size
    val orientation = DefaultPolygonAnalyzer().winding(outline)?.sign ?: 1
    return List(n) { i ->
        val incoming = vertices[i] - vertices[(i + n - 1) % n]
        val outgoing = vertices[(i + 1) % n] - vertices[i]
        val turn = StrictMath.atan2(incoming.cross(outgoing), incoming.dot(outgoing))
        STRAIGHT_DEG - orientation * StrictMath.toDegrees(turn)
    }
}

/** Shape distance of DOMAIN section 5.6: largest vertex distance after centroid shift and optimal rotation. */
fun shapeDistanceMm(
    p: List<Vec2>,
    q: List<Vec2>,
): Double {
    val a = centered(p)
    val b = centered(q)
    val dot = a.indices.sumOf { a[it].dot(b[it]) }
    val cross = a.indices.sumOf { a[it].cross(b[it]) }
    val theta = StrictMath.atan2(cross, dot)
    return a.indices.maxOf { (a[it].rotated(theta) - b[it]).length() }
}

private fun centered(points: List<Vec2>): List<Vec2> {
    val cx = points.sumOf { it.x } / points.size
    val cy = points.sumOf { it.y } / points.size
    return points.map { Vec2(it.x - cx, it.y - cy) }
}

/** Wraps degrees to `(−180°, 180°]`. */
fun wrapDeg(degrees: Double): Double = StrictMath.toDegrees(SolverProblem.wrap(StrictMath.toRadians(degrees)))

/** Asserts that [actual] matches [expected] in degrees within [toleranceDeg], compared after wrapping. */
fun assertTurnsDeg(
    expected: List<Double>,
    actual: List<Double>,
    toleranceDeg: Double,
) {
    assertTrue(expected.size == actual.size, "size $expected vs $actual")
    expected.indices.forEach { i ->
        val deviation = wrapDeg(StrictMath.toDegrees(actual[i]) - expected[i])
        assertTrue(StrictMath.abs(deviation) <= toleranceDeg, "turn $i: expected ${expected[i]}, got $actual")
    }
}

/** Whether [actual] matches [expected] in degrees within [toleranceDeg], compared after wrapping. */
fun turnsMatchDeg(
    expected: List<Double>,
    actual: List<Double>,
    toleranceDeg: Double,
): Boolean =
    expected.indices.all { i ->
        StrictMath.abs(wrapDeg(StrictMath.toDegrees(actual[i]) - expected[i])) <= toleranceDeg
    }

/** Declared right angle to the left. */
val LEFT_RIGHT_ANGLE: CornerInput = CornerInput.DeclaredRight(TurnDirection.LEFT)

/** Measured corner with interior angle [milliDegrees] turning in [direction]. */
fun measured(
    milliDegrees: Int,
    direction: TurnDirection = TurnDirection.LEFT,
): CornerInput = CornerInput.Measured(direction, Angle(milliDegrees))

/** Quadrilateral with a free closing corner, declared left right angles and the given [diagonals]. */
fun declaredQuad(
    vararg lengthsMm: Long,
    diagonals: List<DiagonalInput> = emptyList(),
): ShapeInput =
    ShapeInput(
        lengthsMm = lengthsMm.toList(),
        corners = listOf(CornerInput.Free) + List(lengthsMm.size - 1) { LEFT_RIGHT_ANGLE },
        diagonals = diagonals,
    )

/** Raw bits of every number in [outcome], for bit-identity checks. */
fun fingerprint(outcome: SolverOutcome): List<Any> =
    when (outcome) {
        SolverOutcome.NoSimpleShape -> listOf("none")
        is SolverOutcome.Solved -> (listOf(outcome.solution) + outcome.alternatives).flatMap(::bits)
    }

private fun bits(solution: ShapeSolution): List<Any> =
    solution.turnsRad.map { it.toRawBits() } +
        solution.outline.vertices.flatMap { listOf(it.x.toRawBits(), it.y.toRawBits()) } +
        listOf(solution.cost.toRawBits(), solution.start, solution.honorsDirections)

private const val STRAIGHT_DEG = 180.0
