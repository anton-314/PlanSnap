package app.plansnap.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.flatMap
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.domain.validation.ProjectValidator
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * [ProjectRepository] on top of the Room database (ARCHITECTURE section 7).
 *
 * Every operation runs on the IO dispatcher. Writes are single transactions, so a failure leaves no partial state.
 * Technical failures are returned as `DomainError.Storage`, a store that cannot be decoded fails with `READ_FAILED`.
 * A duplicate keeps the name of the original. Duplicates and imported collisions get fresh ids from [ids] and
 * [clock] as creation and change time.
 *
 * @param database opened database.
 * @param codec codec of the stored room documents and of imported project documents.
 * @param validator check applied to every imported project before it is stored.
 * @param clock source of times for renames and copies.
 * @param ids source of ids for copies.
 * @param dispatchers source of the dispatcher for storage access.
 */
class RoomProjectRepository(
    private val database: PlanSnapDatabase,
    private val codec: DocumentCodec,
    private val validator: ProjectValidator,
    private val clock: EpochClock,
    private val ids: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : ProjectRepository {
    private val projects = database.projectDao()
    private val rooms = database.roomDao()

    override fun observeProjects(): Flow<ImmutableList<ProjectSummary>> =
        projects
            .observeSummaries()
            .map { rows ->
                rows
                    .map {
                        ProjectSummary(
                            ProjectId(it.id),
                            it.name,
                            it.roomCount,
                            it.createdAtEpochMs,
                            it.updatedAtEpochMs,
                        )
                    }.toImmutableList()
            }.flowOn(dispatchers.io)

    override suspend fun getProject(id: ProjectId): DomainResult<Project> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.READ_FAILED) {
                val snapshot =
                    database.withTransaction {
                        projects.get(id.value)?.let { it to rooms.getByProject(id.value) }
                    }
                if (snapshot == null) notFound(id) else assembleProject(snapshot.first, snapshot.second, codec)
            }
        }

    override suspend fun createProject(project: Project): DomainResult<Unit> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.WRITE_FAILED) {
                try {
                    projects.create(project.toEntity(), project.roomEntities(codec))
                    DomainResult.Success(Unit)
                } catch (_: SQLiteConstraintException) {
                    DomainResult.Failure(DomainError.RuleViolation(DomainRule.DUPLICATE_ID))
                }
            }
        }

    override suspend fun renameProject(
        id: ProjectId,
        name: String,
    ): DomainResult<Unit> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.WRITE_FAILED) {
                if (projects.rename(id.value, name, clock.nowEpochMillis()) == 1) {
                    DomainResult.Success(Unit)
                } else {
                    notFound(id)
                }
            }
        }

    override suspend fun deleteProject(id: ProjectId): DomainResult<Unit> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.WRITE_FAILED) {
                if (projects.delete(id.value) == 1) DomainResult.Success(Unit) else notFound(id)
            }
        }

    override suspend fun duplicateProject(id: ProjectId): DomainResult<ProjectId> =
        getProject(id).flatMap { source ->
            withContext(dispatchers.io) {
                storageResult(StorageProblem.WRITE_FAILED) { DomainResult.Success(insertCopy(source)) }
            }
        }

    override suspend fun importProject(input: InputStream): DomainResult<ProjectId> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.READ_FAILED) { codec.decodeProject(input) }
                .flatMap { project ->
                    storageResult(StorageProblem.READ_FAILED) { validator.validate(project) }
                        .flatMap { DomainResult.Success(project) }
                }.flatMap { project ->
                    storageResult(StorageProblem.WRITE_FAILED) { DomainResult.Success(insertImported(project)) }
                }
        }

    /** Stores [project] under its ids, or as a copy if the project id or a room id is taken, in one transaction. */
    private suspend fun insertImported(project: Project): ProjectId =
        database.withTransaction {
            val roomIds = project.rooms.map { it.id.value }
            val collides =
                projects.get(project.id.value) != null ||
                    roomIds.chunked(ROOM_ID_QUERY_CHUNK).any { rooms.existingIds(it).isNotEmpty() }
            if (collides) {
                insertCopy(project)
            } else {
                projects.create(project.toEntity(), project.roomEntities(codec))
                project.id
            }
        }

    private suspend fun insertCopy(source: Project): ProjectId {
        val copy = source.copyWithNewIds(ids, clock.nowEpochMillis())
        projects.create(copy.toEntity(), copy.roomEntities(codec))
        return copy.id
    }

    private fun notFound(id: ProjectId): DomainResult.Failure =
        DomainResult.Failure(DomainError.NotFound(EntityKind.PROJECT, id.value))

    private companion object {
        /** Stays below SQLite's default limit of bound variables. */
        const val ROOM_ID_QUERY_CHUNK = 500
    }
}
