package app.plansnap.feature.projects.a11y

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.designsystem.a11y.AccessibilityAudit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.model.ProjectId
import app.plansnap.feature.projects.ProjectListState
import app.plansnap.feature.projects.ProjectsDialog
import app.plansnap.feature.projects.ProjectsError
import app.plansnap.feature.projects.ProjectsScreen
import app.plansnap.feature.projects.ProjectsScreenActions
import app.plansnap.feature.projects.ProjectsUiState
import app.plansnap.feature.projects.R
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NAME_DIALOG_WIDTH_DP = 320

/**
 * Automatic accessibility checks of every state of the project list (REQ-UX-008), in English and German at normal and
 * at 200 percent font: every control has a spoken label and 48 dp, no text is cut off and no word is broken.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ProjectsAccessibilityTest(
    private val language: String,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createComposeRule()

    private val noActions =
        ProjectsScreenActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val loaded =
        ProjectListState.Loaded(
            persistentListOf(
                ProjectSummary(ProjectId("a"), "Apartment South", 4, 0L, 0L),
                ProjectSummary(ProjectId("b"), "Garden cabin", 1, 0L, 0L),
            ),
        )

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun show(
        state: ProjectsUiState,
        widthDp: Int = 360,
    ) {
        RuntimeEnvironment.setQualifiers("+$language-w${widthDp}dp-h640dp-port-notnight-mdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { PlanSnapTheme { ProjectsScreen(state, noActions) } }
    }

    private fun assertAccessible() {
        compose.waitForIdle()
        val merged = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        val unmerged = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val findings = AccessibilityAudit.controls(merged) + AccessibilityAudit.texts(unmerged)
        assertEquals("", findings.joinToString("\n") { "${it.rule}: ${it.description}" })
    }

    private fun check(state: ProjectsUiState) {
        show(state)
        assertAccessible()
    }

    /**
     * The dialogs with a text field are checked on a window 320 dp wide: a Material dialog with a text field never lets
     * Robolectric go idle on a 360 dp window, also without this app around it.
     */
    private fun checkNameDialog(dialog: ProjectsDialog) {
        show(ProjectsUiState(list = loaded, dialog = dialog), widthDp = NAME_DIALOG_WIDTH_DP)
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        assertAccessible()
    }

    @Test
    fun `project list`() = check(ProjectsUiState(list = loaded))

    @Test
    fun `empty list`() = check(ProjectsUiState(list = ProjectListState.Loaded(persistentListOf())))

    @Test
    fun loading() = check(ProjectsUiState())

    @Test
    fun `load failed`() = check(ProjectsUiState(list = ProjectListState.Failed(ProjectsError.READ_FAILED)))

    @Test
    fun importing() = check(ProjectsUiState(list = loaded, isImporting = true))

    @Test
    fun `error message`() = check(ProjectsUiState(list = loaded, error = ProjectsError.IMPORT_UNSUPPORTED_VERSION))

    @Test
    fun `create dialog`() = checkNameDialog(ProjectsDialog.Create("Flat"))

    @Test
    fun `rename dialog`() = checkNameDialog(ProjectsDialog.Rename(ProjectId("a"), "Flat"))

    @Test
    fun `delete dialog`() =
        check(ProjectsUiState(list = loaded, dialog = ProjectsDialog.ConfirmDelete(ProjectId("a"), "Apartment South")))

    @Test
    fun `row menu`() {
        show(ProjectsUiState(list = loaded))
        val more =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.projects_more_actions, "Apartment South")
        compose.onNodeWithContentDescription(more).performClick()
        assertAccessible()
    }

    @Test
    fun `new project button stays on screen`() {
        show(ProjectsUiState(list = loaded))

        compose.onNodeWithText(string(R.string.projects_new_project)).assertIsDisplayed()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}, font scale {1}")
        fun variants(): List<Array<Any>> =
            listOf("en-rUS", "de-rDE").flatMap { language ->
                listOf(1f, 2f).map { arrayOf<Any>(language, it) }
            }
    }
}
