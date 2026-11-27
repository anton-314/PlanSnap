package app.plansnap.feature.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.ProjectId
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the project list: empty, loading, filled, delete dialog and overflow menu (TESTING section 6). */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class ProjectsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val noActions =
        ProjectsScreenActions(
            onOpenProject = {},
            onOpenSettings = {},
            onNewProject = {},
            onImport = {},
            onRetry = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            onNameChanged = {},
            onDialogConfirmed = {},
            onDialogDismissed = {},
            onErrorShown = {},
        )

    private val projects =
        ProjectListState.Loaded(
            persistentListOf(
                summary("a", name = "Apartment South", rooms = 4, updated = FIXED_TIME),
                summary("b", name = "Garden cabin", rooms = 1, updated = FIXED_TIME),
            ),
        )

    @Test
    fun `given no projects when shown then it matches the empty baseline`() =
        capture("projects-empty.png") {
            ProjectsScreen(ProjectsUiState(list = ProjectListState.Loaded(persistentListOf())), noActions)
        }

    @Test
    fun `given projects when shown then it matches the list baseline`() =
        capture("projects-list.png") { ProjectsScreen(ProjectsUiState(list = projects), noActions) }

    @Test
    fun `given a delete request when shown then it matches the dialog baseline`() =
        capture("projects-delete-dialog.png") {
            ProjectsScreen(
                ProjectsUiState(
                    list = projects,
                    dialog = ProjectsDialog.ConfirmDelete(ProjectId("a"), "Apartment South"),
                ),
                noActions,
            )
        }

    @Test
    fun `given the list is loading when shown then it matches the skeleton baseline`() =
        capture("projects-loading.png") { ProjectsScreen(ProjectsUiState(), noActions) }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a narrow german screen when the overflow menu is open then it matches the baseline`() {
        compose.setContent { PlanSnapTheme { ProjectsScreen(ProjectsUiState(list = projects), noActions) } }
        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.waitForIdle()
        captureScreenRoboImage(filePath = "projects-overflow-menu-de.png", roborazziOptions = STRICT_OPTIONS)
    }

    private fun capture(
        fileName: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { PlanSnapTheme { content() } }
        compose.waitForIdle()
        captureScreenRoboImage(filePath = fileName, roborazziOptions = STRICT_OPTIONS)
    }

    private companion object {
        /** A fixed instant (2024-01-15 UTC), so that the shown date does not change between runs. */
        const val FIXED_TIME = 1_705_320_000_000L

        /** Any pixel difference fails; the renderer is deterministic, so no tolerance is needed. */
        val STRICT_OPTIONS =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))
    }
}
