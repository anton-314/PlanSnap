package app.plansnap.feature.editor.history

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.navigation.EditorNavigationActions
import app.plansnap.feature.editor.navigation.EditorRoute
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.emptyProject
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

/**
 * The undo and redo buttons of the whole editor with the real view model and domain: walls typed on the keypad are
 * taken back and restored by tapping, in the plan and in the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h900dp")
class HistoryFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = TestScope()
    private val rig = EditorRig(scope, emptyProject())
    private val viewModel: EditorViewModel by lazy { rig.viewModel() }

    private fun show() {
        compose.setContent {
            PlanSnapTheme {
                EditorRoute(
                    state = viewModel.state,
                    effects = viewModel.effects,
                    onIntent = viewModel::onIntent,
                    actions = EditorNavigationActions(onBack = {}, onOpenExport = {}),
                )
            }
        }
        settle()
    }

    /** Lets the view model and the evaluator run on the virtual clock, then lets Compose draw the result. */
    private fun settle() {
        scope.advanceUntilIdle()
        compose.waitForIdle()
        scope.runCurrent()
        compose.waitForIdle()
    }

    private fun type(vararg labels: String) {
        labels.forEach { compose.onNode(hasText(it) and hasClickAction()).performClick() }
        settle()
    }

    private fun undo(): SemanticsNodeInteraction = compose.onNodeWithContentDescription("Undo")

    private fun redo(): SemanticsNodeInteraction = compose.onNodeWithContentDescription("Redo")

    private fun tap(button: SemanticsNodeInteraction) {
        button.performClick()
        settle()
    }

    private fun storedWalls(): Int =
        rig.stored.rooms
            .single()
            .walls.size

    @Test
    fun `given typed walls when undo and redo are tapped then plan, storage and buttons follow`() {
        show()
        undo().assertIsNotEnabled()
        redo().assertIsNotEnabled()
        val empty = viewModel.snapshot()

        type("4", "0", "0", "OK")
        val one = viewModel.snapshot()
        type("3", "0", "0", "OK")
        val two = viewModel.snapshot()
        undo().assertIsEnabled()
        redo().assertIsNotEnabled()

        tap(undo())
        assertShows(one, rig, viewModel, "First undo")
        assertEquals(1, storedWalls())
        undo().assertIsEnabled()
        redo().assertIsEnabled()

        tap(undo())
        assertShows(empty, rig, viewModel, "Second undo")
        assertEquals(0, storedWalls())
        undo().assertIsNotEnabled()
        redo().assertIsEnabled()

        tap(redo())
        tap(redo())
        assertShows(two, rig, viewModel, "Redone")
        assertEquals(2, storedWalls())
        undo().assertIsEnabled()
        redo().assertIsNotEnabled()
    }
}
