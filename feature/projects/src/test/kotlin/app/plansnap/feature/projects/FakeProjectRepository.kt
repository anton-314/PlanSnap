package app.plansnap.feature.projects

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.InputStream

/** Recording [ProjectRepository] for ViewModel tests. Every write operation fails with [failure] if it is set. */
class FakeProjectRepository(
    initial: List<ProjectSummary> = emptyList(),
) : ProjectRepository {
    private val projects = MutableStateFlow(initial)

    /** Failure returned by every write operation, or `null` for success. */
    var failure: DomainError? = null

    /** If set, [observeProjects] throws this on collection once. */
    var observeFailure: Throwable? = null

    /** Result of [importProject] when [failure] is not set. */
    var importResult: DomainResult<ProjectId> = DomainResult.Success(ProjectId("imported"))

    val created = mutableListOf<Project>()
    val renamed = mutableListOf<Pair<ProjectId, String>>()
    val deleted = mutableListOf<ProjectId>()
    val duplicated = mutableListOf<ProjectId>()
    val importedBytes = mutableListOf<ByteArray>()

    fun emit(list: List<ProjectSummary>) {
        projects.value = list
    }

    override fun observeProjects(): Flow<ImmutableList<ProjectSummary>> =
        flow {
            observeFailure?.let {
                observeFailure = null
                throw it
            }
            projects.collect { emit(it.toImmutableList()) }
        }

    override suspend fun getProject(id: ProjectId): DomainResult<Project> =
        DomainResult.Failure(DomainError.NotFound(EntityKind.PROJECT, id.value))

    override suspend fun createProject(project: Project): DomainResult<Unit> = write { created += project }

    override suspend fun renameProject(
        id: ProjectId,
        name: String,
    ): DomainResult<Unit> = write { renamed += id to name }

    override suspend fun deleteProject(id: ProjectId): DomainResult<Unit> = write { deleted += id }

    override suspend fun duplicateProject(id: ProjectId): DomainResult<ProjectId> =
        failure?.let { DomainResult.Failure(it) } ?: run {
            duplicated += id
            DomainResult.Success(ProjectId("copy-of-${id.value}"))
        }

    override suspend fun importProject(input: InputStream): DomainResult<ProjectId> {
        failure?.let { return DomainResult.Failure(it) }
        importedBytes += input.readBytes()
        return importResult
    }

    private inline fun write(action: () -> Unit): DomainResult<Unit> =
        failure?.let { DomainResult.Failure(it) } ?: run {
            action()
            DomainResult.Success(Unit)
        }
}

/** Creates a summary for tests. */
fun summary(
    id: String,
    name: String = "Project $id",
    rooms: Int = 0,
    updated: Long = 1_000L,
): ProjectSummary = ProjectSummary(ProjectId(id), name, rooms, createdAtEpochMs = 0L, updatedAtEpochMs = updated)
