package app.plansnap.core.geometry.solver

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolverProblemTest {
    @Test
    fun `given angles when wrapped then the result lies in the half open interval up to pi`() {
        assertEquals(PI, SolverProblem.wrap(PI), 0.0)
        assertEquals(PI, SolverProblem.wrap(-PI), 1e-15)
        assertEquals(PI, SolverProblem.wrap(3.0 * PI), 1e-15)
        assertEquals(0.5, SolverProblem.wrap(0.5), 1e-15)
        assertEquals(-0.5, SolverProblem.wrap(-0.5 + 4.0 * PI), 1e-14)
        assertEquals(-PI + 0.25, SolverProblem.wrap(PI + 0.25), 1e-15)
    }

    @Test
    fun `given input when the problem is built then targets, sign, free corners and rows follow DOMAIN`() {
        val input =
            ShapeInput(
                listOf(4000, 3000, 4000, 3000, 2000),
                listOf(measured(90_000), LEFT_RIGHT_ANGLE, measured(100_000), LEFT_RIGHT_ANGLE, LEFT_RIGHT_ANGLE),
                listOf(
                    DiagonalInput(3, 1, 5000),
                    DiagonalInput(0, 4, 2000),
                    DiagonalInput(1, 3, 4000),
                    DiagonalInput(0, 2, 6000),
                    DiagonalInput(2, 3, 4000),
                    DiagonalInput(3, 1, 4000),
                ),
            )
        val problem = SolverProblem(input)
        assertArrayEquals(intArrayOf(1, 3, 4), problem.free)
        assertEquals(1, problem.sign)
        assertEquals(16_000.0, problem.perimeter, 0.0)
        assertTrue(problem.closingMeasured)
        assertEquals(StrictMath.toRadians(80.0), problem.targets[2], 1e-15)
        assertEquals(
            listOf(
                HardCondition.ClosureX,
                HardCondition.ClosureY,
                HardCondition.ClosingCorner,
                HardCondition.DiagonalLength(3),
                HardCondition.DiagonalLength(2),
                HardCondition.DiagonalLength(5),
                HardCondition.DiagonalLength(0),
            ),
            problem.rows,
        )
        assertTrue(problem.isAdjacent(DiagonalInput(4, 0, 10)))
        assertTrue(problem.isAdjacent(DiagonalInput(2, 3, 10)))
        assertFalse(problem.isAdjacent(DiagonalInput(1, 3, 10)))
    }

    @Test
    fun `given turns when walked then the chain matches the traverse walk bit for bit`() {
        val input = declaredQuad(5000, 3000, 3000, 3000)
        val problem = SolverProblem(input)
        val turns = doubleArrayOf(0.3, 1.9, 1.2, 1.2)
        val chain = problem.walk(turns)
        val expected = DefaultTraverse().walk(input.lengthsMm.map { it.toDouble() }, turns.toList())
        expected.indices.forEach {
            assertEquals(expected[it].x.toRawBits(), chain.x[it].toRawBits())
            assertEquals(expected[it].y.toRawBits(), chain.y[it].toRawBits())
        }
        assertEquals(4, chain.vertices().size)
    }

    @Test
    fun `given random inputs when differentiated then the Jacobian matches central differences`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_JACOBIAN), problems) { case ->
                val problem = case.problem
                val chain = problem.walk(case.turns)
                val jacobian = problem.jacobian(problem.rows, chain)
                problem.free.forEachIndexed { k, i ->
                    val plus = case.turns.copyOf().also { it[i] += STEP }
                    val minus = case.turns.copyOf().also { it[i] -= STEP }
                    val up = problem.residuals(problem.rows, plus, problem.walk(plus))
                    val down = problem.residuals(problem.rows, minus, problem.walk(minus))
                    problem.rows.indices.forEach { r ->
                        val numeric = (up[r] - down[r]) / (2.0 * STEP)
                        val tolerance = DERIVATIVE_TOLERANCE * maxOf(1.0, StrictMath.abs(numeric))
                        assertEquals(numeric, jacobian[r, k], tolerance, "row ${problem.rows[r]} col $i")
                    }
                }
            }
        }

    @Test
    fun `given random inputs when the objective is differentiated then the gradient matches central differences`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_GRADIENT), problems) { case ->
                val problem = case.problem
                val gradient = problem.gradient(case.turns)
                problem.free.forEachIndexed { k, i ->
                    val plus = case.turns.copyOf().also { it[i] += STEP }
                    val minus = case.turns.copyOf().also { it[i] -= STEP }
                    val numeric = (problem.cost(plus) - problem.cost(minus)) / (2.0 * STEP)
                    assertEquals(numeric, gradient[k], DERIVATIVE_TOLERANCE * problem.n)
                }
            }
        }

    @Test
    fun `given closed trapezoid when measured then residuals vanish and the cost counts soft corners`() {
        val input = declaredQuad(5000, 3000, 3000, 3000, diagonals = listOf(DiagonalInput(0, 2, 4899)))
        val problem = SolverProblem(input)
        val top = StrictMath.acos(1.0 / 3.0)
        val turns = doubleArrayOf(PI - top, PI - top, top, top)
        val chain = problem.walk(turns)
        problem.residuals(problem.rows, turns, chain).forEach { assertEquals(0.0, it, 1e-5) }
        val diagonalResidual = 4899.0 - StrictMath.sqrt(24e6)
        assertEquals(diagonalResidual, problem.hardResidualMm(turns, chain), 1e-6)
        val deviation = PI / 2.0 - top
        assertEquals(4.0 * deviation * deviation, problem.cost(turns), 1e-12)
        assertEquals(PI - top, problem.derivedClosingTurn(turns), 1e-12)
        assertEquals(diagonalResidual, problem.outsideResidualMm(listOf(HardCondition.ClosureX), turns, chain), 1e-6)
        assertEquals(0.0, problem.outsideResidualMm(problem.rows, turns, chain), 0.0)
    }

    @Test
    fun `given measured closing corner when it is outside the selection then its arc counts as residual`() {
        val input =
            ShapeInput(listOf(4000, 3000, 4000, 3000), listOf(measured(91_000)) + List(3) { LEFT_RIGHT_ANGLE })
        val problem = SolverProblem(input)
        val turns = doubleArrayOf(0.0, PI / 2.0, PI / 2.0, PI / 2.0)
        val chain = problem.walk(turns)
        val arc = StrictMath.toRadians(1.0) * problem.perimeter / (2.0 * PI)
        assertEquals(arc, problem.outsideResidualMm(listOf(HardCondition.ClosureX), turns, chain), 1e-9)
        assertEquals(0.0, problem.outsideResidualMm(problem.rows, turns, chain), 0.0)
        assertEquals(arc, problem.hardResidualMm(turns, chain), 1e-9)
        // The measured closing corner keeps its target in a candidate, the free turns are wrapped.
        val candidate = problem.candidateTurns(doubleArrayOf(5.0, PI / 2.0 + 2.0 * PI, PI / 2.0, -PI))
        assertEquals(problem.targets[0], candidate[0], 0.0)
        assertEquals(PI / 2.0, candidate[1], 1e-12)
        assertEquals(PI, candidate[3], 1e-12)
    }

    @Test
    fun `given free closing corner when a candidate is built then its turn is derived and wrapped`() {
        val problem = SolverProblem(declaredQuad(4000, 3000, 4000, 3000))
        val candidate = problem.candidateTurns(doubleArrayOf(9.0, PI / 2.0, PI / 2.0, PI / 2.0 - 2.0 * PI))
        assertEquals(PI / 2.0, candidate[0], 1e-12)
        assertEquals(PI / 2.0, candidate[3], 1e-12)
        assertEquals((PI / 2.0).toRawBits(), candidate[1].toRawBits())
    }

    @Test
    fun `given turns near the direction tolerance when checked then only beyond two degrees is a violation`() {
        val input =
            ShapeInput(
                listOf(1000, 1000, 1000, 1000),
                listOf(
                    CornerInput.Free,
                    LEFT_RIGHT_ANGLE,
                    measured(180_000, TurnDirection.LEFT),
                    CornerInput.DeclaredRight(TurnDirection.RIGHT),
                ),
            )
        val problem = SolverProblem(input)
        val ok = StrictMath.toRadians(-1.9)
        val bad = StrictMath.toRadians(-2.1)
        // The free closing corner and the straight measured corner never violate.
        assertFalse(problem.violatesDirections(doubleArrayOf(-3.0, ok, -3.0, -ok)))
        assertTrue(problem.violatesDirections(doubleArrayOf(0.0, bad, 0.0, 0.0)))
        assertTrue(problem.violatesDirections(doubleArrayOf(0.0, 0.0, 0.0, -bad)))
        val measuredLeft =
            ShapeInput(listOf(1000, 1000, 1000), listOf(measured(60_000), measured(60_000), LEFT_RIGHT_ANGLE))
        assertTrue(SolverProblem(measuredLeft).violatesDirections(doubleArrayOf(bad, 1.0, 1.0)))
    }

    private class ProblemCase(
        val problem: SolverProblem,
        val turns: DoubleArray,
    ) {
        override fun toString(): String = "${problem.input} ${turns.toList()}"
    }

    private companion object {
        const val PI = StrictMath.PI
        const val ITERATIONS = 1_000
        const val SEED_JACOBIAN = 41_001L
        const val SEED_GRADIENT = 41_002L
        const val STEP = 1e-6
        const val DERIVATIVE_TOLERANCE = 1e-6

        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        /** Random inputs with free, declared and measured corners and diagonals, at random turns. */
        val problems: Arb<ProblemCase> =
            arbitrary { rs ->
                val random = rs.random
                val n = random.nextInt(3, 10)
                val corners =
                    List(n) { i ->
                        when (random.nextInt(3)) {
                            0 -> if (i == 0) CornerInput.Free else LEFT_RIGHT_ANGLE
                            1 -> CornerInput.DeclaredRight(TurnDirection.RIGHT)
                            else -> measured(random.nextInt(1, 180_001), TurnDirection.entries[random.nextInt(2)])
                        }
                    }
                val diagonals =
                    List(random.nextInt(4)) {
                        val a = random.nextInt(n)
                        DiagonalInput(a, (a + random.nextInt(1, n)) % n, random.nextLong(100, 50_000))
                    }
                val input = ShapeInput(List(n) { random.nextLong(100, 20_000) }, corners, diagonals)
                ProblemCase(SolverProblem(input), DoubleArray(n) { random.nextDouble(-3.0, 3.0) })
            }
    }
}
