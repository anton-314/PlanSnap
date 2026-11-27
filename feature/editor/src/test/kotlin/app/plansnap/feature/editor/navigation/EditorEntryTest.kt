package app.plansnap.feature.editor.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.canvas.CanvasTestScenes
import app.plansnap.feature.editor.opening.OpeningHandler
import app.plansnap.feature.editor.shape.ShapeHandler
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorStore
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor entry with the real view model, domain and evaluator over fake storage: what is typed on the keypad
 * reaches the canvas, rejected commands become messages and the sheets open from the keys.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h900dp")
class EditorEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = TestScope()
    private var backs = 0
    private var exports = 0
    private val intents = mutableListOf<EditorIntent>()

    private fun viewModel(
        project: Project,
        handle: SavedStateHandle? = null,
    ): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(scope, project, AppSettings())
        rig.handlers += ShapeHandler(SequentialIdGenerator("gen"))
        rig.handlers += OpeningHandler(SequentialIdGenerator("opening"), DefaultOpeningRules())
        val viewModel = if (handle == null) rig.viewModel() else rig.viewModel(handle)
        return rig to viewModel
    }

    private fun show(viewModel: EditorViewModel) {
        compose.setContent {
            PlanSnapTheme {
                EditorRoute(
                    state = viewModel.state,
                    effects = viewModel.effects,
                    onIntent = {
                        intents += it
                        viewModel.onIntent(it)
                    },
                    actions = EditorNavigationActions(onBack = { backs++ }, onOpenExport = { exports++ }),
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

    private fun key(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and hasClickAction())

    /** A key of the open sheet: the sheet is a window above the editor, which has the same keys beneath it. */
    private fun sheetKey(label: String): SemanticsNodeInteraction =
        compose.onAllNodes(hasText(label) and hasClickAction()).onLast()

    private fun sheetType(vararg labels: String) {
        labels.forEach { sheetKey(it).performClick() }
        settle()
    }

    private fun type(vararg labels: String) {
        labels.forEach { key(it).performClick() }
        settle()
    }

    @Test
    fun `given a project that is still loading then the screen shows progress`() {
        val (_, viewModel) = viewModel(emptyProject())
        show(viewModel)
        compose.onNodeWithText("Loading project").assertIsDisplayed()
    }

    @Test
    fun `given a loaded empty project then the plan, the input bar and the empty hint are shown`() {
        val (_, viewModel) = viewModel(emptyProject())
        show(viewModel)
        settle()
        compose.onNodeWithText("Test").assertIsDisplayed()
        compose.onNodeWithText("Enter the first wall").assertIsDisplayed()
        compose.onNode(hasContentDescription("Floor plan", substring = true)).assertExists()
        compose.onNodeWithContentDescription("Wall thickness").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add diagonal").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h900dp")
    fun `given the german locale then the empty hint and the keys are german`() {
        val (_, viewModel) = viewModel(emptyProject())
        show(viewModel)
        settle()
        compose.onNodeWithText("Erste Wand eingeben").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wandstärke").assertIsDisplayed()
        compose.onNodeWithContentDescription("Diagonale hinzufügen").assertIsDisplayed()
    }

    @Test
    fun `given digits typed on the keypad when confirmed then the wall reaches the canvas and the store`() {
        val (rig, viewModel) = viewModel(openProject())
        show(viewModel)
        settle()
        compose.onNodeWithContentDescription("Floor plan with 1 room and 3 walls").assertExists()

        type("3", "0", "0")
        compose.onNodeWithContentDescription("Wall 4, 300 centimeters").assertExists()
        compose.onNodeWithContentDescription("Floor plan with 1 room and 4 walls").assertExists()

        key("OK").performClick()
        settle()
        assertEquals(
            4,
            rig.stored.rooms
                .single()
                .walls.size,
        )
        compose.onNodeWithContentDescription("Wall 5, no value entered").assertExists()
    }

    @Test
    fun `given four walls when the room is closed then the status card confirms the shape with its area`() {
        val (rig, viewModel) = viewModel(openProject())
        show(viewModel)
        settle()
        type("3", "0", "0")
        key("OK").performClick()
        settle()
        compose.onNodeWithContentDescription("Close room").performClick()
        settle()
        assertTrue(
            rig.stored.rooms
                .single()
                .isClosed,
        )
        compose.onNodeWithText("Shape confirmed").assertIsDisplayed()
        compose.onNodeWithText("12.00 m²").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close room").assertDoesNotExist()
    }

    @Test
    fun `given a measured angle when the next wall is confirmed then the corner is stored with it`() {
        val (rig, viewModel) = viewModel(openProject())
        show(viewModel)
        settle()
        compose.onNodeWithContentDescription("Enter corner angle").performClick()
        settle()
        compose.onNodeWithText("Corner before wall 4").assertIsDisplayed()
        sheetType("7", "5")
        sheetKey("OK").performClick()
        settle()
        compose.onNodeWithText("Corner before wall 4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Corner angle 75 degrees, change").assertExists()
        type("3", "0", "0")
        key("OK").performClick()
        settle()
        val corner =
            rig.stored.rooms
                .single()
                .corners
                .last()
        assertEquals(75_000, (corner as CornerSpec.Measured).angle.milliDegrees)
    }

    @Test
    fun `given the thickness key when a value is typed then the next wall gets that thickness`() {
        val (rig, viewModel) = viewModel(openProject())
        show(viewModel)
        settle()
        compose.onNodeWithContentDescription("Wall thickness").performClick()
        settle()
        compose
            .onNodeWithText("Applies to the next wall. It grows outward and never changes the area of the room.")
            .assertIsDisplayed()
        repeat(4) { compose.onAllNodesWithContentDescription("Delete last character").onLast().performClick() }
        sheetType("2", "5")
        sheetKey("OK").performClick()
        settle()
        type("3", "0", "0")
        key("OK").performClick()
        settle()
        assertEquals(
            250L,
            rig.stored.rooms
                .single()
                .walls
                .last()
                .thickness.mm,
        )
    }

    @Test
    fun `given a room with one wall when the diagonal key is tapped then the rejection is shown as a message`() {
        val (_, viewModel) = viewModel(emptyProject())
        show(viewModel)
        settle()
        type("4", "0", "0")
        key("OK").performClick()
        settle()
        compose.onNodeWithContentDescription("Add diagonal").performClick()
        settle()
        compose.onNodeWithText("The room needs at least three walls first.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h900dp")
    fun `given the german locale when a command is rejected then the message is german`() {
        val (_, viewModel) = viewModel(emptyProject())
        show(viewModel)
        settle()
        type("4", "0", "0")
        key("OK").performClick()
        settle()
        compose.onNodeWithContentDescription("Diagonale hinzufügen").performClick()
        settle()
        compose.onNodeWithText("Der Raum braucht zuerst mindestens drei Wände.").assertIsDisplayed()
    }

    @Test
    fun `given two rooms when the second one is tapped then it receives the input`() {
        val first = CanvasTestScenes.rectangle("a", 3000, 2000, 0.0, 0.0).copy(name = "A")
        val second = CanvasTestScenes.rectangle("b", 3000, 2000, 6000.0, 0.0).copy(name = "B")
        val (_, viewModel) = viewModel(emptyProject().copy(rooms = persistentListOf(first, second)))
        show(viewModel)
        settle()
        assertEquals(RoomId("a"), viewModel.state.value.selectedRoomId)
        compose
            .onNode(hasContentDescription("Floor plan", substring = true))
            .performTouchInput { click(Offset(width * TAP_RIGHT_FRACTION, height / 2f)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_TIMEOUT_MS)
        settle()
        assertEquals(RoomId("b"), viewModel.state.value.selectedRoomId)
    }

    @Test
    fun `given a project that cannot be loaded then the error state offers retry and back`() {
        val (_, viewModel) = viewModel(emptyProject(), SavedStateHandle(mapOf(EditorStore.KEY_PROJECT_ID to "missing")))
        show(viewModel)
        settle()
        compose.onNodeWithText("The project could not be loaded.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Back to the project list").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `given the top bar when back and export are tapped then the callbacks of the app run`() {
        val (_, viewModel) = viewModel(emptyProject())
        show(viewModel)
        settle()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Export").performClick()
        assertEquals(1 to 1, backs to exports)
    }

    private companion object {
        const val TAP_RIGHT_FRACTION = 0.85f

        /** The canvas waits this long for a second tap before it reports a single tap. */
        const val DOUBLE_TAP_TIMEOUT_MS = 500L
    }
}
