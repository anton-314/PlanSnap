package app.plansnap.feature.projects.navigation

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ProjectsEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private var settingsOpened = 0

    private fun show() {
        compose.setContent {
            PlanSnapTheme {
                ProjectsEntry(ProjectsNavigationActions(onOpenProject = {}, onOpenSettings = { settingsOpened++ }))
            }
        }
    }

    @Test
    fun showsTitleAndEmptyStateWithNewProjectButton() {
        show()
        compose.onNodeWithText("Projects").assertExists()
        compose.onNodeWithText("New project").assertExists()
        compose.onNodeWithText("Measure a room", substring = true).assertExists()
    }

    @Test
    fun newProjectIsNotAvailableUntilTheListIsImplemented() {
        show()
        compose.onNodeWithText("New project").assertIsNotEnabled()
    }

    @Test
    fun settingsActionInvokesCallback() {
        show()
        compose.onNodeWithText("Settings").assertIsEnabled().performClick()
        assertEquals(1, settingsOpened)
    }

    @Test
    @Config(qualifiers = "de")
    fun germanStringsAreUsedOnGermanDevices() {
        show()
        compose.onNodeWithText("Projekte").assertExists()
        compose.onNodeWithText("Neues Projekt").assertExists()
    }
}
