@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests of the mutex flow, commit, cancellation, undo, redo and reconcile of [DefaultCommandHistory] (ADR 0016). */
class DefaultCommandHistoryTest {
    private val strict = ClosureTolerance()
    private val loose = ClosureTolerance(absoluteMm = 250L)
    private val project = projectOf(rectangle("a"))
    private val rejection = DomainResult.Failure(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE))

    private fun rename(
        name: String,
        events: MutableList<String>? = null,
    ) = object : ProjectCommand {
        override suspend fun verify(
            project: Project,
            context: CommandContext,
        ): DomainResult<Unit> {
            events?.add("verify $name sees ${project.name}")
            return DomainResult.Success(Unit)
        }

        override fun apply(project: Project): DomainResult<Project> {
            events?.add("apply $name")
            return DomainResult.Success(project.copy(name = name))
        }
    }

    @Test
    fun `given a command when executed then verify precedes apply and resolve and one undo step is created`() =
        runTest {
            val events = mutableListOf<String>()
            val resolver =
                RecordingResolver {
                    events += "resolve ${it.name}"
                    DomainResult.Success(it.copy(updatedAtEpochMs = 99))
                }
            val history = historyOf(project, resolver = resolver)

            val result = valueOf(history.execute(rename("one", events), strict))

            assertEquals(listOf("verify one sees Flat", "apply one", "resolve one"), events)
            assertEquals(project.copy(name = "one", updatedAtEpochMs = 99), result)
            assertEquals(HistoryState(result, undoDepth = 1, redoDepth = 0), history.state)
            assertEquals(listOf(strict), resolver.calls.map { it.second })
        }

    @Test
    fun `given a failing verify when executed then apply and resolve never run and nothing changes`() =
        runTest {
            val resolver = RecordingResolver()
            val history = historyOf(project, resolver = resolver)
            var applied = false
            val command =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> = rejection

                    override fun apply(project: Project): DomainResult<Project> {
                        applied = true
                        return DomainResult.Success(project)
                    }
                }
            val before = history.state

            val result = history.execute(command, strict)

            assertEquals(rejection, result)
            assertFalse(applied)
            assertTrue(resolver.calls.isEmpty())
            assertEquals(before, history.state)
        }

    @Test
    fun `given a failing apply or resolve when executed then the first failure is returned and nothing changes`() =
        runTest {
            val failing = ProjectCommand { rejection }
            val history = historyOf(project)
            val resolverFailure = DomainResult.Failure(DomainError.NotFound(EntityKind.ROOM, "q"))
            val resolving = historyOf(project, resolver = RecordingResolver { resolverFailure })

            assertEquals(rejection, history.execute(failing, strict))
            assertEquals(resolverFailure, resolving.execute(rename("x"), strict))
            assertEquals(HistoryState(project, 0, 0), history.state)
            assertEquals(HistoryState(project, 0, 0), resolving.state)
        }

    @Test
    fun `given executed commands when undone and redone then the stored projects are restored without verify`() =
        runTest {
            val evaluator = OpenEvaluator()
            val resolver = RecordingResolver()
            val history = historyOf(project, evaluator, resolver)
            var verified = 0
            val counting =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> {
                        verified++
                        return DomainResult.Success(Unit)
                    }

                    override fun apply(project: Project) = DomainResult.Success(project.copy(name = "two"))
                }
            val one = valueOf(history.execute(rename("one"), strict))
            val two = valueOf(history.execute(counting, strict))
            val resolves = resolver.calls.size

            assertSame(one, history.undo())
            assertSame(project, history.undo())
            assertNull(history.undo())
            assertSame(one, history.redo())
            assertSame(two, history.redo())
            assertNull(history.redo())

            assertEquals(1, verified)
            assertEquals(resolves, resolver.calls.size)
            assertEquals(HistoryState(two, 2, 0), history.state)
        }

    @Test
    fun `given an undone step when a new command is executed then the redo stack is discarded`() =
        runTest {
            val history = historyOf(project)
            history.execute(rename("one"), strict)
            history.undo()
            assertTrue(history.state.canRedo)

            history.execute(rename("two"), strict)

            assertFalse(history.state.canRedo)
            assertTrue(history.state.canUndo)
            assertNull(history.redo())
        }

    @Test
    fun `given a failing command after undo when executed then the redo stack survives`() =
        runTest {
            val history = historyOf(project)
            history.execute(rename("one"), strict)
            history.undo()

            history.execute(ProjectCommand { rejection }, strict)

            assertEquals(1, history.state.redoDepth)
        }

    @Test
    fun `given changed placements when reconciled then exactly one undo step is created and redo is discarded`() =
        runTest {
            var moved = false
            val resolver =
                RecordingResolver {
                    DomainResult.Success(if (moved) it.copy(name = "placed") else it)
                }
            val history = historyOf(project, resolver = resolver)
            history.execute(rename("one"), strict)
            history.undo()
            assertEquals(1, history.state.redoDepth)

            assertEquals(DomainResult.Success(ReconcileOutcome.UNCHANGED), history.reconcile(loose))
            assertEquals(HistoryState(project, 0, 1), history.state)

            moved = true
            assertEquals(DomainResult.Success(ReconcileOutcome.PLACEMENTS_UPDATED), history.reconcile(loose))
            assertEquals(HistoryState(project.copy(name = "placed"), 1, 0), history.state)
            assertEquals(project, history.undo())
        }

    @Test
    fun `given a failing resolver when reconciled then project and history stay unchanged`() =
        runTest {
            val history = historyOf(project, resolver = RecordingResolver { rejection })

            assertEquals(rejection, history.reconcile(strict))
            assertEquals(HistoryState(project, 0, 0), history.state)
        }

    @Test
    fun `given a running execute when others are called then they run in call order after it`() =
        runTest {
            val events = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> {
                        events += "slow verify start"
                        gate.await()
                        events += "slow verify end"
                        return DomainResult.Success(Unit)
                    }

                    override fun apply(project: Project) = DomainResult.Success(project.copy(name = "one"))
                }
            val history = historyOf(project)

            launch { history.execute(slow, strict) }
            runCurrent()
            launch { history.execute(rename("two", events), strict) }
            launch { events += "undo -> ${history.undo()?.name}" }
            runCurrent()

            assertEquals(listOf("slow verify start"), events)
            assertEquals(HistoryState(project, 0, 0), history.state)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf("slow verify start", "slow verify end", "verify two sees one", "apply two", "undo -> one"),
                events,
            )
            assertEquals(HistoryState(project.copy(name = "one"), 1, 1), history.state)
        }

    @Test
    fun `given a computing execute when read then the state shows nothing before the commit`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val resolver =
                RecordingResolver {
                    gate.await()
                    DomainResult.Success(it)
                }
            val history = historyOf(project, resolver = resolver)

            val job = launch { history.execute(rename("one"), strict) }
            runCurrent()

            assertEquals(HistoryState(project, 0, 0), history.state)
            gate.complete(Unit)
            job.join()
            assertEquals(1, history.state.undoDepth)
            assertEquals("one", history.state.project.name)
        }

    @Test
    fun `given a cancellation inside verify when executed then nothing changes and the mutex is free`() =
        runTest {
            val resolver = RecordingResolver()
            val history = historyOf(project, resolver = resolver)
            val started = CompletableDeferred<Unit>()
            val hanging =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> {
                        started.complete(Unit)
                        awaitCancellation()
                    }

                    override fun apply(project: Project) = DomainResult.Success(project.copy(name = "never"))
                }

            val job = launch { history.execute(hanging, strict) }
            started.await()
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(HistoryState(project, 0, 0), history.state)
            assertTrue(resolver.calls.isEmpty())
            assertEquals("one", valueOf(history.execute(rename("one"), strict)).name)
        }

    @Test
    fun `given a cancellation after the computation when executed then the commit does not happen`() =
        runTest {
            val history = historyOf(project)
            lateinit var job: Job
            val cancelling =
                ProjectCommand {
                    job.cancel()
                    DomainResult.Success(it.copy(name = "computed"))
                }

            job = launch { history.execute(cancelling, strict) }
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(HistoryState(project, 0, 0), history.state)
            assertEquals("one", valueOf(history.execute(rename("one"), strict)).name)
        }

    @Test
    fun `given a caller waiting for the mutex when it is cancelled then its command never runs`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val blocking =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> {
                        gate.await()
                        return DomainResult.Success(Unit)
                    }

                    override fun apply(project: Project) = DomainResult.Success(project.copy(name = "first"))
                }
            val history = historyOf(project)
            launch { history.execute(blocking, strict) }
            runCurrent()
            val waiting = launch { history.execute(rename("second", events), strict) }
            runCurrent()

            waiting.cancel()
            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(events.isEmpty())
            assertEquals(HistoryState(project.copy(name = "first"), 1, 0), history.state)
        }

    @Test
    fun `given a reconcile queued behind an execute when the execute runs then it finishes with the old tolerance`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val evaluator = OpenEvaluator()
            val resolver = RecordingResolver()
            val slow =
                object : ProjectCommand {
                    override suspend fun verify(
                        project: Project,
                        context: CommandContext,
                    ): DomainResult<Unit> {
                        gate.await()
                        context.evaluate(project.rooms.first())
                        return DomainResult.Success(Unit)
                    }

                    override fun apply(project: Project) = DomainResult.Success(project.copy(name = "one"))
                }
            val history = historyOf(project, evaluator, resolver)

            launch { history.execute(slow, strict) }
            runCurrent()
            launch { history.reconcile(loose) }
            runCurrent()
            assertTrue(resolver.calls.isEmpty())
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(strict), evaluator.tolerances)
            assertEquals(listOf(strict, loose), resolver.calls.map { it.second })
            assertEquals(
                "one",
                resolver.calls
                    .last()
                    .first.name,
            )
        }

    @Test
    fun `given a history state when created with negative depths then it is rejected`() {
        val result = runCatching { HistoryState(project, undoDepth = -1, redoDepth = 0) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { HistoryState(project, 0, -1) }.exceptionOrNull() is IllegalArgumentException)
    }
}
