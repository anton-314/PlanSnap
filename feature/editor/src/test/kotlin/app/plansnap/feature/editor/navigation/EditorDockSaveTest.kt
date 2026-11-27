package app.plansnap.feature.editor.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.feature.editor.rooms.GUEST_ID
import app.plansnap.feature.editor.rooms.RoomsHandler
import app.plansnap.feature.editor.rooms.RoomsIntent
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.rooms.twoRoomProject
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val HINT = "Tap a wall or an opening of another room on the plan."

/** A failed save while the host of a dock is chosen on the plan, on a small phone, with the real view model. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class EditorDockSaveTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = TestScope()
    private val rig = EditorRig(scope, twoRoomProject())
    private var backs = 0
    private lateinit var viewModel: EditorViewModel

    private val dock
        get() =
            viewModel.state.value
                .section(RoomsState::class)
                ?.dock

    private fun show(fontScale: Float = 1f) {
        val appScope = ApplicationScope(scope.backgroundScope)
        rig.handlers += RoomsHandler(SequentialIdGenerator("gen"), rig.settingsRepository, appScope)
        viewModel = rig.viewModel()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
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
        settle()
    }

    private fun settle() {
        scope.advanceUntilIdle()
        compose.waitForIdle()
        scope.runCurrent()
        compose.waitForIdle()
    }

    private fun send(intent: EditorIntent) {
        compose.runOnUiThread { viewModel.onIntent(intent) }
        settle()
    }

    private fun pickOnPlan() {
        send(CoreIntent.SelectRoom(GUEST_ID))
        send(RoomsIntent.OpenDock)
        send(RoomsIntent.StartPicking)
    }

    private fun failSaveAndPick() {
        rig.saver.failures.failNext(
            FakeProjectSaver.Operation.SAVE,
            DomainError.Storage(StorageProblem.WRITE_FAILED),
        )
        send(RoomsIntent.AddRoom)
        pickOnPlan()
    }

    private fun button(label: String) = compose.onNode(hasText(label) and hasClickAction())

    private fun bounds(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `given a failed save when the host is chosen on the plan then the message leaves the dock bar uncovered`() {
        show()
        failSaveAndPick()

        val cancel = button("Cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val title = bounds("Not saved")
        assertTrue("message $title starts below cancel $cancel", title.top >= cancel.bottom)
        assertTrue("message $title starts below the hint", title.top >= bounds(HINT).bottom)
        button("Save again").assertIsDisplayed()

        button("Cancel").performClick()
        settle()

        assertFalse("the tap reached the cancel button", checkNotNull(dock).picking)
    }

    @Test
    fun `given a failed save while choosing the host when saving again is tapped then the change is stored`() {
        show()
        failSaveAndPick()

        button("Save again").performClick()
        settle()

        button("Save again").assertDoesNotExist()
        assertEquals(
            viewModel.state.value.project
                ?.rooms,
            rig.stored.rooms,
        )
        assertTrue(checkNotNull(dock).picking)
    }

    @Test
    fun `given font scale 2 when a save failed while choosing the host then cancel works and retry follows`() {
        show(fontScale = 2f)
        failSaveAndPick()

        button("Cancel").assertIsDisplayed().performClick()
        settle()
        assertFalse(checkNotNull(dock).picking)

        send(RoomsIntent.Dismiss)
        button("Save again").assertIsDisplayed().performClick()
        settle()
        button("Save again").assertDoesNotExist()
    }

    @Test
    fun `given the selection on the plan when back is pressed then only the selection ends`() {
        show()
        pickOnPlan()
        compose.onNodeWithText(HINT).assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()

        assertNotNull("the dock stays open", dock)
        assertFalse(checkNotNull(dock).picking)
        assertFalse(compose.activity.isFinishing)
        assertEquals(0, backs)
    }

    @Test
    fun `given no selection on the plan then the editor does not take the back key`() {
        show()
        compose.runOnUiThread {
            assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }
}
