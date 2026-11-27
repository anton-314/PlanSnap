package app.plansnap.feature.editor.config

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.feature.editor.details.DetailsState
import app.plansnap.feature.editor.opening.OpeningIntent
import app.plansnap.feature.editor.opening.OpeningState
import app.plansnap.feature.editor.shape.ShapeState
import app.plansnap.feature.editor.shape.largeCorrectionRoom
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorSectionState
import app.plansnap.feature.editor.state.closedProject
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/** Every sheet and dialog of the editor stays open with its typed values across window changes (REQ-UX-009). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SheetSurvivalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun editor(
        project: Project,
        device: DeviceConfig = DeviceConfig.PHONE_PORTRAIT,
    ) = ConfigEditor(compose, project).launch(device)

    /** Four measured walls that the user has not closed yet. */
    private fun openRectangle(): Project {
        val project = closedProject(3000)
        return project.copy(rooms = persistentListOf(project.rooms.single().copy(isClosed = false)))
    }

    /** Opens the adjustment sheet from the status card, which is expanded in a high window. */
    private fun ConfigEditor.openAdjustment() = tap(key("Adjust"))

    private fun ConfigEditor.room() = rig.stored.rooms.single()

    /** Walks through all windows: the section is unchanged and [title] is still shown in every one. */
    private fun <T : EditorSectionState> ConfigEditor.tourOpenSheet(
        section: KClass<T>,
        title: String,
        check: () -> Unit = {},
    ) {
        val before = state.section(section)
        assertNotNull(before)
        tour {
            assertEquals(before, state.section(section))
            compose.onNodeWithText(title, substring = true).assertExists()
            check()
        }
    }

    private fun assertValue(description: String) {
        compose.onNodeWithContentDescription(description).assertExists()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun tagged(tag: String) = compose.onNodeWithTag(tag)

    private fun fill(
        tag: String,
        text: String,
    ) {
        tagged(tag).performScrollTo().performTextReplacement(text)
    }

    @Test
    fun `given a typed corner angle when the window changes then the sheet keeps it and stores it`() {
        val editor = editor(openProject())
        editor.tapDescribed("Enter corner angle")
        editor.tapSheet("Right")
        editor.sheetType("7")

        editor.tourOpenSheet(ShapeState::class, "Corner before wall 4") { assertValue("Corner angle, 7 degrees") }

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.sheetType("5", "OK")
        assertValue("Corner angle 75 degrees, change")
    }

    @Test
    fun `given a diagonal with chosen corners when the window changes then corners and length stay`() {
        val editor = editor(openRectangle())
        editor.tapDescribed("Add diagonal")
        editor.tap(compose.onAllNodesWithContentDescription("Corner 2").onFirst())
        editor.tapDescribed("Corner 4")
        editor.sheetType("5")
        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.sheetType("0")

        editor.tourOpenSheet(ShapeState::class, "Measure the straight line between two corners.") {
            assertValue("Diagonal, 50 centimeters")
        }

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.sheetType("0", "OK")
        val diagonal = editor.room().diagonals.single()
        assertEquals(Length(5000), diagonal.length)
        assertEquals(1 to 3, diagonal.fromCorner to diagonal.toCorner)
    }

    @Test
    fun `given a typed thickness when the window changes then the sheet keeps it and applies it`() {
        val editor = editor(openProject())
        editor.tapDescribed("Wall thickness")
        repeat(PREFILLED_CHARS) { editor.tapDescribed("Delete last character") }
        editor.sheetType("2")

        editor.tourOpenSheet(ShapeState::class, "Applies to the next wall.") {
            assertValue("Wall thickness, 2 centimeters")
        }

        editor.sheetType("5", "OK")
        assertEquals(Length(250), editor.state.thickness)
    }

    @Test
    fun `given the adjustment sheet when the window changes then it stays and can be applied`() {
        val editor = editor(closedProject(SIMPLE_GAP_MM), DeviceConfig.TABLET_PORTRAIT)
        editor.openAdjustment()

        editor.tourOpenSheet(ShapeState::class, "Adjust measurements")
        assertNull(editor.room().adjustment)

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Apply adjustment")
        assertNotNull(editor.room().adjustment)
    }

    @Test
    fun `given the dialog for a large correction when the window changes then nothing is applied until confirmed`() {
        val project = emptyProject().copy(rooms = persistentListOf(largeCorrectionRoom()))
        val editor = editor(project, DeviceConfig.TABLET_PORTRAIT)
        editor.openAdjustment()
        editor.tapSheet("Review large correction")

        editor.tourOpenSheet(ShapeState::class, "Apply large correction?")
        assertNull(editor.room().adjustment)

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Apply correction")
        assertNotNull(editor.room().adjustment)
    }

    @Test
    fun `given a filled opening form when the window changes then every value stays and is saved`() {
        val editor = editor(closedProject(3000))
        editor.chooseFromMenu("Add door")
        field("Distance").performScrollTo().performTextReplacement("35")
        field("Width").performScrollTo().performTextReplacement("80")
        field("Height").performScrollTo().performTextReplacement("201")
        editor.tapSheet("Hinge right, opens into the room")
        editor.tapSheet("Wall 2")

        editor.tourOpenSheet(OpeningState::class, "New opening") {
            field("Distance").assertTextContains("35")
            field("Width").assertTextContains("80")
            field("Height").assertTextContains("201")
        }

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Save opening")
        val opening = editor.room().openings.single()
        assertEquals(OpeningType.DOOR, opening.type)
        assertEquals(Length(350), opening.offset)
        assertEquals(Length(800), opening.width)
        assertEquals(Length(2010), opening.height)
        assertEquals(DoorSwing.HINGE_RIGHT_INWARD, opening.swing)
        assertEquals(editor.room().walls[1].id, opening.wallId)
    }

    @Test
    fun `given the question to delete an opening when the window changes then it stays and deletes on confirm`() {
        val editor = editor(closedProject(3000))
        editor.chooseFromMenu("Add door")
        editor.tapSheet("Save opening")
        val room = editor.room()
        editor.viewModel.onIntent(OpeningIntent.Edit(room.id, room.openings.single().id))
        editor.settle()
        editor.tapSheet("Delete opening")

        editor.tourOpenSheet(OpeningState::class, "Delete opening?")
        assertEquals(1, editor.room().openings.size)

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Delete")
        assertTrue(editor.room().openings.isEmpty())
    }

    @Test
    fun `given the openings of a wall when the window changes then the list stays open`() {
        val editor = editor(closedProject(3000))
        val room = editor.room()
        editor.viewModel.onIntent(OpeningIntent.OpenWall(room.id, room.walls.first().id))
        editor.settle()

        editor.tourOpenSheet(OpeningState::class, "This wall has no openings yet.")
    }

    @Test
    fun `given edited room details when the window changes then every field stays and is applied`() {
        val editor = editor(closedProject(3000))
        editor.openMenu()
        editor.tap(tagged("menu-room-details"))
        fill("details-name", "Study")
        editor.tapSheet("Office")
        fill("details-notes", "North side\nNew floor")
        fill("details-height", "260")
        fill("details-factor", "50")
        editor.settle()

        editor.tourOpenSheet(DetailsState::class, "Room details") {
            tagged("details-name").assertTextContains("Study")
            tagged("details-notes").assertTextContains("North side\nNew floor")
            tagged("details-height").assertTextContains("260")
            tagged("details-factor").assertTextContains("50")
        }

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Apply")
        val room = editor.room()
        assertEquals("Study", room.name)
        assertEquals("office", room.typeKey?.value)
        assertEquals("North side\nNew floor", room.notes)
        assertEquals(Length(2600), room.heightOverride)
        assertEquals(50, room.areaFactor.percent)
    }

    @Test
    fun `given the area overview with a typed height when the window changes then it stays and is applied`() {
        val editor = editor(closedProject(3000))
        editor.chooseFromMenu("Rooms and areas")
        fill("details-project-height", "275")
        editor.settle()

        editor.tourOpenSheet(DetailsState::class, "Totals") {
            tagged("details-project-height").assertTextContains("275")
        }

        editor.resize(DeviceConfig.PHONE_LANDSCAPE)
        editor.tapSheet("Apply default height")
        assertEquals(Length(2750), editor.rig.stored.defaultHeight)
    }

    @Test
    fun `given an open sheet when the process dies then nothing half typed is stored and the wall input stays`() {
        val editor = editor(openProject())
        editor.viewModel.onIntent(CoreIntent.InputChanged("12"))
        editor.tapDescribed("Add diagonal")
        editor.sheetType("5")
        val before = editor.rig.stored

        editor.killProcess()

        assertEquals(before, editor.rig.stored)
        assertEquals("12", editor.state.inputText)
        editor.tapDescribed("Add diagonal")
        compose.onNodeWithText("Measure the straight line between two corners.", substring = true).assertExists()
    }

    private companion object {
        const val PREFILLED_CHARS = 4

        /** A last wall of 303 cm leaves a gap that a simple confirmation closes. */
        const val SIMPLE_GAP_MM = 3030L
    }
}
