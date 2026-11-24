package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.traverse.ShapeInput
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SolverFuzzTest {
    private val solver = DefaultShapeSolver()

    @Test
    fun `given arbitrary valid input and any row subset when solved then it never throws and stays finite`() =
        runBlocking<Unit> {
            var solvedCount = 0
            checkAll(ITERATIONS, config(SEED_VALID), fuzzCases) { case ->
                val outcome = solver.solve(case.input, case.conditions, CancellationSignal.NONE)
                if (outcome is SolverOutcome.Solved) {
                    solvedCount++
                    (listOf(outcome.solution) + outcome.alternatives).forEach { solution ->
                        assertTrue(solution.turnsRad.all { it.isFinite() })
                        assertTrue(solution.cost.isFinite() && solution.cost >= 0.0)
                        assertTrue(solution.outline.vertices.size == case.input.wallCount)
                    }
                    // Alternatives keep every direction and every hard condition on the raw lengths.
                    outcome.alternatives.forEach { alternative ->
                        val problem = SolverProblem(case.input)
                        val turns = alternative.turnsRad.toDoubleArray()
                        assertTrue(problem.hardResidualMm(turns, problem.walk(turns)) <= EXACT_CLOSURE_MM)
                        assertTrue(!problem.violatesDirections(turns))
                    }
                }
            }
            assertTrue(solvedCount > 0, "no fuzz case was solved")
        }

    @Test
    fun `given a condition that is no row of the input when solved then only IllegalArgumentException is thrown`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_INVALID), fuzzCases) { case ->
                val problem = SolverProblem(case.input)
                val foreign =
                    if (problem.closingMeasured) {
                        HardCondition.DiagonalLength(case.input.diagonals.size)
                    } else {
                        HardCondition.ClosingCorner
                    }
                assertThrows<IllegalArgumentException> {
                    solver.solve(case.input, case.conditions + foreign, CancellationSignal.NONE)
                }
                if (case.conditions.isNotEmpty()) {
                    assertThrows<IllegalArgumentException> {
                        solver.solve(case.input, case.conditions + case.conditions.first(), CancellationSignal.NONE)
                    }
                }
            }
        }

    private class FuzzCase(
        val input: ShapeInput,
        val conditions: List<HardCondition>,
    ) {
        override fun toString(): String = "$input $conditions"
    }

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED_VALID = 61_001L
        const val SEED_INVALID = 61_002L
        const val EXACT_CLOSURE_MM = 1.0

        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        /** Random valid input with a random subset of its rows in a random order. */
        val fuzzCases: Arb<FuzzCase> =
            arbitrary { rs ->
                val random = rs.random
                val input = randomFuzzInput(random)
                val rows = SolverProblem(input).rows
                val subset = if (random.nextInt(3) == 0) rows else rows.filter { random.nextBoolean() }
                FuzzCase(input, subset.shuffled(random))
            }
    }
}
