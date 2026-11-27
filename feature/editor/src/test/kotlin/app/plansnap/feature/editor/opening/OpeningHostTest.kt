package app.plansnap.feature.editor.opening

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h1000dp")
class OpeningHostTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: EditorUiState,
        vararg opening: OpeningIntent,
    ): OpeningHarness {
        val harness = OpeningHarness(state)
        opening.forEach(harness::onIntent)
        compose.setContent {
            PlanSnapTheme {
                val current by harness.stateAsState()
                OpeningHost(current, harness::onIntent)
            }
        }
        compose.waitForIdle()
        return harness
    }

    private val state get() = stateOf(openingRoom(door("o1", "w0", 0, 900), door("o2", "w0", 3500, 900)))

    @Test
    fun `given no sheet when hosted then nothing is shown`() {
        show(state)
        compose.onNodeWithText("Wall 1").assertDoesNotExist()
    }

    @Test
    fun `given a wall when its sheet is shown then each opening has its status and the add actions are large`() {
        val harness = show(state, OpeningIntent.OpenWall(SHAPE_ROOM, WallId("w0")))
        compose.onNodeWithText("Wall 1").assertIsDisplayed()
        compose.onNodeWithText("Door, 90 cm wide, starts 0 cm from the start of the wall").assertIsDisplayed()
        compose.onNodeWithText("Fits on the wall").assertIsDisplayed()
        compose.onNodeWithText("Does not fit on the wall").assertIsDisplayed()
        compose.onNodeWithText("Add door").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Add window").performClick()
        compose.waitForIdle()
        assertEquals(OpeningType.WINDOW, harness.form.draft.type)
        compose.onNodeWithText("New opening").assertIsDisplayed()
    }

    @Test
    fun `given an empty wall when its sheet is shown then the empty text explains it`() {
        show(state, OpeningIntent.OpenWall(SHAPE_ROOM, WallId("w1")))
        compose.onNodeWithText("This wall has no openings yet.").assertIsDisplayed()
    }

    @Test
    fun `given an opening row when tapped then its form opens`() {
        val harness = show(state, OpeningIntent.OpenWall(SHAPE_ROOM, WallId("w0")))
        compose.onNode(hasText("Door, 90 cm wide, starts 0 cm", substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(OpeningId("o1"), harness.form.editingId)
        compose.onNodeWithText("Edit opening").assertIsDisplayed()
    }

    @Test
    fun `given a new door when values are typed then the form stores it`() {
        val harness = show(state, OpeningIntent.Add(SHAPE_ROOM, WallId("w1"), OpeningType.DOOR))
        compose.onNodeWithText("New opening").assertIsDisplayed()
        compose.onNodeWithText("Measured from corner 2", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Distance (cm)").performTextClearance()
        compose.onNodeWithText("Distance (cm)").performTextInput("25")
        compose.onNodeWithText("Hinge right, opens out of the room").performClick()
        compose
            .onNodeWithText("Save opening")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.waitForIdle()
        val edit = harness.submitted.single() as ProjectEdit.AddOpening
        assertEquals(250, edit.opening.offset.mm)
        assertEquals(WallId("w1"), edit.opening.wallId)
        compose.onNodeWithText("New opening").assertDoesNotExist()
    }

    @Test
    fun `given a door that is too wide when typed then the sheet explains the conflict and blocks saving`() {
        show(state, OpeningIntent.Add(SHAPE_ROOM, WallId("w1"), OpeningType.DOOR))
        compose.onNodeWithText("Width (cm)").performTextClearance()
        compose.onNodeWithText("Width (cm)").performTextInput("500")
        compose.waitForIdle()
        compose.onNodeWithText("The opening does not fit on its wall.").assertExists()
        compose.onNodeWithText("Save opening").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `given text that is no number when typed then the field is named`() {
        show(state, OpeningIntent.Add(SHAPE_ROOM, WallId("w1"), OpeningType.DOOR))
        compose.onNodeWithText("Width (cm)").performTextClearance()
        compose.onNodeWithText("Width (cm)").performTextInput("x")
        compose.waitForIdle()
        compose.onNode(hasText("The opening width", substring = true)).assertExists()
        compose.onNodeWithText("Save opening").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `given a window when its form is shown then the sill field exists and a door has the swing choice`() {
        show(state, OpeningIntent.Add(SHAPE_ROOM, WallId("w1"), OpeningType.WINDOW))
        compose.onNodeWithText("Sill height (cm)").assertExists()
        compose.onNodeWithText("Hinge left, opens into the room").assertDoesNotExist()
        compose.onNodeWithText("Door").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hinge left, opens into the room").assertExists()
        compose.onNodeWithText("Sill height (cm)").assertDoesNotExist()
    }

    @Test
    fun `given a stored opening when deleted then the dialog asks first and then removes it`() {
        val harness = show(state, OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")))
        compose.onNodeWithText("Delete opening").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete opening?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertTrue(harness.submitted.isEmpty())
        compose.onNodeWithText("Delete opening").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertEquals(listOf<ProjectEdit>(ProjectEdit.RemoveOpening(SHAPE_ROOM, OpeningId("o1"))), harness.submitted)
    }

    @Test
    fun `given a new door when no optional value is chosen then it is stored without them`() {
        val harness = show(state, OpeningIntent.Add(SHAPE_ROOM, WallId("w1"), OpeningType.DOOR))
        compose.onNodeWithText("Not specified").assertIsSelected()
        compose.onNodeWithText("Hinge left, opens into the room").performClick()
        compose.onNodeWithText("Not specified").assertIsNotSelected().performClick()
        compose.onNodeWithText("Save opening").performScrollTo().performClick()
        compose.waitForIdle()
        val opening = (harness.submitted.single() as ProjectEdit.AddOpening).opening
        assertNull(opening.swing)
        assertNull(opening.height)
    }

    @Test
    fun `given an opening on a far wall when its form is shown then that wall chip is visible and selected`() {
        val room = roomOf(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000)
        show(stateOf(room), OpeningIntent.Add(SHAPE_ROOM, WallId("w10"), OpeningType.PASSAGE))
        compose.onNode(hasText("Wall 11") and isSelectable()).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `given an opening that disappears when its form is open then the sheet closes`() {
        val harness = show(state, OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")))
        harness.host.update { current ->
            val project = current.project!!
            val room = project.rooms.single()
            val remaining = room.copy(openings = persistentListOf(room.openings.last()))
            current.copy(project = project.copy(rooms = persistentListOf(remaining)))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Edit opening").assertDoesNotExist()
        assertNull(harness.opening.sheet)
    }
}
