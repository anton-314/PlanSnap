package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId

/**
 * [ProjectSaver] that writes into a [FakeProjectRepository], so a save is visible through the repository.
 *
 * Follows the contract: `updatedAtEpochMs` is set from the [EpochClock] and returned, only the rooms in
 * `changedRooms` are written, a room id that is no longer in the project removes the stored room, and a failure
 * (injected through [failures]) leaves the stored project untouched. A project the repository does not know fails
 * with `NotFound(PROJECT)`, because the contract saves changes to a stored project and does not create one.
 *
 * @param repository store that receives the writes.
 * @param clock source of the new `updatedAtEpochMs`.
 */
class FakeProjectSaver(
    private val repository: FakeProjectRepository,
    private val clock: EpochClock,
) : ProjectSaver {
    /** Operations that [failures] can fail. */
    enum class Operation {
        /** [ProjectSaver.save]. */
        SAVE,
    }

    /** Triggers technical failures of [save]. */
    val failures: FailureInjector<Operation> = FailureInjector()

    private val recordedSaves = mutableListOf<Set<RoomId>>()

    /** The `changedRooms` argument of every call that reached the store, in order. */
    val savedRoomSets: List<Set<RoomId>> get() = synchronized(recordedSaves) { recordedSaves.toList() }

    override suspend fun save(
        project: Project,
        changedRooms: Set<RoomId>,
    ): DomainResult<Long> {
        failures.take(Operation.SAVE)?.let { return DomainResult.Failure(it) }
        val result = repository.saveChanges(project, changedRooms, clock.nowEpochMillis())
        if (result is DomainResult.Success) synchronized(recordedSaves) { recordedSaves += changedRooms.toSet() }
        return result
    }
}
