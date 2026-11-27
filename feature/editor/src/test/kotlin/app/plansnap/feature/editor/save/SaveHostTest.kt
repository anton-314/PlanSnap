package app.plansnap.feature.editor.save

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.shape.assertTextComplete
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.SaveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The failed-save message on the editor screen: when it is shown, what it says and what it leaves uncovered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class SaveHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<EditorIntent>()
    private var status by mutableStateOf<SaveStatus>(SaveStatus.Idle)

    private fun show(
        initial: SaveStatus,
        fontScale: Float = 1f,
    ) {
        status = initial
        compose.setContent { SaveEditorHost(editorState(walls = 2).copy(save = status), { intents += it }, fontScale) }
    }

    private fun become(next: SaveStatus) {
        status = next
        compose.waitForIdle()
    }

    private fun title() = compose.onNodeWithText("Not saved")

    private fun retry() = compose.onNode(hasText("Save again") and hasClickAction())

    private fun assertHidden() {
        title().assertDoesNotExist()
        retry().assertDoesNotExist()
    }

    private fun assertShown() {
        title().assertIsDisplayed()
        retry().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `given no failure when idle saving or saved then no message exists`() {
        show(SaveStatus.Idle)
        assertHidden()
        become(SaveStatus.Saving)
        assertHidden()
        become(SaveStatus.Saved(1L))
        assertHidden()
    }

    @Test
    fun `given a failed save when shown then title explanation and button are readable`() {
        show(FAILED)
        assertShown()
        compose.assertTextComplete("Not saved")
        compose.assertTextComplete("Leaving the editor loses changes.")
        compose.assertTextComplete("Save again")
    }

    @Test
    fun `given a full device when shown then the message asks to free space`() {
        show(SaveStatus.Failed(NO_SPACE))
        compose.assertTextComplete("Storage is full. Please free up space.")
        compose.onNodeWithText("Leaving the editor loses changes.").assertDoesNotExist()
    }

    @Test
    fun `given the message when it appears then screen readers announce it politely`() {
        show(FAILED)
        title().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `given the message when the button is tapped then exactly one retry is sent`() {
        show(FAILED)
        retry().performClick()
        assertEquals(listOf<EditorIntent>(CoreIntent.RetrySave), intents)
        assertShown()
    }

    @Test
    fun `given a failure when further saves run and fail then the message stays until one succeeds`() {
        show(FAILED)
        become(SaveStatus.Saving)
        assertShown()
        become(SaveStatus.Failed(NO_SPACE))
        compose.onNodeWithText("Storage is full. Please free up space.").assertIsDisplayed()
        become(SaveStatus.Saving)
        compose.onNodeWithText("Storage is full. Please free up space.").assertIsDisplayed()
        become(SaveStatus.Saved(2L))
        assertHidden()
        become(SaveStatus.Saving)
        assertHidden()
    }

    @Test
    fun `given a retry runs after a failure when the screen is recreated then the message is still there`() {
        val restoration = StateRestorationTester(compose)
        status = FAILED
        restoration.setContent { SaveEditorHost(editorState().copy(save = status), {}) }
        become(SaveStatus.Saving)
        restoration.emulateSavedInstanceStateRestore()
        assertShown()
    }

    @Test
    fun `given a restored message when the project is opened anew then it is gone`() {
        val restoration = StateRestorationTester(compose)
        status = FAILED
        restoration.setContent { SaveEditorHost(editorState().copy(save = status), {}) }
        compose.waitForIdle()
        status = SaveStatus.Idle
        restoration.emulateSavedInstanceStateRestore()
        assertHidden()
    }

    @Test
    fun `given a 640 dp phone when a save failed then the input bar stays uncovered`() {
        show(FAILED)
        assertAboveInput("Not saved", "Save again")
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given German at font scale 2 when a save failed then title and button fit the plan area`() {
        show(FAILED, fontScale = 2f)
        compose.assertTextComplete("Nicht gespeichert")
        compose.assertTextComplete("Erneut speichern")
        assertAboveInput("Nicht gespeichert", "Erneut speichern")
        val button = compose.onNode(hasText("Erneut speichern") and hasClickAction())
        assertTrue("touch target", button.fetchSemanticsNode().boundsInRoot.height >= 48f)
        button.performClick()
        assertEquals(listOf<EditorIntent>(CoreIntent.RetrySave), intents)
    }

    /** Title and button lie inside the plan area, which ends above the first line of the input bar. */
    private fun assertAboveInput(
        title: String,
        button: String,
    ) {
        val area = compose.onNodeWithTag(PLAN_AREA_TAG).fetchSemanticsNode().boundsInRoot
        listOf(title, button).forEach { text ->
            val bounds = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$text $bounds must lie inside $area", bounds.top >= area.top && bounds.bottom <= area.bottom)
        }
        val buttonBounds = compose.onNode(hasText(button) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("button $buttonBounds must end inside $area", buttonBounds.bottom <= area.bottom)
    }
}
