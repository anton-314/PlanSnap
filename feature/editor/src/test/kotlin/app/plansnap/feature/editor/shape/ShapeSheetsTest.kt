package app.plansnap.feature.editor.shape

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorUiState
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
class ShapeSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: EditorUiState,
        vararg opening: ShapeIntent,
    ): HandlerEditor {
        val editor = HandlerEditor(state)
        opening.forEach(editor::onIntent)
        editor.intents.clear()
        compose.setContent {
            PlanSnapTheme {
                val current by editor.stateAsState()
                ShapeSheetHost(current, editor::onIntent)
            }
        }
        compose.waitForIdle()
        return editor
    }

    private fun key(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and hasClickAction())

    private fun type(vararg labels: String) {
        labels.forEach { key(it).performClick() }
        compose.waitForIdle()
    }

    @Test
    fun `given the angle sheet when digits are typed then the angle is shown and confirmed as the next corner`() {
        val editor = show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenCorner())
        compose.onNodeWithText("Corner angle").assertIsDisplayed()
        compose.onNodeWithText("Corner before wall 4").assertIsDisplayed()
        key("OK").assertIsNotEnabled()

        type("7", "5")
        compose.onNodeWithContentDescription("Corner angle, 75 degrees").assertExists()
        key("OK").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(CornerSpec.Measured(TurnDirection.LEFT, Angle(75_000)), editor.host.state.value.nextCorner)
        compose.onNodeWithText("Corner angle").assertDoesNotExist()
    }

    @Test
    fun `given the angle sheet when the direction is changed then the corner turns right`() {
        val editor = show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenCorner())
        compose.onNodeWithContentDescription("Next wall turns right").performClick()
        type("6", "0")
        key("OK").performClick()
        compose.waitForIdle()
        assertEquals(CornerSpec.Measured(TurnDirection.RIGHT, Angle(60_000)), editor.host.state.value.nextCorner)
    }

    @Test
    fun `given an angle of zero when typed then the sheet explains the range and ok stays disabled`() {
        show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenCorner())
        type("0")
        compose.onNodeWithText("The angle must be greater than 0° and at most 180°.").assertIsDisplayed()
        key("OK").assertIsNotEnabled()
    }

    @Test
    fun `given the keypad of a sheet then every key is at least 56 dp`() {
        show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenCorner())
        listOf("1", "5", "0", "OK").forEach { key(it).assertHeightIsAtLeast(56.dp) }
    }

    @Test
    fun `given a stored corner when its sheet is confirmed then SetCorner is submitted for that corner`() {
        val editor = show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenCorner(CornerTarget.Existing(2)))
        compose.onNodeWithText("Corner 3").assertIsDisplayed()
        type("1", "0", "5")
        key("OK").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf<Any>(ProjectEdit.SetCorner(SHAPE_ROOM, 2, CornerSpec.Measured(TurnDirection.LEFT, Angle(105_000)))),
            editor.host.submitted,
        )
    }

    @Test
    fun `given the diagonal sheet when corners and length are chosen then the diagonal is submitted`() {
        val state = stateOf(roomOf(3000, 3000, 3000, 5000, closed = true))
        val editor = show(state, ShapeIntent.OpenDiagonal())
        compose.onNodeWithText("From corner").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Corner 4")[1].performClick()
        compose.waitForIdle()
        assertEquals(ShapeSheet.Diagonal(0, 3, ""), editor.host.shape.sheet)
        type("5", "0", "0")
        key("OK").performClick()
        compose.waitForIdle()
        val edit = editor.host.submitted.single() as ProjectEdit.AddDiagonal
        assertEquals(
            Triple(0, 3, Length(5000)),
            Triple(edit.diagonal.fromCorner, edit.diagonal.toCorner, edit.diagonal.length),
        )
    }

    @Test
    fun `given the same corner twice when chosen then the sheet says a diagonal needs two corners`() {
        val editor = show(stateOf(roomOf(3000, 3000, 3000, 5000, closed = true)), ShapeIntent.OpenDiagonal(1, 1))
        compose.onNodeWithText("A diagonal must connect two different corners of the room.").assertIsDisplayed()
        type("5", "0", "0")
        key("OK").assertIsNotEnabled()
        assertTrue(editor.host.submitted.isEmpty())
        assertEquals(DomainError.RuleViolation(DomainRule.DIAGONAL_INVALID_CORNERS), editor.host.shape.error)
    }

    @Test
    fun `given the thickness sheet when a value is typed then it is kept for the next wall`() {
        val editor = show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenThickness)
        compose.onNodeWithText("Wall thickness").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete last character").performClick()
        compose.onNodeWithContentDescription("Delete last character").performClick()
        compose.onNodeWithContentDescription("Delete last character").performClick()
        compose.onNodeWithContentDescription("Delete last character").performClick()
        type("2", "5")
        key("OK").performClick()
        compose.waitForIdle()
        assertEquals(Length(250), editor.host.state.value.thicknessOverride)
    }

    @Test
    fun `given a simple proposal when the adjustment is applied then the simple confirmation is sent`() {
        val room = almostRectangle(3030)
        val editor = show(stateOf(room), ShapeIntent.OpenAdjustment)
        compose.onNodeWithText("Adjust measurements").assertIsDisplayed()
        compose.onNodeWithText("This correction is large. You must confirm it separately.").assertDoesNotExist()
        compose.onNodeWithText("Apply adjustment").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(CoreIntent.AcceptAdjustment(SHAPE_ROOM, proposalOf(room), AdjustmentConfirmation.SIMPLE)),
            editor.sent<CoreIntent.AcceptAdjustment>(),
        )
        assertNull(editor.host.shape.sheet)
    }

    @Test
    fun `given a large correction when the sheet button is tapped then only the dialog opens`() {
        val editor = show(stateOf(largeCorrectionRoom()), ShapeIntent.OpenAdjustment)
        compose.onNodeWithText("This correction is large. You must confirm it separately.").assertIsDisplayed()
        compose.onNodeWithText("Apply adjustment").assertDoesNotExist()
        compose.onNodeWithText("Review large correction").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Apply large correction?").assertIsDisplayed()
        assertTrue(editor.sent<CoreIntent.AcceptAdjustment>().isEmpty())
    }

    @Test
    fun `given the dialog when it is cancelled then nothing is accepted and the sheet stays`() {
        val editor = show(stateOf(largeCorrectionRoom()), ShapeIntent.OpenAdjustment)
        compose.onNodeWithText("Review large correction").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Check measurements first").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Apply large correction?").assertDoesNotExist()
        compose.onNodeWithText("Adjust measurements").assertIsDisplayed()
        assertTrue(editor.sent<CoreIntent.AcceptAdjustment>().isEmpty())
    }

    @Test
    fun `given the dialog when the correction is applied then exactly one explicit confirmation is sent`() {
        val room = largeCorrectionRoom()
        val editor = show(stateOf(room), ShapeIntent.OpenAdjustment)
        compose.onNodeWithText("Review large correction").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Apply correction").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(CoreIntent.AcceptAdjustment(SHAPE_ROOM, proposalOf(room), AdjustmentConfirmation.EXPLICIT)),
            editor.sent<CoreIntent.AcceptAdjustment>(),
        )
        assertNull(editor.host.shape.sheet)
    }

    @Test
    fun `given the adjustment sheet when its proposal disappears then the sheet closes itself`() {
        val editor = show(stateOf(almostRectangle(3030)), ShapeIntent.OpenAdjustment)
        compose.onNodeWithText("Adjust measurements").assertIsDisplayed()
        editor.evaluated(evaluate(almostRectangle(3030).withOverlay()))
        compose.waitForIdle()
        compose.onNodeWithText("Adjust measurements").assertDoesNotExist()
        assertNull(editor.host.shape.sheet)
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1000dp")
    fun `given the german locale when the angle sheet opens then its texts and labels are german`() {
        show(stateOf(roomOf(4000, 3000, 4000)), ShapeIntent.OpenCorner())
        compose.onNodeWithText("Eckenwinkel").assertIsDisplayed()
        compose.onNodeWithText("Ecke vor Wand 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Komma").assertExists()
    }
}
