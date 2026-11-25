package app.plansnap.feature.editor.input

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h1200dp")
class InputBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val log = IntentLog()

    private fun show(
        state: EditorUiState,
        extraKeys: List<ExtraKey> = emptyList(),
    ) {
        compose.setContent {
            PlanSnapTheme {
                val (current, onIntent) = rememberFakeEditor(state, log)
                InputBar(state = current, onIntent = onIntent, extraKeys = extraKeys)
            }
        }
    }

    private fun key(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and hasClickAction())

    private fun typedTexts() = log.intents.filterIsInstance<CoreIntent.InputChanged>().map { it.text }

    @Test
    fun `given an empty input when digits are tapped then the text grows and is shown live`() {
        show(editorState())

        key("3").performClick()
        key("4").performClick()
        key("5").performClick()

        assertEquals(listOf("3", "34", "345"), typedTexts())
        compose.onNodeWithContentDescription("Wall 1, 345 centimeters").assertIsDisplayed()
    }

    @Test
    fun `given a typed digit when backspace is tapped then it is deleted`() {
        show(editorState(input = "345"))

        compose.onNodeWithContentDescription("Delete last character").performClick()

        assertEquals(listOf("34"), typedTexts())
    }

    @Test
    fun `given the english locale when the separator is tapped then a point is typed`() {
        show(editorState(input = "3"))

        compose.onNodeWithContentDescription("Decimal point").performClick()

        assertEquals(listOf("3."), typedTexts())
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1200dp")
    fun `given the german locale when the separator is tapped then a comma is typed`() {
        show(editorState(input = "3"))

        compose.onNodeWithContentDescription("Komma").performClick()

        assertEquals(listOf("3,"), typedTexts())
    }

    @Test
    fun `given a valid input when ok is tapped then the wall is confirmed`() {
        show(editorState(input = "345"))

        key("OK").assertIsEnabled().performClick()

        assertEquals(listOf<Any>(CoreIntent.ConfirmWall), log.intents)
    }

    @Test
    fun `given an empty input then ok is disabled`() {
        show(editorState())

        key("OK").assertIsNotEnabled()
    }

    @Test
    fun `given an invalid input then the error is explained and ok is disabled`() {
        show(editorState(input = "3,4,", error = malformedLength()))

        compose
            .onNodeWithText("This is not a valid length. Use digits and one decimal separator.")
            .assertIsDisplayed()
        key("OK").assertIsNotEnabled()
    }

    @Test
    fun `given a closed room then the keys are disabled and the reason is shown`() {
        show(editorState(walls = 3, closed = true))

        key("5").assertIsNotEnabled()
        compose.onNodeWithText("This room is closed. Select another room to add walls.").assertIsDisplayed()
    }

    @Test
    fun `given the unit setting then the unit is visible next to the value`() {
        show(editorState(input = "3,45", settings = AppSettings(lengthUnit = LengthUnit.METER)))

        compose.onNodeWithText("m").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wall 1, 3,45 meters").assertIsDisplayed()
    }

    @Test
    fun `given a room with walls then the next wall number is shown`() {
        show(editorState(walls = 3))

        compose.onNodeWithText("Wall 4").assertIsDisplayed()
    }

    @Test
    fun `given the keypad then every key is at least 56 dp wide and high and no text field exists`() {
        show(editorState())

        for (label in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "OK")) {
            key(label).assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        }
        compose
            .onNodeWithContentDescription(
                "Delete last character",
            ).assertWidthIsAtLeast(56.dp)
            .assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription("Decimal point").assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `given the corner choice then the selected side is marked and a tap sets the corner`() {
        show(editorState(walls = 1))

        compose.onNodeWithContentDescription("Next wall turns left").assertIsSelected()
        compose.onNodeWithContentDescription("Next wall turns right").performClick()

        assertEquals(
            listOf<Any>(CoreIntent.SetNextCorner(CornerSpec.DeclaredRight(TurnDirection.RIGHT))),
            log.intents,
        )
    }

    @Test
    fun `given extra keys then they are shown and react`() {
        var clicks = 0
        show(editorState(), listOf(ExtraKey("D", "Wall thickness", { clicks++ })))

        compose.onNodeWithContentDescription("Wall thickness").assertWidthIsAtLeast(56.dp).performClick()

        assertEquals(1, clicks)
    }
}
