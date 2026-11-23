package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Project

/**
 * Pure, deterministic change of an immutable project (DOMAIN section 11, ADR 0007).
 *
 * A command never changes `updatedAtEpochMs`, reads no clock and does not compute dock placements (ADR 0015).
 */
fun interface ProjectCommand {
    /** Returns the changed project, or the expected failure. [project] itself is never modified. */
    fun apply(project: Project): DomainResult<Project>
}

/** Creates the executable command for a [ProjectEdit]. Equal edits yield commands with equal results. */
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
 * Session-long undo and redo over before and after references (ADR 0007, ADR 0015, ADR 0016). The history is not
 * persisted and its depth is unbounded.
 *
 * All suspending operations are main-safe and serialized in call order: evaluation runs on the default dispatcher while
 * no other operation can change the history. Cancellation before the commit leaves project and history unchanged.
 * The commit itself is a single atomic update.
 */
interface CommandHistory {
    /** Latest committed state, an atomic snapshot that is safe to read from any thread. */
    val state: HistoryState

    /**
     * Runs [ProjectCommand.apply] and then [DockPlacementResolver.resolve] as exactly one undo step and discards the
     * redo stack. If either step fails, project and history stay unchanged and the failure is returned.
     *
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

    /** Restores the stored previous state exactly, without recomputing. Returns `null` if there is no undo step. */
    suspend fun undo(): Project?

    /** Restores the stored next state exactly, without recomputing. Returns `null` if there is no redo step. */
    suspend fun redo(): Project?
}
