package app.plansnap.feature.projects

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.model.ProjectId
import app.plansnap.feature.projects.ui.states.ProjectsEmptyState
import app.plansnap.feature.projects.ui.states.ProjectsFailedState
import app.plansnap.feature.projects.ui.states.ProjectsLoadingState
import java.text.DateFormat
import java.util.Date

/**
 * Stateless project list screen of docs/UX.md section 1 with its empty, loading and error states and the dialogs.
 *
 * @param state State to render.
 * @param actions Callbacks for user actions.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    actions: ProjectsScreenActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.error?.let { error ->
        val message = stringResource(error.messageRes())
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            actions.onErrorShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_title)) },
                actions = {
                    TextButton(onClick = actions.onImport, enabled = !state.isImporting) {
                        Text(stringResource(R.string.projects_import))
                    }
                    TextButton(onClick = actions.onOpenSettings) {
                        Text(stringResource(R.string.projects_open_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isImporting) {
                val importing = stringResource(R.string.projects_importing)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = importing },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val list = state.list) {
                    ProjectListState.Loading -> {
                        ProjectsLoadingState()
                    }

                    is ProjectListState.Failed -> {
                        ProjectsFailedState(list.error.messageRes(), actions.onRetry)
                    }

                    is ProjectListState.Loaded -> {
                        if (list.projects.isEmpty()) {
                            ProjectsEmptyState()
                        } else {
                            ProjectList(list, actions)
                        }
                    }
                }
            }
            PlanSnapButton(
                text = stringResource(R.string.projects_new_project),
                onClick = actions.onNewProject,
                modifier = Modifier.fillMaxWidth().padding(PlanSnapTheme.spacing.l),
            )
        }
    }
    ProjectsDialogs(state.dialog, actions)
}

@Composable
private fun ProjectList(
    list: ProjectListState.Loaded,
    actions: ProjectsScreenActions,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(list.projects, key = { it.id.value }) { project ->
            ProjectRow(project, actions)
        }
    }
}

@Composable
private fun ProjectRow(
    project: ProjectSummary,
    actions: ProjectsScreenActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val openLabel = stringResource(R.string.projects_open_project, project.name)
    val rooms = pluralStringResource(R.plurals.projects_room_count, project.roomCount, project.roomCount)
    val changed = remember(project.updatedAtEpochMs) { formatDate(project.updatedAtEpochMs) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = PlanSnapTheme.sizes.minTouchTarget)
                .clickable(onClickLabel = openLabel) { actions.onOpenProject(project.id) }
                .padding(start = PlanSnapTheme.spacing.l, end = PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(vertical = PlanSnapTheme.spacing.m),
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.projects_summary, rooms, changed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            PlanSnapIconButton(
                icon = MoreIcon,
                contentDescription = stringResource(R.string.projects_more_actions, project.name),
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                RowMenuItem(R.string.projects_action_rename) {
                    menuOpen = false
                    actions.onRename(project.id)
                }
                RowMenuItem(R.string.projects_action_duplicate) {
                    menuOpen = false
                    actions.onDuplicate(project.id)
                }
                RowMenuItem(R.string.projects_action_delete) {
                    menuOpen = false
                    actions.onDelete(project.id)
                }
            }
        }
    }
}

@Composable
private fun RowMenuItem(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        modifier = Modifier.sizeIn(minHeight = PlanSnapTheme.sizes.minTouchTarget),
    )
}

@StringRes
internal fun ProjectsError.messageRes(): Int =
    when (this) {
        ProjectsError.READ_FAILED -> R.string.projects_error_read_failed
        ProjectsError.WRITE_FAILED -> R.string.projects_error_write_failed
        ProjectsError.NO_SPACE -> R.string.projects_error_no_space
        ProjectsError.NOT_FOUND -> R.string.projects_error_not_found
        ProjectsError.INVALID_NAME -> R.string.projects_error_invalid_name
        ProjectsError.IMPORT_MALFORMED -> R.string.projects_error_import_malformed
        ProjectsError.IMPORT_UNSUPPORTED_VERSION -> R.string.projects_error_import_unsupported_version
        ProjectsError.IMPORT_TOO_LARGE -> R.string.projects_error_import_too_large
        ProjectsError.IMPORT_INVALID_VALUES -> R.string.projects_error_import_invalid_values
        ProjectsError.UNKNOWN -> R.string.projects_error_unknown
    }

private fun formatDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

/** Three vertical dots; the design system offers no overflow symbol. */
private val MoreIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector
        .Builder(
            name = "More",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z" +
                        "M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
                        "M12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}
