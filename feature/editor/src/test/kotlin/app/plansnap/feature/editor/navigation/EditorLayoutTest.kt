package app.plansnap.feature.editor.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.plansnap.feature.editor.screen.PLAN_MIN_HEIGHT
import app.plansnap.feature.editor.shape.assertTextComplete
import app.plansnap.feature.editor.state.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `given the card height when the font grows then the card starts expanded only if its header fits`() {
        assertTrue(statusCardStartsExpanded(88.dp, 1f))
        assertFalse(statusCardStartsExpanded(87.dp, 1f))
        assertTrue(statusCardStartsExpanded(176.dp, 2f))
        assertFalse(statusCardStartsExpanded(175.dp, 2f))
    }

    @Test
    fun `given a 740 dp phone when the card is shown then it starts expanded and collapses on a tap`() {
        show(layoutState(withCard = true))
        compose.onNodeWithText("Adjust").assertExists()
        cardHeader().performClick()
        compose.onNodeWithText("Adjust").assertDoesNotExist()
        assertSummaryComplete()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `given a 640 dp phone when the card is shown then it starts collapsed with status name and area`() {
        show(layoutState(withCard = true))
        compose.onNodeWithText("Adjust").assertDoesNotExist()
        assertSummaryComplete()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `given a 640 dp phone when the header is tapped then the card expands and its action can be reached`() {
        show(layoutState(withCard = true))
        cardHeader().performClick()
        compose.onNodeWithText("Adjust").performScrollTo().assertIsDisplayed()
        assertOkInsideRoot()
    }

    @Test
    fun `given a 740 dp phone and font scale 2 when the card is shown then status name and area are complete`() {
        show(layoutState(withCard = true), fontScale = 2f)
        compose.onNodeWithText("Adjust").assertDoesNotExist()
        assertSummaryComplete()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
    fun `given a 640 dp phone and font scale 2 when the card is shown then status name and area are complete`() {
        show(layoutState(withCard = true), fontScale = 2f)
        assertSummaryComplete()
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `given a german 640 dp phone and font scale 2 when shown then status name and area are complete`() {
        show(layoutState(withCard = true), fontScale = 2f)
        listOf("Küche", "12,06 m²", "Form nicht bestätigt").forEach { compose.assertTextComplete(it) }
    }

    private fun cardHeader() = compose.onNode(hasText("Küche", substring = true) and hasClickAction())

    private fun assertSummaryComplete() {
        listOf("Küche", "12.06 m²", "Shape not confirmed").forEach { compose.assertTextComplete(it) }
    }

    @Test
    fun `given any slot when the card height is limited then the plan keeps its minimum if the card can give way`() {
        for (slot in listOf(176f, 276f, 400f, 600f)) {
            val card = statusCardMaxHeight(slot.dp, 16.dp)
            assertTrue(
                "card of slot $slot is at most 35 percent or minimal",
                card <= (slot * 0.35f).dp || card == 64.dp,
            )
            if (card > 64.dp) assertTrue("plan keeps its minimum in $slot", slot.dp - card - 16.dp >= PLAN_MIN_HEIGHT)
        }
        assertEquals(64.dp, statusCardMaxHeight(176.dp, 16.dp))
        assertEquals((276 * 0.35f).dp, statusCardMaxHeight(276.dp, 16.dp))
        assertEquals((400 * 0.35f).dp, statusCardMaxHeight(400.dp, 16.dp))
        assertEquals((600 * 0.35f).dp, statusCardMaxHeight(600.dp, 16.dp))
        assertEquals(68.dp, statusCardMaxHeight(200.dp, 36.dp))
    }
}
