package app.plansnap.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.plansnap.core.model.ProjectId
import app.plansnap.feature.editor.navigation.EditorEntry
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.export.navigation.ExportEntry
import app.plansnap.feature.projects.navigation.ProjectsEntry
import app.plansnap.feature.projects.navigation.ProjectsNavigationActions
import app.plansnap.feature.settings.navigation.SettingsEntry

/**
 * Navigation graph of the app with type-safe routes (docs/UX.md section 1).
 *
 * The sheets inside the editor and the import file dialog are not destinations.
 *
 * @param modifier Modifier for the host.
 * @param navController Controller that owns the back stack; tests pass a test controller.
 */
@Composable
fun PlanSnapNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = ProjectsRoute, modifier = modifier) {
        composable<ProjectsRoute> {
            ProjectsEntry(
                ProjectsNavigationActions(
                    onOpenProject = navController::navigateToEditor,
                    onOpenSettings = navController::navigateToSettings,
                ),
            )
        }
        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            EditorEntry(
                EditorNavigationActions(
                    onBack = { navController.popBackStack() },
                    onOpenExport = { navController.navigateToExport(ProjectId(route.projectId)) },
                ),
            )
        }
        composable<ExportRoute> {
            ExportEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsEntry(onBack = { navController.popBackStack() })
        }
    }
}
