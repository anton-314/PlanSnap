@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import app.plansnap.testing.fixtures.random.SeededRandom
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property test over random command sequences (TESTING section 3, property 7): `undo ∘ execute` and `redo ∘ undo`
 * are the identity, failures change nothing and commands are pure.
 */
class CommandHistoryPropertyTest {
    private val factory = DefaultProjectCommandFactory(FakeOpeningRules())
    private val tolerance = ClosureTolerance()

    @Test
    fun `given random command sequences when executed undone and redone then history laws hold`() =
        runBlocking<Unit> {
            val iterations = AtomicInteger()
            val successes = AtomicInteger()
            val failures = AtomicInteger()
            checkAll(ITERATIONS, config(), Arb.long()) { seed ->
                iterations.incrementAndGet()
                val random = SeededRandom(seed)
                val start = ProjectGenerator(seed).project(roomCount = 3)
                val history =
                    DefaultCommandHistory(
                        start,
                        OpenEvaluator(),
                        RecordingResolver(),
                        TestDispatcherProvider(UnconfinedTestDispatcher()),
                    )
                val edits = RandomEdits(random)
                repeat(STEPS) {
                    when (random.nextInt(ACTIONS)) {
                        0 -> checkUndo(history)
                        1 -> checkRedo(history)
                        else -> checkExecute(history, edits, successes, failures)
                    }
                }
                // Walking back to the start must reach the initial project exactly.
                while (history.state.canUndo) history.undo()
                assertSame(start, history.state.project)
            }
            assertEquals(ITERATIONS, iterations.get())
            assertTrue(successes.get() >= MIN_SUCCESSES, "successful commands: ${successes.get()}")
            assertTrue(failures.get() >= MIN_FAILURES, "rejected commands: ${failures.get()}")
        }

    private suspend fun checkExecute(
        history: CommandHistory,
        edits: RandomEdits,
        successes: AtomicInteger,
        failures: AtomicInteger,
    ) {
        val before = history.state
        val edit = edits.next(before.project)
        val command = factory.create(edit)
        val expected = command.apply(before.project)
        assertEquals(expected, factory.create(edit).apply(before.project), "$edit must be deterministic")

        val result = history.execute(command, tolerance)

        if (result is DomainResult.Success && expected is DomainResult.Success) {
            successes.incrementAndGet()
            assertEquals(expected.value, result.value)
            assertEquals(HistoryState(result.value, before.undoDepth + 1, 0), history.state)
            assertSame(before.project, history.undo())
            assertEquals(HistoryState(before.project, before.undoDepth, 1), history.state)
            assertSame(result.value, history.redo())
            assertEquals(HistoryState(result.value, before.undoDepth + 1, 0), history.state)
        } else {
            failures.incrementAndGet()
            assertEquals(expected, result, "$edit")
            assertEquals(before, history.state, "a failure must change nothing")
        }
    }

    private suspend fun checkUndo(history: CommandHistory) {
        val before = history.state
        val undone = history.undo()
        if (before.canUndo) {
            assertEquals(before.undoDepth - 1, history.state.undoDepth)
            assertEquals(before.redoDepth + 1, history.state.redoDepth)
            assertSame(undone, history.state.project)
            assertSame(before.project, history.redo())
            assertSame(undone, history.undo())
        } else {
            assertNull(undone)
            assertEquals(before, history.state)
        }
    }

    private suspend fun checkRedo(history: CommandHistory) {
        val before = history.state
        val redone = history.redo()
        if (before.canRedo) {
            assertSame(redone, history.state.project)
            assertEquals(before.redoDepth - 1, history.state.redoDepth)
            assertSame(before.project, history.undo())
            assertSame(redone, history.redo())
        } else {
            assertNull(redone)
            assertEquals(before, history.state)
        }
    }

    private companion object {
        const val ITERATIONS = 300
        const val STEPS = 40
        const val ACTIONS = 5
        const val SEED = 20_241_124L
        const val MIN_SUCCESSES = 1_000
        const val MIN_FAILURES = 1_000

        @OptIn(ExperimentalKotest::class)
        fun config() = PropTestConfig(seed = SEED)
    }
}
