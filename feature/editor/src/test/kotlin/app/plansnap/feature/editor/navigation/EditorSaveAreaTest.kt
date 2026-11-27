package app.plansnap.feature.editor.navigation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.feature.editor.shape.assertTextComplete
import app.plansnap.feature.editor.state.SaveStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Where the failed-save message ends in the editor at a font scale of 200 percent, with real text metrics. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h690dp-port-notnight-mdpi")
class EditorSaveAreaTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        val failed = SaveStatus.Failed(DomainError.Storage(StorageProblem.WRITE_FAILED))
        compose.setContent { LayoutHost(layoutState(withCard = true).copy(save = failed), 2f) }
    }

    private fun retry() = compose.onNode(hasText("Save again") and hasClickAction())

    private fun retryBounds(): Rect = retry().assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    private fun cardHeader(): Rect =
        compose
            .onNode(hasText("Küche", substring = true) and hasClickAction())
            .fetchSemanticsNode()
            .boundsInRoot

    @Test
    fun `given room for the message above the card when a save failed then the card stays uncovered`() {
        show()

        compose.assertTextComplete("Not saved")
        val bottom = retryBounds().bottom
        assertTrue("message ends at $bottom, the card is at ${cardHeader()}", bottom <= cardHeader().top)
        retry().performClick()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `given no room above the card when a save failed then the message takes only what title and retry need`() {
        show()

        compose.assertTextComplete("Not saved")
        compose.assertTextComplete("Save again")
        val button = retryBounds()
        assertTrue("touch target $button", button.height >= 48f)
        val card = cardHeader()
        assertTrue("message ends at ${button.bottom}, the card is at $card", button.bottom < card.bottom)
        retry().performClick()
    }
}
