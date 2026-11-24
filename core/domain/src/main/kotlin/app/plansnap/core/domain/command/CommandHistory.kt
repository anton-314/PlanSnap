package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room

/**
 * Read-only environment of [ProjectCommand.verify] (DOMAIN section 11, ADR 0019).
 *
 * It binds the tolerance of the running [CommandHistory.execute] to the evaluator, so that a precondition can only
 * evaluate against the tolerance of that operation.
 *
 * @property tolerance tolerance passed to [CommandHistory.execute], the current tolerance of the operation.
 * @param evaluator evaluator of the history. It already runs on the default dispatcher and is cancellable.
 */
class CommandContext(
    val tolerance: ClosureTolerance,
    private val evaluator: RoomEvaluator,
) {
    /**
     * Evaluates [room] against [tolerance] as specified in DOMAIN section 4.
     *
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun evaluate(room: Room): DomainResult<RoomEvaluation> = evaluator.evaluate(room, tolerance)
}

/**
 * Pure, deterministic change of an immutable project with an optional precondition (DOMAIN section 11, ADR 0007,
 * ADR 0019).
 *
 * A command never changes `updatedAtEpochMs`, reads no clock and does not compute dock placements (ADR 0015). A
 * condition that needs an evaluation or the tolerance belongs in [verify], never in [apply].
 */
fun interface ProjectCommand {
    /**
     * Checks the precondition of this command against the current [project] and [context].
     *
     * [CommandHistory.execute] calls it under the history mutex on the default dispatcher, before [apply], dock
     * resolution and commit, so that no other operation can change project or tolerance in between. Undo and redo
     * never call it. The default accepts every project. [project] is never modified.
     *
     * @return `Success(Unit)` if [apply] may run, otherwise the expected failure, which [CommandHistory.execute]
     * returns unchanged.
     * @throws kotlinx.coroutines.CancellationException if cancelled. Project and history then stay unchanged.
     */
    suspend fun verify(
        project: Project,
        context: CommandContext,
    ): DomainResult<Unit> = DomainResult.Success(Unit)

    /**
     * Returns the changed project, or the expected failure. [project] itself is never modified.
     *
     * Pure and deterministic: equal projects yield equal results, without evaluation, tolerance, clock or dispatcher.
     */
    fun apply(project: Project): DomainResult<Project>
}

/**
 * Creates the executable command for a [ProjectEdit]. Equal edits yield commands with equal results.
 *
 * The created command implements [ProjectCommand.verify] wherever DOMAIN section 11 names a precondition, at least
 * for [ProjectEdit.AcceptAdjustment]. Production code executes only commands from this factory.
 */
fun interface ProjectCommandFactory {
    /** Returns the command described by [edit]. */
    fun create(edit: ProjectEdit): ProjectCommand
}

/**
 * Recomputes the placements of docked rooms in topological order (DOMAIN section 10, ADR 0015).
 *
 * The displayed outline is authoritative: target shape for `CONFIRMED`, overlay for `ADJUSTED`, preview for
 * `NEEDS_ADJUSTMENT` and proposal for `PROPOSED`. A room keeps its last placement, including its dock link, if its host
 * or the room itself is `OPEN` or `CONFLICT`, because no outline exists to align.
 */
fun interface DockPlacementResolver {
    /**
     * Returns [project] with resolved placements. Only placements may differ from the input.
     *
     * Runs on the default dispatcher and is cancellable (ADR 0016).
     */
    suspend fun resolve(
        project: Project,
        tolerance: ClosureTolerance,
    ): DomainResult<Project>
}

/**
 * Snapshot of the history.
 *
 * @property project current project.
 * @property undoDepth available undo steps, not negative.
 * @property redoDepth available redo steps, not negative.
 */
data class HistoryState(
    val project: Project,
    val undoDepth: Int,
    val redoDepth: Int,
) {
    init {
        require(undoDepth >= 0) { "Undo depth must not be negative: $undoDepth" }
        require(redoDepth >= 0) { "Redo depth must not be negative: $redoDepth" }
    }

    /** Whether an undo step exists. */
    val canUndo: Boolean get() = undoDepth > 0

    /** Whether a redo step exists. */
    val canRedo: Boolean get() = redoDepth > 0
}

/** Result of [CommandHistory.reconcile]. */
enum class ReconcileOutcome {
    /** No placement changed. No undo step was created and the redo stack is intact. */
    UNCHANGED,

    /** Placements changed. Exactly one undo step was created and the redo stack was discarded. */
    PLACEMENTS_UPDATED,
}

/**
 * Session-long undo and redo over before and after references (ADR 0007, ADR 0015, ADR 0016, ADR 0019). The history
 * is not persisted and its depth is unbounded.
 *
 * All suspending operations are main-safe and serialized in call order by one fair mutex. An operation takes the mutex,
 * computes inside `withContext` on the default dispatcher while still holding it, returns to the caller context and
 * commits only then, under the same mutex, as a single atomic update (ADR 0016). Cancellation before the commit leaves
 * project and history unchanged.
 *
 * Confirmed commands run in the application scope, so that leaving the editor does not cancel them. Only previews and
 * evaluations without a commit are cancellable by the screen.
 */
interface CommandHistory {
    /** Latest committed state, an atomic snapshot that is safe to read from any thread. */
    val state: HistoryState

    /**
     * Runs [ProjectCommand.verify], [ProjectCommand.apply] and then [DockPlacementResolver.resolve] as exactly one undo
     * step and discards the redo stack. If any step fails, project and history stay unchanged and the first failure is
     * returned.
     *
     * All three steps run under the history mutex inside one `withContext` on the default dispatcher, in this order
     * and on the same committed project. `verify` receives a [CommandContext] with [tolerance] and the evaluator of
     * the history. It cannot be skipped: a precondition that fails never reaches `apply` (ADR 0019).
     *
     * @param tolerance current tolerance, read by the caller from the settings at the time of the call, never one
     * captured together with a displayed evaluation.
     * @throws kotlinx.coroutines.CancellationException if cancelled. Project and history then stay unchanged.
     */
    suspend fun execute(
        command: ProjectCommand,
        tolerance: ClosureTolerance,
    ): DomainResult<Project>

    /**
     * Resolves placements for a changed [tolerance].
     *
     * Creates exactly one undo step if placements change and none otherwise. On failure, project and history stay
     * unchanged.
     *
     * @throws kotlinx.coroutines.CancellationException if cancelled. Project and history then stay unchanged.
     */
    suspend fun reconcile(tolerance: ClosureTolerance): DomainResult<ReconcileOutcome>

    /**
     * Restores the stored previous state exactly, without recomputing and without [ProjectCommand.verify]. Returns
     * `null` if there is no undo step.
     */
    suspend fun undo(): Project?

    /**
     * Restores the stored next state exactly, without recomputing and without [ProjectCommand.verify], also after a
     * tolerance change (ADR 0019). Returns `null` if there is no redo step.
     */
    suspend fun redo(): Project?
}
