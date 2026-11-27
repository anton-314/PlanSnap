package app.plansnap.feature.editor.hints

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.plansnap.feature.editor.save.WRITE_FAILED
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.feature.editor.state.SaveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tolerance of bounds in pixels at mdpi. */
private const val SLACK = 0.5f

/** The hint on the whole editor screen of a 360 x 640 dp phone: it stays inside the plan canvas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class HintsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(Situation.EMPTY.state())

    private fun show(
        initial: EditorUiState,
        fontScale: Float,
    ) {
        state = initial
        compose.setContent { HintsEditorHost(state, {}, fontScale) }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun closeButtons() = compose.onNodeWithContentDescription("Close hint")

    /** The hint, if any, lies inside the plan canvas, so neither the status card nor the input bar is covered. */
    private fun assertHintInsidePlan(situation: Situation) {
        val plan = bounds(PLAN_TAG)
        val text = compose.onNodeWithText("Hint:", substring = true).fetchSemanticsNode().boundsInRoot
        val close = closeButtons().fetchSemanticsNode().boundsInRoot
        listOf(text, close).forEach {
            assertTrue("$situation: $it in $plan", it.top >= plan.top - SLACK && it.bottom <= plan.bottom + SLACK)
        }
        closeButtons().assertIsDisplayed()
    }

    private fun check(fontScale: Float): List<Situation> {
        val hidden = mutableListOf<Situation>()
        show(Situation.EMPTY.state(), fontScale)
        Situation.entries.forEach { situation ->
            state = situation.state()
            compose.waitForIdle()
            val plan = bounds(PLAN_TAG)
            if (plan.height >= HINT_MIN_HEIGHT.value) {
                assertHintInsidePlan(situation)
            } else {
                closeButtons().assertDoesNotExist()
                hidden += situation
            }
            compose.onNode(hasText("OK") and hasClickAction()).assertExists()
        }
        return hidden
    }

    @Test
    fun `given normal font size when each situation is shown then every hint fits into the plan`() {
        val hidden = check(fontScale = 1f)

        assertTrue("hidden: $hidden", hidden.isEmpty())
    }

    @Test
    fun `given font scale 2 when each situation is shown then no hint leaves the plan`() {
        val hidden = check(fontScale = 2f)

        // Without a status card the plan keeps 96 dp, which holds the hint. With the card it is lower than the hint.
        assertEquals(listOf(Situation.PROPOSAL, Situation.GAP), hidden)
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a card that leaves too little plan when shown then the hint waits instead of covering the card`() {
        show(Situation.GAP.state(), fontScale = 2f)

        assertTrue(bounds(PLAN_TAG).height < HINT_MIN_HEIGHT.value)
        compose.onNodeWithText("Hinweis:", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Hinweis schließen").assertDoesNotExist()
        compose.onNodeWithTag(CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `given a failed save at font scale 2 when shown then only its message is on the plan`() {
        show(Situation.EMPTY.state().copy(save = SaveStatus.Failed(WRITE_FAILED)), fontScale = 2f)

        compose.onNodeWithText("Save again").assertIsDisplayed()
        closeButtons().assertDoesNotExist()
    }
}
