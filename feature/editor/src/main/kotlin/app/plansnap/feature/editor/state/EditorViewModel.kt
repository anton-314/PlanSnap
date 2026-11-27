package app.plansnap.feature.editor.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.command.CommandHistory
import app.plansnap.core.domain.command.ProjectCommandFactory
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State layer of the plan editor (ARCHITECTURE sections 3 to 5). The screen sends [EditorIntent]s through [onIntent]
 * and renders [state]; one-time events arrive on [effects].
 *
 * - **Live state:** [state] is an immutable [EditorUiState]. Scene, evaluation and the preview of the typed wall are
 *   recomputed on every input, a newer input cancels the older evaluation.
 * - **Confirmed work:** commands, undo, redo and saving run in the injected [ApplicationScope], in call order, and are
 *   not cancelled by [onCleared].
 * - **Process death:** project id, room id, plan view and input text are in the [SavedStateHandle], the input also in
 *   the `EditorDraftRepository`. Confirmed walls are stored by the `ProjectSaver`.
 * - **Sub-functions:** intents that are no [CoreIntent] are offered to the [EditorHandler]s.
 *
 * Expected failures are never swallowed: a failed load is [EditorLoadState.Failed], a failed save is
 * [SaveStatus.Failed], a failed draft is [EditorUiState.draftError], everything else is an [EditorEffect.Error].
 */
@HiltViewModel
class EditorViewModel
    @Suppress("LongParameterList") // Constructor injection needs every dependency as its own parameter.
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val projects: ProjectRepository,
        private val drafts: EditorDraftRepository,
        private val settings: SettingsRepository,
        private val saver: ProjectSaver,
        private val historyFactory: @JvmSuppressWildcards (Project) -> CommandHistory,
        private val commandFactory: ProjectCommandFactory,
        private val evaluator: RoomEvaluator,
        private val sceneBuilder: SceneBuilder,
        private val ids: IdGenerator,
        private val dispatchers: DispatcherProvider,
        applicationScope: ApplicationScope,
        private val handlers: Set<@JvmSuppressWildcards EditorHandler>,
    ) : ViewModel() {
        private val screenScope = CoroutineScope(SupervisorJob() + dispatchers.main)
        private val store =
            EditorStore(
                initial = EditorUiState(),
                handle = savedStateHandle,
                drafts = drafts,
                applicationScope = applicationScope,
                scope = screenScope,
                submitEdit = { edit -> session?.submit(edit) ?: rejectUnloaded() },
                submitBuiltEdit = { build, onCommitted ->
                    session?.submitQueued(build, onCommitted) ?: rejectUnloaded()
                },
            )

        @Volatile
        private var session: EditorSession? = null

        /** Current state of the editor. */
        val state: StateFlow<EditorUiState> = store.state

        /** One-time events, to be collected while the screen is visible. */
        val effects: Flow<EditorEffect> = store.effects

        init {
            load()
        }

        /** Handles [intent]. Must be called on the main thread. */
        fun onIntent(intent: EditorIntent) {
            val open = session
            when {
                intent is ReloadProject -> reload()
                intent is CoreIntent && open != null -> open.handle(intent)
                intent is CoreIntent -> rejectUnloaded()
                handlers.any { it.handle(intent, store) } -> Unit
                else -> error("No handler for editor intent $intent")
            }
        }

        override fun onCleared() {
            store.close()
        }

        private fun reload() {
            if (store.state.value.load is EditorLoadState.Failed) {
                store.update { it.copy(load = EditorLoadState.Loading) }
                load()
            }
        }

        private fun load() {
            screenScope.launch {
                val projectId =
                    savedStateHandle
                        .get<String>(
                            EditorStore.KEY_PROJECT_ID,
                        )?.takeIf { it.isNotBlank() }
                        ?.let(::ProjectId)
                if (projectId == null) {
                    fail(DomainError.NotFound(EntityKind.PROJECT, ""))
                } else {
                    when (val loaded = projects.getProject(projectId)) {
                        is DomainResult.Failure -> fail(loaded.error)
                        is DomainResult.Success -> open(loaded.value)
                    }
                }
            }
        }

        private suspend fun open(project: Project) {
            val storedDraft = drafts.getDraft(project.id)
            val draft = (storedDraft as? DomainResult.Success)?.value
            val current = settings.settings.first()
            store.restore(
                restoredState(project, draft, current).copy(draftError = (storedDraft as? DomainResult.Failure)?.error),
                draft,
            )
            val history = historyFactory(project)
            val runner = CommandRunner(store, history, commandFactory, saver, settings, evaluator)
            val pipelines = EvaluationPipelines(store, evaluator, sceneBuilder, commandFactory, dispatchers)
            session = EditorSession(store, runner, pipelines, settings, ids).also { it.start() }
        }

        private fun restoredState(
            project: Project,
            draft: EditorDraft?,
            current: AppSettings,
        ): EditorUiState {
            val roomCandidates =
                listOf(
                    savedStateHandle.get<String>(EditorStore.KEY_ROOM_ID)?.takeIf { it.isNotBlank() },
                    draft?.roomId?.value,
                )
            val roomId =
                roomCandidates.firstNotNullOfOrNull { id -> project.rooms.firstOrNull { it.id.value == id }?.id }
                    ?: project.rooms.firstOrNull()?.id
            return EditorUiState(
                load = EditorLoadState.Loaded,
                project = project,
                selectedRoomId = roomId,
                inputText = savedStateHandle.get<String>(EditorStore.KEY_INPUT_TEXT) ?: draft?.inputText.orEmpty(),
                settings = current,
                view = savedStateHandle.get<DoubleArray>(EditorStore.KEY_PLAN_VIEW)?.toPlanView(),
            )
        }

        private fun fail(error: DomainError) {
            store.update { it.copy(load = EditorLoadState.Failed(error)) }
        }

        private fun rejectUnloaded() {
            val id = savedStateHandle.get<String>(EditorStore.KEY_PROJECT_ID).orEmpty()
            store.send(EditorEffect.Error(DomainError.NotFound(EntityKind.PROJECT, id)))
        }

        private fun DoubleArray.toPlanView(): PlanView? =
            if (size == PLAN_VIEW_SIZE && all { it.isFinite() } &&
                this[2] > 0.0
            ) {
                PlanView(this[0], this[1], this[2])
            } else {
                null
            }

        private companion object {
            const val PLAN_VIEW_SIZE = 3
        }
    }
