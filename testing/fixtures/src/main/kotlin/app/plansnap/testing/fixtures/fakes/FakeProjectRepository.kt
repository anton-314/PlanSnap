package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream

/**
 * In-memory [ProjectRepository] that follows the contract, including its error paths:
 * `getProject`, `renameProject`, `deleteProject` and `duplicateProject` fail with `NotFound(PROJECT)` for an unknown
 * id, `createProject` fails with `RuleViolation(DUPLICATE_ID)` for a known id, `duplicateProject` copies with new
 * ids and all internal references redirected, and `importProject` decodes through `importDecoder` and keeps ids
 * unless the project id or a room id is already stored, in which case it stores a copy. Technical failures
 * (`DomainError.Storage`) are triggered through [failures] before any state changes.
 *
 * [observeProjects] lists the projects in insertion order. The contract does not define an order.
 *
 * @param ids source of ids for copies.
 * @param clock source of the creation time of copies.
 * @param initialProjects projects that are stored from the start, with unique ids.
 * @param importDecoder replaces decoding and validation of `:core:data`. Rejects every document by default.
 * @param drafts if set, `deleteProject` also removes the draft of the project from it, as the contract demands. A
 * failure of the draft removal (injected through its `failures`) fails the deletion and leaves project and draft
 * unchanged.
 */
class FakeProjectRepository(
    private val ids: IdGenerator = SequentialIdGenerator("copy"),
    private val clock: EpochClock = FakeEpochClock(),
    initialProjects: List<Project> = emptyList(),
    private val importDecoder: (InputStream) -> DomainResult<Project> = {
        DomainResult.Failure(DomainError.Document(DocumentProblem.MALFORMED))
    },
    private val drafts: FakeEditorDraftRepository? = null,
) : ProjectRepository {
    /** Operations of the repository that [failures] can fail. */
    enum class Operation {
        /** [ProjectRepository.getProject]. */
        GET,

        /** [ProjectRepository.createProject]. */
        CREATE,

        /** [ProjectRepository.renameProject]. */
        RENAME,

        /** [ProjectRepository.deleteProject]. */
        DELETE,

        /** [ProjectRepository.duplicateProject]. */
        DUPLICATE,

        /** [ProjectRepository.importProject]. */
        IMPORT,
    }

    /** Triggers technical failures for single operations. */
    val failures: FailureInjector<Operation> = FailureInjector()

    private val lock = Any()
    private val stored = LinkedHashMap<ProjectId, Project>()
    private val summaries = MutableStateFlow<ImmutableList<ProjectSummary>>(persistentListOf())

    init {
        initialProjects.forEach { project ->
            require(project.id !in stored) { "Duplicate project id in initial projects: ${project.id.value}" }
            stored[project.id] = project
        }
        summaries.value = summariesOf(stored.values)
    }

    /** Snapshot of the stored projects in insertion order. */
    val projects: List<Project> get() = synchronized(lock) { stored.values.toList() }

    override fun observeProjects(): Flow<ImmutableList<ProjectSummary>> = summaries

    override suspend fun getProject(id: ProjectId): DomainResult<Project> =
        guarded(Operation.GET) { stored[id]?.let { DomainResult.Success(it) } ?: notFound(id) }

    override suspend fun createProject(project: Project): DomainResult<Unit> =
        guarded(Operation.CREATE) {
            if (project.id in stored) {
                DomainResult.Failure(DomainError.RuleViolation(DomainRule.DUPLICATE_ID))
            } else {
                stored[project.id] = project
                DomainResult.Success(Unit)
            }
        }

    override suspend fun renameProject(
        id: ProjectId,
        name: String,
    ): DomainResult<Unit> =
        guarded(Operation.RENAME) {
            val project = stored[id]
            if (project == null) {
                notFound(id)
            } else {
                stored[id] = project.copy(name = name)
                DomainResult.Success(Unit)
            }
        }

    override suspend fun deleteProject(id: ProjectId): DomainResult<Unit> =
        guarded(Operation.DELETE) {
            if (id !in stored) {
                notFound(id)
            } else {
                val draftFailure = drafts?.clearForProjectDelete(id)
                if (draftFailure != null) {
                    DomainResult.Failure(draftFailure)
                } else {
                    stored.remove(id)
                    DomainResult.Success(Unit)
                }
            }
        }

    override suspend fun duplicateProject(id: ProjectId): DomainResult<ProjectId> =
        guarded(Operation.DUPLICATE) {
            val source = stored[id]
            if (source == null) {
                notFound(id)
            } else {
                DomainResult.Success(storeCopy(source))
            }
        }

    override suspend fun importProject(input: InputStream): DomainResult<ProjectId> =
        guarded(Operation.IMPORT) {
            importDecoder(input).flatMap { project ->
                val takenRoomIds =
                    stored.values.flatMapTo(mutableSetOf<RoomId>()) { other -> other.rooms.map { it.id } }
                val collides = project.id in stored || project.rooms.any { it.id in takenRoomIds }
                if (collides) {
                    DomainResult.Success(storeCopy(project))
                } else {
                    stored[project.id] = project
                    DomainResult.Success(project.id)
                }
            }
        }

    /**
     * Stores [project] as seen by `ProjectSaver`: metadata comes from [project]. Only ids in [changedRooms] are
     * applied: an id that is in [project] replaces the stored room or adds it (after the stored rooms, in project
     * order), an id that is no longer in [project] deletes the stored room. Rooms that are not reported keep their
     * stored version, a new unreported room is not stored and an unreported removed room stays stored. A reported id
     * that is neither in [project] nor stored changes nothing.
     */
    internal fun saveChanges(
        project: Project,
        changedRooms: Set<RoomId>,
        updatedAtEpochMs: Long,
    ): DomainResult<Long> =
        synchronized(lock) {
            val existing = stored[project.id]
            if (existing == null) {
                notFound(project.id)
            } else {
                val submitted = project.rooms.associateBy { it.id }
                val storedIds = existing.rooms.map { it.id }.toSet()
                val kept =
                    existing.rooms.mapNotNull { room ->
                        if (room.id in changedRooms) submitted[room.id] else room
                    }
                val added = project.rooms.filter { it.id in changedRooms && it.id !in storedIds }
                stored[project.id] =
                    project.copy(rooms = (kept + added).toImmutableList(), updatedAtEpochMs = updatedAtEpochMs)
                summaries.value = summariesOf(stored.values)
                DomainResult.Success(updatedAtEpochMs)
            }
        }

    private fun storeCopy(source: Project): ProjectId {
        val copy = source.copyWithNewIds(ids, clock.nowEpochMillis())
        stored[copy.id] = copy
        return copy.id
    }

    private inline fun <T> guarded(
        operation: Operation,
        block: () -> DomainResult<T>,
    ): DomainResult<T> {
        failures.take(operation)?.let { return DomainResult.Failure(it) }
        return synchronized(lock) { block().also { summaries.value = summariesOf(stored.values) } }
    }
}

private fun summariesOf(projects: Collection<Project>): ImmutableList<ProjectSummary> =
    projects
        .map { ProjectSummary(it.id, it.name, it.rooms.size, it.createdAtEpochMs, it.updatedAtEpochMs) }
        .toImmutableList()

private fun notFound(id: ProjectId): DomainResult.Failure =
    DomainResult.Failure(DomainError.NotFound(EntityKind.PROJECT, id.value))
