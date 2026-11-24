package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.common.result.map
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Project
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Committed state of the history. Replaced as a whole, so a reader never sees a half-updated history.
 *
 * Every stored project is a reference to an immutable value, so undo and redo restore exactly the same objects.
 */
private data class Snapshot(
    val project: Project,
    val past: PersistentList<Project>,
    val future: PersistentList<Project>,
) {
    fun toState(): HistoryState = HistoryState(project, past.size, future.size)

    /** New undo step: the current project is kept, [next] becomes current and the redo stack is discarded. */
    fun advanced(next: Project): Snapshot = Snapshot(next, past.adding(project), persistentListOf())

    fun steppedBack(): Snapshot? =
        past.lastOrNull()?.let { Snapshot(it, past.removingAt(past.lastIndex), future.adding(project)) }

    fun steppedForward(): Snapshot? =
        future.lastOrNull()?.let { Snapshot(it, past.adding(project), future.removingAt(future.lastIndex)) }
}

/**
 * Session-long [CommandHistory] with unbounded depth (ADR 0007, ADR 0015, ADR 0016, ADR 0019).
 *
 * Every operation takes one fair [Mutex], computes inside `withContext` on the default dispatcher while it still holds
 * the mutex and commits only after returning to the caller context, as one assignment of an immutable snapshot. The
 * commit is the last statement of an operation and cannot be cancelled, so a cancelled operation changes nothing.
 *
 * @param initial project at the start of the session.
 * @param evaluator evaluator that [ProjectCommand.verify] receives through its [CommandContext].
 * @param resolver resolver of dock placements after each command and for [reconcile].
 * @param dispatchers source of the default dispatcher for the computation.
 */
class DefaultCommandHistory(
    initial: Project,
    private val evaluator: RoomEvaluator,
    private val resolver: DockPlacementResolver,
    private val dispatchers: DispatcherProvider,
) : CommandHistory {
    private val mutex = Mutex()

    @Volatile
    private var snapshot = Snapshot(initial, persistentListOf(), persistentListOf())

    override val state: HistoryState get() = snapshot.toState()

    override suspend fun execute(
        command: ProjectCommand,
        tolerance: ClosureTolerance,
    ): DomainResult<Project> =
        mutex.withLock {
            val base = snapshot.project
            val computed =
                withContext(dispatchers.default) {
                    command
                        .verify(base, CommandContext(tolerance, evaluator))
                        .flatMap { command.apply(base) }
                        .flatMap { resolver.resolve(it, tolerance) }
                }
            currentCoroutineContext().ensureActive()
            if (computed is DomainResult.Success) snapshot = snapshot.advanced(computed.value)
            computed
        }

    override suspend fun reconcile(tolerance: ClosureTolerance): DomainResult<ReconcileOutcome> =
        mutex.withLock {
            val base = snapshot.project
            val resolved = withContext(dispatchers.default) { resolver.resolve(base, tolerance) }
            currentCoroutineContext().ensureActive()
            resolved.map { commitPlacements(base, it) }
        }

    private fun commitPlacements(
        base: Project,
        resolved: Project,
    ): ReconcileOutcome =
        if (resolved == base) {
            ReconcileOutcome.UNCHANGED
        } else {
            snapshot = snapshot.advanced(resolved)
            ReconcileOutcome.PLACEMENTS_UPDATED
        }

    override suspend fun undo(): Project? =
        mutex.withLock {
            snapshot.steppedBack()?.also { snapshot = it }?.project
        }

    override suspend fun redo(): Project? =
        mutex.withLock {
            snapshot.steppedForward()?.also { snapshot = it }?.project
        }
}
