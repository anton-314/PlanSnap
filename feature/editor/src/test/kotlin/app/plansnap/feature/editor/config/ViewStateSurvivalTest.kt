package app.plansnap.feature.editor.config

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.model.Project
import app.plansnap.feature.editor.save.WRITE_FAILED
import app.plansnap.feature.editor.state.SaveStatus
import app.plansnap.feature.editor.state.closedProject
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plan view, status card, menu, hints and messages across window changes (REQ-UX-009). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewStateSurvivalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun editor(
        project: Project,
        device: DeviceConfig = DeviceConfig.PHONE_PORTRAIT,
    ) = ConfigEditor(compose, project).launch(device)

    /** Zooms in with two fingers around the middle of the plan and then drags it with one finger. */
    private fun ConfigEditor.zoomAndPan() {
        plan().performTouchInput { pinchOpen() }
        settle()
        plan().performTouchInput { drag(center, center + Offset(width * NEAR, height * NEAR)) }
        settle()
    }

    private fun TouchInjectionScope.pinchOpen() {
        val near = Offset(width * NEAR, 0f)
        val far = Offset(width * FAR, 0f)
        down(0, center - near)
        down(1, center + near)
        for (step in 1..GESTURE_STEPS) {
            val shift = near + (far - near) * (step.toFloat() / GESTURE_STEPS)
            updatePointerTo(0, center - shift)
            updatePointerTo(1, center + shift)
            move()
        }
        up(0)
        up(1)
    }

    private fun TouchInjectionScope.drag(
        from: Offset,
        to: Offset,
    ) {
        down(from)
        for (step in 1..GESTURE_STEPS) moveTo(from + (to - from) * (step.toFloat() / GESTURE_STEPS))
        up()
    }

    /** The header of the status card, the button that expands and collapses it. */
    private fun cardHeader() =
        compose.onNode((hasStateDescription("Expanded") or hasStateDescription("Collapsed")) and hasClickAction())

    private fun assertCard(expanded: Boolean) {
        cardHeader().assert(hasStateDescription(if (expanded) "Expanded" else "Collapsed"))
        val action = compose.onNode(hasText("Adjust") and hasClickAction())
        if (expanded) action.assertExists() else action.assertDoesNotExist()
    }

    private fun ConfigEditor.failedSave() {
        rig.saver.failures.failNext(FakeProjectSaver.Operation.SAVE, WRITE_FAILED)
        type("3", "0", "0", "OK")
    }

    private fun ConfigEditor.storedWalls() =
        rig.stored.rooms
            .single()
            .walls.size

    @Test
    fun `given a zoomed and panned plan when the window changes then zoom and center stay`() {
        val editor = editor(openProject())
        val fitted = checkNotNull(editor.state.view)
        editor.zoomAndPan()
        val chosen = checkNotNull(editor.state.view)
        assertTrue("The pinch zoomed in", chosen.pixelsPerMm > fitted.pixelsPerMm)
        assertNotEquals(fitted.centerXMm, chosen.centerXMm)

        editor.tour { assertEquals(chosen, editor.state.view) }
    }

    @Test
    fun `given a zoomed plan when the process dies then zoom and center are restored`() {
        val editor = editor(openProject())
        editor.zoomAndPan()
        val chosen = checkNotNull(editor.state.view)

        editor.killProcess()

        assertEquals(chosen, editor.state.view)
    }

    @Test
    fun `given a collapsed status card when the window changes and the process dies then it stays collapsed`() {
        val editor = editor(closedProject(GAP_MM), DeviceConfig.TABLET_PORTRAIT)
        assertCard(expanded = true)
        editor.tap(cardHeader())

        editor.tour { assertCard(expanded = false) }
        // A high window, in which an untouched card would be expanded.
        editor.resize(DeviceConfig.TABLET_PORTRAIT)
        editor.killProcess()

        assertCard(expanded = false)
        editor.tap(cardHeader())
        assertCard(expanded = true)
    }

    @Test
    fun `given a card expanded by the user in a low window when the window changes then it stays expanded`() {
        val editor = editor(closedProject(GAP_MM), DeviceConfig.PHONE_LANDSCAPE)
        assertCard(expanded = false)
        editor.tap(cardHeader())

        editor.tour { assertCard(expanded = true) }
    }

    @Test
    fun `given an untouched status card when the window changes then it follows the height of the window`() {
        val editor = editor(closedProject(GAP_MM))

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        assertCard(expanded = false)
        editor.resize(DeviceConfig.TABLET_PORTRAIT)
        assertCard(expanded = true)
    }

    @Test
    fun `given the open menu when the window changes then it stays open and its entries work`() {
        val editor = editor(closedProject(GAP_MM))
        editor.openMenu()

        editor.tour { compose.onNodeWithText("Rooms and areas").assertExists() }

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Rooms and areas")
        compose.onNodeWithText("Totals").assertExists()
        compose.onNodeWithText("Add door").assertDoesNotExist()
    }

    @Test
    fun `given the closed menu when the window changes then it stays closed`() {
        val editor = editor(closedProject(GAP_MM))

        editor.tour { compose.onNodeWithText("Rooms and areas").assertDoesNotExist() }
    }

    @Test
    fun `given a hint when the window changes then it is offered until it is closed and never again`() {
        val editor = editor(emptyProject())
        compose.onNodeWithText(NUMPAD_HINT).assertExists()
        editor.resize(DeviceConfig.TABLET_LANDSCAPE)
        compose.onNodeWithText(NUMPAD_HINT).assertExists()
        editor.tapDescribed("Close hint")
        assertTrue(EditorHint.NUMPAD in editor.state.settings.dismissedHints)

        editor.tour { compose.onNodeWithText(NUMPAD_HINT).assertDoesNotExist() }
        editor.killProcess()

        compose.onNodeWithText(NUMPAD_HINT).assertDoesNotExist()
    }

    @Test
    fun `given a failed save when the window changes then the message stays and saving again stores the wall`() {
        val editor = editor(openProject())
        editor.failedSave()

        editor.tour {
            assertEquals(SaveStatus.Failed(WRITE_FAILED), editor.state.save)
            compose.onNodeWithText("Not saved").assertExists()
        }

        editor.tap(editor.key("Save again"))
        compose.onNodeWithText("Not saved").assertDoesNotExist()
        assertEquals(4, editor.storedWalls())
    }

    @Test
    fun `given a retry in progress when the window changes then the message does not disappear`() {
        val editor = editor(openProject())
        editor.failedSave()
        val gate = CompletableDeferred<Unit>()
        editor.saver.gate = gate
        editor.tap(editor.key("Save again"))

        editor.tour {
            assertEquals(SaveStatus.Saving, editor.state.save)
            compose.onNodeWithText("Not saved").assertExists()
        }
        gate.complete(Unit)
        editor.settle()

        compose.onNodeWithText("Not saved").assertDoesNotExist()
        compose.onNodeWithContentDescription("Wall 5, no value entered").assertExists()
        assertEquals(4, editor.storedWalls())
    }

    private companion object {
        const val NUMPAD_HINT = "Hint: Type the wall length on the number pad, then tap OK."

        /** A last wall of 303 cm leaves a gap, so the card offers an adjustment. */
        const val GAP_MM = 3030L

        /** Distance of a finger from the middle at the start of a gesture, as a share of the width. */
        const val NEAR = 0.1f

        /** Distance of a finger from the middle at the end of the pinch, as a share of the width. */
        const val FAR = 0.3f
        const val GESTURE_STEPS = 10
    }
}
