package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.model.ProjectId

/**
 * In-memory [EditorDraftRepository] with one draft per project. [getDraft] returns `null` if none exists,
 * [saveDraft] replaces the draft of its project, [clearDraft] succeeds even if there is nothing to remove.
 * Storage failures are triggered through [failures] and leave the stored drafts unchanged.
 */
class FakeEditorDraftRepository : EditorDraftRepository {
    /** Operations that [failures] can fail. */
    enum class Operation {
        /** [EditorDraftRepository.getDraft]. */
        GET,

        /** [EditorDraftRepository.saveDraft]. */
        SAVE,

        /** [EditorDraftRepository.clearDraft]. */
        CLEAR,
    }

    /** Triggers technical failures for single operations. */
    val failures: FailureInjector<Operation> = FailureInjector()

    private val drafts = LinkedHashMap<ProjectId, EditorDraft>()

    /** Snapshot of the stored drafts. */
    val storedDrafts: Map<ProjectId, EditorDraft> get() = synchronized(drafts) { drafts.toMap() }

    override suspend fun getDraft(projectId: ProjectId): DomainResult<EditorDraft?> =
        failures.take(Operation.GET)?.let { DomainResult.Failure(it) }
            ?: DomainResult.Success(synchronized(drafts) { drafts[projectId] })

    override suspend fun saveDraft(draft: EditorDraft): DomainResult<Unit> =
        failures.take(Operation.SAVE)?.let { DomainResult.Failure(it) }
            ?: DomainResult.Success(Unit).also { synchronized(drafts) { drafts[draft.projectId] = draft } }

    override suspend fun clearDraft(projectId: ProjectId): DomainResult<Unit> =
        failures.take(Operation.CLEAR)?.let { DomainResult.Failure(it) }
            ?: DomainResult.Success(Unit).also { synchronized(drafts) { drafts.remove(projectId) } }

    /**
     * Removes the draft as part of `ProjectRepository.deleteProject`. Honours an injected [Operation.CLEAR] failure
     * and returns it without changing anything, otherwise removes the draft and returns `null`.
     */
    internal fun clearForProjectDelete(projectId: ProjectId): DomainError? =
        failures.take(Operation.CLEAR) ?: run {
            synchronized(drafts) { drafts.remove(projectId) }
            null
        }
}
