package app.plansnap.feature.editor.state

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.domain.command.CommandHistory
import app.plansnap.core.domain.command.ProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.ReconcileOutcome
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import kotlinx.coroutines.flow.first

/**
 * Runs the confirmed work of the editor: commands, undo, redo, reconciling and saving (ARCHITECTURE section 3).
 *
 * Every function runs inside the ordered work queue of the `EditorStore`, in the application scope. The mutable fields
 * are therefore touched by one coroutine at a time.
 */
internal class CommandRunner(
    private val store: EditorStore,
    private val history: CommandHistory,
    private val factory: ProjectCommandFactory,
    private val saver: ProjectSaver,
    private val settings: SettingsRepository,
    private val evaluator: RoomEvaluator,
) {
    private val pendingRooms = mutableSetOf<RoomId>()
    private var needsSave = false

    /** Latest committed project of the history. */
    val project: Project get() = history.state.project

    /** Tolerance of the settings at the time of the call, never one remembered from a displayed evaluation. */
    suspend fun currentTolerance(): ClosureTolerance = settings.settings.first().tolerance

    /**
     * Executes [edit] as one undo step and saves the result. A failure is sent as [EditorEffect.Error], project and
     * history stay unchanged.
     */
    suspend fun execute(
        edit: ProjectEdit,
        tolerance: ClosureTolerance? = null,
    ): DomainResult<Project> {
        val before = project
        val result = history.execute(factory.create(edit), tolerance ?: currentTolerance())
        when (result) {
            is DomainResult.Success -> commit(before, result.value)
            is DomainResult.Failure -> store.send(EditorEffect.Error(result.error))
        }
        return result
    }

    /**
     * Accepts the adjustment of [roomId]. The room is evaluated again right now and the fresh proposal is what the
     * command receives, so a proposal that went stale since it was displayed is never applied. The command verifies
     * it once more under the history mutex (ADR 0019).
     */
    suspend fun acceptAdjustment(
        roomId: RoomId,
        confirmation: AdjustmentConfirmation,
    ) {
        val room = project.rooms.firstOrNull { it.id == roomId }
        if (room == null) {
            store.send(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, roomId.value)))
            return
        }
        val tolerance = currentTolerance()
        when (val fresh = evaluator.evaluate(room, tolerance)) {
            is DomainResult.Failure -> {
                store.send(EditorEffect.Error(fresh.error))
            }

            is DomainResult.Success -> {
                val evaluation = fresh.value
                if (evaluation is RoomEvaluation.NeedsAdjustment) {
                    execute(ProjectEdit.AcceptAdjustment(roomId, evaluation.proposal, confirmation), tolerance)
                } else {
                    store.send(EditorEffect.Error(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE)))
                }
            }
        }
    }

    /** Restores the previous state exactly and saves it. Does nothing without an undo step. */
    suspend fun undo() {
        val before = project
        history.undo()?.let { commit(before, it) }
    }

    /** Restores the next state exactly and saves it. Does nothing without a redo step. */
    suspend fun redo() {
        val before = project
        history.redo()?.let { commit(before, it) }
    }

    /** Resolves the placements for a changed [tolerance] and saves them if they changed. */
    suspend fun reconcile(tolerance: ClosureTolerance) {
        val before = project
        when (val result = history.reconcile(tolerance)) {
            is DomainResult.Failure -> {
                store.send(EditorEffect.Error(result.error))
            }

            is DomainResult.Success -> {
                if (result.value == ReconcileOutcome.PLACEMENTS_UPDATED) commit(before, project)
            }
        }
    }

    /** Stores the changes that a failed save left pending. */
    suspend fun retrySave() {
        if (needsSave) save()
    }

    private suspend fun commit(
        before: Project,
        after: Project,
    ) {
        pendingRooms += changedRooms(before, after)
        needsSave = true
        val state = history.state
        store.update {
            val selected = it.selectedRoomId?.takeIf { id -> after.rooms.any { room -> room.id == id } }
            it.copy(
                project = after,
                selectedRoomId = selected ?: after.rooms.firstOrNull()?.id,
                undoDepth = state.undoDepth,
                redoDepth = state.redoDepth,
                save = SaveStatus.Saving,
            )
        }
        save()
    }

    private suspend fun save() {
        val result = saver.save(project, pendingRooms.toSet())
        when (result) {
            is DomainResult.Success -> {
                pendingRooms.clear()
                needsSave = false
                store.update { it.copy(save = SaveStatus.Saved(result.value)) }
            }

            is DomainResult.Failure -> {
                store.update { it.copy(save = SaveStatus.Failed(result.error)) }
            }
        }
    }

    private fun changedRooms(
        before: Project,
        after: Project,
    ): Set<RoomId> {
        val old = before.rooms.associateBy { it.id }
        val new = after.rooms.associateBy { it.id }
        return buildSet {
            new.forEach { (id, room) -> if (old[id] != room) add(id) }
            old.keys.forEach { id -> if (id !in new) add(id) }
        }
    }
}
