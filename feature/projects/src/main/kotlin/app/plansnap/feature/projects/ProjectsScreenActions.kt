package app.plansnap.feature.projects

import androidx.compose.runtime.Immutable
import app.plansnap.core.model.ProjectId

/**
 * User actions of the project list screen.
 *
 * @property onOpenProject Opens a project in the editor.
 * @property onOpenSettings Opens the settings.
 * @property onNewProject Starts creating a project.
 * @property onImport Starts the import file dialog.
 * @property onRetry Reloads the list after a failure.
 * @property onRename Starts renaming a project.
 * @property onDuplicate Duplicates a project.
 * @property onDelete Starts deleting a project; the confirmation follows.
 * @property onNameChanged Reports the text of the name field.
 * @property onDialogConfirmed Confirms the open dialog.
 * @property onDialogDismissed Dismisses the open dialog.
 * @property onErrorShown Reports that the failure message was shown.
 */
@Immutable
data class ProjectsScreenActions(
    val onOpenProject: (ProjectId) -> Unit,
    val onOpenSettings: () -> Unit,
    val onNewProject: () -> Unit,
    val onImport: () -> Unit,
    val onRetry: () -> Unit,
    val onRename: (ProjectId) -> Unit,
    val onDuplicate: (ProjectId) -> Unit,
    val onDelete: (ProjectId) -> Unit,
    val onNameChanged: (String) -> Unit,
    val onDialogConfirmed: () -> Unit,
    val onDialogDismissed: () -> Unit,
    val onErrorShown: () -> Unit,
)
