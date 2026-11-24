package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.testing.fixtures.rooms.ExpectedAmbiguity
import app.plansnap.testing.fixtures.rooms.OverdeterminedCase
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultShapeSolverTest {
    private val solver = DefaultShapeSolver()

    @Test
    fun `given trapezoid 3 3 3 5 when solved then base angles are acos of one third and area is 8 sqrt 2`() {
        val reference = ReferenceRooms.TRAPEZOID_3_3_3_5
        val outcome = solveAll(reference.room.toShapeInput())
        val outline = outcome.solution.outline
        assertEquals(EIGHT_SQRT_TWO_M2, areaMm2(outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
        assertEquals(11.314, areaMm2(outline) / MM2_PER_M2, ROUNDED_AREA_TOLERANCE_M2)
        val angles = interiorAnglesDeg(outline)
        listOf(BASE_ANGLE_DEG, BASE_ANGLE_DEG, TOP_ANGLE_DEG, TOP_ANGLE_DEG).forEachIndexed { i, expected ->
            assertEquals(expected, angles[i], ANGLE_TOLERANCE_DEG, "angle at V$i")
        }
        assertEquals(70.53, angles[0], ROUNDED_ANGLE_TOLERANCE_DEG)
        assertEquals(109.47, angles[2], ROUNDED_ANGLE_TOLERANCE_DEG)
        assertVertices(reference.vertices, outcome.solution.outline.vertices, VERTEX_TOLERANCE_MM)
        assertTrue(outcome.solution.honorsDirections)
        assertSame(SolverStart.TARGET_TURNS, outcome.solution.start)
        // The objective: four corners deviate by 90° − acos(1/3) = 19.47° each from their targets.
        val deviation = StrictMath.toRadians(TOP_ANGLE_DEG - RIGHT_DEG)
        assertEquals(4.0 * deviation * deviation, outcome.solution.cost, COST_TOLERANCE)
    }

    @Test
    fun `given trapezoid with 4 m diagonal when solved then area is 6 plus 2 sqrt 5`() {
        val input = declaredQuad(5000, 3000, 3000, 3000, diagonals = listOf(DiagonalInput(0, 2, 4000)))
        val outcome = solveAll(input)
        // Triangle V0 V1 V2 is 5-4-3 with V2 = (3.2, 2.4) m, triangle V0 V2 V3 is isosceles 4-3-3 with height √5 m.
        val sqrt5 = StrictMath.sqrt(5.0)
        val expected =
            listOf(
                Vec2(0.0, 0.0),
                Vec2(5000.0, 0.0),
                Vec2(3200.0, 2400.0),
                Vec2(1600.0 - 600.0 * sqrt5, 1200.0 + 800.0 * sqrt5),
            )
        assertVertices(expected, outcome.solution.outline.vertices, VERTEX_TOLERANCE_MM)
        assertEquals(6.0 + 2.0 * sqrt5, areaMm2(outcome.solution.outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
        assertEquals(10.472, areaMm2(outcome.solution.outline) / MM2_PER_M2, ROUNDED_AREA_TOLERANCE_M2)
        assertTrue(outcome.solution.honorsDirections)
    }

    @Test
    fun `given trapezoid with measured diagonal 4899 mm when solved then the 8 sqrt 2 trapezoid is kept`() {
        val reference = ReferenceRooms.TRAPEZOID_WITH_DIAGONAL
        val outcome = solveAll(reference.room.toShapeInput())
        assertEquals(11.314, areaMm2(outcome.solution.outline) / MM2_PER_M2, ROUNDED_AREA_TOLERANCE_M2)
        // The rounded diagonal differs from √24 m by 0.02 mm, so the vertices move by less than a millimeter.
        assertVertices(reference.vertices, outcome.solution.outline.vertices, ROUNDED_INPUT_TOLERANCE_MM)
        val diagonal =
            outcome.solution.outline.vertices[2]
                .length()
        assertEquals(4899.0, diagonal, GeometryTolerances.SOLVER_RESIDUAL_MM)
    }

    @Test
    fun `given reference rooms when solved on all rows then the analytic shape and area result`() {
        ReferenceRooms.ALL.filter { it.inputDeterminesShape }.forEach { reference ->
            val outcome = solveAll(reference.room.toShapeInput())
            val outline = outcome.solution.outline
            // The local frame puts V_0 at the origin. Oblique corners carry the rounding of the raw angles.
            val local = reference.vertices.map { it - reference.vertices[0] }
            assertVertices(local, outline.vertices, ROUNDED_INPUT_TOLERANCE_MM, reference.key)
            assertEquals(
                reference.expectedAreaMm2,
                areaMm2(outline),
                reference.expectedAreaMm2 * RELATIVE_AREA_TOLERANCE,
                reference.key,
            )
            interiorAnglesDeg(outline).forEachIndexed { i, angle ->
                assertEquals(reference.expectedInteriorAnglesDeg[i], angle, ROUNDED_ANGLE_TOLERANCE_DEG, reference.key)
            }
            assertTrue(outcome.solution.honorsDirections, reference.key)
            assertTrue(DefaultPolygonAnalyzer().isSimple(outline), reference.key)
        }
    }

    @Test
    fun `given exactly closing right angled rooms when solved then the targets are kept at zero cost`() {
        listOf(ReferenceRooms.RECTANGLE, ReferenceRooms.L_SHAPE, ReferenceRooms.CROSS, ReferenceRooms.HUGE).forEach {
            val outcome = solveAll(it.room.toShapeInput())
            assertTrue(outcome.solution.cost <= ZERO_COST, "${it.key}: ${outcome.solution.cost}")
            assertSame(SolverStart.TARGET_TURNS, outcome.solution.start, it.key)
            assertTurnsDeg(it.expectedInteriorAnglesDeg.map { angle -> 180.0 - angle }, outcome.solution.turnsRad, 1e-9)
        }
    }

    @Test
    fun `given right triangle 3 4 5 when solved then the right angle lies opposite the hypotenuse`() {
        val input = ShapeInput(listOf(3000, 4000, 5000), listOf(CornerInput.Free, LEFT_RIGHT_ANGLE, LEFT_RIGHT_ANGLE))
        val outline = solveAll(input).solution.outline
        val angles = interiorAnglesDeg(outline)
        assertEquals(RIGHT_DEG, angles[1], ANGLE_TOLERANCE_DEG)
        assertEquals(StrictMath.toDegrees(StrictMath.asin(0.6)), angles[2], ANGLE_TOLERANCE_DEG)
        assertEquals(StrictMath.toDegrees(StrictMath.asin(0.8)), angles[0], ANGLE_TOLERANCE_DEG)
        assertEquals(6.0, areaMm2(outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
    }

    @Test
    fun `given equilateral triangle when solved then every angle is 60 degrees`() {
        val input = ShapeInput(listOf(3000, 3000, 3000), listOf(CornerInput.Free, LEFT_RIGHT_ANGLE, LEFT_RIGHT_ANGLE))
        val outline = solveAll(input).solution.outline
        interiorAnglesDeg(outline).forEach { assertEquals(60.0, it, ANGLE_TOLERANCE_DEG) }
        assertEquals(StrictMath.sqrt(3.0) / 4.0 * 9.0, areaMm2(outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
    }

    @Test
    fun `given rectangle with matching diagonal when solved then the rectangle is confirmed exactly`() {
        val input = declaredQuad(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(0, 2, 5000)))
        val outcome = solveAll(input)
        assertVertices(ReferenceRooms.RECTANGLE.vertices, outcome.solution.outline.vertices, 1e-6)
        assertTrue(outcome.solution.cost <= ZERO_COST)
        assertEquals(12.0, areaMm2(outcome.solution.outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
    }

    @Test
    fun `given rectangle lengths with short diagonal when solved then a parallelogram by Heron results`() {
        val input = declaredQuad(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(2, 0, 4800)))
        val outline = solveAll(input).solution.outline
        // Both halves are the triangle 4-3-4.8 m, so the area is twice its Heron area.
        val s = (4.0 + 3.0 + 4.8) / 2.0
        val expectedArea = 2.0 * StrictMath.sqrt(s * (s - 4.0) * (s - 3.0) * (s - 4.8))
        assertEquals(expectedArea, areaMm2(outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
        val angleAtV1 = StrictMath.toDegrees(StrictMath.acos((16.0 + 9.0 - 4.8 * 4.8) / 24.0))
        assertEquals(angleAtV1, interiorAnglesDeg(outline)[1], ANGLE_TOLERANCE_DEG)
        assertEquals(4800.0, outline.vertices[2].length(), GeometryTolerances.SOLVER_RESIDUAL_MM)
    }

    @Test
    fun `given square of 128 walls with straight corners when solved then the 32 m square results`() {
        val n = GeometryTolerances.MAX_WALLS_PER_ROOM
        val corners =
            List(n) { i ->
                when {
                    i == 0 -> {
                        CornerInput.Free
                    }

                    i % SIDE_WALLS == 0 -> {
                        LEFT_RIGHT_ANGLE
                    }

                    else -> {
                        measured(
                            STRAIGHT_MILLI_DEGREES,
                            if (i % 2 ==
                                0
                            ) {
                                TurnDirection.LEFT
                            } else {
                                TurnDirection.RIGHT
                            },
                        )
                    }
                }
            }
        val input = ShapeInput(List(n) { 1000L }, corners)
        val outcome = solveAll(input)
        assertEquals(1024.0, areaMm2(outcome.solution.outline) / MM2_PER_M2, AREA_TOLERANCE_M2)
        assertTrue(outcome.solution.honorsDirections)
        assertTrue(outcome.solution.cost <= ZERO_COST)
    }

    @Test
    fun `given ambiguous room that selects the other shape then the measured room is an alternative`() {
        assertAmbiguity(OverdeterminedCases.AMBIGUOUS_SELECTED_OTHER_SHAPE)
    }

    @Test
    fun `given ambiguous room that selects the true shape then the second shape is an alternative`() {
        assertAmbiguity(OverdeterminedCases.AMBIGUOUS_SELECTED_TRUE_SHAPE)
    }

    @Test
    fun `given triangle that closes only against its declared direction then the solution violates it`() {
        val outcome = solveAll(OverdeterminedCases.TRIANGLE_WRONG_DIRECTION.room.toShapeInput())
        assertFalse(outcome.solution.honorsDirections)
        assertTrue(outcome.alternatives.isEmpty())
        // The triangle 3-4-6 m itself is still met on the raw lengths.
        assertTrue(
            outcome.solution.outline.vertices[0]
                .isCloseTo(Vec2.ZERO, 0.0),
        )
        assertEquals(
            6000.0,
            outcome.solution.outline.vertices[2]
                .length(),
            GeometryTolerances.SOLVER_RESIDUAL_MM,
        )
    }

    @Test
    fun `given start branch triangle on its independent row then the refined shape closes and keeps directions`() {
        val input = OverdeterminedCases.TRIANGLE_START_BRANCH.room.toShapeInput()
        val outcome = solved(solver.solve(input, listOf(HardCondition.ClosureX), CancellationSignal.NONE))
        val problem = SolverProblem(input)
        val turns = outcome.solution.turnsRad.toDoubleArray()
        assertTrue(problem.hardResidualMm(turns, problem.walk(turns)) <= GeometryTolerances.EXACT_CLOSURE_MM)
        assertTrue(outcome.solution.honorsDirections)
        assertTrue(DefaultPolygonAnalyzer().isSimple(outcome.solution.outline))
    }

    @Test
    fun `given hexagon that is closed and simple early then the solver keeps it simple`() {
        val outcome = solveAll(OverdeterminedCases.HEXAGON_KEEPS_SIMPLE.room.toShapeInput())
        assertTrue(DefaultPolygonAnalyzer().isSimple(outcome.solution.outline))
        assertTrue(outcome.solution.honorsDirections)
    }

    @Test
    fun `given consistent second diagonal when solved on the independent rows then all diagonals are met`() {
        val input = OverdeterminedCases.TRAPEZOID_TWO_DIAGONALS.room.toShapeInput()
        val selection = allRows(input).dropLast(1)
        val outcome = solved(solver.solve(input, selection, CancellationSignal.NONE))
        val turns = outcome.solution.turnsRad.toDoubleArray()
        val problem = SolverProblem(input)
        assertTrue(problem.hardResidualMm(turns, problem.walk(turns)) <= GeometryTolerances.EXACT_CLOSURE_MM)
        assertTrue(outcome.alternatives.isNotEmpty())
    }

    @Test
    fun `given wall longer than all others together when solved then no simple shape exists`() {
        val input = ShapeInput(listOf(1000, 1000, 5000), listOf(CornerInput.Free, measured(90_000), LEFT_RIGHT_ANGLE))
        assertSame(SolverOutcome.NoSimpleShape, solver.solve(input, allRows(input), CancellationSignal.NONE))
    }

    @Test
    fun `given every inner corner measured when solved then there is nothing to solve`() {
        val input = OverdeterminedCases.RECTANGLE_LARGE_SIMPLE.room.toShapeInput()
        assertSame(SolverOutcome.NoSimpleShape, solver.solve(input, allRows(input), CancellationSignal.NONE))
    }

    @Test
    fun `given self intersecting target shape when solved then only simple outlines are returned`() {
        val outcome = solveAll(OverdeterminedCases.SELF_INTERSECTING_EXACT.room.toShapeInput())
        (listOf(outcome.solution) + outcome.alternatives).forEach {
            assertTrue(DefaultPolygonAnalyzer().isSimple(it.outline))
        }
    }

    @Test
    fun `given no condition when solved then the result is refined over all rows`() {
        val input = ReferenceRooms.TRAPEZOID_3_3_3_5.room.toShapeInput()
        val outcome = solved(solver.solve(input, emptyList(), CancellationSignal.NONE))
        val turns = outcome.solution.turnsRad.toDoubleArray()
        val problem = SolverProblem(input)
        assertTrue(problem.hardResidualMm(turns, problem.walk(turns)) <= GeometryTolerances.EXACT_CLOSURE_MM)
    }

    @Test
    fun `given mirrored trapezoid when solved then the turns are negated`() {
        val left = solveAll(ReferenceRooms.TRAPEZOID_3_3_3_5.room.toShapeInput()).solution
        val rightInput =
            ShapeInput(
                listOf(5000, 3000, 3000, 3000),
                listOf(CornerInput.Free) + List(3) { CornerInput.DeclaredRight(TurnDirection.RIGHT) },
            )
        val right = solveAll(rightInput).solution
        left.turnsRad.indices.forEach { assertEquals(-left.turnsRad[it], right.turnsRad[it], TURN_TOLERANCE_RAD) }
        assertEquals(areaMm2(left.outline), areaMm2(right.outline), AREA_TOLERANCE_M2 * MM2_PER_M2)
    }

    @Test
    fun `given conditions in another order when solved then the outcome is bit identical`() {
        val input = OverdeterminedCases.AMBIGUOUS_SELECTED_TRUE_SHAPE.room.toShapeInput()
        val forward = solver.solve(input, allRows(input), CancellationSignal.NONE)
        val reversed = DefaultShapeSolver().solve(input, allRows(input).reversed(), CancellationSignal.NONE)
        assertEquals(fingerprint(forward), fingerprint(reversed))
    }

    @Test
    fun `given duplicate condition when solved then it is rejected`() {
        val input = declaredQuad(4000, 3000, 4000, 3000)
        assertRejected(input, listOf(HardCondition.ClosureX, HardCondition.ClosureX))
    }

    @Test
    fun `given conditions that are no rows of the input when solved then they are rejected`() {
        val input =
            declaredQuad(
                4000,
                3000,
                4000,
                3000,
                diagonals = listOf(DiagonalInput(0, 1, 4000), DiagonalInput(0, 2, 5000)),
            )
        assertRejected(input, listOf(HardCondition.ClosingCorner))
        assertRejected(input, listOf(HardCondition.DiagonalLength(0)))
        assertRejected(input, listOf(HardCondition.DiagonalLength(2)))
    }

    private fun assertRejected(
        input: ShapeInput,
        conditions: List<HardCondition>,
    ) {
        assertThrows<IllegalArgumentException> {
            solver.solve(input, conditions, CancellationSignal.NONE)
        }
    }

    private fun assertAmbiguity(case: OverdeterminedCase) {
        val expected = case.expectedAmbiguity ?: error("No ambiguity in ${case.key}")
        val outcome = solveAll(case.room.toShapeInput())
        assertTurnsDeg(expected.selectedTurnsDeg, outcome.solution.turnsRad, FIXTURE_TURN_TOLERANCE_DEG)
        val alternative =
            outcome.alternatives.firstOrNull {
                turnsMatchDeg(expected.alternativeTurnsDeg, it.turnsRad, FIXTURE_TURN_TOLERANCE_DEG)
            } ?: error("Alternative missing in ${case.key}: ${outcome.alternatives}")
        assertEquals(
            expected.distanceMm,
            shapeDistanceMm(outcome.solution.outline.vertices, alternative.outline.vertices),
            ExpectedAmbiguity.DISTANCE_TOLERANCE_MM,
        )
        outcome.alternatives.forEach { assertTrue(it.honorsDirections) }
    }

    private fun assertVertices(
        expected: List<Vec2>,
        actual: List<Vec2>,
        toleranceMm: Double,
        message: String = "",
    ) {
        assertEquals(expected.size, actual.size, message)
        expected.indices.forEach { i ->
            assertTrue(expected[i].isCloseTo(actual[i], toleranceMm), "$message V$i: ${expected[i]} vs ${actual[i]}")
        }
    }

    private companion object {
        const val MM2_PER_M2 = 1_000_000.0
        const val EIGHT_SQRT_TWO_M2 = 11.313708498984761
        const val BASE_ANGLE_DEG = 70.52877936550931
        const val TOP_ANGLE_DEG = 109.47122063449069
        const val RIGHT_DEG = 90.0
        const val AREA_TOLERANCE_M2 = 1e-6
        const val ROUNDED_AREA_TOLERANCE_M2 = 5e-4
        const val ANGLE_TOLERANCE_DEG = 1e-6
        const val ROUNDED_ANGLE_TOLERANCE_DEG = 5e-3
        const val VERTEX_TOLERANCE_MM = 1e-3
        const val ROUNDED_INPUT_TOLERANCE_MM = 1.0
        const val RELATIVE_AREA_TOLERANCE = 1e-6
        const val COST_TOLERANCE = 1e-9
        const val ZERO_COST = 1e-18
        const val TURN_TOLERANCE_RAD = 1e-9
        const val FIXTURE_TURN_TOLERANCE_DEG = 1e-4
        const val SIDE_WALLS = 32
        const val STRAIGHT_MILLI_DEGREES = 180_000
    }
}
