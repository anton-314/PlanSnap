package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.traverse.CornerInput
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

class SolverPropertyTest {
    private val solver = DefaultShapeSolver()

    @Test
    fun `given determined or open convex rooms when solved then every hard measurement is preserved`() =
        runBlocking<Unit> {
            var solvedCount = 0
            checkAll(ITERATIONS, config(SEED_HARD), convexInputs) { input ->
                val outcome = solver.solve(input, allRows(input), CancellationSignal.NONE)
                if (outcome is SolverOutcome.Solved) {
                    solvedCount++
                    assertHardConditions(input, outcome.solution)
                    outcome.alternatives.forEach {
                        assertHardConditions(input, it)
                        assertTrue(it.honorsDirections)
                    }
                }
            }
            // Every generated room has a solution near its true shape, and the fixed starts find one.
            assertEquals(ITERATIONS, solvedCount)
        }

    @Test
    fun `given equal input when solved twice then the outcome is bit identical`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_DETERMINISM), convexInputs) { input ->
                val first = solver.solve(input, allRows(input), CancellationSignal.NONE)
                val second = DefaultShapeSolver().solve(copyOf(input), allRows(input), CancellationSignal.NONE)
                assertEquals(fingerprint(first), fingerprint(second))
            }
        }

    @Test
    fun `given room entered from another start corner when solved then the same shape results`() =
        runBlocking<Unit> {
            var compared = 0
            checkAll(ITERATIONS, config(SEED_START_CORNER), shiftedRooms) { case ->
                val original = solver.solve(case.input, allRows(case.input), CancellationSignal.NONE)
                val shiftedInput = case.shifted()
                val shifted = solver.solve(shiftedInput, allRows(shiftedInput), CancellationSignal.NONE)
                if (original is SolverOutcome.Solved && shifted is SolverOutcome.Solved) {
                    compared++
                    val p = original.solution.outline.vertices
                    val q = shifted.solution.outline.vertices
                    val n = p.size
                    val aligned = List(n) { p[(it + case.shift) % n] }
                    assertTrue(
                        shapeDistanceMm(aligned, q) <= INVARIANCE_TOLERANCE_MM,
                        "distance ${shapeDistanceMm(aligned, q)}",
                    )
                    assertEquals(
                        areaMm2(original.solution.outline),
                        areaMm2(shifted.solution.outline),
                        areaMm2(original.solution.outline) * 1e-9,
                    )
                }
            }
            assertEquals(ITERATIONS, compared)
        }

    @Test
    fun `given mirrored room when solved then the mirrored shape results`() =
        runBlocking<Unit> {
            var compared = 0
            checkAll(ITERATIONS, config(SEED_MIRROR), convexInputs) { input ->
                val original = solver.solve(input, allRows(input), CancellationSignal.NONE)
                val mirror = solver.solve(mirrored(input), allRows(input), CancellationSignal.NONE)
                if (original is SolverOutcome.Solved && mirror is SolverOutcome.Solved) {
                    compared++
                    // The mirror image of the local frame is the reflection at the x axis.
                    val reflected =
                        original.solution.outline.vertices
                            .map { Vec2(it.x, -it.y) }
                    mirror.solution.outline.vertices.forEachIndexed { i, vertex ->
                        assertTrue(
                            vertex.isCloseTo(reflected[i], INVARIANCE_TOLERANCE_MM),
                            "V$i $vertex ${reflected[i]}",
                        )
                    }
                    assertEquals(original.solution.honorsDirections, mirror.solution.honorsDirections)
                }
            }
            assertEquals(ITERATIONS, compared)
        }

    @Test
    fun `given solver sources then trigonometry and roots only use StrictMath`() {
        val sources = File(SOLVER_SOURCES).listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.size >= 2, "no sources found in ${File(SOLVER_SOURCES).absolutePath}")
        sources.forEach { file ->
            val match = FORBIDDEN_MATH.find(file.readText())
            assertTrue(match == null, "non-strict math in ${file.name}: ${match?.value}")
        }
    }

    private fun assertHardConditions(
        input: ShapeInput,
        solution: ShapeSolution,
    ) {
        val problem = SolverProblem(input)
        val turns = solution.turnsRad.toDoubleArray()
        // Measured corners are eliminated, so their turns are the raw targets bit for bit.
        input.corners.indices.filter { it > 0 && input.corners[it] is CornerInput.Measured }.forEach {
            assertEquals(problem.targets[it].toRawBits(), turns[it].toRawBits(), "measured corner $it")
        }
        val chain = problem.walk(turns)
        val residual = problem.hardResidualMm(turns, chain)
        assertTrue(residual <= GeometryTolerances.EXACT_CLOSURE_MM, "hard residual $residual")
        assertTrue(residual <= 2.0 * GeometryTolerances.SOLVER_RESIDUAL_MM, "hard residual $residual")
        val vertices = solution.outline.vertices
        for (i in 0 until vertices.size - 1) {
            val length = (vertices[i + 1] - vertices[i]).length()
            assertEquals(input.lengthsMm[i].toDouble(), length, LENGTH_TOLERANCE_MM, "wall $i")
        }
        assertTrue(DefaultPolygonAnalyzer().isSimple(solution.outline), "not simple")
    }

    private class ShiftedRoom(
        val input: ShapeInput,
        val shift: Int,
    ) {
        fun shifted(): ShapeInput = ShapeInput(input.lengthsMm.rotate(shift), input.corners.rotate(shift), emptyList())

        override fun toString(): String = "$input shift $shift"
    }

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED_HARD = 51_001L
        const val SEED_DETERMINISM = 51_002L
        const val SEED_START_CORNER = 51_003L
        const val SEED_MIRROR = 51_004L
        const val INVARIANCE_TOLERANCE_MM = 0.05
        const val LENGTH_TOLERANCE_MM = 1e-6
        const val DECLARED_CORNERS = 3
        const val SOLVER_SOURCES = "src/main/kotlin/app/plansnap/core/geometry/solver"

        /** Non-strict trigonometry, roots or powers from Kotlin or `java.lang.Math`. */
        val FORBIDDEN_MATH =
            Regex(
                """kotlin\.math\.(\*|sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|ln|log|pow|cbrt)\b|""" +
                    """(?<!Strict)\bMath\.(sin|cos|tan|asin|acos|atan|atan2|sqrt|hypot|exp|log|pow|cbrt)\(""",
            )

        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)

        fun <T> List<T>.rotate(shift: Int): List<T> = List(size) { this[(it + shift) % size] }

        fun copyOf(input: ShapeInput): ShapeInput =
            ShapeInput(input.lengthsMm.toList(), input.corners.toList(), input.diagonals.toList())

        val convexInputs: Arb<ShapeInput> = arbitrary { rs -> randomConvexInput(rs.random) }

        /** Determined rooms: `V_0` measured, three declared corners, every other corner measured, no diagonals. */
        val shiftedRooms: Arb<ShiftedRoom> =
            arbitrary { rs ->
                val random = rs.random
                val vertices = randomConvexPolygon(random, random.nextInt(5, 9))
                val n = vertices.size
                val declared = (1 until n).shuffled(random).take(DECLARED_CORNERS).toSet()
                val corners = List(n) { if (it in declared) LEFT_RIGHT_ANGLE else measuredCorner(vertices, it) }
                ShiftedRoom(ShapeInput(roundedLengths(vertices), corners), random.nextInt(1, n))
            }
    }
}
