package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SolverStartsTest {
    @Test
    fun `given declared rectangle when starts are built then all four starts exist in the fixed order`() {
        val problem = SolverProblem(declaredQuad(4000, 3000, 4000, 3000))
        val starts = SolverStarts.of(problem)
        assertEquals(SolverStart.entries.toList(), starts.map { it.first })
        // S4: β = (360° − 90°) / 3 = 90°.
        starts.last().second.forEach { assertEquals(HALF_PI, it, TOLERANCE) }
    }

    @Test
    fun `given targets when the mean start is built then it averages target and regular turn`() {
        val input =
            ShapeInput(
                listOf(4000, 3000, 4000, 3000),
                listOf(CornerInput.Free) + List(3) { CornerInput.DeclaredRight(TurnDirection.RIGHT) },
            )
        // s = −1, the regular turn is −90° and every target is −90°.
        SolverStarts.meanTurns(SolverProblem(input)).forEach { assertEquals(-HALF_PI, it, TOLERANCE) }
        val pentagon = SolverProblem(declaredQuad(1000, 1000, 1000, 1000, 1000))
        val mean = SolverStarts.meanTurns(pentagon)
        assertEquals((HALF_PI + 2.0 * PI / 5.0) / 2.0, mean[1], TOLERANCE)
    }

    @Test
    fun `given equal lengths when the chord start is built then the regular polygon results`() {
        val problem = SolverProblem(declaredQuad(2000, 2000, 2000, 2000, 2000, 2000))
        SolverStarts.chordTurns(problem).forEach { assertEquals(PI / 3.0, it, TOLERANCE) }
    }

    @Test
    fun `given cyclic trapezoid when the chord start is built then the trapezoid itself results`() {
        val problem = SolverProblem(declaredQuad(5000, 3000, 3000, 3000))
        val turns = SolverStarts.chordTurns(problem)
        val top = StrictMath.acos(1.0 / 3.0)
        val expected = doubleArrayOf(PI - top, PI - top, top, top)
        expected.indices.forEach { assertEquals(expected[it], turns[it], TOLERANCE) }
    }

    @Test
    fun `given obtuse triangle with the center outside when the chord start is built then it closes`() {
        val problem = SolverProblem(declaredQuad(3000, 3000, 5000))
        assertClosedChord(problem)
        val acute = SolverProblem(declaredQuad(3000, 4000, 4500))
        assertClosedChord(acute)
    }

    @Test
    fun `given clockwise input when the chord start is built then every turn is negative`() {
        val input =
            ShapeInput(
                listOf(5000, 3000, 3000, 3000),
                listOf(CornerInput.Free) + List(3) { CornerInput.DeclaredRight(TurnDirection.RIGHT) },
            )
        SolverStarts.chordTurns(SolverProblem(input)).forEach { assertEquals(-1.0, StrictMath.signum(it), 0.0) }
    }

    @Test
    fun `given measured corners when the direction start is built then beta shares the remaining turn`() {
        // Targets 90° at V1 and 120° at V2 and V3 give r = 30°, so the free closing target is 0°.
        val input =
            ShapeInput(
                listOf(1000, 1000, 1000, 1000),
                listOf(CornerInput.Free, LEFT_RIGHT_ANGLE, measured(60_000), measured(60_000)),
            )
        val turns = SolverStarts.directionTurns(SolverProblem(input)) ?: error("S4 expected")
        assertEquals(StrictMath.toRadians(120.0), turns[1], TOLERANCE)
        assertEquals(StrictMath.toRadians(120.0), turns[2], TOLERANCE)
    }

    @Test
    fun `given opposite declared directions when the direction start is built then it is missing`() {
        val input =
            ShapeInput(
                listOf(1000, 1000, 1000, 1000),
                listOf(
                    CornerInput.Free,
                    LEFT_RIGHT_ANGLE,
                    CornerInput.DeclaredRight(TurnDirection.RIGHT),
                    measured(90_000),
                ),
            )
        val problem = SolverProblem(input)
        assertNull(SolverStarts.directionTurns(problem))
        assertEquals(3, SolverStarts.of(problem).size)
    }

    @Test
    fun `given beta outside zero to pi when the direction start is built then it is missing`() {
        // A right turn of 90° leaves β = 360° + 90° for the single free corner.
        val tooLarge =
            ShapeInput(
                listOf(1000, 1000, 1000),
                listOf(CornerInput.Free, LEFT_RIGHT_ANGLE, measured(90_000, TurnDirection.RIGHT)),
            )
        assertNull(SolverStarts.directionTurns(SolverProblem(tooLarge)))
        // A negative β is excluded: three left turns of 170° leave β = 360° − 510° − 90° = −240°.
        val negative =
            ShapeInput(
                listOf(1000, 1000, 1000, 1000, 1000),
                listOf(CornerInput.Free, LEFT_RIGHT_ANGLE, measured(10_000), measured(10_000), measured(10_000)),
            )
        assertNull(SolverStarts.directionTurns(SolverProblem(negative)))
    }

    private fun assertClosedChord(problem: SolverProblem) {
        val turns = SolverStarts.chordTurns(problem)
        val chain = problem.walk(turns)
        assertEquals(0.0, StrictMath.hypot(chain.x[problem.n], chain.y[problem.n]), CLOSURE_TOLERANCE_MM)
        assertEquals(2.0 * PI, turns.sum(), TOLERANCE)
    }

    private companion object {
        const val PI = StrictMath.PI
        const val HALF_PI = PI / 2.0
        const val TOLERANCE = 1e-9
        const val CLOSURE_TOLERANCE_MM = 1e-6
    }
}
