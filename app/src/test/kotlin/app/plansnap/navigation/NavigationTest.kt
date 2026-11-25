package app.plansnap.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.ProjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class NavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var controller: TestNavHostController

    private fun show() {
        compose.setContent {
            controller = TestNavHostController(LocalContext.current)
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            PlanSnapTheme { PlanSnapNavHost(navController = controller) }
        }
        compose.waitForIdle()
    }

    private fun backStackRoutes() =
        controller.currentBackStack.value
            .filter { it.destination.route != null }
            .map { entry ->
                val destination = entry.destination
                when {
                    destination.hasRoute<ProjectsRoute>() -> "projects"
                    destination.hasRoute<EditorRoute>() -> "editor:" + entry.toRoute<EditorRoute>().projectId
                    destination.hasRoute<ExportRoute>() -> "export:" + entry.toRoute<ExportRoute>().projectId
                    destination.hasRoute<SettingsRoute>() -> "settings"
                    else -> error("Unknown destination ${destination.route}")
                }
            }

    @Test
    fun startsOnTheProjectList() {
        show()
        assertTrue(controller.currentDestination!!.hasRoute<ProjectsRoute>())
        assertEquals(listOf("projects"), backStackRoutes())
        compose.onNodeWithText("Projects").assertExists()
    }

    @Test
    fun listOpensSettingsAndBackReturns() {
        show()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        assertTrue(controller.currentDestination!!.hasRoute<SettingsRoute>())
        assertEquals(listOf("projects", "settings"), backStackRoutes())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects"), backStackRoutes())
    }

    @Test
    fun listCallbackOpensEditorWithProjectId() {
        show()
        compose.runOnUiThread { controller.navigateToEditor(ProjectId("p-1")) }
        compose.waitForIdle()
        assertEquals(listOf("projects", "editor:p-1"), backStackRoutes())
        compose.onNodeWithText("Enter the first wall").assertExists()
    }

    @Test
    fun editorOpensExportWithTheSameProjectId() {
        show()
        compose.runOnUiThread { controller.navigateToEditor(ProjectId("p-1")) }
        compose.waitForIdle()
        compose.onNodeWithText("Export").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects", "editor:p-1", "export:p-1"), backStackRoutes())
        compose.onNodeWithText("No room yet").assertExists()
    }

    @Test
    fun backFromExportReturnsToTheEditorAndThenTheList() {
        show()
        compose.runOnUiThread { controller.navigateToEditor(ProjectId("p-1")) }
        compose.waitForIdle()
        compose.onNodeWithText("Export").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects", "editor:p-1"), backStackRoutes())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("projects"), backStackRoutes())
    }
}
