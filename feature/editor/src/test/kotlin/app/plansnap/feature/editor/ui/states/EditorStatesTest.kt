package app.plansnap.feature.editor.ui.states

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.navigation.EditorRoute
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.testing.fixtures.fakes.FakeProjectRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Progress, load failure and empty state of the editor through the real ViewModel (docs/UX.md section 1). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h900dp")
class EditorStatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = TestScope()
    private val rig = EditorRig(scope, emptyProject(), AppSettings())
    private var backs = 0

    private fun failNextLoad() {
        rig.projects.failures.failNext(
            FakeProjectRepository.Operation.GET,
            DomainError.Storage(StorageProblem.READ_FAILED),
        )
    }

    private fun show(viewModel: EditorViewModel) {
        compose.setContent {
            PlanSnapTheme {
                EditorRoute(
                    state = viewModel.state,
                    effects = viewModel.effects,
                    onIntent = viewModel::onIntent,
                    actions = EditorNavigationActions(onBack = { backs++ }, onOpenExport = {}),
                )
            }
        }
    }

    /** Lets the view model and the evaluator run on the virtual clock, then lets Compose draw the result. */
    private fun settle() {
        scope.advanceUntilIdle()
        compose.waitForIdle()
        scope.runCurrent()
        compose.waitForIdle()
    }

    private fun okKey() = compose.onNode(hasText("OK") and hasClickAction())

    @Test
    fun `given a read failure then retry shows progress and then the empty editor`() {
        failNextLoad()
        val viewModel = rig.viewModel()
        show(viewModel)
        settle()
        compose.onNodeWithText("The project could not be loaded.").assertIsDisplayed()
        okKey().assertDoesNotExist()

        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Loading project").assertIsDisplayed()
        settle()
        compose.onNodeWithText("Enter the first wall").assertIsDisplayed()
        compose.onNode(hasText("7") and hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `given retry fails again then the failure, retry and the way to the list stay available`() {
        failNextLoad()
        val viewModel = rig.viewModel()
        show(viewModel)
        settle()
        failNextLoad()

        compose.onNodeWithText("Try again").performClick()
        settle()

        compose.onNodeWithText("The project could not be loaded.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Back to the project list").performClick()
        assertEquals(1, backs)
    }
}
