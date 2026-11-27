package app.plansnap.feature.projects

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Top bar of the project list in narrow windows and with large fonts (docs/UX.md sections 1 and 7). Native graphics,
 * so that text has its real width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w360dp-h640dp-mdpi")
class ProjectsTopBarTest {
    @get:Rule
    val compose = createComposeRule()

    private var settingsOpened = 0
    private var importStarted = 0
    private var newProjects = 0
    private var retries = 0

    private val failed = ProjectsUiState(list = ProjectListState.Failed(ProjectsError.READ_FAILED))
    private val empty = ProjectsUiState(list = ProjectListState.Loaded(persistentListOf()))

    private fun show(
        state: ProjectsUiState = empty,
        fontScale: Float = 1f,
    ) {
        val actions =
            ProjectsScreenActions(
                onOpenProject = {},
                onOpenSettings = { settingsOpened++ },
                onNewProject = { newProjects++ },
                onImport = { importStarted++ },
                onRetry = { retries++ },
                onRename = {},
                onDuplicate = {},
                onDelete = {},
                onNameChanged = {},
                onDialogConfirmed = {},
                onDialogDismissed = {},
                onErrorShown = {},
            )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PlanSnapTheme { ProjectsScreen(state = state, actions = actions) }
            }
        }
    }

    private fun assertOnOneUncutLine(title: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(title)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("lines of $title", 1, layout.lineCount)
        assertFalse("$title is cut off", layout.hasVisualOverflow || layout.isLineEllipsized(0))
    }

    /** Clicks an action of the top bar, opening the overflow menu first when the action is not in the bar. */
    private fun clickAction(
        label: String,
        overflow: String,
    ) {
        if (compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription(overflow).assertIsDisplayed().performClick()
        }
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.waitForIdle()
    }

    private fun assertActionsReachable(
        import: String,
        settings: String,
        overflow: String,
    ) {
        clickAction(import, overflow)
        clickAction(settings, overflow)
        assertEquals(1, importStarted)
        assertEquals(1, settingsOpened)
    }

    @Test
    @Config(qualifiers = "de-w360dp-h640dp-mdpi")
    fun `given german at 360 dp then the title stays on one line and the actions are reachable`() {
        show()
        assertOnOneUncutLine("Projekte")
        assertActionsReachable("Projekt importieren", "Einstellungen", "Weitere Aktionen")
    }

    @Test
    @Config(qualifiers = "de-w360dp-h640dp-mdpi")
    fun `given german at font scale 2 then the title stays on one line and the actions are reachable`() {
        show(fontScale = 2f)
        assertOnOneUncutLine("Projekte")
        assertActionsReachable("Projekt importieren", "Einstellungen", "Weitere Aktionen")
    }

    @Test
    fun `given english at 360 dp then the title stays on one line and the actions are reachable`() {
        show()
        assertOnOneUncutLine("Projects")
        assertActionsReachable("Import project", "Settings", "More actions")
    }

    @Test
    fun `given english at font scale 2 then the title stays on one line and the actions are reachable`() {
        show(fontScale = 2f)
        assertOnOneUncutLine("Projects")
        assertActionsReachable("Import project", "Settings", "More actions")
    }

    @Test
    @Config(qualifiers = "de-w840dp-h480dp-mdpi")
    fun `given a wide window then both actions are in the bar without an overflow menu`() {
        show()
        compose.onNodeWithText("Projekt importieren").assertIsDisplayed()
        compose.onNodeWithText("Einstellungen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Weitere Aktionen").assertDoesNotExist()
    }

    @Test
    fun `given a running import then import is disabled in the overflow menu`() {
        show(state = empty.copy(isImporting = true), fontScale = 2f)
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Import project").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h320dp-mdpi")
    fun `given a small window and font scale 2 then retry and new project are reachable after a failure`() {
        show(state = failed, fontScale = 2f)
        assertOnOneUncutLine("Projects")
        compose
            .onNodeWithText("Try again")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("New project").assertIsDisplayed().performClick()

        assertEquals(1, retries)
        assertEquals(1, newProjects)
    }

    @Test
    @Config(qualifiers = "de-w320dp-h320dp-mdpi")
    fun `given a small window and font scale 2 then the empty state and new project are reachable`() {
        show(fontScale = 2f)
        assertOnOneUncutLine("Projekte")
        compose.onNodeWithText("Miss einen Raum", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Neues Projekt").assertIsDisplayed().performClick()
        assertActionsReachable("Projekt importieren", "Einstellungen", "Weitere Aktionen")

        assertEquals(1, newProjects)
    }
}
