package app.plansnap.core.data.repository

import androidx.room.withTransaction
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.data.db.EditorDraftEntity
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import kotlinx.coroutines.withContext

/**
 * [EditorDraftRepository] on top of the Room database, one draft per project that survives process death
 * (REQ-DATA-002).
 *
 * Saving a draft for a project that is not stored fails with `NotFound(PROJECT)`, for a room that is not part of
 * the project with `NotFound(ROOM)`. Deleting the project or the room deletes the draft. Technical failures are
 * returned as `DomainError.Storage`.
 *
 * @param database opened database.
 * @param clock source of the draft change time.
 * @param dispatchers source of the dispatcher for storage access.
 */
class RoomEditorDraftRepository(
    private val database: PlanSnapDatabase,
    private val clock: EpochClock,
    private val dispatchers: DispatcherProvider,
) : EditorDraftRepository {
    private val drafts = database.editorDraftDao()
    private val projects = database.projectDao()
    private val rooms = database.roomDao()

    override suspend fun getDraft(projectId: ProjectId): DomainResult<EditorDraft?> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.READ_FAILED) {
                val entity = drafts.get(projectId.value)
                DomainResult.Success(
                    entity?.let { EditorDraft(projectId, it.roomId?.let(::RoomId), it.inputText) },
                )
            }
        }

    override suspend fun saveDraft(draft: EditorDraft): DomainResult<Unit> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.WRITE_FAILED) {
                database.withTransaction { store(draft) }
            }
        }

    override suspend fun clearDraft(projectId: ProjectId): DomainResult<Unit> =
        withContext(dispatchers.io) {
            storageResult(StorageProblem.WRITE_FAILED) {
                drafts.delete(projectId.value)
                DomainResult.Success(Unit)
            }
        }

    private suspend fun store(draft: EditorDraft): DomainResult<Unit> {
        val projectId = draft.projectId.value
        val roomId = draft.roomId?.value
        return when {
            projects.get(projectId) == null -> {
                DomainResult.Failure(DomainError.NotFound(EntityKind.PROJECT, projectId))
            }

            roomId != null && rooms.get(roomId)?.projectId != projectId -> {
                DomainResult.Failure(DomainError.NotFound(EntityKind.ROOM, roomId))
            }

            else -> {
                drafts.upsert(EditorDraftEntity(projectId, roomId, draft.inputText, clock.nowEpochMillis()))
                DomainResult.Success(Unit)
            }
        }
    }
}
