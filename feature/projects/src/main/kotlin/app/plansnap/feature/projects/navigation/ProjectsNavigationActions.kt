package app.plansnap.feature.projects.navigation

import androidx.compose.runtime.Immutable
import app.plansnap.core.model.ProjectId

/**
 * Navigation callbacks of the project list. The list knows no routes; the app maps the callbacks to destinations.
 *
 * @property onOpenProject Opens the editor for the given project.
 * @property onOpenSettings Opens the settings screen.
 */
@Immutable
data class ProjectsNavigationActions(
    val onOpenProject: (ProjectId) -> Unit,
    val onOpenSettings: () -> Unit,
)
