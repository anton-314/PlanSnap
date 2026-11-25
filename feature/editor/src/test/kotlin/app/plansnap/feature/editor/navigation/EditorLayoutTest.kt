package app.plansnap.feature.editor.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.plansnap.feature.editor.screen.PLAN_MIN_HEIGHT
import app.plansnap.feature.editor.state.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The thumb zone of the editor in portrait: `OK` is complete and tappable, the plan keeps its minimum height. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class EditorLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: EditorUiState,
        fontScale: Float = 1f,
    ) = compose.setContent { LayoutHost(state, fontScale) }

    private fun assertOkInsideRoot() {
        val ok = compose.onNode(hasText("OK") and hasClickAction())
        ok.assertIsDisplayed().assertIsEnabled()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = ok.fetchSemanticsNode().boundsInRoot
        assertTrue("OK $bounds must lie inside $root", bounds.left >= root.left && bounds.right <= root.right)
        assertTrue("OK $bounds must lie inside $root", bounds.top >= root.top && bounds.bottom <= root.bottom)
        assertTrue("OK is at least 56 dp high", bounds.height >= 56f)
    }

    @Test
    fun `given a 740 dp phone without status card when shown then OK is complete and tappable`() {
        show(layoutState(withCard = false))
        assertOkInsideRoot()
    }

    @Test
    fun `given a 740 dp phone with status card when shown then OK is complete and tappable`() {
        show(layoutState(withCard = true))
        compose.onNodeWithText("Küche", substring = true).assertIsDisplayed()
        assertOkInsideRoot()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `given a 640 dp phone without status card when shown then OK is complete and tappable`() {
        show(layoutState(withCard = false))
        assertOkInsideRoot()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `given a 640 dp phone with status card when shown then OK is complete and tappable`() {
        show(layoutState(withCard = true))
        assertOkInsideRoot()
    }

    @Test
    fun `given font scale 2 when shown then the bar scrolls and OK can be reached`() {
        show(layoutState(withCard = false), fontScale = 2f)
        compose.onNode(hasScrollAction()).assertExists()
        compose.onNode(hasText("OK") and hasClickAction()).performScrollTo()
        assertOkInsideRoot()
    }

    @Test
    fun `given any slot when the card height is limited then the plan keeps its minimum if the card can give way`() {
        for (slot in listOf(176f, 276f, 400f, 600f)) {
            val card = statusCardMaxHeight(slot.dp, 16.dp)
            assertTrue("card of slot $slot is at most half or minimal", card <= (slot / 2).dp || card == 64.dp)
            if (card > 64.dp) assertTrue("plan keeps its minimum in $slot", slot.dp - card - 16.dp >= PLAN_MIN_HEIGHT)
        }
        assertEquals(64.dp, statusCardMaxHeight(176.dp, 16.dp))
    }
}
