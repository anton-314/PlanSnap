package app.plansnap.feature.projects.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plansnap.feature.projects.ImportSource
import app.plansnap.feature.projects.ProjectsEvent
import app.plansnap.feature.projects.ProjectsScreen
import app.plansnap.feature.projects.ProjectsScreenActions
import app.plansnap.feature.projects.ProjectsViewModel

/**
 * Public entry point of the project list feature (docs/UX.md section 1). Connects the screen to its
 * [ProjectsViewModel] and the system file dialog of the import; picking nothing is not an error and changes nothing.
 *
 * @param actions Navigation callbacks supplied by the app.
 * @param modifier Modifier for the screen.
 * @param viewModel State holder, injected by Hilt.
 */
@Composable
fun ProjectsEntry(
    actions: ProjectsNavigationActions,
    modifier: Modifier = Modifier,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onImport(context.importSource(uri))
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.OpenProject -> actions.onOpenProject(event.id)
            }
        }
    }
    val screenActions =
        remember(viewModel, actions, importLauncher) {
            ProjectsScreenActions(
                onOpenProject = actions.onOpenProject,
                onOpenSettings = actions.onOpenSettings,
                onNewProject = viewModel::onNewProject,
                onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                onRetry = viewModel::onRetry,
                onRename = viewModel::onRenameRequested,
                onDuplicate = viewModel::onDuplicate,
                onDelete = viewModel::onDeleteRequested,
                onNameChanged = viewModel::onNameChanged,
                onDialogConfirmed = viewModel::onDialogConfirmed,
                onDialogDismissed = viewModel::onDialogDismissed,
                onErrorShown = viewModel::onErrorShown,
            )
        }
    ProjectsScreen(state = state, actions = screenActions, modifier = modifier)
}

/** Project files are JSON, but file providers label them inconsistently, so generic types are accepted as well. */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** A provider that revokes access after picking throws a [SecurityException], handled like a missing file. */
private fun Context.importSource(uri: Uri): ImportSource =
    ImportSource {
        try {
            contentResolver.openInputStream(uri)
        } catch (_: SecurityException) {
            null
        }
    }
