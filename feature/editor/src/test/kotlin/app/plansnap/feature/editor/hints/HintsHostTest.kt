package app.plansnap.feature.editor.hints

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.save.WRITE_FAILED
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.SaveStatus
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NUMPAD_TEXT = "Hint: Type the wall length on the number pad, then tap OK."

/** The hint itself: its texts, its close button and when it is on the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class HintsHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<EditorIntent>()
    private var state by mutableStateOf(editorState())

    private fun show(
        initial: EditorUiState = editorState(),
        height: Dp = 300.dp,
    ) {
        state = initial
        compose.setContent {
            PlanSnapTheme {
                Box(Modifier.width(360.dp).height(height)) {
                    HintsHost(state, { intents += it })
                }
            }
        }
    }

    private fun become(next: EditorUiState) {
        state = next
        compose.waitForIdle()
    }

    private fun closeButton() = compose.onNodeWithContentDescription("Close hint")

    private fun assertOneHint(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
        closeButton().assertIsDisplayed()
        compose.onNodeWithText("Hint:", substring = true).assertExists()
    }

    private fun assertNothing() {
        compose.onNodeWithText("Hint:", substring = true).assertDoesNotExist()
        closeButton().assertDoesNotExist()
    }

    @Test
    fun `given an empty room when shown then the number pad hint and a close button appear`() {
        show()

        assertOneHint(NUMPAD_TEXT)
        closeButton()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `given each situation when shown then its text appears and closing sends its hint`() {
        val texts =
            mapOf(
                Situation.EMPTY to NUMPAD_TEXT,
                Situation.ONE_WALL to "Hint: “Left” or “Right” sets where the next wall turns.",
                Situation.THREE_WALLS to "Hint: All walls entered? “Close” checks the room.",
                Situation.PROPOSAL to "Hint: A measured diagonal (key “Diag”) helps to confirm the shape.",
                Situation.GAP to "Hint: “Adjust” below the plan shows the corrections before anything changes.",
            )
        show()

        texts.forEach { (situation, text) ->
            become(situation.state())
            assertOneHint(text)
            closeButton().performClick()
        }

        val expected =
            listOf(
                EditorHint.NUMPAD,
                EditorHint.DIRECTION,
                EditorHint.CLOSE,
                EditorHint.DIAGONAL,
                EditorHint.ADJUSTMENT,
            ).map { HintsIntent.Dismiss(it) }
        assertEquals(expected, intents)
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given German when shown then the texts are German`() {
        show()

        compose.onNodeWithText("Hinweis: Wandlänge mit dem Ziffernblock eingeben, dann OK.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hinweis schließen").assertIsDisplayed()
    }

    @Test
    fun `given a shown hint when it appears then screen readers announce it politely`() {
        show()

        compose
            .onNodeWithText(NUMPAD_TEXT)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `given a dismissed hint when shown then nothing is composed`() {
        val dismissed =
            editorState().withSection(
                HintsState::class,
                HintsState(persistentSetOf(EditorHint.NUMPAD)),
            )

        show(dismissed)

        assertNothing()
    }

    @Test
    fun `given an area lower than the close button when shown then nothing appears until there is room`() {
        show(height = HINT_MIN_HEIGHT - 1.dp)

        assertNothing()
        assertEquals(emptyList<EditorIntent>(), intents)
    }

    @Test
    fun `given the lowest possible area when shown then the close button fits`() {
        show(height = HINT_MIN_HEIGHT)

        closeButton().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `given a failed save when it is retried then the hint stays away until a save succeeded`() {
        show()

        become(editorState().copy(save = SaveStatus.Failed(WRITE_FAILED)))
        assertNothing()
        become(editorState().copy(save = SaveStatus.Saving))
        assertNothing()
        become(editorState().copy(save = SaveStatus.Saved(1L)))
        assertOneHint(NUMPAD_TEXT)
    }

    @Test
    fun `given a running save without a failure when shown then the hint stays`() {
        show(editorState().copy(save = SaveStatus.Saving))

        assertOneHint(NUMPAD_TEXT)
    }
}
