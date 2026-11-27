package app.plansnap.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Opens the stream of the file the user picked for an import, or returns `null` if it cannot be opened.
 * The ViewModel closes the stream.
 */
fun interface ImportSource {
    /** Opens the stream. Runs on the IO dispatcher. */
    fun open(): InputStream?
}

/**
 * State holder of the project list (docs/UX.md section 1): create, open, rename, duplicate, delete with confirmation
 * and JSON import (REQ-DATA-001, REQ-DATA-004, REQ-DATA-007).
 */
@Suppress("TooManyFunctions") // One function per user intent of the screen.
@HiltViewModel
class ProjectsViewModel
    @Inject
    constructor(
        private val repository: ProjectRepository,
        private val dispatchers: DispatcherProvider,
        private val ids: IdGenerator,
        private val clock: EpochClock,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ProjectsUiState())
        private val eventChannel = Channel<ProjectsEvent>(Channel.BUFFERED)
        private var observeJob: Job? = null

        /** Current state of the screen. */
        val state: StateFlow<ProjectsUiState> = mutableState.asStateFlow()

        /** One-time effects such as opening a freshly created project. */
        val events: Flow<ProjectsEvent> = eventChannel.receiveAsFlow()

        init {
            observe()
        }

        /** Starts loading the list again after it failed. */
        fun onRetry() {
            mutableState.update { it.copy(list = ProjectListState.Loading) }
            observe()
        }

        /** Opens the name dialog for a new project. */
        fun onNewProject() = showDialog(ProjectsDialog.Create())

        /** Opens the rename dialog for [project]. */
        fun onRenameRequested(project: ProjectId) = showDialogForProject(project) { ProjectsDialog.Rename(project, it) }

        /** Opens the delete confirmation for [project]. */
        fun onDeleteRequested(project: ProjectId) =
            showDialogForProject(project) { ProjectsDialog.ConfirmDelete(project, it) }

        /** Updates the name typed into the create or rename dialog. */
        fun onNameChanged(name: String) {
            mutableState.update { current ->
                when (val dialog = current.dialog) {
                    is ProjectsDialog.Create -> current.copy(dialog = dialog.copy(name = name))
                    is ProjectsDialog.Rename -> current.copy(dialog = dialog.copy(name = name))
                    else -> current
                }
            }
        }

        /** Closes the open dialog without changing anything. */
        fun onDialogDismissed() = showDialog(ProjectsDialog.None)

        /** Applies the open dialog: creates, renames or deletes. A blank name is ignored. */
        fun onDialogConfirmed() {
            when (val dialog = mutableState.value.dialog) {
                ProjectsDialog.None -> {}

                is ProjectsDialog.Create -> {
                    dialog.name
                        .trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let(::create)
                }

                is ProjectsDialog.Rename -> {
                    dialog.name
                        .trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { rename(dialog.id, it) }
                }

                is ProjectsDialog.ConfirmDelete -> {
                    delete(dialog.id)
                }
            }
        }

        /** Duplicates [project]. */
        fun onDuplicate(project: ProjectId) {
            viewModelScope.launch(dispatchers.main) {
                reportFailure(repository.duplicateProject(project))
            }
        }

        /**
         * Imports the file behind [source]. The stream is closed afterwards. If the user cancelled the file dialog,
         * the screen does not call this function, so a cancellation is never reported as an error.
         */
        fun onImport(source: ImportSource) {
            if (mutableState.value.isImporting) return
            mutableState.update { it.copy(isImporting = true) }
            viewModelScope.launch(dispatchers.main) {
                val result = withContext(dispatchers.io) { importFrom(source) }
                mutableState.update { it.copy(isImporting = false) }
                reportFailure(result, duringImport = true)
            }
        }

        /** Clears the failure message after the screen showed it. */
        fun onErrorShown() {
            mutableState.update { it.copy(error = null) }
        }

        @Suppress("SwallowedException") // The cause is not shown to the user; every read error maps to READ_FAILED.
        private suspend fun importFrom(source: ImportSource): DomainResult<ProjectId> =
            try {
                val stream = source.open()
                if (stream == null) {
                    DomainResult.Failure(DomainError.Storage(StorageProblem.READ_FAILED))
                } else {
                    stream.use { repository.importProject(it) }
                }
            } catch (_: IOException) {
                DomainResult.Failure(DomainError.Storage(StorageProblem.READ_FAILED))
            }

        private fun observe() {
            observeJob?.cancel()
            observeJob =
                viewModelScope.launch(dispatchers.main) {
                    repository
                        .observeProjects()
                        .catch {
                            mutableState.update {
                                it.copy(
                                    list = ProjectListState.Failed(ProjectsError.READ_FAILED),
                                )
                            }
                        }.collect { projects ->
                            val sorted = projects.sortedByDescending { it.updatedAtEpochMs }.toImmutableList()
                            mutableState.update { it.copy(list = ProjectListState.Loaded(sorted)) }
                        }
                }
        }

        private fun create(name: String) {
            showDialog(ProjectsDialog.None)
            viewModelScope.launch(dispatchers.main) {
                val now = clock.nowEpochMillis()
                val project =
                    Project(
                        id = ProjectId(ids.newId()),
                        name = name,
                        defaultHeight = DEFAULT_HEIGHT,
                        // docs/UX.md section 3 step 1: a new project starts with its first room.
                        rooms = persistentListOf(Room(RoomId(ids.newId()))),
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                    )
                if (reportFailure(repository.createProject(project))) {
                    eventChannel.send(ProjectsEvent.OpenProject(project.id))
                }
            }
        }

        private fun rename(
            id: ProjectId,
            name: String,
        ) {
            showDialog(ProjectsDialog.None)
            viewModelScope.launch(dispatchers.main) { reportFailure(repository.renameProject(id, name)) }
        }

        private fun delete(id: ProjectId) {
            showDialog(ProjectsDialog.None)
            viewModelScope.launch(dispatchers.main) { reportFailure(repository.deleteProject(id)) }
        }

        private fun showDialog(dialog: ProjectsDialog) {
            mutableState.update { it.copy(dialog = dialog) }
        }

        private fun showDialogForProject(
            id: ProjectId,
            dialog: (name: String) -> ProjectsDialog,
        ) {
            val loaded = mutableState.value.list as? ProjectListState.Loaded ?: return
            val project = loaded.projects.firstOrNull { it.id == id } ?: return
            showDialog(dialog(project.name))
        }

        /** Stores the failure of [result] in the state and returns whether [result] succeeded. */
        private fun reportFailure(
            result: DomainResult<*>,
            duringImport: Boolean = false,
        ): Boolean =
            when (result) {
                is DomainResult.Success -> {
                    true
                }

                is DomainResult.Failure -> {
                    mutableState.update { it.copy(error = result.error.toProjectsError(duringImport)) }
                    false
                }
            }

        private companion object {
            /** Room height of a new project in millimetres (2.50 m); the editor can change it. */
            val DEFAULT_HEIGHT = Length(2_500L)
        }
    }

/** Maps a domain failure; in an import, rejected values and rules mean an invalid file, not an invalid name. */
internal fun DomainError.toProjectsError(duringImport: Boolean = false): ProjectsError =
    when (this) {
        is DomainError.Document -> problem.toProjectsError()
        is DomainError.Storage -> problem.toProjectsError()
        is DomainError.NotFound -> ProjectsError.NOT_FOUND
        is DomainError.InvalidValue -> rejectedValue(duringImport, ProjectsError.INVALID_NAME)
        is DomainError.RuleViolation -> rejectedValue(duringImport, ProjectsError.UNKNOWN)
        is DomainError.Export -> ProjectsError.UNKNOWN
    }

private fun DocumentProblem.toProjectsError(): ProjectsError =
    when (this) {
        DocumentProblem.MALFORMED -> ProjectsError.IMPORT_MALFORMED
        DocumentProblem.UNSUPPORTED_VERSION -> ProjectsError.IMPORT_UNSUPPORTED_VERSION
        DocumentProblem.TOO_LARGE -> ProjectsError.IMPORT_TOO_LARGE
        DocumentProblem.INVALID_VALUES -> ProjectsError.IMPORT_INVALID_VALUES
    }

private fun StorageProblem.toProjectsError(): ProjectsError =
    when (this) {
        StorageProblem.READ_FAILED -> ProjectsError.READ_FAILED
        StorageProblem.WRITE_FAILED -> ProjectsError.WRITE_FAILED
        StorageProblem.NO_SPACE -> ProjectsError.NO_SPACE
    }

private fun rejectedValue(
    duringImport: Boolean,
    otherwise: ProjectsError,
): ProjectsError = if (duringImport) ProjectsError.IMPORT_INVALID_VALUES else otherwise
