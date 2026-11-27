package app.plansnap.feature.editor.state

import androidx.lifecycle.SavedStateHandle
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Holder of the editor state, the effects and the two ordered work queues (ARCHITECTURE sections 4 and 5).
 *
 * - `work` runs confirmed commands, undo, redo and saving one after the other in the [ApplicationScope], so call order
 *   is kept and leaving the editor never cancels a queued job. [close] only stops accepting new jobs.
 * - `draft` stores the running input, latest value wins.
 *
 * Every change of the input text, the selected room or the plan view is written to the [SavedStateHandle] on the main
 * dispatcher of the screen [scope], always with the latest state. A changed input text or room is also queued for the
 * draft in the database.
 */
@Suppress("LongParameterList") // One dependency or callback of the view model per parameter.
internal class EditorStore(
    initial: EditorUiState,
    private val handle: SavedStateHandle,
    private val drafts: EditorDraftRepository,
    applicationScope: ApplicationScope,
    override val scope: CoroutineScope,
    private val submitEdit: (ProjectEdit) -> Unit,
    private val submitBuiltEdit: ((Project) -> ProjectEdit, () -> Unit) -> Unit = { _, _ -> },
) : EditorHost {
    private val mutableState = MutableStateFlow(initial)
    private val effectChannel = Channel<EditorEffect>(Channel.UNLIMITED)
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val draft = Channel<DraftWrite>(Channel.CONFLATED)
    private val handleWrites = Channel<Unit>(Channel.CONFLATED)
    private val syncLock = Any()
    private var synced = InputSnapshot.of(initial)
    private var handleSynced = InputSnapshot.of(initial)

    override val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    /** One-time events for the screen. */
    val effects: Flow<EditorEffect> = effectChannel.receiveAsFlow()

    init {
        applicationScope.launch { for (job in work) job() }
        applicationScope.launch { for (write in draft) persist(write) }
        scope.launch { for (ignored in handleWrites) writeHandle(InputSnapshot.of(mutableState.value)) }
    }

    override fun update(transform: (EditorUiState) -> EditorUiState) {
        while (true) {
            val old = mutableState.value
            if (mutableState.compareAndSet(old, transform(old))) break
        }
        sync(persisted = null)
    }

    override fun submit(edit: ProjectEdit) = submitEdit(edit)

    override fun submitQueued(
        build: (Project) -> ProjectEdit,
        onCommitted: () -> Unit,
    ) = submitBuiltEdit(build, onCommitted)

    override fun send(effect: EditorEffect) {
        effectChannel.trySend(effect)
    }

    /**
     * Replaces the whole state after loading. [persisted] is the draft that the database already holds: the input is
     * only queued for the draft if it differs from it.
     */
    fun restore(
        state: EditorUiState,
        persisted: EditorDraft?,
    ) {
        mutableState.value = state
        sync(persisted = InputSnapshot.ofDraft(persisted, state))
    }

    /** Queues [job] behind all earlier jobs. It runs in the application scope even after [close]. */
    fun enqueue(job: suspend () -> Unit) {
        work.trySend(job)
    }

    /** Ends the screen: new jobs are refused, queued jobs and the last draft are still processed. */
    fun close() {
        work.close()
        draft.close()
        scope.cancel()
    }

    private fun sync(persisted: InputSnapshot?) =
        synchronized(syncLock) {
            val current = InputSnapshot.of(mutableState.value)
            val previous = persisted ?: synced
            handleWrites.trySend(Unit)
            val inputChanged =
                current.text != previous.text || (current.text.isNotEmpty() && current.roomId != previous.roomId)
            if (inputChanged && current.projectId != null) {
                draft.trySend(DraftWrite(current.projectId, current.roomId, current.text))
            }
            synced = current
        }

    /** Writes the changes to the [SavedStateHandle]. Runs on the main dispatcher only, with the latest state. */
    private fun writeHandle(current: InputSnapshot) {
        if (current.roomId != handleSynced.roomId) {
            handle[KEY_ROOM_ID] = current.roomId?.value
        }
        if (current.text != handleSynced.text) {
            handle[KEY_INPUT_TEXT] = current.text
        }
        if (current.view != handleSynced.view) {
            handle[KEY_PLAN_VIEW] = current.view?.let { doubleArrayOf(it.centerXMm, it.centerYMm, it.pixelsPerMm) }
        }
        handleSynced = current
    }

    private suspend fun persist(write: DraftWrite) {
        val result =
            if (write.text.isEmpty()) {
                drafts.clearDraft(write.projectId)
            } else {
                drafts.saveDraft(EditorDraft(write.projectId, write.roomId, write.text))
            }
        val error = (result as? DomainResult.Failure)?.error
        update { if (it.draftError == error) it else it.copy(draftError = error) }
    }

    private data class DraftWrite(
        val projectId: ProjectId,
        val roomId: RoomId?,
        val text: String,
    )

    private data class InputSnapshot(
        val projectId: ProjectId?,
        val roomId: RoomId?,
        val text: String,
        val view: PlanView?,
    ) {
        companion object {
            fun of(state: EditorUiState) =
                InputSnapshot(state.project?.id, state.selectedRoomId, state.inputText, state.view)

            fun ofDraft(
                draft: EditorDraft?,
                state: EditorUiState,
            ) = InputSnapshot(state.project?.id, draft?.roomId, draft?.inputText.orEmpty(), state.view)
        }
    }

    /** Keys of the [SavedStateHandle]. The project id is the navigation argument of the same name. */
    companion object {
        /** Navigation argument with the project id. */
        const val KEY_PROJECT_ID = "projectId"

        /** Selected room id. */
        const val KEY_ROOM_ID = "roomId"

        /** Running input text. */
        const val KEY_INPUT_TEXT = "inputText"

        /** Pan and zoom as `[centerX, centerY, pixelsPerMm]`. */
        const val KEY_PLAN_VIEW = "planView"
    }
}
