package app.plansnap.core.geometry.solver

import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.traverse.toShapeInput
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException

class SolverCancellationTest {
    private val solver = DefaultShapeSolver()
    private val input = ReferenceRooms.TRAPEZOID_3_3_3_5.room.toShapeInput()

    @Test
    fun `given cancelled signal when solved then the computation stops without a result`() {
        assertThrows<CancellationException> {
            solver.solve(input, allRows(input), CancellationSignal { throw CancellationException("cancelled") })
        }
    }

    @Test
    fun `given signal when solved then it is checked before every start and in every iteration`() {
        val counter = CountingSignal(Int.MAX_VALUE)
        solver.solve(input, allRows(input), counter)
        // Four starts, each with at least one SQP iteration, one refinement step and a second SQP run.
        assertTrue(counter.calls >= 4 * 4, "only ${counter.calls} checkpoints")
    }

    @Test
    fun `given cancellation at any checkpoint when solved then no partial result is returned`() {
        val total = CountingSignal(Int.MAX_VALUE).also { solver.solve(input, allRows(input), it) }.calls
        listOf(1, 2, total / 2, total).forEach { limit ->
            val signal = CountingSignal(limit)
            assertThrows<CancellationException> { solver.solve(input, allRows(input), signal) }
            assertEquals(limit, signal.calls)
        }
    }

    @Test
    fun `given overdetermined selection when cancelled in the refinement then the computation stops`() {
        val calls = CountingSignal(Int.MAX_VALUE).also { solver.solve(input, emptyList(), it) }.calls
        assertThrows<CancellationException> { solver.solve(input, emptyList(), CountingSignal(calls)) }
    }

    /** Signal that cancels at checkpoint number [limit]. */
    private class CountingSignal(
        private val limit: Int,
    ) : CancellationSignal {
        var calls: Int = 0
            private set

        override fun checkpoint() {
            calls++
            if (calls >= limit) throw CancellationException("cancelled at $calls")
        }
    }
}
