package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.determinacy.DefaultDeterminacyAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.ShapeInput
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

/**
 * The direction test at the closing corner `V_0` uses the turn that actually results, `τ_0 = 2π·s − Σ_{i≥1} τ_i`
 * (DOMAIN section 3), in the selection of section 5.3 and the alternatives filter of section 5.6 (T-GEO-009).
 */
class SolverClosingTurnTest {
    private val solver = DefaultShapeSolver()

    @Test
    fun `given measured closing corner outside the solved set when refined against it then its derived turn counts`() {
        // One free corner (V2), so ρ = 1 and I = {c_x}: the closing corner is not enforced, only refined against.
        val input =
            ShapeInput(
                lengthsMm = listOf(24_194, 2_497, 3_022, 5_012, 21_405, 4_245),
                corners =
                    listOf(
                        measured(179_301),
                        measured(143_156),
                        CornerInput.DeclaredRight(TurnDirection.RIGHT),
                        measured(92_267),
                        measured(87_262),
                        measured(103_947, TurnDirection.RIGHT),
                    ),
                diagonals = emptyList(),
            )
        val problem = SolverProblem(input)
        val selection =
            DefaultDeterminacyAnalyzer()
                .analyzeConstraints(input, problem.targets.toList(), CancellationSignal.NONE)
                .independentSelection
        assertEquals(listOf(HardCondition.ClosureX), selection)

        val outcome = solved(solver.solve(input, selection, CancellationSignal.NONE))

        // The target τ̂_0 = +0.699° keeps the left turn, the walked outline turns right by more than ε at V_0.
        val derived = problem.derivedClosingTurn(outcome.solution.turnsRad.toDoubleArray())
        val derivedDeg = StrictMath.toDegrees(SolverProblem.wrap(derived))
        assertTrue(derivedDeg < -GeometryTolerances.TURN_DIRECTION_TOLERANCE_DEG, "derived τ_0 = $derivedDeg°")
        assertFalse(outcome.solution.honorsDirections, "the closing corner turns against its measured direction")
        assertTrue(outcome.alternatives.none { violatesWithDerivedClosing(input, it.turnsRad) })
    }

    @Test
    fun `given enforced measured closing corner when solved then the derived turn meets its target`() {
        // Not overdetermined: the closing row is enforced, |Δτ_0| · U ≤ SOLVER_RESIDUAL_MM, far below ε.
        val reference = ReferenceTrapezoid
        val outcome = solveAll(reference)
        val problem = SolverProblem(reference)
        val turns = outcome.solution.turnsRad.toDoubleArray()
        val arcMm = StrictMath.abs(problem.closingResidual(turns)) * problem.perimeter / SolverProblem.FULL_TURN
        assertTrue(arcMm <= GeometryTolerances.SOLVER_RESIDUAL_MM, "closing arc $arcMm mm")
        assertTrue(outcome.solution.honorsDirections)
        assertFalse(violatesWithDerivedClosing(reference, outcome.solution.turnsRad))
    }

    @Test
    fun `given any closing corner when solved then honorsDirections matches the derived closing turn`() =
        runBlocking<Unit> {
            var measuredClosings = 0
            checkAll(ITERATIONS, config(), cases) { input ->
                val problem = SolverProblem(input)
                val selection =
                    DefaultDeterminacyAnalyzer()
                        .analyzeConstraints(input, problem.targets.toList(), CancellationSignal.NONE)
                        .independentSelection
                val outcome = solver.solve(input, selection, CancellationSignal.NONE)
                if (outcome is SolverOutcome.Solved) {
                    if (problem.closingMeasured) measuredClosings++
                    val expected = !violatesWithDerivedClosing(input, outcome.solution.turnsRad)
                    assertEquals(expected, outcome.solution.honorsDirections, "solution of $input")
                    outcome.alternatives.forEach {
                        assertFalse(violatesWithDerivedClosing(input, it.turnsRad), "alternative of $input")
                    }
                }
            }
            assertTrue(measuredClosings > 0, "no measured closing corner was solved")
        }

    private companion object {
        const val ITERATIONS = 1_500
        const val SEED = 61_009L
        const val NEAR_STRAIGHT_MILLI_DEGREES = 170_000
        const val STRAIGHT_MILLI_DEGREES = 180_000

        /** Trapezoid 3 3 3 5 with the true base angle measured at `V_0`: `v = 3`, `m = 3`, not overdetermined. */
        val ReferenceTrapezoid: ShapeInput =
            ShapeInput(
                lengthsMm = listOf(5_000, 3_000, 3_000, 3_000),
                corners = listOf(measured(70_529), LEFT_RIGHT_ANGLE, LEFT_RIGHT_ANGLE, LEFT_RIGHT_ANGLE),
                diagonals = emptyList(),
            )

        @OptIn(ExperimentalKotest::class)
        fun config() = PropTestConfig(seed = SEED)

        /** Fuzz inputs whose closing corner is free, declared or measured, measured ones often almost straight. */
        val cases: Arb<ShapeInput> =
            arbitrary { rs ->
                val random = rs.random
                val raw = randomFuzzInput(random)
                val direction = if (random.nextBoolean()) TurnDirection.LEFT else TurnDirection.RIGHT
                val closing =
                    when (random.nextInt(4)) {
                        0 -> CornerInput.Free
                        1 -> CornerInput.DeclaredRight(direction)
                        2 -> measured(random.nextInt(NEAR_STRAIGHT_MILLI_DEGREES, STRAIGHT_MILLI_DEGREES), direction)
                        else -> measured(random.nextInt(1, STRAIGHT_MILLI_DEGREES + 1), direction)
                    }
                ShapeInput(raw.lengthsMm, listOf(closing) + raw.corners.drop(1), raw.diagonals)
            }

        /**
         * Test oracle of DOMAIN section 3, independent of the production check: a corner with direction `d` violates
         * it at `d · wrap(τ) < −ε`, with `τ_0 = 2π·s − Σ_{i≥1} τ_i` at the closing corner.
         */
        fun violatesWithDerivedClosing(
            input: ShapeInput,
            turnsRad: List<Double>,
        ): Boolean {
            val problem = SolverProblem(input)
            val epsilon = StrictMath.toRadians(GeometryTolerances.TURN_DIRECTION_TOLERANCE_DEG)
            var inner = 0.0
            for (i in 1 until turnsRad.size) inner += turnsRad[i]
            val turns = listOf(SolverProblem.FULL_TURN * problem.sign - inner) + turnsRad.drop(1)
            return input.corners.indices.any { i ->
                val d =
                    when (val corner = input.corners[i]) {
                        is CornerInput.DeclaredRight -> corner.direction.sign
                        is CornerInput.Measured -> if (corner.angle == Angle.STRAIGHT) 0 else corner.direction.sign
                        CornerInput.Free -> 0
                    }
                d != 0 && d * SolverProblem.wrap(turns[i]) < -epsilon
            }
        }
    }
}
