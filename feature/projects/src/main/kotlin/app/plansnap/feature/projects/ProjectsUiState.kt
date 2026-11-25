package app.plansnap.feature.projects

import androidx.compose.runtime.Immutable
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.model.ProjectId
import kotlinx.collections.immutable.ImmutableList

/** Failure categories the project list shows to the user. The screen maps each case to a string resource. */
enum class ProjectsError {
    /** The project list or a project could not be read. */
    READ_FAILED,

    /** A change could not be written. */
    WRITE_FAILED,

    /** The device has no free space. */
    NO_SPACE,

    /** The project no longer exists. */
    NOT_FOUND,

    /** The entered name was rejected. */
    INVALID_NAME,

    /** The import file is not a valid project file. */
    IMPORT_MALFORMED,

    /** The import file was written by a newer version of the app. */
    IMPORT_UNSUPPORTED_VERSION,

    /** The import file is too large. */
    IMPORT_TOO_LARGE,

    /** The import file contains values that are not permitted. */
    IMPORT_INVALID_VALUES,

    /** Any other failure. */
    UNKNOWN,
}

/** Content of the project list. */
sealed interface ProjectListState {
    /** The first list has not arrived yet; the screen shows a skeleton. */
    data object Loading : ProjectListState

    /** The list failed to load; the screen shows [error] and offers to try again. */
    @Immutable
    data class Failed(
        val error: ProjectsError,
    ) : ProjectListState

    /** The list is loaded, newest change first. An empty list shows the empty state. */
    @Immutable
    data class Loaded(
        val projects: ImmutableList<ProjectSummary>,
    ) : ProjectListState
}

/** The dialog that is currently open, if any. */
sealed interface ProjectsDialog {
    /** No dialog. */
    data object None : ProjectsDialog

    /** Name entry for a new project. */
    @Immutable
    data class Create(
        val name: String = "",
    ) : ProjectsDialog

    /** Name entry for renaming the project [id]. */
    @Immutable
    data class Rename(
        val id: ProjectId,
        val name: String,
    ) : ProjectsDialog

    /** Confirmation before deleting the project [id], which is called [name]. */
    @Immutable
    data class ConfirmDelete(
        val id: ProjectId,
        val name: String,
    ) : ProjectsDialog
}

/**
 * Complete state of the project list screen.
 *
 * @property list Content of the list.
 * @property dialog Open dialog.
 * @property error One-time failure message of the last action; cleared by [ProjectsViewModel.onErrorShown].
 * @property isImporting Whether an import is running.
 */
@Immutable
data class ProjectsUiState(
    val list: ProjectListState = ProjectListState.Loading,
    val dialog: ProjectsDialog = ProjectsDialog.None,
    val error: ProjectsError? = null,
    val isImporting: Boolean = false,
)

/** One-time effects of the project list. */
sealed interface ProjectsEvent {
    /** The project [id] was created and should be opened in the editor. */
    @Immutable
    data class OpenProject(
        val id: ProjectId,
    ) : ProjectsEvent
}
