package app.plansnap.navigation

import androidx.navigation.NavController
import app.plansnap.core.model.ProjectId
import kotlinx.serialization.Serializable

/** Project list, the start destination. */
@Serializable
data object ProjectsRoute

/**
 * Editor of one project.
 *
 * @property projectId value of the [ProjectId] to edit.
 */
@Serializable
data class EditorRoute(
    val projectId: String,
)

/**
 * Export of one project.
 *
 * @property projectId value of the [ProjectId] to export.
 */
@Serializable
data class ExportRoute(
    val projectId: String,
)

/** Settings. */
@Serializable
data object SettingsRoute

/** Opens the editor for [projectId]. */
fun NavController.navigateToEditor(projectId: ProjectId) {
    navigate(EditorRoute(projectId.value))
}

/** Opens the export screen for [projectId]. */
fun NavController.navigateToExport(projectId: ProjectId) {
    navigate(ExportRoute(projectId.value))
}

/** Opens the settings screen. */
fun NavController.navigateToSettings() {
    navigate(SettingsRoute)
}
