package app.plansnap.core.data.repository

import androidx.room.withTransaction
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.data.db.ProjectMetadataUpdate
import app.plansnap.core.data.db.RoomEntity
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.document.FormatVersion
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ProjectSaver] on top of the Room database (ARCHITECTURE section 5).
 *
 * Calls are serialized by a fair [Mutex], so a later save never overtakes an earlier one. Each save is one
 * transaction: a failure leaves metadata and rooms as stored before. Technical failures are returned as
 * `DomainError.Storage`. A project that is not stored fails with `NotFound(PROJECT)`, a reported room whose id
 * belongs to another project fails with `RuleViolation(DUPLICATE_ID)`.
 *
 * @param database opened database.
 * @param codec codec of the stored room documents.
 * @param clock source of the stored `updatedAtEpochMs`.
 * @param dispatchers source of the dispatcher for storage access.
 */
class RoomProjectSaver(
    private val database: PlanSnapDatabase,
    private val codec: DocumentCodec,
    private val clock: EpochClock,
    private val dispatchers: DispatcherProvider,
) : ProjectSaver {
    private val projects = database.projectDao()
    private val rooms = database.roomDao()
    private val mutex = Mutex()

    override suspend fun save(
        project: Project,
        changedRooms: Set<RoomId>,
    ): DomainResult<Long> =
        mutex.withLock {
            withContext(dispatchers.io) {
                storageResult(StorageProblem.WRITE_FAILED) {
                    database.withTransaction { write(project, changedRooms.mapTo(HashSet()) { it.value }) }
                }
            }
        }

    private suspend fun write(
        project: Project,
        changed: Set<String>,
    ): DomainResult<Long> {
        val projectId = project.id.value
        if (projects.get(projectId) == null) {
            return DomainResult.Failure(DomainError.NotFound(EntityKind.PROJECT, projectId))
        }
        val stored = rooms.getByProject(projectId)
        val storedIds = stored.mapTo(HashSet()) { it.id }
        val takenElsewhere =
            project.rooms.any { it.id.value in changed && it.id.value !in storedIds && rooms.get(it.id.value) != null }
        return if (takenElsewhere) {
            DomainResult.Failure(DomainError.RuleViolation(DomainRule.DUPLICATE_ID))
        } else {
            DomainResult.Success(commit(project, changed, stored))
        }
    }

    private suspend fun commit(
        project: Project,
        changed: Set<String>,
        stored: List<RoomEntity>,
    ): Long {
        val projectId = project.id.value
        val updatedAt = clock.nowEpochMillis()
        projects.updateMetadata(
            ProjectMetadataUpdate(
                id = projectId,
                name = project.name,
                defaultHeightMm = project.defaultHeight.mm.toDouble(),
                formatMajor = FormatVersion.CURRENT.major,
                formatMinor = FormatVersion.CURRENT.minor,
                updatedAtEpochMs = updatedAt,
            ),
        )
        val storedById = stored.associateBy { it.id }
        val inProject = project.rooms.mapTo(HashSet()) { it.id.value }
        stored.filter { it.id !in inProject && it.id in changed }.forEach { rooms.delete(projectId, it.id) }
        val ordered =
            project.rooms.mapNotNull { room ->
                if (room.id.value in changed) {
                    room.toEntity(project.id, 0, codec)
                } else {
                    storedById[room.id.value]
                }
            } + stored.filter { it.id !in inProject && it.id !in changed }
        val positioned = ordered.mapIndexed { position, row -> row.copy(position = position) }
        rooms.upsert(positioned.filter { storedById[it.id] != it })
        return updatedAt
    }
}
