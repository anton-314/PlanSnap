package app.plansnap.feature.editor.config

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.plansnap.core.common.units.Length
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.feature.editor.state.openProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In every window all keys of the keypad can be scrolled into view completely, keep their size of at least 56 dp and
 * react to a touch, in the input bar and in a value sheet (UX section 2).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36])
internal class ReachabilityTest(
    private val device: DeviceConfig,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun SemanticsNodeInteraction.tapCompletelyVisible(editor: ConfigEditor) {
        performScrollTo().assertIsDisplayed().assertIsEnabled()
        val node = fetchSemanticsNode()
        val visible = node.boundsInRoot
        assertEquals("Complete width of $visible", node.size.width.toFloat(), visible.width, ROUNDING)
        assertEquals("Complete height of $visible", node.size.height.toFloat(), visible.height, ROUNDING)
        assertTrue("At least 56 dp high: $visible", visible.height >= KEY_SIZE)
        assertTrue("At least 56 dp wide: $visible", visible.width >= KEY_SIZE)
        assertTrue("Inside the window: $visible", visible.left >= 0f && visible.right <= device.widthDp)
        assertTrue("Inside the window: $visible", visible.top >= 0f && visible.bottom <= device.heightDp)
        performClick()
        editor.settle()
    }

    @Test
    fun `given the input bar then every digit, the separator, delete and OK are reachable and enter a wall`() {
        val editor = ConfigEditor(compose, openProject()).launch(device)
        for (digit in 0..9) {
            editor.key(digit.toString()).tapCompletelyVisible(editor)
            assertEquals(digit.toString(), editor.state.inputText)
            editor.described("Delete last character").tapCompletelyVisible(editor)
            assertEquals("", editor.state.inputText)
        }
        listOf("3", "0", "0").forEach { editor.key(it).tapCompletelyVisible(editor) }
        editor.described("Decimal point").tapCompletelyVisible(editor)
        assertEquals("300.", editor.state.inputText)
        editor.key("5").tapCompletelyVisible(editor)

        editor.key("OK").tapCompletelyVisible(editor)

        assertEquals(
            Length(3005),
            editor.rig.stored.rooms
                .single()
                .walls
                .last()
                .length,
        )
    }

    @Test
    fun `given a value sheet then its keys are reachable and store the value`() {
        val editor = ConfigEditor(compose, openProject()).launch(device)
        editor.tapDescribed("Enter corner angle")

        listOf("7", "5").forEach { editor.sheetKey(it).tapCompletelyVisible(editor) }
        editor.sheetKey("OK").tapCompletelyVisible(editor)

        compose.onNodeWithContentDescription("Corner angle 75 degrees, change").assertExists()
    }

    @Test
    fun `given the top bar then back and the menu are shown`() {
        ConfigEditor(compose, emptyProject()).launch(device)

        listOf("Back", "More actions").forEach {
            compose.onNode(hasContentDescription(it) and hasClickAction()).assertIsDisplayed()
        }
    }

    companion object {
        private const val KEY_SIZE = 56f
        private const val ROUNDING = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun devices(): List<DeviceConfig> = DeviceConfig.entries
    }
}
