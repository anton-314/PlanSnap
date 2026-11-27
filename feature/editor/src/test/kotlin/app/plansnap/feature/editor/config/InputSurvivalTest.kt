package app.plansnap.feature.editor.config

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.state.PROJECT_ID
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.SaveStatus
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The running wall input across window changes and process death (REQ-UX-009, REQ-DATA-002). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InputSurvivalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun editor() = ConfigEditor(compose, openProject()).launch()

    private fun assertTyped(
        editor: ConfigEditor,
        text: String,
    ) {
        assertEquals(text, editor.state.inputText)
        compose.onNodeWithContentDescription("Wall 4, $text centimeters").assertExists()
    }

    private fun ConfigEditor.room() = rig.stored.rooms.single()

    @Test
    fun `given a typed length when the window changes through all shapes then the input stays and can be finished`() {
        val editor = editor()
        editor.type("3", "4")

        editor.tour { assertTyped(editor, "34") }

        editor.type("5")
        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.type("OK")
        assertEquals(4, editor.room().walls.size)
        assertEquals(
            3450L,
            editor
                .room()
                .walls
                .last()
                .length.mm,
        )
    }

    @Test
    fun `given a measured angle, a thickness and a direction when the window changes then the next wall uses them`() {
        val editor = editor()
        editor.tapDescribed("Enter corner angle")
        editor.tapSheet("Right")
        editor.sheetType("7", "5", "OK")
        editor.tapDescribed("Wall thickness")
        repeat(PREFILLED_THICKNESS_CHARS) { editor.tapDescribed("Delete last character") }
        editor.sheetType("2", "5", "OK")
        editor.type("2", "0", "0")

        editor.tour {
            assertTyped(editor, "200")
            compose.onNodeWithContentDescription("Corner angle 75 degrees, change").assertExists()
            compose.onNodeWithContentDescription("Next wall turns right").assertIsSelected()
            compose.onNodeWithText("T 25.0", substring = true, useUnmergedTree = true).assertExists()
        }

        editor.type("OK")
        val corner = editor.room().corners.last() as CornerSpec.Measured
        assertEquals(75_000, corner.angle.milliDegrees)
        assertEquals(TurnDirection.RIGHT, corner.direction)
        assertEquals(
            250L,
            editor
                .room()
                .walls
                .last()
                .thickness.mm,
        )
    }

    @Test
    fun `given a chosen direction when the window changes then it stays chosen`() {
        val editor = editor()
        editor.tapDescribed("Next wall turns right")

        editor.tour { compose.onNodeWithContentDescription("Next wall turns right").assertIsSelected() }
    }

    @Test
    fun `given a rejected length when the window changes then the text and its explanation stay`() {
        val editor = editor()
        editor.type("0")

        editor.tour {
            assertTyped(editor, "0")
            compose.onNodeWithText("The wall must be longer than 0.").assertExists()
        }
    }

    @Test
    fun `given a typed length when the process dies then the input is restored and can be confirmed`() {
        val editor = editor()
        editor.type("3", "4", "5")

        editor.killProcess()

        assertTyped(editor, "345")
        assertEquals(ROOM_ID, editor.state.selectedRoomId)
        assertEquals(3, editor.room().walls.size)
        editor.type("OK")
        assertEquals(
            3450L,
            editor
                .room()
                .walls
                .last()
                .length.mm,
        )
    }

    @Test
    fun `given input typed across window changes when the process dies twice then the latest input is restored`() {
        val editor = editor()
        editor.type("3")
        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.type("4")
        editor.resize(DeviceConfig.FOLDABLE_CLOSED)
        editor.type("5")

        editor.killProcess()

        assertTyped(editor, "345")
        editor.resize(DeviceConfig.TABLET_LANDSCAPE)
        assertTyped(editor, "345")
        editor.killProcess()
        assertTyped(editor, "345")
    }

    @Test
    fun `given a typed length when the task is discarded then the input comes back from the stored draft`() {
        val editor = editor()
        editor.type("3", "4", "5")
        assertEquals(EditorDraft(PROJECT_ID, ROOM_ID, "345"), editor.storedDraft())

        editor.killProcess(keepInstanceState = false)

        assertTyped(editor, "345")
        editor.type("OK")
        assertEquals(
            3450L,
            editor
                .room()
                .walls
                .last()
                .length.mm,
        )
    }

    @Test
    fun `given a confirmed wall when the process dies then it is stored once and the input is empty`() {
        val editor = editor()
        editor.type("3", "0", "0", "OK")

        editor.killProcess()

        assertEquals(4, editor.room().walls.size)
        assertEquals(
            4,
            editor.state.selectedRoom
                ?.walls
                ?.size,
        )
        assertEquals("", editor.state.inputText)
        assertNull(editor.storedDraft())
    }

    @Test
    fun `given a save in progress when the window changes then the wall is stored exactly once`() {
        val editor = editor()
        editor.type("3", "0", "0")
        val gate = CompletableDeferred<Unit>()
        editor.saver.gate = gate
        editor.type("OK")

        editor.tour { assertEquals(SaveStatus.Saving, editor.state.save) }
        gate.complete(Unit)
        editor.settle()

        assertEquals(4, editor.room().walls.size)
        assertEquals(
            4,
            editor.state.selectedRoom
                ?.walls
                ?.size,
        )
        assertEquals("", editor.state.inputText)
    }

    @Test
    fun `given an empty project when the window changes and the process dies then the first wall can be entered`() {
        val editor = ConfigEditor(compose, emptyProject()).launch(DeviceConfig.PHONE_LANDSCAPE)
        editor.type("4")
        editor.tour { compose.onNodeWithContentDescription("Wall 1, 4 centimeters").assertExists() }
        editor.killProcess()
        editor.type("0", "0", "OK")

        assertEquals(
            4000L,
            editor
                .room()
                .walls
                .single()
                .length.mm,
        )
    }

    private companion object {
        /** Characters of the prefilled thickness, enough to delete it completely. */
        const val PREFILLED_THICKNESS_CHARS = 4
    }
}
